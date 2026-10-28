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
    void testBitAndOptionIsNull() {
        assertEquals(null, GaussDBAggregateFunction.BIT_AND.getOption());
    }

    @Test
    void testBitAndIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.BIT_AND.isVariadic());
    }

    // BIT_OR tests
    @Test
    void testBitOrFunctionName() {
        assertEquals("BIT_OR", GaussDBAggregateFunction.BIT_OR.getName());
    }

    @Test
    void testBitOrOptionIsNull() {
        assertEquals(null, GaussDBAggregateFunction.BIT_OR.getOption());
    }

    @Test
    void testBitOrIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.BIT_OR.isVariadic());
    }

    // BIT_XOR tests
    @Test
    void testBitXorFunctionName() {
        assertEquals("BIT_XOR", GaussDBAggregateFunction.BIT_XOR.getName());
    }

    @Test
    void testBitXorOptionIsNull() {
        assertEquals(null, GaussDBAggregateFunction.BIT_XOR.getOption());
    }

    @Test
    void testBitXorIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.BIT_XOR.isVariadic());
    }

    // Verify all bitwise functions are in the enum (no DISTINCT variants: GaussDB-M's parser
    // rejects e.g. BIT_XOR(DISTINCT x) with a syntax error)
    @Test
    void testBitwiseFunctionsIncluded() {
        Set<GaussDBAggregateFunction> values = Arrays.stream(GaussDBAggregateFunction.values())
                .collect(Collectors.toSet());
        assertTrue(values.contains(GaussDBAggregateFunction.BIT_AND));
        assertTrue(values.contains(GaussDBAggregateFunction.BIT_OR));
        assertTrue(values.contains(GaussDBAggregateFunction.BIT_XOR));
    }
}
