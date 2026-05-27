package sqlancer.common.oracle.eet;

import sqlancer.IgnoreMeException;

/**
 * EXISTS to IN semantic rewrite rule:
 * EXISTS(SELECT ... FROM t WHERE p) → TRUE IN (SELECT CASE WHEN p IS NULL THEN FALSE ELSE p END FROM t WHERE TRUE)
 *
 * Strictly follows EET native implementation (exists_predicate.cc):
 * - Only applies to simple query_spec (no GROUP BY, no window functions)
 * - NULL safety: CASE WHEN (p IS NULL) THEN FALSE ELSE p END handles the semantic gap
 *   where EXISTS(SELECT...WHERE NULL) returns FALSE but TRUE IN (SELECT NULL...) returns NULL
 * - The subquery WHERE clause becomes WHERE TRUE (always true)
 * - The select list is replaced with the CASE expression
 *
 * Conditions for NOT applying (EET native):
 * - has_group: skip (GROUP BY present)
 * - has_window: skip (window functions present)
 * - unioned_query: skip (UNION/INTERSECT/EXCEPT subquery)
 */
public class EETExistsToInRule<E> implements EETRule<E> {

    @Override
    public String getName() {
        return "ExistsToIn";
    }

    @Override
    public boolean canApply(E expr, EETTransformAdapter<E> adapter) {
        // Only applies to EXISTS expressions
        return adapter.isExists(expr);
    }

    @Override
    public E apply(E expr, EETTransformAdapter<E> adapter) throws IgnoreMeException {
        // The adapter handles the dialect-specific construction
        // because EXISTS subquery extraction and IN subquery construction
        // require deep knowledge of the AST structure
        E result = adapter.applyExistsToInTransform(expr);
        if (result == null) {
            throw new IgnoreMeException();
        }
        return result;
    }
}