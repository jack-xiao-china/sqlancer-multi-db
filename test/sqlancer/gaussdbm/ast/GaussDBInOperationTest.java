package sqlancer.gaussdbm.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.GaussDBToStringVisitor;

class GaussDBInOperationTest {

    @Test
    void testInMatch() {
        // 1 IN (1, 2, 3) = TRUE
        GaussDBConstant expr = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e1 = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e2 = GaussDBConstant.createIntConstant(2);
        GaussDBConstant e3 = GaussDBConstant.createIntConstant(3);

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Arrays.asList(e1, e2, e3), true);

        GaussDBConstant result = inOp.getExpectedValue();
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testInNoMatch() {
        // 5 IN (1, 2, 3) = FALSE
        GaussDBConstant expr = GaussDBConstant.createIntConstant(5);
        GaussDBConstant e1 = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e2 = GaussDBConstant.createIntConstant(2);
        GaussDBConstant e3 = GaussDBConstant.createIntConstant(3);

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Arrays.asList(e1, e2, e3), true);

        GaussDBConstant result = inOp.getExpectedValue();
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    @Test
    void testNotInMatch() {
        // 1 NOT IN (1, 2, 3) = FALSE
        GaussDBConstant expr = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e1 = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e2 = GaussDBConstant.createIntConstant(2);
        GaussDBConstant e3 = GaussDBConstant.createIntConstant(3);

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Arrays.asList(e1, e2, e3), false);

        GaussDBConstant result = inOp.getExpectedValue();
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    @Test
    void testNotInNoMatch() {
        // 5 NOT IN (1, 2, 3) = TRUE
        GaussDBConstant expr = GaussDBConstant.createIntConstant(5);
        GaussDBConstant e1 = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e2 = GaussDBConstant.createIntConstant(2);
        GaussDBConstant e3 = GaussDBConstant.createIntConstant(3);

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Arrays.asList(e1, e2, e3), false);

        GaussDBConstant result = inOp.getExpectedValue();
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testInWithNull() {
        // 1 IN (NULL, 2, 3) = NULL (because NULL in list means result could be unknown)
        GaussDBConstant expr = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e1 = GaussDBConstant.createNullConstant();
        GaussDBConstant e2 = GaussDBConstant.createIntConstant(2);
        GaussDBConstant e3 = GaussDBConstant.createIntConstant(3);

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Arrays.asList(e1, e2, e3), true);

