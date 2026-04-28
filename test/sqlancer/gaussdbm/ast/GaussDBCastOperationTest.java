package sqlancer.gaussdbm.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBCastOperation.GaussDBCastType;

class GaussDBCastOperationTest {

    @Test
    void testCastTypeNames() {
        assertEquals("SIGNED", GaussDBCastType.SIGNED.getTextRepresentation());
        assertEquals("UNSIGNED", GaussDBCastType.UNSIGNED.getTextRepresentation());
        assertEquals("BINARY", GaussDBCastType.BINARY.getTextRepresentation());
        assertEquals("CHAR", GaussDBCastType.CHAR.getTextRepresentation());
        assertEquals("DATE", GaussDBCastType.DATE.getTextRepresentation());
        assertEquals("DATETIME", GaussDBCastType.DATETIME.getTextRepresentation());
        assertEquals("TIME", GaussDBCastType.TIME.getTextRepresentation());
        assertEquals("DECIMAL", GaussDBCastType.DECIMAL.getTextRepresentation());
    }

    @Test
    void testCastSqlGeneration() {
        // Create a constant expression
        GaussDBConstant expr = GaussDBConstant.createIntConstant(5);
        GaussDBCastOperation cast = new GaussDBCastOperation(expr, GaussDBCastType.SIGNED);

        String sql = GaussDBToStringVisitor.asString(cast);
        assertEquals("CAST(5 AS SIGNED)", sql);
    }

    @Test
    void testCastUnsignedSqlGeneration() {
        GaussDBConstant expr = GaussDBConstant.createStringConstant("123");
        GaussDBCastOperation cast = new GaussDBCastOperation(expr, GaussDBCastType.UNSIGNED);

        String sql = GaussDBToStringVisitor.asString(cast);
        assertEquals("CAST('123' AS UNSIGNED)", sql);
    }

    @Test
    void testCastCharSqlGeneration() {
        GaussDBConstant expr = GaussDBConstant.createIntConstant(42);
        GaussDBCastOperation cast = new GaussDBCastOperation(expr, GaussDBCastType.CHAR);

        String sql = GaussDBToStringVisitor.asString(cast);
        assertEquals("CAST(42 AS CHAR)", sql);
    }

    @Test
    void testCastSignedIntToInt() {
        GaussDBConstant intVal = GaussDBConstant.createIntConstant(5);
        GaussDBCastOperation cast = new GaussDBCastOperation(intVal, GaussDBCastType.SIGNED);

        GaussDBConstant result = cast.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(5, result.asIntNotNull());
    }

    @Test
    void testCastSignedNegativeIntToInt() {
        GaussDBConstant intVal = GaussDBConstant.createIntConstant(-5);
        GaussDBCastOperation cast = new GaussDBCastOperation(intVal, GaussDBCastType.SIGNED);

        GaussDBConstant result = cast.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(-5, result.asIntNotNull());
    }

    @Test
    void testCastSignedStringToInt() {
        GaussDBConstant strVal = GaussDBConstant.createStringConstant("123");
        GaussDBCastOperation cast = new GaussDBCastOperation(strVal, GaussDBCastType.SIGNED);

        GaussDBConstant result = cast.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(123, result.asIntNotNull());
    }

    @Test
    void testCastSignedStringWithLeadingSpacesToInt() {
        GaussDBConstant strVal = GaussDBConstant.createStringConstant("  456");
        GaussDBCastOperation cast = new GaussDBCastOperation(strVal, GaussDBCastType.SIGNED);

        GaussDBConstant result = cast.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(456, result.asIntNotNull());
    }

    @Test
    void testCastSignedNonNumericStringToInt() {
        GaussDBConstant strVal = GaussDBConstant.createStringConstant("abc");
        GaussDBCastOperation cast = new GaussDBCastOperation(strVal, GaussDBCastType.SIGNED);

        GaussDBConstant result = cast.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(0, result.asIntNotNull());
    }

