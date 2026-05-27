package sqlancer.common.oracle.eet;

import sqlancer.IgnoreMeException;

/**
 * EXCEPT to NOT EXISTS semantic rewrite rule:
 * Q1 EXCEPT Q2 → Q1 WHERE NOT EXISTS(Q2 WHERE (q1.col=q2.col OR (q1.col IS NULL AND q2.col IS NULL)) AND ... AND Q2_pred) AND Q1_pred
 *
 * Same as INTERSECT→EXISTS but wraps EXISTS in NOT.
 * Strictly follows EET native implementation (grammar.cc).
 *
 * MySQL does NOT support EXCEPT, so this rule is disabled via supportsRule().
 */
public class EETExceptToNotExistsRule<E> implements EETRule<E> {

    @Override
    public String getName() {
        return "ExceptToNotExists";
    }

    @Override
    public boolean canApply(E expr, EETTransformAdapter<E> adapter) {
        return adapter.isExcept(expr);
    }

    @Override
    public E apply(E expr, EETTransformAdapter<E> adapter) throws IgnoreMeException {
        E result = adapter.applyExceptToNotExistsTransform(expr);
        if (result == null) {
            throw new IgnoreMeException();
        }
        return result;
    }
}