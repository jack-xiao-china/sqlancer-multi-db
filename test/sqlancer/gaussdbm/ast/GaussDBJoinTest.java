package sqlancer.gaussdbm.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBJoin.JoinType;

class GaussDBJoinTest {

    // ========== JoinType enum tests ==========

    @Test
    void testJoinTypeEnumContainsNatural() {
        JoinType[] types = JoinType.values();
        List<JoinType> typeList = Arrays.asList(types);
        assertTrue(typeList.contains(JoinType.NATURAL), "JoinType enum should contain NATURAL");
    }

    @Test
    void testJoinTypeEnumValues() {
        assertEquals(5, JoinType.values().length, "JoinType should have 5 values: NATURAL, INNER, LEFT, RIGHT, CROSS");
    }

    @Test
    void testNaturalJoinTypeValueOf() {
        JoinType natural = JoinType.valueOf("NATURAL");
        assertEquals(JoinType.NATURAL, natural);
    }

    // ========== NATURAL JOIN SQL generation tests ==========

    @Test
    void testNaturalJoinSqlGeneration() {
        // Create a simple table reference
        GaussDBConstant tableRef = GaussDBConstant.createStringConstant("test_table");
        GaussDBJoin join = new GaussDBJoin(tableRef, null, JoinType.NATURAL);

        String sql = GaussDBToStringVisitor.asString(join);
        assertTrue(sql.contains("NATURAL JOIN"), "SQL should contain NATURAL JOIN");
        assertFalse(sql.contains(" ON "), "NATURAL JOIN should not have ON clause");
    }

    @Test
    void testNaturalJoinNoOnClause() {
        GaussDBConstant tableRef = GaussDBConstant.createStringConstant("t1");
        GaussDBJoin join = new GaussDBJoin(tableRef, null, JoinType.NATURAL);

        assertNull(join.getOnCondition(), "NATURAL JOIN onCondition should be null");
    }

    @Test
    void testNaturalJoinGetJoinType() {
        GaussDBConstant tableRef = GaussDBConstant.createStringConstant("t1");
        GaussDBJoin join = new GaussDBJoin(tableRef, null, JoinType.NATURAL);

        assertEquals(JoinType.NATURAL, join.getJoinType());
    }

    @Test
    void testNaturalJoinGetTableReference() {
        GaussDBConstant tableRef = GaussDBConstant.createStringConstant("t1");
        GaussDBJoin join = new GaussDBJoin(tableRef, null, JoinType.NATURAL);

        assertNotNull(join.getTableReference());
        assertEquals(tableRef, join.getTableReference());
    }

    // ========== All JoinType SQL generation tests ==========

    @Test
    void testInnerJoinSqlGeneration() {
        GaussDBConstant tableRef = GaussDBConstant.createStringConstant("t1");
        GaussDBConstant onCondition = GaussDBConstant.createIntConstant(1);
        GaussDBJoin join = new GaussDBJoin(tableRef, onCondition, JoinType.INNER);

        String sql = GaussDBToStringVisitor.asString(join);
        assertTrue(sql.startsWith("JOIN "));
        assertTrue(sql.contains(" ON "));
    }

    @Test
    void testLeftJoinSqlGeneration() {
        GaussDBConstant tableRef = GaussDBConstant.createStringConstant("t1");
        GaussDBConstant onCondition = GaussDBConstant.createIntConstant(1);
        GaussDBJoin join = new GaussDBJoin(tableRef, onCondition, JoinType.LEFT);

        String sql = GaussDBToStringVisitor.asString(join);
        assertTrue(sql.startsWith("LEFT JOIN "));
        assertTrue(sql.contains(" ON "));
    }

    @Test
    void testRightJoinSqlGeneration() {
        GaussDBConstant tableRef = GaussDBConstant.createStringConstant("t1");
        GaussDBConstant onCondition = GaussDBConstant.createIntConstant(1);
        GaussDBJoin join = new GaussDBJoin(tableRef, onCondition, JoinType.RIGHT);

        String sql = GaussDBToStringVisitor.asString(join);
        assertTrue(sql.startsWith("RIGHT JOIN "));
        assertTrue(sql.contains(" ON "));
    }

    @Test
    void testCrossJoinSqlGeneration() {
        GaussDBConstant tableRef = GaussDBConstant.createStringConstant("t1");
        GaussDBJoin join = new GaussDBJoin(tableRef, null, JoinType.CROSS);

        String sql = GaussDBToStringVisitor.asString(join);
        assertTrue(sql.startsWith("CROSS JOIN "));
        assertFalse(sql.contains(" ON "), "CROSS JOIN should not have ON clause");
    }

    // ========== Join without ON clause tests ==========

    @Test
    void testJoinWithNullOnCondition() {
        GaussDBConstant tableRef = GaussDBConstant.createStringConstant("t1");
        GaussDBJoin join = new GaussDBJoin(tableRef, null, JoinType.NATURAL);

        assertNull(join.getOnCondition());
    }

    @Test
    void testSetOnClause() {
        GaussDBConstant tableRef = GaussDBConstant.createStringConstant("t1");
        GaussDBJoin join = new GaussDBJoin(tableRef, null, JoinType.INNER);

        assertNull(join.getOnCondition());

        GaussDBConstant newOnCondition = GaussDBConstant.createIntConstant(1);
        join.setOnClause(newOnCondition);

        assertEquals(newOnCondition, join.getOnCondition());
    }

    // ========== Expected value tests ==========

    @Test
    void testGetExpectedValue() {
        GaussDBConstant tableRef = GaussDBConstant.createStringConstant("t1");
        GaussDBJoin join = new GaussDBJoin(tableRef, null, JoinType.NATURAL);

        GaussDBConstant expected = join.getExpectedValue();
        assertTrue(expected.isNull(), "Join expected value should be NULL");
    }
}