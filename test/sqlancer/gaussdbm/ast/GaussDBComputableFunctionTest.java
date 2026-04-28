package sqlancer.gaussdbm.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBComputableFunction.GaussDBFunction;

class GaussDBComputableFunctionTest {

    // ========== Function name and argument count tests ==========

    @Test
    void testMathFunctionNames() {
        assertEquals("ABS", GaussDBFunction.ABS.getName());
        assertEquals("CEIL", GaussDBFunction.CEIL.getName());
        assertEquals("FLOOR", GaussDBFunction.FLOOR.getName());
        assertEquals("ROUND", GaussDBFunction.ROUND.getName());
        assertEquals("MOD", GaussDBFunction.MOD.getName());
        assertEquals("SIGN", GaussDBFunction.SIGN.getName());
    }

    @Test
    void testStringFunctionNames() {
        assertEquals("CONCAT", GaussDBFunction.CONCAT.getName());
        assertEquals("LENGTH", GaussDBFunction.LENGTH.getName());
        assertEquals("UPPER", GaussDBFunction.UPPER.getName());
        assertEquals("LOWER", GaussDBFunction.LOWER.getName());
        assertEquals("TRIM", GaussDBFunction.TRIM.getName());
    }

    @Test
    void testControlFlowFunctionNames() {
        assertEquals("COALESCE", GaussDBFunction.COALESCE.getName());
        assertEquals("IFNULL", GaussDBFunction.IFNULL.getName());
    }

    @Test
    void testMathFunctionArgumentCounts() {
        assertEquals(1, GaussDBFunction.ABS.getMinNrArgs());
        assertEquals(1, GaussDBFunction.CEIL.getMinNrArgs());
        assertEquals(1, GaussDBFunction.FLOOR.getMinNrArgs());
        assertEquals(1, GaussDBFunction.ROUND.getMinNrArgs());
        assertEquals(2, GaussDBFunction.MOD.getMinNrArgs());
        assertEquals(1, GaussDBFunction.SIGN.getMinNrArgs());
    }

    @Test
    void testStringFunctionArgumentCounts() {
        assertEquals(2, GaussDBFunction.CONCAT.getMinNrArgs());
        assertTrue(GaussDBFunction.CONCAT.isVariadic());
        assertEquals(1, GaussDBFunction.LENGTH.getMinNrArgs());
        assertFalse(GaussDBFunction.LENGTH.isVariadic());
        assertEquals(1, GaussDBFunction.UPPER.getMinNrArgs());
        assertEquals(1, GaussDBFunction.LOWER.getMinNrArgs());
        assertEquals(1, GaussDBFunction.TRIM.getMinNrArgs());
    }

    @Test
    void testControlFlowFunctionArgumentCounts() {
        assertEquals(2, GaussDBFunction.COALESCE.getMinNrArgs());
        assertTrue(GaussDBFunction.COALESCE.isVariadic());
        assertEquals(2, GaussDBFunction.IFNULL.getMinNrArgs());
        assertFalse(GaussDBFunction.IFNULL.isVariadic());
    }

    // ========== Math function expectedValue tests ==========

    @Test
    void testABSPositive() {
        GaussDBConstant arg = GaussDBConstant.createIntConstant(5);
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.ABS.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(5, result.asIntNotNull());
    }

    @Test
    void testABSNegative() {
        GaussDBConstant arg = GaussDBConstant.createIntConstant(-10);
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.ABS.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(10, result.asIntNotNull());
    }

    @Test
    void testABSZero() {
        GaussDBConstant arg = GaussDBConstant.createIntConstant(0);
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.ABS.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(0, result.asIntNotNull());
    }

    @Test
    void testABSNull() {
        GaussDBConstant arg = GaussDBConstant.createNullConstant();
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.ABS.apply(evaluatedArgs, null);
        assertTrue(result.isNull());
    }

    @Test
    void testCEIL() {
        GaussDBConstant arg = GaussDBConstant.createIntConstant(5);
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.CEIL.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(5, result.asIntNotNull());
    }

    @Test
    void testCEILNull() {
        GaussDBConstant arg = GaussDBConstant.createNullConstant();
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.CEIL.apply(evaluatedArgs, null);
        assertTrue(result.isNull());
    }

    @Test
    void testFLOOR() {
        GaussDBConstant arg = GaussDBConstant.createIntConstant(5);
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.FLOOR.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(5, result.asIntNotNull());
    }

