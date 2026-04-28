package sqlancer.gaussdbm.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBBinaryArithmeticOperation.GaussDBArithmeticOperator;

class GaussDBBinaryArithmeticOperationTest {

    // ========== Addition tests ==========

    @Test
    void testAddIntegers() {
        GaussDBConstant left = GaussDBConstant.createIntConstant(1);
        GaussDBConstant right = GaussDBConstant.createIntConstant(2);
        GaussDBConstant result = GaussDBArithmeticOperator.ADD.apply(left, right);
        assertTrue(result.isInt());
        assertEquals(3, result.asIntNotNull());
    }

    @Test
    void testAddNegativeIntegers() {
        GaussDBConstant left = GaussDBConstant.createIntConstant(-5);
        GaussDBConstant right = GaussDBConstant.createIntConstant(3);
        GaussDBConstant result = GaussDBArithmeticOperator.ADD.apply(left, right);
        assertTrue(result.isInt());
        assertEquals(-2, result.asIntNotNull());
    }

    @Test
    void testAddWithNull() {
        GaussDBConstant left = GaussDBConstant.createNullConstant();
        GaussDBConstant right = GaussDBConstant.createIntConstant(1);
        GaussDBConstant result = GaussDBArithmeticOperator.ADD.apply(left, right);
        assertTrue(result.isNull());
    }

    @Test
    void testAddBothNull() {
        GaussDBConstant left = GaussDBConstant.createNullConstant();
        GaussDBConstant right = GaussDBConstant.createNullConstant();
        GaussDBConstant result = GaussDBArithmeticOperator.ADD.apply(left, right);
        assertTrue(result.isNull());
    }

    // ========== Subtraction tests ==========

    @Test
    void testSubIntegers() {
        GaussDBConstant left = GaussDBConstant.createIntConstant(5);
        GaussDBConstant right = GaussDBConstant.createIntConstant(3);
        GaussDBConstant result = GaussDBArithmeticOperator.SUB.apply(left, right);
        assertTrue(result.isInt());
        assertEquals(2, result.asIntNotNull());
    }

    @Test
    void testSubNegativeIntegers() {
        GaussDBConstant left = GaussDBConstant.createIntConstant(3);
        GaussDBConstant right = GaussDBConstant.createIntConstant(5);
        GaussDBConstant result = GaussDBArithmeticOperator.SUB.apply(left, right);
        assertTrue(result.isInt());
        assertEquals(-2, result.asIntNotNull());
    }

    @Test
    void testSubWithNull() {
        GaussDBConstant left = GaussDBConstant.createIntConstant(5);
        GaussDBConstant right = GaussDBConstant.createNullConstant();
        GaussDBConstant result = GaussDBArithmeticOperator.SUB.apply(left, right);
        assertTrue(result.isNull());
    }

    // ========== Multiplication tests ==========

    @Test
    void testMulIntegers() {
        GaussDBConstant left = GaussDBConstant.createIntConstant(2);
        GaussDBConstant right = GaussDBConstant.createIntConstant(3);
        GaussDBConstant result = GaussDBArithmeticOperator.MUL.apply(left, right);
        assertTrue(result.isInt());
        assertEquals(6, result.asIntNotNull());
    }

    @Test
    void testMulByZero() {
        GaussDBConstant left = GaussDBConstant.createIntConstant(5);
        GaussDBConstant right = GaussDBConstant.createIntConstant(0);
        GaussDBConstant result = GaussDBArithmeticOperator.MUL.apply(left, right);
        assertTrue(result.isInt());
        assertEquals(0, result.asIntNotNull());
    }

    @Test
    void testMulWithNull() {
        GaussDBConstant left = GaussDBConstant.createNullConstant();
        GaussDBConstant right = GaussDBConstant.createIntConstant(2);
        GaussDBConstant result = GaussDBArithmeticOperator.MUL.apply(left, right);
        assertTrue(result.isNull());
    }

    // ========== Division tests ==========

    @Test
    void testDivIntegers() {
        GaussDBConstant left = GaussDBConstant.createIntConstant(6);
        GaussDBConstant right = GaussDBConstant.createIntConstant(2);
        GaussDBConstant result = GaussDBArithmeticOperator.DIV.apply(left, right);
        assertTrue(result.isInt());
        assertEquals(3, result.asIntNotNull());
    }

    @Test
    void testDivByZero() {
        GaussDBConstant left = GaussDBConstant.createIntConstant(1);
        GaussDBConstant right = GaussDBConstant.createIntConstant(0);
        GaussDBConstant result = GaussDBArithmeticOperator.DIV.apply(left, right);
        // Division by zero returns NULL in MySQL/GaussDB-M
        assertTrue(result.isNull());
    }

