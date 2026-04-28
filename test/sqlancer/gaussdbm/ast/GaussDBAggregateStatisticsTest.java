package sqlancer.gaussdbm.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBAggregate.GaussDBAggregateFunction;

class GaussDBAggregateStatisticsTest {

    // Standard deviation functions tests

    @Test
    void testStddevFunctionName() {
        assertEquals("STDDEV", GaussDBAggregateFunction.STDDEV.getName());
    }

    @Test
    void testStddevPopFunctionName() {
        assertEquals("STDDEV_POP", GaussDBAggregateFunction.STDDEV_POP.getName());
    }

    @Test
    void testStddevSampFunctionName() {
        assertEquals("STDDEV_SAMP", GaussDBAggregateFunction.STDDEV_SAMP.getName());
    }

    @Test
    void testStdFunctionName() {
        assertEquals("STD", GaussDBAggregateFunction.STD.getName());
    }

    // Variance functions tests

    @Test
    void testVarianceFunctionName() {
        assertEquals("VARIANCE", GaussDBAggregateFunction.VARIANCE.getName());
    }

    @Test
    void testVarPopFunctionName() {
        assertEquals("VAR_POP", GaussDBAggregateFunction.VAR_POP.getName());
    }

    @Test
    void testVarSampFunctionName() {
        assertEquals("VAR_SAMP", GaussDBAggregateFunction.VAR_SAMP.getName());
    }

    // Options are null for statistical functions

    @Test
    void testStddevOptionIsNull() {
        assertNull(GaussDBAggregateFunction.STDDEV.getOption());
    }

    @Test
    void testStddevPopOptionIsNull() {
        assertNull(GaussDBAggregateFunction.STDDEV_POP.getOption());
    }

    @Test
    void testStddevSampOptionIsNull() {
        assertNull(GaussDBAggregateFunction.STDDEV_SAMP.getOption());
    }

    @Test
    void testStdOptionIsNull() {
        assertNull(GaussDBAggregateFunction.STD.getOption());
    }

    @Test
    void testVarianceOptionIsNull() {
        assertNull(GaussDBAggregateFunction.VARIANCE.getOption());
    }

    @Test
    void testVarPopOptionIsNull() {
        assertNull(GaussDBAggregateFunction.VAR_POP.getOption());
    }

    @Test
    void testVarSampOptionIsNull() {
        assertNull(GaussDBAggregateFunction.VAR_SAMP.getOption());
    }

    // Statistical functions are not variadic

    @Test
    void testStddevIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.STDDEV.isVariadic());
    }

    @Test
    void testStddevPopIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.STDDEV_POP.isVariadic());
    }

    @Test
    void testStddevSampIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.STDDEV_SAMP.isVariadic());
    }

    @Test
    void testStdIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.STD.isVariadic());
    }

    @Test
    void testVarianceIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.VARIANCE.isVariadic());
    }

    @Test
    void testVarPopIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.VAR_POP.isVariadic());
    }

    @Test
    void testVarSampIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.VAR_SAMP.isVariadic());
    }

    // Test all statistical functions are in values()

    @Test
    void testAllStatisticsFunctionsInValues() {
        Set<GaussDBAggregateFunction> values = Arrays.stream(GaussDBAggregateFunction.values())
                .collect(Collectors.toSet());
        // STDDEV series
        assertTrue(values.contains(GaussDBAggregateFunction.STDDEV));
        assertTrue(values.contains(GaussDBAggregateFunction.STDDEV_POP));
        assertTrue(values.contains(GaussDBAggregateFunction.STDDEV_SAMP));
        assertTrue(values.contains(GaussDBAggregateFunction.STD));
        // VARIANCE series
        assertTrue(values.contains(GaussDBAggregateFunction.VARIANCE));
        assertTrue(values.contains(GaussDBAggregateFunction.VAR_POP));
        assertTrue(values.contains(GaussDBAggregateFunction.VAR_SAMP));
    }

    // Test SQL generation format

    @Test
    void testStddevSqlGeneration() {
        GaussDBExpression expr = new GaussDBConstant.GaussDBIntConstant(1);
        GaussDBAggregate aggregate = new GaussDBAggregate(Arrays.asList(expr), GaussDBAggregateFunction.STDDEV);
        GaussDBToStringVisitor visitor = new GaussDBToStringVisitor();
        visitor.visit(aggregate);
        assertEquals("STDDEV(1)", visitor.get());
    }

    @Test
    void testStddevPopSqlGeneration() {
        GaussDBExpression expr = new GaussDBConstant.GaussDBIntConstant(42);
        GaussDBAggregate aggregate = new GaussDBAggregate(Arrays.asList(expr), GaussDBAggregateFunction.STDDEV_POP);
        GaussDBToStringVisitor visitor = new GaussDBToStringVisitor();
        visitor.visit(aggregate);
        assertEquals("STDDEV_POP(42)", visitor.get());
    }

    @Test
    void testStddevSampSqlGeneration() {
        GaussDBExpression expr = new GaussDBConstant.GaussDBIntConstant(100);
        GaussDBAggregate aggregate = new GaussDBAggregate(Arrays.asList(expr), GaussDBAggregateFunction.STDDEV_SAMP);
        GaussDBToStringVisitor visitor = new GaussDBToStringVisitor();
        visitor.visit(aggregate);
        assertEquals("STDDEV_SAMP(100)", visitor.get());
    }

    @Test
    void testStdSqlGeneration() {
        GaussDBExpression expr = new GaussDBConstant.GaussDBIntConstant(5);
        GaussDBAggregate aggregate = new GaussDBAggregate(Arrays.asList(expr), GaussDBAggregateFunction.STD);
        GaussDBToStringVisitor visitor = new GaussDBToStringVisitor();
        visitor.visit(aggregate);
        assertEquals("STD(5)", visitor.get());
    }

    @Test
    void testVarianceSqlGeneration() {
        GaussDBExpression expr = new GaussDBConstant.GaussDBIntConstant(10);
        GaussDBAggregate aggregate = new GaussDBAggregate(Arrays.asList(expr), GaussDBAggregateFunction.VARIANCE);
        GaussDBToStringVisitor visitor = new GaussDBToStringVisitor();
        visitor.visit(aggregate);
        assertEquals("VARIANCE(10)", visitor.get());
    }

    @Test
    void testVarPopSqlGeneration() {
        GaussDBExpression expr = new GaussDBConstant.GaussDBIntConstant(20);
        GaussDBAggregate aggregate = new GaussDBAggregate(Arrays.asList(expr), GaussDBAggregateFunction.VAR_POP);
        GaussDBToStringVisitor visitor = new GaussDBToStringVisitor();
        visitor.visit(aggregate);
        assertEquals("VAR_POP(20)", visitor.get());
    }

    @Test
    void testVarSampSqlGeneration() {
        GaussDBExpression expr = new GaussDBConstant.GaussDBIntConstant(30);
        GaussDBAggregate aggregate = new GaussDBAggregate(Arrays.asList(expr), GaussDBAggregateFunction.VAR_SAMP);
        GaussDBToStringVisitor visitor = new GaussDBToStringVisitor();
        visitor.visit(aggregate);
        assertEquals("VAR_SAMP(30)", visitor.get());
    }

    // Test total function count

    @Test
    void testAllAggregateFunctionsCount() {
        // COUNT(2) + SUM(2) + MIN(2) + MAX(2) + AVG(2) +
        // BIT_AND(2) + BIT_OR(2) + BIT_XOR(2) +
        // STDDEV(4) + VARIANCE(3) = 23
        assertEquals(23, GaussDBAggregateFunction.values().length);
    }
}