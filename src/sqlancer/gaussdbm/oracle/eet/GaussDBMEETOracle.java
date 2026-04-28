package sqlancer.gaussdbm.oracle.eet;

import java.sql.SQLException;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Reproducer;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.oracle.TestOracleUtils;
import sqlancer.common.schema.AbstractTables;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

public class GaussDBMEETOracle implements TestOracle<GaussDBMGlobalState> {

    public static final int MAX_PROCESS_ROW_NUM = 10_000;

    private final GaussDBMGlobalState state;
    private final EETQueryExecutor executor;
    private String lastQueryString;
    private Reproducer<GaussDBMGlobalState> lastReproducer;

    public GaussDBMEETOracle(GaussDBMGlobalState state) {
        this(state, null);
    }

    public GaussDBMEETOracle(GaussDBMGlobalState state, EETQueryExecutor executor) {
        this.state = state;
        this.executor = executor != null ? executor : new GaussDBMEETDefaultQueryExecutor(state);
    }

    @Override
    public void check() throws SQLException {
        lastReproducer = null;
        AbstractTables<GaussDBTable, GaussDBColumn> targetTables = TestOracleUtils
                .getRandomTableNonEmptyTables(state.getSchema());
        GaussDBMExpressionGenerator gen = new GaussDBMExpressionGenerator(state).setTablesAndColumns(targetTables);
        GaussDBExpression root = GaussDBMEETQueryGenerator.generateEETQueryRandomShape(state, gen, targetTables);

        GaussDBMEETQueryTransformer qtf = new GaussDBMEETQueryTransformer(gen);
        long reductionSeed = state.getRandomly().getSeed();
        GaussDBExpression transformed = qtf.eqTransformRoot(root);

        String originalQuery = GaussDBToStringVisitor.asString(root);
        String transformedQuery = GaussDBToStringVisitor.asString(transformed);
        lastQueryString = originalQuery + "\n-- EET transformed:\n" + transformedQuery;

        if (state.getOptions().logEachSelect()) {
            state.getLogger().writeCurrent(lastQueryString);
        }

        List<List<String>> originalResult;
        List<List<String>> transformedResult;
        try {
            originalResult = executor.executeQuery(originalQuery);
            transformedResult = executor.executeQuery(transformedQuery);
        } catch (IgnoreMeException e) {
            throw e;
        } catch (SQLException e) {
            throw new IgnoreMeException();
        }

        if (originalResult.size() > MAX_PROCESS_ROW_NUM || transformedResult.size() > MAX_PROCESS_ROW_NUM) {
            throw new IgnoreMeException();
        }

        if (EETMultisetComparator.compareResultMultisets(originalResult, transformedResult)) {
            return;
        }

        List<List<String>> orig2;
        List<List<String>> trans2;
        try {
            orig2 = executor.executeQuery(originalQuery);
            trans2 = executor.executeQuery(transformedQuery);
        } catch (SQLException e) {
            throw new IgnoreMeException();
        }
        if (!EETMultisetComparator.compareResultMultisets(orig2, trans2)) {
            lastReproducer = new GaussDBMEETReproducer(root, transformed, gen, targetTables, reductionSeed, executor);
            throw new AssertionError(String.format("EET logic bug: multiset mismatch\n%s\n", lastQueryString));
        }
    }

    @Override
    public String getLastQueryString() {
        return lastQueryString;
    }

    @Override
    public Reproducer<GaussDBMGlobalState> getLastReproducer() {
        return lastReproducer;
    }
}
