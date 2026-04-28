package sqlancer.gaussdbm.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

/**
 * Tests for statistics aggregate functions (STDDEV, VARIANCE series) in GaussDBMExpressionGenerator.
 */
class GaussDBMExpressionGeneratorStatisticsTest {

    private static GaussDBMExpressionGenerator newGenerator(long seed) {
        GaussDBMOptions gaussdbmOpts = new GaussDBMOptions();
        gaussdbmOpts.oracles = Arrays.asList(GaussDBMOracleFactory.QUERY_PARTITIONING);

        GaussDBMGlobalState state = new GaussDBMGlobalState();
        state.setMainOptions(MainOptions.DEFAULT_OPTIONS);
        state.setDbmsSpecificOptions(gaussdbmOpts);
        state.setRandomly(new Randomly(seed));

        GaussDBMExpressionGenerator gen = new GaussDBMExpressionGenerator(state);
        // Initialize columns as empty list to avoid NullPointerException
        gen.setColumns(List.of());
        return gen;
    }

    // Tests for generateAggregate method including statistics functions

    @Test
    void testGenerateAggregateIncludesStatisticsFunctions() {
        GaussDBMExpressionGenerator gen = newGenerator(12345L);

        // Verify that generateAggregate can generate statistics functions
        // Try multiple times to cover different random selections
        Set<GaussDBAggregateFunction> seenFunctions = generateAndCollectFunctions(gen, 500);

        // Check that at least some statistics functions were generated
        Set<GaussDBAggregateFunction> statsFunctions = Set.of(GaussDBAggregateFunction.STDDEV,
                GaussDBAggregateFunction.STDDEV_POP, GaussDBAggregateFunction.STDDEV_SAMP,
                GaussDBAggregateFunction.STD, GaussDBAggregateFunction.VARIANCE, GaussDBAggregateFunction.VAR_POP,
                GaussDBAggregateFunction.VAR_SAMP);

        // At least one statistics function should have been generated across many attempts
        boolean sawStatistics = seenFunctions.stream().anyMatch(statsFunctions::contains);
        assertTrue(sawStatistics, "Expected at least one statistics function to be generated. Seen functions: "
                + seenFunctions.stream().map(GaussDBAggregateFunction::getName).collect(Collectors.joining(", ")));
    }

    @Test
    void testGenerateAggregateStddevFunction() {
        GaussDBMExpressionGenerator gen = newGenerator(1L);

        // Generate aggregates until we get STDDEV
        GaussDBAggregate aggregate = findAggregateByFunction(gen, GaussDBAggregateFunction.STDDEV, 1000);
        assertNotNull(aggregate, "Should be able to generate STDDEV aggregate within reasonable attempts");

        // Verify SQL generation
        String sql = GaussDBToStringVisitor.asString(aggregate);
        assertTrue(sql.startsWith("STDDEV("), "STDDEV SQL should start with STDDEV(");
    }

    @Test
    void testGenerateAggregateVarianceFunction() {
        GaussDBMExpressionGenerator gen = newGenerator(2L);

        // Generate aggregates until we get VARIANCE
        GaussDBAggregate aggregate = findAggregateByFunction(gen, GaussDBAggregateFunction.VARIANCE, 1000);
        assertNotNull(aggregate, "Should be able to generate VARIANCE aggregate within reasonable attempts");

        // Verify SQL generation
        String sql = GaussDBToStringVisitor.asString(aggregate);
        assertTrue(sql.startsWith("VARIANCE("), "VARIANCE SQL should start with VARIANCE(");
    }

    @Test
    void testGenerateAggregateStddevPopFunction() {
        GaussDBMExpressionGenerator gen = newGenerator(3L);

        GaussDBAggregate aggregate = findAggregateByFunction(gen, GaussDBAggregateFunction.STDDEV_POP, 1000);
        assertNotNull(aggregate, "Should be able to generate STDDEV_POP aggregate within reasonable attempts");

        String sql = GaussDBToStringVisitor.asString(aggregate);
        assertTrue(sql.startsWith("STDDEV_POP("), "STDDEV_POP SQL should start with STDDEV_POP(");
    }

