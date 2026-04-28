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
     *
     * @param expr
     *            the subquery expression (must have a non-null expected value)
     */
    public GaussDBExists(GaussDBExpression expr) {
        this.expr = expr;
        this.expectedValue = expr.getExpectedValue();
        if (expectedValue == null) {
            throw new AssertionError("Expected value cannot be null for EXISTS expression");
        }
    }

    public GaussDBExpression getExpr() {
        return expr;
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        return expectedValue;
    }
}