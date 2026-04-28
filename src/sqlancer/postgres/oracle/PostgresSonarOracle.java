package sqlancer.postgres.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.common.oracle.NoRECBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.postgres.gen.PostgresCommon;
import sqlancer.postgres.PostgresCompoundDataType;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresDataType;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresSchema.PostgresTables;
import sqlancer.postgres.PostgresVisitor;
import sqlancer.postgres.ast.PostgresBinaryArithmeticOperation;
import sqlancer.postgres.ast.PostgresBinaryArithmeticOperation.PostgresBinaryOperator;
import sqlancer.postgres.ast.PostgresColumnValue;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.ast.PostgresJoin;
import sqlancer.postgres.ast.PostgresJoin.PostgresJoinType;
import sqlancer.postgres.ast.PostgresPostfixText;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.ast.PostgresSelect.PostgresFromTable;
import sqlancer.postgres.ast.PostgresText;
import sqlancer.postgres.gen.PostgresExpressionGenerator;

/**
 * PostgreSQL SONAR Oracle implementation.
 *
 * SONAR (Select Optimization N-gram Analysis Runtime) is a testing technique that compares the results of optimized
 * queries (with WHERE clause filtering) against unoptimized queries (using flag-based filtering in outer query). This
 * helps detect optimization bugs where the database optimizer incorrectly transforms queries.
 *
 * The oracle generates: 1. Optimized query: SELECT ... FROM ... WHERE condition 2. Unoptimized query: SELECT ... FROM
 * (SELECT ..., condition IS TRUE AS flag FROM ...) WHERE flag
 *
 * If the result sets differ, a bug is detected.
 */
public class PostgresSonarOracle extends NoRECBase<PostgresGlobalState> implements TestOracle<PostgresGlobalState> {

    private final PostgresSchema s;

    public PostgresSonarOracle(PostgresGlobalState globalState) {
        super(globalState);
        this.s = globalState.getSchema();
        PostgresCommon.addCommonExpressionErrors(errors);
        PostgresCommon.addCommonFetchErrors(errors);
    }

    List<PostgresExpression> generateFetchColumns(List<PostgresColumn> columns) {
        List<PostgresExpression> list = new ArrayList<>();
        String alias = "_" + columns.get(0).getName();
        list.add(new PostgresPostfixText(new PostgresColumnValue(columns.get(0), null), alias, null,
                PostgresCompoundDataType.create(PostgresDataType.INT)));
        return list;
    }

    @Override
    public void check() throws SQLException {
        boolean useFetchColumnsExp = Randomly.getBoolean();

        PostgresPostfixText asText = null;
        PostgresExpression whereExp = null;

        PostgresTables randomTables = s.getRandomTableNonEmptyTables();
        List<PostgresColumn> columns = randomTables.getColumns();

        List<PostgresTable> tables = randomTables.getTables();
        PostgresExpressionGenerator gen = new PostgresExpressionGenerator(state).setColumns(columns);

        List<PostgresJoin> joinStatements = getJoinStatements(state, columns, tables);
        List<PostgresExpression> fromTables = tables.stream().map(t -> new PostgresFromTable(t, Randomly.getBoolean()))
                .collect(Collectors.toList());

        PostgresSelect select = new PostgresSelect();

        if (useFetchColumnsExp) {
            PostgresExpression exp = gen.generateFetchColumnExpression(columns.get(0));
            asText = new PostgresPostfixText(exp, " AS f1", null,
                    PostgresCompoundDataType.create(PostgresDataType.INT));
            List<PostgresExpression> fetchColumns = new ArrayList<>();
            fetchColumns.add(asText);
            select.setFetchColumns(fetchColumns);
        } else {
            select.setFetchColumns(generateFetchColumns(columns));
        }

        select.setJoinClauses(joinStatements);
        select.setFromList(fromTables);

        if (useFetchColumnsExp) {
            whereExp = gen.generateWhereColumnExpression(asText, columns.get(0));
            optimizedQueryString = getOptimizedQuery(select, asText, whereExp);
        } else {
            PostgresExpression whereCondition = gen.generateExpression(PostgresDataType.BOOLEAN);
            select.setWhereClause(whereCondition);
            optimizedQueryString = PostgresVisitor.asString(select);
        }

        unoptimizedQueryString = getUnoptimizedQuery(select, useFetchColumnsExp, asText, whereExp);
        List<String> optimizedResultSet = ComparatorHelper.getResultSetFirstColumnAsString(optimizedQueryString, errors,
                state);
        List<String> unoptimizedResultSet = ComparatorHelper.getResultSetFirstColumnAsString(unoptimizedQueryString,
                errors, state);

        ComparatorHelper.assumeResultSetsAreEqual(optimizedResultSet, unoptimizedResultSet, optimizedQueryString,
                Arrays.asList(unoptimizedQueryString), state);
    }

