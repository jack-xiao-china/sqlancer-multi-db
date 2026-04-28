package sqlancer.gaussdbm.gen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMOptions;
import sqlancer.gaussdbm.GaussDBMOracleFactory;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBDataType;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTables;
import sqlancer.gaussdbm.ast.GaussDBAggregate;
import sqlancer.gaussdbm.ast.GaussDBBinaryComparisonOperation;
import sqlancer.gaussdbm.ast.GaussDBColumnReference;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBManuelPredicate;
import sqlancer.gaussdbm.ast.GaussDBPostfixText;
import sqlancer.gaussdbm.GaussDBToStringVisitor;

class GaussDBMExpressionGeneratorWhereColumnTest {

    private GaussDBMExpressionGenerator generator;
    private List<GaussDBColumn> testColumns;
    private GaussDBPostfixText testPostfixText;

    @BeforeEach
    void setUp() {
        // Create test columns
        GaussDBColumn col1 = new GaussDBColumn("c1", GaussDBDataType.INT, false, 0);
        GaussDBColumn col2 = new GaussDBColumn("c2", GaussDBDataType.VARCHAR, false, 0);

        // Create test table with columns
        List<GaussDBColumn> tableColumns = new ArrayList<>();
        tableColumns.add(col1);
        tableColumns.add(col2);
        GaussDBTable testTable = new GaussDBTable("t1", tableColumns, List.of(), GaussDBTable.GaussDBEngine.UNKNOWN);
        col1.setTable(testTable);
        col2.setTable(testTable);

        // Create test tables (contains the test table)
        GaussDBTables testTables = new GaussDBTables(List.of(testTable));
        testColumns = testTables.getColumns();

        // Initialize generator with the columns
        GaussDBMOptions gaussdbmOpts = new GaussDBMOptions();
        gaussdbmOpts.oracles = Arrays.asList(GaussDBMOracleFactory.QUERY_PARTITIONING);

        GaussDBMGlobalState state = new GaussDBMGlobalState();
        state.setMainOptions(MainOptions.DEFAULT_OPTIONS);
        state.setDbmsSpecificOptions(gaussdbmOpts);
        state.setRandomly(new Randomly(12345L));

        generator = new GaussDBMExpressionGenerator(state);
        generator.setColumns(testColumns);

        // Create test PostfixText
        GaussDBColumnReference colRef = GaussDBColumnReference.create(col1, null);
        testPostfixText = new GaussDBPostfixText(colRef, "_c1");
    }

    @Test
    void testGenerateWhereColumnExpressionNotNull() {
        for (int i = 0; i < 100; i++) {
            GaussDBExpression expr = generator.generateWhereColumnExpression(testPostfixText);
            assertNotNull(expr, "Generated expression should not be null");
        }
    }

    @Test
    void testGenerateBinaryComparisonOperation() {
        boolean sawBinaryComparison = false;
        for (int i = 0; i < 500; i++) {
            GaussDBExpression expr = generator.generateWhereColumnExpression(testPostfixText);
            if (expr instanceof GaussDBBinaryComparisonOperation) {
                sawBinaryComparison = true;
                GaussDBBinaryComparisonOperation binComp = (GaussDBBinaryComparisonOperation) expr;
                assertNotNull(binComp.getLeft(), "Left expression should not be null");
                assertNotNull(binComp.getRight(), "Right expression should not be null");
                assertNotNull(binComp.getOp(), "Operator should not be null");
                // Left should be a ManuelPredicate containing the postfixText
                assertTrue(binComp.getLeft() instanceof GaussDBManuelPredicate, "Left should be a ManuelPredicate");
                break;
            }
        }
        assertTrue(sawBinaryComparison, "Expected at least one BinaryComparisonOperation expression");
    }

