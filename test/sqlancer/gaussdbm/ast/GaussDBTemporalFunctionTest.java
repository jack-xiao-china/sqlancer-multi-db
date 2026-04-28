package sqlancer.gaussdbm.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBTemporalFunction.GaussDBTemporalFunctionType;

class GaussDBTemporalFunctionTest {

    // ========== GaussDBTemporalFunctionType Enum Tests ==========

    @Test
    void testNowFunctionName() {
        assertEquals("NOW", GaussDBTemporalFunctionType.NOW.getFunctionName());
    }

    @Test
    void testNowArity() {
        assertEquals(0, GaussDBTemporalFunctionType.NOW.getArity());
    }

    @Test
    void testCurdateFunctionName() {
        assertEquals("CURDATE", GaussDBTemporalFunctionType.CURDATE.getFunctionName());
    }

    @Test
    void testCurdateArity() {
        assertEquals(0, GaussDBTemporalFunctionType.CURDATE.getArity());
    }

    @Test
    void testCurtimeFunctionName() {
        assertEquals("CURTIME", GaussDBTemporalFunctionType.CURTIME.getFunctionName());
    }

    @Test
    void testCurtimeArity() {
        assertEquals(0, GaussDBTemporalFunctionType.CURTIME.getArity());
    }

    @Test
    void testYearFunctionName() {
        assertEquals("YEAR", GaussDBTemporalFunctionType.YEAR.getFunctionName());
    }

    @Test
    void testYearArity() {
        assertEquals(1, GaussDBTemporalFunctionType.YEAR.getArity());
    }

    @Test
    void testMonthFunctionName() {
        assertEquals("MONTH", GaussDBTemporalFunctionType.MONTH.getFunctionName());
    }

    @Test
    void testMonthArity() {
        assertEquals(1, GaussDBTemporalFunctionType.MONTH.getArity());
    }

    @Test
    void testDayFunctionName() {
        assertEquals("DAY", GaussDBTemporalFunctionType.DAY.getFunctionName());
    }

    @Test
    void testDayArity() {
        assertEquals(1, GaussDBTemporalFunctionType.DAY.getArity());
    }

    @Test
    void testHourFunctionName() {
        assertEquals("HOUR", GaussDBTemporalFunctionType.HOUR.getFunctionName());
    }

    @Test
    void testHourArity() {
        assertEquals(1, GaussDBTemporalFunctionType.HOUR.getArity());
    }

    @Test
    void testMinuteFunctionName() {
        assertEquals("MINUTE", GaussDBTemporalFunctionType.MINUTE.getFunctionName());
    }

    @Test
    void testMinuteArity() {
        assertEquals(1, GaussDBTemporalFunctionType.MINUTE.getArity());
    }

    @Test
    void testSecondFunctionName() {
        assertEquals("SECOND", GaussDBTemporalFunctionType.SECOND.getFunctionName());
    }

    @Test
    void testSecondArity() {
        assertEquals(1, GaussDBTemporalFunctionType.SECOND.getArity());
    }

    @Test
    void testDayofweekFunctionName() {
        assertEquals("DAYOFWEEK", GaussDBTemporalFunctionType.DAYOFWEEK.getFunctionName());
    }

    @Test
    void testDayofweekArity() {
        assertEquals(1, GaussDBTemporalFunctionType.DAYOFWEEK.getArity());
    }

    @Test
    void testDatediffFunctionName() {
        assertEquals("DATEDIFF", GaussDBTemporalFunctionType.DATEDIFF.getFunctionName());
    }

    @Test
    void testDatediffArity() {
        assertEquals(2, GaussDBTemporalFunctionType.DATEDIFF.getArity());
    }

    @Test
    void testLastdayFunctionName() {
        assertEquals("LAST_DAY", GaussDBTemporalFunctionType.LAST_DAY.getFunctionName());
    }

    @Test
    void testLastdayArity() {
        assertEquals(1, GaussDBTemporalFunctionType.LAST_DAY.getArity());
    }

