package sqlancer.common.oracle.jir;

/**
 * How to combine target query results for comparison with the source query.
 */
public enum JIRResultType {

    /**
     * Combine target results via UNION ALL (bag semantics). Used for decomposition rules where the source equals the
     * union of multiple target parts.
     */
    UNION_ALL,

    /**
     * Single target query, direct equality comparison. Used for equivalence rules where source and target should
     * produce identical results.
     */
    EQUAL
}
