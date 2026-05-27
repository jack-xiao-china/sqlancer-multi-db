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
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.ast.PostgresTableReference;
import sqlancer.postgres.gen.PostgresCommon;
import sqlancer.postgres.gen.PostgresExpressionGenerator;

/**
 * PostgreSQL EET INSERT-SELECT Oracle — tests INSERT INTO target SELECT ... FROM source WHERE predicate
 * by comparing inserted rows between original and EET-transformed WHERE conditions.
 */
public class PostgresEETInsertSelectOracle
        extends EETDMLOracleBase<PostgresGlobalState, PostgresExpression, AbstractTables<PostgresTable, PostgresColumn>, PostgresColumn> {

    private PostgresTable targetTable;
    private PostgresTable sourceTable;
    private PostgresExpressionGenerator selectGen;
    private PostgresSelect originalSelect;

    public PostgresEETInsertSelectOracle(PostgresGlobalState state) {
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
        List<PostgresTable> tableList = tables.getTables();
        if (tableList.size() < 2) {
            throw new IgnoreMeException();
        }

        targetTable = Randomly.fromList(tableList);
        sourceTable = Randomly.fromList(tableList);
        while (sourceTable == targetTable && tableList.size() > 1) {
            sourceTable = Randomly.fromList(tableList);
        }

        selectGen = new PostgresExpressionGenerator(state).setColumns(tables.getColumns());
        originalSelect = generateSelectFromSource(sourceTable, selectGen);

        PostgresExpression whereCondition = originalSelect.getWhereClause();
        if (whereCondition == null) {
            whereCondition = selectGen.generateExpression(0);
            originalSelect.setWhereClause(whereCondition);
        }
        selectGen.setLastGeneratedExpression(whereCondition);

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ");
        sb.append(targetTable.getName());
        sb.append(" (");
        List<PostgresColumn> targetColumns = targetTable.getColumns();
        for (int i = 0; i < targetColumns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(targetColumns.get(i).getName());
        }
        sb.append(") ");
        sb.append(PostgresVisitor.asString(originalSelect));

        ExpectedErrors errors = ExpectedErrors.newErrors()
                .withRegex(PostgresCommon.getCommonExpressionRegexErrors())
                .with(PostgresCommon.getCommonExpressionErrors())
                .with(PostgresCommon.getCommonFetchErrors()).build();
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    @Override
    protected SQLQueryAdapter generateTransformedDML(PostgresGlobalState state, Object gen,
            AbstractTables<PostgresTable, PostgresColumn> tables, SQLQueryAdapter original) {
        if (selectGen == null || originalSelect == null) {
            throw new IgnoreMeException();
        }

        PostgresEETTransformer transformer = (PostgresEETTransformer) createTransformer(selectGen, tables);

        PostgresExpression whereCondition = selectGen.getLastGeneratedExpression();
        if (whereCondition == null) {
            throw new IgnoreMeException();
        }

        PostgresExpression transformedWhere = transformer.transformExpression(whereCondition);

        PostgresSelect transformedSelect = shallowCopySelect(originalSelect);
        transformedSelect.setWhereClause(transformedWhere);

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ");
        sb.append(targetTable.getName());
        sb.append(" (");
        List<PostgresColumn> targetColumns = targetTable.getColumns();
        for (int i = 0; i < targetColumns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(targetColumns.get(i).getName());
        }
        sb.append(") ");
        sb.append(PostgresVisitor.asString(transformedSelect));

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

    private PostgresSelect generateSelectFromSource(PostgresTable source, PostgresExpressionGenerator gen) {
        PostgresSelect select = new PostgresSelect();
        PostgresTableReference tableRef = new PostgresTableReference(source);
        select.setFromList(List.of(tableRef));
        select.setFetchColumns(gen.generateExpressions(source.getColumns().size()));
        select.setWhereClause(gen.generateExpression(0));
        return select;
    }

    private PostgresSelect shallowCopySelect(PostgresSelect s) {
        PostgresSelect copy = new PostgresSelect();
        copy.setSelectOption(s.getSelectOption());
        copy.setFetchColumns(new ArrayList<>(s.getFetchColumns()));
        copy.setFromList(new ArrayList<>(s.getFromList()));
        copy.setJoinClauses(new ArrayList<>(s.getJoinClauses()));
        copy.setWhereClause(s.getWhereClause());
        copy.setGroupByExpressions(new ArrayList<>(s.getGroupByExpressions()));
        copy.setHavingClause(s.getHavingClause());
        copy.setOrderByClauses(new ArrayList<>(s.getOrderByClauses()));
        copy.setLimitClause(s.getLimitClause());
        copy.setOffsetClause(s.getOffsetClause());
        return copy;
    }
}