    @Test
    void testAllTemporalFunctionTypesCount() {
        assertEquals(12, GaussDBTemporalFunctionType.values().length);
    }

    @Test
    void testRandomIncludesAllFunctions() {
        Set<GaussDBTemporalFunctionType> values = Arrays.stream(GaussDBTemporalFunctionType.values())
                .collect(Collectors.toSet());
        assertTrue(values.contains(GaussDBTemporalFunctionType.NOW));
        assertTrue(values.contains(GaussDBTemporalFunctionType.CURDATE));
        assertTrue(values.contains(GaussDBTemporalFunctionType.CURTIME));
        assertTrue(values.contains(GaussDBTemporalFunctionType.YEAR));
        assertTrue(values.contains(GaussDBTemporalFunctionType.MONTH));
        assertTrue(values.contains(GaussDBTemporalFunctionType.DAY));
        assertTrue(values.contains(GaussDBTemporalFunctionType.HOUR));
        assertTrue(values.contains(GaussDBTemporalFunctionType.MINUTE));
        assertTrue(values.contains(GaussDBTemporalFunctionType.SECOND));
        assertTrue(values.contains(GaussDBTemporalFunctionType.DAYOFWEEK));
        assertTrue(values.contains(GaussDBTemporalFunctionType.DATEDIFF));
        assertTrue(values.contains(GaussDBTemporalFunctionType.LAST_DAY));
    }

    // ========== SQL Generation Tests ==========

