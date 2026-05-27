package sqlancer.common.oracle.eet;

import sqlancer.IgnoreMeException;

/**
 * COALESCE to CASE semantic rewrite rule:
 * COALESCE(a, b) → CASE WHEN a IS NOT NULL THEN a ELSE b END
 *
 * Strictly follows EET native implementation:
 * - COALESCE with two arguments transforms to CASE with IS NOT NULL check
 * - For more arguments, transforms recursively: COALESCE(a,b,c) → CASE WHEN a IS NOT NULL THEN a ELSE COALESCE(b,c) END
 *
 * This transformation tests NULL handling differences between COALESCE and CASE.
 */
public class EETCoalesceToCaseRule<E> implements EETRule<E> {

    @Override
    public String getName() {
        return "CoalesceToCase";
    }

    @Override
    public boolean canApply(E expr, EETTransformAdapter<E> adapter) {
        return adapter.isCoalesce(expr);
    }

    @Override
    public E apply(E expr, EETTransformAdapter<E> adapter) throws IgnoreMeException {
        E[] args = adapter.getCoalesceArguments(expr);
        if (args == null || args.length < 2) {
            throw new IgnoreMeException();
        }

        // COALESCE(a, b) → CASE WHEN a IS NOT NULL THEN a ELSE b END
        if (args.length == 2) {
            E a = args[0];
            E b = args[1];
            E isNotNull = adapter.createIsNullCheck(a, true); // a IS NOT NULL
            return adapter.createCaseWhen(isNotNull, a, b);
        }

        // COALESCE(a, b, c, ...) → CASE WHEN a IS NOT NULL THEN a ELSE COALESCE(b, c, ...) END
        // Handle recursively
        E a = args[0];
        E isNotNull = adapter.createIsNullCheck(a, true);
        @SuppressWarnings("unchecked")
        E[] remainingArgs = (E[]) new Object[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            remainingArgs[i - 1] = args[i];
        }
        E remainingCoalesce = adapter.createCoalesce(remainingArgs);
        return adapter.createCaseWhen(isNotNull, a, remainingCoalesce);
    }
}