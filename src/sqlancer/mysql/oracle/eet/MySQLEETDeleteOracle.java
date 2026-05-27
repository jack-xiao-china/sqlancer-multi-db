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
import sqlancer.mysql.gen.MySQLExpressionGenerator;

/**
 * MySQL EET DELETE Oracle — tests DELETE by comparing row-state changes
 * between original and EET-transformed WHERE conditions.
 */
public class MySQLEETDeleteOracle
        extends EETDMLOracleBase<MySQLGlobalState, MySQLExpression, AbstractTables<MySQLTable, MySQLColumn>, MySQLColumn> {

    private MySQLTable targetTable;

    public MySQLEETDeleteOracle(MySQLGlobalState state) {
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
        MySQLExpressionGenerator expressionGen = (MySQLExpressionGenerator) gen;
        List<MySQLTable> tableList = tables.getTables();
        targetTable = Randomly.fromList(tableList);

        MySQLExpression whereCondition = expressionGen.generateExpression();
        expressionGen.setLastGeneratedExpression(whereCondition);

        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ");
        sb.append(targetTable.getName());
        sb.append(" WHERE ");
        sb.append(MySQLVisitor.asString(whereCondition));

        ExpectedErrors errors = new ExpectedErrors();
        MySQLErrors.addExpressionErrors(errors);
        errors.add("doesn't have this option");
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    @Override
    protected SQLQueryAdapter generateTransformedDML(MySQLGlobalState state, Object gen,
            AbstractTables<MySQLTable, MySQLColumn> tables, SQLQueryAdapter original) {
        MySQLExpressionGenerator expressionGen = (MySQLExpressionGenerator) gen;
        MySQLEETTransformer transformer = (MySQLEETTransformer) createTransformer(gen, tables);

        MySQLExpression whereCondition = expressionGen.getLastGeneratedExpression();
        if (whereCondition == null) {
            throw new IgnoreMeException();
        }
        MySQLExpression transformedWhere = transformer.transformExpression(whereCondition);

        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ");
        sb.append(targetTable.getName());
        sb.append(" WHERE ");
        sb.append(MySQLVisitor.asString(transformedWhere));

        ExpectedErrors errors = new ExpectedErrors();
        MySQLErrors.addExpressionErrors(errors);
        errors.add("doesn't have this option");
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
}