    @Test
    void testNowSqlGeneration() {
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.NOW);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("NOW()", sql);
    }

    @Test
    void testCurdateSqlGeneration() {
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.CURDATE);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("CURDATE()", sql);
    }

    @Test
    void testCurtimeSqlGeneration() {
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.CURTIME);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("CURTIME()", sql);
    }

    @Test
    void testYearSqlGeneration() {
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-06-15");
        List<GaussDBExpression> args = Collections.singletonList(date);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.YEAR, args);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("YEAR('2024-06-15')", sql);
    }

    @Test
    void testMonthSqlGeneration() {
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-06-15");
        List<GaussDBExpression> args = Collections.singletonList(date);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.MONTH, args);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("MONTH('2024-06-15')", sql);
    }

    @Test
    void testDatediffSqlGeneration() {
        GaussDBConstant date1 = GaussDBConstant.createStringConstant("2024-06-16");
        GaussDBConstant date2 = GaussDBConstant.createStringConstant("2024-06-15");
        List<GaussDBExpression> args = Arrays.asList(date1, date2);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.DATEDIFF, args);
        String sql = GaussDBToStringVisitor.asString(func);
        assertEquals("DATEDIFF('2024-06-16', '2024-06-15')", sql);
    }

    // ========== getExpectedValue Tests ==========

    @Test
    void testNowExpectedValueReturnsNull() {
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.NOW);
        assertNull(func.getExpectedValue());
    }

    @Test
    void testCurdateExpectedValueReturnsNull() {
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.CURDATE);
        assertNull(func.getExpectedValue());
    }

    @Test
    void testCurtimeExpectedValueReturnsNull() {
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.CURTIME);
        assertNull(func.getExpectedValue());
    }

    @Test
    void testYearExpectedValue() {
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-06-15");
        List<GaussDBExpression> args = Collections.singletonList(date);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.YEAR, args);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(2024, result.asIntNotNull());
    }

    @Test
    void testMonthExpectedValue() {
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-06-15");
        List<GaussDBExpression> args = Collections.singletonList(date);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.MONTH, args);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(6, result.asIntNotNull());
    }

    @Test
    void testDayExpectedValue() {
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-06-15");
        List<GaussDBExpression> args = Collections.singletonList(date);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.DAY, args);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(15, result.asIntNotNull());
    }

    @Test
    void testHourExpectedValue() {
        GaussDBConstant datetime = GaussDBConstant.createStringConstant("2024-06-15 14:30:45");
        List<GaussDBExpression> args = Collections.singletonList(datetime);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.HOUR, args);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(14, result.asIntNotNull());
    }

    @Test
    void testMinuteExpectedValue() {
        GaussDBConstant datetime = GaussDBConstant.createStringConstant("2024-06-15 14:30:45");
        List<GaussDBExpression> args = Collections.singletonList(datetime);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.MINUTE, args);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(30, result.asIntNotNull());
    }

    @Test
    void testSecondExpectedValue() {
        GaussDBConstant datetime = GaussDBConstant.createStringConstant("2024-06-15 14:30:45");
        List<GaussDBExpression> args = Collections.singletonList(datetime);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.SECOND, args);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(45, result.asIntNotNull());
    }

    @Test
    void testDayofweekExpectedValueSunday() {
        // 2024-01-07 is a Sunday
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-01-07");
        List<GaussDBExpression> args = Collections.singletonList(date);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.DAYOFWEEK, args);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(1, result.asIntNotNull()); // Sunday = 1
    }

    @Test
    void testDayofweekExpectedValueMonday() {
        // 2024-01-08 is a Monday
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-01-08");
        List<GaussDBExpression> args = Collections.singletonList(date);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.DAYOFWEEK, args);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(2, result.asIntNotNull()); // Monday = 2
    }

    @Test
    void testDatediffExpectedValue() {
        GaussDBConstant date1 = GaussDBConstant.createStringConstant("2024-06-16");
        GaussDBConstant date2 = GaussDBConstant.createStringConstant("2024-06-15");
        List<GaussDBExpression> args = Arrays.asList(date1, date2);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.DATEDIFF, args);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(1, result.asIntNotNull());
    }

    @Test
    void testDatediffExpectedValueSameDate() {
        GaussDBConstant date1 = GaussDBConstant.createStringConstant("2024-06-15");
        GaussDBConstant date2 = GaussDBConstant.createStringConstant("2024-06-15");
        List<GaussDBExpression> args = Arrays.asList(date1, date2);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.DATEDIFF, args);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(0, result.asIntNotNull());
    }

    @Test
    void testLastdayExpectedValue() {
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-06-15");
        List<GaussDBExpression> args = Collections.singletonList(date);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.LAST_DAY, args);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isString());
        assertEquals("2024-06-30", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testLastdayExpectedValueFebruaryLeapYear() {
        // 2024 is a leap year
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-02-15");
        List<GaussDBExpression> args = Collections.singletonList(date);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.LAST_DAY, args);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isString());
        assertEquals("2024-02-29", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testLastdayExpectedValueFebruaryNonLeapYear() {
        // 2023 is not a leap year
        GaussDBConstant date = GaussDBConstant.createStringConstant("2023-02-15");
        List<GaussDBExpression> args = Collections.singletonList(date);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.LAST_DAY, args);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isString());
        assertEquals("2023-02-28", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testExpectedValueWithNullArgument() {
        GaussDBConstant nullConst = GaussDBConstant.createNullConstant();
        List<GaussDBExpression> args = Collections.singletonList(nullConst);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.YEAR, args);
        GaussDBConstant result = func.getExpectedValue();
        assertTrue(result.isNull());
    }

    // ========== GaussDBTemporalUtil Tests ==========

    @Test
    void testParseDate() {
        LocalDate result = GaussDBTemporalUtil.parseDate("2024-06-15");
        assertEquals(2024, result.getYear());
        assertEquals(6, result.getMonthValue());
        assertEquals(15, result.getDayOfMonth());
    }

    @Test
    void testParseDateTime() {
        LocalDateTime result = GaussDBTemporalUtil.parseDateTime("2024-06-15 14:30:45");
        assertEquals(2024, result.getYear());
        assertEquals(6, result.getMonthValue());
        assertEquals(15, result.getDayOfMonth());
        assertEquals(14, result.getHour());
        assertEquals(30, result.getMinute());
        assertEquals(45, result.getSecond());
    }

    @Test
    void testDateDiff() {
        long diff = GaussDBTemporalUtil.dateDiff("2024-06-16", "2024-06-15");
        assertEquals(1, diff);
    }

    @Test
    void testExtractYear() {
        int year = GaussDBTemporalUtil.extractYear("2024-06-15");
        assertEquals(2024, year);
    }

    @Test
    void testExtractMonth() {
        int month = GaussDBTemporalUtil.extractMonth("2024-06-15");
        assertEquals(6, month);
    }

    @Test
    void testExtractDay() {
        int day = GaussDBTemporalUtil.extractDay("2024-06-15");
        assertEquals(15, day);
    }

    @Test
    void testExtractHour() {
        int hour = GaussDBTemporalUtil.extractHour("2024-06-15 14:30:45");
        assertEquals(14, hour);
    }

    @Test
    void testExtractMinute() {
        int minute = GaussDBTemporalUtil.extractMinute("2024-06-15 14:30:45");
        assertEquals(30, minute);
    }

    @Test
    void testExtractSecond() {
        int second = GaussDBTemporalUtil.extractSecond("2024-06-15 14:30:45");
        assertEquals(45, second);
    }

    @Test
    void testDayOfWeekSunday() {
        // 2024-01-07 is a Sunday
        int dow = GaussDBTemporalUtil.dayOfWeek("2024-01-07");
        assertEquals(1, dow); // MySQL: Sunday = 1
    }

    @Test
    void testDayOfWeekMonday() {
        // 2024-01-08 is a Monday
        int dow = GaussDBTemporalUtil.dayOfWeek("2024-01-08");
        assertEquals(2, dow); // MySQL: Monday = 2
    }

    @Test
    void testLastDayOfMonthJune() {
        LocalDate lastDay = GaussDBTemporalUtil.lastDayOfMonth("2024-06-15");
        assertEquals(30, lastDay.getDayOfMonth());
    }

    @Test
    void testLastDayOfMonthFebruaryLeapYear() {
        LocalDate lastDay = GaussDBTemporalUtil.lastDayOfMonth("2024-02-15");
        assertEquals(29, lastDay.getDayOfMonth());
    }

    @Test
    void testLastDayOfMonthFebruaryNonLeapYear() {
        LocalDate lastDay = GaussDBTemporalUtil.lastDayOfMonth("2023-02-15");
        assertEquals(28, lastDay.getDayOfMonth());
    }

    // ========== Constructor Validation Tests ==========

    @Test
    void testConstructorValidationForZeroArgFunction() {
        // Should succeed for NOW with no arguments
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.NOW);
        assertEquals(GaussDBTemporalFunctionType.NOW, func.getFunc());
        assertTrue(func.getArgs().isEmpty());
    }

    @Test
    void testConstructorValidationForOneArgFunction() {
        // Should succeed for YEAR with one argument
        GaussDBConstant date = GaussDBConstant.createStringConstant("2024-06-15");
        List<GaussDBExpression> args = Collections.singletonList(date);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.YEAR, args);
        assertEquals(GaussDBTemporalFunctionType.YEAR, func.getFunc());
        assertEquals(1, func.getArgs().size());
    }

    @Test
    void testConstructorValidationForTwoArgFunction() {
        // Should succeed for DATEDIFF with two arguments
        GaussDBConstant date1 = GaussDBConstant.createStringConstant("2024-06-16");
        GaussDBConstant date2 = GaussDBConstant.createStringConstant("2024-06-15");
        List<GaussDBExpression> args = Arrays.asList(date1, date2);
        GaussDBTemporalFunction func = new GaussDBTemporalFunction(GaussDBTemporalFunctionType.DATEDIFF, args);
        assertEquals(GaussDBTemporalFunctionType.DATEDIFF, func.getFunc());
        assertEquals(2, func.getArgs().size());
    }
}