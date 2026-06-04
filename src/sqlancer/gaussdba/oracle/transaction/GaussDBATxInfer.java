package sqlancer.gaussdba.oracle.transaction;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.common.transaction.QueryResultUtil;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxStatementExecutionResult;
import sqlancer.common.transaction.TxTestExecutionResult;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;
import sqlancer.gaussdba.transaction.GaussDBAIsolation.GaussDBAIsolationLevel;
import sqlancer.gaussdba.transaction.GaussDBATxStatement.GaussDBAStatementType;

/**
 * GaussDB-A MVCC version inference engine.
 * Creates auxiliary version tables to track row versions and infer transaction execution results
 * based on MVCC visibility rules.
 *
 * GaussDB-A uses Oracle-compatible mode with PostgreSQL-like MVCC behavior:
 * - Snapshot timing: BEGIN (same as PostgreSQL)
 * - No READ_UNCOMMITTED isolation level
 * - Oracle-compatible error codes (ORA-00060, ORA-04020)
 */
public class GaussDBATxInfer {

    private static final String ROWID = "rid";
    private static final String VERSION_TABLE = "_vt";
    private static final String VERSION_ID = "vid";
    private static final String VERSION_IS_DELETED = "deleted";
    private static final String VERSION_TX_ID = "txid";
    private static final String TABLE_PREFIX = "_infer_";

    private final GaussDBAGlobalState globalState;
    private final List<GaussDBATable> tables;
    private final TxTestExecutionResult txTestResult;
    private final GaussDBAIsolationLevel isolationLevel;
    private List<Transaction> committedTxs;
    private Map<Transaction, List<Integer>> snapshotTxs;

    private int uniqueRowId = 1;

    public GaussDBATxInfer(GaussDBAGlobalState globalState, List<Transaction> transactions,
                           TxTestExecutionResult txTestResult, GaussDBAIsolationLevel isolationLevel) {
        this.globalState = globalState;
        this.txTestResult = txTestResult;
        this.isolationLevel = isolationLevel;
        this.committedTxs = new ArrayList<>();
        this.snapshotTxs = new HashMap<>();
        for (Transaction tx : transactions) {
            snapshotTxs.put(tx, new ArrayList<>());
        }
        this.tables = globalState.getSchema().getDatabaseTables();
    }

    public TxTestExecutionResult inferOracle() throws SQLException {
        List<TxStatementExecutionResult> stmtExecutionResults = txTestResult.getStatementExecutionResults();
        List<TxStatementExecutionResult> stmtOracleResults = scheduleClone(stmtExecutionResults);
        for (GaussDBATable table : tables) {
            String tableName = table.getName();
            addRowIdColumn(tableName);
            fillEmptyRowIds(tableName);
            initVersionTable(tableName);
        }
        int stmtId = 0;
        for (TxStatementExecutionResult stmtResult : stmtOracleResults) {
            Transaction curTx = stmtResult.getStatement().getTransaction();
            analyzeStmt(stmtResult, curTx, ++stmtId);
        }

        TxTestExecutionResult txOracleResult = new TxTestExecutionResult();
        txOracleResult.setIsolationLevel(isolationLevel);
        txOracleResult.setStatementExecutionResults(stmtOracleResults);
        Map<String, List<Object>> finalStates = new HashMap<>();
        for (GaussDBATable targetTable : tables) {
            String tableName = targetTable.getName();
            checkDeadlock(tableName);
            String finalTable = TABLE_PREFIX + tableName;
            List<Object> finalState = getDBFinalState(finalTable, targetTable);
            dropTable(finalTable);
            finalStates.put(tableName, finalState);
            String auxiliaryTable = TABLE_PREFIX + tableName + VERSION_TABLE;
            dropTable(auxiliaryTable);
        }
        txOracleResult.setDbFinalStates(finalStates);
        return txOracleResult;
    }

    private void addRowIdColumn(String tableName) throws SQLException {
        SQLQueryAdapter checkColumn = new SQLQueryAdapter(
                String.format("SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '%s' AND column_name = '%s'",
                        tableName, ROWID));
        SQLancerResultSet result = checkColumn.executeAndGet(globalState);
        boolean columnExists = false;
        if (result != null) {
            columnExists = result.next();
        }

        if (columnExists) {
            SQLQueryAdapter dropColumn = new SQLQueryAdapter(
                    String.format("ALTER TABLE %s DROP COLUMN %s", tableName, ROWID));
            dropColumn.execute(globalState);
        }

        SQLQueryAdapter addColumn = new SQLQueryAdapter(
                String.format("ALTER TABLE %s ADD COLUMN %s INT", tableName, ROWID));
        addColumn.execute(globalState);
    }

