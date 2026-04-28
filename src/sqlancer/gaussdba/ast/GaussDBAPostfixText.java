package sqlancer.gaussdba.ast;

/**
 * Expression alias wrapper (expr AS alias) for SELECT column aliases. Wraps an expression with an alias text.
 */
public class GaussDBAPostfixText implements GaussDBAExpression {

    private final GaussDBAExpression expr;
    private final String text;

    public GaussDBAPostfixText(GaussDBAExpression expr, String text) {
        this.expr = expr;
        this.text = text;
    }

    public GaussDBAExpression getExpr() {
        return expr;
    }

    public String getText() {
        return text;
    }

    @Override
    public GaussDBADataType getExpressionType() {
        return null;
    }
}