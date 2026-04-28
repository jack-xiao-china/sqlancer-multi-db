package sqlancer.gaussdbm.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMOptions;
import sqlancer.gaussdbm.GaussDBMOracleFactory;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBAggregate;
import sqlancer.gaussdbm.ast.GaussDBAggregate.GaussDBAggregateFunction;
import sqlancer.gaussdbm.ast.GaussDBConstant;

class GaussDBMExpressionGeneratorAggregateTest {

    private static GaussDBMExpressionGenerator newGenerator(long seed) {
        GaussDBMOptions gaussdbmOpts = new GaussDBMOptions();
        gaussdbmOpts.oracles = Arrays.asList(GaussDBMOracleFactory.QUERY_PARTITIONING);

        GaussDBMGlobalState state = new GaussDBMGlobalState();
        state.setMainOptions(MainOptions.DEFAULT_OPTIONS);
        state.setDbmsSpecificOptions(gaussdbmOpts);
        state.setRandomly(new Randomly(seed));

        GaussDBMExpressionGenerator gen = new GaussDBMExpressionGenerator(state);
        gen.setColumns(List.of());
        return gen;
    }

    /**
     * Tests that generateAggregate() can generate bitwise aggregate functions.
     */
    @Test
    void testBitwiseAggregateCanBeGenerated() {
        GaussDBMExpressionGenerator gen = newGenerator(42L);

        Set<GaussDBAggregateFunction> generatedBitwise = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            try {
                GaussDBAggregate aggregate = gen.generateAggregate();
                GaussDBAggregateFunction func = aggregate.getFunc();

                // Check if it's a bitwise function
                if (func == GaussDBAggregateFunction.BIT_AND
                        || func == GaussDBAggregateFunction.BIT_AND_DISTINCT
                        || func == GaussDBAggregateFunction.BIT_OR
                        || func == GaussDBAggregateFunction.BIT_OR_DISTINCT
                        || func == GaussDBAggregateFunction.BIT_XOR
                        || func == GaussDBAggregateFunction.BIT_XOR_DISTINCT) {
                    generatedBitwise.add(func);
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }

        // Should have generated at least one bitwise function
        assertTrue(generatedBitwise.size() > 0,
                "Expected at least one bitwise aggregate function generated from 1000 iterations");
    }

    /**
     * Tests that all three basic bitwise functions (BIT_AND, BIT_OR, BIT_XOR) can be generated.
     */
    @Test
    void testAllBasicBitwiseFunctionsCanBeGenerated() {
        GaussDBMExpressionGenerator gen = newGenerator(12345L);

        Set<String> generatedNames = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            try {
                GaussDBAggregate aggregate = gen.generateAggregate();
                String name = aggregate.getFunc().getName();

                if (name.equals("BIT_AND") || name.equals("BIT_OR") || name.equals("BIT_XOR")) {
                    generatedNames.add(name);
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }

        assertTrue(generatedNames.contains("BIT_AND"), "BIT_AND should be generated");
        assertTrue(generatedNames.contains("BIT_OR"), "BIT_OR should be generated");
        assertTrue(generatedNames.contains("BIT_XOR"), "BIT_XOR should be generated");
    }

    /**
     * Tests that BIT_AND SQL generation format is correct.
     */
    @Test
    void testBitAndSqlGenerationFormat() {
        GaussDBAggregate aggregate = new GaussDBAggregate(
                List.of(GaussDBConstant.createIntConstant(1)),
                GaussDBAggregateFunction.BIT_AND);

        String sql = GaussDBToStringVisitor.asString(aggregate);
        assertEquals("BIT_AND(1)", sql, "BIT_AND SQL format should be 'BIT_AND(expr)'");
    }

    /**
     * Tests that BIT_OR SQL generation format is correct.
     */
    @Test
    void testBitOrSqlGenerationFormat() {
        GaussDBAggregate aggregate = new GaussDBAggregate(
                List.of(GaussDBConstant.createIntConstant(42)),
                GaussDBAggregateFunction.BIT_OR);

        String sql = GaussDBToStringVisitor.asString(aggregate);
        assertEquals("BIT_OR(42)", sql, "BIT_OR SQL format should be 'BIT_OR(expr)'");
    }

    /**
     * Tests that BIT_XOR SQL generation format is correct.
     */
    @Test
    void testBitXorSqlGenerationFormat() {
        GaussDBAggregate aggregate = new GaussDBAggregate(
                List.of(GaussDBConstant.createIntConstant(7)),
                GaussDBAggregateFunction.BIT_XOR);

        String sql = GaussDBToStringVisitor.asString(aggregate);
        assertEquals("BIT_XOR(7)", sql, "BIT_XOR SQL format should be 'BIT_XOR(expr)'");
    }

    /**
     * Tests that BIT_AND DISTINCT SQL generation format is correct.
     */
    @Test
    void testBitAndDistinctSqlGenerationFormat() {
        GaussDBAggregate aggregate = new GaussDBAggregate(
                List.of(GaussDBConstant.createIntConstant(1)),
                GaussDBAggregateFunction.BIT_AND_DISTINCT);

        String sql = GaussDBToStringVisitor.asString(aggregate);
        assertEquals("BIT_AND(DISTINCT 1)", sql, "BIT_AND DISTINCT SQL format should be 'BIT_AND(DISTINCT expr)'");
    }

    /**
     * Tests that BIT_OR DISTINCT SQL generation format is correct.
     */
    @Test
    void testBitOrDistinctSqlGenerationFormat() {
        GaussDBAggregate aggregate = new GaussDBAggregate(
                List.of(GaussDBConstant.createIntConstant(2)),
                GaussDBAggregateFunction.BIT_OR_DISTINCT);

        String sql = GaussDBToStringVisitor.asString(aggregate);
        assertEquals("BIT_OR(DISTINCT 2)", sql, "BIT_OR DISTINCT SQL format should be 'BIT_OR(DISTINCT expr)'");
    }

    /**
     * Tests that BIT_XOR DISTINCT SQL generation format is correct.
     */
    @Test
    void testBitXorDistinctSqlGenerationFormat() {
        GaussDBAggregate aggregate = new GaussDBAggregate(
                List.of(GaussDBConstant.createIntConstant(3)),
                GaussDBAggregateFunction.BIT_XOR_DISTINCT);

        String sql = GaussDBToStringVisitor.asString(aggregate);
        assertEquals("BIT_XOR(DISTINCT 3)", sql, "BIT_XOR DISTINCT SQL format should be 'BIT_XOR(DISTINCT expr)'");
    }

    /**
     * Tests that generateAggregate() returns non-null result.
     */
    @Test
    void testGenerateAggregateReturnsNotNull() {
        GaussDBMExpressionGenerator gen = newGenerator(999L);

        int successCount = 0;
        for (int i = 0; i < 100; i++) {
            try {
                GaussDBAggregate aggregate = gen.generateAggregate();
                assertNotNull(aggregate, "generateAggregate should return non-null result");
                assertNotNull(aggregate.getFunc(), "Aggregate function should not be null");
                assertNotNull(aggregate.getExprs(), "Aggregate expressions list should not be null");
                assertFalse(aggregate.getExprs().isEmpty(), "Aggregate expressions list should not be empty");
                successCount++;
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }
        assertTrue(successCount > 50, "Should have at least 50 successful aggregate generations");
    }

    /**
     * Tests that generateAggregate() can generate statistical functions.
     */
    @Test
    void testStatisticalAggregateCanBeGenerated() {
        GaussDBMExpressionGenerator gen = newGenerator(777L);

        Set<GaussDBAggregateFunction> generatedStats = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            try {
                GaussDBAggregate aggregate = gen.generateAggregate();
                GaussDBAggregateFunction func = aggregate.getFunc();

                // Check if it's a statistical function
                if (func == GaussDBAggregateFunction.STDDEV
                        || func == GaussDBAggregateFunction.STDDEV_POP
                        || func == GaussDBAggregateFunction.STDDEV_SAMP
                        || func == GaussDBAggregateFunction.STD
                        || func == GaussDBAggregateFunction.VARIANCE
                        || func == GaussDBAggregateFunction.VAR_POP
                        || func == GaussDBAggregateFunction.VAR_SAMP) {
                    generatedStats.add(func);
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }

        assertTrue(generatedStats.size() > 0,
                "Expected at least one statistical aggregate function generated from 1000 iterations");
    }

    /**
     * Tests that STDDEV SQL generation format is correct.
     */
    @Test
    void testStddevSqlGenerationFormat() {
        GaussDBAggregate aggregate = new GaussDBAggregate(
                List.of(GaussDBConstant.createIntConstant(10)),
                GaussDBAggregateFunction.STDDEV);

        String sql = GaussDBToStringVisitor.asString(aggregate);
        assertEquals("STDDEV(10)", sql, "STDDEV SQL format should be 'STDDEV(expr)'");
    }

    /**
     * Tests that VARIANCE SQL generation format is correct.
     */
    @Test
    void testVarianceSqlGenerationFormat() {
        GaussDBAggregate aggregate = new GaussDBAggregate(
                List.of(GaussDBConstant.createIntConstant(100)),
                GaussDBAggregateFunction.VARIANCE);

        String sql = GaussDBToStringVisitor.asString(aggregate);
        assertEquals("VARIANCE(100)", sql, "VARIANCE SQL format should be 'VARIANCE(expr)'");
    }

    /**
     * Tests that existing aggregate functions (COUNT, SUM, AVG, MIN, MAX) are still generated.
     */
    @Test
    void testExistingAggregatesStillGenerated() {
        GaussDBMExpressionGenerator gen = newGenerator(111L);

        Set<String> generatedNames = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            try {
                GaussDBAggregate aggregate = gen.generateAggregate();
                String name = aggregate.getFunc().getName();
                generatedNames.add(name);
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }

        // Verify basic aggregates still work
        assertTrue(generatedNames.contains("COUNT"), "COUNT should still be generated");
        assertTrue(generatedNames.contains("SUM"), "SUM should still be generated");
        assertTrue(generatedNames.contains("AVG"), "AVG should still be generated");
        assertTrue(generatedNames.contains("MIN"), "MIN should still be generated");
        assertTrue(generatedNames.contains("MAX"), "MAX should still be generated");
    }

    /**
     * Tests that bitwise aggregate functions have isVariadic() returning false.
     */
    @Test
    void testBitwiseAggregatesAreNotVariadic() {
        assertFalse(GaussDBAggregateFunction.BIT_AND.isVariadic(), "BIT_AND should not be variadic");
        assertFalse(GaussDBAggregateFunction.BIT_AND_DISTINCT.isVariadic(), "BIT_AND_DISTINCT should not be variadic");
        assertFalse(GaussDBAggregateFunction.BIT_OR.isVariadic(), "BIT_OR should not be variadic");
        assertFalse(GaussDBAggregateFunction.BIT_OR_DISTINCT.isVariadic(), "BIT_OR_DISTINCT should not be variadic");
        assertFalse(GaussDBAggregateFunction.BIT_XOR.isVariadic(), "BIT_XOR should not be variadic");
        assertFalse(GaussDBAggregateFunction.BIT_XOR_DISTINCT.isVariadic(), "BIT_XOR_DISTINCT should not be variadic");
    }

    /**
     * Tests that statistical aggregate functions have isVariadic() returning false.
     */
    @Test
    void testStatisticalAggregatesAreNotVariadic() {
        assertFalse(GaussDBAggregateFunction.STDDEV.isVariadic(), "STDDEV should not be variadic");
        assertFalse(GaussDBAggregateFunction.STDDEV_POP.isVariadic(), "STDDEV_POP should not be variadic");
        assertFalse(GaussDBAggregateFunction.STDDEV_SAMP.isVariadic(), "STDDEV_SAMP should not be variadic");
        assertFalse(GaussDBAggregateFunction.STD.isVariadic(), "STD should not be variadic");
        assertFalse(GaussDBAggregateFunction.VARIANCE.isVariadic(), "VARIANCE should not be variadic");
        assertFalse(GaussDBAggregateFunction.VAR_POP.isVariadic(), "VAR_POP should not be variadic");
        assertFalse(GaussDBAggregateFunction.VAR_SAMP.isVariadic(), "VAR_SAMP should not be variadic");
    }

    /**
     * Tests that aggregate function count includes bitwise and statistical functions.
     */
    @Test
    void testAggregateFunctionCount() {
        GaussDBAggregateFunction[] values = GaussDBAggregateFunction.values();
        // COUNT: 2, SUM: 2, MIN: 2, MAX: 2, AVG: 2 = 10 basic
        // BIT_AND: 2, BIT_OR: 2, BIT_XOR: 2 = 6 bitwise
        // STDDEV: 1, STDDEV_POP: 1, STDDEV_SAMP: 1, STD: 1 = 4
        // VARIANCE: 1, VAR_POP: 1, VAR_SAMP: 1 = 3
        // Total: 10 + 6 + 7 = 23
        assertEquals(23, values.length, "Total aggregate functions should be 23");
    }
}