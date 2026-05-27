package sqlancer.common.oracle.eet;

import java.sql.SQLException;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Reproducer;
import sqlancer.SQLGlobalState;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.schema.AbstractTableColumn;
import sqlancer.common.schema.AbstractTables;

/**
 * Shared EET Oracle check() flow — identical across all dialects.
 * Dialect-specific logic is delegated to abstract methods.
 */
public abstract class EETOracleBase<S extends SQLGlobalState<?, ?>, E, T extends AbstractTables<?, C>, C extends AbstractTableColumn<?, ?>>
        implements TestOracle<S> {

    public static final int MAX_PROCESS_ROW_NUM = 10_000;

    protected final S state;
    protected final EETQueryExecutor executor;
    protected String lastQueryString;
    protected Reproducer<S> lastReproducer;
    protected final EETBackTransformTracker backTransformTracker = new EETBackTransformTracker();
    protected final EETImpedanceTracker impedanceTracker = new EETImpedanceTracker();

    protected EETOracleBase(S state, EETQueryExecutor executor) {
        this.state = state;
        this.executor = executor != null ? executor : createDefaultExecutor();
    }

    // --- Abstract methods for dialect-specific logic ---

    /** Get random non-empty tables from the schema. */
    protected abstract T getRandomTables();

    /** Create an expression generator bound to the given tables. */
    protected abstract Object createGenerator(T tables);

    /** Generate the root query expression. */
    protected abstract E generateRootQuery(S state, Object gen, T tables);

    /** Create a transformer for the given generator and tables. */
    protected abstract Object createTransformer(Object gen, T tables);

    /** Create a query transformer wrapping the expression transformer. */
    protected abstract Object createQueryTransformer(Object transformer);

    /** Transform the root expression via the query transformer. */
    protected abstract E transformRoot(Object queryTransformer, E root);

    /** Render an expression to its SQL string. */
    protected abstract String asString(E expr);

    /** Create a full reproducer with AST context for component reduction. */
    protected abstract Reproducer<S> createReproducer(E original, E transformed, Object gen, T tables,
            long reductionSeed);

    /** Create the default query executor for this dialect. */
    protected abstract EETQueryExecutor createDefaultExecutor();

    // --- Concrete check() flow (shared across all dialects) ---

    @Override
    public void check() throws SQLException {
        lastReproducer = null;
        T targetTables = getRandomTables();
        Object gen = createGenerator(targetTables);
        E root = generateRootQuery(state, gen, targetTables);

        Object transformer = createTransformer(gen, targetTables);

        // Set impedance tracker on transformer for expression type blacklisting
        if (transformer instanceof EETTransformerBase) {
            ((EETTransformerBase<?>) transformer).setImpedanceTracker(impedanceTracker);
        }

        Object qtf = createQueryTransformer(transformer);
        long reductionSeed = state.getRandomly().getSeed();
        E transformed = transformRoot(qtf, root);

        String originalQuery = asString(root);
        String transformedQuery = asString(transformed);
        lastQueryString = originalQuery + "\n-- EET transformed:\n" + transformedQuery;

        if (state.getOptions().logEachSelect()) {
            state.getLogger().writeCurrent(lastQueryString);
        }

        List<List<String>> originalResult;
        List<List<String>> transformedResult;
        try {
            originalResult = executor.executeQuery(originalQuery);
            impedanceTracker.recordSuccess("SELECT");
        } catch (IgnoreMeException e) {
            throw e;
        } catch (SQLException e) {
            // Crash detection: distinguish internal errors from syntax errors
            String sqlState = e.getSQLState();
            int errorCode = e.getErrorCode();
            if (EETCrashTracker.isCrashError(sqlState, errorCode)) {
                EETCrashTracker.getInstance().logCrash(state.getDatabaseName(), sqlState, errorCode,
                        originalQuery, e.getMessage());
                impedanceTracker.recordFailure("SELECT_CRASH");
            }
            impedanceTracker.recordFailure("SELECT");
            throw new IgnoreMeException();
        }

        try {
            transformedResult = executor.executeQuery(transformedQuery);
            impedanceTracker.recordSuccess("SELECT");
        } catch (IgnoreMeException e) {
            throw e;
        } catch (SQLException e) {
            String sqlState = e.getSQLState();
            int errorCode = e.getErrorCode();
            if (EETCrashTracker.isCrashError(sqlState, errorCode)) {
                EETCrashTracker.getInstance().logCrash(state.getDatabaseName(), sqlState, errorCode,
                        transformedQuery, e.getMessage());
                impedanceTracker.recordFailure("SELECT_CRASH");
            }
            impedanceTracker.recordFailure("SELECT");
            throw new IgnoreMeException();
        }

        if (originalResult.size() > MAX_PROCESS_ROW_NUM || transformedResult.size() > MAX_PROCESS_ROW_NUM) {
            throw new IgnoreMeException();
        }

        if (EETMultisetComparator.compareResultMultisets(originalResult, transformedResult)) {
            return;
        }

        // Double-execution confirmation to reduce flakiness
        List<List<String>> orig2;
        List<List<String>> trans2;
        try {
            orig2 = executor.executeQuery(originalQuery);
            trans2 = executor.executeQuery(transformedQuery);
        } catch (SQLException e) {
            throw new IgnoreMeException();
        }
        if (!EETMultisetComparator.compareResultMultisets(orig2, trans2)) {
            lastReproducer = createReproducer(root, transformed, gen, targetTables, reductionSeed);
            throw new AssertionError(String.format("EET logic bug: multiset mismatch\n%s\n", lastQueryString));
        }
    }

    @Override
    public String getLastQueryString() {
        return lastQueryString;
    }

    @Override
    public Reproducer<S> getLastReproducer() {
        return lastReproducer;
    }
}