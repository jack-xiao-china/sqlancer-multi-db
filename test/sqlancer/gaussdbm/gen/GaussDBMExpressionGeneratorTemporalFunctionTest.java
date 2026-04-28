package sqlancer.gaussdbm.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBConstant;
import sqlancer.gaussdbm.ast.GaussDBTemporalFunction;
import sqlancer.gaussdbm.ast.GaussDBTemporalFunction.GaussDBTemporalFunctionType;

class GaussDBMExpressionGeneratorTemporalFunctionTest {

    // ========== Zero-Argument Temporal Function Tests ==========

    @Test
    void testNowFunctionGeneration() {
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.NOW);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("NOW()", sql);
    }

    @Test
    void testCurdateFunctionGeneration() {
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.CURDATE);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("CURDATE()", sql);
    }

    @Test
    void testCurtimeFunctionGeneration() {
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.CURTIME);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("CURTIME()", sql);
    }

    // ========== One-Argument Temporal Function Tests ==========

    @Test
    void testYearFunctionGeneration() {
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-06-15");
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.YEAR, List.of(date));
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("YEAR('2024-06-15')", sql);
    }

    @Test
    void testMonthFunctionGeneration() {
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-06-15");
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.MONTH, List.of(date));
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("MONTH('2024-06-15')", sql);
    }

    @Test
    void testDayFunctionGeneration() {
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-06-15");
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.DAY, List.of(date));
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("DAY('2024-06-15')", sql);
    }

    @Test
    void testHourFunctionGeneration() {
        GaussDBConstant datetime = GaussDBConstant.createStringConstant("2024-06-15 14:30:45");
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.HOUR, List.of(datetime));
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("HOUR('2024-06-15 14:30:45')", sql);
    }

    @Test
    void testMinuteFunctionGeneration() {
        GaussDBConstant datetime = GaussDBConstant.createStringConstant("2024-06-15 14:30:45");
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.MINUTE,
                List.of(datetime));
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("MINUTE('2024-06-15 14:30:45')", sql);
    }

    @Test
    void testSecondFunctionGeneration() {
        GaussDBConstant datetime = GaussDBConstant.createStringConstant("2024-06-15 14:30:45");
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.SECOND,
                List.of(datetime));
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("SECOND('2024-06-15 14:30:45')", sql);
    }

    @Test
    void testDayofweekFunctionGeneration() {
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-01-07");
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.DAYOFWEEK,
                List.of(date));
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("DAYOFWEEK('2024-01-07')", sql);
    }

    @Test
    void testLastDayFunctionGeneration() {
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-06-15");
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.LAST_DAY, List.of(date));
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("LAST_DAY('2024-06-15')", sql);
    }

    // ========== Two-Argument Temporal Function Tests ==========

    @Test
    void testDatediffFunctionGeneration() {
        GaussDBConstant date1 = GaussDBConstant.createStringConstant("2024-06-16");
        GaussDBConstant date2 = GaussDBConstant.createStringConstant("2024-06-15");
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.DATEDIFF,
                List.of(date1, date2));
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("DATEDIFF('2024-06-16', '2024-06-15')", sql);
    }

    // ========== Arity Validation Tests ==========

    @Test
    void testNowArityZero() {
        assertEquals(0, GaussDBTemporalFunctionType.NOW.getArity());
    }

    @Test
    void testCurdateArityZero() {
        assertEquals(0, GaussDBTemporalFunctionType.CURDATE.getArity());
    }

    @Test
    void testCurtimeArityZero() {
        assertEquals(0, GaussDBTemporalFunctionType.CURTIME.getArity());
    }

    @Test
    void testYearArityOne() {
        assertEquals(1, GaussDBTemporalFunctionType.YEAR.getArity());
    }

    @Test
    void testMonthArityOne() {
        assertEquals(1, GaussDBTemporalFunctionType.MONTH.getArity());
    }

    @Test
    void testDayArityOne() {
        assertEquals(1, GaussDBTemporalFunctionType.DAY.getArity());
    }

    @Test
    void testHourArityOne() {
        assertEquals(1, GaussDBTemporalFunctionType.HOUR.getArity());
    }

    @Test
    void testMinuteArityOne() {
        assertEquals(1, GaussDBTemporalFunctionType.MINUTE.getArity());
    }

    @Test
    void testSecondArityOne() {
        assertEquals(1, GaussDBTemporalFunctionType.SECOND.getArity());
    }

    @Test
    void testDayofweekArityOne() {
        assertEquals(1, GaussDBTemporalFunctionType.DAYOFWEEK.getArity());
    }

    @Test
    void testLastDayArityOne() {
        assertEquals(1, GaussDBTemporalFunctionType.LAST_DAY.getArity());
    }

    @Test
    void testDatediffArityTwo() {
        assertEquals(2, GaussDBTemporalFunctionType.DATEDIFF.getArity());
    }

    @Test
    void testAllTemporalFunctionTypesCount() {
        assertEquals(12, GaussDBTemporalFunctionType.values().length);
    }

    // ========== getExpectedValue Tests ==========

    @Test
    void testCurrentTimeFunctionsReturnNullExpectedValue() {
        assertNull(new GaussDBTemporalFunction(GaussDBTemporalFunctionType.NOW).getExpectedValue());
        assertNull(new GaussDBTemporalFunction(GaussDBTemporalFunctionType.CURDATE).getExpectedValue());
        assertNull(new GaussDBTemporalFunction(GaussDBTemporalFunctionType.CURTIME).getExpectedValue());
    }

    @Test
    void testYearExpectedValue() {
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-06-15");
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.YEAR, List.of(date));
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(2024, result.asIntNotNull());
    }

    @Test
    void testMonthExpectedValue() {
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-06-15");
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.MONTH, List.of(date));
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(6, result.asIntNotNull());
    }

    @Test
    void testDayExpectedValue() {
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-06-15");
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.DAY, List.of(date));
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(15, result.asIntNotNull());
    }

    @Test
    void testDatediffExpectedValue() {
        GaussDBConstant date1 = GaussDBConstant.createStringConstant("2024-06-16");
        GaussDBConstant date2 = GaussDBConstant.createStringConstant("2024-06-15");
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.DATEDIFF,
                List.of(date1, date2));
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(1, result.asIntNotNull());
    }

    // ========== Function Name Tests ==========

    @Test
    void testFunctionNames() {
        assertEquals("NOW", GaussDBTemporalFunctionType.NOW.getFunctionName());
        assertEquals("CURDATE", GaussDBTemporalFunctionType.CURDATE.getFunctionName());
        assertEquals("CURTIME", GaussDBTemporalFunctionType.CURTIME.getFunctionName());
        assertEquals("YEAR", GaussDBTemporalFunctionType.YEAR.getFunctionName());
        assertEquals("MONTH", GaussDBTemporalFunctionType.MONTH.getFunctionName());
        assertEquals("DAY", GaussDBTemporalFunctionType.DAY.getFunctionName());
        assertEquals("HOUR", GaussDBTemporalFunctionType.HOUR.getFunctionName());
        assertEquals("MINUTE", GaussDBTemporalFunctionType.MINUTE.getFunctionName());
        assertEquals("SECOND", GaussDBTemporalFunctionType.SECOND.getFunctionName());
        assertEquals("DAYOFWEEK", GaussDBTemporalFunctionType.DAYOFWEEK.getFunctionName());
        assertEquals("DATEDIFF", GaussDBTemporalFunctionType.DATEDIFF.getFunctionName());
        assertEquals("LAST_DAY", GaussDBTemporalFunctionType.LAST_DAY.getFunctionName());
    }

    // ========== Random Function Tests ==========

    @Test
    void testGetRandomReturnsValidFunction() {
        for (int i = 0; i < 50; i++) {
            GaussDBTemporalFunctionType func = GaussDBTemporalFunctionType.getRandom();
            assertNotNull(func);
            assertTrue(func.getArity() >= 0);
        }
    }

    @Test
    void testGetRandomCurrentTimeFunction() {
        for (int i = 0; i < 20; i++) {
            GaussDBTemporalFunctionType func = GaussDBTemporalFunctionType.getRandomCurrentTimeFunction();
            assertNotNull(func);
            assertEquals(0, func.getArity());
        }
    }

    @Test
    void testGetRandomExtractionFunction() {
        for (int i = 0; i < 20; i++) {
            GaussDBTemporalFunctionType func = GaussDBTemporalFunctionType.getRandomExtractionFunction();
            assertNotNull(func);
            assertEquals(1, func.getArity());
        }
    }

    @Test
    void testGetRandomCalculationFunction() {
        for (int i = 0; i < 20; i++) {
            GaussDBTemporalFunctionType func = GaussDBTemporalFunctionType.getRandomCalculationFunction();
            assertNotNull(func);
            assertTrue(func.getArity() >= 1);
        }
    }

    // ========== Expression Convertible to SQL Tests ==========

    @Test
    void testZeroArgFunctionsConvertibleToSQL() {
        for (GaussDBTemporalFunctionType type : List.of(GaussDBTemporalFunctionType.NOW,
                GaussDBTemporalFunctionType.CURDATE, GaussDBTemporalFunctionType.CURTIME)) {
            GaussDBTemporalFunction func = new GaussDBTemporalFunction(type);
            String sql = GaussDBToStringVisitor.asString(func);
            assertNotNull(sql);
            assertFalse(sql.isEmpty());
            assertTrue(sql.contains(type.getFunctionName()));
        }
    }

    @Test
    void testOneArgFunctionsConvertibleToSQL() {
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-06-15");
        for (GaussDBTemporalFunctionType type : List.of(GaussDBTemporalFunctionType.YEAR,
                GaussDBTemporalFunctionType.MONTH, GaussDBTemporalFunctionType.DAY, GaussDBTemporalFunctionType.HOUR,
                GaussDBTemporalFunctionType.MINUTE, GaussDBTemporalFunctionType.SECOND,
                GaussDBTemporalFunctionType.DAYOFWEEK, GaussDBTemporalFunctionType.LAST_DAY)) {
            GaussDBTemporalFunction func = new GaussDBTemporalFunction(type, List.of(date));
            String sql = GaussDBToStringVisitor.asString(func);
            assertNotNull(sql);
            assertFalse(sql.isEmpty());
            assertTrue(sql.contains(type.getFunctionName()));
        }
    }
}