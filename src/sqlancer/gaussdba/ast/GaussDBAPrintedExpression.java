package sqlancer.gaussdba.ast;

import sqlancer.gaussdba.GaussDBAToStringVisitor;

/**
 * Snapshot expression for EET value-transform rules (Rule 5-6).
 * Captures the SQL string at construction time so the same logical expression
 * rendered in different branches of a CASE produces distinct strings.
 */
public class GaussDBAPrintedExpression implements GaussDBAExpression {

    private final GaussDBAExpression original;
    private final String printedSql;

    public GaussDBAPrintedExpression(GaussDBAExpression original) {
        this.original = original;
        this.printedSql = GaussDBAToStringVisitor.asString(original);
    }

    public GaussDBAExpression getOriginal() {
        return original;
    }

    public String getPrintedSql() {
        return printedSql;
    }

    @Override
    public GaussDBAConstant getExpectedValue() {
        return original.getExpectedValue();
    }

    @Override
    public GaussDBADataType getExpressionType() {
        return original.getExpressionType();
    }
}