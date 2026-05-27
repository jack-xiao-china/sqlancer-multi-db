package sqlancer.common.oracle.eet;

import sqlancer.IgnoreMeException;

/**
 * Constant boolean extension (EET Rule for 0/1/TRUE/FALSE).
 *
 * Integer constant 1 → 1 OR random_bool  (always TRUE)
 * Integer constant 0 → 0 AND random_bool  (always FALSE)
 *
 * Follows EET native const_bool transformation: wraps constant boolean
 * values in OR/AND with a random boolean extension expression.
 */
public class EETConstBoolTransformRule<E> implements EETRule<E> {

    public static final String NAME = "ConstBoolTransform";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean canApply(E expr, EETTransformAdapter<E> adapter) {
        if (!adapter.isConstant(expr)) {
            return false;
        }
        if (adapter.isNullConstant(expr)) {
            return false;
        }
        // Apply to: integer 0/1, TRUE/FALSE boolean constants
        return adapter.isZeroOrOneInt(expr) || adapter.isBooleanConstant(expr);
    }

    @Override
    public E apply(E expr, EETTransformAdapter<E> adapter) throws IgnoreMeException {
        E extend;
        try {
            extend = adapter.generateBooleanExpression();
        } catch (IgnoreMeException e) {
            return null; // allow caller to try alternative rules
        }
        if (adapter.asBooleanNotNull(expr)) {
            return adapter.createBinaryLogicalOp(expr, extend, EETTransformAdapter.BinaryLogicalOperator.OR);
        } else {
            return adapter.createBinaryLogicalOp(expr, extend, EETTransformAdapter.BinaryLogicalOperator.AND);
        }
    }
}