package sqlancer.gaussdbm.ast;

import sqlancer.IgnoreMeException;

public class GaussDBBetweenOperation implements GaussDBExpression {

    private final GaussDBExpression expr;
    private final GaussDBExpression left;
    private final GaussDBExpression right;
    private final boolean isNegated;

    public GaussDBBetweenOperation(GaussDBExpression expr, GaussDBExpression left, GaussDBExpression right,
            boolean isNegated) {
        this.expr = expr;
        this.left = left;
        this.right = right;
        this.isNegated = isNegated;
    }

    public GaussDBExpression getExpr() {
        return expr;
    }

    public GaussDBExpression getLeft() {
        return left;
    }

    public GaussDBExpression getRight() {
        return right;
    }

    public boolean isNegated() {
        return isNegated;
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        GaussDBConstant ev = expr.getExpectedValue();
        GaussDBConstant lv = left.getExpectedValue();
        GaussDBConstant rv = right.getExpectedValue();
        if (!lv.isNull() && lv.isInt() && lv.asIntNotNull() < 0 || !rv.isNull() && rv.isInt() && rv.asIntNotNull() < 0
                || !ev.isNull() && ev.isInt() && ev.asIntNotNull() < 0) {
            throw new IgnoreMeException();
        }
        GaussDBBinaryComparisonOperation leftCmp = new GaussDBBinaryComparisonOperation(lv, ev,
                GaussDBBinaryComparisonOperation.BinaryComparisonOperator.SMALLER_EQUALS);
        GaussDBBinaryComparisonOperation rightCmp = new GaussDBBinaryComparisonOperation(ev, rv,
                GaussDBBinaryComparisonOperation.BinaryComparisonOperator.SMALLER_EQUALS);
        GaussDBBinaryLogicalOperation andOp = new GaussDBBinaryLogicalOperation(leftCmp, rightCmp,
                GaussDBBinaryLogicalOperation.GaussDBBinaryLogicalOperator.AND);
        GaussDBConstant inner = andOp.getExpectedValue();
        if (!isNegated) {
            return inner;
        }
        if (inner.isNull()) {
            return GaussDBConstant.createNullConstant();
        }
        return GaussDBConstant.createBooleanConstant(!inner.asBooleanNotNull());
    }
}
