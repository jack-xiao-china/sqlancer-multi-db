package sqlancer.fucci.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PredicateEvaluatorTest {

    private ColumnResolver createResolver() {
        return new ColumnResolver("t0", Arrays.asList("c0", "c1", "c2"));
    }

    private Map<Integer, Object[]> createTestData() {
        Map<Integer, Object[]> data = new HashMap<>();
        data.put(0, new Object[] {1, "hello", 100});
        data.put(1, new Object[] {2, "world", 200});
        data.put(2, new Object[] {3, "hello", 300});
        return data;
    }

    @Test
    void testSimpleEquals() {
        Set<Integer> result = PredicateEvaluator.evaluate("c0 = 2", createResolver(), createTestData());
        assertEquals(Set.of(1), result);
    }

    @Test
    void testGreaterThan() {
        Set<Integer> result = PredicateEvaluator.evaluate("c0 > 1", createResolver(), createTestData());
        assertEquals(Set.of(1, 2), result);
    }

    @Test
    void testLessThanEquals() {
        Set<Integer> result = PredicateEvaluator.evaluate("c2 <= 200", createResolver(), createTestData());
        assertEquals(Set.of(0, 1), result);
    }

    @Test
    void testAndExpression() {
        Set<Integer> result = PredicateEvaluator.evaluate(
                "c0 > 1 AND c2 = 300", createResolver(), createTestData());
        assertEquals(Set.of(2), result);
    }

    @Test
    void testOrExpression() {
        Set<Integer> result = PredicateEvaluator.evaluate(
                "c0 = 1 OR c0 = 3", createResolver(), createTestData());
        assertEquals(Set.of(0, 2), result);
    }

    @Test
    void testNotExpression() {
        Set<Integer> result = PredicateEvaluator.evaluate(
                "NOT c0 = 2", createResolver(), createTestData());
        assertEquals(Set.of(0, 2), result);
    }

    @Test
    void testIsNull() {
        Map<Integer, Object[]> data = new HashMap<>();
        data.put(0, new Object[] {1, null, 100});
        data.put(1, new Object[] {2, "hello", 200});

        Set<Integer> result = PredicateEvaluator.evaluate("c1 IS NULL", createResolver(), data);
        assertEquals(Set.of(0), result);
    }

    @Test
    void testIsNotNull() {
        Map<Integer, Object[]> data = new HashMap<>();
        data.put(0, new Object[] {1, null, 100});
        data.put(1, new Object[] {2, "hello", 200});

        Set<Integer> result = PredicateEvaluator.evaluate("c1 IS NOT NULL", createResolver(), data);
        assertEquals(Set.of(1), result);
    }

    @Test
    void testInExpression() {
        Set<Integer> result = PredicateEvaluator.evaluate(
                "c0 IN (1, 3)", createResolver(), createTestData());
        assertEquals(Set.of(0, 2), result);
    }

    @Test
    void testBetween() {
        Set<Integer> result = PredicateEvaluator.evaluate(
                "c0 BETWEEN 2 AND 3", createResolver(), createTestData());
        assertEquals(Set.of(1, 2), result);
    }

    @Test
    void testLikeExpression() {
        Set<Integer> result = PredicateEvaluator.evaluate(
                "c1 LIKE 'hel%'", createResolver(), createTestData());
        assertEquals(Set.of(0, 2), result);
    }

    @Test
    void testCrossTypeNumericComparison() {
        Map<Integer, Object[]> data = new HashMap<>();
        data.put(0, new Object[] {Integer.valueOf(42), "a", 100});

        // JSqlParser parses 42 as LongValue, JDBC returns Integer
        Set<Integer> result = PredicateEvaluator.evaluate("c0 = 42", createResolver(), data);
        assertEquals(Set.of(0), result);
    }

    @Test
    void testNullPredicateReturnsAll() {
        Set<Integer> result = PredicateEvaluator.evaluate((String) null, createResolver(), createTestData());
        assertEquals(Set.of(0, 1, 2), result);
    }

    @Test
    void testEmptyPredicateReturnsAll() {
        Set<Integer> result = PredicateEvaluator.evaluate("  ", createResolver(), createTestData());
        assertEquals(Set.of(0, 1, 2), result);
    }

    @Test
    void testParseFailureReturnsAll() {
        Set<Integer> result = PredicateEvaluator.evaluate(
                "THIS IS NOT VALID SQL", createResolver(), createTestData());
        assertEquals(Set.of(0, 1, 2), result);
    }

    @Test
    void testArithmeticExpression() {
        Map<Integer, Object[]> data = new HashMap<>();
        data.put(0, new Object[] {10, "a", 100});
        data.put(1, new Object[] {20, "b", 200});

        Set<Integer> result = PredicateEvaluator.evaluate(
                "c0 + 5 > 12", createResolver(), data);
        assertEquals(Set.of(0, 1), result);
    }

    @Test
    void testEvaluateSingle() {
        assertTrue(PredicateEvaluator.evaluateSingle(
                "c0 = 1", createResolver(), new Object[] {1, "hello", 100}));
        assertFalse(PredicateEvaluator.evaluateSingle(
                "c0 = 2", createResolver(), new Object[] {1, "hello", 100}));
    }

    @Test
    void testEvaluateSingleNullPredicate() {
        assertTrue(PredicateEvaluator.evaluateSingle((String) null, createResolver(), new Object[] {1}));
    }

    @Test
    void testTableQualifiedColumn() {
        Set<Integer> result = PredicateEvaluator.evaluate(
                "t0.c0 = 2", createResolver(), createTestData());
        assertEquals(Set.of(1), result);
    }

    @Test
    void testComplexAndOr() {
        Set<Integer> result = PredicateEvaluator.evaluate(
                "(c0 = 1 OR c0 = 2) AND c2 >= 200", createResolver(), createTestData());
        assertEquals(Set.of(1), result);
    }
}
