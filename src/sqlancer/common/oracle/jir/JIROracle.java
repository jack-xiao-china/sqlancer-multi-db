package sqlancer.common.oracle.jir;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import sqlancer.ComparatorHelper;
import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.Reproducer;
import sqlancer.SQLGlobalState;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.schema.AbstractTable;

/**
 * Generic JIR (Join Implication Reasoning) Oracle. Detects JOIN optimizer bugs by comparing query results across
 * different JOIN types using semantic implication rules.
 *
 * <p>
 * Based on: "Detecting Join Bugs in Database Engines via Join Implication Reasoning" (SIGMOD 2026).
 *
 * @param <G>
 *            the DBMS-specific global state type
 */
public class JIROracle<G extends SQLGlobalState<?, ?>> implements TestOracle<G> {

    private final G state;
    private final JIRTransformer<G> transformer;
    private final JIRRule[] availableRules;
    private String lastQueryString;
    private Reproducer<G> reproducer;

    public JIROracle(G state, JIRTransformer<G> transformer, JIRRule[] availableRules) {
        this.state = state;
        this.transformer = transformer;
        this.availableRules = availableRules;
    }

    private class JIRReproducer implements Reproducer<G> {
        private final String sourceQuery;
        private final String combinedTargetQuery;
        private final ExpectedErrors errors;

        JIRReproducer(String sourceQuery, String combinedTargetQuery, ExpectedErrors errors) {
            this.sourceQuery = sourceQuery;
            this.combinedTargetQuery = combinedTargetQuery;
            this.errors = errors;
        }

        @Override
        public boolean bugStillTriggers(G globalState) {
            try {
                List<String> sourceResult = ComparatorHelper.getResultSetFirstColumnAsString(
                        sourceQuery, errors, globalState);
                List<String> targetResult = ComparatorHelper.getResultSetFirstColumnAsString(
                        combinedTargetQuery, errors, globalState);
                ComparatorHelper.assumeResultSetsAreEqualMultiset(sourceResult, targetResult,
                        sourceQuery, List.of(combinedTargetQuery), globalState,
                        ComparatorHelper::canonicalizeResultValue);
            } catch (AssertionError e) {
                return true;
            } catch (SQLException | IgnoreMeException e) {
                // Query failed to execute - not a reproducible bug
            }
            return false;
        }
    }

    @Override
    public void check() throws Exception {
        lastQueryString = null;
        reproducer = null;

        // 1. Pick at least 2 random non-empty tables (required for JOIN)
        List<? extends AbstractTable<?, ?, ?>> allTables = state.getSchema().getDatabaseTables();
        if (allTables.size() < 2) {
            throw new IgnoreMeException();
        }
        List<? extends AbstractTable<?, ?, ?>> selectedTables = Randomly.nonEmptySubsetLeast(allTables, 2);

        // 2. Initialize transformer with selected tables
        transformer.initialize(state, selectedTables);

        // 3. Pick a random rule
        JIRRule rule = Randomly.fromOptions(availableRules);

        // 4. Generate source + target queries
        JIRQuerySet querySet = transformer.generateQuerySet(rule);
        if (querySet == null) {
            throw new IgnoreMeException();
        }

        ExpectedErrors errors = transformer.getExpectedErrors();
        String sourceQuery = querySet.getSourceQuery();

        // P1: Add ORDER BY 1 with low probability to exercise different optimizer paths
        if (Randomly.getBooleanWithRatherLowProbability()) {
            sourceQuery = sourceQuery + " ORDER BY 1";
        }

        lastQueryString = sourceQuery;

        if (state.getOptions().logEachSelect()) {
            state.getLogger().writeCurrent(sourceQuery);
        }

        // 5. Execute source query
        List<String> sourceResult;
        try {
            sourceResult = ComparatorHelper.getResultSetFirstColumnAsString(sourceQuery, errors, state);
        } catch (IgnoreMeException e) {
            throw e;
        } catch (AssertionError e) {
            throw new IgnoreMeException();
        }

        // 6. Execute target queries and combine results
        List<String> targetResult;
        List<String> targetQueries = querySet.getTargetQueries();
        String combinedTargetQuery;

        if (querySet.getResultType() == JIRResultType.EQUAL) {
            // Single target query
            if (targetQueries.size() != 1) {
                throw new IgnoreMeException();
            }
            String targetQuery = targetQueries.get(0);

            // P1: Mirror ORDER BY on target for EQUAL comparison
            if (sourceQuery.endsWith("ORDER BY 1")) {
                targetQuery = targetQuery + " ORDER BY 1";
            }

            combinedTargetQuery = targetQuery;
            if (state.getOptions().logEachSelect()) {
                state.getLogger().writeCurrent(targetQuery);
            }
            try {
                targetResult = ComparatorHelper.getResultSetFirstColumnAsString(targetQuery, errors, state);
            } catch (IgnoreMeException e) {
                throw e;
            } catch (AssertionError e) {
                throw new IgnoreMeException();
            }
        } else {
            // UNION_ALL: combine via UNION ALL SQL
            StringBuilder unionBuilder = new StringBuilder();
            for (int i = 0; i < targetQueries.size(); i++) {
                if (i > 0) {
                    unionBuilder.append(" UNION ALL ");
                }
                unionBuilder.append("(").append(targetQueries.get(i)).append(")");
            }
            combinedTargetQuery = unionBuilder.toString();
            if (state.getOptions().logEachSelect()) {
                state.getLogger().writeCurrent(combinedTargetQuery);
            }
            try {
                targetResult = ComparatorHelper.getResultSetFirstColumnAsString(combinedTargetQuery, errors, state);
            } catch (IgnoreMeException e) {
                throw e;
            } catch (AssertionError e) {
                throw new IgnoreMeException();
            }
        }

        // 7. Compare results (multiset/bag comparison, with canonicalization)
        try {
            ComparatorHelper.assumeResultSetsAreEqualMultiset(sourceResult, targetResult, sourceQuery,
                    new ArrayList<>(targetQueries), state, ComparatorHelper::canonicalizeResultValue);
        } catch (AssertionError e) {
            // P2: Create reproducer for verification
            reproducer = new JIRReproducer(sourceQuery, combinedTargetQuery, errors);
            throw e;
        }
    }

    @Override
    public Reproducer<G> getLastReproducer() {
        return reproducer;
    }

    @Override
    public String getLastQueryString() {
        return lastQueryString;
    }
}