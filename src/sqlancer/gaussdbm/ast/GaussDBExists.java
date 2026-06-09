package sqlancer.gaussdbm.ast;

/**
 * Represents an EXISTS subquery expression in GaussDB-M. EXISTS returns TRUE if the subquery returns any rows, FALSE
 * otherwise.
 */
public class GaussDBExists implements GaussDBExpression {

    private final GaussDBExpression expr;
    private final GaussDBConstant expectedValue;

    /**
     * Creates an EXISTS expression with a precomputed expected value.
     *
     * @param expr
     *            the subquery expression
     * @param expectedValue
     *            the expected result value (TRUE/FALSE)
     */
    public GaussDBExists(GaussDBExpression expr, GaussDBConstant expectedValue) {
        this.expr = expr;
        this.expectedValue = expectedValue;
    }

    /**
     * Creates an EXISTS expression by extracting the expected value from the subquery.
     * Tolerates null expected values (e.g., in JIR context where expected values are not computed).
     *
     * @param expr
     *            the subquery expression
     */
    public GaussDBExists(GaussDBExpression expr) {
        this.expr = expr;
        GaussDBConstant ev = null;
        try {
            ev = expr.getExpectedValue();
        } catch (Exception e) {
            // ignore — expected value may be null in some contexts
        }
        this.expectedValue = ev;
    }

    public GaussDBExpression getExpr() {
        return expr;
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        return expectedValue;
    }
}