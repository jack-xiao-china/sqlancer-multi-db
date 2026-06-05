package sqlancer.fucci.lock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import sqlancer.common.transaction.Transaction;
import sqlancer.fucci.FucciColumn;
import sqlancer.fucci.FucciSchema;
import sqlancer.fucci.FucciTable;
import sqlancer.fucci.eval.ColumnResolver;
import sqlancer.fucci.mvcc.View;
import sqlancer.fucci.transaction.FucciTransaction;
import sqlancer.fucci.transaction.FucciTxStatement;
import sqlancer.fucci.transaction.FucciTxStatement.FucciStatementType;

/**
 * Fucci锁分析器。
 * 分析每条语句获取的锁类型和锁对象，检测事务间的锁冲突。
 * 参照原生Fucci的Lock.isConflict()设计。
 */
public class FucciLockAnalyzer {

    private final List<Transaction> transactions;

    public FucciLockAnalyzer(List<Transaction> transactions) {
        this.transactions = transactions;
    }

    /**
     * 推断语句将获取的锁类型。
     *
     * @param stmt Fucci语句
     * @return 锁类型，无锁时返回null
     */
    public LockType inferLockType(FucciTxStatement stmt) {
        if (!(stmt.getType() instanceof FucciStatementType)) {
            return null;
        }
        FucciStatementType type = (FucciStatementType) stmt.getType();
        switch (type) {
            case SELECT:
                return null;
            case SELECT_FOR_SHARE:
                return LockType.SHARED_LOCK;
            case SELECT_FOR_UPDATE:
                return LockType.EXCLUSIVE_LOCK;
            case UPDATE:
            case DELETE:
            case INSERT:
            case REPLACE:
                return LockType.EXCLUSIVE_LOCK;
            default:
                return null;
        }
    }

    /**
     * 推断语句的锁对象。
     * 从SQL中提取表名，从involvedRowIds提取行ID。
     *
     * @param stmt Fucci语句
     * @return 锁对象
     */
    public LockObject inferLockObject(FucciTxStatement stmt) {
        String sql = stmt.getTxQueryAdapter().getQueryString();
        String tableName = extractTableName(sql);
        int[] rowIds = stmt.getInvolvedRowIds();

        if (rowIds != null && rowIds.length > 0) {
            if (rowIds.length == 1) {
                return new LockObject(tableName, rowIds[0]);
            }
            // 多行: 创建 RANGE 锁
            Set<Integer> matchedSet = new HashSet<>();
            for (int id : rowIds) {
                matchedSet.add(id);
            }
            return new LockObject(tableName, matchedSet);
        }
        return new LockObject(tableName);
    }

    /**
     * 检测请求的锁是否与其他事务持有的锁冲突。
     *
     * @param requestingTx 请求锁的事务
     * @param newLock 请求的锁
     * @return 冲突的事务，无冲突返回null
     */
    public FucciTransaction checkConflict(FucciTransaction requestingTx, Lock newLock) {
        for (Transaction tx : transactions) {
            if (!(tx instanceof FucciTransaction)) {
                continue;
            }
            FucciTransaction otherTx = (FucciTransaction) tx;
            if (otherTx.getId() == requestingTx.getId()) {
                continue;
            }
            if (!otherTx.isActive()) {
                continue;
            }
            for (Lock heldLock : otherTx.getLocks()) {
                if (!heldLock.isReleased() && !newLock.isCompatibleWith(heldLock)) {
                    return otherTx;
                }
            }
        }
        return null;
    }

    /**
     * 分析语句的锁需求并尝试获取。
     * 若冲突则标记语句为阻塞。
     *
     * @param tx 事务
     * @param stmt 语句
     * @return true表示语句被阻塞
     */
    public boolean analyzeAndAcquire(FucciTransaction tx, FucciTxStatement stmt) {
        return analyzeAndAcquire(tx, stmt, null, null);
    }

