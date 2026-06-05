package sqlancer.fucci.transaction;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sqlancer.common.query.SQLancerResultSet;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxTestExecutor;
import sqlancer.common.transaction.TxTestExecutionResult;
import sqlancer.fucci.FucciGlobalState;
import sqlancer.fucci.FucciIsolation.FucciIsolationLevel;
import sqlancer.fucci.FucciTable;
import sqlancer.fucci.lock.FucciLockAnalyzer;
import sqlancer.fucci.mvcc.FucciMVCCAnalyzer;
import sqlancer.fucci.mvcc.MVCCSimulator;
import sqlancer.fucci.mvcc.Version;

/**
 * Fucci事务测试执行器。
 * 在TxTestExecutor基础上集成锁分析和MVCC模拟。
 */
public class FucciTxTestExecutor extends TxTestExecutor<FucciGlobalState> {

    /** 当前隔离级别 */
    private final FucciIsolationLevel fucciIsoLevel;

    /** 锁分析器 */
    private FucciLockAnalyzer lockAnalyzer;

    /** MVCC分析器 */
    private FucciMVCCAnalyzer mvccAnalyzer;

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
     * 执行事务测试(带锁分析)
     *
     * @return 执行结果
     */
    @Override
    public TxTestExecutionResult execute() throws SQLException {
        // 初始化锁分析器
        lockAnalyzer = new FucciLockAnalyzer(transactions);

        // 初始化MVCC分析器（从当前数据库状态构建版本链）
        Map<String, Map<Integer, List<Version>>> versionChains = initializeVersionChains();
        MVCCSimulator simulator = new MVCCSimulator(versionChains);
        mvccAnalyzer = new FucciMVCCAnalyzer(simulator, fucciIsoLevel, globalState.getDbmsType());

        // 执行标准事务流程（postStatementComplete hook 会在每条语句完成后被调用）
        TxTestExecutionResult result = super.execute();

        return result;
    }

    /**
     * 从当前数据库状态初始化版本链。
     */
    private Map<String, Map<Integer, List<Version>>> initializeVersionChains() throws SQLException {
        Map<String, Map<Integer, List<Version>>> versionChains = new HashMap<>();
        List<FucciTable> tables = globalState.getSchema().getDatabaseTables();
        for (FucciTable table : tables) {
            String tableName = table.getName();
            Map<Integer, List<Version>> tableVersions = new HashMap<>();

            String query = "SELECT * FROM " + tableName;
            sqlancer.common.query.SQLQueryAdapter sql = new sqlancer.common.query.SQLQueryAdapter(query);
            sqlancer.common.query.SQLancerResultSet rs = sql.executeAndGet(globalState);

            if (rs != null) {
                int rowId = 0;
                while (rs.next()) {
                    int columnCount = rs.getMetaData().getColumnCount();
                    Object[] rowData = new Object[columnCount];
                    for (int i = 0; i < columnCount; i++) {
                        rowData[i] = rs.getObject(i + 1);
                    }
                    Version initVersion = new Version(rowData, "initial", false);
                    List<Version> versions = new ArrayList<>();
                    versions.add(initVersion);
                    tableVersions.put(rowId, versions);
                    rowId++;
                }
                rs.close();
            }
            versionChains.put(tableName, tableVersions);
        }
        return versionChains;
    }

    /**
     * 语句完成后的分析 hook。
     * 由父类 TxTestExecutor.execute() 在每条语句执行完毕后调用。
     */
    @Override
    protected void postStatementComplete(TxStatement stmt) {
        if (!(stmt instanceof FucciTxStatement)) {
            return;
        }
        FucciTxStatement fucciStmt = (FucciTxStatement) stmt;
        FucciTransaction tx = (FucciTransaction) stmt.getTransaction();

        // MVCC分析: 更新版本链和视图
        mvccAnalyzer.processStatement(fucciStmt);

        if (fucciStmt.isEndTxType()) {
            // COMMIT/ROLLBACK: 释放锁并处理其他事务的阻塞语句
            if (fucciStmt.getType() == FucciTxStatement.FucciStatementType.COMMIT) {
                tx.setCommitted(true);
            }
            tx.setFinished(true);
            lockAnalyzer.releaseLocks(tx);

            // 处理其他事务的阻塞语句
            for (Transaction otherTx : transactions) {
                if (otherTx instanceof FucciTransaction && otherTx.getId() != tx.getId()) {
                    lockAnalyzer.processBlockedStatements((FucciTransaction) otherTx);
                }
            }
        } else if (!tx.isAborted()) {
            // 非结束语句: 分析锁获取
            lockAnalyzer.analyzeAndAcquire(tx, fucciStmt);
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
        if (transaction instanceof FucciTransaction) {
            FucciTransaction fucciTx = (FucciTransaction) transaction;
            fucciTx.setAborted(true);
            fucciTx.setFinished(true);
            if (lockAnalyzer != null) {
                lockAnalyzer.releaseLocks(fucciTx);
                // 处理其他事务的阻塞语句
                for (Transaction otherTx : transactions) {
                    if (otherTx instanceof FucciTransaction && otherTx.getId() != fucciTx.getId()) {
                        lockAnalyzer.processBlockedStatements((FucciTransaction) otherTx);
                    }
                }
            }
        }
    }
}
