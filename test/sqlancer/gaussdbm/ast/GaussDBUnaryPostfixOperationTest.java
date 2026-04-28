package sqlancer.gaussdbm.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation.UnaryPostfixOperator;
import sqlancer.gaussdbm.GaussDBToStringVisitor;

class GaussDBUnaryPostfixOperationTest {

    // ========== IS_TRUE Tests ==========

    @Test
    void testIsTrueWithTrue() {
        GaussDBConstant bool = GaussDBConstant.createBooleanConstant(true);
        GaussDBConstant result = UnaryPostfixOperator.IS_TRUE.applyNotNull(bool);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testIsTrueWithFalse() {
        GaussDBConstant bool = GaussDBConstant.createBooleanConstant(false);
        GaussDBConstant result = UnaryPostfixOperator.IS_TRUE.applyNotNull(bool);
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    @Test
    void testIsTrueWithNonNullInt() {
        // Non-zero integers are truthy
        GaussDBConstant five = GaussDBConstant.createIntConstant(5);
        GaussDBConstant result = UnaryPostfixOperator.IS_TRUE.applyNotNull(five);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testIsTrueWithZero() {
        // Zero is falsy
        GaussDBConstant zero = GaussDBConstant.createIntConstant(0);
        GaussDBConstant result = UnaryPostfixOperator.IS_TRUE.applyNotNull(zero);
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    @Test
    void testIsTrueWithNull() {
        // IS TRUE for NULL should return FALSE
        GaussDBExpression nullExpr = createNullExpression();
        GaussDBUnaryPostfixOperation op = new GaussDBUnaryPostfixOperation(nullExpr, UnaryPostfixOperator.IS_TRUE);
        GaussDBConstant result = op.getExpectedValue();
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    // ========== IS_FALSE Tests ==========

    @Test
    void testIsFalseWithTrue() {
        GaussDBConstant bool = GaussDBConstant.createBooleanConstant(true);
        GaussDBConstant result = UnaryPostfixOperator.IS_FALSE.applyNotNull(bool);
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    @Test
    void testIsFalseWithFalse() {
        GaussDBConstant bool = GaussDBConstant.createBooleanConstant(false);
        GaussDBConstant result = UnaryPostfixOperator.IS_FALSE.applyNotNull(bool);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testIsFalseWithNonNullInt() {
        // Non-zero integers are truthy, so IS FALSE returns false
        GaussDBConstant five = GaussDBConstant.createIntConstant(5);
        GaussDBConstant result = UnaryPostfixOperator.IS_FALSE.applyNotNull(five);
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    @Test
    void testIsFalseWithZero() {
        // Zero is falsy, so IS FALSE returns true
        GaussDBConstant zero = GaussDBConstant.createIntConstant(0);
        GaussDBConstant result = UnaryPostfixOperator.IS_FALSE.applyNotNull(zero);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testIsFalseWithNull() {
        // IS FALSE for NULL should return FALSE
        GaussDBExpression nullExpr = createNullExpression();
        GaussDBUnaryPostfixOperation op = new GaussDBUnaryPostfixOperation(nullExpr, UnaryPostfixOperator.IS_FALSE);
        GaussDBConstant result = op.getExpectedValue();
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    // ========== IS_NOT_TRUE Tests ==========

    @Test
    void testIsNotTrueWithTrue() {
        GaussDBConstant bool = GaussDBConstant.createBooleanConstant(true);
        GaussDBConstant result = UnaryPostfixOperator.IS_NOT_TRUE.applyNotNull(bool);
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    @Test
    void testIsNotTrueWithFalse() {
        GaussDBConstant bool = GaussDBConstant.createBooleanConstant(false);
        GaussDBConstant result = UnaryPostfixOperator.IS_NOT_TRUE.applyNotNull(bool);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testIsNotTrueWithNonNullInt() {
        // Non-zero integers are truthy, so IS NOT TRUE returns false
        GaussDBConstant five = GaussDBConstant.createIntConstant(5);
        GaussDBConstant result = UnaryPostfixOperator.IS_NOT_TRUE.applyNotNull(five);
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    @Test
    void testIsNotTrueWithZero() {
        // Zero is falsy, so IS NOT TRUE returns true
        GaussDBConstant zero = GaussDBConstant.createIntConstant(0);
        GaussDBConstant result = UnaryPostfixOperator.IS_NOT_TRUE.applyNotNull(zero);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testIsNotTrueWithNull() {
        // IS NOT TRUE for NULL should return TRUE
        GaussDBExpression nullExpr = createNullExpression();
        GaussDBUnaryPostfixOperation op = new GaussDBUnaryPostfixOperation(nullExpr, UnaryPostfixOperator.IS_NOT_TRUE);
        GaussDBConstant result = op.getExpectedValue();
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    // ========== IS_NOT_FALSE Tests ==========

    @Test
    void testIsNotFalseWithTrue() {
        GaussDBConstant bool = GaussDBConstant.createBooleanConstant(true);
        GaussDBConstant result = UnaryPostfixOperator.IS_NOT_FALSE.applyNotNull(bool);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testIsNotFalseWithFalse() {
        GaussDBConstant bool = GaussDBConstant.createBooleanConstant(false);
        GaussDBConstant result = UnaryPostfixOperator.IS_NOT_FALSE.applyNotNull(bool);
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    @Test
    void testIsNotFalseWithNonNullInt() {
        // Non-zero integers are truthy, so IS NOT FALSE returns true
        GaussDBConstant five = GaussDBConstant.createIntConstant(5);
        GaussDBConstant result = UnaryPostfixOperator.IS_NOT_FALSE.applyNotNull(five);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testIsNotFalseWithZero() {
        // Zero is falsy, so IS NOT FALSE returns false
        GaussDBConstant zero = GaussDBConstant.createIntConstant(0);
        GaussDBConstant result = UnaryPostfixOperator.IS_NOT_FALSE.applyNotNull(zero);
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    @Test
    void testIsNotFalseWithNull() {
        // IS NOT FALSE for NULL should return TRUE
        GaussDBExpression nullExpr = createNullExpression();
        GaussDBUnaryPostfixOperation op = new GaussDBUnaryPostfixOperation(nullExpr, UnaryPostfixOperator.IS_NOT_FALSE);
        GaussDBConstant result = op.getExpectedValue();
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    // ========== IS_NULL Tests (existing functionality) ==========

    @Test
    void testIsNullWithNull() {
        GaussDBExpression nullExpr = createNullExpression();
        GaussDBUnaryPostfixOperation op = new GaussDBUnaryPostfixOperation(nullExpr, UnaryPostfixOperator.IS_NULL);
        GaussDBConstant result = op.getExpectedValue();
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testIsNullWithNonNull() {
        GaussDBConstant five = GaussDBConstant.createIntConstant(5);
        GaussDBConstant result = UnaryPostfixOperator.IS_NULL.applyNotNull(five);
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    // ========== IS_NOT_NULL Tests (existing functionality) ==========

    @Test
    void testIsNotNullWithNull() {
        GaussDBExpression nullExpr = createNullExpression();
        GaussDBUnaryPostfixOperation op = new GaussDBUnaryPostfixOperation(nullExpr, UnaryPostfixOperator.IS_NOT_NULL);
        GaussDBConstant result = op.getExpectedValue();
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    @Test
    void testIsNotNullWithNonNull() {
        GaussDBConstant five = GaussDBConstant.createIntConstant(5);
        GaussDBConstant result = UnaryPostfixOperator.IS_NOT_NULL.applyNotNull(five);
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    // ========== SQL Generation Tests ==========

    @Test
    void testIsTrueSqlGeneration() {
        GaussDBConstant bool = GaussDBConstant.createBooleanConstant(true);
        GaussDBUnaryPostfixOperation op = new GaussDBUnaryPostfixOperation(bool, UnaryPostfixOperator.IS_TRUE);
        String sql = GaussDBToStringVisitor.asString(op);
        assertEquals("(TRUE IS TRUE)", sql);
    }

    @Test
    void testIsFalseSqlGeneration() {
        GaussDBConstant bool = GaussDBConstant.createBooleanConstant(false);
        GaussDBUnaryPostfixOperation op = new GaussDBUnaryPostfixOperation(bool, UnaryPostfixOperator.IS_FALSE);
        String sql = GaussDBToStringVisitor.asString(op);
        assertEquals("(FALSE IS FALSE)", sql);
    }

    @Test
    void testIsNotTrueSqlGeneration() {
        GaussDBConstant five = GaussDBConstant.createIntConstant(5);
        GaussDBUnaryPostfixOperation op = new GaussDBUnaryPostfixOperation(five, UnaryPostfixOperator.IS_NOT_TRUE);
        String sql = GaussDBToStringVisitor.asString(op);
        assertEquals("(5 IS NOT TRUE)", sql);
    }

    @Test
    void testIsNotFalseSqlGeneration() {
        GaussDBConstant zero = GaussDBConstant.createIntConstant(0);
        GaussDBUnaryPostfixOperation op = new GaussDBUnaryPostfixOperation(zero, UnaryPostfixOperator.IS_NOT_FALSE);
        String sql = GaussDBToStringVisitor.asString(op);
        assertEquals("(0 IS NOT FALSE)", sql);
    }

    @Test
    void testIsNullSqlGeneration() {
        GaussDBConstant five = GaussDBConstant.createIntConstant(5);
        GaussDBUnaryPostfixOperation op = new GaussDBUnaryPostfixOperation(five, UnaryPostfixOperator.IS_NULL);
        String sql = GaussDBToStringVisitor.asString(op);
        assertEquals("(5 IS NULL)", sql);
    }

    @Test
    void testIsNotNullSqlGeneration() {
        GaussDBConstant five = GaussDBConstant.createIntConstant(5);
        GaussDBUnaryPostfixOperation op = new GaussDBUnaryPostfixOperation(five, UnaryPostfixOperator.IS_NOT_NULL);
        String sql = GaussDBToStringVisitor.asString(op);
        assertEquals("(5 IS NOT NULL)", sql);
    }

    // ========== Operator Text Tests ==========

    @Test
    void testOperatorTextRepresentation() {
        assertEquals("IS NULL", UnaryPostfixOperator.IS_NULL.getText());
        assertEquals("IS NOT NULL", UnaryPostfixOperator.IS_NOT_NULL.getText());
        assertEquals("IS TRUE", UnaryPostfixOperator.IS_TRUE.getText());
        assertEquals("IS FALSE", UnaryPostfixOperator.IS_FALSE.getText());
        assertEquals("IS NOT TRUE", UnaryPostfixOperator.IS_NOT_TRUE.getText());
        assertEquals("IS NOT FALSE", UnaryPostfixOperator.IS_NOT_FALSE.getText());
    }

    @Test
    void testOperatorEnumCount() {
        UnaryPostfixOperator[] operators = UnaryPostfixOperator.values();
        assertEquals(6, operators.length);
    }

    // ========== Helper Methods ==========

    private GaussDBExpression createNullExpression() {
        return new GaussDBExpression() {
            @Override
            public GaussDBConstant getExpectedValue() {
                return GaussDBConstant.createNullConstant();
            }
        };
    }
}