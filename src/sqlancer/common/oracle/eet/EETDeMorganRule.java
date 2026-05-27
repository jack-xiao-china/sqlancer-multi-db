package sqlancer.common.oracle.eet;

import sqlancer.IgnoreMeException;

/**
 * De Morgan's Law semantic rewrite rule:
 * (A AND B) → NOT(NOT(A) OR NOT(B))
 * (A OR B) → NOT(NOT(A) AND NOT(B))
 *
 * Strictly follows EET native implementation (bool_term.cc):
 * - Swap the operator (AND→OR, OR→AND)
 * - Wrap both children in NOT
 * - Construct new term with swapped operator and negated children
 * - Wrap the whole thing in NOT
 *
 * Equivalent in three-valued logic (NULL-safe).
 */
public class EETDeMorganRule<E> implements EETRule<E> {

    @Override
    public String getName() {
        return "DeMorgan";
    }

    @Override
    public boolean canApply(E expr, EETTransformAdapter<E> adapter) {
        return adapter.isAndOp(expr) || adapter.isOrOp(expr);
    }

    @Override
    public E apply(E expr, EETTransformAdapter<E> adapter) throws IgnoreMeException {
        if (adapter.isAndOp(expr)) {
            // (A AND B) → NOT(NOT(A) OR NOT(B))
            E[] children = getChildren(expr, adapter);
            E notA = adapter.createNot(children[0]);
            E notB = adapter.createNot(children[1]);
            E orExpr = adapter.createBinaryLogicalOp(notA, notB, EETTransformAdapter.BinaryLogicalOperator.OR);
            return adapter.createNot(orExpr);
        }
        if (adapter.isOrOp(expr)) {
            // (A OR B) → NOT(NOT(A) AND NOT(B))
            E[] children = getChildren(expr, adapter);
            E notA = adapter.createNot(children[0]);
            E notB = adapter.createNot(children[1]);
            E andExpr = adapter.createBinaryLogicalOp(notA, notB, EETTransformAdapter.BinaryLogicalOperator.AND);
            return adapter.createNot(andExpr);
        }
        throw new IgnoreMeException();
    }

    /**
     * Extract left and right children from a binary logical operation.
     * Uses the adapter's mapChildren with a collector function to extract children.
     */
    private E[] getChildren(E expr, EETTransformAdapter<E> adapter) throws IgnoreMeException {
        // We need to extract left/right from the binary logical op.
        // Since adapter doesn't have a direct getLeft/getRight, we use
        // the dialect-specific extraction via the adapter.
        // The adapter must provide a way to get children of binary ops.
        // For now, we rely on the fact that mapChildren traverses children
        // but we need to extract them directly.
        // We'll add getBinaryLogicalOpChildren to the adapter.
        E[] children = adapter.getBinaryLogicalOpChildren(expr);
        if (children == null || children.length != 2) {
            throw new IgnoreMeException();
        }
        return children;
    }
}