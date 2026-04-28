package sqlancer.mysql.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import sqlancer.mysql.ast.MySQLWindowFunction.MySQLFunction;

public class MySQLWindowFunctionTest {

    @Test
    void testConstructorAndGetters() {
        MySQLExpression expr = MySQLConstant.createIntConstant(10);
        MySQLExpression partitionBy = MySQLConstant.createIntConstant(5);

        MySQLWindowFunction windowFunc = new MySQLWindowFunction(MySQLFunction.ROW_NUMBER, expr, partitionBy);

        assertEquals(MySQLFunction.ROW_NUMBER, windowFunc.getFunction());
        assertEquals(expr, windowFunc.getExpr());
        assertEquals(partitionBy, windowFunc.getPartitionBy());
    }

    @Test
    void testGetExpectedValueReturnsNull() {
        MySQLWindowFunction windowFunc = new MySQLWindowFunction(MySQLFunction.SUM, MySQLConstant.createIntConstant(10),
                null);

        assertNull(windowFunc.getExpectedValue());
    }

    @Test
    void testImplementsMySQLExpression() {
        MySQLWindowFunction windowFunc = new MySQLWindowFunction(MySQLFunction.RANK, null, null);

        assertNotNull(windowFunc);
        assert windowFunc instanceof MySQLExpression;
    }

    @Test
    void testMySQLFunctionEnumValues() {
        // Test that all window function types exist
        assertNotNull(MySQLFunction.ROW_NUMBER);
        assertNotNull(MySQLFunction.RANK);
        assertNotNull(MySQLFunction.DENSE_RANK);
        assertNotNull(MySQLFunction.CUME_DIST);
        assertNotNull(MySQLFunction.PERCENT_RANK);
        assertNotNull(MySQLFunction.LAG);
        assertNotNull(MySQLFunction.LEAD);
        assertNotNull(MySQLFunction.AVG);
        assertNotNull(MySQLFunction.SUM);
        assertNotNull(MySQLFunction.COUNT);
        assertNotNull(MySQLFunction.MAX);
        assertNotNull(MySQLFunction.MIN);
    }

    @Test
    void testMySQLFunctionGetName() {
        assertEquals("ROW_NUMBER", MySQLFunction.ROW_NUMBER.getName());
        assertEquals("RANK", MySQLFunction.RANK.getName());
        assertEquals("SUM", MySQLFunction.SUM.getName());
        assertEquals("AVG", MySQLFunction.AVG.getName());
    }

    @Test
    void testMySQLFunctionToString() {
        assertEquals("ROW_NUMBER", MySQLFunction.ROW_NUMBER.toString());
        assertEquals("RANK", MySQLFunction.RANK.toString());
    }

    @Test
    void testMySQLFunctionGetArgs() {
        // Functions with 0 args
        assertEquals(0, MySQLFunction.ROW_NUMBER.getArgs());
        assertEquals(0, MySQLFunction.RANK.getArgs());
        assertEquals(0, MySQLFunction.DENSE_RANK.getArgs());
        assertEquals(0, MySQLFunction.CUME_DIST.getArgs());
        assertEquals(0, MySQLFunction.PERCENT_RANK.getArgs());

        // Functions with 1 arg (default)
        assertEquals(1, MySQLFunction.AVG.getArgs());
        assertEquals(1, MySQLFunction.SUM.getArgs());
        assertEquals(1, MySQLFunction.LAG.getArgs());
    }

    @Test
    void testMySQLFunctionGetRandomFunction() {
        MySQLFunction randomFunc = MySQLFunction.getRandomFunction();
        assertNotNull(randomFunc);
    }

    @Test
    void testWithNullExpr() {
        MySQLWindowFunction windowFunc = new MySQLWindowFunction(MySQLFunction.ROW_NUMBER, null, null);

        assertNull(windowFunc.getExpr());
        assertNull(windowFunc.getPartitionBy());
    }

    @Test
    void testAggregateAsWindowFunction() {
        // Window functions can also be aggregate functions
        MySQLWindowFunction sumWindow = new MySQLWindowFunction(MySQLFunction.SUM, MySQLConstant.createIntConstant(10),
                null);

        assertEquals(MySQLFunction.SUM, sumWindow.getFunction());
    }
}