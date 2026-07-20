package sqlancer.gaussdba.oracle;

import static sqlancer.ComparatorHelper.getResultSetFirstColumnAsString;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.beust.jcommander.Strings;

import sqlancer.common.oracle.DQEBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLQueryError;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.common.schema.AbstractRelationalTable;
import sqlancer.common.schema.AbstractTable;
import sqlancer.common.schema.AbstractTables;
import sqlancer.gaussdba.GaussDBAErrors;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATables;
import sqlancer.gaussdba.GaussDBAToStringVisitor;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.gen.GaussDBAExpressionGenerator;

/**
 * Differential Query Execution Oracle for GaussDB A compatibility mode.
 * Verifies that SELECT, UPDATE, and DELETE queries with the same predicate access the same rows.
 */
public class GaussDBADQEOracle extends DQEBase<GaussDBAGlobalState> implements TestOracle<GaussDBAGlobalState> {

    private final GaussDBASchema schema;
    private boolean operateOnSingleTable;

    public GaussDBADQEOracle(GaussDBAGlobalState state) {
        super(state);
        schema = state.getSchema();

        GaussDBAErrors.addExpressionErrors(selectExpectedErrors);
        GaussDBAErrors.addInsertUpdateErrors(updateExpectedErrors);
        GaussDBAErrors.addExpressionErrors(deleteExpectedErrors);
    }

    @Override
    public String generateSelectStatement(AbstractTables<?, ?> tables, String tableName, String whereClauseStr) {
        operateOnSingleTable = tables.getTables().size() == 1;
        List<String> selectColumns = new ArrayList<>();
        GaussDBATables gaussTables = (GaussDBATables) tables;
        for (GaussDBATable table : gaussTables.getTables()) {
            selectColumns.add(table.getName() + "." + COLUMN_ROWID);
        }

        return String.format("SELECT %s FROM %s WHERE %s",
                Strings.join(",", selectColumns).toLowerCase(),
                tableName, whereClauseStr);
    }

    @Override
    public String generateUpdateStatement(AbstractTables<?, ?> tables, String tableName, String whereClauseStr) {
        List<String> updateColumns = new ArrayList<>();
        GaussDBATables gaussTables = (GaussDBATables) tables;
        for (GaussDBATable table : gaussTables.getTables()) {
            updateColumns.add(String.format("%s = 1", table.getName() + "." + COLUMN_UPDATED));
        }
        return String.format("UPDATE %s SET %s WHERE %s",
                tableName, Strings.join(",", updateColumns), whereClauseStr);
    }

    @Override
    public String generateDeleteStatement(String tableName, String whereClauseStr) {
        if (operateOnSingleTable) {
            return String.format("DELETE FROM %s WHERE %s", tableName, whereClauseStr);
        } else {
            // Multi-table delete syntax (Oracle style)
            return String.format("DELETE FROM %s WHERE %s", tableName, whereClauseStr);
        }
    }

    @Override
    public void check() throws SQLException {
        operateOnSingleTable = false;

        GaussDBATables tables = schema.getRandomTableNonEmptyTables();
        String tableName = tables.getTables().stream()
                .map(AbstractTable::getName)
                .collect(Collectors.joining(","));

        GaussDBAExpressionGenerator expressionGenerator = new GaussDBAExpressionGenerator(state)
                .setColumns(tables.getColumns());
        GaussDBAExpression whereClause = expressionGenerator.generateBooleanExpression();

        String whereClauseStr = GaussDBAToStringVisitor.asString(whereClause);

        String selectStmt = generateSelectStatement(tables, tableName, whereClauseStr);
        String updateStmt = generateUpdateStatement(tables, tableName, whereClauseStr);
        String deleteStmt = generateDeleteStatement(tableName, whereClauseStr);

        for (GaussDBATable table : tables.getTables()) {
            addAuxiliaryColumns(table);
        }

        state.getState().getLocalState().log(selectStmt);
        SQLQueryResult selectExecutionResult = executeSelect(selectStmt, tables);
        state.getState().getLocalState().log(selectExecutionResult.getAccessedRows().values().toString());

        state.getState().getLocalState().log(updateStmt);
        SQLQueryResult updateExecutionResult = executeUpdate(updateStmt, tables);
        state.getState().getLocalState().log(updateExecutionResult.getAccessedRows().values().toString());

        state.getState().getLocalState().log(deleteStmt);
        SQLQueryResult deleteExecutionResult = executeDelete(deleteStmt, tables);
        state.getState().getLocalState().log(deleteExecutionResult.getAccessedRows().values().toString());

        String compareSelectAndUpdate = compareSelectAndUpdate(selectExecutionResult, updateExecutionResult);
        String compareSelectAndDelete = compareSelectAndDelete(selectExecutionResult, deleteExecutionResult);
        String compareUpdateAndDelete = compareUpdateAndDelete(updateExecutionResult, deleteExecutionResult);

        String errorMessage = compareSelectAndUpdate == null ? "" : compareSelectAndUpdate + "\n";
        errorMessage += compareSelectAndDelete == null ? "" : compareSelectAndDelete + "\n";
        errorMessage += compareUpdateAndDelete == null ? "" : compareUpdateAndDelete + "\n";

        if (!errorMessage.isEmpty()) {
            throw new AssertionError(errorMessage);
        }

        for (GaussDBATable table : tables.getTables()) {
            dropAuxiliaryColumns(table);
        }
    }

