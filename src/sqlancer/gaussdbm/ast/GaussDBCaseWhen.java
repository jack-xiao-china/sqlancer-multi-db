package sqlancer.gaussdbm.ast;

/**
 * {@code CASE WHEN whenExpr THEN thenExpr ELSE elseExpr END} (M-compatible searched CASE).
 */
public class GaussDBCaseWhen implements GaussDBExpression {

    private final GaussDBExpression whenExpr;
    private final GaussDBExpression thenExpr;
    private final GaussDBExpression elseExpr;

    public GaussDBCaseWhen(GaussDBExpression whenExpr, GaussDBExpression thenExpr, GaussDBExpression elseExpr) {
        this.whenExpr = whenExpr;
        this.thenExpr = thenExpr;
        this.elseExpr = elseExpr;
    }

    public GaussDBExpression getWhenExpr() {
        return whenExpr;
    }

    public GaussDBExpression getThenExpr() {
        return thenExpr;
    }

    public GaussDBExpression getElseExpr() {
        return elseExpr;
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        GaussDBConstant w = whenExpr.getExpectedValue();
        if (w == null) {
            return null; // unpredictable operand: propagate
        }
        if (w.isNull()) {
            return GaussDBConstant.createNullConstant();
        }
        return w.asBooleanNotNull() ? thenExpr.getExpectedValue() : elseExpr.getExpectedValue();
    }
}
