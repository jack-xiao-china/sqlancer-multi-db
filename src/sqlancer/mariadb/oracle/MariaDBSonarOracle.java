package sqlancer.mariadb.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.common.oracle.NoRECBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.mariadb.MariaDBErrors;
import sqlancer.mariadb.MariaDBProvider;
import sqlancer.mariadb.MariaDBSchema;
import sqlancer.mariadb.MariaDBSchema.MariaDBTable;
import sqlancer.mariadb.ast.MariaDBBinaryOperator;
import sqlancer.mariadb.ast.MariaDBColumnName;
import sqlancer.mariadb.ast.MariaDBExpression;
import sqlancer.mariadb.ast.MariaDBJoin;
import sqlancer.mariadb.ast.MariaDBManuelPredicate;
import sqlancer.mariadb.ast.MariaDBPostfixText;
import sqlancer.mariadb.ast.MariaDBSelectStatement;
import sqlancer.mariadb.ast.MariaDBTableReference;
import sqlancer.mariadb.ast.MariaDBVisitor;
import sqlancer.mariadb.gen.MariaDBExpressionGenerator;

/**
 * MariaDB SONAR Oracle implementation.
 *
 * SONAR (Select Optimization N-gram Analysis Runtime) is a testing technique that compares the results of optimized
 * queries (with WHERE clause filtering) against unoptimized queries (using flag-based filtering in outer query). This
 * helps detect optimization bugs where the database optimizer incorrectly transforms queries.
 *
 * The oracle generates: 1. Optimized query: SELECT ... FROM ... WHERE condition 2. Unoptimized query: SELECT ... FROM
 * (SELECT ..., condition IS TRUE AS flag FROM ...) WHERE flag=1
 *
 * If the result sets differ, a bug is detected.
 */
