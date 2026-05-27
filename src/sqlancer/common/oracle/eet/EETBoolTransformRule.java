package sqlancer.common.oracle.eet;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;

/**
 * Boolean tautology/contradiction wrapping (EET Rule 1-2).
 *
 * Rule 1: expr → ((p OR NOT p OR p IS NULL) AND expr)  -- true_expr wrapping
 * Rule 2: expr → ((p AND NOT p AND p IS NOT NULL) OR expr)  -- false_expr wrapping
 *
 * p is a randomly generated boolean expression. The true/false_expr is always
 * TRUE/FALSE respectively under SQL's three-valued logic.
 */
public class EETBoolTransformRule<E> implements EETRule<E> {

    public static final String NAME = "BoolTransform";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean canApply(E expr, EETTransformAdapter<E> adapter) {
        return adapter.isBooleanLike(expr);
    }

    @Override
    public E apply(E expr, EETTransformAdapter<E> adapter) throws IgnoreMeException {
        E randomBool = adapter.generateBooleanExpression();
        int choice = (int) Randomly.getNotCachedInteger(0, 6);
        boolean useTrueExpr = choice <= 2;

        E notRand = adapter.createNot(randomBool);
        E randIsNull = adapter.createIsNull(randomBool);
        E randIsNotNull = adapter.createIsNotNull(randomBool);

        if (useTrueExpr) {
            E part1 = adapter.createBinaryLogicalOp(randomBool, notRand, EETTransformAdapter.BinaryLogicalOperator.OR);
            E base = adapter.createBinaryLogicalOp(part1, randIsNull, EETTransformAdapter.BinaryLogicalOperator.OR);
            return adapter.createBinaryLogicalOp(base, expr, EETTransformAdapter.BinaryLogicalOperator.AND);
        } else {
            E part1 = adapter.createBinaryLogicalOp(randomBool, notRand, EETTransformAdapter.BinaryLogicalOperator.AND);
            E base = adapter.createBinaryLogicalOp(part1, randIsNotNull, EETTransformAdapter.BinaryLogicalOperator.AND);
            return adapter.createBinaryLogicalOp(base, expr, EETTransformAdapter.BinaryLogicalOperator.OR);
        }
    }
}