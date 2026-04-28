package sqlancer.mysql.ast;

/**
 * Expression alias wrapper (expr AS alias) for SELECT column aliases. Unlike MySQLText which only wraps pure text,
 * MySQLPostfixText wraps an expression with an alias.
 */
public class MySQLPostfixText implements MySQLExpression {
    private final MySQLExpression expr;
    private final String text;

    public MySQLPostfixText(MySQLExpression expr, String text) {
        this.expr = expr;
        this.text = text;
    }

    public MySQLExpression getExpr() {
        return expr;
    }

    public String getText() {
        return text;
    }

}