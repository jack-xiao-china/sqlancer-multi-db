package sqlancer.common.oracle.eet;

import sqlancer.IgnoreMeException;

/**
 * NULLIF to CASE semantic rewrite rule:
 * NULLIF(a, b) → CASE WHEN a = b THEN NULL ELSE a END
 *
 * Strictly follows EET native implementation:
 * - NULLIF with two arguments transforms to CASE with equality check
 * - When a = b, return NULL; otherwise return a
 *
 * This transformation tests NULL handling differences between NULLIF and CASE.
 */
public class EETNullifToCaseRule<E> implements EETRule<E> {

    @Override
    public String getName() {
        return "NullifToCase";
    }

    @Override
    public boolean canApply(E expr, EETTransformAdapter<E> adapter) {
        return adapter.isNullif(expr);
    }

    @Override
    public E apply(E expr, EETTransformAdapter<E> adapter) throws IgnoreMeException {
        E[] args = adapter.getNullifArguments(expr);
        if (args == null || args.length != 2) {
            throw new IgnoreMeException();
        }

        E a = args[0];
        E b = args[1];

        // a = b
        E equalsExpr = adapter.createComparisonOp(a, b, EETTransformAdapter.ComparisonOperator.EQUALS);

        // CASE WHEN a = b THEN NULL ELSE a END
        E nullLiteral = adapter.createNullLiteral();
        return adapter.createCaseWhen(equalsExpr, nullLiteral, a);
    }
}