    @Test
    void testGenerateAggregateStddevSampFunction() {
        GaussDBMExpressionGenerator gen = newGenerator(4L);

        GaussDBAggregate aggregate = findAggregateByFunction(gen, GaussDBAggregateFunction.STDDEV_SAMP, 1000);
        assertNotNull(aggregate, "Should be able to generate STDDEV_SAMP aggregate within reasonable attempts");

        String sql = GaussDBToStringVisitor.asString(aggregate);
        assertTrue(sql.startsWith("STDDEV_SAMP("), "STDDEV_SAMP SQL should start with STDDEV_SAMP(");
    }

    @Test
    void testGenerateAggregateStdFunction() {
        GaussDBMExpressionGenerator gen = newGenerator(5L);

        GaussDBAggregate aggregate = findAggregateByFunction(gen, GaussDBAggregateFunction.STD, 1000);
        assertNotNull(aggregate, "Should be able to generate STD aggregate within reasonable attempts");

        String sql = GaussDBToStringVisitor.asString(aggregate);
        assertTrue(sql.startsWith("STD("), "STD SQL should start with STD(");
    }

    @Test
    void testGenerateAggregateVarPopFunction() {
        GaussDBMExpressionGenerator gen = newGenerator(6L);

        GaussDBAggregate aggregate = findAggregateByFunction(gen, GaussDBAggregateFunction.VAR_POP, 1000);
        assertNotNull(aggregate, "Should be able to generate VAR_POP aggregate within reasonable attempts");

        String sql = GaussDBToStringVisitor.asString(aggregate);
        assertTrue(sql.startsWith("VAR_POP("), "VAR_POP SQL should start with VAR_POP(");
    }

    @Test
    void testGenerateAggregateVarSampFunction() {
        GaussDBMExpressionGenerator gen = newGenerator(7L);

        GaussDBAggregate aggregate = findAggregateByFunction(gen, GaussDBAggregateFunction.VAR_SAMP, 1000);
        assertNotNull(aggregate, "Should be able to generate VAR_SAMP aggregate within reasonable attempts");

        String sql = GaussDBToStringVisitor.asString(aggregate);
        assertTrue(sql.startsWith("VAR_SAMP("), "VAR_SAMP SQL should start with VAR_SAMP(");
    }

    // Tests for expectedValue returning null for statistics functions

    @Test
    void testStatisticsAggregateExpectedValueReturnsNull() {
        // Create aggregates with statistics functions and verify expectedValue returns null
        GaussDBConstant expr = GaussDBConstant.createIntConstant(1);

        // STDDEV series
        assertNull(createAggregateAndGetExpectedValue(expr, GaussDBAggregateFunction.STDDEV));
        assertNull(createAggregateAndGetExpectedValue(expr, GaussDBAggregateFunction.STDDEV_POP));
        assertNull(createAggregateAndGetExpectedValue(expr, GaussDBAggregateFunction.STDDEV_SAMP));
        assertNull(createAggregateAndGetExpectedValue(expr, GaussDBAggregateFunction.STD));

        // VARIANCE series
        assertNull(createAggregateAndGetExpectedValue(expr, GaussDBAggregateFunction.VARIANCE));
        assertNull(createAggregateAndGetExpectedValue(expr, GaussDBAggregateFunction.VAR_POP));
        assertNull(createAggregateAndGetExpectedValue(expr, GaussDBAggregateFunction.VAR_SAMP));
    }

    @Test
    void testAllAggregateFunctionsExpectedValueReturnsNull() {
        // All aggregate functions should return null for expectedValue
        // since they require actual data to compute
        GaussDBConstant expr = GaussDBConstant.createIntConstant(1);

        for (GaussDBAggregateFunction func : GaussDBAggregateFunction.values()) {
            GaussDBAggregate aggregate = new GaussDBAggregate(List.of(expr), func);
            assertNull(aggregate.getExpectedValue(),
                    func.getName() + " aggregate expectedValue should return null");
        }
    }

