package sqlancer.gaussdbm.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.GaussDBToStringVisitor;

class GaussDBCaseWhenTest {

    // ========== Expected value tests ==========

    @Test
    void testCaseWhenTrueThen1Else0() {
        GaussDBExpression whenExpr = GaussDBConstant.createBooleanConstant(true);
        GaussDBExpression thenExpr = GaussDBConstant.createIntConstant(1);
        GaussDBExpression elseExpr = GaussDBConstant.createIntConstant(0);
        GaussDBCaseWhen caseWhen = new GaussDBCaseWhen(whenExpr, thenExpr, elseExpr);
        GaussDBConstant result = caseWhen.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(1, result.asIntNotNull());
    }

    @Test
    void testCaseWhenFalseThen1Else0() {
        GaussDBExpression whenExpr = GaussDBConstant.createBooleanConstant(false);
        GaussDBExpression thenExpr = GaussDBConstant.createIntConstant(1);
        GaussDBExpression elseExpr = GaussDBConstant.createIntConstant(0);
        GaussDBCaseWhen caseWhen = new GaussDBCaseWhen(whenExpr, thenExpr, elseExpr);
        GaussDBConstant result = caseWhen.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(0, result.asIntNotNull());
    }

    @Test
    void testCaseWhenNullThen1Else0() {
        // WHEN NULL THEN 1 ELSE 0 should return NULL (three-valued logic)
        GaussDBExpression whenExpr = GaussDBConstant.createNullConstant();
        GaussDBExpression thenExpr = GaussDBConstant.createIntConstant(1);
        GaussDBExpression elseExpr = GaussDBConstant.createIntConstant(0);
        GaussDBCaseWhen caseWhen = new GaussDBCaseWhen(whenExpr, thenExpr, elseExpr);
        GaussDBConstant result = caseWhen.getExpectedValue();
        assertTrue(result.isNull());
    }

    @Test
    void testCaseWhenTrueThenNullElse0() {
        GaussDBExpression whenExpr = GaussDBConstant.createBooleanConstant(true);
        GaussDBExpression thenExpr = GaussDBConstant.createNullConstant();
        GaussDBExpression elseExpr = GaussDBConstant.createIntConstant(0);
        GaussDBCaseWhen caseWhen = new GaussDBCaseWhen(whenExpr, thenExpr, elseExpr);
        GaussDBConstant result = caseWhen.getExpectedValue();
        assertTrue(result.isNull());
    }

    @Test
    void testCaseWhenFalseThen1ElseNull() {
        GaussDBExpression whenExpr = GaussDBConstant.createBooleanConstant(false);
        GaussDBExpression thenExpr = GaussDBConstant.createIntConstant(1);
        GaussDBExpression elseExpr = GaussDBConstant.createNullConstant();
        GaussDBCaseWhen caseWhen = new GaussDBCaseWhen(whenExpr, thenExpr, elseExpr);
        GaussDBConstant result = caseWhen.getExpectedValue();
        assertTrue(result.isNull());
    }

    @Test
    void testCaseWhenIntNonZeroThen1Else0() {
        // MySQL treats non-zero integers as TRUE
        GaussDBExpression whenExpr = GaussDBConstant.createIntConstant(5);
        GaussDBExpression thenExpr = GaussDBConstant.createIntConstant(1);
        GaussDBExpression elseExpr = GaussDBConstant.createIntConstant(0);
        GaussDBCaseWhen caseWhen = new GaussDBCaseWhen(whenExpr, thenExpr, elseExpr);
        GaussDBConstant result = caseWhen.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(1, result.asIntNotNull());
    }

    @Test
    void testCaseWhenIntZeroThen1Else0() {
        // MySQL treats zero as FALSE
        GaussDBExpression whenExpr = GaussDBConstant.createIntConstant(0);
        GaussDBExpression thenExpr = GaussDBConstant.createIntConstant(1);
        GaussDBExpression elseExpr = GaussDBConstant.createIntConstant(0);
        GaussDBCaseWhen caseWhen = new GaussDBCaseWhen(whenExpr, thenExpr, elseExpr);
        GaussDBConstant result = caseWhen.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(0, result.asIntNotNull());
    }

    // ========== SQL string generation tests ==========

    @Test
    void testAsStringCaseWhenTrue() {
        GaussDBExpression whenExpr = GaussDBConstant.createBooleanConstant(true);
        GaussDBExpression thenExpr = GaussDBConstant.createIntConstant(1);
        GaussDBExpression elseExpr = GaussDBConstant.createIntConstant(0);
        GaussDBCaseWhen caseWhen = new GaussDBCaseWhen(whenExpr, thenExpr, elseExpr);
        String sql = GaussDBToStringVisitor.asString(caseWhen);
        assertEquals("(CASE WHEN TRUE THEN 1 ELSE 0 END)", sql);
    }

    @Test
    void testAsStringCaseWhenFalse() {
        GaussDBExpression whenExpr = GaussDBConstant.createBooleanConstant(false);
        GaussDBExpression thenExpr = GaussDBConstant.createIntConstant(1);
        GaussDBExpression elseExpr = GaussDBConstant.createIntConstant(0);
        GaussDBCaseWhen caseWhen = new GaussDBCaseWhen(whenExpr, thenExpr, elseExpr);
        String sql = GaussDBToStringVisitor.asString(caseWhen);
        assertEquals("(CASE WHEN FALSE THEN 1 ELSE 0 END)", sql);
    }