public class MariaDBSonarOracle extends NoRECBase<MariaDBProvider.MariaDBGlobalState>
        implements TestOracle<MariaDBProvider.MariaDBGlobalState> {

    MariaDBSchema s;
    MariaDBSchema.MariaDBTables targetTables;
    MariaDBExpressionGenerator gen;
    MariaDBSelectStatement select;

    public MariaDBSonarOracle(MariaDBProvider.MariaDBGlobalState globalState) {
        super(globalState);
        this.s = globalState.getSchema();
        MariaDBErrors.addCommonErrors(errors);
        errors.add("is out of range");
        errors.add("unmatched parentheses");
        errors.add("nothing to repeat at offset");
        errors.add("missing )");
        errors.add("missing terminating ]");
        errors.add("range out of order in character class");
        errors.add("unrecognized character after ");
        errors.add("Got error '(*VERB) not recognized or malformed");
        errors.add("must be followed by");
        errors.add("malformed number or name after");
        errors.add("digit expected after");
    }

    List<MariaDBExpression> generateFetchColumns(List<MariaDBSchema.MariaDBColumn> columns) {
        List<MariaDBExpression> list = new ArrayList<>();
        MariaDBColumnName mariaDBColumnName = new MariaDBColumnName(Randomly.fromList(columns));
        String alias = "_" + mariaDBColumnName.getColumn().getName();
        list.add(new MariaDBPostfixText(mariaDBColumnName, alias));
        return list;
    }

    @Override
    public void check() throws SQLException {
        boolean useFetchColumnsExp = Randomly.getBoolean();
        MariaDBPostfixText asText = null;
        MariaDBExpression whereExp = null;
        s = state.getSchema();
        targetTables = s.getRandomTableNonEmptyTables();
        gen = new MariaDBExpressionGenerator(state.getRandomly()).setColumns(targetTables.getColumns());
        select = new MariaDBSelectStatement();

        if (useFetchColumnsExp) {
            MariaDBExpression exp = gen.generateFetchColumnExpression(targetTables);
            asText = new MariaDBPostfixText(exp, " AS f1");
            List<MariaDBExpression> fetchColumns = new ArrayList<>();
            fetchColumns.add(asText);
            select.setFetchColumns(fetchColumns);
        } else {
            select.setFetchColumns(generateFetchColumns(targetTables.getColumns()));
        }

        select.setJoinClauses(MariaDBJoin.getRandomJoinClauses(targetTables.getTables(), state.getRandomly()));

        List<MariaDBTable> tables = targetTables.getTables();
        List<MariaDBExpression> tableList = tables.stream().map(t -> new MariaDBTableReference(t))
                .collect(Collectors.toList());
        select.setFromList(tableList);

        if (useFetchColumnsExp) {
            whereExp = gen.generateWhereColumnExpression(asText);
            optimizedQueryString = getOptimizedQuery(select, asText, whereExp);
        } else {
            select.setWhereClause(gen.getRandomExpression());
            optimizedQueryString = getOptimizedQuery(select);
        }

        unoptimizedQueryString = getUnoptimizedQuery(select, useFetchColumnsExp, asText, whereExp);

        List<String> optimizedResultSet = ComparatorHelper.getResultSetFirstColumnAsString(optimizedQueryString, errors,
                state);
        List<String> unoptimizedResultSet = ComparatorHelper.getResultSetFirstColumnAsString(unoptimizedQueryString,
                errors, state);

        ComparatorHelper.assumeResultSetsAreEqual(optimizedResultSet, unoptimizedResultSet, optimizedQueryString,
                Arrays.asList(unoptimizedQueryString), state);
    }

    @Override
    public String getLastQueryString() {
        return optimizedQueryString;
    }

    private String getUnoptimizedQuery(MariaDBSelectStatement select, Boolean useFetchColumnsExp,
            MariaDBPostfixText asText, MariaDBExpression whereExp) throws SQLException {

        if (useFetchColumnsExp && whereExp != null) {
            if (whereExp instanceof MariaDBManuelPredicate) {
                String flagName = "("
                        + MariaDBVisitor.asString(((MariaDBPostfixText) select.getColumns().get(0)).getExpr()) + ")"
                        + " IS TRUE AS flag";
                select.getColumns().add(new MariaDBManuelPredicate(flagName));
            } else {
                MariaDBExpression right = null;
                if (whereExp instanceof MariaDBBinaryOperator) {
                    right = ((MariaDBBinaryOperator) whereExp).getRight();
                    MariaDBBinaryOperator.MariaDBBinaryComparisonOperator op = ((MariaDBBinaryOperator) whereExp)
                            .getOp();
                    MariaDBBinaryOperator fetchColumn = new MariaDBBinaryOperator(
                            ((MariaDBPostfixText) select.getColumns().get(0)).getExpr(), right, op);
                    String flagName = "(" + MariaDBVisitor.asString(fetchColumn) + ")" + " IS TRUE AS flag";
                    select.getColumns().add(new MariaDBManuelPredicate(flagName));
                }
            }

            MariaDBSelectStatement outerSelect = new MariaDBSelectStatement();
            outerSelect.setFetchColumns(Arrays.asList(new MariaDBManuelPredicate(asText.getText())));
            outerSelect.setFromList(Arrays.asList(select));
            outerSelect.setWhereClause(new MariaDBManuelPredicate("flag=1"));
            unoptimizedQueryString = MariaDBVisitor.asString(outerSelect);
            return unoptimizedQueryString;

        } else {
            MariaDBSelectStatement outerSelect = new MariaDBSelectStatement();
            outerSelect.setFetchColumns(Arrays
                    .asList(new MariaDBManuelPredicate(((MariaDBPostfixText) select.getColumns().get(0)).getText())));

            String flagName = "(" + MariaDBVisitor.asString(select.getWhereCondition()) + ")" + " IS TRUE AS flag";
            select.getColumns().add(new MariaDBManuelPredicate(flagName));

            select.setWhereClause(null);
            outerSelect.setFromList(Arrays.asList(select));
            outerSelect.setWhereClause(new MariaDBManuelPredicate("flag=1"));
            unoptimizedQueryString = MariaDBVisitor.asString(outerSelect);
            return unoptimizedQueryString;
        }
    }

    private String getOptimizedQuery(MariaDBSelectStatement select) throws SQLException {
        optimizedQueryString = MariaDBVisitor.asString(select);
        return optimizedQueryString;
    }

    private String getOptimizedQuery(MariaDBSelectStatement select, MariaDBPostfixText asText,
            MariaDBExpression whereExp) throws SQLException {
        MariaDBSelectStatement outerSelect = new MariaDBSelectStatement();
        outerSelect.setFetchColumns(Arrays.asList(new MariaDBManuelPredicate(asText.getText())));
        outerSelect.setFromList(Arrays.asList(select));
        outerSelect.setWhereClause(whereExp);

        optimizedQueryString = MariaDBVisitor.asString(outerSelect);
        return optimizedQueryString;
    }
}