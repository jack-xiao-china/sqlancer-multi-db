package sqlancer.gaussdbm.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation.UnaryPrefixOperator;

class GaussDBUnaryPrefixOperationTest {

    @Test
    void testPlusInt() {
        GaussDBConstant five = GaussDBConstant.createIntConstant(5);
        GaussDBConstant result = UnaryPrefixOperator.PLUS.applyNotNull(five);
        assertTrue(result.isInt());
        assertEquals(5, result.asIntNotNull());
    }

    @Test
    void testPlusNegativeInt() {
        GaussDBConstant negFive = GaussDBConstant.createIntConstant(-5);
        GaussDBConstant result = UnaryPrefixOperator.PLUS.applyNotNull(negFive);
        assertTrue(result.isInt());
        assertEquals(-5, result.asIntNotNull());
    }

    @Test
    void testMinusInt() {
        GaussDBConstant five = GaussDBConstant.createIntConstant(5);
        GaussDBConstant result = UnaryPrefixOperator.MINUS.applyNotNull(five);
        assertTrue(result.isInt());
        assertEquals(-5, result.asIntNotNull());
    }

    @Test
    void testMinusNegativeInt() {
        GaussDBConstant negFive = GaussDBConstant.createIntConstant(-5);
        GaussDBConstant result = UnaryPrefixOperator.MINUS.applyNotNull(negFive);
        assertTrue(result.isInt());
        assertEquals(5, result.asIntNotNull());
    }

    @Test
    void testMinusZero() {
        GaussDBConstant zero = GaussDBConstant.createIntConstant(0);
        GaussDBConstant result = UnaryPrefixOperator.MINUS.applyNotNull(zero);
        assertTrue(result.isInt());
        assertEquals(0, result.asIntNotNull());
    }

    @Test
    void testMinusString() {
        GaussDBConstant str = GaussDBConstant.createStringConstant("123");
        GaussDBConstant result = UnaryPrefixOperator.MINUS.applyNotNull(str);
        assertTrue(result.isInt());
        assertEquals(-123, result.asIntNotNull());
    }

    @Test
    void testMinusStringWithLeadingSpaces() {
        GaussDBConstant str = GaussDBConstant.createStringConstant("  456");
        GaussDBConstant result = UnaryPrefixOperator.MINUS.applyNotNull(str);
        assertTrue(result.isInt());
        assertEquals(-456, result.asIntNotNull());
    }

    @Test
    void testMinusNonNumericString() {
        GaussDBConstant str = GaussDBConstant.createStringConstant("abc");
        GaussDBConstant result = UnaryPrefixOperator.MINUS.applyNotNull(str);
        assertTrue(result.isInt());
        assertEquals(0, result.asIntNotNull());
    }

    @Test
    void testNotBooleanTrue() {
        GaussDBConstant bool = GaussDBConstant.createBooleanConstant(true);
        GaussDBConstant result = UnaryPrefixOperator.NOT.applyNotNull(bool);
        assertTrue(result.isBoolean());
        assertEquals(false, result.asBooleanNotNull());
    }

    @Test
    void testNotBooleanFalse() {
        GaussDBConstant bool = GaussDBConstant.createBooleanConstant(false);
        GaussDBConstant result = UnaryPrefixOperator.NOT.applyNotNull(bool);
        assertTrue(result.isBoolean());
        assertEquals(true, result.asBooleanNotNull());
    }

    @Test
    void testGetExpectedValueWithNull() {
        GaussDBExpression nullExpr = new GaussDBExpression() {
            @Override
            public GaussDBConstant getExpectedValue() {
                return GaussDBConstant.createNullConstant();
            }
        };

        GaussDBUnaryPrefixOperation plusOp = new GaussDBUnaryPrefixOperation(nullExpr, UnaryPrefixOperator.PLUS);
        assertTrue(plusOp.getExpectedValue().isNull());

        GaussDBUnaryPrefixOperation minusOp = new GaussDBUnaryPrefixOperation(nullExpr, UnaryPrefixOperator.MINUS);
        assertTrue(minusOp.getExpectedValue().isNull());

        GaussDBUnaryPrefixOperation notOp = new GaussDBUnaryPrefixOperation(nullExpr, UnaryPrefixOperator.NOT);
        assertTrue(notOp.getExpectedValue().isNull());
    }

    @Test
    void testOperatorTextRepresentation() {
        assertEquals("NOT", UnaryPrefixOperator.NOT.getText());
        assertEquals("+", UnaryPrefixOperator.PLUS.getText());
        assertEquals("-", UnaryPrefixOperator.MINUS.getText());
    }
}