    public static List<PostgresJoin> getJoinStatements(PostgresGlobalState globalState, List<PostgresColumn> columns,
            List<PostgresTable> tables) {
        List<PostgresJoin> joinStatements = new ArrayList<>();
        PostgresExpressionGenerator gen = new PostgresExpressionGenerator(globalState).setColumns(columns);
        for (int i = 1; i < tables.size(); i++) {
            PostgresExpression joinClause = gen.generateExpression(PostgresDataType.BOOLEAN);
            PostgresTable table = Randomly.fromList(tables);
            tables.remove(table);
            PostgresJoinType options = PostgresJoinType.getRandom();
            PostgresJoin j = new PostgresJoin(new PostgresFromTable(table, Randomly.getBoolean()), joinClause, options);
            joinStatements.add(j);
        }
        return joinStatements;
    }

    private String getOptimizedQuery(PostgresSelect select, PostgresPostfixText asText, PostgresExpression whereExp)
            throws SQLException {
        PostgresSelect outerSelect = new PostgresSelect();
        outerSelect.setFetchColumns(Arrays.asList(new PostgresText(asText.getText())));
        outerSelect.setFromList(Arrays.asList(select));
        outerSelect.setWhereClause(whereExp);
        optimizedQueryString = PostgresVisitor.asString(outerSelect);
        return optimizedQueryString;
    }

    private String getUnoptimizedQuery(PostgresSelect select, Boolean useFetchColumnsExp, PostgresPostfixText asText,
            PostgresExpression whereExp) throws SQLException {

        if (useFetchColumnsExp && whereExp != null) {
            if (whereExp instanceof PostgresText) {
                String flagName = "("
                        + PostgresVisitor.asString(((PostgresPostfixText) select.getFetchColumns().get(0)).getExpr())
                        + ")" + " IS TRUE AS flag";
                select.getFetchColumns().add(new PostgresText(flagName));
            } else {
                PostgresExpression right = null;
                if (whereExp instanceof PostgresBinaryArithmeticOperation) {
                    right = ((PostgresBinaryArithmeticOperation) whereExp).getRight();
                    PostgresBinaryOperator op = ((PostgresBinaryArithmeticOperation) whereExp).getOp();
                    PostgresBinaryArithmeticOperation fetchColumn = new PostgresBinaryArithmeticOperation(
                            ((PostgresPostfixText) select.getFetchColumns().get(0)).getExpr(), right, op);
                    String flagName = "(" + PostgresVisitor.asString(fetchColumn) + ")" + " IS TRUE AS flag";
                    select.getFetchColumns().add(new PostgresText(flagName));
                } else {
                    throw new AssertionError(whereExp.getClass().toString());
                }
            }

            PostgresSelect outerSelect = new PostgresSelect();
            outerSelect.setFetchColumns(Arrays.asList(new PostgresText(asText.getText())));
            outerSelect.setFromList(Arrays.asList(select));
            outerSelect.setWhereClause(new PostgresText("flag"));
            unoptimizedQueryString = PostgresVisitor.asString(outerSelect);
            return unoptimizedQueryString;

        } else {
            PostgresSelect outerSelect = new PostgresSelect();
            outerSelect.setFetchColumns(
                    Arrays.asList(new PostgresText(((PostgresPostfixText) select.getFetchColumns().get(0)).getText())));

            String flagName = "(" + PostgresVisitor.asString(select.getWhereClause()) + ")" + " IS TRUE AS flag";
            select.getFetchColumns().add(new PostgresText(flagName));

            select.setWhereClause(null);
            outerSelect.setFromList(Arrays.asList(select));
            outerSelect.setWhereClause(new PostgresText("flag"));
            unoptimizedQueryString = PostgresVisitor.asString(outerSelect);
            return unoptimizedQueryString;
        }
    }
}