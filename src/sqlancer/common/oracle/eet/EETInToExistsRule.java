package sqlancer.common.oracle.eet;

import sqlancer.IgnoreMeException;

/**
 * IN to EXISTS semantic rewrite rule:
 * lhs IN (SELECT sel FROM ... WHERE pred) →
 * CASE WHEN (lhs IN subquery) IS NOT NULL
 *   THEN EXISTS(SELECT ... WHERE (sel = lhs) AND pred)
 *   ELSE NULL END
 *
 * Strictly follows EET native implementation (in_query.cc):
 * - For simple query_spec: construct EXISTS with (sel = lhs) AND pred
 * - For unioned_query: construct EXISTS on both sides separately
 * - NULL safety: CASE WHEN (lhs IN subquery) IS NOT NULL THEN EXISTS_result ELSE NULL
 *   handles the gap where IN on NULL returns NULL but EXISTS returns FALSE
 *
 * The dialect-specific construction is delegated to the adapter because
 * subquery manipulation requires deep AST knowledge.
 */
public class EETInToExistsRule<E> implements EETRule<E> {

    @Override
    public String getName() {
        return "InToExists";
    }

    @Override
    public boolean canApply(E expr, EETTransformAdapter<E> adapter) {
        return adapter.isInOperation(expr);
    }

    @Override
    public E apply(E expr, EETTransformAdapter<E> adapter) throws IgnoreMeException {
        E result = adapter.applyInToExistsTransform(expr);
        if (result == null) {
            throw new IgnoreMeException();
        }
        return result;
    }
}