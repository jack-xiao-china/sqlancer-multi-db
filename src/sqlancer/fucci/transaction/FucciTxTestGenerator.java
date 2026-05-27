package sqlancer.fucci.transaction;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sqlancer.Randomly;
import sqlancer.common.transaction.Transaction;
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

    /** 冲突构造策略 */
    private final String conflictType;

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
        this.conflictType = globalState.getFucciOptions().getConflictType();
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

        // 生成冲突语句
        if (conflictType.equals("random")) {
            generateRandomConflictStatements(tx);
        } else {
            generateStructuredConflictStatements(tx);
        }

        return tx;
    }

    /**
     * 生成随机冲突语句
     *
     * @param tx 事务
     */
    private void generateRandomConflictStatements(FucciTransaction tx) throws SQLException {
        // 使用标准SQLancer生成器生成语句
        List<TxStatement> statements = adapter.generateStatements(globalState, tx);

        for (TxStatement stmt : statements) {
            if (stmt instanceof FucciTxStatement) {
                tx.addStatement(stmt);
            } else {
                // 将普通TxStatement转换为FucciTxStatement
                FucciTxStatement fucciStmt = new FucciTxStatement(tx, stmt.getTxQueryAdapter());
                tx.addStatement(fucciStmt);
            }
        }
    }

    /**
     * 生成结构化冲突语句
     *
     * @param tx 事务
     */
    private void generateStructuredConflictStatements(FucciTransaction tx) throws SQLException {
        // 根据冲突类型生成特定结构的语句序列
        switch (conflictType.toLowerCase()) {
            case "fully-shared":
                generateFullySharedConflict(tx);
                break;
            case "part-shared":
                generatePartSharedConflict(tx);
                break;
            case "tuple":
                generateTupleConflict(tx);
                break;
            default:
                generateRandomConflictStatements(tx);
        }
    }

    /**
     * 生成完全共享冲突(同一行)
     */
    private void generateFullySharedConflict(FucciTransaction tx) throws SQLException {
        // 选择同一行进行并发操作
        adapter.generateConflictStatements(globalState, tx, "fully-shared");
    }

    /**
     * 生成部分共享冲突(部分行重叠)
     */
    private void generatePartSharedConflict(FucciTransaction tx) throws SQLException {
        adapter.generateConflictStatements(globalState, tx, "part-shared");
    }

    /**
     * 生成元组冲突
     */
    private void generateTupleConflict(FucciTransaction tx) throws SQLException {
        adapter.generateConflictStatements(globalState, tx, "tuple");
    }

    /**
     * 生成调度序列
     *
     * @param transactions 事务列表
     * @return 调度序列列表
     */
    @Override
    public List<List<TxStatement>> genSchedules(List<Transaction> transactions) {
        // Fucci使用固定的调度数量配置
        int scheduleCount = globalState.getFucciOptions().getScheduleCount();

        List<List<TxStatement>> schedules = new ArrayList<>();
        Integer maxNum = countSchedules(transactions);
        int num = Math.min(scheduleCount, maxNum);

        int count = 0;
        while (count < num) {
            List<TxStatement> schedule = generateOneSchedule(transactions);
            if (!schedules.contains(schedule)) {
                schedules.add(schedule);
                count++;
            }
        }
        return schedules;
    }

    /**
     * 生成单个调度序列
     */
    public List<TxStatement> generateOneSchedule(List<Transaction> transactions) {
        // 基于冲突概率的调度生成
        if (conflictType.equals("random")) {
            return genOneScheduleInternal(transactions);
        } else {
            // 生成冲突调度
            return generateConflictSchedule(transactions);
        }
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

    /**
     * 生成冲突调度序列
     *
     * @param transactions 事务列表
     * @return 冲突调度
     */
    private List<TxStatement> generateConflictSchedule(List<Transaction> transactions) {
        List<Transaction> txs = new ArrayList<>(transactions);
        List<TxStatement> schedule = new ArrayList<>();

        // 简化实现: 使用随机调度
        while (!txs.isEmpty()) {
            Transaction tx = Randomly.fromList(txs);
            if (tx.getStatements().isEmpty()) {
                txs.remove(tx);
                continue;
            }

            // 选择第一个未执行的语句
            for (TxStatement stmt : tx.getStatements()) {
                if (!schedule.contains(stmt)) {
                    schedule.add(stmt);
                    break;
                }
            }

            // 如果事务所有语句都已执行，移除该事务
            boolean allScheduled = true;
            for (TxStatement stmt : tx.getStatements()) {
                if (!schedule.contains(stmt)) {
                    allScheduled = false;
                    break;
                }
            }
            if (allScheduled) {
                txs.remove(tx);
            }
        }

        return schedule;
    }
}