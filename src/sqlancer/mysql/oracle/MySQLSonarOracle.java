package sqlancer.mysql.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.common.oracle.NoRECBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.mysql.MySQLErrors;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLSchema;
import sqlancer.mysql.MySQLSchema.MySQLTables;
import sqlancer.mysql.MySQLVisitor;
import sqlancer.mysql.ast.MySQLBinaryComparisonOperation;
import sqlancer.mysql.ast.MySQLBinaryOperation;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLManuelPredicate;
import sqlancer.mysql.ast.MySQLPostfixText;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

/**
 * MySQL SONAR Oracle implementation.
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
public class MySQLSonarOracle extends NoRECBase<MySQLGlobalState> implements TestOracle<MySQLGlobalState> {

    MySQLSchema s;
    MySQLTables targetTables;
    MySQLExpressionGenerator gen;
    MySQLSelect select;

    public MySQLSonarOracle(MySQLGlobalState globalState) {
        super(globalState);
        this.s = globalState.getSchema();
        MySQLErrors.addExpressionErrors(errors);

        // Additional errors specific to SONAR oracle
        errors.add("Data truncation");
        errors.add("Incorrect DATETIME value");
        errors.add("Incorrect DATE value");
        errors.add("Invalid use of group function");
        errors.add("incompatible with sql_mode=only_full_group_by");
        errors.add("Cannot convert string");
    }

    /**
     * Generates default fetch columns (simple column reference with alias).
     */
    List<MySQLExpression> generateFetchColumns() {
        List<MySQLExpression> list = new ArrayList<>();
        MySQLColumnReference mySQLColumnReference = MySQLColumnReference.create(targetTables.getColumns().get(0), null);
        String alias = "_" + mySQLColumnReference.getColumn().getName();
        list.add(new MySQLPostfixText(mySQLColumnReference, alias));
        return list;
    }

    /**
     * Generates default GROUP BY columns (simple column reference).
     */
    List<MySQLExpression> generateGroupByColumns() {
        List<MySQLExpression> list = new ArrayList<>();
        MySQLColumnReference mySQLColumnReference = MySQLColumnReference.create(targetTables.getColumns().get(0), null);
        list.add(mySQLColumnReference);
        return list;
    }

    @Override
    public void check() throws SQLException {
        boolean useFetchColumnsExp = Randomly.getBoolean();
        MySQLPostfixText asText = null;
        MySQLExpression whereExp = null;
        s = state.getSchema();
        targetTables = s.getRandomTableNonEmptyTables();
        gen = new MySQLExpressionGenerator(state).setColumns(targetTables.getColumns());
        select = new MySQLSelect();

        // Generate fetch columns
        if (useFetchColumnsExp) {
            if (Randomly.getBoolean()) {
                // Use window function as fetch column
                MySQLExpression exp = gen.generateWindowFuc();
                asText = new MySQLPostfixText(exp, "f1");
                List<MySQLExpression> fetchColumns = new ArrayList<>();
                fetchColumns.add(asText);
                select.setFetchColumns(fetchColumns);
            } else {
                // Use fetch column expression
                MySQLExpression exp = gen.generateFetchColumnExpression(targetTables);
                asText = new MySQLPostfixText(exp, "f1");
                List<MySQLExpression> fetchColumns = new ArrayList<>();
                fetchColumns.add(asText);
                select.setFetchColumns(fetchColumns);
            }
        } else {
            // Use default fetch columns (simple column reference)
            select.setFetchColumns(generateFetchColumns());
        }

        // Set JOIN clauses
        select.setJoinList(gen.getRandomJoinClauses(targetTables.getTables()));

        // Set FROM list
        List<MySQLSchema.MySQLTable> tables = targetTables.getTables();
        List<MySQLExpression> tableList = tables.stream().map(t -> new MySQLTableReference(t))
                .collect(Collectors.toList());
        select.setFromList(tableList);

        // Generate optimized and unoptimized queries
        if (useFetchColumnsExp) {
            whereExp = gen.generateWhereColumnExpression(asText);
            optimizedQueryString = getOptimizedQuery(select, asText, whereExp);
        } else {
            select.setWhereClause(gen.generateExpression());
            optimizedQueryString = getOptimizedQuery(select);
        }

        unoptimizedQueryString = getUnoptimizedQuery(select, useFetchColumnsExp, asText, whereExp);

        // Execute and compare result sets
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

    /**
     * Generates the unoptimized query string.
     *
     * The unoptimized query wraps the inner SELECT with a flag column, then filters by flag=1 in the outer SELECT.
     */
    private String getUnoptimizedQuery(MySQLSelect select, Boolean useFetchColumnsExp, MySQLPostfixText asText,
            MySQLExpression whereExp) throws SQLException {

        if (useFetchColumnsExp && whereExp != null) {
            if (whereExp instanceof MySQLManuelPredicate) {
                // Simple flag expression
                String flag_name = "("
                        + MySQLVisitor.asString(((MySQLPostfixText) select.getFetchColumns().get(0)).getExpr()) + ")"
                        + " IS TRUE AS flag";
                select.getFetchColumns().add(new MySQLManuelPredicate(flag_name));

            } else {
                MySQLExpression right = null;
                if (whereExp instanceof MySQLBinaryOperation) {
                    right = ((MySQLBinaryOperation) whereExp).getRight();
                    MySQLBinaryOperation.MySQLBinaryOperator op = ((MySQLBinaryOperation) whereExp).getOp();
                    MySQLBinaryOperation fetch_column = new MySQLBinaryOperation(
                            ((MySQLPostfixText) select.getFetchColumns().get(0)).getExpr(), right, op);
                    String flag_name = "(" + MySQLVisitor.asString(fetch_column) + ")" + " IS TRUE AS flag";
                    select.getFetchColumns().add(new MySQLManuelPredicate(flag_name));

                } else if (whereExp instanceof MySQLBinaryComparisonOperation) {
                    right = ((MySQLBinaryComparisonOperation) whereExp).getRight();
                    MySQLBinaryComparisonOperation.BinaryComparisonOperator op = ((MySQLBinaryComparisonOperation) whereExp)
                            .getOp();
                    MySQLBinaryComparisonOperation fetch_column = new MySQLBinaryComparisonOperation(
                            ((MySQLPostfixText) select.getFetchColumns().get(0)).getExpr(), right, op);
                    String flag_name = "(" + MySQLVisitor.asString(fetch_column) + ")" + " IS TRUE AS flag";
                    select.getFetchColumns().add(new MySQLManuelPredicate(flag_name));
                }
            }

            // Create outer SELECT with flag filter
            MySQLSelect outerSelect = new MySQLSelect();
            outerSelect.setFetchColumns(Arrays.asList(new MySQLManuelPredicate(asText.getText())));
            outerSelect.setFromList(Arrays.asList(select));
            outerSelect.setWhereClause(new MySQLManuelPredicate("flag=1"));
            unoptimizedQueryString = MySQLVisitor.asString(outerSelect);
            return unoptimizedQueryString;

        } else {
            // Non-expression fetch columns case
            MySQLSelect outerSelect = new MySQLSelect();
            outerSelect.setFetchColumns(Arrays
                    .asList(new MySQLManuelPredicate(((MySQLPostfixText) select.getFetchColumns().get(0)).getText())));

            // Add flag column based on WHERE clause
            String flag_name = "(" + MySQLVisitor.asString(select.getWhereClause()) + ")" + " IS TRUE AS flag";
            select.getFetchColumns().add(new MySQLManuelPredicate(flag_name));

            // Clear WHERE clause and wrap with outer SELECT
            select.setWhereClause(null);
            outerSelect.setFromList(Arrays.asList(select));
            outerSelect.setWhereClause(new MySQLManuelPredicate("flag=1"));
            unoptimizedQueryString = MySQLVisitor.asString(outerSelect);
            return unoptimizedQueryString;
        }
    }

    /**
     * Generates the optimized query string (simple SELECT with WHERE clause).
     */
    private String getOptimizedQuery(MySQLSelect select) throws SQLException {
        if (Randomly.getBoolean()) {
            select.setOrderByClauses(gen.generateExpressions(1));
        }

        optimizedQueryString = MySQLVisitor.asString(select);
        return optimizedQueryString;
    }

    /**
     * Generates the optimized query string with expression-based WHERE clause.
     */
    private String getOptimizedQuery(MySQLSelect select, MySQLPostfixText asText, MySQLExpression whereExp)
            throws SQLException {
        MySQLSelect outerSelect = new MySQLSelect();
        outerSelect.setFetchColumns(Arrays.asList(new MySQLManuelPredicate(asText.getText())));
        outerSelect.setFromList(Arrays.asList(select));
        outerSelect.setWhereClause(whereExp);

        optimizedQueryString = MySQLVisitor.asString(outerSelect);
        return optimizedQueryString;
    }
}