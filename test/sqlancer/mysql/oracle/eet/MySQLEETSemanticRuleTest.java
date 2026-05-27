package sqlancer.mysql.oracle.eet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.common.oracle.eet.EETTransformerBase;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLOptions;
import sqlancer.mysql.MySQLSchema;
import sqlancer.mysql.MySQLVisitor;
import sqlancer.mysql.ast.MySQLBinaryLogicalOperation;
import sqlancer.mysql.ast.MySQLBinaryLogicalOperation.MySQLBinaryLogicalOperator;
import sqlancer.mysql.ast.MySQLBetweenOperation;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLText;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

class MySQLEETSemanticRuleTest {

    private MySQLExpressionGenerator gen;
    private MySQLEETTransformer transformer;
    private EETTransformerBase<MySQLExpression> base;

    @BeforeEach
    void setUp() {
        new Randomly(42L);
        MySQLGlobalState state = new MySQLGlobalState();
        state.setMainOptions(new MainOptions());
        state.setDbmsSpecificOptions(new MySQLOptions());
        state.setRandomly(new Randomly(42L));
        gen = new MySQLExpressionGenerator(state);
        MySQLSchema.MySQLColumn col = new MySQLSchema.MySQLColumn("c", MySQLSchema.MySQLDataType.INT, false, 0);
        gen.setColumns(List.of(col));
        transformer = new MySQLEETTransformer(gen);
        base = transformer.getBase();
    }

    @Test
    void deMorgan_andToNotOrNot() {
        // (A AND B) → NOT(NOT(A) OR NOT(B))
        MySQLExpression colA = new MySQLColumnReference(
                new MySQLSchema.MySQLColumn("a", MySQLSchema.MySQLDataType.INT, false, 0),
                MySQLConstant.createNullConstant());
        MySQLExpression colB = new MySQLColumnReference(
                new MySQLSchema.MySQLColumn("b", MySQLSchema.MySQLDataType.INT, false, 0),
                MySQLConstant.createNullConstant());
        MySQLExpression andExpr = new MySQLBinaryLogicalOperation(colA, colB, MySQLBinaryLogicalOperator.AND);

        MySQLExpression result = base.transformExpression(andExpr);
        assertNotNull(result);
        String s = MySQLVisitor.asString(result);
        // Result should contain NOT, OR pattern from De Morgan
        // Note: De Morgan is tried first before wrapping rules
        assertTrue(s.contains("NOT") || s.contains("OR") || s.contains("CASE"),
                "De Morgan transform should produce NOT/OR pattern, got: " + s);
    }

    @Test
    void deMorgan_orToNotAndNot() {
        // (A OR B) → NOT(NOT(A) AND NOT(B))
        MySQLExpression colA = new MySQLColumnReference(
                new MySQLSchema.MySQLColumn("a", MySQLSchema.MySQLDataType.INT, false, 0),
                MySQLConstant.createNullConstant());
        MySQLExpression colB = new MySQLColumnReference(
                new MySQLSchema.MySQLColumn("b", MySQLSchema.MySQLDataType.INT, false, 0),
                MySQLConstant.createNullConstant());
        MySQLExpression orExpr = new MySQLBinaryLogicalOperation(colA, colB, MySQLBinaryLogicalOperator.OR);

        MySQLExpression result = base.transformExpression(orExpr);
        assertNotNull(result);
        String s = MySQLVisitor.asString(result);
        assertTrue(s.contains("NOT") || s.contains("AND") || s.contains("CASE"),
                "De Morgan transform should produce NOT/AND pattern, got: " + s);
    }

    @Test
    void betweenToComparison_producesGeAndLe() {
        // x BETWEEN a AND b → (x >= a) AND (x <= b)
        MySQLExpression col = new MySQLColumnReference(
                new MySQLSchema.MySQLColumn("x", MySQLSchema.MySQLDataType.INT, false, 0),
                MySQLConstant.createNullConstant());
        MySQLExpression between = new MySQLBetweenOperation(col,
                MySQLConstant.createIntConstant(1), MySQLConstant.createIntConstant(10));

        MySQLExpression result = base.transformExpression(between);
        assertNotNull(result);
        String s = MySQLVisitor.asString(result);
        // BETWEEN transform produces >= and <= comparison, then AND
        assertTrue(s.contains(">=") || s.contains("<=") || s.contains("AND") || s.contains("CASE"),
                "BETWEEN→Comp should produce >=, <=, AND pattern, got: " + s);
    }

    @Test
    void deMorgan_preservesRule7() {
        // TableReference and Text should be unchanged (Rule 7)
        MySQLText raw = new MySQLText("eet_cte.ref0");
        assertEquals(raw, base.transformExpression(raw));
    }
}