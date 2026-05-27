package sqlancer.common.oracle.eet;

import sqlancer.IgnoreMeException;

/**
 * Pluggable transformation rule interface for EET. Each rule can apply to an expression
 * and produce a semantically equivalent result. Rules are registered with
 * {@link EETTransformerBase} and tried in order during transformation.
 */
public interface EETRule<E> {

    /** Unique name for this rule (used for configuration and dialect support checks). */
    String getName();

    /** Whether this rule can potentially apply to the given expression. */
    boolean canApply(E expr, EETTransformAdapter<E> adapter);

    /**
     * Apply the rule to the expression, producing a semantically equivalent result.
     * Throws {@link IgnoreMeException} if the rule cannot produce a valid transform
     * for this particular expression (e.g., sub-expression generation failure).
     */
    E apply(E expr, EETTransformAdapter<E> adapter) throws IgnoreMeException;
}