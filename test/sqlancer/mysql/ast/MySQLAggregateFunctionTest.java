package sqlancer.mysql.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import sqlancer.mysql.ast.MySQLAggregateFunction.MySQLFunction;

public class MySQLAggregateFunctionTest {

    @Test
    void testConstructorAndGetters() {
        MySQLExpression expr = MySQLConstant.createIntConstant(10);

        MySQLAggregateFunction aggFunc = new MySQLAggregateFunction(MySQLFunction.SUM, expr);

        assertEquals(MySQLFunction.SUM, aggFunc.getFunction());
        assertEquals(expr, aggFunc.getExpr());
    }

    @Test
    void testGetExpectedValueReturnsNull() {
        MySQLAggregateFunction aggFunc = new MySQLAggregateFunction(MySQLFunction.AVG,
                MySQLConstant.createIntConstant(10));

        assertNull(aggFunc.getExpectedValue());
    }

    @Test
    void testImplementsMySQLExpression() {
        MySQLAggregateFunction aggFunc = new MySQLAggregateFunction(MySQLFunction.COUNT,
                MySQLConstant.createIntConstant(1));

        assertNotNull(aggFunc);
        assert aggFunc instanceof MySQLExpression;
    }

    @Test
    void testMySQLFunctionEnumValues() {
        // Test that all aggregate function types exist
        assertNotNull(MySQLFunction.AVG);
        assertNotNull(MySQLFunction.BIT_AND);
        assertNotNull(MySQLFunction.BIT_OR);
        assertNotNull(MySQLFunction.BIT_XOR);
        assertNotNull(MySQLFunction.COUNT);
        assertNotNull(MySQLFunction.MAX);
        assertNotNull(MySQLFunction.MIN);
        assertNotNull(MySQLFunction.SUM);
        assertNotNull(MySQLFunction.STD);
        assertNotNull(MySQLFunction.STDDEV_POP);
        assertNotNull(MySQLFunction.STDDEV_SAMP);
        assertNotNull(MySQLFunction.STDDEV);
        assertNotNull(MySQLFunction.VAR_POP);
        assertNotNull(MySQLFunction.VAR_SAMP);
        assertNotNull(MySQLFunction.VARIATION);
    }

    @Test
    void testMySQLFunctionGetName() {
        assertEquals("AVG", MySQLFunction.AVG.getName());
        assertEquals("COUNT", MySQLFunction.COUNT.getName());
        assertEquals("SUM", MySQLFunction.SUM.getName());
        assertEquals("MAX", MySQLFunction.MAX.getName());
        assertEquals("MIN", MySQLFunction.MIN.getName());
    }

    @Test
    void testMySQLFunctionToString() {
        assertEquals("AVG", MySQLFunction.AVG.toString());
        assertEquals("COUNT", MySQLFunction.COUNT.toString());
        assertEquals("SUM", MySQLFunction.SUM.toString());
    }

    @Test
    void testMySQLFunctionGetRandomFunction() {
        MySQLFunction randomFunc = MySQLFunction.getRandomFunction();
        assertNotNull(randomFunc);
    }

    @Test
    void testWithNullExpr() {
        MySQLAggregateFunction aggFunc = new MySQLAggregateFunction(MySQLFunction.COUNT, null);

        assertNull(aggFunc.getExpr());
    }

    @Test
    void testWithDifferentExpressionTypes() {
        // Test with integer constant
        MySQLAggregateFunction aggWithInt = new MySQLAggregateFunction(MySQLFunction.SUM,
                MySQLConstant.createIntConstant(42));
        assertNotNull(aggWithInt.getExpr());

        // Test with string constant
        MySQLAggregateFunction aggWithStr = new MySQLAggregateFunction(MySQLFunction.MAX,
                MySQLConstant.createStringConstant("test"));
        assertNotNull(aggWithStr.getExpr());

        // Test with null constant
        MySQLAggregateFunction aggWithNull = new MySQLAggregateFunction(MySQLFunction.COUNT,
                MySQLConstant.createNullConstant());
        assertNotNull(aggWithNull.getExpr());
    }

    @Test
    void testBitAggregateFunctions() {
        MySQLAggregateFunction bitAnd = new MySQLAggregateFunction(MySQLFunction.BIT_AND,
                MySQLConstant.createIntConstant(5));
        assertEquals(MySQLFunction.BIT_AND, bitAnd.getFunction());

        MySQLAggregateFunction bitOr = new MySQLAggregateFunction(MySQLFunction.BIT_OR,
                MySQLConstant.createIntConstant(5));
        assertEquals(MySQLFunction.BIT_OR, bitOr.getFunction());

        MySQLAggregateFunction bitXor = new MySQLAggregateFunction(MySQLFunction.BIT_XOR,
                MySQLConstant.createIntConstant(5));
        assertEquals(MySQLFunction.BIT_XOR, bitXor.getFunction());
    }

    @Test
    void testStddevFunctions() {
        assertEquals("STD", MySQLFunction.STD.getName());
        assertEquals("STDDEV_POP", MySQLFunction.STDDEV_POP.getName());
        assertEquals("STDDEV_SAMP", MySQLFunction.STDDEV_SAMP.getName());
        assertEquals("STD", MySQLFunction.STDDEV.getName());
    }

    @Test
    void testVarianceFunctions() {
        assertEquals("VAR_POP", MySQLFunction.VAR_POP.getName());
        assertEquals("VAR_SAMP", MySQLFunction.VAR_SAMP.getName());
        assertEquals("VARIANCE", MySQLFunction.VARIATION.getName());
    }
}