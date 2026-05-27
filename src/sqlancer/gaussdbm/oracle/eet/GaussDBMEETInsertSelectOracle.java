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
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

/**
 * GaussDB-M EET INSERT-SELECT Oracle — tests INSERT INTO target SELECT ... FROM source WHERE predicate
 * by comparing inserted rows between original and EET-transformed WHERE conditions.
 */
public class GaussDBMEETInsertSelectOracle
        extends EETDMLOracleBase<GaussDBMGlobalState, GaussDBExpression, AbstractTables<GaussDBTable, GaussDBColumn>, GaussDBColumn> {

    private GaussDBTable targetTable;
    private GaussDBTable sourceTable;
    private GaussDBMExpressionGenerator selectGen;
    private GaussDBSelect originalSelect;

    public GaussDBMEETInsertSelectOracle(GaussDBMGlobalState state) {
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
        List<GaussDBTable> tableList = tables.getTables();
        if (tableList.size() < 2) {
            throw new IgnoreMeException();
        }

        targetTable = Randomly.fromList(tableList);
        sourceTable = Randomly.fromList(tableList);
        while (sourceTable == targetTable && tableList.size() > 1) {
            sourceTable = Randomly.fromList(tableList);
        }

        selectGen = new GaussDBMExpressionGenerator(state).setTablesAndColumns(tables);
        originalSelect = generateSelectFromSource(sourceTable, selectGen);

        GaussDBExpression whereCondition = originalSelect.getWhereClause();
        if (whereCondition == null) {
            whereCondition = selectGen.generateExpression();
            originalSelect.setWhereClause(whereCondition);
        }
        selectGen.setLastGeneratedExpression(whereCondition);

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ");
        sb.append(targetTable.getName());
        sb.append(" (");
        List<GaussDBColumn> targetColumns = targetTable.getColumns();
        for (int i = 0; i < targetColumns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(targetColumns.get(i).getName());
        }
        sb.append(") ");
        sb.append(GaussDBToStringVisitor.asString(originalSelect));

        ExpectedErrors errors = new ExpectedErrors();
        // Add GaussDB-M specific errors if needed
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    @Override
    protected SQLQueryAdapter generateTransformedDML(GaussDBMGlobalState state, Object gen,
            AbstractTables<GaussDBTable, GaussDBColumn> tables, SQLQueryAdapter original) {
        if (selectGen == null || originalSelect == null) {
            throw new IgnoreMeException();
        }

        GaussDBMEETTransformer transformer = (GaussDBMEETTransformer) createTransformer(selectGen, tables);

        GaussDBExpression whereCondition = selectGen.getLastGeneratedExpression();
        if (whereCondition == null) {
            throw new IgnoreMeException();
        }

        GaussDBExpression transformedWhere = transformer.transformExpression(whereCondition);

        GaussDBSelect transformedSelect = shallowCopySelect(originalSelect);
        transformedSelect.setWhereClause(transformedWhere);

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ");
        sb.append(targetTable.getName());
        sb.append(" (");
        List<GaussDBColumn> targetColumns = targetTable.getColumns();
        for (int i = 0; i < targetColumns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(targetColumns.get(i).getName());
        }
        sb.append(") ");
        sb.append(GaussDBToStringVisitor.asString(transformedSelect));

        ExpectedErrors errors = new ExpectedErrors();
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    private GaussDBSelect generateSelectFromSource(GaussDBTable source, GaussDBMExpressionGenerator gen) {
        GaussDBSelect select = new GaussDBSelect();
        select.setFromList(List.of(GaussDBTableReference.create(source)));
        select.setFetchColumns(gen.generateExpressions(source.getColumns().size()));
        select.setWhereClause(gen.generateExpression());
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

    private GaussDBSelect shallowCopySelect(GaussDBSelect s) {
        GaussDBSelect copy = new GaussDBSelect();
        copy.setFromList(new ArrayList<>(s.getFromList()));
        copy.setFetchColumns(new ArrayList<>(s.getFetchColumns()));
        copy.setWhereClause(s.getWhereClause());
        copy.setGroupByExpressions(new ArrayList<>(s.getGroupByExpressions()));
        copy.setHavingClause(s.getHavingClause());
        copy.setOrderByClauses(new ArrayList<>(s.getOrderByClauses()));
        copy.setLimitClause(s.getLimitClause());
        return copy;
    }
}