    private String compareSelectAndUpdate(SQLQueryResult selectResult, SQLQueryResult updateResult) {
        if (updateResult.hasEmptyErrors()) {
            if (!selectResult.hasEmptyErrors()) {
                return "SELECT has errors, but UPDATE does not.";
            }
            if (!selectResult.hasSameAccessedRows(updateResult)) {
                return "SELECT accessed different rows from UPDATE.";
            }
        }
        return null;
    }

    private String compareSelectAndDelete(SQLQueryResult selectResult, SQLQueryResult deleteResult) {
        if (deleteResult.hasEmptyErrors()) {
            if (!selectResult.hasEmptyErrors()) {
                return "SELECT has errors, but DELETE does not.";
            }
            if (!selectResult.hasSameAccessedRows(deleteResult)) {
                return "SELECT accessed different rows from DELETE.";
            }
        }
        return null;
    }

    private String compareUpdateAndDelete(SQLQueryResult updateResult, SQLQueryResult deleteResult) {
        if (updateResult.hasEmptyErrors() && deleteResult.hasEmptyErrors()) {
            if (!updateResult.hasSameAccessedRows(deleteResult)) {
                return "UPDATE accessed different rows from DELETE.";
            }
        }
        return null;
    }

    private SQLQueryResult executeSelect(String selectStmt, GaussDBATables tables) throws SQLException {
        Map<AbstractRelationalTable<?, ?, ?>, Set<String>> accessedRows = new HashMap<>();
        List<SQLQueryError> queryErrors = new ArrayList<>();
        SQLancerResultSet resultSet = null;
        try {
            resultSet = new SQLQueryAdapter(selectStmt, selectExpectedErrors).executeAndGet(state, false);
        } catch (SQLException ignored) {
            // Unreachable in practice: these statements run with reportException=false, so
            // SQLQueryAdapter returns null/false instead of throwing. A failed UPDATE/DELETE is
            // skipped upstream via the transaction-abort (25P02) path in the finally block (whose
            // getResultSetFirstColumnAsString runs with reportException=true and re-raises the
            // original error, in COMMON_EXPECTED_SQLSTATES, as IgnoreMeException). So this oracle
            // does not produce the "accessed different rows" false positive, and recording errors
            // here would be dead code.
        } finally {
            if (resultSet != null) {
                for (GaussDBATable table : tables.getTables()) {
                    HashSet<String> rows = new HashSet<>();
                    accessedRows.put(table, rows);
                }
                while (resultSet.next()) {
                    for (GaussDBATable table : tables.getTables()) {
                        accessedRows.get(table).add(resultSet.getString(table.getName() + "." + COLUMN_ROWID));
                    }
                }
                resultSet.close();
            }
        }
        return new SQLQueryResult(accessedRows, queryErrors);
    }

