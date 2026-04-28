package sqlancer.gaussdbm.ast;

/**
 * Expression alias wrapper (expr AS alias) for SELECT column aliases. Wraps an expression with an alias text.
 */
public class GaussDBPostfixText implements GaussDBExpression {

    private final GaussDBExpression expr;
    private final String text;

    public GaussDBPostfixText(GaussDBExpression expr, String text) {
        this.expr = expr;
        this.text = text;
    }

    public GaussDBExpression getExpr() {
        return expr;
    }

    public String getText() {
        return text;
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        throw new AssertionError("GaussDBPostfixText");
    }
}