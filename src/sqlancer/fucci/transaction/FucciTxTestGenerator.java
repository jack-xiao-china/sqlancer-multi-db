package sqlancer.fucci.transaction;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sqlancer.Randomly;
import sqlancer.common.transaction.ScheduleExhaustiveEnumerator;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxConflictConstructor;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxTestGenerator;
import sqlancer.fucci.FucciGlobalState;
import sqlancer.fucci.FucciIsolation;
import sqlancer.fucci.FucciIsolation.FucciIsolationLevel;
import sqlancer.fucci.bridge.DBMSFucciAdapter;

/**
 * Fucci事务测试生成器。
 * 在TxTestGenerator基础上支持冲突构造和MVCC快照管理。
 */
public class FucciTxTestGenerator extends TxTestGenerator<FucciGlobalState> {

    /** 数据库适配器 */
    private final DBMSFucciAdapter adapter;

    /** 隔离级别 */
    private final FucciIsolationLevel isolationLevel;

    /**
     * 构造函数
     *
     * @param globalState 全局状态
     */
    public FucciTxTestGenerator(FucciGlobalState globalState) {
        super(globalState);
        this.adapter = globalState.getFucciAdapter();
        String isolationStr = globalState.getFucciOptions().getIsolationLevel();
        this.isolationLevel = FucciIsolation.getIsolationLevel(
                isolationStr == null ? "RANDOM" : isolationStr,
                globalState.getDbmsType());
    }

    /**
     * 生成事务列表(固定2个事务)
     *
     * @return 事务列表
     */
    @Override
    public List<Transaction> generateTransactions() throws SQLException {
        List<Transaction> transactions = new ArrayList<>();

        // Fucci固定使用2个事务进行并发测试
        int txCount = 2;
        if (globalState.getOptions().useFixedNumTransaction()) {
            txCount = Math.min(globalState.getOptions().getNrTransactions(), 2);
        }

        for (int i = 1; i <= txCount; i++) {
            Transaction tx = generateTransaction();
            transactions.add(tx);
        }

        // 使用TxConflictConstructor进行冲突注入
        if (transactions.size() >= 2 && globalState.getOptions().useConflictConstruction()) {
            TxConflictConstructor.makeConflict(transactions, globalState);
        }

        return transactions;
    }

    /**
     * 生成单个事务
     *
     * @return 事务对象
     */
    @Override
    protected Transaction generateTransaction() throws SQLException {
        // 使用适配器生成FucciTransaction
        FucciTransaction tx = adapter.generateTransaction(globalState, isolationLevel);

        // 使用标准SQLancer生成器生成语句
        List<TxStatement> statements = adapter.generateStatements(globalState, tx);

        for (TxStatement stmt : statements) {
            if (stmt instanceof FucciTxStatement) {
                tx.addStatement(stmt);
            } else {
                FucciTxStatement fucciStmt = new FucciTxStatement(tx, stmt.getTxQueryAdapter());
                tx.addStatement(fucciStmt);
            }
        }

        return tx;
    }

    /**
     * 生成调度序列。
     * 使用穷举+过滤替代纯随机采样：
     * 1. 穷举所有交错排列（或蓄水池采样）
     * 2. 过滤非交错退化调度
     * 3. 过滤异常历史（冲突必须在COMMIT之前）
     *
     * @param transactions 事务列表
     * @return 调度序列列表
     */
    @Override
    public List<List<TxStatement>> genSchedules(List<Transaction> transactions) {
        int scheduleCount = globalState.getFucciOptions().getScheduleCount();

        if (transactions.size() == 2) {
            List<TxStatement> tx1Stmts = transactions.get(0).getStatements();
            List<TxStatement> tx2Stmts = transactions.get(1).getStatements();

            // 穷举或采样
            List<List<TxStatement>> schedules = ScheduleExhaustiveEnumerator.hybridGenerate(
                    tx1Stmts, tx2Stmts, scheduleCount);

            // 过滤非交错调度
            schedules = filterNonInterleaved(schedules, transactions);

            // 过滤异常历史
            schedules = filterAnomalousHistories(schedules, transactions);

            return schedules;
        }

        // 回退: 非2事务时使用随机
        return super.genSchedules(transactions);
    }

