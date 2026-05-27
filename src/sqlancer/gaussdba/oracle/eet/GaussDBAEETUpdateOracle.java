package sqlancer.gaussdba.oracle.eet;

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
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;
import sqlancer.gaussdba.GaussDBAToStringVisitor;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.gen.GaussDBAExpressionGenerator;

/**
 * GaussDB-A EET UPDATE Oracle — tests UPDATE by comparing row-state changes
 * between original and EET-transformed WHERE conditions.
 */
public class GaussDBAEETUpdateOracle
        extends EETDMLOracleBase<GaussDBAGlobalState, GaussDBAExpression, AbstractTables<GaussDBATable, GaussDBAColumn>, GaussDBAColumn> {

    private GaussDBATable targetTable;

    public GaussDBAEETUpdateOracle(GaussDBAGlobalState state) {
        super(state, null);
    }

    @Override
    protected AbstractTables<GaussDBATable, GaussDBAColumn> getRandomTables() {
        return TestOracleUtils.getRandomTableNonEmptyTables(state.getSchema());
    }

    @Override
    protected Object createGenerator(AbstractTables<GaussDBATable, GaussDBAColumn> tables) {
        return new GaussDBAExpressionGenerator(state).setTablesAndColumns(tables);
    }

    @Override
    protected Object createTransformer(Object gen, AbstractTables<GaussDBATable, GaussDBAColumn> tables) {
        GaussDBAExpressionGenerator expressionGen = (GaussDBAExpressionGenerator) gen;
        return new GaussDBAEETTransformer(expressionGen, tables);
    }

    @Override
    protected EETQueryExecutor createDefaultExecutor() {
        return new GaussDBAEETDefaultQueryExecutor(state);
    }

    @Override
    protected SQLQueryAdapter generateDML(GaussDBAGlobalState state, Object gen,
            AbstractTables<GaussDBATable, GaussDBAColumn> tables) {
        GaussDBAExpressionGenerator expressionGen = (GaussDBAExpressionGenerator) gen;
        List<GaussDBATable> tableList = tables.getTables();
        targetTable = Randomly.fromList(tableList);

        GaussDBAExpression whereCondition = expressionGen.generatePredicate();
        String whereStr = GaussDBAToStringVisitor.asString(whereCondition);

        expressionGen.setLastGeneratedExpression(whereCondition);

        List<GaussDBAColumn> columns = targetTable.getRandomNonEmptyColumnSubset();
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
            sb.append(GaussDBAToStringVisitor.asString(expressionGen.generateExpression(0)));
        }
        sb.append(" WHERE ");
        sb.append(whereStr);

        ExpectedErrors errors = new ExpectedErrors();
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    @Override
    protected SQLQueryAdapter generateTransformedDML(GaussDBAGlobalState state, Object gen,
            AbstractTables<GaussDBATable, GaussDBAColumn> tables, SQLQueryAdapter original) {
        GaussDBAExpressionGenerator expressionGen = (GaussDBAExpressionGenerator) gen;
        GaussDBAEETTransformer transformer = (GaussDBAEETTransformer) createTransformer(gen, tables);

        GaussDBAExpression whereCondition = expressionGen.getLastGeneratedExpression();
        if (whereCondition == null) {
            throw new IgnoreMeException();
        }
        GaussDBAExpression transformedWhere = transformer.transformExpression(whereCondition);

        List<GaussDBAColumn> columns = targetTable.getRandomNonEmptyColumnSubset();
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
            sb.append(GaussDBAToStringVisitor.asString(expressionGen.generateExpression(0)));
        }
        sb.append(" WHERE ");
        sb.append(GaussDBAToStringVisitor.asString(transformedWhere));

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
        for (GaussDBAColumn c : targetTable.getColumns()) {
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