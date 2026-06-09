package sqlancer.common.oracle.jir;

import java.util.List;

import sqlancer.SQLGlobalState;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.schema.AbstractTable;

/**
 * DBMS-specific transformer for JIR queries. Each DBMS implements this interface to handle its own AST types and SQL
 * syntax.
 *
 * @param <G>
 *            the DBMS-specific global state type
 */
public interface JIRTransformer<G extends SQLGlobalState<?, ?>> {

    /**
     * Initialize the transformer with tables and generators for this iteration.
     *
     * @param state
     *            the global state
     * @param tables
     *            non-empty tables to use (at least 2)
     */
    void initialize(G state, List<? extends AbstractTable<?, ?, ?>> tables);

    /**
     * Generate a query set (source + targets) for the given rule.
     *
     * @param rule
     *            the JIR rule to apply
     * @return the query set, or {@code null} if the rule cannot be applied (e.g., no suitable join generated)
     */
    JIRQuerySet generateQuerySet(JIRRule rule);

    /**
     * Get the expected errors that should be treated as {@link sqlancer.IgnoreMeException}.
     *
     * @return the expected errors
     */
    ExpectedErrors getExpectedErrors();
}
