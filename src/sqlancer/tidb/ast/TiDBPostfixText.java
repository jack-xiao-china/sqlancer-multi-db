package sqlancer.tidb.ast;

/**
 * Expression alias wrapper (expr AS alias) for SELECT column aliases. Wraps an expression with an alias text.
 */
public class TiDBPostfixText implements TiDBExpression {

    private final TiDBExpression expr;
    private final String text;

    public TiDBPostfixText(TiDBExpression expr, String text) {
        this.expr = expr;
        this.text = text;
    }

    public TiDBExpression getExpr() {
        return expr;
    }

    public String getText() {
        return text;
    }
}