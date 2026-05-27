package sqlancer.gaussdbm.ast;

/**
 * Scalar subquery expression: (SELECT ...) used as a value in an expression.
 * Example: SELECT (SELECT MAX(x) FROM t1) + 1 FROM t2
 */
public class GaussDBScalarSubquery implements GaussDBExpression {

    private final GaussDBSelect subquery;

    public GaussDBScalarSubquery(GaussDBSelect subquery) {
        this.subquery = subquery;
    }

    public GaussDBSelect getSubquery() {
        return subquery;
    }

    public static GaussDBScalarSubquery create(GaussDBSelect subquery) {
        return new GaussDBScalarSubquery(subquery);
    }
}