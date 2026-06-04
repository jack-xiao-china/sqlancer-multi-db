package sqlancer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import sqlancer.common.transaction.TxConflictConstructor;

/**
 * Tests for {@link TxConflictConstructor} SQL string manipulation methods.
 */
public class TestTxConflictConstructor {

    // Use reflection to test package-private methods
    private String extractWhereCondition(String sql) throws Exception {
        Method m = TxConflictConstructor.class.getDeclaredMethod("extractWhereCondition", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, sql);
    }

    @Test
    public void testExtractWhere_simpleUpdate() throws Exception {
        String sql = "UPDATE t0 SET c0 = 1 WHERE c0 > 5";
        assertEquals("c0 > 5", extractWhereCondition(sql));
    }

    @Test
    public void testExtractWhere_simpleDelete() throws Exception {
        String sql = "DELETE FROM t0 WHERE c1 = 'abc'";
        assertEquals("c1 = 'abc'", extractWhereCondition(sql));
    }

    @Test
    public void testExtractWhere_selectWithForUpdate() throws Exception {
        String sql = "SELECT * FROM t0 WHERE c0 > 5 FOR UPDATE";
        assertEquals("c0 > 5", extractWhereCondition(sql));
    }

    @Test
    public void testExtractWhere_selectWithLockInShareMode() throws Exception {
        String sql = "SELECT * FROM t0 WHERE c0 > 5 LOCK IN SHARE MODE";
        assertEquals("c0 > 5", extractWhereCondition(sql));
    }

    @Test
    public void testExtractWhere_noWhereClause() throws Exception {
        String sql = "UPDATE t0 SET c0 = 1";
        assertNull(extractWhereCondition(sql));
    }

    @Test
    public void testExtractWhere_noWhereWithForUpdate() throws Exception {
        String sql = "SELECT * FROM t0 FOR UPDATE";
        assertNull(extractWhereCondition(sql));
    }

    @Test
    public void testExtractWhere_complexExpression() throws Exception {
        String sql = "DELETE FROM t0 WHERE (c0 > 5 AND c1 < 10) OR c2 IS NULL";
        assertEquals("(c0 > 5 AND c1 < 10) OR c2 IS NULL", extractWhereCondition(sql));
    }

    @Test
    public void testExtractWhere_withSubquery() throws Exception {
        String sql = "SELECT * FROM t0 WHERE c0 IN (SELECT c1 FROM t1 WHERE c1 > 3)";
        String result = extractWhereCondition(sql);
        assertNotNull(result);
        assertTrue(result.contains("IN"));
    }

    @Test
    public void testExtractWhere_withOrderByAndLimit() throws Exception {
        String sql = "SELECT * FROM t0 WHERE c0 > 5 ORDER BY c1 LIMIT 10";
        assertEquals("c0 > 5", extractWhereCondition(sql));
    }

    @Test
    public void testExtractWhere_deleteWithModifiers() throws Exception {
        String sql = "DELETE LOW_PRIORITY QUICK IGNORE FROM t0 WHERE c0 > 5";
        assertEquals("c0 > 5", extractWhereCondition(sql));
    }

    @Test
    public void testExtractWhere_selectWithForShare() throws Exception {
        String sql = "SELECT c0, c1 FROM t0 WHERE c0 BETWEEN 1 AND 10 FOR SHARE";
        assertEquals("c0 BETWEEN 1 AND 10", extractWhereCondition(sql));
    }

    @Test
    public void testExtractWhere_nestedParentheses() throws Exception {
        String sql = "UPDATE t0 SET c0 = 1 WHERE ((c0 > 5) AND (c1 < 10 OR (c2 IS NULL)))";
        String result = extractWhereCondition(sql);
        assertNotNull(result);
        assertTrue(result.startsWith("(("));
    }
}
