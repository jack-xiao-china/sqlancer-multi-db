package sqlancer.fucci.bridge;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.common.transaction.TxStatement;
import sqlancer.fucci.FucciGlobalState;
import sqlancer.fucci.FucciIsolation.FucciIsolationLevel;
import sqlancer.fucci.FucciTable;
import sqlancer.fucci.transaction.FucciTransaction;
import sqlancer.fucci.transaction.FucciTxStatement;

/**
 * MySQL Fucci适配器实现。
 */
public class MySQLFucciAdapter implements DBMSFucciAdapter {

    @Override
    public String getDbmsType() { return "mysql"; }

    @Override
    public FucciTransaction generateTransaction(FucciGlobalState globalState, FucciIsolationLevel isolationLevel) throws SQLException {
        FucciTransaction tx = new FucciTransaction(globalState.getConnection(), isolationLevel);
        tx.addStatement(new FucciTxStatement(tx, new SQLQueryAdapter("BEGIN")));
        return tx;
    }

    @Override
    public List<TxStatement> generateStatements(FucciGlobalState globalState, FucciTransaction transaction) throws SQLException {
        List<TxStatement> statements = new ArrayList<>();
        statements.add(new FucciTxStatement(transaction, new SQLQueryAdapter("BEGIN")));
        String tableName = getFirstTableName(globalState);
        if (tableName != null) {
            statements.add(new FucciTxStatement(transaction, new SQLQueryAdapter("SELECT * FROM " + tableName + " LIMIT 10")));
            statements.add(new FucciTxStatement(transaction, new SQLQueryAdapter("UPDATE " + tableName + " SET c0 = c0 + 1 WHERE id = 1")));
        }
        statements.add(new FucciTxStatement(transaction, new SQLQueryAdapter("COMMIT")));
        return statements;
    }

    private String getFirstTableName(FucciGlobalState globalState) throws SQLException {
        List<FucciTable> tables = globalState.getSchema().getDatabaseTables();
        if (tables.isEmpty()) { return null; }
        return tables.get(0).getName();
    }

    @Override
    public void generateConflictStatements(FucciGlobalState globalState, FucciTransaction transaction, String conflictType) throws SQLException {
        String tableName = getFirstTableName(globalState);
        if (tableName == null) { return; }
        String sql;
        switch (conflictType.toLowerCase()) {
            case "fully-shared": sql = String.format("UPDATE %s SET c0 = c0 + 1 WHERE id = 1", tableName); break;
            case "part-shared": sql = String.format("UPDATE %s SET c0 = c0 + 1 WHERE id BETWEEN 1 AND 5", tableName); break;
            default: sql = String.format("UPDATE %s SET c0 = c0 + 1 WHERE id = 10", tableName);
        }
        transaction.addStatement(new FucciTxStatement(transaction, new SQLQueryAdapter(sql)));
    }

    @Override
    public Object[][] getTableSnapshot(FucciGlobalState globalState, String tableName) throws SQLException {
        String query = "SELECT * FROM " + tableName;
        SQLQueryAdapter sql = new SQLQueryAdapter(query);
        SQLancerResultSet rs = sql.executeAndGet(globalState);
        List<Object[]> rows = new ArrayList<>();
        if (rs != null) {
            while (rs.next()) {
                int columnCount = rs.getMetaData().getColumnCount();
                Object[] rowData = new Object[columnCount];
                for (int i = 0; i < columnCount; i++) {
                    rowData[i] = rs.getObject(i + 1);
                }
                rows.add(rowData);
            }
            rs.close();
        }
        return rows.toArray(new Object[0][]);
    }

    @Override
    public Object parsePredicate(String predicate) { return predicate; }

    @Override
    public boolean evaluateConstraint(Object constraint, Object[] rowData) { return true; }

    @Override
    public String getBeginStatement() { return "BEGIN"; }
    @Override
    public String getCommitStatement() { return "COMMIT"; }
    @Override
    public String getRollbackStatement() { return "ROLLBACK"; }
    @Override
    public String getLockTimeoutStatement(int timeoutSeconds) { return "SET innodb_lock_wait_timeout = " + timeoutSeconds; }
    @Override
    public String getIsolationLevelStatement(FucciIsolationLevel isolationLevel) { return "SET SESSION TRANSACTION ISOLATION LEVEL " + isolationLevel.getName(); }

    @Override
    public boolean isDeadlockError(String errorMessage) {
        if (errorMessage == null) { return false; }
        return errorMessage.contains("Deadlock") || errorMessage.contains("try restarting transaction");
    }

    @Override
    public boolean isLockWaitTimeoutError(String errorMessage) {
        if (errorMessage == null) { return false; }
        return errorMessage.contains("Lock wait timeout exceeded");
    }

    @Override
    public String getPrimaryKeyColumn(FucciGlobalState globalState, String tableName) throws SQLException {
        String query = "SHOW KEYS FROM " + tableName + " WHERE Key_name = 'PRIMARY'";
        SQLQueryAdapter sql = new SQLQueryAdapter(query);
        SQLancerResultSet rs = sql.executeAndGet(globalState);
        if (rs != null && rs.next()) {
            String pkColumn = rs.getString("Column_name");
            rs.close();
            return pkColumn;
        }
        return null;
    }

    @Override
    public List<String> getSupportedExpressionTypes() { return Arrays.asList("comparison", "logical", "arithmetical", "function"); }
}