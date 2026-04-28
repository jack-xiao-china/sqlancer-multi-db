package sqlancer.gaussdbm.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class GaussDBExistsTest {

    @Test
    void testExistsTrue() {
        // Create an EXISTS expression with TRUE expected value
        GaussDBSelect select = createSelectWithConstant(1);
        GaussDBConstant expectedResult = GaussDBConstant.createBooleanConstant(true);

        GaussDBExists exists = new GaussDBExists(select, expectedResult);

        assertTrue(exists.getExpectedValue().isBoolean());
        assertTrue(exists.getExpectedValue().asBooleanNotNull());
    }

    @Test
    void testExistsFalse() {
        // Create an EXISTS expression with FALSE expected value
        GaussDBSelect select = createSelectWithConstant(1);
        GaussDBConstant expectedResult = GaussDBConstant.createBooleanConstant(false);

        GaussDBExists exists = new GaussDBExists(select, expectedResult);

        assertTrue(exists.getExpectedValue().isBoolean());
        assertFalse(exists.getExpectedValue().asBooleanNotNull());
    }

    @Test
    void testExistsNullExpectedValue() {
        // Test EXISTS with NULL expected value
        GaussDBSelect select = createSelectWithConstant(1);
        GaussDBConstant expectedResult = GaussDBConstant.createNullConstant();

        GaussDBExists exists = new GaussDBExists(select, expectedResult);

        assertTrue(exists.getExpectedValue().isNull());
    }

    @Test
    void testConstructorWithNullExpectedValueThrowsAssertionError() {
        // Test that constructor throws AssertionError when expression's expectedValue is null
        // This is the expected behavior since GaussDBSelect.getExpectedValue() returns null
        GaussDBSelect select = createSelectWithConstant(1);

        assertThrows(AssertionError.class, () -> {
            new GaussDBExists(select);
        });
    }

    @Test
    void testConstructorWithNullExprExpectedValueThrowsAssertionError() {
        // Test that constructor throws AssertionError when a custom expression returns null
        GaussDBExpression nullExpr = new GaussDBExpression() {
            @Override
            public GaussDBConstant getExpectedValue() {
                return null; // Intentionally return null
            }
        };

        assertThrows(AssertionError.class, () -> {
            new GaussDBExists(nullExpr);
        });
    }

    @Test
    void testGetExpr() {
        // Test that getExpr returns the original subquery
        GaussDBSelect select = createSelectWithConstant(42);
        GaussDBConstant expectedResult = GaussDBConstant.createBooleanConstant(true);

        GaussDBExists exists = new GaussDBExists(select, expectedResult);

        assertEquals(select, exists.getExpr());
    }

    @Test
    void testExistsWithConstantExpr() {
        // Test EXISTS with a constant expression (not a SELECT)
        GaussDBConstant constant = GaussDBConstant.createIntConstant(1);
        GaussDBConstant expectedResult = GaussDBConstant.createBooleanConstant(true);

        GaussDBExists exists = new GaussDBExists(constant, expectedResult);

        assertEquals(constant, exists.getExpr());
        assertTrue(exists.getExpectedValue().isBoolean());
    }

    @Test
    void testExistsIntExpectedValue() {
        // Test EXISTS with integer expected value (MySQL-style: 1 = TRUE, 0 = FALSE)
        GaussDBSelect select = createSelectWithConstant(1);
        GaussDBConstant expectedResult = GaussDBConstant.createIntConstant(1);

        GaussDBExists exists = new GaussDBExists(select, expectedResult);

        assertTrue(exists.getExpectedValue().isInt());
        assertEquals(1, exists.getExpectedValue().asIntNotNull());
    }

    /**
     * Helper method to create a simple SELECT with a constant.
     */
    private GaussDBSelect createSelectWithConstant(int value) {
        GaussDBSelect select = new GaussDBSelect();
        select.setFetchColumns(List.of(GaussDBConstant.createIntConstant(value)));
        return select;
    }
}