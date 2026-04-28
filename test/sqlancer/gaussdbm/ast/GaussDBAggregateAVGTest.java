package sqlancer.gaussdbm.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.ast.GaussDBAggregate.GaussDBAggregateFunction;

class GaussDBAggregateAVGTest {

    @Test
    void testAvgFunctionName() {
        assertEquals("AVG", GaussDBAggregateFunction.AVG.getName());
    }

    @Test
    void testAvgDistinctFunctionName() {
        assertEquals("AVG", GaussDBAggregateFunction.AVG_DISTINCT.getName());
    }

    @Test
    void testAvgDistinctOption() {
        assertEquals("DISTINCT", GaussDBAggregateFunction.AVG_DISTINCT.getOption());
    }

    @Test
    void testAvgOptionIsNull() {
        assertEquals(null, GaussDBAggregateFunction.AVG.getOption());
    }

    @Test
    void testAvgIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.AVG.isVariadic());
    }

    @Test
    void testAvgDistinctIsNotVariadic() {
        assertFalse(GaussDBAggregateFunction.AVG_DISTINCT.isVariadic());
    }

    @Test
    void testAvgRandomIncludesAvgFunctions() {
        Set<GaussDBAggregateFunction> values = Arrays.stream(GaussDBAggregateFunction.values())
                .collect(Collectors.toSet());
        assertTrue(values.contains(GaussDBAggregateFunction.AVG));
        assertTrue(values.contains(GaussDBAggregateFunction.AVG_DISTINCT));
    }

    @Test
    void testAllAggregateFunctionsCount() {
        // Verify we have all expected aggregate functions
        assertEquals(23, GaussDBAggregateFunction.values().length);
    }
}