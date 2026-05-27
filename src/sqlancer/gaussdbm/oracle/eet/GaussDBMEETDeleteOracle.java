package sqlancer.gaussdbm.oracle.eet;

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
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

/**
 * GaussDB-M EET DELETE Oracle — tests DELETE by comparing row-state changes
 * between original and EET-transformed WHERE conditions.
 */
public class GaussDBMEETDeleteOracle
        extends EETDMLOracleBase<GaussDBMGlobalState, GaussDBExpression, AbstractTables<GaussDBTable, GaussDBColumn>, GaussDBColumn> {

    private GaussDBTable targetTable;

    public GaussDBMEETDeleteOracle(GaussDBMGlobalState state) {
        super(state, null);
    }

    @Override
    protected AbstractTables<GaussDBTable, GaussDBColumn> getRandomTables() {
        return TestOracleUtils.getRandomTableNonEmptyTables(state.getSchema());
    }

    @Override
    protected Object createGenerator(AbstractTables<GaussDBTable, GaussDBColumn> tables) {
        return new GaussDBMExpressionGenerator(state).setTablesAndColumns(tables);
    }

    @Override
    protected Object createTransformer(Object gen, AbstractTables<GaussDBTable, GaussDBColumn> tables) {
        GaussDBMExpressionGenerator expressionGen = (GaussDBMExpressionGenerator) gen;
        return new GaussDBMEETTransformer(expressionGen, tables);
    }

    @Override
    protected EETQueryExecutor createDefaultExecutor() {
        return new GaussDBMEETDefaultQueryExecutor(state);
    }

    @Override
    protected SQLQueryAdapter generateDML(GaussDBMGlobalState state, Object gen,
            AbstractTables<GaussDBTable, GaussDBColumn> tables) {
        GaussDBMExpressionGenerator expressionGen = (GaussDBMExpressionGenerator) gen;
        List<GaussDBTable> tableList = tables.getTables();
        targetTable = Randomly.fromList(tableList);

        GaussDBExpression whereCondition = expressionGen.generateExpression();
        expressionGen.setLastGeneratedExpression(whereCondition);

        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ");
        sb.append(targetTable.getName());
        sb.append(" WHERE ");
        sb.append(GaussDBToStringVisitor.asString(whereCondition));

        ExpectedErrors errors = new ExpectedErrors();
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    @Override
    protected SQLQueryAdapter generateTransformedDML(GaussDBMGlobalState state, Object gen,
            AbstractTables<GaussDBTable, GaussDBColumn> tables, SQLQueryAdapter original) {
        GaussDBMExpressionGenerator expressionGen = (GaussDBMExpressionGenerator) gen;
        GaussDBMEETTransformer transformer = (GaussDBMEETTransformer) createTransformer(gen, tables);

        GaussDBExpression whereCondition = expressionGen.getLastGeneratedExpression();
        if (whereCondition == null) {
            throw new IgnoreMeException();
        }
        GaussDBExpression transformedWhere = transformer.transformExpression(whereCondition);

        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ");
        sb.append(targetTable.getName());
        sb.append(" WHERE ");
        sb.append(GaussDBToStringVisitor.asString(transformedWhere));

        ExpectedErrors errors = new ExpectedErrors();
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
        for (GaussDBColumn c : targetTable.getColumns()) {
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