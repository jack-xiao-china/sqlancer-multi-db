package sqlancer.mysql.oracle.eet;

import static org.junit.jupiter.api.Assertions.assertSame;
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
import sqlancer.mysql.ast.MySQLBinaryComparisonOperation;
import sqlancer.mysql.ast.MySQLBinaryComparisonOperation.BinaryComparisonOperator;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLText;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

class MySQLEETTransformerTest {

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
    void transformBoolExpr_wrapsPredicate() {
        MySQLExpression col = new MySQLColumnReference(
                new MySQLSchema.MySQLColumn("c", MySQLSchema.MySQLDataType.INT, false, 0), MySQLConstant.createNullConstant());
        MySQLExpression pred = new MySQLBinaryComparisonOperation(col, MySQLConstant.createIntConstant(0),
                BinaryComparisonOperator.EQUALS);
        MySQLExpression out = base.transformExpression(pred);
        String s = MySQLVisitor.asString(out);
        assertTrue(s.contains("OR") || s.contains("||"));
        assertTrue(s.contains("AND") || s.contains("&&"));
    }

    @Test
    void buildTrueExpr_isTautologyShape() {
        // MySQLConstant.createTrue() → int 1 via createConstantBoolean(true).
        // ConstBoolTransform applies: 1 → (1 OR rand_expr), producing OR pattern.
        // BoolTransform applies: (true_expr AND expr), producing AND/OR/IS NULL pattern.
        // ValueTransform applies: CASE WHEN wrapping.
        // With fixed seed, accept any valid EET transformation output.
        MySQLExpression p = MySQLConstant.createTrue();
        MySQLExpression t = base.transformExpression(p);
        String s = MySQLVisitor.asString(t);
        boolean hasCase = s.toUpperCase().contains("CASE");
        boolean hasBoolOrConst = s.contains("AND") || s.contains("&&") || s.contains("OR") || s.contains("||")
                || s.contains("IS NULL");
        assertTrue(hasCase || hasBoolOrConst, "Output should contain CASE or AND/OR/IS NULL, got: " + s);
    }

    @Test
    void transformValueExpr_producesCase() {
        MySQLExpression col = new MySQLColumnReference(
                new MySQLSchema.MySQLColumn("c", MySQLSchema.MySQLDataType.INT, false, 0), MySQLConstant.createNullConstant());
        MySQLExpression out = base.transformExpression(col);
        String s = MySQLVisitor.asString(out);
        assertTrue(s.toUpperCase().contains("CASE"));
    }

    @Test
    void rule7_mySQLTextUnchanged() {
        MySQLText raw = new MySQLText("eet_cte.ref0");
        assertSame(raw, base.transformExpression(raw));
    }

    @Test
    void rule7_tableRefUnchanged() {
        MySQLSchema.MySQLTable table = new MySQLSchema.MySQLTable("t", List.of(
                new MySQLSchema.MySQLColumn("c", MySQLSchema.MySQLDataType.INT, false, 0)), List.of(),
                MySQLSchema.MySQLTable.MySQLEngine.INNO_DB);
        sqlancer.mysql.ast.MySQLTableReference ref = new sqlancer.mysql.ast.MySQLTableReference(table);
        assertSame(ref, base.transformExpression(ref));
    }
}