    /**
     * 过滤非交错调度（排除 Tx1全在Tx2前 或 Tx2全在Tx1前 的退化情况）。
     */
    private List<List<TxStatement>> filterNonInterleaved(
            List<List<TxStatement>> schedules, List<Transaction> transactions) {
        if (transactions.size() != 2) {
            return schedules;
        }
        int tx1Id = transactions.get(0).getId();
        int tx2Id = transactions.get(1).getId();

        List<List<TxStatement>> filtered = new ArrayList<>();
        for (List<TxStatement> schedule : schedules) {
            if (isInterleaved(schedule, tx1Id, tx2Id)) {
                filtered.add(schedule);
            }
        }
        return filtered.isEmpty() ? schedules : filtered;
    }

    /**
     * 过滤异常历史（冲突语句必须出现在两个COMMIT之前）。
     */
    private List<List<TxStatement>> filterAnomalousHistories(
            List<List<TxStatement>> schedules, List<Transaction> transactions) {
        if (transactions.size() != 2) {
            return schedules;
        }

        List<List<TxStatement>> filtered = new ArrayList<>();
        for (List<TxStatement> schedule : schedules) {
            if (hasConflictBeforeBothCommits(schedule, transactions)) {
                filtered.add(schedule);
            }
        }
        return filtered.isEmpty() ? schedules : filtered;
    }

    private boolean isInterleaved(List<TxStatement> schedule, int tx1Id, int tx2Id) {
        boolean seenTx1 = false;
        boolean seenTx2 = false;
        boolean switched = false;
        int lastTxId = -1;

        for (TxStatement stmt : schedule) {
            int curTxId = stmt.getTransaction().getId();
            if (curTxId == tx1Id) {
                seenTx1 = true;
            }
            if (curTxId == tx2Id) {
                seenTx2 = true;
            }
            if (lastTxId != -1 && curTxId != lastTxId && seenTx1 && seenTx2) {
                switched = true;
            }
            lastTxId = curTxId;
        }
        return switched;
    }

    private boolean hasConflictBeforeBothCommits(
            List<TxStatement> schedule, List<Transaction> transactions) {
        int tx1Id = transactions.get(0).getId();
        int tx2Id = transactions.get(1).getId();
        boolean tx1DataBeforeCommit = false;
        boolean tx2DataBeforeCommit = false;
        boolean bothCommitsSeen = false;
        int commitCount = 0;

        for (TxStatement stmt : schedule) {
            int txId = stmt.getTransaction().getId();

            if (stmt.isEndTxType()) {
                commitCount++;
                if (commitCount == 2) {
                    bothCommitsSeen = true;
                }
            }

            if (!bothCommitsSeen && !stmt.isEndTxType() && !isBeginStmt(stmt)) {
                if (txId == tx1Id) {
                    tx1DataBeforeCommit = true;
                }
                if (txId == tx2Id) {
                    tx2DataBeforeCommit = true;
                }
            }
        }
        return tx1DataBeforeCommit && tx2DataBeforeCommit;
    }

    private boolean isBeginStmt(TxStatement stmt) {
        if (stmt instanceof FucciTxStatement) {
            return stmt.getType() == FucciTxStatement.FucciStatementType.BEGIN
                    || stmt.getType() == FucciTxStatement.FucciStatementType.SET;
        }
        return false;
    }

    /**
     * 生成单个调度序列
     */
    public List<TxStatement> generateOneSchedule(List<Transaction> transactions) {
        return genOneScheduleInternal(transactions);
    }

    /**
     * 内部调度生成方法
     */
    private List<TxStatement> genOneScheduleInternal(List<Transaction> transactions) {
        List<Transaction> txs = new ArrayList<>(transactions);
        List<TxStatement> schedule = new ArrayList<>();
        Map<Transaction, Integer> stmtIndexes = new HashMap<>();
        for (Transaction tx : txs) {
            stmtIndexes.put(tx, 0);
        }
        while (!txs.isEmpty()) {
            Transaction tx = Randomly.fromList(txs);
            int stmtIndex = stmtIndexes.get(tx);
            schedule.add(tx.getStatements().get(stmtIndex));
            if (stmtIndex + 1 < tx.getStatements().size()) {
                stmtIndexes.put(tx, stmtIndex + 1);
            } else {
                txs.remove(tx);
            }
        }
        return schedule;
    }
}