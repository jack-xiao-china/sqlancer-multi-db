package sqlancer.gaussdbm.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.ast.GaussDBBinaryComparisonOperation.BinaryComparisonOperator;

class GaussDBBinaryComparisonOperationLIKETest {

    @Test
    void testLikeExactMatch() {
        GaussDBConstant left = GaussDBConstant.createStringConstant("abc");
        GaussDBConstant right = GaussDBConstant.createStringConstant("abc");
        GaussDBConstant result = BinaryComparisonOperator.LIKE.getExpectedValue(left, right);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testLikePatternPercent() {
        GaussDBConstant left = GaussDBConstant.createStringConstant("abc");
        GaussDBConstant right = GaussDBConstant.createStringConstant("a%");
        GaussDBConstant result = BinaryComparisonOperator.LIKE.getExpectedValue(left, right);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testLikePatternPercentMiddle() {
        GaussDBConstant left = GaussDBConstant.createStringConstant("abc");
        GaussDBConstant right = GaussDBConstant.createStringConstant("a%c");
        GaussDBConstant result = BinaryComparisonOperator.LIKE.getExpectedValue(left, right);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testLikeNoMatch() {
        GaussDBConstant left = GaussDBConstant.createStringConstant("abc");
        GaussDBConstant right = GaussDBConstant.createStringConstant("xyz");
        GaussDBConstant result = BinaryComparisonOperator.LIKE.getExpectedValue(left, right);
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    @Test
    void testLikeNullLeft() {
        GaussDBConstant left = GaussDBConstant.createNullConstant();
        GaussDBConstant right = GaussDBConstant.createStringConstant("abc");
        GaussDBConstant result = BinaryComparisonOperator.LIKE.getExpectedValue(left, right);
        assertTrue(result.isNull());
    }

    @Test
    void testLikeNullRight() {
        GaussDBConstant left = GaussDBConstant.createStringConstant("abc");
        GaussDBConstant right = GaussDBConstant.createNullConstant();
        GaussDBConstant result = BinaryComparisonOperator.LIKE.getExpectedValue(left, right);
        assertTrue(result.isNull());
    }

    @Test
    void testLikeNullBoth() {
        GaussDBConstant left = GaussDBConstant.createNullConstant();
        GaussDBConstant right = GaussDBConstant.createNullConstant();
        GaussDBConstant result = BinaryComparisonOperator.LIKE.getExpectedValue(left, right);
        assertTrue(result.isNull());
    }

    @Test
    void testLikeUnderscore() {
        GaussDBConstant left = GaussDBConstant.createStringConstant("abc");
        GaussDBConstant right = GaussDBConstant.createStringConstant("a_c");
        GaussDBConstant result = BinaryComparisonOperator.LIKE.getExpectedValue(left, right);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testLikeUnderscoreNoMatch() {
        GaussDBConstant left = GaussDBConstant.createStringConstant("abbc");
        GaussDBConstant right = GaussDBConstant.createStringConstant("a_c");
        GaussDBConstant result = BinaryComparisonOperator.LIKE.getExpectedValue(left, right);
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    @Test
    void testLikePercentMatchAll() {
        GaussDBConstant left = GaussDBConstant.createStringConstant("anything");
        GaussDBConstant right = GaussDBConstant.createStringConstant("%");
        GaussDBConstant result = BinaryComparisonOperator.LIKE.getExpectedValue(left, right);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testLikeEmptyString() {
        GaussDBConstant left = GaussDBConstant.createStringConstant("");
        GaussDBConstant right = GaussDBConstant.createStringConstant("");
        GaussDBConstant result = BinaryComparisonOperator.LIKE.getExpectedValue(left, right);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testLikeEmptyStringWithPercent() {
        GaussDBConstant left = GaussDBConstant.createStringConstant("");
        GaussDBConstant right = GaussDBConstant.createStringConstant("%");
        GaussDBConstant result = BinaryComparisonOperator.LIKE.getExpectedValue(left, right);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testLikeTextRepresentation() {
        assertEquals("LIKE", BinaryComparisonOperator.LIKE.getTextRepr());
    }

    @Test
    void testLikeCaseInsensitive() {
        // LikeImplementationHelper uses case-insensitive matching (caseSensitive=false)
        GaussDBConstant left = GaussDBConstant.createStringConstant("ABC");
        GaussDBConstant right = GaussDBConstant.createStringConstant("abc");
        GaussDBConstant result = BinaryComparisonOperator.LIKE.getExpectedValue(left, right);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testLikeCombinedPatterns() {
        GaussDBConstant left = GaussDBConstant.createStringConstant("hello world");
        GaussDBConstant right = GaussDBConstant.createStringConstant("h%o_w%d");
        GaussDBConstant result = BinaryComparisonOperator.LIKE.getExpectedValue(left, right);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }
}