    // Tests for generateStatisticsAggregate method (if implemented)
    // Currently generateAggregate uses Randomly.fromOptions which includes all functions

    @Test
    void testGenerateAggregateCoversAllFunctionTypes() {
        GaussDBMExpressionGenerator gen = newGenerator(42L);

        Set<GaussDBAggregateFunction> seenFunctions = generateAndCollectFunctions(gen, 2000);

        // We should have seen a good variety of functions
        // Note: Due to randomness, we can't guarantee all functions are seen,
        // but we should see at least 8 different function types
        assertTrue(seenFunctions.size() >= 8,
                "Expected to see at least 8 different aggregate function types. Seen: " + seenFunctions.size());
    }

    @Test
    void testStatisticsFunctionsAreNotVariadic() {
        // Verify that statistics functions are not variadic (they take exactly one argument)
        assertFalse(GaussDBAggregateFunction.STDDEV.isVariadic());
        assertFalse(GaussDBAggregateFunction.STDDEV_POP.isVariadic());
        assertFalse(GaussDBAggregateFunction.STDDEV_SAMP.isVariadic());
        assertFalse(GaussDBAggregateFunction.STD.isVariadic());
        assertFalse(GaussDBAggregateFunction.VARIANCE.isVariadic());
        assertFalse(GaussDBAggregateFunction.VAR_POP.isVariadic());
        assertFalse(GaussDBAggregateFunction.VAR_SAMP.isVariadic());
    }

    @Test
    void testStatisticsFunctionsGenerateSingleArgument() {
        GaussDBMExpressionGenerator gen = newGenerator(100L);

        // Statistics functions should have exactly one argument since they're not variadic
        Set<GaussDBAggregateFunction> statsFunctions = Set.of(GaussDBAggregateFunction.STDDEV,
                GaussDBAggregateFunction.STDDEV_POP, GaussDBAggregateFunction.STDDEV_SAMP,
                GaussDBAggregateFunction.STD, GaussDBAggregateFunction.VARIANCE, GaussDBAggregateFunction.VAR_POP,
                GaussDBAggregateFunction.VAR_SAMP);

        for (int i = 0; i < 500; i++) {
            try {
                GaussDBAggregate aggregate = gen.generateAggregate();
                if (statsFunctions.contains(aggregate.getFunc())) {
                    assertEquals(1, aggregate.getExprs().size(),
                            "Statistics function " + aggregate.getFunc().getName() + " should have exactly one argument");
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action generates empty columns
                continue;
            }
        }
    }

    // Helper methods

    private Set<GaussDBAggregateFunction> generateAndCollectFunctions(GaussDBMExpressionGenerator gen, int attempts) {
        Set<GaussDBAggregateFunction> seenFunctions = new HashSet<>();
        for (int i = 0; i < attempts; i++) {
            try {
                GaussDBAggregate aggregate = gen.generateAggregate();
                seenFunctions.add(aggregate.getFunc());
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action generates empty columns
                continue;
            }
        }
        return seenFunctions;
    }

    private GaussDBAggregate findAggregateByFunction(GaussDBMExpressionGenerator gen,
            GaussDBAggregateFunction targetFunc, int maxAttempts) {
        for (int i = 0; i < maxAttempts; i++) {
            try {
                GaussDBAggregate aggregate = gen.generateAggregate();
                if (aggregate.getFunc() == targetFunc) {
                    return aggregate;
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action generates empty columns
                continue;
            }
        }
        return null;
    }

    private GaussDBConstant createAggregateAndGetExpectedValue(GaussDBConstant expr, GaussDBAggregateFunction func) {
        GaussDBAggregate aggregate = new GaussDBAggregate(List.of(expr), func);
        return aggregate.getExpectedValue();
    }
}