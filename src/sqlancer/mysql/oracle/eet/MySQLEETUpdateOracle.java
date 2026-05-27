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
 * MySQL EET DML Oracle — tests UPDATE and DELETE by comparing row-state changes
 * between original and EET-transformed WHERE conditions.
 */
public class MySQLEETUpdateOracle
        extends EETDMLOracleBase<MySQLGlobalState, MySQLExpression, AbstractTables<MySQLTable, MySQLColumn>, MySQLColumn> {

    private MySQLTable targetTable;

    public MySQLEETUpdateOracle(MySQLGlobalState state) {
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
        String whereStr = MySQLVisitor.asString(whereCondition);

        // Store the WHERE AST for later transformation
        expressionGen.setLastGeneratedExpression(whereCondition);

        List<MySQLColumn> columns = targetTable.getRandomNonEmptyColumnSubset();
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
            sb.append(MySQLVisitor.asString(expressionGen.generateExpression()));
        }
        sb.append(" WHERE ");
        sb.append(whereStr);

        ExpectedErrors errors = new ExpectedErrors();
        MySQLErrors.addInsertUpdateErrors(errors);
        MySQLErrors.addExpressionErrors(errors);
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

        // Re-generate the UPDATE with the transformed WHERE
        List<MySQLColumn> columns = targetTable.getRandomNonEmptyColumnSubset();
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
            sb.append(MySQLVisitor.asString(expressionGen.generateExpression()));
        }
        sb.append(" WHERE ");
        sb.append(MySQLVisitor.asString(transformedWhere));

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
}