    @Test
    void testFLOORNull() {
        GaussDBConstant arg = GaussDBConstant.createNullConstant();
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.FLOOR.apply(evaluatedArgs, null);
        assertTrue(result.isNull());
    }

    @Test
    void testROUND() {
        GaussDBConstant arg = GaussDBConstant.createIntConstant(5);
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.ROUND.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(5, result.asIntNotNull());
    }

    @Test
    void testROUNDNull() {
        GaussDBConstant arg = GaussDBConstant.createNullConstant();
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.ROUND.apply(evaluatedArgs, null);
        assertTrue(result.isNull());
    }

    @Test
    void testMOD() {
        GaussDBConstant arg1 = GaussDBConstant.createIntConstant(10);
        GaussDBConstant arg2 = GaussDBConstant.createIntConstant(3);
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg1, arg2);
        GaussDBConstant result = GaussDBFunction.MOD.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(1, result.asIntNotNull());
    }

    @Test
    void testMODNegative() {
        GaussDBConstant arg1 = GaussDBConstant.createIntConstant(-10);
        GaussDBConstant arg2 = GaussDBConstant.createIntConstant(3);
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg1, arg2);
        GaussDBConstant result = GaussDBFunction.MOD.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(-1, result.asIntNotNull());
    }

    @Test
    void testMODByZero() {
        GaussDBConstant arg1 = GaussDBConstant.createIntConstant(10);
        GaussDBConstant arg2 = GaussDBConstant.createIntConstant(0);
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg1, arg2);
        GaussDBConstant result = GaussDBFunction.MOD.apply(evaluatedArgs, null);
        assertTrue(result.isNull()); // Division by zero returns NULL
    }

    @Test
    void testMODNullFirstArg() {
        GaussDBConstant arg1 = GaussDBConstant.createNullConstant();
        GaussDBConstant arg2 = GaussDBConstant.createIntConstant(3);
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg1, arg2);
        GaussDBConstant result = GaussDBFunction.MOD.apply(evaluatedArgs, null);
        assertTrue(result.isNull());
    }

    @Test
    void testMODNullSecondArg() {
        GaussDBConstant arg1 = GaussDBConstant.createIntConstant(10);
        GaussDBConstant arg2 = GaussDBConstant.createNullConstant();
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg1, arg2);
        GaussDBConstant result = GaussDBFunction.MOD.apply(evaluatedArgs, null);
        assertTrue(result.isNull());
    }

    @Test
    void testSIGNPositive() {
        GaussDBConstant arg = GaussDBConstant.createIntConstant(100);
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.SIGN.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(1, result.asIntNotNull());
    }

    @Test
    void testSIGNZero() {
        GaussDBConstant arg = GaussDBConstant.createIntConstant(0);
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.SIGN.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(0, result.asIntNotNull());
    }

    @Test
    void testSIGNNegative() {
        GaussDBConstant arg = GaussDBConstant.createIntConstant(-50);
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.SIGN.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(-1, result.asIntNotNull());
    }

    @Test
    void testSIGNNull() {
        GaussDBConstant arg = GaussDBConstant.createNullConstant();
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.SIGN.apply(evaluatedArgs, null);
        assertTrue(result.isNull());
    }

    // ========== String function expectedValue tests ==========

    @Test
    void testCONCAT() {
        GaussDBConstant arg1 = GaussDBConstant.createStringConstant("Hello");
        GaussDBConstant arg2 = GaussDBConstant.createStringConstant("World");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg1, arg2);
        GaussDBConstant result = GaussDBFunction.CONCAT.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("HelloWorld", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testCONCATThreeArgs() {
        GaussDBConstant arg1 = GaussDBConstant.createStringConstant("A");
        GaussDBConstant arg2 = GaussDBConstant.createStringConstant("B");
        GaussDBConstant arg3 = GaussDBConstant.createStringConstant("C");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg1, arg2, arg3);
        GaussDBConstant result = GaussDBFunction.CONCAT.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("ABC", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testCONCATWithNull() {
        GaussDBConstant arg1 = GaussDBConstant.createStringConstant("Hello");
        GaussDBConstant arg2 = GaussDBConstant.createNullConstant();
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg1, arg2);
        GaussDBConstant result = GaussDBFunction.CONCAT.apply(evaluatedArgs, null);
        assertTrue(result.isNull()); // CONCAT with NULL returns NULL
    }

    @Test
    void testLENGTH() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("Hello");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.LENGTH.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(5, result.asIntNotNull());
    }

    @Test
    void testLENGTHEmptyString() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.LENGTH.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(0, result.asIntNotNull());
    }

    @Test
    void testLENGTHNull() {
        GaussDBConstant arg = GaussDBConstant.createNullConstant();
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.LENGTH.apply(evaluatedArgs, null);
        assertTrue(result.isNull());
    }

    @Test
    void testUPPER() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("Hello");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.UPPER.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("HELLO", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testUPPERNull() {
        GaussDBConstant arg = GaussDBConstant.createNullConstant();
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.UPPER.apply(evaluatedArgs, null);
        assertTrue(result.isNull());
    }

    @Test
    void testLOWER() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("HELLO");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.LOWER.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("hello", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testLOWERNull() {
        GaussDBConstant arg = GaussDBConstant.createNullConstant();
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.LOWER.apply(evaluatedArgs, null);
        assertTrue(result.isNull());
    }

    @Test
    void testTRIM() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("  Hello  ");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.TRIM.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("Hello", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testTRIMNoSpaces() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("Hello");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.TRIM.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("Hello", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testTRIMNull() {
        GaussDBConstant arg = GaussDBConstant.createNullConstant();
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBFunction.TRIM.apply(evaluatedArgs, null);
        assertTrue(result.isNull());
    }

    // ========== Control flow function expectedValue tests ==========

    @Test
    void testCOALESCEFirstNonNull() {
        GaussDBConstant arg1 = GaussDBConstant.createStringConstant("Hello");
        GaussDBConstant arg2 = GaussDBConstant.createNullConstant();
        GaussDBConstant arg3 = GaussDBConstant.createStringConstant("World");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg1, arg2, arg3);
        GaussDBConstant result = GaussDBFunction.COALESCE.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("Hello", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testCOALESCEFirstNull() {
        GaussDBConstant arg1 = GaussDBConstant.createNullConstant();
        GaussDBConstant arg2 = GaussDBConstant.createStringConstant("Hello");
        GaussDBConstant arg3 = GaussDBConstant.createStringConstant("World");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg1, arg2, arg3);
        GaussDBConstant result = GaussDBFunction.COALESCE.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("Hello", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testCOALESCEAllNull() {
        GaussDBConstant arg1 = GaussDBConstant.createNullConstant();
        GaussDBConstant arg2 = GaussDBConstant.createNullConstant();
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg1, arg2);
        GaussDBConstant result = GaussDBFunction.COALESCE.apply(evaluatedArgs, null);
        assertTrue(result.isNull());
    }

    @Test
    void testIFNULLFirstNonNull() {
        GaussDBConstant arg1 = GaussDBConstant.createStringConstant("Hello");
        GaussDBConstant arg2 = GaussDBConstant.createStringConstant("World");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg1, arg2);
        GaussDBConstant result = GaussDBFunction.IFNULL.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("Hello", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testIFNULLFirstNull() {
        GaussDBConstant arg1 = GaussDBConstant.createNullConstant();
        GaussDBConstant arg2 = GaussDBConstant.createStringConstant("World");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg1, arg2);
        GaussDBConstant result = GaussDBFunction.IFNULL.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("World", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testIFNULLBothNull() {
        GaussDBConstant arg1 = GaussDBConstant.createNullConstant();
        GaussDBConstant arg2 = GaussDBConstant.createNullConstant();
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg1, arg2);
        GaussDBConstant result = GaussDBFunction.IFNULL.apply(evaluatedArgs, null);
        assertTrue(result.isNull());
    }

    // ========== SQL generation tests ==========

    @Test
    void testAsStringABS() {
        GaussDBExpression arg = GaussDBConstant.createIntConstant(-5);
        GaussDBComputableFunction func = new GaussDBComputableFunction(GaussDBFunction.ABS, arg);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("ABS(-5)", sql);
    }

    @Test
    void testAsStringMOD() {
        GaussDBExpression arg1 = GaussDBConstant.createIntConstant(10);
        GaussDBExpression arg2 = GaussDBConstant.createIntConstant(3);
        GaussDBComputableFunction func = new GaussDBComputableFunction(GaussDBFunction.MOD, arg1, arg2);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("MOD(10, 3)", sql);
    }

    @Test
    void testAsStringCONCAT() {
        GaussDBExpression arg1 = GaussDBConstant.createStringConstant("Hello");
        GaussDBExpression arg2 = GaussDBConstant.createStringConstant("World");
        GaussDBComputableFunction func = new GaussDBComputableFunction(GaussDBFunction.CONCAT,
                Arrays.asList(arg1, arg2));
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("CONCAT('Hello', 'World')", sql);
    }

    @Test
    void testAsStringWithNull() {
        GaussDBExpression arg = GaussDBConstant.createNullConstant();
        GaussDBComputableFunction func = new GaussDBComputableFunction(GaussDBFunction.ABS, arg);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("ABS(NULL)", sql);
    }

    @Test
    void testAsStringCOALESCE() {
        GaussDBExpression arg1 = GaussDBConstant.createNullConstant();
        GaussDBExpression arg2 = GaussDBConstant.createStringConstant("Hello");
        GaussDBExpression arg3 = GaussDBConstant.createStringConstant("World");
        GaussDBComputableFunction func = new GaussDBComputableFunction(GaussDBFunction.COALESCE,
                Arrays.asList(arg1, arg2, arg3));
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("COALESCE(NULL, 'Hello', 'World')", sql);
    }

    // ========== getExpectedValue tests for GaussDBComputableFunction ==========

    @Test
    void testGetExpectedValueABS() {
        GaussDBExpression arg = GaussDBConstant.createIntConstant(-10);
        GaussDBComputableFunction func = new GaussDBComputableFunction(GaussDBFunction.ABS, arg);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(10, result.asIntNotNull());
    }

    @Test
    void testGetExpectedValueCONCAT() {
        GaussDBExpression arg1 = GaussDBConstant.createStringConstant("Hello");
        GaussDBExpression arg2 = GaussDBConstant.createStringConstant(" ");
        GaussDBExpression arg3 = GaussDBConstant.createStringConstant("World");
        GaussDBComputableFunction func = new GaussDBComputableFunction(GaussDBFunction.CONCAT,
                Arrays.asList(arg1, arg2, arg3));
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isString());
        assertEquals("Hello World", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testGetExpectedValueIFNULL() {
        GaussDBExpression arg1 = GaussDBConstant.createNullConstant();
        GaussDBExpression arg2 = GaussDBConstant.createIntConstant(5);
        GaussDBComputableFunction func = new GaussDBComputableFunction(GaussDBFunction.IFNULL, arg1, arg2);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(5, result.asIntNotNull());
    }

    // ========== Constructor tests ==========

    @Test
    void testGetFunction() {
        GaussDBExpression arg = GaussDBConstant.createIntConstant(5);
        GaussDBComputableFunction func = new GaussDBComputableFunction(GaussDBFunction.ABS, arg);
        assertEquals(GaussDBFunction.ABS, func.getFunction());
    }

    @Test
    void testGetArgumentsSingleArg() {
        GaussDBExpression arg = GaussDBConstant.createIntConstant(5);
        GaussDBComputableFunction func = new GaussDBComputableFunction(GaussDBFunction.ABS, arg);
        List<GaussDBExpression> args = func.getArguments();
        assertEquals(1, args.size());
        assertEquals(arg, args.get(0));
    }

    @Test
    void testGetArgumentsMultipleArgs() {
        GaussDBExpression arg1 = GaussDBConstant.createIntConstant(10);
        GaussDBExpression arg2 = GaussDBConstant.createIntConstant(3);
        GaussDBComputableFunction func = new GaussDBComputableFunction(GaussDBFunction.MOD, arg1, arg2);
        List<GaussDBExpression> args = func.getArguments();
        assertEquals(2, args.size());
        assertEquals(arg1, args.get(0));
        assertEquals(arg2, args.get(1));
    }

    @Test
    void testGetArgumentsList() {
        GaussDBExpression arg1 = GaussDBConstant.createStringConstant("A");
        GaussDBExpression arg2 = GaussDBConstant.createStringConstant("B");
        GaussDBExpression arg3 = GaussDBConstant.createStringConstant("C");
        List<GaussDBExpression> argList = Arrays.asList(arg1, arg2, arg3);
        GaussDBComputableFunction func = new GaussDBComputableFunction(GaussDBFunction.CONCAT, argList);
        List<GaussDBExpression> args = func.getArguments();
        assertEquals(3, args.size());
        assertEquals(arg1, args.get(0));
        assertEquals(arg2, args.get(1));
        assertEquals(arg3, args.get(2));
    }
}