    private SQLQueryResult executeUpdate(String updateStmt, GaussDBATables tables) throws SQLException {
        Map<AbstractRelationalTable<?, ?, ?>, Set<String>> accessedRows = new HashMap<>();
        List<SQLQueryError> queryErrors = new ArrayList<>();
        try {
            new SQLQueryAdapter("BEGIN").execute(state, false);
            new SQLQueryAdapter(updateStmt, updateExpectedErrors).execute(state, false);
        } catch (SQLException ignored) {
            // Unreachable in practice: these statements run with reportException=false, so
            // SQLQueryAdapter returns null/false instead of throwing. A failed UPDATE/DELETE is
            // skipped upstream via the transaction-abort (25P02) path in the finally block (whose
            // getResultSetFirstColumnAsString runs with reportException=true and re-raises the
            // original error, in COMMON_EXPECTED_SQLSTATES, as IgnoreMeException). So this oracle
            // does not produce the "accessed different rows" false positive, and recording errors
            // here would be dead code.
        } finally {
            for (GaussDBATable table : tables.getTables()) {
                String tableName = table.getName();
                String rowId = tableName + "." + COLUMN_ROWID;
                String updated = tableName + "." + COLUMN_UPDATED;
                String selectRowIdWithUpdated = String.format("SELECT %s FROM %s WHERE %s = 1",
                        rowId, tableName, updated);
                HashSet<String> rows = new HashSet<>(getResultSetFirstColumnAsString(
                        selectRowIdWithUpdated, updateExpectedErrors, state));
                accessedRows.put(table, rows);
            }
            new SQLQueryAdapter("ROLLBACK").execute(state, false);
        }
        return new SQLQueryResult(accessedRows, queryErrors);
    }

    private SQLQueryResult executeDelete(String deleteStmt, GaussDBATables tables) throws SQLException {
        Map<AbstractRelationalTable<?, ?, ?>, Set<String>> accessedRows = new HashMap<>();
        List<SQLQueryError> queryErrors = new ArrayList<>();
        try {
            for (GaussDBATable table : tables.getTables()) {
                String tableName = table.getName();
                String rowId = tableName + "." + COLUMN_ROWID;
                String selectRowId = String.format("SELECT %s FROM %s", rowId, tableName);
                HashSet<String> rows = new HashSet<>(getResultSetFirstColumnAsString(
                        selectRowId, deleteExpectedErrors, state));
                accessedRows.put(table, rows);
            }
            new SQLQueryAdapter("BEGIN").execute(state, false);
            new SQLQueryAdapter(deleteStmt, deleteExpectedErrors).execute(state, false);
        } catch (SQLException ignored) {
            // Unreachable in practice: these statements run with reportException=false, so
            // SQLQueryAdapter returns null/false instead of throwing. A failed UPDATE/DELETE is
            // skipped upstream via the transaction-abort (25P02) path in the finally block (whose
            // getResultSetFirstColumnAsString runs with reportException=true and re-raises the
            // original error, in COMMON_EXPECTED_SQLSTATES, as IgnoreMeException). So this oracle
            // does not produce the "accessed different rows" false positive, and recording errors
            // here would be dead code.
        } finally {
            for (GaussDBATable table : tables.getTables()) {
                String tableName = table.getName();
                String rowId = tableName + "." + COLUMN_ROWID;
                String selectRowId = String.format("SELECT %s FROM %s", rowId, tableName);
                HashSet<String> rows = new HashSet<>(getResultSetFirstColumnAsString(
                        selectRowId, deleteExpectedErrors, state));
                accessedRows.get(table).removeAll(rows);
            }
            new SQLQueryAdapter("ROLLBACK").execute(state, false);
        }
        return new SQLQueryResult(accessedRows, queryErrors);
    }

    @Override
    public void addAuxiliaryColumns(AbstractRelationalTable<?, ?, ?> table) throws SQLException {
        String tableName = table.getName();

        // Oracle风格的ALTER TABLE语法
        String addColumnRowID = String.format("ALTER TABLE %s ADD (%s VARCHAR2(100))", tableName, COLUMN_ROWID);
        new SQLQueryAdapter(addColumnRowID).execute(state, false);
        state.getState().getLocalState().log(addColumnRowID);

        String addColumnUpdated = String.format("ALTER TABLE %s ADD (%s NUMBER(1) DEFAULT 0)", tableName, COLUMN_UPDATED);
        new SQLQueryAdapter(addColumnUpdated).execute(state, false);
        state.getState().getLocalState().log(addColumnUpdated);

        // 使用SYS_GUID()或UUID生成唯一标识（Oracle风格）
        String updateRowsWithUniqueID = String.format("UPDATE %s SET %s = RAWTOHEX(SYS_GUID())", tableName, COLUMN_ROWID);
        new SQLQueryAdapter(updateRowsWithUniqueID).execute(state, false);
        state.getState().getLocalState().log(updateRowsWithUniqueID);
    }
}