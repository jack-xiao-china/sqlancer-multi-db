package sqlancer.fucci.transaction;

import java.sql.SQLException;
import java.util.List;

import sqlancer.common.query.SQLancerResultSet;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxTestExecutor;
import sqlancer.common.transaction.TxTestExecutionResult;
import sqlancer.fucci.FucciGlobalState;
import sqlancer.fucci.FucciIsolation.FucciIsolationLevel;

/**
 * Fucci事务测试执行器。
 * 在TxTestExecutor基础上扩展锁分析和MVCC模拟。
 */
public class FucciTxTestExecutor extends TxTestExecutor<FucciGlobalState> {

    /** 当前隔离级别 */
    private final FucciIsolationLevel fucciIsoLevel;

    /** 锁分析器 */
    private FucciLockAnalyzer lockAnalyzer;

    /** MVCC模拟器 */
    private FucciMVCCSimulator mvccSimulator;

    /**
     * 构造函数
     *
     * @param globalState 全局状态
     * @param transactions 事务列表
     * @param schedule 调度序列
     * @param isoLevel 隔离级别
     */
    public FucciTxTestExecutor(FucciGlobalState globalState, List<Transaction> transactions,
            List<TxStatement> schedule, FucciIsolationLevel isoLevel) {
        super(globalState, transactions, schedule, isoLevel);
        this.fucciIsoLevel = isoLevel;
    }

    /**
     * 执行事务测试(带MVCC和锁分析)
     *
     * @return 执行结果
     */
    @Override
    public TxTestExecutionResult execute() throws SQLException {
        // 初始化锁分析器
        lockAnalyzer = new FucciLockAnalyzer(globalState, transactions);

        // 初始化MVCC模拟器
        mvccSimulator = new FucciMVCCSimulator(globalState, fucciIsoLevel);

        // 执行标准事务流程
        TxTestExecutionResult result = super.execute();

        // 添加Fucci特有分析
        addFucciAnalysis(result);

        return result;
    }

    /**
     * 添加Fucci特有的分析结果
     *
     * @param result 执行结果
     */
    private void addFucciAnalysis(TxTestExecutionResult result) {
        // 分析锁冲突
        if (lockAnalyzer != null) {
            lockAnalyzer.analyzeLockConflicts(result);
        }

        // MVCC模拟验证
        if (mvccSimulator != null && globalState.getFucciOptions().isMTOracle()) {
            mvccSimulator.verifyMVCC(result);
        }
    }

    @Override
    protected void generateIsolationLevel() throws SQLException {
        String isoQuery = getIsolationLevelQuery(fucciIsoLevel);
        for (Transaction tx : transactions) {
            TxSQLQueryAdapter query = new TxSQLQueryAdapter(isoQuery);
            query.execute(tx);
        }
    }

    /**
     * 获取隔离级别设置SQL
     *
     * @param isoLevel 隔离级别
     * @return SQL语句
     */
    private String getIsolationLevelQuery(FucciIsolationLevel isoLevel) {
        String dbmsType = globalState.getDbmsType();
        switch (dbmsType.toLowerCase()) {
            case "mysql":
            case "gaussdbm":
                return "SET SESSION TRANSACTION ISOLATION LEVEL " + isoLevel.getName();
            case "postgres":
            case "gaussdba":
                return "SET TRANSACTION ISOLATION LEVEL " + isoLevel.getName();
            default:
                return "SET TRANSACTION ISOLATION LEVEL " + isoLevel.getName();
        }
    }

    @Override
    protected void setTimeout() throws SQLException {
        String timeoutQuery = getTimeoutQuery();
        for (Transaction tx : transactions) {
            TxSQLQueryAdapter query = new TxSQLQueryAdapter(timeoutQuery);
            query.execute(tx);
        }
    }

    /**
     * 获取超时设置SQL
     *
     * @return SQL语句
     */
    private String getTimeoutQuery() {
        String dbmsType = globalState.getDbmsType();
        switch (dbmsType.toLowerCase()) {
            case "mysql":
            case "gaussdbm":
                return "SET innodb_lock_wait_timeout = 100";
            case "postgres":
            case "gaussdba":
                return "SET statement_timeout = 100000"; // 100秒(毫秒)
            default:
                return "SET lock_timeout = 100";
        }
    }

    @Override
    protected SQLancerResultSet showWarnings(Transaction transaction) throws SQLException {
        String dbmsType = globalState.getDbmsType();
        String warningQuery;
        switch (dbmsType.toLowerCase()) {
            case "mysql":
            case "gaussdbm":
                warningQuery = "SHOW WARNINGS";
                break;
            case "postgres":
            case "gaussdba":
                warningQuery = "SELECT * FROM pg_catalog.pg_warnings LIMIT 10";
                break;
            default:
                warningQuery = "SHOW WARNINGS";
        }
        TxSQLQueryAdapter showSql = new TxSQLQueryAdapter(warningQuery);
        return showSql.executeAndGet(transaction);
    }

    @Override
    protected void handleAbortedTxn(Transaction transaction) throws SQLException {
        // 将FucciTransaction标记为中止
        if (transaction instanceof FucciTransaction) {
            FucciTransaction fucciTx = (FucciTransaction) transaction;
            fucciTx.setAborted(true);
            fucciTx.clearLocks();
        }
    }

    // ============ 内部类定义 ============

    /**
     * Fucci锁分析器(简化实现)
     */
    private static class FucciLockAnalyzer {
        private final FucciGlobalState globalState;
        private final List<Transaction> transactions;

        public FucciLockAnalyzer(FucciGlobalState globalState, List<Transaction> transactions) {
            this.globalState = globalState;
            this.transactions = transactions;
        }

        public void analyzeLockConflicts(TxTestExecutionResult result) {
            // 锁冲突分析逻辑由具体的Oracle实现
            // 使用transactions进行锁分析记录
            globalState.getState().getLocalState().log("Lock analysis for " + transactions.size() + " transactions");
        }
    }

    /**
     * Fucci MVCC模拟器(简化实现)
     */
    private static class FucciMVCCSimulator {
        private final FucciGlobalState globalState;
        private final FucciIsolationLevel isoLevel;

        public FucciMVCCSimulator(FucciGlobalState globalState, FucciIsolationLevel isoLevel) {
            this.globalState = globalState;
            this.isoLevel = isoLevel;
        }

        public void verifyMVCC(TxTestExecutionResult result) {
            // 根据隔离级别进行MVCC验证
            result.setIsolationLevel(isoLevel);
            // 使用globalState记录MVCC验证信息
            globalState.getState().getLocalState().log("MVCC verification for isolation level: " + isoLevel);
        }
    }
}