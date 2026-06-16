package sqlancer.gaussdba.ast;

/**
 * OracleExpressionBag for CODDTEST: a mutable container that holds an expression
 * which can be replaced (e.g., folding an expression to a constant).
 * Similar to GaussDB-M's GaussDBOracleExpressionBag but adapted for GaussDB-A AST types.
 */
public class GaussDBAOracleExpressionBag implements GaussDBAExpression {

    private GaussDBAExpression innerExpr;

    public GaussDBAOracleExpressionBag(GaussDBAExpression expr) {
        this.innerExpr = expr;
    }

    public void updateInnerExpr(GaussDBAExpression expr) {
        this.innerExpr = expr;
    }

    public GaussDBAExpression getInnerExpr() {
        return innerExpr;
    }

    @Override
    public GaussDBAConstant getExpectedValue() {
        if (innerExpr != null) {
            return innerExpr.getExpectedValue();
        }
        return null;
    }

    @Override
    public GaussDBADataType getExpressionType() {
        if (innerExpr != null) {
            return innerExpr.getExpressionType();
        }
        return null;
    }
}