        GaussDBConstant result = inOp.getExpectedValue();
        assertTrue(result.isNull());
    }

    @Test
    void testInWithNullMatch() {
        // 2 IN (NULL, 2, 3) = TRUE (match found before NULL matters)
        GaussDBConstant expr = GaussDBConstant.createIntConstant(2);
        GaussDBConstant e1 = GaussDBConstant.createNullConstant();
        GaussDBConstant e2 = GaussDBConstant.createIntConstant(2);
        GaussDBConstant e3 = GaussDBConstant.createIntConstant(3);

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Arrays.asList(e1, e2, e3), true);

        GaussDBConstant result = inOp.getExpectedValue();
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testNullExpr() {
        // NULL IN (1, 2, 3) = NULL
        GaussDBConstant expr = GaussDBConstant.createNullConstant();
        GaussDBConstant e1 = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e2 = GaussDBConstant.createIntConstant(2);
        GaussDBConstant e3 = GaussDBConstant.createIntConstant(3);

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Arrays.asList(e1, e2, e3), true);

        GaussDBConstant result = inOp.getExpectedValue();
        assertTrue(result.isNull());
    }

    @Test
    void testNullExprNotIn() {
        // NULL NOT IN (1, 2, 3) = NULL
        GaussDBConstant expr = GaussDBConstant.createNullConstant();
        GaussDBConstant e1 = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e2 = GaussDBConstant.createIntConstant(2);
        GaussDBConstant e3 = GaussDBConstant.createIntConstant(3);

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Arrays.asList(e1, e2, e3), false);

        GaussDBConstant result = inOp.getExpectedValue();
        assertTrue(result.isNull());
    }

    @Test
    void testEmptyListIn() {
        // 1 IN () = FALSE (empty list)
        GaussDBConstant expr = GaussDBConstant.createIntConstant(1);

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Collections.emptyList(), true);

        GaussDBConstant result = inOp.getExpectedValue();
        assertTrue(result.isBoolean());
        assertFalse(result.asBooleanNotNull());
    }

    @Test
    void testEmptyListNotIn() {
        // 1 NOT IN () = TRUE (empty list)
        GaussDBConstant expr = GaussDBConstant.createIntConstant(1);

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Collections.emptyList(), false);

        GaussDBConstant result = inOp.getExpectedValue();
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testAsStringIn() {
        // Verify SQL generation: (1 IN (1, 2, 3))
        GaussDBConstant expr = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e1 = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e2 = GaussDBConstant.createIntConstant(2);
        GaussDBConstant e3 = GaussDBConstant.createIntConstant(3);

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Arrays.asList(e1, e2, e3), true);

        String sql = GaussDBToStringVisitor.asString(inOp);
        assertEquals("(1 IN (1, 2, 3))", sql);
    }

    @Test
    void testAsStringNotIn() {
        // Verify SQL generation: (1 NOT IN (1, 2, 3))
        GaussDBConstant expr = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e1 = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e2 = GaussDBConstant.createIntConstant(2);
        GaussDBConstant e3 = GaussDBConstant.createIntConstant(3);

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Arrays.asList(e1, e2, e3), false);

        String sql = GaussDBToStringVisitor.asString(inOp);
        assertEquals("(1 NOT IN (1, 2, 3))", sql);
    }

    @Test
    void testAsStringWithStringConstants() {
        // Verify SQL generation with strings: ('a' IN ('a', 'b'))
        GaussDBConstant expr = GaussDBConstant.createStringConstant("a");
        GaussDBConstant e1 = GaussDBConstant.createStringConstant("a");
        GaussDBConstant e2 = GaussDBConstant.createStringConstant("b");

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Arrays.asList(e1, e2), true);

        String sql = GaussDBToStringVisitor.asString(inOp);
        assertEquals("('a' IN ('a', 'b'))", sql);
    }

    @Test
    void testInWithStringMatch() {
        // 'a' IN ('a', 'b') = TRUE
        GaussDBConstant expr = GaussDBConstant.createStringConstant("a");
        GaussDBConstant e1 = GaussDBConstant.createStringConstant("a");
        GaussDBConstant e2 = GaussDBConstant.createStringConstant("b");

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Arrays.asList(e1, e2), true);

        GaussDBConstant result = inOp.getExpectedValue();
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testInWithBooleanMatch() {
        // TRUE IN (TRUE, FALSE) = TRUE
        GaussDBConstant expr = GaussDBConstant.createBooleanConstant(true);
        GaussDBConstant e1 = GaussDBConstant.createBooleanConstant(true);
        GaussDBConstant e2 = GaussDBConstant.createBooleanConstant(false);

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Arrays.asList(e1, e2), true);

        GaussDBConstant result = inOp.getExpectedValue();
        assertTrue(result.isBoolean());
        assertTrue(result.asBooleanNotNull());
    }

    @Test
    void testGetters() {
        GaussDBConstant expr = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e1 = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e2 = GaussDBConstant.createIntConstant(2);

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Arrays.asList(e1, e2), true);

        assertEquals(expr, inOp.getExpr());
        assertEquals(2, inOp.getListElements().size());
        assertTrue(inOp.isTrue());
    }

    @Test
    void testNotInGetter() {
        GaussDBConstant expr = GaussDBConstant.createIntConstant(1);
        GaussDBConstant e1 = GaussDBConstant.createIntConstant(1);

        GaussDBInOperation inOp = new GaussDBInOperation(expr, Arrays.asList(e1), false);

        assertFalse(inOp.isTrue());
    }
}