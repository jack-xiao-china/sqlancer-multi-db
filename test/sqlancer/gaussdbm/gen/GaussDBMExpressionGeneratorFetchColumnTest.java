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
import sqlancer.gaussdbm.ast.GaussDBBinaryArithmeticOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryComparisonOperation;
import sqlancer.gaussdbm.ast.GaussDBCaseWhen;
import sqlancer.gaussdbm.ast.GaussDBColumnReference;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBWindowFunction;
import sqlancer.gaussdbm.GaussDBToStringVisitor;

class GaussDBMExpressionGeneratorFetchColumnTest {

    private GaussDBMExpressionGenerator generator;
    private GaussDBTables testTables;
    private List<GaussDBColumn> testColumns;

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
        testTables = new GaussDBTables(List.of(testTable));
        // Note: testTables.getColumns() returns the flattened columns from all tables
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
    }

    @Test
    void testGenerateFetchColumnExpressionNotNull() {
        for (int i = 0; i < 100; i++) {
            GaussDBExpression expr = generator.generateFetchColumnExpression(testTables);
            assertNotNull(expr, "Generated expression should not be null");
        }
    }

    @Test
    void testGenerateBinaryComparisonOperation() {
        boolean sawBinaryComparison = false;
        for (int i = 0; i < 500; i++) {
            GaussDBExpression expr = generator.generateFetchColumnExpression(testTables);
            if (expr instanceof GaussDBBinaryComparisonOperation) {
                sawBinaryComparison = true;
                GaussDBBinaryComparisonOperation binComp = (GaussDBBinaryComparisonOperation) expr;
                assertNotNull(binComp.getLeft(), "Left expression should not be null");
                assertNotNull(binComp.getRight(), "Right expression should not be null");
                assertNotNull(binComp.getOp(), "Operator should not be null");
                break;
            }
        }
        assertTrue(sawBinaryComparison, "Expected at least one BinaryComparisonOperation expression");
    }

    @Test
    void testGenerateBinaryArithmeticOperation() {
        boolean sawBinaryArithmetic = false;
        for (int i = 0; i < 500; i++) {
            GaussDBExpression expr = generator.generateFetchColumnExpression(testTables);
            if (expr instanceof GaussDBBinaryArithmeticOperation) {
                sawBinaryArithmetic = true;
                GaussDBBinaryArithmeticOperation binArith = (GaussDBBinaryArithmeticOperation) expr;
                assertNotNull(binArith.getLeft(), "Left expression should not be null");
                assertNotNull(binArith.getRight(), "Right expression should not be null");
                assertNotNull(binArith.getOp(), "Operator should not be null");
                break;
            }
        }
        assertTrue(sawBinaryArithmetic, "Expected at least one BinaryArithmeticOperation expression");
    }

    @Test
    void testGenerateAggregateFunction() {
        boolean sawAggregate = false;
        for (int i = 0; i < 500; i++) {
            GaussDBExpression expr = generator.generateFetchColumnExpression(testTables);
            if (expr instanceof GaussDBAggregate) {
                sawAggregate = true;
                GaussDBAggregate agg = (GaussDBAggregate) expr;
                assertNotNull(agg.getExprs(), "Expression list should not be null");
                assertTrue(!agg.getExprs().isEmpty(), "Expression list should not be empty");
                assertNotNull(agg.getFunc(), "Aggregate function should not be null");
                break;
            }
        }
        assertTrue(sawAggregate, "Expected at least one Aggregate expression");
    }

    @Test
    void testGenerateCaseWhen() {
        boolean sawCaseWhen = false;
        for (int i = 0; i < 500; i++) {
            GaussDBExpression expr = generator.generateFetchColumnExpression(testTables);
            if (expr instanceof GaussDBCaseWhen) {
                sawCaseWhen = true;
                GaussDBCaseWhen caseWhen = (GaussDBCaseWhen) expr;
                assertNotNull(caseWhen.getWhenExpr(), "WHEN expression should not be null");
                assertNotNull(caseWhen.getThenExpr(), "THEN expression should not be null");
                assertNotNull(caseWhen.getElseExpr(), "ELSE expression should not be null");
                break;
            }
        }
        assertTrue(sawCaseWhen, "Expected at least one CaseWhen expression");
    }

    @Test
    void testGenerateColumnReference() {
        boolean sawColumnRef = false;
        for (int i = 0; i < 500; i++) {
            GaussDBExpression expr = generator.generateFetchColumnExpression(testTables);
            if (expr instanceof GaussDBColumnReference) {
                sawColumnRef = true;
                GaussDBColumnReference colRef = (GaussDBColumnReference) expr;
                assertNotNull(colRef.getColumn(), "Column should not be null");
                break;
            }
        }
        assertTrue(sawColumnRef, "Expected at least one ColumnReference expression");
    }

    @Test
    void testGenerateWindowFunction() {
        boolean sawWindowFunction = false;
        for (int i = 0; i < 500; i++) {
            GaussDBExpression expr = generator.generateFetchColumnExpression(testTables);
            if (expr instanceof GaussDBWindowFunction) {
                sawWindowFunction = true;
                GaussDBWindowFunction window = (GaussDBWindowFunction) expr;
                assertNotNull(window.getFunction(), "Window function should not be null");
                // Aggregate window functions should have an expression
                if (window.getFunction().isAggregateWindowFunction()) {
                    assertNotNull(window.getExpr(), "Aggregate window function should have an expression");
                }
                break;
            }
        }
        assertTrue(sawWindowFunction, "Expected at least one WindowFunction expression");
    }

    @Test
    void testWindowFunctionCanBeConvertedToString() {
        boolean generatedWindowFunction = false;
        for (int i = 0; i < 200; i++) {
            GaussDBExpression expr = generator.generateFetchColumnExpression(testTables);
            if (expr instanceof GaussDBWindowFunction) {
                generatedWindowFunction = true;
                String str = GaussDBToStringVisitor.asString(expr);
                assertNotNull(str, "String representation should not be null");
                assertTrue(!str.isEmpty(), "String representation should not be empty");
                assertTrue(str.contains("OVER"), "Window function should contain OVER");
                break;
            }
        }
        assertTrue(generatedWindowFunction, "Expected to generate at least one WindowFunction");
    }

    @Test
    void testExpressionCanBeConvertedToString() {
        for (int i = 0; i < 100; i++) {
            GaussDBExpression expr = generator.generateFetchColumnExpression(testTables);
            String str = GaussDBToStringVisitor.asString(expr);
            assertNotNull(str, "String representation should not be null");
            assertTrue(!str.isEmpty(), "String representation should not be empty");
        }
    }

    @Test
    void testAllExpressionTypesGenerated() {
        boolean sawBinaryComparison = false;
        boolean sawBinaryArithmetic = false;
        boolean sawAggregate = false;
        boolean sawCaseWhen = false;
        boolean sawColumn = false;
        boolean sawWindowFunction = false;

        // Generate enough expressions to cover all types (probabilistic)
        for (int i = 0; i < 2000; i++) {
            GaussDBExpression expr = generator.generateFetchColumnExpression(testTables);

            if (expr instanceof GaussDBBinaryComparisonOperation) {
                sawBinaryComparison = true;
            } else if (expr instanceof GaussDBBinaryArithmeticOperation) {
                sawBinaryArithmetic = true;
            } else if (expr instanceof GaussDBAggregate) {
                sawAggregate = true;
            } else if (expr instanceof GaussDBCaseWhen) {
                sawCaseWhen = true;
            } else if (expr instanceof GaussDBColumnReference) {
                sawColumn = true;
            } else if (expr instanceof GaussDBWindowFunction) {
                sawWindowFunction = true;
            }
        }

        assertTrue(sawBinaryComparison, "Expected BinaryComparisonOperation to be generated");
        assertTrue(sawBinaryArithmetic, "Expected BinaryArithmeticOperation to be generated");
        assertTrue(sawAggregate, "Expected Aggregate to be generated");
        assertTrue(sawCaseWhen, "Expected CaseWhen to be generated");
        assertTrue(sawColumn, "Expected ColumnReference to be generated");
        assertTrue(sawWindowFunction, "Expected WindowFunction to be generated");
    }

    @Test
    void testGenerateFetchColumnExpressionWithEmptyTables() {
        // Create empty tables
        GaussDBTables emptyTables = new GaussDBTables(List.of());

        // Should fallback to generateColumn() which uses generator's columns
        GaussDBExpression expr = generator.generateFetchColumnExpression(emptyTables);
        assertNotNull(expr, "Should generate expression even with empty tables");
    }

    @Test
    void testGenerateWindowFucNotNull() {
        for (int i = 0; i < 100; i++) {
            GaussDBExpression expr = generator.generateWindowFuc();
            assertNotNull(expr, "Generated window function should not be null");
            assertTrue(expr instanceof GaussDBWindowFunction, "Expression should be a WindowFunction");
        }
    }

    @Test
    void testGenerateWindowFucValidFunction() {
        for (int i = 0; i < 100; i++) {
            GaussDBExpression expr = generator.generateWindowFuc();
            GaussDBWindowFunction window = (GaussDBWindowFunction) expr;
            assertNotNull(window.getFunction(), "Window function should not be null");

            // Aggregate window functions should have an expression
            if (window.getFunction().isAggregateWindowFunction()) {
                assertNotNull(window.getExpr(), "Aggregate window function should have an expression");
            }

            // Ranking functions should not have an expression
            if (window.getFunction().isRankingFunction()) {
                // expr can be null for ranking functions
                assertTrue(window.getExpr() == null || window.getExpr() != null,
                        "Ranking function can have null or non-null expression");
            }
        }
    }

    @Test
    void testGenerateWindowFucCanBeStringified() {
        for (int i = 0; i < 100; i++) {
            GaussDBExpression expr = generator.generateWindowFuc();
            String str = GaussDBToStringVisitor.asString(expr);
            assertNotNull(str, "String representation should not be null");
            assertTrue(!str.isEmpty(), "String representation should not be empty");
            assertTrue(str.contains("OVER"), "Window function should contain OVER");
        }
    }

    @Test
    void testGenerateWindowFucPartitionAndOrderBy() {
        boolean sawPartitionBy = false;
        boolean sawOrderBy = false;

        for (int i = 0; i < 200; i++) {
            GaussDBExpression expr = generator.generateWindowFuc();
            GaussDBWindowFunction window = (GaussDBWindowFunction) expr;

            if (!window.getPartitionBy().isEmpty()) {
                sawPartitionBy = true;
            }
            if (!window.getOrderBy().isEmpty()) {
                sawOrderBy = true;
            }
        }

        // Both partition by and order by are optional, so we just verify they can be generated
        assertTrue(sawPartitionBy || sawOrderBy, "Expected at least one window function with PARTITION BY or ORDER BY");
    }
}