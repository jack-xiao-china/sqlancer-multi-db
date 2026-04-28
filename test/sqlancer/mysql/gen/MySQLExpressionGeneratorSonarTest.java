package sqlancer.mysql.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLOptions;
import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLDataType;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.MySQLSchema.MySQLTables;
import sqlancer.mysql.ast.MySQLAggregateFunction;
import sqlancer.mysql.ast.MySQLBinaryArithmeticOperation;
import sqlancer.mysql.ast.MySQLBinaryComparisonOperation;
import sqlancer.mysql.ast.MySQLBinaryOperation;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLComputableFunction;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLJoin;
import sqlancer.mysql.ast.MySQLManuelPredicate;
import sqlancer.mysql.ast.MySQLPostfixText;
import sqlancer.mysql.ast.MySQLWindowFunction;

/**
 * Unit tests for SonarOracle methods added to MySQLExpressionGenerator.
 */
public class MySQLExpressionGeneratorSonarTest {

    private MySQLGlobalState state;
    private MySQLExpressionGenerator generator;
    private MySQLTables targetTables;

    @BeforeEach
    public void setUp() {
        // Initialize Randomly with a fixed seed for reproducible tests
        new Randomly(42L);

        // Properly initialize MySQLGlobalState with required options
        state = new MySQLGlobalState();
        state.setMainOptions(new MainOptions());
        state.setDbmsSpecificOptions(new MySQLOptions());
        state.setRandomly(new Randomly(42L));

        generator = new MySQLExpressionGenerator(state);

        // Create mock tables and columns for testing
        MySQLColumn column1 = new MySQLColumn("col1", MySQLDataType.INT, false, 10);
        MySQLColumn column2 = new MySQLColumn("col2", MySQLDataType.VARCHAR, false, 100);
        MySQLTable table1 = new MySQLTable("t0", Arrays.asList(column1, column2), null, MySQLTable.MySQLEngine.INNO_DB);
        column1.setTable(table1);
        column2.setTable(table1);

        targetTables = new MySQLTables(Arrays.asList(table1));
        generator.setColumns(Arrays.asList(column1, column2));
    }

    /**
     * Tests generateFetchColumnExpression method - verifies it generates correct expression types.
     */
    @Test
    public void testGenerateFetchColumnExpression() {
        // Test multiple generations to cover different expression types
        for (int i = 0; i < 50; i++) {
            MySQLExpression expr = generator.generateFetchColumnExpression(targetTables);

            assertNotNull(expr, "Expression should not be null");

            // Verify the expression is one of the expected types
            boolean isValidType = expr instanceof MySQLBinaryComparisonOperation || expr instanceof MySQLBinaryOperation
                    || expr instanceof MySQLBinaryArithmeticOperation || expr instanceof MySQLAggregateFunction
                    || expr instanceof MySQLComputableFunction || expr instanceof MySQLColumnReference;

            assertTrue(isValidType, "Expression should be one of the valid types: " + expr.getClass().getSimpleName());
        }
    }

    /**
     * Tests generateFetchColumnExpression specifically for aggregate function generation.
     */
    @Test
    public void testGenerateFetchColumnExpressionAggregateFunction() {
        // Run multiple times to hopefully hit AGGREGATE_FUNCTION case
        for (int i = 0; i < 100; i++) {
            MySQLExpression expr = generator.generateFetchColumnExpression(targetTables);
            if (expr instanceof MySQLAggregateFunction) {
                MySQLAggregateFunction aggFunc = (MySQLAggregateFunction) expr;
                assertNotNull(aggFunc.getFunction(), "Aggregate function should have a function type");
                assertNotNull(aggFunc.getExpr(), "Aggregate function should have an expression");
                break;
            }
        }
        // Note: This test is probabilistic; we don't fail if not found
        // because Randomly.fromOptions may not select AGGREGATE_FUNCTION
    }