    private List<Integer> fillEmptyRowIds(String tableName) throws SQLException {
        List<Integer> rowIds = new ArrayList<>();
        while (true) {
            String updateRowId = String.format(
                    "UPDATE %s SET %s = %d WHERE %s IS NULL AND ctid = (SELECT ctid FROM %s WHERE %s IS NULL LIMIT 1)",
                    tableName, ROWID, uniqueRowId, ROWID, tableName, ROWID);
            Statement statement = globalState.getConnection().createStatement();
            int ret = statement.executeUpdate(updateRowId);
            statement.close();
            if (ret == 1) {
                rowIds.add(uniqueRowId);
                uniqueRowId++;
            }
            if (ret == 0) {
                break;
            }
        }
        return rowIds;
    }

    private void initVersionTable(String tableName) throws SQLException {
        String auxiliaryVersionTable = TABLE_PREFIX + tableName + VERSION_TABLE;
        dropTable(auxiliaryVersionTable);
        createVersionTable(tableName);
        SQLQueryAdapter insertVersionAdapter = new SQLQueryAdapter(
                String.format("INSERT INTO %s SELECT * FROM %s",
                        auxiliaryVersionTable, tableName));
        insertVersionAdapter.execute(globalState);
        SQLQueryAdapter addColumnAdapter = new SQLQueryAdapter(
                String.format("ALTER TABLE %s ADD COLUMN %s INT, ADD COLUMN %s INT DEFAULT 0, " +
                                "ADD COLUMN %s INT DEFAULT 0", auxiliaryVersionTable, VERSION_ID, VERSION_IS_DELETED,
                        VERSION_TX_ID));
        addColumnAdapter.execute(globalState);
        SQLQueryAdapter updateVTAdapter = new SQLQueryAdapter(
                String.format("UPDATE %s SET %s = 0", auxiliaryVersionTable, VERSION_ID));
        updateVTAdapter.execute(globalState);
    }

    private void createVersionTable(String tableName) throws SQLException {
        String auxiliaryVersionTable = TABLE_PREFIX + tableName + VERSION_TABLE;
        SQLQueryAdapter createVTAdapter = new SQLQueryAdapter(
                String.format("CREATE TABLE %s AS SELECT * FROM %s", auxiliaryVersionTable, tableName), true);
        createVTAdapter.execute(globalState);
    }

    private void analyzeStmt(TxStatementExecutionResult stmtResult, Transaction curTx, int stmtId) throws SQLException {
        TxStatement stmt = stmtResult.getStatement();
        Object stmtType = stmt.getType();
        String auxiliaryPrefix = TABLE_PREFIX + stmt.getTransaction().getId() + "_" + stmtId;

        if (stmtType == GaussDBAStatementType.BEGIN) {
            // GaussDB-A (Oracle-compatible) snapshot is established at BEGIN for RR/SER.
            if (isolationLevel == GaussDBAIsolationLevel.REPEATABLE_READ
                    || isolationLevel == GaussDBAIsolationLevel.SERIALIZABLE) {
                createSnapshot(curTx);
            }
        } else if (stmtType == GaussDBAStatementType.COMMIT) {
            committedTxs.add(curTx);
        } else if (stmtType == GaussDBAStatementType.ROLLBACK) {
            for (GaussDBATable table : tables) {
                String tableName = table.getName();
                String auxiliaryVersionTable = TABLE_PREFIX + tableName + VERSION_TABLE;
                SQLQueryAdapter sql = new SQLQueryAdapter(String.format("DELETE FROM %s WHERE %s = %d",
                        auxiliaryVersionTable, VERSION_TX_ID, curTx.getId()));
                sql.execute(globalState);
            }
        } else if (stmtType == GaussDBAStatementType.SELECT
                || stmtType == GaussDBAStatementType.SELECT_FOR_SHARE
                || stmtType == GaussDBAStatementType.SELECT_FOR_UPDATE) {
            List<GaussDBATable> targetTables = getTargetTables(stmt.getTxQueryAdapter().getQueryString());
            List<String> targetTableNames = new ArrayList<>();
            for (GaussDBATable targetTable : targetTables) {
                targetTableNames.add(targetTable.getName());
                String auxiliaryTableName = auxiliaryPrefix + "_" + targetTable.getName();
                if (isolationLevel == GaussDBAIsolationLevel.READ_COMMITTED
                        || stmtType == GaussDBAStatementType.SELECT_FOR_UPDATE
                        || stmtType == GaussDBAStatementType.SELECT_FOR_SHARE) {
                    readCommittedVersion(auxiliaryTableName, curTx, targetTable);
                } else {
                    readSnapshotVersion(auxiliaryTableName, curTx, targetTable);
                }
            }
            stmtResult.setResult(selectExecuteOnView(stmtResult, auxiliaryPrefix, targetTableNames));
            for (String targetTableName : targetTableNames) {
                String auxiliaryTableName = auxiliaryPrefix + "_" + targetTableName;
                dropTable(auxiliaryTableName);
            }
        } else {
            GaussDBATable targetTable = getTargetTable(stmt.getTxQueryAdapter().getQueryString());
            String auxiliaryTableName = auxiliaryPrefix + "_" + targetTable.getName();
            readCommittedVersion(auxiliaryTableName, curTx, targetTable);
            List<Integer> affectedRowIds = affectRowOnView(stmtResult, auxiliaryPrefix);
            updateVersionTable(stmtResult, auxiliaryPrefix, stmtId, affectedRowIds);
            dropTable(auxiliaryTableName);
        }
    }

