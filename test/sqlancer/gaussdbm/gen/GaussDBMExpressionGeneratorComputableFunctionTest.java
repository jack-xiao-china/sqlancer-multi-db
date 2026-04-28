package sqlancer.gaussdbm.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMOptions;
import sqlancer.gaussdbm.GaussDBMOracleFactory;
import sqlancer.gaussdbm.ast.GaussDBComputableFunction;
import sqlancer.gaussdbm.ast.GaussDBComputableFunction.GaussDBFunction;
import sqlancer.gaussdbm.ast.GaussDBConstant;
import sqlancer.gaussdbm.ast.GaussDBExpression;

class GaussDBMExpressionGeneratorComputableFunctionTest {

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

    @Test
    void testGenerateComputableFunction() {
        GaussDBMExpressionGenerator gen = newGenerator(12345L);

        boolean sawComputableFunction = false;
        for (int i = 0; i < 1000; i++) {
            try {
                GaussDBExpression expr = gen.generateExpression(2);
                if (expr instanceof GaussDBComputableFunction) {
                    sawComputableFunction = true;
                    GaussDBComputableFunction func = (GaussDBComputableFunction) expr;
                    assertNotNull(func.getFunction(), "Function type should not be null");
                    assertNotNull(func.getArguments(), "Arguments list should not be null");
                    assertTrue(func.getArguments().size() >= func.getFunction().getMinNrArgs(),
                            "Arguments count should be at least minimum required");
                    break;
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }
        assertTrue(sawComputableFunction, "expected at least one GaussDBComputableFunction expression generated");
    }

    @Test
    void testComputableFunctionExpectedValueNotNull() {
        GaussDBMExpressionGenerator gen = newGenerator(1L);

        for (int i = 0; i < 100; i++) {
            try {
                GaussDBExpression expr = gen.generateExpression(3);
                if (expr instanceof GaussDBComputableFunction) {
                    GaussDBComputableFunction func = (GaussDBComputableFunction) expr;
                    // Verify getExpectedValue() doesn't throw exception
                    func.getExpectedValue();
                    assertNotNull(func.getFunction(), "Function type should always be set");
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }
    }

    @Test
    void testMathFunctionABSExpectedValue() {
        // Test ABS function expected value calculation
        GaussDBConstant input = GaussDBConstant.createIntConstant(-5);
        GaussDBComputableFunction absFunc = new GaussDBComputableFunction(GaussDBFunction.ABS, input);

        GaussDBConstant result = absFunc.getExpectedValue();
        assertNotNull(result, "ABS expected value should not be null");
        assertTrue(result.isInt(), "ABS result should be integer");
        assertEquals(5, result.asIntNotNull(), "ABS(-5) should equal 5");
    }

    @Test
    void testMathFunctionABSPositiveValue() {
        // Test ABS with positive value
        GaussDBConstant input = GaussDBConstant.createIntConstant(10);
        GaussDBComputableFunction absFunc = new GaussDBComputableFunction(GaussDBFunction.ABS, input);

        GaussDBConstant result = absFunc.getExpectedValue();
        assertNotNull(result, "ABS expected value should not be null");
        assertEquals(10, result.asIntNotNull(), "ABS(10) should equal 10");
    }

    @Test
    void testMathFunctionABSNullValue() {
        // Test ABS with NULL value
        GaussDBConstant input = GaussDBConstant.createNullConstant();
        GaussDBComputableFunction absFunc = new GaussDBComputableFunction(GaussDBFunction.ABS, input);

        GaussDBConstant result = absFunc.getExpectedValue();
        assertNotNull(result, "ABS(NULL) expected value should be NULL constant");
        assertTrue(result.isNull(), "ABS(NULL) should return NULL");
    }

    @Test
    void testMathFunctionSIGNExpectedValue() {
        // Test SIGN function expected value calculation
        GaussDBConstant negativeInput = GaussDBConstant.createIntConstant(-8);
        GaussDBComputableFunction signNegFunc = new GaussDBComputableFunction(GaussDBFunction.SIGN, negativeInput);
        GaussDBConstant negResult = signNegFunc.getExpectedValue();
        assertEquals(-1, negResult.asIntNotNull(), "SIGN(-8) should equal -1");

        GaussDBConstant zeroInput = GaussDBConstant.createIntConstant(0);
        GaussDBComputableFunction signZeroFunc = new GaussDBComputableFunction(GaussDBFunction.SIGN, zeroInput);
        GaussDBConstant zeroResult = signZeroFunc.getExpectedValue();
        assertEquals(0, zeroResult.asIntNotNull(), "SIGN(0) should equal 0");

        GaussDBConstant positiveInput = GaussDBConstant.createIntConstant(5);
        GaussDBComputableFunction signPosFunc = new GaussDBComputableFunction(GaussDBFunction.SIGN, positiveInput);
        GaussDBConstant posResult = signPosFunc.getExpectedValue();
        assertEquals(1, posResult.asIntNotNull(), "SIGN(5) should equal 1");
    }

    @Test
    void testMathFunctionMODExpectedValue() {
        // Test MOD function expected value calculation
        GaussDBConstant arg1 = GaussDBConstant.createIntConstant(10);
        GaussDBConstant arg2 = GaussDBConstant.createIntConstant(3);
        GaussDBComputableFunction modFunc = new GaussDBComputableFunction(GaussDBFunction.MOD,
                Arrays.asList(arg1, arg2));

        GaussDBConstant result = modFunc.getExpectedValue();
        assertNotNull(result, "MOD expected value should not be null");
        assertEquals(1, result.asIntNotNull(), "MOD(10, 3) should equal 1");
    }

    @Test
    void testMathFunctionMODDivisionByZero() {
        // Test MOD with division by zero - should return NULL
        GaussDBConstant arg1 = GaussDBConstant.createIntConstant(10);
        GaussDBConstant arg2 = GaussDBConstant.createIntConstant(0);
        GaussDBComputableFunction modFunc = new GaussDBComputableFunction(GaussDBFunction.MOD,
                Arrays.asList(arg1, arg2));

        GaussDBConstant result = modFunc.getExpectedValue();
        assertNotNull(result, "MOD(10, 0) expected value should be NULL constant");
        assertTrue(result.isNull(), "MOD(10, 0) should return NULL");
    }

    @Test
    void testStringFunctionCONCATExpectedValue() {
        // Test CONCAT function expected value calculation
        GaussDBConstant arg1 = GaussDBConstant.createStringConstant("Hello");
        GaussDBConstant arg2 = GaussDBConstant.createStringConstant("World");
        GaussDBComputableFunction concatFunc = new GaussDBComputableFunction(GaussDBFunction.CONCAT,
                Arrays.asList(arg1, arg2));

        GaussDBConstant result = concatFunc.getExpectedValue();
        assertNotNull(result, "CONCAT expected value should not be null");
        assertTrue(result.isString(), "CONCAT result should be string");
        assertEquals("HelloWorld", ((GaussDBConstant.GaussDBStringConstant) result).getValue(),
                "CONCAT('Hello', 'World') should equal 'HelloWorld'");
    }

    @Test
    void testStringFunctionCONCATWithNull() {
        // Test CONCAT with NULL value - should return NULL
        GaussDBConstant arg1 = GaussDBConstant.createStringConstant("Hello");
        GaussDBConstant arg2 = GaussDBConstant.createNullConstant();
        GaussDBComputableFunction concatFunc = new GaussDBComputableFunction(GaussDBFunction.CONCAT,
                Arrays.asList(arg1, arg2));

        GaussDBConstant result = concatFunc.getExpectedValue();
        assertNotNull(result, "CONCAT with NULL expected value should be NULL constant");
        assertTrue(result.isNull(), "CONCAT('Hello', NULL) should return NULL");
    }

    @Test
    void testStringFunctionUPPERExpectedValue() {
        // Test UPPER function expected value calculation
        GaussDBConstant input = GaussDBConstant.createStringConstant("hello");
        GaussDBComputableFunction upperFunc = new GaussDBComputableFunction(GaussDBFunction.UPPER, input);

        GaussDBConstant result = upperFunc.getExpectedValue();
        assertNotNull(result, "UPPER expected value should not be null");
        assertTrue(result.isString(), "UPPER result should be string");
        assertEquals("HELLO", ((GaussDBConstant.GaussDBStringConstant) result).getValue(),
                "UPPER('hello') should equal 'HELLO'");
    }

    @Test
    void testStringFunctionLOWERExpectedValue() {
        // Test LOWER function expected value calculation
        GaussDBConstant input = GaussDBConstant.createStringConstant("WORLD");
        GaussDBComputableFunction lowerFunc = new GaussDBComputableFunction(GaussDBFunction.LOWER, input);

        GaussDBConstant result = lowerFunc.getExpectedValue();
        assertNotNull(result, "LOWER expected value should not be null");
        assertTrue(result.isString(), "LOWER result should be string");
        assertEquals("world", ((GaussDBConstant.GaussDBStringConstant) result).getValue(),
                "LOWER('WORLD') should equal 'world'");
    }

    @Test
    void testStringFunctionLENGTHExpectedValue() {
        // Test LENGTH function expected value calculation
        GaussDBConstant input = GaussDBConstant.createStringConstant("Hello");
        GaussDBComputableFunction lengthFunc = new GaussDBComputableFunction(GaussDBFunction.LENGTH, input);

        GaussDBConstant result = lengthFunc.getExpectedValue();
        assertNotNull(result, "LENGTH expected value should not be null");
        assertTrue(result.isInt(), "LENGTH result should be integer");
        assertEquals(5, result.asIntNotNull(), "LENGTH('Hello') should equal 5");
    }

    @Test
    void testStringFunctionTRIMExpectedValue() {
        // Test TRIM function expected value calculation
        GaussDBConstant input = GaussDBConstant.createStringConstant("  hello  ");
        GaussDBComputableFunction trimFunc = new GaussDBComputableFunction(GaussDBFunction.TRIM, input);

        GaussDBConstant result = trimFunc.getExpectedValue();
        assertNotNull(result, "TRIM expected value should not be null");
        assertTrue(result.isString(), "TRIM result should be string");
        assertEquals("hello", ((GaussDBConstant.GaussDBStringConstant) result).getValue(),
                "TRIM('  hello  ') should equal 'hello'");
    }

    @Test
    void testControlFlowFunctionCOALESCEExpectedValue() {
        // Test COALESCE function expected value calculation - returns first non-NULL
        GaussDBConstant arg1 = GaussDBConstant.createNullConstant();
        GaussDBConstant arg2 = GaussDBConstant.createIntConstant(5);
        GaussDBConstant arg3 = GaussDBConstant.createIntConstant(10);
        GaussDBComputableFunction coalesceFunc = new GaussDBComputableFunction(GaussDBFunction.COALESCE,
                Arrays.asList(arg1, arg2, arg3));

        GaussDBConstant result = coalesceFunc.getExpectedValue();
        assertNotNull(result, "COALESCE expected value should not be null");
        assertEquals(5, result.asIntNotNull(), "COALESCE(NULL, 5, 10) should equal 5");
    }

    @Test
    void testControlFlowFunctionCOALESCEAllNull() {
        // Test COALESCE with all NULL values
        GaussDBConstant arg1 = GaussDBConstant.createNullConstant();
        GaussDBConstant arg2 = GaussDBConstant.createNullConstant();
        GaussDBComputableFunction coalesceFunc = new GaussDBComputableFunction(GaussDBFunction.COALESCE,
                Arrays.asList(arg1, arg2));

        GaussDBConstant result = coalesceFunc.getExpectedValue();
        assertNotNull(result, "COALESCE all NULL expected value should be NULL constant");
        assertTrue(result.isNull(), "COALESCE(NULL, NULL) should return NULL");
    }

    @Test
    void testControlFlowFunctionIFNULLExpectedValue() {
        // Test IFNULL function expected value calculation
        GaussDBConstant arg1 = GaussDBConstant.createNullConstant();
        GaussDBConstant arg2 = GaussDBConstant.createIntConstant(10);
        GaussDBComputableFunction ifnullFunc = new GaussDBComputableFunction(GaussDBFunction.IFNULL,
                Arrays.asList(arg1, arg2));

        GaussDBConstant result = ifnullFunc.getExpectedValue();
        assertNotNull(result, "IFNULL expected value should not be null");
        assertEquals(10, result.asIntNotNull(), "IFNULL(NULL, 10) should equal 10");

        // Test IFNULL with non-NULL first argument
        GaussDBConstant arg1NonNull = GaussDBConstant.createIntConstant(5);
        GaussDBComputableFunction ifnullFunc2 = new GaussDBComputableFunction(GaussDBFunction.IFNULL,
                Arrays.asList(arg1NonNull, arg2));

        GaussDBConstant result2 = ifnullFunc2.getExpectedValue();
        assertEquals(5, result2.asIntNotNull(), "IFNULL(5, 10) should equal 5");
    }

    @Test
    void testVariadicFunctionArgumentCount() {
        GaussDBMExpressionGenerator gen = newGenerator(42L);

        // Test that variadic functions (CONCAT, COALESCE) can have variable number of args
        boolean sawVariadic = false;
        for (int i = 0; i < 1000; i++) {
            try {
                GaussDBExpression expr = gen.generateExpression(2);
                if (expr instanceof GaussDBComputableFunction) {
                    GaussDBComputableFunction func = (GaussDBComputableFunction) expr;
                    if (func.getFunction().isVariadic()) {
                        sawVariadic = true;
                        int argCount = func.getArguments().size();
                        // Variadic functions should have at least 2 args (minNrArgs)
                        // and can have more based on Randomly.smallNumber()
                        assertTrue(argCount >= 2 && argCount <= 10,
                                "Variadic function argument count should be reasonable (2-10): " + argCount);
                    }
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }
        assertTrue(sawVariadic, "expected at least one variadic function generated");
    }

    @Test
    void testFixedArgumentFunctionCount() {
        GaussDBMExpressionGenerator gen = newGenerator(100L);

        // Test that fixed-argument functions have exactly the expected number of args
        boolean sawFixed = false;
        for (int i = 0; i < 1000; i++) {
            try {
                GaussDBExpression expr = gen.generateExpression(2);
                if (expr instanceof GaussDBComputableFunction) {
                    GaussDBComputableFunction func = (GaussDBComputableFunction) expr;
                    if (!func.getFunction().isVariadic()) {
                        sawFixed = true;
                        int argCount = func.getArguments().size();
                        int expectedArgs = func.getFunction().getMinNrArgs();
                        assertEquals(expectedArgs, argCount,
                                "Fixed-argument function should have exactly minNrArgs arguments");
                    }
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }
        assertTrue(sawFixed, "expected at least one fixed-argument function generated");
    }

    @Test
    void testComputableFunctionCanBeNested() {
        GaussDBMExpressionGenerator gen = newGenerator(777L);

        // Try multiple seeds to find nested computable functions
        // Note: Nesting is probabilistic and depends on the random action selection
        boolean sawNestedComputableFunction = false;
        for (int seed = 0; seed < 10; seed++) {
            gen = newGenerator(seed);
            for (int i = 0; i < 1000; i++) {
                try {
                    // Use depth 0 to allow more nesting room (max depth is typically 5)
                    GaussDBExpression expr = gen.generateExpression(0);
                    if (expr instanceof GaussDBComputableFunction) {
                        GaussDBComputableFunction outer = (GaussDBComputableFunction) expr;
                        for (GaussDBExpression arg : outer.getArguments()) {
                            if (arg instanceof GaussDBComputableFunction) {
                                sawNestedComputableFunction = true;
                                break;
                            }
                        }
                        if (sawNestedComputableFunction) {
                            break;
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // Skip if COLUMN action is picked with empty columns list
                    continue;
                }
            }
            if (sawNestedComputableFunction) {
                break;
            }
        }
        assertTrue(sawNestedComputableFunction,
                "expected at least one nested computable function expression across multiple seeds");
    }
}