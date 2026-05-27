package sqlancer.gaussdba.ast;

/**
 * Scalar subquery expression: (SELECT ...) used as a value in an expression.
 * Example: SELECT (SELECT MAX(x) FROM t1) + 1 FROM t2
 */
public class GaussDBAScalarSubquery implements GaussDBAExpression {

    private final GaussDBASelect subquery;

    public GaussDBAScalarSubquery(GaussDBASelect subquery) {
        this.subquery = subquery;
    }

    public GaussDBASelect getSubquery() {
        return subquery;
    }

    @Override
    public GaussDBADataType getExpressionType() {
        // Scalar subquery type depends on subquery result, default to NUMBER
        return GaussDBADataType.NUMBER;
    }

    public static GaussDBAScalarSubquery create(GaussDBASelect subquery) {
        return new GaussDBAScalarSubquery(subquery);
    }
}