    private void checkDeadlock(String tableName) throws SQLException {
        String auxiliaryVersionTable = TABLE_PREFIX + tableName + VERSION_TABLE;
        for (TxStatementExecutionResult stmtResult : txTestResult.getStatementExecutionResults()) {
            if (stmtResult.reportDeadlock()) {
                int txId = stmtResult.getStatement().getTransaction().getId();
                SQLQueryAdapter sql = new SQLQueryAdapter(
                        String.format("DELETE FROM %s WHERE %s = %d", auxiliaryVersionTable, VERSION_TX_ID, txId));
                sql.execute(globalState);
            }
        }
    }

    private List<Object> getDBFinalState(String auxiliaryTable, GaussDBATable targetTable) throws SQLException {
        dropTable(auxiliaryTable);
        readLatestVersion(auxiliaryTable, targetTable);
        dropRowIdColumn(auxiliaryTable);
        SQLQueryAdapter queryTableAdapter = new SQLQueryAdapter("SELECT * FROM " + auxiliaryTable);
        List<Object> tableState = QueryResultUtil.getQueryResult(queryTableAdapter.executeAndGet(globalState));
        return tableState;
    }

    private void readLatestVersion(String auxiliaryTable, GaussDBATable targetTable) throws SQLException {
        createTableLike(auxiliaryTable, targetTable.getName());
        String auxiliaryVersionTable = TABLE_PREFIX + targetTable.getName() + VERSION_TABLE;

        SQLQueryAdapter queryRids = new SQLQueryAdapter(
                String.format("SELECT DISTINCT %s FROM %s WHERE %s NOT IN (SELECT %s FROM %s WHERE %s = 1)",
                        ROWID, auxiliaryVersionTable, ROWID, ROWID, auxiliaryVersionTable, VERSION_IS_DELETED));
        List<Integer> visibleRowIds = getRowIds(queryRids);

        List<String> viewColumns = new ArrayList<>();
        for (GaussDBAColumn tableColumn : targetTable.getColumns()) {
            viewColumns.add(tableColumn.getName());
        }
        viewColumns.add(ROWID);
        String columnNames = String.join(", ", viewColumns);
        for (int rowId : visibleRowIds) {
            SQLQueryAdapter insertVersionAdapter = new SQLQueryAdapter(
                    String.format("INSERT INTO %s SELECT %s FROM %s WHERE %s = %d ORDER BY %s DESC LIMIT 1",
                            auxiliaryTable, columnNames, auxiliaryVersionTable, ROWID, rowId, VERSION_ID));
            insertVersionAdapter.execute(globalState);
        }
    }

