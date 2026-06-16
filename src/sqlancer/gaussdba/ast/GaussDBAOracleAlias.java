package sqlancer.gaussdba.ast;

/**
 * OracleAlias for CODDTEST: wraps an original expression and an alias expression.
 * Similar to GaussDB-M's GaussDBOracleAlias but adapted for GaussDB-A AST types.
 */
public class GaussDBAOracleAlias implements GaussDBAExpression {

    private final GaussDBAExpression originalExpression;
    private final GaussDBAExpression aliasExpression;

    public GaussDBAOracleAlias(GaussDBAExpression originalExpr, GaussDBAExpression aliasExpr) {
        this.originalExpression = originalExpr;
        this.aliasExpression = aliasExpr;
    }

    public GaussDBAExpression getOriginalExpression() {
        return originalExpression;
    }

    public GaussDBAExpression getAliasExpression() {
        return aliasExpression;
    }

    @Override
    public GaussDBAConstant getExpectedValue() {
        if (aliasExpression != null) {
            return aliasExpression.getExpectedValue();
        }
        return originalExpression != null ? originalExpression.getExpectedValue() : null;
    }

    @Override
    public GaussDBADataType getExpressionType() {
        if (originalExpression != null) {
            return originalExpression.getExpressionType();
        }
        return null;
    }
}