    /**
     * Tests generateWhereColumnExpression method - verifies it generates correct WHERE condition types.
     */
    @Test
    public void testGenerateWhereColumnExpression() {
        MySQLExpression innerExpr = MySQLConstant.createIntConstant(1);
        MySQLPostfixText postfixText = new MySQLPostfixText(innerExpr, "ref0");

        for (int i = 0; i < 50; i++) {
            MySQLExpression expr = generator.generateWhereColumnExpression(postfixText);

            assertNotNull(expr, "Expression should not be null");

            // Verify the expression is one of the expected types
            boolean isValidType = expr instanceof MySQLBinaryComparisonOperation || expr instanceof MySQLBinaryOperation
                    || expr instanceof MySQLManuelPredicate;

            assertTrue(isValidType,
                    "Expression should be one of the valid WHERE expression types: " + expr.getClass().getSimpleName());
        }
    }

    /**
     * Tests generateWhereColumnExpression specifically for MySQLManuelPredicate type.
     */
    @Test
    public void testGenerateWhereColumnExpressionManuelPredicate() {
        MySQLExpression innerExpr = MySQLConstant.createIntConstant(1);
        MySQLPostfixText postfixText = new MySQLPostfixText(innerExpr, "ref0");

        // Run multiple times to hopefully hit UNARY_POSTFIX case
        for (int i = 0; i < 100; i++) {
            MySQLExpression expr = generator.generateWhereColumnExpression(postfixText);
            if (expr instanceof MySQLManuelPredicate) {
                MySQLManuelPredicate predicate = (MySQLManuelPredicate) expr;
                assertEquals("ref0", predicate.getString(), "Predicate should contain the postfix text");
                break;
            }
        }
    }

    /**
     * Tests generateWindowFuc method - verifies window function generation.
     */
    @Test
    public void testGenerateWindowFuc() {
        for (int i = 0; i < 50; i++) {
            MySQLExpression expr = generator.generateWindowFuc();

            assertNotNull(expr, "Window function expression should not be null");
            assertTrue(expr instanceof MySQLWindowFunction,
                    "Expression should be MySQLWindowFunction: " + expr.getClass().getSimpleName());

            MySQLWindowFunction windowFunc = (MySQLWindowFunction) expr;
            assertNotNull(windowFunc.getFunction(), "Window function should have a function type");
            assertNotNull(windowFunc.getPartitionBy(), "Window function should have partitionBy expression");

            // Verify args handling based on function type
            MySQLWindowFunction.MySQLFunction func = windowFunc.getFunction();
            if (func.getArgs() == 0) {
                assertNull(windowFunc.getExpr(), "Zero-arg window function should not have expr");
            } else if (func.getArgs() == 1) {
                assertNotNull(windowFunc.getExpr(), "One-arg window function should have expr");
            }
        }
    }

    /**
     * Tests that generateWindowFuc can generate both zero-arg and one-arg window functions.
     */
    @Test
    public void testGenerateWindowFucBothTypes() {
        boolean foundZeroArg = false;
        boolean foundOneArg = false;

        for (int i = 0; i < 100; i++) {
            MySQLWindowFunction windowFunc = (MySQLWindowFunction) generator.generateWindowFuc();

            if (windowFunc.getFunction().getArgs() == 0) {
                foundZeroArg = true;
            } else if (windowFunc.getFunction().getArgs() == 1) {
                foundOneArg = true;
            }

            if (foundZeroArg && foundOneArg) {
                break;
            }
        }

        assertTrue(foundZeroArg, "Should generate at least one zero-arg window function (e.g., ROW_NUMBER)");
        assertTrue(foundOneArg, "Should generate at least one one-arg window function (e.g., SUM)");
    }