    /**
     * 分析语句的锁需求并尝试获取（带谓词求值增强）。
     * 先用 PredicateEvaluator 精确解析 involvedRowIds，再进行锁推断。
     *
     * @param tx          事务
     * @param stmt        语句
     * @param currentView 当前可见视图（用于谓词求值）
     * @param schema      schema 元数据（用于列名解析）
     * @return true表示语句被阻塞
     */
    public boolean analyzeAndAcquire(FucciTransaction tx, FucciTxStatement stmt,
            View currentView, FucciSchema schema) {
        // 先用谓词求值填充 involvedRowIds
        resolveInvolvedRowIds(stmt, currentView, schema);

        LockType lockType = inferLockType(stmt);
        if (lockType == null) {
            return false;
        }

        LockObject lockObject = inferLockObject(stmt);
        Lock newLock = new Lock(tx.getId(), lockObject, lockType);

        FucciTransaction conflictingTx = checkConflict(tx, newLock);
        if (conflictingTx != null) {
            newLock.block();
            tx.addBlockedStatement(stmt);
            tx.setBlocked(true);
            stmt.setBlockedExecution(true);
            return true;
        }

        newLock.grant();
        tx.addLock(newLock);
        return false;
    }

    /**
     * 释放事务持有的所有锁（COMMIT或ROLLBACK时调用）。
     *
     * @param tx 事务
     */
    public void releaseLocks(FucciTransaction tx) {
        for (Lock lock : tx.getLocks()) {
            lock.release();
        }
        tx.clearLocks();
        tx.setBlocked(false);
    }

    /**
     * 重处理被阻塞的语句（锁释放后调用）。
     * 尝试重新获取锁，成功则解除阻塞。
     *
     * @param tx 之前被阻塞的事务
     * @return 已解除阻塞的语句列表
     */
    public List<FucciTxStatement> processBlockedStatements(FucciTransaction tx) {
        List<FucciTxStatement> unblocked = new ArrayList<>();
        List<FucciTxStatement> blockedStmts = tx.getBlockedStatements();

        for (FucciTxStatement blockedStmt : blockedStmts) {
            LockType lockType = inferLockType(blockedStmt);
            if (lockType == null) {
                unblocked.add(blockedStmt);
                continue;
            }
            LockObject lockObject = inferLockObject(blockedStmt);
            Lock newLock = new Lock(tx.getId(), lockObject, lockType);
            FucciTransaction conflict = checkConflict(tx, newLock);
            if (conflict == null) {
                newLock.grant();
                tx.addLock(newLock);
                blockedStmt.setBlockedExecution(false);
                unblocked.add(blockedStmt);
            }
        }

        blockedStmts.removeAll(unblocked);
        if (blockedStmts.isEmpty()) {
            tx.setBlocked(false);
        }
        return unblocked;
    }

    /**
     * 用 PredicateEvaluator 精确解析语句涉及的行ID。
     * 在锁推断前调用，填充 stmt.involvedRowIds。
     */
    private void resolveInvolvedRowIds(FucciTxStatement stmt, View currentView, FucciSchema schema) {
        if (currentView == null || schema == null) {
            return;
        }
        String sql = stmt.getTxQueryAdapter().getQueryString();
        String tableName = extractTableName(sql);

        FucciTable table = schema.getTable(tableName);
        if (table == null) {
            return;
        }

        List<FucciColumn> cols = table.getColumns();
        List<String> colNames = new ArrayList<>();
        for (FucciColumn col : cols) {
            colNames.add(col.getName());
        }
        ColumnResolver resolver = new ColumnResolver(tableName, colNames);

        stmt.extractInvolvedRowIds(currentView, resolver);
    }

    /**
     * 从SQL语句中提取表名。
     */
    private String extractTableName(String sql) {
        if (sql == null) {
            return "unknown";
        }
        String upper = sql.toUpperCase();
        String[] keywords = {"FROM ", "INTO ", "UPDATE ", "JOIN "};
        for (String kw : keywords) {
            int idx = upper.indexOf(kw);
            if (idx != -1) {
                String rest = upper.substring(idx + kw.length()).trim();
                int end = rest.indexOf(' ');
                if (end == -1) {
                    end = rest.length();
                }
                return rest.substring(0, end).toLowerCase();
            }
        }
        return "unknown";
    }
}