    @Test
    void testAsStringCaseWhenNull() {
        GaussDBExpression whenExpr = GaussDBConstant.createNullConstant();
        GaussDBExpression thenExpr = GaussDBConstant.createIntConstant(1);
        GaussDBExpression elseExpr = GaussDBConstant.createIntConstant(0);
        GaussDBCaseWhen caseWhen = new GaussDBCaseWhen(whenExpr, thenExpr, elseExpr);
        String sql = GaussDBToStringVisitor.asString(caseWhen);
        assertEquals("(CASE WHEN NULL THEN 1 ELSE 0 END)", sql);
    }

    @Test
    void testAsStringCaseWhenNullElse() {
        GaussDBExpression whenExpr = GaussDBConstant.createBooleanConstant(true);
        GaussDBExpression thenExpr = GaussDBConstant.createIntConstant(1);
        GaussDBExpression elseExpr = GaussDBConstant.createNullConstant();
        GaussDBCaseWhen caseWhen = new GaussDBCaseWhen(whenExpr, thenExpr, elseExpr);
        String sql = GaussDBToStringVisitor.asString(caseWhen);
        assertEquals("(CASE WHEN TRUE THEN 1 ELSE NULL END)", sql);
    }

    // ========== Getter tests ==========

    @Test
    void testGetWhenExpr() {
        GaussDBExpression whenExpr = GaussDBConstant.createBooleanConstant(true);
        GaussDBExpression thenExpr = GaussDBConstant.createIntConstant(1);
        GaussDBExpression elseExpr = GaussDBConstant.createIntConstant(0);
        GaussDBCaseWhen caseWhen = new GaussDBCaseWhen(whenExpr, thenExpr, elseExpr);
        assertEquals(whenExpr, caseWhen.getWhenExpr());
    }

    @Test
    void testGetThenExpr() {
        GaussDBExpression whenExpr = GaussDBConstant.createBooleanConstant(true);
        GaussDBExpression thenExpr = GaussDBConstant.createIntConstant(1);
        GaussDBExpression elseExpr = GaussDBConstant.createIntConstant(0);
        GaussDBCaseWhen caseWhen = new GaussDBCaseWhen(whenExpr, thenExpr, elseExpr);
        assertEquals(thenExpr, caseWhen.getThenExpr());
    }

    @Test
    void testGetElseExpr() {
        GaussDBExpression whenExpr = GaussDBConstant.createBooleanConstant(true);
        GaussDBExpression thenExpr = GaussDBConstant.createIntConstant(1);
        GaussDBExpression elseExpr = GaussDBConstant.createIntConstant(0);
        GaussDBCaseWhen caseWhen = new GaussDBCaseWhen(whenExpr, thenExpr, elseExpr);
        assertEquals(elseExpr, caseWhen.getElseExpr());
    }

    // ========== Nested CASE tests ==========

    @Test
    void testNestedCaseWhenOuterTrue() {
        // CASE WHEN TRUE THEN (CASE WHEN TRUE THEN 10 ELSE 20 END) ELSE 0 END
        GaussDBCaseWhen innerCase = new GaussDBCaseWhen(GaussDBConstant.createBooleanConstant(true),
                GaussDBConstant.createIntConstant(10), GaussDBConstant.createIntConstant(20));
        GaussDBCaseWhen outerCase = new GaussDBCaseWhen(GaussDBConstant.createBooleanConstant(true), innerCase,
                GaussDBConstant.createIntConstant(0));
        GaussDBConstant result = outerCase.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(10, result.asIntNotNull());
    }

    @Test
    void testNestedCaseWhenOuterFalseInnerTrue() {
        // CASE WHEN FALSE THEN (CASE WHEN TRUE THEN 10 ELSE 20 END) ELSE 0 END
        GaussDBCaseWhen innerCase = new GaussDBCaseWhen(GaussDBConstant.createBooleanConstant(true),
                GaussDBConstant.createIntConstant(10), GaussDBConstant.createIntConstant(20));
        GaussDBCaseWhen outerCase = new GaussDBCaseWhen(GaussDBConstant.createBooleanConstant(false), innerCase,
                GaussDBConstant.createIntConstant(0));
        GaussDBConstant result = outerCase.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(0, result.asIntNotNull());
    }

    @Test
    void testNestedCaseWhenOuterTrueInnerFalse() {
        // CASE WHEN TRUE THEN (CASE WHEN FALSE THEN 10 ELSE 20 END) ELSE 0 END
        GaussDBCaseWhen innerCase = new GaussDBCaseWhen(GaussDBConstant.createBooleanConstant(false),
                GaussDBConstant.createIntConstant(10), GaussDBConstant.createIntConstant(20));
        GaussDBCaseWhen outerCase = new GaussDBCaseWhen(GaussDBConstant.createBooleanConstant(true), innerCase,
                GaussDBConstant.createIntConstant(0));
        GaussDBConstant result = outerCase.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(20, result.asIntNotNull());
    }
}