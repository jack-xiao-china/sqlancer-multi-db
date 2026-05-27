package sqlancer.common.oracle.eet;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;

/**
 * CASE WHEN value wrapping (EET Rule 3-6).
 *
 * Rule 3: expr → CASE WHEN false_expr THEN rand ELSE expr END
 * Rule 4: expr → CASE WHEN true_expr THEN expr ELSE rand END
 * Rule 5: expr → CASE WHEN rand THEN copy_expr ELSE expr END
 * Rule 6: expr → CASE WHEN rand THEN expr ELSE copy_expr END
 *
 * Uses randomly generated boolean for condition, randomly generated value for
 * the "other" branch, and printed_expr (snapshot of the original) for the
 * "copy" branch in Rule 5/6.
 */
public class EETValueTransformRule<E> implements EETRule<E> {

    public static final String NAME = "ValueTransform";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean canApply(E expr, EETTransformAdapter<E> adapter) {
        // Value transform can apply to any expression (not just boolean-like)
        return true;
    }

    @Override
    public E apply(E expr, EETTransformAdapter<E> adapter) throws IgnoreMeException {
        E randomBool = adapter.generateBooleanExpression();
        int choice = (int) Randomly.getNotCachedInteger(0, 9);
        E randVal = adapter.generateSameTypeExpression(expr);

        if (choice <= 2) {
            E trueExpr = buildTrueExpr(randomBool, adapter);
            return adapter.createCaseWhen(trueExpr, adapter.createPrintedExpression(expr), randVal);
        } else if (choice <= 5) {
            E falseExpr = buildFalseExpr(randomBool, adapter);
            return adapter.createCaseWhen(falseExpr, randVal, adapter.createPrintedExpression(expr));
        } else {
            E copy = adapter.createPrintedExpression(expr);
            if (Randomly.getBoolean()) {
                return adapter.createCaseWhen(randomBool, copy, expr);
            } else {
                return adapter.createCaseWhen(randomBool, expr, copy);
            }
        }
    }

    private E buildTrueExpr(E randomBool, EETTransformAdapter<E> adapter) {
        E notRand = adapter.createNot(randomBool);
        E randIsNull = adapter.createIsNull(randomBool);
        E part1 = adapter.createBinaryLogicalOp(randomBool, notRand, EETTransformAdapter.BinaryLogicalOperator.OR);
        return adapter.createBinaryLogicalOp(part1, randIsNull, EETTransformAdapter.BinaryLogicalOperator.OR);
    }

    private E buildFalseExpr(E randomBool, EETTransformAdapter<E> adapter) {
        E notRand = adapter.createNot(randomBool);
        E randIsNotNull = adapter.createIsNotNull(randomBool);
        E part1 = adapter.createBinaryLogicalOp(randomBool, notRand, EETTransformAdapter.BinaryLogicalOperator.AND);
        return adapter.createBinaryLogicalOp(part1, randIsNotNull, EETTransformAdapter.BinaryLogicalOperator.AND);
    }
}