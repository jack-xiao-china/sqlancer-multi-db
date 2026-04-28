package sqlancer.gaussdbm.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.common.oracle.NoRECBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.gaussdbm.GaussDBMErrors;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTables;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBBinaryArithmeticOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryComparisonOperation;
import sqlancer.gaussdbm.ast.GaussDBColumnReference;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBManuelPredicate;
import sqlancer.gaussdbm.ast.GaussDBPostfixText;
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

/**
 * GaussDB-M SONAR Oracle implementation.
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
public class GaussDBMSonarOracle extends NoRECBase<GaussDBMGlobalState> implements TestOracle<GaussDBMGlobalState> {

    GaussDBMSchema s;
    GaussDBTables targetTables;
    GaussDBMExpressionGenerator gen;
    GaussDBSelect select;

    public GaussDBMSonarOracle(GaussDBMGlobalState globalState) {
        super(globalState);
        this.s = globalState.getSchema();
        GaussDBMErrors.addExpressionErrors(errors);

        // Additional errors specific to SONAR oracle
        errors.add("Data truncation");
        errors.add("Incorrect DATETIME value");
        errors.add("Incorrect DATE value");
        errors.add("Incorrect TIME value");
        errors.add("Invalid use of group function");
        errors.add("contains nonaggregated column");
        errors.add("is not in GROUP BY clause");
        errors.add("Cannot convert string");

        // Window function specific errors
        errors.add("window function");
        errors.add("OVER");
        errors.add("PARTITION BY");
        errors.add("You cannot use a window function");
        errors.add("is not allowed in window function");
    }

    /**
     * Generates default fetch columns (simple column reference with alias).
     */
    List<GaussDBExpression> generateFetchColumns() {
        List<GaussDBExpression> list = new ArrayList<>();
        GaussDBColumnReference colRef = GaussDBColumnReference.create(targetTables.getColumns().get(0), null);
        String alias = "_" + colRef.getColumn().getName();
        list.add(new GaussDBPostfixText(colRef, alias));
        return list;
    }

    @Override
    public void check() throws SQLException {
        boolean useFetchColumnsExp = Randomly.getBoolean();
        GaussDBPostfixText asText = null;
        GaussDBExpression whereExp = null;
        s = state.getSchema();
        targetTables = s.getRandomTableNonEmptyTables();
        gen = new GaussDBMExpressionGenerator(state).setColumns(targetTables.getColumns());
        select = new GaussDBSelect();

        // Generate fetch columns
        if (useFetchColumnsExp) {
            if (Randomly.getBoolean()) {
                // Use window function as fetch column
                GaussDBExpression exp = gen.generateWindowFuc();
                asText = new GaussDBPostfixText(exp, "f1");
                List<GaussDBExpression> fetchColumns = new ArrayList<>();
                fetchColumns.add(asText);
                select.setFetchColumns(fetchColumns);
            } else {
                // Use fetch column expression
                GaussDBExpression exp = gen.generateFetchColumnExpression(targetTables);
                asText = new GaussDBPostfixText(exp, "f1");
                List<GaussDBExpression> fetchColumns = new ArrayList<>();
                fetchColumns.add(asText);
                select.setFetchColumns(fetchColumns);
            }
        } else {
            // Use default fetch columns (simple column reference)
            select.setFetchColumns(generateFetchColumns());
        }

        // Set FROM list
        List<GaussDBTable> tables = targetTables.getTables();
        List<GaussDBExpression> tableList = tables.stream().map(t -> GaussDBTableReference.create(t))
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
    private String getUnoptimizedQuery(GaussDBSelect select, boolean useFetchColumnsExp, GaussDBPostfixText asText,
            GaussDBExpression whereExp) throws SQLException {

        if (useFetchColumnsExp && whereExp != null) {
            if (whereExp instanceof GaussDBManuelPredicate) {
                // Simple flag expression
                String flagName = "("
                        + GaussDBToStringVisitor
                                .asString(((GaussDBPostfixText) select.getFetchColumns().get(0)).getExpr())
                        + ")" + " IS TRUE AS flag";
                select.getFetchColumns().add(new GaussDBManuelPredicate(flagName));

            } else if (whereExp instanceof GaussDBBinaryArithmeticOperation) {
                GaussDBExpression right = ((GaussDBBinaryArithmeticOperation) whereExp).getRight();
                GaussDBBinaryArithmeticOperation.GaussDBArithmeticOperator op = ((GaussDBBinaryArithmeticOperation) whereExp)
                        .getOp();
                GaussDBBinaryArithmeticOperation fetchColumn = new GaussDBBinaryArithmeticOperation(
                        ((GaussDBPostfixText) select.getFetchColumns().get(0)).getExpr(), right, op);
                String flagName = "(" + GaussDBToStringVisitor.asString(fetchColumn) + ")" + " IS TRUE AS flag";
                select.getFetchColumns().add(new GaussDBManuelPredicate(flagName));

            } else if (whereExp instanceof GaussDBBinaryComparisonOperation) {
                GaussDBExpression right = ((GaussDBBinaryComparisonOperation) whereExp).getRight();
                GaussDBBinaryComparisonOperation.BinaryComparisonOperator op = ((GaussDBBinaryComparisonOperation) whereExp)
                        .getOp();
                GaussDBBinaryComparisonOperation fetchColumn = new GaussDBBinaryComparisonOperation(
                        ((GaussDBPostfixText) select.getFetchColumns().get(0)).getExpr(), right, op);
                String flagName = "(" + GaussDBToStringVisitor.asString(fetchColumn) + ")" + " IS TRUE AS flag";
                select.getFetchColumns().add(new GaussDBManuelPredicate(flagName));
            }

            // Create outer SELECT with flag filter
            GaussDBSelect outerSelect = new GaussDBSelect();
            outerSelect.setFetchColumns(Arrays.asList(new GaussDBManuelPredicate(asText.getText())));
            outerSelect.setFromList(Arrays.asList(select));
            outerSelect.setWhereClause(new GaussDBManuelPredicate("flag=1"));
            return GaussDBToStringVisitor.asString(outerSelect);

        } else {
            // Non-expression fetch columns case
            GaussDBSelect outerSelect = new GaussDBSelect();
            outerSelect.setFetchColumns(Arrays.asList(
                    new GaussDBManuelPredicate(((GaussDBPostfixText) select.getFetchColumns().get(0)).getText())));

            // Add flag column based on WHERE clause
            String flagName = "(" + GaussDBToStringVisitor.asString(select.getWhereClause()) + ")" + " IS TRUE AS flag";
            select.getFetchColumns().add(new GaussDBManuelPredicate(flagName));

            // Clear WHERE clause and wrap with outer SELECT
            select.setWhereClause(null);
            outerSelect.setFromList(Arrays.asList(select));
            outerSelect.setWhereClause(new GaussDBManuelPredicate("flag=1"));
            return GaussDBToStringVisitor.asString(outerSelect);
        }
    }

    /**
     * Generates the optimized query string (simple SELECT with WHERE clause).
     */
    private String getOptimizedQuery(GaussDBSelect select) throws SQLException {
        if (Randomly.getBoolean()) {
            select.setOrderByClauses(gen.generateExpressions(1));
        }

        optimizedQueryString = GaussDBToStringVisitor.asString(select);
        return optimizedQueryString;
    }

    /**
     * Generates the optimized query string with expression-based WHERE clause.
     */
    private String getOptimizedQuery(GaussDBSelect select, GaussDBPostfixText asText, GaussDBExpression whereExp)
            throws SQLException {
        GaussDBSelect outerSelect = new GaussDBSelect();
        outerSelect.setFetchColumns(Arrays.asList(new GaussDBManuelPredicate(asText.getText())));
        outerSelect.setFromList(Arrays.asList(select));
        outerSelect.setWhereClause(whereExp);

        optimizedQueryString = GaussDBToStringVisitor.asString(outerSelect);
        return optimizedQueryString;
    }
}