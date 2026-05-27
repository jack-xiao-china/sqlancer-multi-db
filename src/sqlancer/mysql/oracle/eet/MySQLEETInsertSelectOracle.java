package sqlancer.mysql.oracle.eet;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.oracle.TestOracleUtils;
import sqlancer.common.oracle.eet.EETDMLOracleBase;
import sqlancer.common.oracle.eet.EETQueryExecutor;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.schema.AbstractTables;
import sqlancer.mysql.MySQLErrors;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.MySQLVisitor;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

/**
 * MySQL EET INSERT-SELECT Oracle — tests INSERT INTO target SELECT ... FROM source WHERE predicate
 * by comparing inserted rows between original and EET-transformed WHERE conditions.
 *
 * Flow:
 * 1. Snapshot target table before
 * 2. Execute original INSERT-SELECT
 * 3. Snapshot target table after → compute inserted rows
 * 4. Restore target table (delete inserted rows)
 * 5. Execute transformed INSERT-SELECT (WHERE condition transformed by EET)
 * 6. Snapshot target table after → compute inserted rows
 * 7. Compare inserted-row multisets (original vs transformed)
 */
public class MySQLEETInsertSelectOracle
        extends EETDMLOracleBase<MySQLGlobalState, MySQLExpression, AbstractTables<MySQLTable, MySQLColumn>, MySQLColumn> {

    private MySQLTable targetTable;
    private MySQLTable sourceTable;
    private MySQLExpressionGenerator selectGen;
    private MySQLSelect originalSelect;

    public MySQLEETInsertSelectOracle(MySQLGlobalState state) {
        super(state, null);
    }

    @Override
    protected AbstractTables<MySQLTable, MySQLColumn> getRandomTables() {
        return TestOracleUtils.getRandomTableNonEmptyTables(state.getSchema());
    }

    @Override
    protected Object createGenerator(AbstractTables<MySQLTable, MySQLColumn> tables) {
        return new MySQLExpressionGenerator(state).setTablesAndColumns(tables);
    }

    @Override
    protected Object createTransformer(Object gen, AbstractTables<MySQLTable, MySQLColumn> tables) {
        MySQLExpressionGenerator expressionGen = (MySQLExpressionGenerator) gen;
        return new MySQLEETTransformer(expressionGen, tables);
    }

    @Override
    protected EETQueryExecutor createDefaultExecutor() {
        return new EETDefaultQueryExecutor(state);
    }

    @Override
    protected SQLQueryAdapter generateDML(MySQLGlobalState state, Object gen,
            AbstractTables<MySQLTable, MySQLColumn> tables) {
        List<MySQLTable> tableList = tables.getTables();
        if (tableList.size() < 2) {
            // Need at least 2 tables: one as source, one as target
            throw new IgnoreMeException();
        }

        // Select target and source tables
        targetTable = Randomly.fromList(tableList);
        sourceTable = Randomly.fromList(tableList);
        // Ensure source != target (avoid self-insert which might cause issues)
        while (sourceTable == targetTable && tableList.size() > 1) {
            sourceTable = Randomly.fromList(tableList);
        }

        // Generate SELECT subquery with WHERE condition
        selectGen = new MySQLExpressionGenerator(state).setTablesAndColumns(tables);
        originalSelect = generateSelectFromSource(sourceTable, selectGen);

        // Store the WHERE condition for transformation
        MySQLExpression whereCondition = originalSelect.getWhereClause();
        if (whereCondition == null) {
            // Add a WHERE clause if none exists
            whereCondition = selectGen.generateExpression();
            originalSelect.setWhereClause(whereCondition);
        }
        selectGen.setLastGeneratedExpression(whereCondition);

        // Build INSERT-SELECT statement
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ");
        sb.append(targetTable.getName());
        sb.append(" (");
        List<MySQLColumn> targetColumns = targetTable.getColumns();
        for (int i = 0; i < targetColumns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(targetColumns.get(i).getName());
        }
        sb.append(") ");
        sb.append(MySQLVisitor.asString(originalSelect));

        ExpectedErrors errors = new ExpectedErrors();
        MySQLErrors.addInsertUpdateErrors(errors);
        MySQLErrors.addExpressionErrors(errors);
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    @Override
    protected SQLQueryAdapter generateTransformedDML(MySQLGlobalState state, Object gen,
            AbstractTables<MySQLTable, MySQLColumn> tables, SQLQueryAdapter original) {
        if (selectGen == null || originalSelect == null) {
            throw new IgnoreMeException();
        }

        MySQLEETTransformer transformer = (MySQLEETTransformer) createTransformer(selectGen, tables);

        MySQLExpression whereCondition = selectGen.getLastGeneratedExpression();
        if (whereCondition == null) {
            throw new IgnoreMeException();
        }

        MySQLExpression transformedWhere = transformer.transformExpression(whereCondition);

        // Build transformed SELECT
        MySQLSelect transformedSelect = shallowCopySelect(originalSelect);
        transformedSelect.setWhereClause(transformedWhere);

        // Build INSERT-SELECT with transformed WHERE
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ");
        sb.append(targetTable.getName());
        sb.append(" (");
        List<MySQLColumn> targetColumns = targetTable.getColumns();
        for (int i = 0; i < targetColumns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(targetColumns.get(i).getName());
        }
        sb.append(") ");
        sb.append(MySQLVisitor.asString(transformedSelect));

        ExpectedErrors errors = new ExpectedErrors();
        MySQLErrors.addInsertUpdateErrors(errors);
        MySQLErrors.addExpressionErrors(errors);
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    @Override
    protected List<List<String>> readTableContent(String tableName) throws SQLException {
        return executor.executeQuery("SELECT * FROM " + tableName);
    }

    @Override
    protected void restoreTable(String tableName, List<List<String>> snapshot) throws SQLException {
        // Delete all current rows, then re-insert from snapshot
        state.getConnection().createStatement().execute("DELETE FROM " + tableName);
        if (snapshot.isEmpty()) {
            return;
        }
        // Reconstruct column list from target table
        List<String> columnNames = new ArrayList<>();
        for (MySQLColumn c : targetTable.getColumns()) {
            columnNames.add(c.getName());
        }
        String colList = String.join(", ", columnNames);
        for (List<String> row : snapshot) {
            StringBuilder insertSb = new StringBuilder();
            insertSb.append("INSERT INTO ").append(tableName).append(" (").append(colList).append(") VALUES (");
            for (int i = 0; i < row.size(); i++) {
                if (i != 0) {
                    insertSb.append(", ");
                }
                String val = row.get(i);
                if (val == null || val.equals("null")) {
                    insertSb.append("NULL");
                } else {
                    insertSb.append("'").append(val.replace("'", "''")).append("'");
                }
            }
            insertSb.append(")");
            state.getConnection().createStatement().execute(insertSb.toString());
        }
    }

    @Override
    protected String getTargetTableName(SQLQueryAdapter dml) {
        return targetTable != null ? targetTable.getName() : null;
    }

    private MySQLSelect generateSelectFromSource(MySQLTable source, MySQLExpressionGenerator gen) {
        MySQLSelect select = new MySQLSelect();
        MySQLTableReference tableRef = new MySQLTableReference(source);
        select.setFromList(List.of(tableRef));
        select.setFetchColumns(gen.generateExpressions(source.getColumns().size()));
        select.setWhereClause(gen.generateExpression());
        return select;
    }

    private MySQLSelect shallowCopySelect(MySQLSelect s) {
        MySQLSelect copy = new MySQLSelect();
        copy.setFromOptions(s.getFromOptions());
        copy.setFetchColumns(new ArrayList<>(s.getFetchColumns()));
        copy.setFromList(new ArrayList<>(s.getFromList()));
        copy.setJoinList(new ArrayList<>(s.getJoinList()));
        copy.setWhereClause(s.getWhereClause());
        copy.setGroupByExpressions(new ArrayList<>(s.getGroupByExpressions()));
        copy.setHavingClause(s.getHavingClause());
        copy.setOrderByClauses(new ArrayList<>(s.getOrderByClauses()));
        copy.setLimitClause(s.getLimitClause());
        copy.setOffsetClause(s.getOffsetClause());
        return copy;
    }
}