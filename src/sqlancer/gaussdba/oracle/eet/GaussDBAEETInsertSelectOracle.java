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
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.ast.GaussDBATableReference;
import sqlancer.gaussdba.gen.GaussDBAExpressionGenerator;

/**
 * GaussDB-A EET INSERT-SELECT Oracle — tests INSERT INTO target SELECT ... FROM source WHERE predicate
 * by comparing inserted rows between original and EET-transformed WHERE conditions.
 */
public class GaussDBAEETInsertSelectOracle
        extends EETDMLOracleBase<GaussDBAGlobalState, GaussDBAExpression, AbstractTables<GaussDBATable, GaussDBAColumn>, GaussDBAColumn> {

    private GaussDBATable targetTable;
    private GaussDBATable sourceTable;
    private GaussDBAExpressionGenerator selectGen;
    private GaussDBASelect originalSelect;

    public GaussDBAEETInsertSelectOracle(GaussDBAGlobalState state) {
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
        List<GaussDBATable> tableList = tables.getTables();
        if (tableList.size() < 2) {
            throw new IgnoreMeException();
        }

        targetTable = Randomly.fromList(tableList);
        sourceTable = Randomly.fromList(tableList);
        while (sourceTable == targetTable && tableList.size() > 1) {
            sourceTable = Randomly.fromList(tableList);
        }

        selectGen = new GaussDBAExpressionGenerator(state).setTablesAndColumns(tables);
        originalSelect = generateSelectFromSource(sourceTable, selectGen);

        GaussDBAExpression whereCondition = originalSelect.getWhereClause();
        if (whereCondition == null) {
            whereCondition = selectGen.generatePredicate();
            originalSelect.setWhereClause(whereCondition);
        }
        selectGen.setLastGeneratedExpression(whereCondition);

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ");
        sb.append(targetTable.getName());
        sb.append(" (");
        List<GaussDBAColumn> targetColumns = targetTable.getColumns();
        for (int i = 0; i < targetColumns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(targetColumns.get(i).getName());
        }
        sb.append(") ");
        sb.append(GaussDBAToStringVisitor.asString(originalSelect));

        ExpectedErrors errors = new ExpectedErrors();
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    @Override
    protected SQLQueryAdapter generateTransformedDML(GaussDBAGlobalState state, Object gen,
            AbstractTables<GaussDBATable, GaussDBAColumn> tables, SQLQueryAdapter original) {
        if (selectGen == null || originalSelect == null) {
            throw new IgnoreMeException();
        }

        GaussDBAEETTransformer transformer = (GaussDBAEETTransformer) createTransformer(selectGen, tables);

        GaussDBAExpression whereCondition = selectGen.getLastGeneratedExpression();
        if (whereCondition == null) {
            throw new IgnoreMeException();
        }

        GaussDBAExpression transformedWhere = transformer.transformExpression(whereCondition);

        GaussDBASelect transformedSelect = shallowCopySelect(originalSelect);
        transformedSelect.setWhereClause(transformedWhere);

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ");
        sb.append(targetTable.getName());
        sb.append(" (");
        List<GaussDBAColumn> targetColumns = targetTable.getColumns();
        for (int i = 0; i < targetColumns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(targetColumns.get(i).getName());
        }
        sb.append(") ");
        sb.append(GaussDBAToStringVisitor.asString(transformedSelect));

        ExpectedErrors errors = new ExpectedErrors();
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    private GaussDBASelect generateSelectFromSource(GaussDBATable source, GaussDBAExpressionGenerator gen) {
        GaussDBASelect select = new GaussDBASelect();
        select.setFromList(List.of(new GaussDBATableReference(source)));
        select.setFetchColumns(gen.generateExpressions(source.getColumns().size()));
        select.setWhereClause(gen.generatePredicate());
        return select;
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

    private GaussDBASelect shallowCopySelect(GaussDBASelect s) {
        GaussDBASelect copy = new GaussDBASelect();
        copy.setFromList(new ArrayList<>(s.getFromList()));
        copy.setFetchColumns(new ArrayList<>(s.getFetchColumns()));
        copy.setWhereClause(s.getWhereClause());
        copy.setGroupByExpressions(new ArrayList<>(s.getGroupByExpressions()));
        copy.setHavingClause(s.getHavingClause());
        copy.setOrderByClauses(new ArrayList<>(s.getOrderByClauses()));
        return copy;
    }
}