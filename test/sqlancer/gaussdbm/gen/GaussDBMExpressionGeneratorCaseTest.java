package sqlancer.gaussdbm.gen;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMOptions;
import sqlancer.gaussdbm.GaussDBMOracleFactory;
import sqlancer.gaussdbm.ast.GaussDBCaseWhen;
import sqlancer.gaussdbm.ast.GaussDBExpression;

class GaussDBMExpressionGeneratorCaseTest {

    private static GaussDBMExpressionGenerator newGenerator(long seed) {
        GaussDBMOptions gaussdbmOpts = new GaussDBMOptions();
        gaussdbmOpts.oracles = Arrays.asList(GaussDBMOracleFactory.QUERY_PARTITIONING);

        GaussDBMGlobalState state = new GaussDBMGlobalState();
        state.setMainOptions(MainOptions.DEFAULT_OPTIONS);
        state.setDbmsSpecificOptions(gaussdbmOpts);
        state.setRandomly(new Randomly(seed));

        GaussDBMExpressionGenerator gen = new GaussDBMExpressionGenerator(state);
        // Initialize columns as empty list to avoid NullPointerException
        gen.setColumns(java.util.List.of());
        return gen;
    }

    @Test
    void testGenerateCaseWhen() {
        GaussDBMExpressionGenerator gen = newGenerator(12345L);

        boolean sawCaseWhen = false;
        for (int i = 0; i < 1000; i++) {
            try {
                // Generate expressions at depth 2 to allow for CASE_OPERATOR
                GaussDBExpression expr = gen.generateExpression(2);
                if (expr instanceof GaussDBCaseWhen) {
                    sawCaseWhen = true;
                    GaussDBCaseWhen caseWhen = (GaussDBCaseWhen) expr;
                    assertNotNull(caseWhen.getWhenExpr(), "WHEN expression should not be null");
                    assertNotNull(caseWhen.getThenExpr(), "THEN expression should not be null");
                    assertNotNull(caseWhen.getElseExpr(), "ELSE expression should not be null");
                    break;
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }
        assertTrue(sawCaseWhen, "expected at least one GaussDBCaseWhen expression generated");
    }

    @Test
    void testCaseWhenExpectedValueNotNullWhenTrue() {
        GaussDBMExpressionGenerator gen = newGenerator(1L);

        // Generate CASE expressions and verify expected value is computed
        for (int i = 0; i < 100; i++) {
            try {
                GaussDBExpression expr = gen.generateExpression(3);
                if (expr instanceof GaussDBCaseWhen) {
                    GaussDBCaseWhen caseWhen = (GaussDBCaseWhen) expr;
                    GaussDBExpression result = caseWhen.getExpectedValue();
                    assertNotNull(result, "Expected value should not be null for CASE expression");
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }
    }

    @Test
    void testCaseWhenCanBeNested() {
        GaussDBMExpressionGenerator gen = newGenerator(42L);

        boolean sawNestedCase = false;
        // With max depth 3, we need outer CASE at depth 1 and inner CASE at depth 2
        // Use a higher iteration count since CASE_OPERATOR has ~12.5% probability
        for (int i = 0; i < 5000; i++) {
            // Generate at depth 1 to allow nested CASE at depth 2 in THEN/ELSE
            // Note: generateLeafNode will only generate constants since columns is empty
            try {
                GaussDBExpression expr = gen.generateExpression(1);
                if (expr instanceof GaussDBCaseWhen) {
                    GaussDBCaseWhen outerCase = (GaussDBCaseWhen) expr;
                    if (outerCase.getThenExpr() instanceof GaussDBCaseWhen
                            || outerCase.getElseExpr() instanceof GaussDBCaseWhen) {
                        sawNestedCase = true;
                        break;
                    }
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }
        // This test is probabilistic - may not always generate nested CASE
        // but with enough iterations and proper depth, it should find one
        assertTrue(sawNestedCase, "expected at least one nested CASE expression (CASE within THEN or ELSE)");
    }

    @Test
    void testCaseWhenWithConstantValues() {
        GaussDBMExpressionGenerator gen = newGenerator(999L);

        // Verify that CASE WHEN with constants can compute expected values
        for (int i = 0; i < 100; i++) {
            try {
                GaussDBExpression expr = gen.generateExpression(3);
                if (expr instanceof GaussDBCaseWhen) {
                    GaussDBCaseWhen caseWhen = (GaussDBCaseWhen) expr;
                    // Expected value should be computable even with leaf node constants
                    GaussDBExpression whenExpr = caseWhen.getWhenExpr();
                    GaussDBExpression result = caseWhen.getExpectedValue();
                    // Verify the result is a valid constant (not null reference)
                    if (whenExpr.getExpectedValue() != null) {
                        assertNotNull(result, "Expected value should be computed when WHEN has expected value");
                    }
                }
            } catch (IllegalArgumentException e) {
                // Skip if COLUMN action is picked with empty columns list
                continue;
            }
        }
    }
}