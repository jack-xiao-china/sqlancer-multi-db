package sqlancer.mysql.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class MySQLPostfixTextTest {

    @Test
    void testGetExprReturnsCorrectExpression() {
        MySQLExpression expr = MySQLConstant.createIntConstant(42);
        MySQLPostfixText postfixText = new MySQLPostfixText(expr, "alias");

        assertEquals(expr, postfixText.getExpr());
    }

    @Test
    void testGetTextReturnsCorrectAlias() {
        MySQLExpression expr = MySQLConstant.createIntConstant(42);
        String alias = "my_alias";
        MySQLPostfixText postfixText = new MySQLPostfixText(expr, alias);

        assertEquals(alias, postfixText.getText());
    }

    @Test
    void testImplementsMySQLExpression() {
        MySQLPostfixText postfixText = new MySQLPostfixText(MySQLConstant.createIntConstant(1), "test");

        assertNotNull(postfixText);
        assert postfixText instanceof MySQLExpression;
    }

    @Test
    void testWithNullExpression() {
        MySQLPostfixText postfixText = new MySQLPostfixText(null, "alias");

        assertNull(postfixText.getExpr());
        assertEquals("alias", postfixText.getText());
    }

    @Test
    void testWithNullAlias() {
        MySQLExpression expr = MySQLConstant.createIntConstant(42);
        MySQLPostfixText postfixText = new MySQLPostfixText(expr, null);

        assertEquals(expr, postfixText.getExpr());
        assertNull(postfixText.getText());
    }

    @Test
    void testWithTextConstant() {
        MySQLExpression expr = MySQLConstant.createStringConstant("hello");
        MySQLPostfixText postfixText = new MySQLPostfixText(expr, "greeting");

        assertEquals(expr, postfixText.getExpr());
        assertEquals("greeting", postfixText.getText());
    }
}