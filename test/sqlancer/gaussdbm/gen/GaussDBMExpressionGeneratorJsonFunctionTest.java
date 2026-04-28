package sqlancer.gaussdbm.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBConstant;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBJsonFunction;
import sqlancer.gaussdbm.ast.GaussDBJsonFunction.GaussDBJsonFunctionType;

class GaussDBMExpressionGeneratorJsonFunctionTest {

    // ========== JSON_VALID expectedValue tests ==========

    @Test
    void testJSONValidWithValidJsonObject() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("{\"key\": \"value\"}");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_VALID.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(1, result.asIntNotNull());
    }

    @Test
    void testJSONValidWithValidJsonArray() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("[1, 2, 3]");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_VALID.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(1, result.asIntNotNull());
    }

    @Test
    void testJSONValidWithEmptyJsonObject() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("{}");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_VALID.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(1, result.asIntNotNull());
    }

    @Test
    void testJSONValidWithEmptyJsonArray() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("[]");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_VALID.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(1, result.asIntNotNull());
    }

    @Test
    void testJSONValidWithValidJsonPrimitive() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("true");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_VALID.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(1, result.asIntNotNull());
    }

    @Test
    void testJSONValidWithInvalidJson() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("not valid json");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_VALID.apply(evaluatedArgs, null);
        assertTrue(result.isInt());
        assertEquals(0, result.asIntNotNull());
    }

    @Test
    void testJSONValidWithNull() {
        GaussDBConstant arg = GaussDBConstant.createNullConstant();
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_VALID.apply(evaluatedArgs, null);
        assertTrue(result.isNull());
    }

    // ========== JSON_TYPE expectedValue tests ==========

    @Test
    void testJSONTypeWithJsonObject() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("{\"a\": 1}");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_TYPE.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("OBJECT", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testJSONTypeWithJsonArray() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("[1, 2]");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_TYPE.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("ARRAY", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testJSONTypeWithJsonBooleanTrue() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("true");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_TYPE.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("BOOLEAN", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testJSONTypeWithJsonBooleanFalse() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("false");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_TYPE.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("BOOLEAN", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testJSONTypeWithJsonNull() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("null");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_TYPE.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("NULL", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testJSONTypeWithJsonInteger() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("42");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_TYPE.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("INTEGER", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testJSONTypeWithJsonDecimal() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("3.14");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_TYPE.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("DECIMAL", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testJSONTypeWithJsonString() {
        GaussDBConstant arg = GaussDBConstant.createStringConstant("\"hello\"");
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_TYPE.apply(evaluatedArgs, null);
        assertTrue(result.isString());
        assertEquals("STRING", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testJSONTypeWithNullArg() {
        GaussDBConstant arg = GaussDBConstant.createNullConstant();
        List<GaussDBConstant> evaluatedArgs = Arrays.asList(arg);
        GaussDBConstant result = GaussDBJsonFunctionType.JSON_TYPE.apply(evaluatedArgs, null);
        assertTrue(result.isNull());
    }

    // ========== Variadic function tests ==========

    @Test
    void testJSONArrayVariadicArgs() {
        GaussDBExpression arg1 = GaussDBConstant.createIntConstant(1);
        GaussDBExpression arg2 = GaussDBConstant.createStringConstant("hello");
        GaussDBExpression arg3 = GaussDBConstant.createNullConstant();
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_ARRAY, arg1, arg2, arg3);
        assertEquals(GaussDBJsonFunctionType.JSON_ARRAY, func.getFunction());
        assertEquals(3, func.getArguments().size());
    }

    @Test
    void testJSONEmptyArray() {
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_ARRAY);
        assertEquals(GaussDBJsonFunctionType.JSON_ARRAY, func.getFunction());
        assertEquals(0, func.getArguments().size());
    }

    @Test
    void testJSONObjectVariadicArgs() {
        GaussDBExpression key1 = GaussDBConstant.createStringConstant("key1");
        GaussDBExpression val1 = GaussDBConstant.createIntConstant(1);
        GaussDBExpression key2 = GaussDBConstant.createStringConstant("key2");
        GaussDBExpression val2 = GaussDBConstant.createStringConstant("value");
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_OBJECT, key1, val1, key2, val2);
        assertEquals(GaussDBJsonFunctionType.JSON_OBJECT, func.getFunction());
        assertEquals(4, func.getArguments().size());
    }

    // ========== JSON_EXTRACT specific tests ==========

    @Test
    void testJsonExtractArgs() {
        GaussDBExpression jsonDoc = GaussDBConstant.createStringConstant("{\"a\": 1}");
        GaussDBExpression jsonPath = GaussDBConstant.createStringConstant("$.a");
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_EXTRACT, jsonDoc, jsonPath);
        assertEquals(GaussDBJsonFunctionType.JSON_EXTRACT, func.getFunction());
        assertEquals(2, func.getArguments().size());
        assertTrue(func.getArguments().get(0) instanceof GaussDBConstant);
        assertTrue(func.getArguments().get(1) instanceof GaussDBConstant);
    }

    // ========== SQL generation tests ==========

    @Test
    void testAsStringJSONExtract() {
        GaussDBExpression arg1 = GaussDBConstant.createStringConstant("{\"a\": 1}");
        GaussDBExpression arg2 = GaussDBConstant.createStringConstant("$.a");
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_EXTRACT, arg1, arg2);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("JSON_EXTRACT('{\"a\": 1}', '$.a')", sql);
    }

    @Test
    void testAsStringJSONContains() {
        GaussDBExpression arg1 = GaussDBConstant.createStringConstant("{\"a\": [1, 2]}");
        GaussDBExpression arg2 = GaussDBConstant.createStringConstant("1");
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_CONTAINS, arg1, arg2);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("JSON_CONTAINS('{\"a\": [1, 2]}', '1')", sql);
    }

    @Test
    void testAsStringJSONKeys() {
        GaussDBExpression arg = GaussDBConstant.createStringConstant("{\"a\": 1}");
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_KEYS, arg);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("JSON_KEYS('{\"a\": 1}')", sql);
    }

    @Test
    void testAsStringJSONType() {
        GaussDBExpression arg = GaussDBConstant.createStringConstant("[1, 2]");
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_TYPE, arg);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("JSON_TYPE('[1, 2]')", sql);
    }

    @Test
    void testAsStringJSONValid() {
        GaussDBExpression arg = GaussDBConstant.createStringConstant("{\"valid\": true}");
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_VALID, arg);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("JSON_VALID('{\"valid\": true}')", sql);
    }

    @Test
    void testAsStringJSONArray() {
        GaussDBExpression arg1 = GaussDBConstant.createIntConstant(1);
        GaussDBExpression arg2 = GaussDBConstant.createStringConstant("hello");
        GaussDBExpression arg3 = GaussDBConstant.createNullConstant();
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_ARRAY,
                Arrays.asList(arg1, arg2, arg3));
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("JSON_ARRAY(1, 'hello', NULL)", sql);
    }

    @Test
    void testAsStringJSONEmptyArray() {
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_ARRAY);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("JSON_ARRAY()", sql);
    }

    @Test
    void testAsStringJSONRemove() {
        GaussDBExpression arg1 = GaussDBConstant.createStringConstant("{\"a\": 1}");
        GaussDBExpression arg2 = GaussDBConstant.createStringConstant("$.a");
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_REMOVE, arg1, arg2);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("JSON_REMOVE('{\"a\": 1}', '$.a')", sql);
    }

    @Test
    void testAsStringJSONReplace() {
        GaussDBExpression arg1 = GaussDBConstant.createStringConstant("{\"a\": 1}");
        GaussDBExpression arg2 = GaussDBConstant.createStringConstant("$.a");
        GaussDBExpression arg3 = GaussDBConstant.createIntConstant(10);
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_REPLACE, arg1, arg2, arg3);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("JSON_REPLACE('{\"a\": 1}', '$.a', 10)", sql);
    }

    @Test
    void testAsStringJSONSet() {
        GaussDBExpression arg1 = GaussDBConstant.createStringConstant("{\"a\": 1}");
        GaussDBExpression arg2 = GaussDBConstant.createStringConstant("$.b");
        GaussDBExpression arg3 = GaussDBConstant.createStringConstant("new");
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_SET, arg1, arg2, arg3);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("JSON_SET('{\"a\": 1}', '$.b', 'new')", sql);
    }

    // ========== getExpectedValue tests for GaussDBJsonFunction ==========

    @Test
    void testGetExpectedValueJSONValid() {
        GaussDBExpression arg = GaussDBConstant.createStringConstant("{\"key\": \"value\"}");
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_VALID, arg);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(1, result.asIntNotNull());
    }

    @Test
    void testGetExpectedValueJSONValidInvalidJson() {
        GaussDBExpression arg = GaussDBConstant.createStringConstant("invalid json");
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_VALID, arg);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(0, result.asIntNotNull());
    }

    @Test
    void testGetExpectedValueJSONType() {
        GaussDBExpression arg = GaussDBConstant.createStringConstant("{\"a\": 1}");
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_TYPE, arg);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isString());
        assertEquals("OBJECT", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    // ========== Constructor tests ==========

    @Test
    void testGetFunction() {
        GaussDBExpression arg = GaussDBConstant.createStringConstant("{}");
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_VALID, arg);
        assertEquals(GaussDBJsonFunctionType.JSON_VALID, func.getFunction());
    }

    @Test
    void testGetArgumentsSingleArg() {
        GaussDBExpression arg = GaussDBConstant.createStringConstant("{}");
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_KEYS, arg);
        List<GaussDBExpression> args = func.getArguments();
        assertEquals(1, args.size());
        assertEquals(arg, args.get(0));
    }

    @Test
    void testGetArgumentsMultipleArgs() {
        GaussDBExpression arg1 = GaussDBConstant.createStringConstant("{\"a\": 1}");
        GaussDBExpression arg2 = GaussDBConstant.createStringConstant("$.a");
        GaussDBJsonFunction func = new GaussDBJsonFunction(GaussDBJsonFunctionType.JSON_EXTRACT, arg1, arg2);
        List<GaussDBExpression> args = func.getArguments();
        assertEquals(2, args.size());
        assertEquals(arg1, args.get(0));
        assertEquals(arg2, args.get(1));
    }

    // ========== Random function selection tests ==========

    @Test
    void testGetRandomFunctionReturnsNonNull() {
        GaussDBJsonFunctionType func = GaussDBJsonFunctionType.getRandomFunction();
        assertNotNull(func);
    }

    @Test
    void testGetRandomQueryFunctionReturnsNonNull() {
        GaussDBJsonFunctionType func = GaussDBJsonFunctionType.getRandomQueryFunction();
        assertNotNull(func);
        assertTrue(func == GaussDBJsonFunctionType.JSON_EXTRACT || func == GaussDBJsonFunctionType.JSON_CONTAINS
                || func == GaussDBJsonFunctionType.JSON_KEYS || func == GaussDBJsonFunctionType.JSON_TYPE
                || func == GaussDBJsonFunctionType.JSON_VALID);
    }

    @Test
    void testGetRandomConstructionFunctionReturnsNonNull() {
        GaussDBJsonFunctionType func = GaussDBJsonFunctionType.getRandomConstructionFunction();
        assertNotNull(func);
        assertTrue(func == GaussDBJsonFunctionType.JSON_ARRAY || func == GaussDBJsonFunctionType.JSON_OBJECT);
    }

    @Test
    void testGetRandomModificationFunctionReturnsNonNull() {
        GaussDBJsonFunctionType func = GaussDBJsonFunctionType.getRandomModificationFunction();
        assertNotNull(func);
        assertTrue(func == GaussDBJsonFunctionType.JSON_REMOVE || func == GaussDBJsonFunctionType.JSON_REPLACE
                || func == GaussDBJsonFunctionType.JSON_SET);
    }

    // ========== Function count tests ==========

    @Test
    void testTotalFunctionCount() {
        assertEquals(10, GaussDBJsonFunctionType.values().length);
    }

    @Test
    void testVariadicFunctionCount() {
        int variadicCount = 0;
        for (GaussDBJsonFunctionType func : GaussDBJsonFunctionType.values()) {
            if (func.isVariadic()) {
                variadicCount++;
            }
        }
        assertEquals(2, variadicCount);
    }

    // ========== Argument count tests ==========

    @Test
    void testJsonQueryFunctionArgumentCounts() {
        assertEquals(2, GaussDBJsonFunctionType.JSON_EXTRACT.getMinNrArgs());
        assertEquals(2, GaussDBJsonFunctionType.JSON_CONTAINS.getMinNrArgs());
        assertEquals(1, GaussDBJsonFunctionType.JSON_KEYS.getMinNrArgs());
        assertEquals(1, GaussDBJsonFunctionType.JSON_TYPE.getMinNrArgs());
        assertEquals(1, GaussDBJsonFunctionType.JSON_VALID.getMinNrArgs());
    }

    @Test
    void testJsonConstructionFunctionArgumentCounts() {
        assertEquals(0, GaussDBJsonFunctionType.JSON_ARRAY.getMinNrArgs());
        assertTrue(GaussDBJsonFunctionType.JSON_ARRAY.isVariadic());
        assertEquals(0, GaussDBJsonFunctionType.JSON_OBJECT.getMinNrArgs());
        assertTrue(GaussDBJsonFunctionType.JSON_OBJECT.isVariadic());
    }

    @Test
    void testJsonModificationFunctionArgumentCounts() {
        assertEquals(2, GaussDBJsonFunctionType.JSON_REMOVE.getMinNrArgs());
        assertTrue(!GaussDBJsonFunctionType.JSON_REMOVE.isVariadic());
        assertEquals(3, GaussDBJsonFunctionType.JSON_REPLACE.getMinNrArgs());
        assertTrue(!GaussDBJsonFunctionType.JSON_REPLACE.isVariadic());
        assertEquals(3, GaussDBJsonFunctionType.JSON_SET.getMinNrArgs());
        assertTrue(!GaussDBJsonFunctionType.JSON_SET.isVariadic());
    }
}