    private void readSpecificVersion(String auxiliaryTable, List<String> selectTxId, GaussDBATable targetTable)
            throws SQLException {
        createTableLike(auxiliaryTable, targetTable.getName());
        String auxiliaryVersionTable = TABLE_PREFIX + targetTable.getName() + VERSION_TABLE;

        String selectTxIdName = String.join(", ", selectTxId);
        SQLQueryAdapter queryRids = new SQLQueryAdapter(
                String.format("SELECT DISTINCT %s FROM %s WHERE %s NOT IN (SELECT %s FROM %s WHERE %s = 1 AND %s IN (%s))",
                        ROWID, auxiliaryVersionTable, ROWID, ROWID, auxiliaryVersionTable,
                        VERSION_IS_DELETED, VERSION_TX_ID, selectTxIdName));
        List<Integer> visibleRowIds = getRowIds(queryRids);

        List<String> viewColumns = new ArrayList<>();
        for (GaussDBAColumn tableColumn : targetTable.getColumns()) {
            viewColumns.add(tableColumn.getName());
        }
        viewColumns.add(ROWID);
        String columnNames = String.join(", ", viewColumns);

        for (int rowId : visibleRowIds) {
            String insertVersionSql = String.format(
                    "INSERT INTO %s SELECT %s FROM %s WHERE %s = %d AND %s IN (%s) ORDER BY %s DESC LIMIT 1",
                    auxiliaryTable, columnNames, auxiliaryVersionTable, ROWID, rowId,
                    VERSION_TX_ID, selectTxIdName, VERSION_ID);
            SQLQueryAdapter insertVersionAdapter = new SQLQueryAdapter(insertVersionSql);
            insertVersionAdapter.execute(globalState);
        }
    }

    private void readCommittedVersion(String auxiliaryTable, Transaction curTx, GaussDBATable targetTable)
            throws SQLException {
        List<String> selectTxId = new ArrayList<>();
        selectTxId.add("0");
        selectTxId.add(String.valueOf(curTx.getId()));
        for (Transaction committedTx : committedTxs) {
            selectTxId.add(String.valueOf(committedTx.getId()));
        }
        readSpecificVersion(auxiliaryTable, selectTxId, targetTable);
    }

    private void readSnapshotVersion(String auxiliaryTable, Transaction curTx, GaussDBATable targetTable)
            throws SQLException {
        List<String> selectTxId = new ArrayList<>();
        selectTxId.add("0");
        selectTxId.add(String.valueOf(curTx.getId()));
        for (Transaction committedTx : committedTxs) {
            int committedTxId = committedTx.getId();
            if (snapshotTxs.get(curTx).isEmpty() || snapshotTxs.get(curTx).contains(committedTxId)) {
                selectTxId.add(String.valueOf(committedTxId));
            }
        }
        readSpecificVersion(auxiliaryTable, selectTxId, targetTable);
    }

    private List<Object> selectExecuteOnView(TxStatementExecutionResult stmtResult, String auxiliaryPrefix,
                                             List<String> targetTableNames) throws SQLException {
        TxSQLQueryAdapter initSelectAdapter = stmtResult.getStatement().getTxQueryAdapter();
        String selectStmt = initSelectAdapter.getQueryString();
        for (String targetTableName : targetTableNames) {
            String auxiliaryTable = auxiliaryPrefix + "_" + targetTableName;
            dropRowIdColumn(auxiliaryTable);
            selectStmt = selectStmt.replace(targetTableName, auxiliaryTable);
        }

        TxSQLQueryAdapter selectAdapter = new TxSQLQueryAdapter(selectStmt, initSelectAdapter.getExpectedErrors());
        SQLancerResultSet queryResult = null;
        try {
            queryResult = selectAdapter.executeAndGet(globalState, true);
        } catch (SQLException e) {
            String errorInfo = e.getMessage();
            for (String targetTableName : targetTableNames) {
                String auxiliaryTable = auxiliaryPrefix + "_" + targetTableName;
                errorInfo = errorInfo.replace(auxiliaryTable, targetTableName);
            }
            stmtResult.setErrorInfo(errorInfo);
        }
        return QueryResultUtil.getQueryResult(queryResult);
    }

