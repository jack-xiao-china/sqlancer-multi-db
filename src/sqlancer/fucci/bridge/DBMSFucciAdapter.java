package sqlancer.fucci.bridge;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import sqlancer.SQLConnection;
import sqlancer.common.transaction.TxStatement;
import sqlancer.fucci.FucciGlobalState;
import sqlancer.fucci.FucciIsolation.FucciIsolationLevel;
import sqlancer.fucci.transaction.FucciTransaction;

/**
 * Fucci数据库适配器接口。
 */
public interface DBMSFucciAdapter {
    String getDbmsType();

    /**
     * Opens a dedicated connection for one FUCCI transaction. Each transaction must run on its
     * own connection: the oracle tests isolation BETWEEN transactions (a shared session would
     * make the interleaved schedules meaningless), and {@code tx.closeConnection()} must never
     * kill the shared main connection that {@code TxBase.reproduceDatabase()} still needs.
     */
    default SQLConnection createTransactionConnection(FucciGlobalState state) throws SQLException {
        String url = state.getConnection().getURL();
        return new SQLConnection(DriverManager.getConnection(url, state.getOptions().getUserName(),
                state.getOptions().getPassword()));
    }

    FucciTransaction generateTransaction(FucciGlobalState globalState, FucciIsolationLevel isolationLevel) throws SQLException;
    List<TxStatement> generateStatements(FucciGlobalState globalState, FucciTransaction transaction) throws SQLException;
    void generateConflictStatements(FucciGlobalState globalState, FucciTransaction transaction, String conflictType) throws SQLException;
    Object[][] getTableSnapshot(FucciGlobalState globalState, String tableName) throws SQLException;
    Object parsePredicate(String predicate);
    boolean evaluateConstraint(Object constraint, Object[] rowData);
    String getBeginStatement();
    String getCommitStatement();
    String getRollbackStatement();
    String getLockTimeoutStatement(int timeoutSeconds);
    String getIsolationLevelStatement(FucciIsolationLevel isolationLevel);
    boolean isDeadlockError(String errorMessage);
    boolean isLockWaitTimeoutError(String errorMessage);
    String getPrimaryKeyColumn(FucciGlobalState globalState, String tableName) throws SQLException;
    List<String> getSupportedExpressionTypes();
}