    @Test
    void testGenerateAggregateFunction() {
        boolean sawAggregate = false;
        for (int i = 0; i < 500; i++) {
            GaussDBExpression expr = generator.generateWhereColumnExpression(testPostfixText);
            if (expr instanceof GaussDBAggregate) {
                sawAggregate = true;
                GaussDBAggregate agg = (GaussDBAggregate) expr;
                assertNotNull(agg.getExprs(), "Expression list should not be null");
                assertTrue(!agg.getExprs().isEmpty(), "Expression list should not be empty");
                assertNotNull(agg.getFunc(), "Aggregate function should not be null");
                // First expression should be a ManuelPredicate containing the postfixText
                assertTrue(agg.getExprs().get(0) instanceof GaussDBManuelPredicate,
                        "First expression should be a ManuelPredicate");
                break;
            }
        }
        assertTrue(sawAggregate, "Expected at least one Aggregate expression");
    }

    @Test
    void testGenerateColumnReference() {
        boolean sawManuelPredicate = false;
        for (int i = 0; i < 500; i++) {
            GaussDBExpression expr = generator.generateWhereColumnExpression(testPostfixText);
            if (expr instanceof GaussDBManuelPredicate) {
                sawManuelPredicate = true;
                GaussDBManuelPredicate pred = (GaussDBManuelPredicate) expr;
                assertNotNull(pred.getString(), "Predicate string should not be null");
                // The predicate should contain the postfixText's text
                assertTrue(pred.getString().equals("_c1"), "Predicate should contain the postfix text");
                break;
            }
        }
        assertTrue(sawManuelPredicate, "Expected at least one ManuelPredicate expression");
    }

    @Test
    void testExpressionCanBeConvertedToString() {
        for (int i = 0; i < 100; i++) {
            GaussDBExpression expr = generator.generateWhereColumnExpression(testPostfixText);
            String str = GaussDBToStringVisitor.asString(expr);
            assertNotNull(str, "String representation should not be null");
            assertTrue(!str.isEmpty(), "String representation should not be empty");
        }
    }

    @Test
    void testAllExpressionTypesGenerated() {
        boolean sawBinaryComparison = false;
        boolean sawAggregate = false;
        boolean sawManuelPredicate = false;

        // Generate enough expressions to cover all types (probabilistic)
        for (int i = 0; i < 1000; i++) {
            GaussDBExpression expr = generator.generateWhereColumnExpression(testPostfixText);

            if (expr instanceof GaussDBBinaryComparisonOperation) {
                sawBinaryComparison = true;
            } else if (expr instanceof GaussDBAggregate) {
                sawAggregate = true;
            } else if (expr instanceof GaussDBManuelPredicate) {
                sawManuelPredicate = true;
            }
        }

        assertTrue(sawBinaryComparison, "Expected BinaryComparisonOperation to be generated");
        assertTrue(sawAggregate, "Expected Aggregate to be generated");
        assertTrue(sawManuelPredicate, "Expected ManuelPredicate to be generated");
    }

    @Test
    void testPostfixTextContentPreserved() {
        for (int i = 0; i < 100; i++) {
            GaussDBExpression expr = generator.generateWhereColumnExpression(testPostfixText);

            // For BinaryComparison, check left contains postfixText
            if (expr instanceof GaussDBBinaryComparisonOperation) {
                GaussDBBinaryComparisonOperation binComp = (GaussDBBinaryComparisonOperation) expr;
                assertTrue(binComp.getLeft() instanceof GaussDBManuelPredicate);
                String leftText = ((GaussDBManuelPredicate) binComp.getLeft()).getString();
                assertTrue(leftText.equals("_c1"), "Left text should match postfix text");
            }
            // For Aggregate, check first expr contains postfixText
            else if (expr instanceof GaussDBAggregate) {
                GaussDBAggregate agg = (GaussDBAggregate) expr;
                assertTrue(agg.getExprs().get(0) instanceof GaussDBManuelPredicate);
                String firstText = ((GaussDBManuelPredicate) agg.getExprs().get(0)).getString();
                assertTrue(firstText.equals("_c1"), "First text should match postfix text");
            }
            // For ManuelPredicate, check text matches
            else if (expr instanceof GaussDBManuelPredicate) {
                String text = ((GaussDBManuelPredicate) expr).getString();
                assertTrue(text.equals("_c1"), "Text should match postfix text");
            }
        }
    }
}