    @Test
    void testDivWithNull() {
        GaussDBConstant left = GaussDBConstant.createIntConstant(6);
        GaussDBConstant right = GaussDBConstant.createNullConstant();
        GaussDBConstant result = GaussDBArithmeticOperator.DIV.apply(left, right);
        assertTrue(result.isNull());
    }

    @Test
    void testDivIntegerTruncation() {
        GaussDBConstant left = GaussDBConstant.createIntConstant(7);
        GaussDBConstant right = GaussDBConstant.createIntConstant(2);
        GaussDBConstant result = GaussDBArithmeticOperator.DIV.apply(left, right);
        assertTrue(result.isInt());
        assertEquals(3, result.asIntNotNull()); // Integer division truncates
    }

    // ========== Modulo tests ==========

    @Test
    void testModIntegers() {
        GaussDBConstant left = GaussDBConstant.createIntConstant(7);
        GaussDBConstant right = GaussDBConstant.createIntConstant(3);
        GaussDBConstant result = GaussDBArithmeticOperator.MOD.apply(left, right);
        assertTrue(result.isInt());
        assertEquals(1, result.asIntNotNull());
    }

    @Test
    void testModByZero() {
        GaussDBConstant left = GaussDBConstant.createIntConstant(7);
        GaussDBConstant right = GaussDBConstant.createIntConstant(0);
        GaussDBConstant result = GaussDBArithmeticOperator.MOD.apply(left, right);
        // Modulo by zero returns NULL in MySQL/GaussDB-M
        assertTrue(result.isNull());
    }

    @Test
    void testModWithNull() {
        GaussDBConstant left = GaussDBConstant.createNullConstant();
        GaussDBConstant right = GaussDBConstant.createIntConstant(3);
        GaussDBConstant result = GaussDBArithmeticOperator.MOD.apply(left, right);
        assertTrue(result.isNull());
    }

    // ========== String conversion tests ==========

    @Test
    void testAddStringToInt() {
        GaussDBConstant left = GaussDBConstant.createStringConstant("10");
        GaussDBConstant right = GaussDBConstant.createIntConstant(5);
        GaussDBConstant result = GaussDBArithmeticOperator.ADD.apply(left, right);
        assertTrue(result.isInt());
        assertEquals(15, result.asIntNotNull());
    }

    @Test
    void testSubStringNumbers() {
        GaussDBConstant left = GaussDBConstant.createStringConstant("100");
        GaussDBConstant right = GaussDBConstant.createStringConstant("30");
        GaussDBConstant result = GaussDBArithmeticOperator.SUB.apply(left, right);
        assertTrue(result.isInt());
        assertEquals(70, result.asIntNotNull());
    }

    // ========== Boolean conversion tests ==========

    @Test
    void testAddBooleanToInt() {
        GaussDBConstant left = GaussDBConstant.createBooleanConstant(true);
        GaussDBConstant right = GaussDBConstant.createIntConstant(5);
        GaussDBConstant result = GaussDBArithmeticOperator.ADD.apply(left, right);
        assertTrue(result.isInt());
        assertEquals(6, result.asIntNotNull()); // true = 1
    }

    @Test
    void testMulBooleanFalse() {
        GaussDBConstant left = GaussDBConstant.createIntConstant(5);
        GaussDBConstant right = GaussDBConstant.createBooleanConstant(false);
        GaussDBConstant result = GaussDBArithmeticOperator.MUL.apply(left, right);
        assertTrue(result.isInt());
        assertEquals(0, result.asIntNotNull()); // false = 0
    }

    // ========== Text representation tests ==========

    @Test
    void testAddTextRepresentation() {
        assertEquals("+", GaussDBArithmeticOperator.ADD.getTextRepresentation());
    }

    @Test
    void testSubTextRepresentation() {
        assertEquals("-", GaussDBArithmeticOperator.SUB.getTextRepresentation());
    }

    @Test
    void testMulTextRepresentation() {
        assertEquals("*", GaussDBArithmeticOperator.MUL.getTextRepresentation());
    }

    @Test
    void testDivTextRepresentation() {
        assertEquals("/", GaussDBArithmeticOperator.DIV.getTextRepresentation());
    }

    @Test
    void testModTextRepresentation() {
        assertEquals("%", GaussDBArithmeticOperator.MOD.getTextRepresentation());
    }

    // ========== SQL string generation tests ==========

    @Test
    void testAsStringAdd() {
        GaussDBExpression left = GaussDBConstant.createIntConstant(1);
        GaussDBExpression right = GaussDBConstant.createIntConstant(2);
        GaussDBBinaryArithmeticOperation op = new GaussDBBinaryArithmeticOperation(left, right,
                GaussDBArithmeticOperator.ADD);
        String sql = GaussDBToStringVisitor.asString(op);
        assertEquals("(1 + 2)", sql);
    }

