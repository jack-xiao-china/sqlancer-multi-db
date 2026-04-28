package sqlancer.mysql.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

public class MySQLManuelPredicateTest {

    @Test
    void testGetStringReturnsCorrectPredicate() {
        String sqlFragment = "1 = 1";
        MySQLManuelPredicate predicate = new MySQLManuelPredicate(sqlFragment);

        assertEquals(sqlFragment, predicate.getString());
    }

    @Test
    void testGetStringWithComplexSql() {
        String sqlFragment = "a > 0 AND b < 100";
        MySQLManuelPredicate predicate = new MySQLManuelPredicate(sqlFragment);

        assertEquals(sqlFragment, predicate.getString());
    }

    @Test
    void testImplementsMySQLExpression() {
        MySQLManuelPredicate predicate = new MySQLManuelPredicate("test");

        assertNotNull(predicate);
        assert predicate instanceof MySQLExpression;
    }

    @Test
    void testGetStringWithEmptyString() {
        MySQLManuelPredicate predicate = new MySQLManuelPredicate("");

        assertEquals("", predicate.getString());
    }

    @Test
    void testGetStringWithNullValue() {
        MySQLManuelPredicate predicate = new MySQLManuelPredicate(null);

        assertEquals(null, predicate.getString());
    }
}