    private List<Integer> affectRowOnView(TxStatementExecutionResult stmtResult, String auxiliaryPrefix)
            throws SQLException {
        List<Integer> affectedRowIds;
        TxStatement stmt = stmtResult.getStatement();
        String sql = stmt.getTxQueryAdapter().getQueryString();
        GaussDBATable targetTable = getTargetTable(sql);
        String targetTableName = targetTable.getName();
        String auxiliaryTable = auxiliaryPrefix + "_" + targetTableName;
        sql = sql.replace(targetTableName, auxiliaryTable);

        Object stmtType = stmt.getType();
        if (stmtType == GaussDBAStatementType.UPDATE) {
            String updatedColumn = "updated";
            SQLQueryAdapter addColumnUpdated = new SQLQueryAdapter(
                    String.format("ALTER TABLE %s ADD COLUMN %s INT DEFAULT 0", auxiliaryTable, updatedColumn));
            addColumnUpdated.execute(globalState);
            String setUpdated = String.format(", %s = 1", updatedColumn);
            if (sql.contains("WHERE")) {
                int whereId = sql.indexOf(" WHERE");
                String updateSql = sql.substring(0, whereId) + setUpdated;
                sql = updateSql + sql.substring(whereId);
            } else {
                sql = sql.replaceAll(";", setUpdated + ";");
            }
            TxSQLQueryAdapter updateStmt = new TxSQLQueryAdapter(sql,
                    stmtResult.getStatement().getTxQueryAdapter().getExpectedErrors());
            executeTxStatement(stmtResult, updateStmt);
            SQLQueryAdapter selectUpdatedRids = new SQLQueryAdapter(
                    String.format("SELECT %s FROM %s WHERE %s = 1", ROWID, auxiliaryTable, updatedColumn));
            affectedRowIds = getRowIds(selectUpdatedRids);
            SQLQueryAdapter dropColumnUpdated = new SQLQueryAdapter(
                    String.format("ALTER TABLE %s DROP COLUMN %s", auxiliaryTable, updatedColumn));
            dropColumnUpdated.execute(globalState);
        } else if (stmtType == GaussDBAStatementType.DELETE) {
            SQLQueryAdapter queryRids = new SQLQueryAdapter(String.format("SELECT %s FROM %s", ROWID, auxiliaryTable));
            affectedRowIds = getRowIds(queryRids);

            TxSQLQueryAdapter deleteStmt = new TxSQLQueryAdapter(sql, stmt.getTxQueryAdapter().getExpectedErrors());
            executeTxStatement(stmtResult, deleteStmt);

            if (!stmtResult.reportError()) {
                List<Integer> resRowIds = getRowIds(queryRids);
                affectedRowIds.removeAll(resRowIds);
            } else {
                affectedRowIds.clear();
            }
            readCommittedVersion(auxiliaryTable, stmt.getTransaction(), targetTable);
        } else {
            // INSERT
            SQLQueryAdapter queryTableStmt = new SQLQueryAdapter(String.format("SELECT * FROM %s", auxiliaryTable));
            List<Object> tableBeforeResult = QueryResultUtil.getQueryResult(queryTableStmt.executeAndGet(globalState));

            TxSQLQueryAdapter insertStmt = new TxSQLQueryAdapter(sql, stmt.getTxQueryAdapter().getExpectedErrors());
            executeTxStatement(stmtResult, insertStmt);

            List<Object> tableAfterResult = QueryResultUtil.getQueryResult(queryTableStmt.executeAndGet(globalState));
            affectedRowIds = new ArrayList<>(computeAffectedRowIds(tableBeforeResult, tableAfterResult, targetTable));

            if (!stmtResult.reportError()) {
                List<Integer> newRowIds = fillEmptyRowIds(auxiliaryTable);
                affectedRowIds.addAll(newRowIds);
            }
        }
        return affectedRowIds;
    }

    private List<Integer> computeAffectedRowIds(List<Object> tableBeforeResult, List<Object> tableAfterResult,
                                                GaussDBATable targetTable) {
        List<Integer> affectedRowIds = new ArrayList<>();
        List<String> beforeResults = preprocessResultSet(tableBeforeResult);
        List<String> afterResults = preprocessResultSet(tableAfterResult);
        int columnNum = targetTable.getColumns().size() + 1;
        int j = 1;
        for (int i = 0; i < tableBeforeResult.size(); i++) {
            if (!beforeResults.get(i).equals(afterResults.get(i))) {
                if (j == columnNum) {
                    throw new AssertionError("Executing sql wrongly");
                }
                int rowId = i + columnNum - j;
                affectedRowIds.add(Integer.parseInt(afterResults.get(rowId)));
            }
            if (j == columnNum) {
                j = 1;
            } else {
                j++;
            }
        }
        return affectedRowIds;
    }

    private void executeTxStatement(TxStatementExecutionResult stmtResult, TxSQLQueryAdapter txStmt)
            throws SQLException {
        try {
            txStmt.execute(globalState, true);
        } catch (SQLException e) {
            stmtResult.setErrorInfo(e.getMessage());
        }
    }

