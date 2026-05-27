package sqlancer.postgres.oracle.ext.eet;

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
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresVisitor;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.gen.PostgresCommon;
import sqlancer.postgres.gen.PostgresExpressionGenerator;

/**
 * PostgreSQL EET UPDATE Oracle — tests UPDATE by comparing row-state changes
 * between original and EET-transformed WHERE conditions.
 */
public class PostgresEETUpdateOracle
        extends EETDMLOracleBase<PostgresGlobalState, PostgresExpression, AbstractTables<PostgresTable, PostgresColumn>, PostgresColumn> {

    private PostgresTable targetTable;

    public PostgresEETUpdateOracle(PostgresGlobalState state) {
        super(state, null);
    }

    @Override
    protected AbstractTables<PostgresTable, PostgresColumn> getRandomTables() {
        return TestOracleUtils.getRandomTableNonEmptyTables(state.getSchema());
    }

    @Override
    protected Object createGenerator(AbstractTables<PostgresTable, PostgresColumn> tables) {
        return new PostgresExpressionGenerator(state).setColumns(tables.getColumns());
    }

    @Override
    protected Object createTransformer(Object gen, AbstractTables<PostgresTable, PostgresColumn> tables) {
        PostgresExpressionGenerator expressionGen = (PostgresExpressionGenerator) gen;
        return new PostgresEETTransformer(expressionGen, tables);
    }

    @Override
    protected EETQueryExecutor createDefaultExecutor() {
        return new PostgresEETDefaultQueryExecutor(state);
    }

    @Override
    protected SQLQueryAdapter generateDML(PostgresGlobalState state, Object gen,
            AbstractTables<PostgresTable, PostgresColumn> tables) {
        PostgresExpressionGenerator expressionGen = (PostgresExpressionGenerator) gen;
        List<PostgresTable> tableList = tables.getTables();
        targetTable = Randomly.fromList(tableList);

        PostgresExpression whereCondition = expressionGen.generateExpression(0);
        expressionGen.setLastGeneratedExpression(whereCondition);

        List<PostgresColumn> columns = targetTable.getRandomNonEmptyColumnSubset();
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ");
        sb.append(targetTable.getName());
        sb.append(" SET ");
        for (int i = 0; i < columns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(columns.get(i).getName());
            sb.append(" = ");
            sb.append(PostgresVisitor.asString(expressionGen.generateExpression(0)));
        }
        sb.append(" WHERE ");
        sb.append(PostgresVisitor.asString(whereCondition));

        ExpectedErrors errors = ExpectedErrors.newErrors()
                .withRegex(PostgresCommon.getCommonExpressionRegexErrors())
                .with(PostgresCommon.getCommonExpressionErrors())
                .with(PostgresCommon.getCommonFetchErrors()).build();
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    @Override
    protected SQLQueryAdapter generateTransformedDML(PostgresGlobalState state, Object gen,
            AbstractTables<PostgresTable, PostgresColumn> tables, SQLQueryAdapter original) {
        PostgresExpressionGenerator expressionGen = (PostgresExpressionGenerator) gen;
        PostgresEETTransformer transformer = (PostgresEETTransformer) createTransformer(gen, tables);

        PostgresExpression whereCondition = expressionGen.getLastGeneratedExpression();
        if (whereCondition == null) {
            throw new IgnoreMeException();
        }
        PostgresExpression transformedWhere = transformer.transformExpression(whereCondition);

        List<PostgresColumn> columns = targetTable.getRandomNonEmptyColumnSubset();
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ");
        sb.append(targetTable.getName());
        sb.append(" SET ");
        for (int i = 0; i < columns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(columns.get(i).getName());
            sb.append(" = ");
            sb.append(PostgresVisitor.asString(expressionGen.generateExpression(0)));
        }
        sb.append(" WHERE ");
        sb.append(PostgresVisitor.asString(transformedWhere));

        ExpectedErrors errors = ExpectedErrors.newErrors()
                .withRegex(PostgresCommon.getCommonExpressionRegexErrors())
                .with(PostgresCommon.getCommonExpressionErrors())
                .with(PostgresCommon.getCommonFetchErrors()).build();
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    @Override
    protected List<List<String>> readTableContent(String tableName) throws SQLException {
        return executor.executeQuery("SELECT * FROM " + tableName);
    }

    @Override
    protected void restoreTable(String tableName, List<List<String>> snapshot) throws SQLException {
        state.getConnection().createStatement().execute("DELETE FROM " + tableName);
        if (snapshot.isEmpty()) {
            return;
        }
        List<String> columnNames = new ArrayList<>();
        for (PostgresColumn c : targetTable.getColumns()) {
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
}