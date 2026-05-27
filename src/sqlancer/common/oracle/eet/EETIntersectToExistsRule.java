package sqlancer.common.oracle.eet;

import sqlancer.IgnoreMeException;

/**
 * INTERSECT to EXISTS semantic rewrite rule:
 * Q1 INTERSECT Q2 → Q1 WHERE EXISTS(Q2 WHERE (q1.col=q2.col OR (q1.col IS NULL AND q2.col IS NULL)) AND ... AND Q2_pred) AND Q1_pred
 *
 * Strictly follows EET native implementation (grammar.cc):
 * - Per-column NULL-aware equality: (q1_col = q2_col) OR (q1_col IS NULL AND q2_col IS NULL)
 * - All column predicates ANDed together, then ANDed with Q2's original WHERE predicate
 * - The EXISTS subquery is ANDed with Q1's original WHERE predicate
 * - Only applies when neither Q1 nor Q2 has GROUP BY or window functions
 *
 * MySQL does NOT support INTERSECT/EXCEPT, so this rule is disabled via supportsRule().
 */
public class EETIntersectToExistsRule<E> implements EETRule<E> {

    @Override
    public String getName() {
        return "IntersectToExists";
    }

    @Override
    public boolean canApply(E expr, EETTransformAdapter<E> adapter) {
        // INTERSECT node detection — dialect-specific
        return adapter.isIntersect(expr);
    }

    @Override
    public E apply(E expr, EETTransformAdapter<E> adapter) throws IgnoreMeException {
        E result = adapter.applyIntersectToExistsTransform(expr);
        if (result == null) {
            throw new IgnoreMeException();
        }
        return result;
    }
}