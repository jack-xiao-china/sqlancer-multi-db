package sqlancer.gaussdbm.oracle.eet;

import sqlancer.Reproducer;
import sqlancer.common.oracle.TestOracleUtils;
import sqlancer.common.oracle.eet.EETOracleBase;
import sqlancer.common.oracle.eet.EETQueryExecutor;
import sqlancer.common.schema.AbstractTables;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

public class GaussDBMEETOracle
        extends EETOracleBase<GaussDBMGlobalState, GaussDBExpression, AbstractTables<GaussDBTable, GaussDBColumn>, GaussDBColumn> {

    public GaussDBMEETOracle(GaussDBMGlobalState state) {
        this(state, null);
    }

    public GaussDBMEETOracle(GaussDBMGlobalState state, EETQueryExecutor executor) {
        super(state, executor);
    }

    @Override
    protected AbstractTables<GaussDBTable, GaussDBColumn> getRandomTables() {
        return TestOracleUtils.getRandomTableNonEmptyTables(state.getSchema());
    }

    @Override
    protected Object createGenerator(AbstractTables<GaussDBTable, GaussDBColumn> tables) {
        return new GaussDBMExpressionGenerator(state).setTablesAndColumns(tables);
    }

    @Override
    protected GaussDBExpression generateRootQuery(GaussDBMGlobalState state2, Object gen,
            AbstractTables<GaussDBTable, GaussDBColumn> tables) {
        GaussDBMExpressionGenerator expressionGen = (GaussDBMExpressionGenerator) gen;
        return GaussDBMEETQueryGenerator.generateEETQueryRandomShape(state2, expressionGen, tables);
    }

    @Override
    protected Object createTransformer(Object gen, AbstractTables<GaussDBTable, GaussDBColumn> tables) {
        GaussDBMExpressionGenerator expressionGen = (GaussDBMExpressionGenerator) gen;
        return new GaussDBMEETTransformer(expressionGen, tables);
    }

    @Override
    protected Object createQueryTransformer(Object transformer) {
        return new GaussDBMEETQueryTransformer((GaussDBMEETTransformer) transformer);
    }

    @Override
    protected GaussDBExpression transformRoot(Object queryTransformer, GaussDBExpression root) {
        return ((GaussDBMEETQueryTransformer) queryTransformer).eqTransformRoot(root);
    }

    @Override
    protected String asString(GaussDBExpression expr) {
        return GaussDBToStringVisitor.asString(expr);
    }

    @Override
    protected Reproducer<GaussDBMGlobalState> createReproducer(GaussDBExpression original,
            GaussDBExpression transformed, Object gen, AbstractTables<GaussDBTable, GaussDBColumn> tables,
            long reductionSeed) {
        GaussDBMExpressionGenerator expressionGen = (GaussDBMExpressionGenerator) gen;
        return new GaussDBMEETReproducer(original, transformed, expressionGen, tables, reductionSeed, executor);
    }

    @Override
    protected EETQueryExecutor createDefaultExecutor() {
        return new GaussDBMEETDefaultQueryExecutor(state);
    }
}