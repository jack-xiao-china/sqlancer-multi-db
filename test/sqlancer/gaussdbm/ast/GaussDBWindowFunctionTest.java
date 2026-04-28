package sqlancer.gaussdbm.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBWindowFunction.GaussDBFunction;

class GaussDBWindowFunctionTest {

    @Test
    void testRowNumberFunctionName() {
        assertEquals("ROW_NUMBER", GaussDBFunction.ROW_NUMBER.getName());
    }

    @Test
    void testRankFunctionName() {
        assertEquals("RANK", GaussDBFunction.RANK.getName());
    }

    @Test
    void testDenseRankFunctionName() {
        assertEquals("DENSE_RANK", GaussDBFunction.DENSE_RANK.getName());
    }

    @Test
    void testSumFunctionName() {
        assertEquals("SUM", GaussDBFunction.SUM.getName());
    }

    @Test
    void testAvgFunctionName() {
        assertEquals("AVG", GaussDBFunction.AVG.getName());
    }

    @Test
    void testCountFunctionName() {
        assertEquals("COUNT", GaussDBFunction.COUNT.getName());
    }

    @Test
    void testMaxFunctionName() {
        assertEquals("MAX", GaussDBFunction.MAX.getName());
    }

    @Test
    void testMinFunctionName() {
        assertEquals("MIN", GaussDBFunction.MIN.getName());
    }

    @Test
    void testRankingFunctionArgs() {
        assertEquals(0, GaussDBFunction.ROW_NUMBER.getArgs());
        assertEquals(0, GaussDBFunction.RANK.getArgs());
        assertEquals(0, GaussDBFunction.DENSE_RANK.getArgs());
    }

    @Test
    void testAggregateWindowFunctionArgs() {
        assertEquals(1, GaussDBFunction.SUM.getArgs());
        assertEquals(1, GaussDBFunction.AVG.getArgs());
        assertEquals(1, GaussDBFunction.COUNT.getArgs());
        assertEquals(1, GaussDBFunction.MAX.getArgs());
        assertEquals(1, GaussDBFunction.MIN.getArgs());
    }

    @Test
    void testIsRankingFunction() {
        assertTrue(GaussDBFunction.ROW_NUMBER.isRankingFunction());
        assertTrue(GaussDBFunction.RANK.isRankingFunction());
        assertTrue(GaussDBFunction.DENSE_RANK.isRankingFunction());
    }

    @Test
    void testIsAggregateWindowFunction() {
        assertTrue(GaussDBFunction.SUM.isAggregateWindowFunction());
        assertTrue(GaussDBFunction.AVG.isAggregateWindowFunction());
        assertTrue(GaussDBFunction.COUNT.isAggregateWindowFunction());
        assertTrue(GaussDBFunction.MAX.isAggregateWindowFunction());
        assertTrue(GaussDBFunction.MIN.isAggregateWindowFunction());
        assertFalse(GaussDBFunction.ROW_NUMBER.isAggregateWindowFunction());
        assertFalse(GaussDBFunction.RANK.isAggregateWindowFunction());
        assertFalse(GaussDBFunction.DENSE_RANK.isAggregateWindowFunction());
    }

    @Test
    void testAllWindowFunctionsCount() {
        assertEquals(8, GaussDBFunction.values().length);
    }

    @Test
    void testGetExpectedValueReturnsNull() {
        GaussDBWindowFunction window = new GaussDBWindowFunction(GaussDBFunction.ROW_NUMBER, null, null, null);
        assertNull(window.getExpectedValue());
    }

    @Test
    void testGetExpectedValueReturnsNullForAggregate() {
        GaussDBConstant constant = GaussDBConstant.createIntConstant(1);
        GaussDBWindowFunction window = new GaussDBWindowFunction(GaussDBFunction.SUM, constant, null, null);
        assertNull(window.getExpectedValue());
    }

    @Test
    void testRowNumberSqlGeneration() {
        GaussDBWindowFunction window = new GaussDBWindowFunction(GaussDBFunction.ROW_NUMBER, null, null, null);
        String sql = GaussDBToStringVisitor.asString(window);
        assertEquals("ROW_NUMBER() OVER ()", sql);
    }

    @Test
    void testRankSqlGeneration() {
        GaussDBWindowFunction window = new GaussDBWindowFunction(GaussDBFunction.RANK, null, null, null);
        String sql = GaussDBToStringVisitor.asString(window);
        assertEquals("RANK() OVER ()", sql);
    }

    @Test
    void testSumSqlGeneration() {
        GaussDBConstant constant = GaussDBConstant.createIntConstant(1);
        GaussDBWindowFunction window = new GaussDBWindowFunction(GaussDBFunction.SUM, constant, null, null);
        String sql = GaussDBToStringVisitor.asString(window);
        assertEquals("SUM(1) OVER ()", sql);
    }