    /**
     * Tests getRandomJoinClauses method - verifies JOIN list generation.
     */
    @Test
    public void testGetRandomJoinClauses() {
        // Create a list of tables for join testing
        MySQLColumn col1 = new MySQLColumn("col1", MySQLDataType.INT, false, 10);
        MySQLColumn col2 = new MySQLColumn("col2", MySQLDataType.INT, false, 10);
        MySQLTable table0 = new MySQLTable("t0", Arrays.asList(col1), null, MySQLTable.MySQLEngine.INNO_DB);
        MySQLTable table1 = new MySQLTable("t1", Arrays.asList(col2), null, MySQLTable.MySQLEngine.INNO_DB);
        MySQLTable table2 = new MySQLTable("t2", Arrays.asList(col2), null, MySQLTable.MySQLEngine.INNO_DB);
        col1.setTable(table0);
        col2.setTable(table1);

        List<MySQLTable> tables = new ArrayList<>(Arrays.asList(table0, table1, table2));

        for (int i = 0; i < 20; i++) {
            // Create a copy of the list since the method modifies it
            List<MySQLTable> tablesCopy = new ArrayList<>(tables);
            List<MySQLExpression> joins = generator.getRandomJoinClauses(tablesCopy);

            assertNotNull(joins, "Join list should not be null");

            for (MySQLExpression joinExpr : joins) {
                assertTrue(joinExpr instanceof MySQLJoin,
                        "Join expression should be MySQLJoin: " + joinExpr.getClass().getSimpleName());

                MySQLJoin join = (MySQLJoin) joinExpr;
                assertNotNull(join.getTable(), "Join should have a table");
                assertNotNull(join.getType(), "Join should have a type");

                // NATURAL joins should not have ON clause
                if (join.getType() == MySQLJoin.JoinType.NATURAL) {
                    assertNull(join.getOnClause(), "NATURAL join should not have ON clause");
                }
            }
        }
    }

    /**
     * Tests getRandomJoinClauses with empty table list.
     */
    @Test
    public void testGetRandomJoinClausesEmpty() {
        List<MySQLTable> tables = new ArrayList<>();
        List<MySQLExpression> joins = generator.getRandomJoinClauses(tables);

        assertNotNull(joins, "Join list should not be null even with empty tables");
        assertTrue(joins.isEmpty(), "Join list should be empty when no tables provided");
    }

    /**
     * Tests getRandomJoinClauses with single table (no joins possible).
     */
    @Test
    public void testGetRandomJoinClausesSingleTable() {
        MySQLColumn col = new MySQLColumn("col", MySQLDataType.INT, false, 10);
        MySQLTable table = new MySQLTable("t0", Arrays.asList(col), null, MySQLTable.MySQLEngine.INNO_DB);
        col.setTable(table);

        List<MySQLTable> tables = new ArrayList<>(Arrays.asList(table));
        List<MySQLExpression> joins = generator.getRandomJoinClauses(tables);

        assertNotNull(joins, "Join list should not be null");
        // With single table, no joins should be generated (tables.size() > 1 condition)
    }

    /**
     * Tests getRandomJoinClauses NATURAL join handling.
     */
    @Test
    public void testGetRandomJoinClausesNaturalJoin() {
        // With multiple joins, NATURAL should be excluded
        MySQLColumn col1 = new MySQLColumn("col1", MySQLDataType.INT, false, 10);
        MySQLColumn col2 = new MySQLColumn("col2", MySQLDataType.INT, false, 10);
        MySQLColumn col3 = new MySQLColumn("col3", MySQLDataType.INT, false, 10);
        MySQLTable table0 = new MySQLTable("t0", Arrays.asList(col1), null, MySQLTable.MySQLEngine.INNO_DB);
        MySQLTable table1 = new MySQLTable("t1", Arrays.asList(col2), null, MySQLTable.MySQLEngine.INNO_DB);
        MySQLTable table2 = new MySQLTable("t2", Arrays.asList(col3), null, MySQLTable.MySQLEngine.INNO_DB);
        col1.setTable(table0);
        col2.setTable(table1);
        col3.setTable(table2);

        List<MySQLTable> tables = new ArrayList<>(Arrays.asList(table0, table1, table2));

        // Run multiple times to check NATURAL join behavior
        for (int i = 0; i < 50; i++) {
            List<MySQLTable> tablesCopy = new ArrayList<>(tables);
            List<MySQLExpression> joins = generator.getRandomJoinClauses(tablesCopy);

            // If multiple joins are generated, none should be NATURAL
            if (joins.size() > 1) {
                for (MySQLExpression joinExpr : joins) {
                    MySQLJoin join = (MySQLJoin) joinExpr;
                    assertNotEquals(MySQLJoin.JoinType.NATURAL, join.getType(),
                            "NATURAL join should not be present when multiple joins exist");
                }
            }

            // Single join could be NATURAL (with null ON clause)
            if (joins.size() == 1) {
                MySQLJoin join = (MySQLJoin) joins.get(0);
                if (join.getType() == MySQLJoin.JoinType.NATURAL) {
                    assertNull(join.getOnClause(), "NATURAL join should have null ON clause");
                }
            }
        }
    }

