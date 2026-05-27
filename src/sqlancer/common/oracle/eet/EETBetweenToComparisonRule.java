package sqlancer.common.oracle.eet;

import sqlancer.IgnoreMeException;

/**
 * BETWEEN to Comparison semantic rewrite rule:
 * x BETWEEN a AND b → (x >= a) AND (x <= b)
 *
 * Strictly follows EET native implementation (between_op.cc):
 * - Extract subject (mhs), lower (lhs), upper (rhs) from BETWEEN
 * - Create comparison: (mhs >= lhs) AND (mhs <= rhs)
 *
 * This transformation may expose NULL handling differences:
 * BETWEEN and >=/<= have different NULL semantics in some DBMS.
 */
public class EETBetweenToComparisonRule<E> implements EETRule<E> {

    @Override
    public String getName() {
        return "BetweenToComp";
    }

    @Override
    public boolean canApply(E expr, EETTransformAdapter<E> adapter) {
        return adapter.isBetween(expr);
    }

    @Override
    public E apply(E expr, EETTransformAdapter<E> adapter) throws IgnoreMeException {
        E[] children = adapter.getBetweenChildren(expr);
        if (children == null || children.length != 3) {
            throw new IgnoreMeException();
        }
        E mhs = children[0]; // subject
        E lhs = children[1]; // lower bound
        E rhs = children[2]; // upper bound

        // (mhs >= lhs)
        E geExpr = adapter.createComparisonOp(mhs, lhs, EETTransformAdapter.ComparisonOperator.GREATER_EQUALS);
        // (mhs <= rhs)
        E leExpr = adapter.createComparisonOp(mhs, rhs, EETTransformAdapter.ComparisonOperator.LESS_EQUALS);
        // (mhs >= lhs) AND (mhs <= rhs)
        return adapter.createBinaryLogicalOp(geExpr, leExpr, EETTransformAdapter.BinaryLogicalOperator.AND);
    }
}