    private void updateVersionTable(TxStatementExecutionResult stmtResult, String auxiliaryPrefix, int stmtId,
                                    List<Integer> affectedRowIds) throws SQLException {
        GaussDBATable targetTable = getTargetTable(stmtResult.getStatement().getTxQueryAdapter().getQueryString());
        String targetTableName = targetTable.getName();
        String auxiliaryVersionTable = TABLE_PREFIX + targetTableName + VERSION_TABLE;
        String auxiliaryTable = auxiliaryPrefix + "_" + targetTableName;
        List<String> viewColumns = new ArrayList<>();
        for (GaussDBAColumn col : targetTable.getColumns()) {
            viewColumns.add(col.getName());
        }
        viewColumns.add(ROWID);
        String columnNames = String.join(", ", viewColumns);
        for (int rowId : affectedRowIds) {
            SQLQueryAdapter insertVersionTable = new SQLQueryAdapter(
                    String.format("INSERT INTO %s (%s) SELECT * FROM %s WHERE %s = %d", auxiliaryVersionTable,
                            columnNames, auxiliaryTable, ROWID, rowId));
            insertVersionTable.execute(globalState);
        }
        int isDeleted = 0;
        if (stmtResult.getStatement().getType() == GaussDBAStatementType.DELETE) {
            isDeleted = 1;
        }
        SQLQueryAdapter updateVersion = new SQLQueryAdapter(
                String.format("UPDATE %s SET %s = %d, %s = %d, %s = %d WHERE %s IS NULL", auxiliaryVersionTable,
                        VERSION_ID, stmtId, VERSION_IS_DELETED, isDeleted, VERSION_TX_ID,
                        stmtResult.getStatement().getTransaction().getId(), VERSION_ID));
        updateVersion.execute(globalState);
    }

    private static List<String> preprocessResultSet(List<Object> resultSet) {
        return resultSet.stream().map(o -> {
            if (o == null) {
                return "[NULL]";
            } else {
                return o.toString();
            }
        }).collect(Collectors.toList());
    }

    private List<TxStatementExecutionResult> scheduleClone(List<TxStatementExecutionResult> stmtExecutionResults) {
        List<TxStatementExecutionResult> copiedOrder = new ArrayList<>();
        for (TxStatementExecutionResult stmtResult : stmtExecutionResults) {
            if (!stmtResult.isBlocked()) {
                copiedOrder.add(new TxStatementExecutionResult(stmtResult.getStatement()));
            }
        }
        return copiedOrder;
    }

    private void createSnapshot(Transaction curTx) {
        List<Integer> snapTx = snapshotTxs.get(curTx);
        snapTx.add(curTx.getId());
        if (!committedTxs.isEmpty()) {
            for (Transaction transaction : committedTxs) {
                snapTx.add(transaction.getId());
            }
        }
        snapshotTxs.put(curTx, snapTx);
    }

    private List<Integer> getRowIds(SQLQueryAdapter sql) throws SQLException {
        List<Integer> rowIds = new ArrayList<>();
        try (SQLancerResultSet results = sql.executeAndGet(globalState)) {
            if (results != null) {
                while (results.next()) {
                    rowIds.add(results.getInt(ROWID));
                }
                Collections.sort(rowIds);
            }
        }
        return rowIds;
    }

    private GaussDBATable getTargetTable(String sql) {
        GaussDBATable targetTable = null;
        for (GaussDBATable t : tables) {
            if (sql.contains(t.getName())) {
                targetTable = t;
            }
        }
        return targetTable;
    }

    private List<GaussDBATable> getTargetTables(String sql) {
        List<GaussDBATable> targetTables = new ArrayList<>();
        for (GaussDBATable t : tables) {
            if (sql.contains(t.getName())) {
                targetTables.add(t);
            }
        }
        return targetTables;
    }

    private void createTableLike(String auxiliaryTable, String tableName) throws SQLException {
        dropTable(auxiliaryTable);
        SQLQueryAdapter createTable = new SQLQueryAdapter(
                String.format("CREATE TABLE %s (LIKE %s INCLUDING ALL)", auxiliaryTable, tableName), true);
        createTable.execute(globalState);
    }

    private void dropRowIdColumn(String tableName) throws SQLException {
        SQLQueryAdapter dropRowId = new SQLQueryAdapter(
                String.format("ALTER TABLE %s DROP COLUMN IF EXISTS %s", tableName, ROWID));
        dropRowId.execute(globalState);
    }

    private void dropTable(String tableName) throws SQLException {
        SQLQueryAdapter dropTableAdapter = new SQLQueryAdapter(String.format("DROP TABLE IF EXISTS %s", tableName));
        dropTableAdapter.execute(globalState);
    }
}
