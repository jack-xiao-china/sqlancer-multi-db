package sqlancer.gaussdba.ast;

import sqlancer.common.visitor.UnaryOperation;

/**
 * Alias for expression: expr AS alias
 * Used for column aliases in SELECT list.
 */
public class GaussDBAAlias implements UnaryOperation<GaussDBAExpression>, GaussDBAExpression {

    private final GaussDBAExpression expr;
    private final String alias;

    public GaussDBAAlias(GaussDBAExpression expr, String alias) {
        this.expr = expr;
        this.alias = alias;
    }

    @Override
    public GaussDBAExpression getExpression() {
        return expr;
    }

    @Override
    public String getOperatorRepresentation() {
        return " AS " + alias;
    }

    @Override
    public OperatorKind getOperatorKind() {
        return OperatorKind.POSTFIX;
    }

    @Override
    public boolean omitBracketsWhenPrinting() {
        return true;
    }

    @Override
    public GaussDBADataType getExpressionType() {
        return expr.getExpressionType();
    }
}