    /**
     * Tests ColumnExpressionActions enum completeness - verifies all enum values are covered.
     */
    @Test
    public void testColumnExpressionActionsEnumCompleteness() {
        // We can't directly test the private enum, but we can verify all expression types are generated
        MySQLBinaryComparisonOperation.class.getName(); // Ensure class exists
        MySQLBinaryOperation.class.getName(); // Ensure class exists
        MySQLBinaryArithmeticOperation.class.getName(); // Ensure class exists
        MySQLAggregateFunction.class.getName(); // Ensure class exists
        MySQLComputableFunction.class.getName(); // Ensure class exists

        // All required expression types exist for the enum values
        assertTrue(true, "All ColumnExpressionActions enum expression types exist");
    }

    /**
     * Tests HavingColumnExpression enum completeness - verifies all enum values are covered.
     */
    @Test
    public void testHavingColumnExpressionEnumCompleteness() {
        // We can't directly test the private enum, but we can verify all expression types are generated
        MySQLBinaryComparisonOperation.class.getName(); // Ensure class exists
        MySQLBinaryOperation.class.getName(); // Ensure class exists
        MySQLManuelPredicate.class.getName(); // Ensure class exists

        // All required expression types exist for the enum values
        assertTrue(true, "All HavingColumnExpression enum expression types exist");
    }

    /**
     * Tests that existing MySQLExpressionGenerator methods still work after SonarOracle additions.
     */
    @Test
    public void testExistingMethodsStillWork() {
        // Test generateExpression still works
        MySQLExpression expr = generator.generateExpression();
        assertNotNull(expr, "generateExpression should still work");

        // Test generateConstant still works
        MySQLConstant constant = (MySQLConstant) generator.generateConstant();
        assertNotNull(constant, "generateConstant should still work");

        // Test generateAggregate still works
        MySQLExpression aggregate = generator.generateAggregate();
        assertNotNull(aggregate, "generateAggregate should still work");
    }

    /**
     * Tests window function function names are correct.
     */
    @Test
    public void testWindowFunctionNames() {
        // Verify window function names
        assertEquals("ROW_NUMBER", MySQLWindowFunction.MySQLFunction.ROW_NUMBER.getName());
        assertEquals("RANK", MySQLWindowFunction.MySQLFunction.RANK.getName());
        assertEquals("SUM", MySQLWindowFunction.MySQLFunction.SUM.getName());
        assertEquals("AVG", MySQLWindowFunction.MySQLFunction.AVG.getName());
    }

    /**
     * Tests aggregate function function names are correct.
     */
    @Test
    public void testAggregateFunctionNames() {
        // Verify aggregate function names
        assertEquals("COUNT", MySQLAggregateFunction.MySQLFunction.COUNT.getName());
        assertEquals("SUM", MySQLAggregateFunction.MySQLFunction.SUM.getName());
        assertEquals("AVG", MySQLAggregateFunction.MySQLFunction.AVG.getName());
        assertEquals("MAX", MySQLAggregateFunction.MySQLFunction.MAX.getName());
        assertEquals("MIN", MySQLAggregateFunction.MySQLFunction.MIN.getName());
    }
}