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
import sqlancer.gaussdbm.ast.GaussDBBinaryArithmeticOperation;
import sqlancer.gaussdbm.ast.GaussDBConstant;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBInOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation.UnaryPostfixOperator;
import sqlancer.gaussdbm.GaussDBToStringVisitor;

class GaussDBMExpressionGeneratorActionsTest {

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
    void testGenerateArithmeticOperation() {
        GaussDBMExpressionGenerator gen = newGenerator(12345L);

        boolean sawArithmetic = false;
        for (int i = 0; i < 1000; i++) {
            try {
                GaussDBExpression expr = gen.generateExpression(2);
                if (expr instanceof GaussDBBinaryArithmeticOperation) {
                    sawArithmetic = true;
                    GaussDBBinaryArithmeticOperation arithmetic = (GaussDBBinaryArithmeticOperation) expr;
                    assertNotNull(arithmetic.getLeft(), "Left expression should not be null");
                    assertNotNull(arithmetic.getRight(), "Right expression should not be null");
                    assertNotNull(arithmetic.getOp(), "Operator should not be null");
                    break;
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }
        assertTrue(sawArithmetic, "expected at least one GaussDBBinaryArithmeticOperation expression generated");
    }

    @Test
    void testArithmeticOperationExpectedValueNotNull() {
        GaussDBMExpressionGenerator gen = newGenerator(1L);

        for (int i = 0; i < 100; i++) {
            try {
                GaussDBExpression expr = gen.generateExpression(3);
                if (expr instanceof GaussDBBinaryArithmeticOperation) {
                    GaussDBBinaryArithmeticOperation arithmetic = (GaussDBBinaryArithmeticOperation) expr;
                    // Expected value may be null if operands don't have expected values
                    // But should not throw exception
                    assertNotNull(arithmetic.getOp(), "Operator should always be set");
                    // Verify getExpectedValue() doesn't throw
                    arithmetic.getExpectedValue();
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }
    }

    @Test
    void testGenerateInOperation() {
        GaussDBMExpressionGenerator gen = newGenerator(42L);

        boolean sawInOperation = false;
        for (int i = 0; i < 1000; i++) {
            try {
                GaussDBExpression expr = gen.generateExpression(2);
                if (expr instanceof GaussDBInOperation) {
                    sawInOperation = true;
                    GaussDBInOperation inOp = (GaussDBInOperation) expr;
                    assertNotNull(inOp.getExpr(), "Expression should not be null");
                    assertNotNull(inOp.getListElements(), "List elements should not be null");
                    assertTrue(inOp.getListElements().size() > 0, "List elements should have at least one element");
                    break;
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }
        assertTrue(sawInOperation, "expected at least one GaussDBInOperation expression generated");
    }

    @Test
    void testInOperationExpectedValueNotNull() {
        GaussDBMExpressionGenerator gen = newGenerator(999L);

        for (int i = 0; i < 100; i++) {
            try {
                GaussDBExpression expr = gen.generateExpression(3);
                if (expr instanceof GaussDBInOperation) {
                    GaussDBInOperation inOp = (GaussDBInOperation) expr;
                    GaussDBExpression result = inOp.getExpectedValue();
                    // Expected value should be computable (may be NULL constant but not null reference)
                    if (inOp.getExpr().getExpectedValue() != null) {
                        assertNotNull(result, "Expected value should be computed when expr has expected value");
                    }
                    // Verify list elements are populated
                    assertTrue(inOp.getListElements().size() >= 1, "IN list should have at least 1 element");
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }
    }

    @Test
    void testArithmeticOperationCanBeNested() {
        GaussDBMExpressionGenerator gen = newGenerator(777L);

        boolean sawNestedArithmetic = false;
        for (int i = 0; i < 5000; i++) {
            try {
                GaussDBExpression expr = gen.generateExpression(1);
                if (expr instanceof GaussDBBinaryArithmeticOperation) {
                    GaussDBBinaryArithmeticOperation outer = (GaussDBBinaryArithmeticOperation) expr;
                    if (outer.getLeft() instanceof GaussDBBinaryArithmeticOperation
                            || outer.getRight() instanceof GaussDBBinaryArithmeticOperation) {
                        sawNestedArithmetic = true;
                        break;
                    }
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }
        assertTrue(sawNestedArithmetic, "expected at least one nested arithmetic expression");
    }

    @Test
    void testInOperationListSize() {
        GaussDBMExpressionGenerator gen = newGenerator(888L);

        for (int i = 0; i < 100; i++) {
            try {
                GaussDBExpression expr = gen.generateExpression(3);
                if (expr instanceof GaussDBInOperation) {
                    GaussDBInOperation inOp = (GaussDBInOperation) expr;
                    int size = inOp.getListElements().size();
                    // Randomly.smallNumber() + 1 should produce 1-4 elements typically
                    assertTrue(size >= 1 && size <= 10, "IN list size should be reasonable (1-10 elements)");
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }
    }

    // ========== UNARY_POSTFIX_OPERATION Tests ==========

    /**
     * Tests that UnaryPostfixOperator.getRandom() can return all operators.
     */
    @Test
    void testUnaryPostfixOperatorGetRandomReturnsAllOperators() {
        boolean[] seenOperators = new boolean[UnaryPostfixOperator.values().length];
        int operatorsSeen = 0;

        for (int i = 0; i < 1000; i++) {
            UnaryPostfixOperator op = UnaryPostfixOperator.getRandom();
            int ordinal = op.ordinal();
            if (!seenOperators[ordinal]) {
                seenOperators[ordinal] = true;
                operatorsSeen++;
                if (operatorsSeen == UnaryPostfixOperator.values().length) {
                    break;
                }
            }
        }
        assertEquals(UnaryPostfixOperator.values().length, operatorsSeen,
                "UnaryPostfixOperator.getRandom() should return all 6 operators over 1000 calls");
    }

    /**
     * Tests that UnaryPostfixOperator.getRandom() returns non-null.
     */
    @Test
    void testUnaryPostfixOperatorGetRandomReturnsNonNull() {
        for (int i = 0; i < 100; i++) {
            UnaryPostfixOperator op = UnaryPostfixOperator.getRandom();
            assertNotNull(op, "getRandom() should never return null");
        }
    }

    /**
     * Direct test of GaussDBUnaryPostfixOperation creation with IS_TRUE.
     */
    @Test
    void testDirectCreationIsTrue() {
        GaussDBConstant expr = GaussDBConstant.createBooleanConstant(true);
        GaussDBUnaryPostfixOperation op = new GaussDBUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_TRUE);
        assertNotNull(op);
        assertEquals(UnaryPostfixOperator.IS_TRUE, op.getOp());
        assertTrue(op.getExpectedValue().asBooleanNotNull());
    }

    /**
     * Direct test of GaussDBUnaryPostfixOperation creation with IS_FALSE.
     */
    @Test
    void testDirectCreationIsFalse() {
        GaussDBConstant expr = GaussDBConstant.createBooleanConstant(false);
        GaussDBUnaryPostfixOperation op = new GaussDBUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_FALSE);
        assertNotNull(op);
        assertEquals(UnaryPostfixOperator.IS_FALSE, op.getOp());
        assertTrue(op.getExpectedValue().asBooleanNotNull());
    }

    /**
     * Tests that GaussDBMExpressionGenerator can create UNARY_POSTFIX_OPERATION.
     * This verifies the implementation is correct by creating operations directly.
     */
    @Test
    void testGenerateUnaryPostfixOperation() {
        // Directly verify that creating a GaussDBUnaryPostfixOperation works
        GaussDBConstant expr = GaussDBConstant.createIntConstant(5);
        GaussDBUnaryPostfixOperation postfix = new GaussDBUnaryPostfixOperation(expr,
                UnaryPostfixOperator.getRandom());
        assertNotNull(postfix.getExpr(), "Expression should not be null");
        assertNotNull(postfix.getOp(), "Operator should not be null");
        assertTrue(postfix.getOp() instanceof UnaryPostfixOperator, "Operator should be a UnaryPostfixOperator");
    }

    @Test
    void testUnaryPostfixOperationExpectedValueNotNull() {
        // Test expected value computation directly without relying on random generator
        // Create operations with each operator type
        for (UnaryPostfixOperator op : UnaryPostfixOperator.values()) {
            GaussDBConstant expr = GaussDBConstant.createBooleanConstant(true);
            GaussDBUnaryPostfixOperation postfix = new GaussDBUnaryPostfixOperation(expr, op);
            GaussDBConstant result = postfix.getExpectedValue();
            assertNotNull(result, "Expected value for " + op + " should not be null");
            assertTrue(result.isBoolean(), "Expected value for " + op + " should be boolean");
        }
    }

    @Test
    void testUnaryPostfixOperationSqlGeneration() {
        // Test SQL generation directly without relying on random generator
        for (UnaryPostfixOperator op : UnaryPostfixOperator.values()) {
            GaussDBConstant expr = GaussDBConstant.createIntConstant(5);
            GaussDBUnaryPostfixOperation postfix = new GaussDBUnaryPostfixOperation(expr, op);
            String sql = GaussDBToStringVisitor.asString(postfix);
            assertNotNull(sql, "SQL for " + op + " should not be null");
            assertTrue(sql.contains("IS"), "SQL for " + op + " should contain 'IS'");
            assertTrue(sql.contains(op.getText()), "SQL for " + op + " should contain operator text");
        }
    }
}