    @Test
    void testAsStringSub() {
        GaussDBExpression left = GaussDBConstant.createIntConstant(5);
        GaussDBExpression right = GaussDBConstant.createIntConstant(3);
        GaussDBBinaryArithmeticOperation op = new GaussDBBinaryArithmeticOperation(left, right,
                GaussDBArithmeticOperator.SUB);
        String sql = GaussDBToStringVisitor.asString(op);
        assertEquals("(5 - 3)", sql);
    }

    @Test
    void testAsStringMul() {
        GaussDBExpression left = GaussDBConstant.createIntConstant(2);
        GaussDBExpression right = GaussDBConstant.createIntConstant(3);
        GaussDBBinaryArithmeticOperation op = new GaussDBBinaryArithmeticOperation(left, right,
                GaussDBArithmeticOperator.MUL);
        String sql = GaussDBToStringVisitor.asString(op);
        assertEquals("(2 * 3)", sql);
    }

    @Test
    void testAsStringDiv() {
        GaussDBExpression left = GaussDBConstant.createIntConstant(6);
        GaussDBExpression right = GaussDBConstant.createIntConstant(2);
        GaussDBBinaryArithmeticOperation op = new GaussDBBinaryArithmeticOperation(left, right,
                GaussDBArithmeticOperator.DIV);
        String sql = GaussDBToStringVisitor.asString(op);
        assertEquals("(6 / 2)", sql);
    }

    @Test
    void testAsStringMod() {
        GaussDBExpression left = GaussDBConstant.createIntConstant(7);
        GaussDBExpression right = GaussDBConstant.createIntConstant(3);
        GaussDBBinaryArithmeticOperation op = new GaussDBBinaryArithmeticOperation(left, right,
                GaussDBArithmeticOperator.MOD);
        String sql = GaussDBToStringVisitor.asString(op);
        assertEquals("(7 % 3)", sql);
    }

    @Test
    void testAsStringWithNull() {
        GaussDBExpression left = GaussDBConstant.createNullConstant();
        GaussDBExpression right = GaussDBConstant.createIntConstant(1);
        GaussDBBinaryArithmeticOperation op = new GaussDBBinaryArithmeticOperation(left, right,
                GaussDBArithmeticOperator.ADD);
        String sql = GaussDBToStringVisitor.asString(op);
        assertEquals("(NULL + 1)", sql);
    }

    // ========== Operation object tests ==========

    @Test
    void testGetOp() {
        GaussDBExpression left = GaussDBConstant.createIntConstant(1);
        GaussDBExpression right = GaussDBConstant.createIntConstant(2);
        GaussDBBinaryArithmeticOperation op = new GaussDBBinaryArithmeticOperation(left, right,
                GaussDBArithmeticOperator.ADD);
        assertEquals(GaussDBArithmeticOperator.ADD, op.getOp());
    }

    @Test
    void testGetLeft() {
        GaussDBExpression left = GaussDBConstant.createIntConstant(1);
        GaussDBExpression right = GaussDBConstant.createIntConstant(2);
        GaussDBBinaryArithmeticOperation op = new GaussDBBinaryArithmeticOperation(left, right,
                GaussDBArithmeticOperator.ADD);
        assertEquals(left, op.getLeft());
    }

    @Test
    void testGetRight() {
        GaussDBExpression left = GaussDBConstant.createIntConstant(1);
        GaussDBExpression right = GaussDBConstant.createIntConstant(2);
        GaussDBBinaryArithmeticOperation op = new GaussDBBinaryArithmeticOperation(left, right,
                GaussDBArithmeticOperator.ADD);
        assertEquals(right, op.getRight());
    }

    @Test
    void testGetExpectedValue() {
        GaussDBExpression left = GaussDBConstant.createIntConstant(1);
        GaussDBExpression right = GaussDBConstant.createIntConstant(2);
        GaussDBBinaryArithmeticOperation op = new GaussDBBinaryArithmeticOperation(left, right,
                GaussDBArithmeticOperator.ADD);
        GaussDBConstant result = op.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(3, result.asIntNotNull());
    }

    @Test
    void testGetExpectedValueWithNull() {
        GaussDBExpression left = GaussDBConstant.createNullConstant();
        GaussDBExpression right = GaussDBConstant.createIntConstant(1);
        GaussDBBinaryArithmeticOperation op = new GaussDBBinaryArithmeticOperation(left, right,
                GaussDBArithmeticOperator.ADD);
        GaussDBConstant result = op.getExpectedValue();
        assertTrue(result.isNull());
    }

    @Test
    void testGetExpectedValueDivisionByZero() {
        GaussDBExpression left = GaussDBConstant.createIntConstant(1);
        GaussDBExpression right = GaussDBConstant.createIntConstant(0);
        GaussDBBinaryArithmeticOperation op = new GaussDBBinaryArithmeticOperation(left, right,
                GaussDBArithmeticOperator.DIV);
        GaussDBConstant result = op.getExpectedValue();
        assertTrue(result.isNull());
    }
}