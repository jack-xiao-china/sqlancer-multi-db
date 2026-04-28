package sqlancer.gaussdbm.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.ast.GaussDBAggregate.GaussDBAggregateFunction;

class GaussDBAggregateBitwiseTest {

    // BIT_AND tests
    @Test
    void testBitAndFunctionName() {
        assertEquals("BIT_AND", GaussDBAggregateFunction.BIT_AND.getName());
    }

    @Test
    void testBitAndDistinctFunctionName() {
        assertEquals("BIT_AND", GaussDBAggregateFunction.BIT_AND_DISTINCT.getName());
    }

    @Test
    void testBitAndDistinctOption() {
        assertEquals("DISTINCT", GaussDBAggregateFunction.BIT_AND_DISTINCT.getOption());
    }

    @Test
    void testBitAndOptionIsNull() {
        assertEquals(null, GaussDBAggregateFunction.BIT_AND.getOption());
    }

    @Test
    void testBitAndIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.BIT_AND.isVariadic());
    }

    @Test
    void testBitAndDistinctIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.BIT_AND_DISTINCT.isVariadic());
    }

    // BIT_OR tests
    @Test
    void testBitOrFunctionName() {
        assertEquals("BIT_OR", GaussDBAggregateFunction.BIT_OR.getName());
    }

    @Test
    void testBitOrDistinctFunctionName() {
        assertEquals("BIT_OR", GaussDBAggregateFunction.BIT_OR_DISTINCT.getName());
    }

    @Test
    void testBitOrDistinctOption() {
        assertEquals("DISTINCT", GaussDBAggregateFunction.BIT_OR_DISTINCT.getOption());
    }

    @Test
    void testBitOrOptionIsNull() {
        assertEquals(null, GaussDBAggregateFunction.BIT_OR.getOption());
    }

    @Test
    void testBitOrIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.BIT_OR.isVariadic());
    }

    @Test
    void testBitOrDistinctIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.BIT_OR_DISTINCT.isVariadic());
    }

    // BIT_XOR tests
    @Test
    void testBitXorFunctionName() {
        assertEquals("BIT_XOR", GaussDBAggregateFunction.BIT_XOR.getName());
    }

    @Test
    void testBitXorDistinctFunctionName() {
        assertEquals("BIT_XOR", GaussDBAggregateFunction.BIT_XOR_DISTINCT.getName());
    }

    @Test
    void testBitXorDistinctOption() {
        assertEquals("DISTINCT", GaussDBAggregateFunction.BIT_XOR_DISTINCT.getOption());
    }

    @Test
    void testBitXorOptionIsNull() {
        assertEquals(null, GaussDBAggregateFunction.BIT_XOR.getOption());
    }

    @Test
    void testBitXorIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.BIT_XOR.isVariadic());
    }

    @Test
    void testBitXorDistinctIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.BIT_XOR_DISTINCT.isVariadic());
    }

    // Verify all bitwise functions are in the enum
    @Test
    void testBitwiseFunctionsIncluded() {
        Set<GaussDBAggregateFunction> values = Arrays.stream(GaussDBAggregateFunction.values())
                .collect(Collectors.toSet());
        assertTrue(values.contains(GaussDBAggregateFunction.BIT_AND));
        assertTrue(values.contains(GaussDBAggregateFunction.BIT_AND_DISTINCT));
        assertTrue(values.contains(GaussDBAggregateFunction.BIT_OR));
        assertTrue(values.contains(GaussDBAggregateFunction.BIT_OR_DISTINCT));
        assertTrue(values.contains(GaussDBAggregateFunction.BIT_XOR));
        assertTrue(values.contains(GaussDBAggregateFunction.BIT_XOR_DISTINCT));
    }
}