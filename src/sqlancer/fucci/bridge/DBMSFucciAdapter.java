package sqlancer.fucci.bridge;

import java.sql.SQLException;
import java.util.List;

import sqlancer.common.transaction.TxStatement;
import sqlancer.fucci.FucciGlobalState;
import sqlancer.fucci.FucciIsolation.FucciIsolationLevel;
import sqlancer.fucci.transaction.FucciTransaction;

/**
 * Fucci数据库适配器接口。
 */
public interface DBMSFucciAdapter {
    String getDbmsType();
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