    @Test
    void testCastSignedBooleanToInt() {
        GaussDBConstant boolVal = GaussDBConstant.createBooleanConstant(true);
        GaussDBCastOperation cast = new GaussDBCastOperation(boolVal, GaussDBCastType.SIGNED);

        GaussDBConstant result = cast.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(1, result.asIntNotNull());

        boolVal = GaussDBConstant.createBooleanConstant(false);
        cast = new GaussDBCastOperation(boolVal, GaussDBCastType.SIGNED);

        result = cast.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(0, result.asIntNotNull());
    }

    @Test
    void testCastUnsignedIntToInt() {
        GaussDBConstant intVal = GaussDBConstant.createIntConstant(5);
        GaussDBCastOperation cast = new GaussDBCastOperation(intVal, GaussDBCastType.UNSIGNED);

        GaussDBConstant result = cast.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(5, result.asIntNotNull());
    }

    @Test
    void testCastUnsignedStringToInt() {
        GaussDBConstant strVal = GaussDBConstant.createStringConstant("123");
        GaussDBCastOperation cast = new GaussDBCastOperation(strVal, GaussDBCastType.UNSIGNED);

        GaussDBConstant result = cast.getExpectedValue();
        assertTrue(result.isInt());
        assertEquals(123, result.asIntNotNull());
    }

    @Test
    void testCastCharIntToString() {
        GaussDBConstant intVal = GaussDBConstant.createIntConstant(42);
        GaussDBCastOperation cast = new GaussDBCastOperation(intVal, GaussDBCastType.CHAR);

        GaussDBConstant result = cast.getExpectedValue();
        assertTrue(result.isString());
        assertEquals("42", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testCastCharBooleanToString() {
        GaussDBConstant boolVal = GaussDBConstant.createBooleanConstant(true);
        GaussDBCastOperation cast = new GaussDBCastOperation(boolVal, GaussDBCastType.CHAR);

        GaussDBConstant result = cast.getExpectedValue();
        assertTrue(result.isString());
        assertEquals("1", ((GaussDBConstant.GaussDBStringConstant) result).getValue());

        boolVal = GaussDBConstant.createBooleanConstant(false);
        cast = new GaussDBCastOperation(boolVal, GaussDBCastType.CHAR);

        result = cast.getExpectedValue();
        assertTrue(result.isString());
        assertEquals("0", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testCastCharStringToString() {
        GaussDBConstant strVal = GaussDBConstant.createStringConstant("hello");
        GaussDBCastOperation cast = new GaussDBCastOperation(strVal, GaussDBCastType.CHAR);

        GaussDBConstant result = cast.getExpectedValue();
        assertTrue(result.isString());
        assertEquals("hello", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testCastNullReturnsNull() {
        GaussDBExpression nullExpr = new GaussDBExpression() {
            @Override
            public GaussDBConstant getExpectedValue() {
                return GaussDBConstant.createNullConstant();
            }
        };

        GaussDBCastOperation cast = new GaussDBCastOperation(nullExpr, GaussDBCastType.SIGNED);
        assertTrue(cast.getExpectedValue().isNull());

        cast = new GaussDBCastOperation(nullExpr, GaussDBCastType.UNSIGNED);
        assertTrue(cast.getExpectedValue().isNull());

        cast = new GaussDBCastOperation(nullExpr, GaussDBCastType.CHAR);
        assertTrue(cast.getExpectedValue().isNull());
    }

    @Test
    void testCastWithExpression() {
        // Create an expression that returns an integer
        GaussDBExpression expr = new GaussDBExpression() {
            @Override
            public GaussDBConstant getExpectedValue() {
                return GaussDBConstant.createIntConstant(10);
            }
        };

        GaussDBCastOperation cast = new GaussDBCastOperation(expr, GaussDBCastType.CHAR);

        GaussDBConstant result = cast.getExpectedValue();
        assertTrue(result.isString());
        assertEquals("10", ((GaussDBConstant.GaussDBStringConstant) result).getValue());
    }

    @Test
    void testToStringVisitorCast() {
        // Create nested expression for ToStringVisitor test
        GaussDBConstant inner = GaussDBConstant.createIntConstant(100);
        GaussDBCastOperation cast = new GaussDBCastOperation(inner, GaussDBCastType.SIGNED);

        String result = GaussDBToStringVisitor.asString(cast);
        assertEquals("CAST(100 AS SIGNED)", result);
    }
}