    @Test
    void testWindowFunctionWithPartitionBy() {
        // Create a simple partition by expression
        List<GaussDBExpression> partitionBy = new ArrayList<>();
        partitionBy.add(GaussDBConstant.createIntConstant(1));
        partitionBy.add(GaussDBConstant.createIntConstant(2));

        GaussDBWindowFunction window = new GaussDBWindowFunction(GaussDBFunction.ROW_NUMBER, null, partitionBy, null);
        String sql = GaussDBToStringVisitor.asString(window);
        assertEquals("ROW_NUMBER() OVER (PARTITION BY 1, 2)", sql);
    }

    @Test
    void testWindowFunctionWithOrderBy() {
        // Create a simple order by expression
        List<GaussDBExpression> orderBy = new ArrayList<>();
        orderBy.add(GaussDBConstant.createIntConstant(1));

        GaussDBWindowFunction window = new GaussDBWindowFunction(GaussDBFunction.ROW_NUMBER, null, null, orderBy);
        String sql = GaussDBToStringVisitor.asString(window);
        assertEquals("ROW_NUMBER() OVER (ORDER BY 1)", sql);
    }

    @Test
    void testWindowFunctionWithPartitionByAndOrderBy() {
        List<GaussDBExpression> partitionBy = new ArrayList<>();
        partitionBy.add(GaussDBConstant.createIntConstant(1));

        List<GaussDBExpression> orderBy = new ArrayList<>();
        orderBy.add(GaussDBConstant.createIntConstant(2));

        GaussDBWindowFunction window = new GaussDBWindowFunction(GaussDBFunction.ROW_NUMBER, null, partitionBy,
                orderBy);
        String sql = GaussDBToStringVisitor.asString(window);
        assertEquals("ROW_NUMBER() OVER (PARTITION BY 1 ORDER BY 2)", sql);
    }

    @Test
    void testAggregateWindowFunctionWithPartitionBy() {
        List<GaussDBExpression> partitionBy = new ArrayList<>();
        partitionBy.add(GaussDBConstant.createIntConstant(1));

        GaussDBConstant expr = GaussDBConstant.createIntConstant(10);
        GaussDBWindowFunction window = new GaussDBWindowFunction(GaussDBFunction.AVG, expr, partitionBy, null);
        String sql = GaussDBToStringVisitor.asString(window);
        assertEquals("AVG(10) OVER (PARTITION BY 1)", sql);
    }

    @Test
    void testRandomFunctionIncludesAllFunctions() {
        Set<GaussDBFunction> values = Arrays.stream(GaussDBFunction.values()).collect(Collectors.toSet());
        assertTrue(values.contains(GaussDBFunction.ROW_NUMBER));
        assertTrue(values.contains(GaussDBFunction.RANK));
        assertTrue(values.contains(GaussDBFunction.DENSE_RANK));
        assertTrue(values.contains(GaussDBFunction.SUM));
        assertTrue(values.contains(GaussDBFunction.AVG));
        assertTrue(values.contains(GaussDBFunction.COUNT));
        assertTrue(values.contains(GaussDBFunction.MAX));
        assertTrue(values.contains(GaussDBFunction.MIN));
    }

    @Test
    void testGetFunction() {
        GaussDBWindowFunction window = new GaussDBWindowFunction(GaussDBFunction.COUNT, null, null, null);
        assertEquals(GaussDBFunction.COUNT, window.getFunction());
    }

    @Test
    void testGetExpr() {
        GaussDBConstant expr = GaussDBConstant.createIntConstant(5);
        GaussDBWindowFunction window = new GaussDBWindowFunction(GaussDBFunction.MAX, expr, null, null);
        assertEquals(expr, window.getExpr());
    }

    @Test
    void testGetPartitionBy() {
        List<GaussDBExpression> partitionBy = new ArrayList<>();
        partitionBy.add(GaussDBConstant.createIntConstant(1));
        GaussDBWindowFunction window = new GaussDBWindowFunction(GaussDBFunction.MIN, null, partitionBy, null);
        assertEquals(partitionBy, window.getPartitionBy());
    }

    @Test
    void testGetOrderBy() {
        List<GaussDBExpression> orderBy = new ArrayList<>();
        orderBy.add(GaussDBConstant.createIntConstant(1));
        GaussDBWindowFunction window = new GaussDBWindowFunction(GaussDBFunction.MIN, null, null, orderBy);
        assertEquals(orderBy, window.getOrderBy());
    }

    @Test
    void testNullPartitionByReturnsEmptyList() {
        GaussDBWindowFunction window = new GaussDBWindowFunction(GaussDBFunction.ROW_NUMBER, null, null, null);
        assertTrue(window.getPartitionBy().isEmpty());
    }

    @Test
    void testNullOrderByReturnsEmptyList() {
        GaussDBWindowFunction window = new GaussDBWindowFunction(GaussDBFunction.ROW_NUMBER, null, null, null);
        assertTrue(window.getOrderBy().isEmpty());
    }
}