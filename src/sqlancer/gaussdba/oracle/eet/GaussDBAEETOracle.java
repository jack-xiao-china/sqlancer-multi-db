package sqlancer.gaussdba.oracle.eet;

import sqlancer.Reproducer;
import sqlancer.common.oracle.TestOracleUtils;
import sqlancer.common.oracle.eet.EETOracleBase;
import sqlancer.common.oracle.eet.EETQueryExecutor;
import sqlancer.common.schema.AbstractTables;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;
import sqlancer.gaussdba.GaussDBAToStringVisitor;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.gen.GaussDBAExpressionGenerator;

public class GaussDBAEETOracle
        extends EETOracleBase<GaussDBAGlobalState, GaussDBAExpression, AbstractTables<GaussDBATable, GaussDBAColumn>, GaussDBAColumn> {

    public GaussDBAEETOracle(GaussDBAGlobalState state) {
        this(state, null);
    }

    public GaussDBAEETOracle(GaussDBAGlobalState state, EETQueryExecutor executor) {
        super(state, executor);
    }

    @Override
    protected AbstractTables<GaussDBATable, GaussDBAColumn> getRandomTables() {
        return TestOracleUtils.getRandomTableNonEmptyTables(state.getSchema());
    }

    @Override
    protected Object createGenerator(AbstractTables<GaussDBATable, GaussDBAColumn> tables) {
        return new GaussDBAExpressionGenerator(state).setTablesAndColumns(tables);
    }

    @Override
    protected GaussDBAExpression generateRootQuery(GaussDBAGlobalState state2, Object gen,
            AbstractTables<GaussDBATable, GaussDBAColumn> tables) {
        GaussDBAExpressionGenerator expressionGen = (GaussDBAExpressionGenerator) gen;
        return GaussDBAEETQueryGenerator.generateEETQueryRandomShape(state2, expressionGen, tables);
    }

    @Override
    protected Object createTransformer(Object gen, AbstractTables<GaussDBATable, GaussDBAColumn> tables) {
        GaussDBAExpressionGenerator expressionGen = (GaussDBAExpressionGenerator) gen;
        return new GaussDBAEETTransformer(expressionGen, tables);
    }

    @Override
    protected Object createQueryTransformer(Object transformer) {
        return new GaussDBAEETQueryTransformer((GaussDBAEETTransformer) transformer);
    }

    @Override
    protected GaussDBAExpression transformRoot(Object queryTransformer, GaussDBAExpression root) {
        return ((GaussDBAEETQueryTransformer) queryTransformer).eqTransformRoot(root);
    }

    @Override
    protected String asString(GaussDBAExpression expr) {
        return GaussDBAToStringVisitor.asString(expr);
    }

    @Override
    protected Reproducer<GaussDBAGlobalState> createReproducer(GaussDBAExpression original,
            GaussDBAExpression transformed, Object gen, AbstractTables<GaussDBATable, GaussDBAColumn> tables,
            long reductionSeed) {
        GaussDBAExpressionGenerator expressionGen = (GaussDBAExpressionGenerator) gen;
        return new GaussDBAEETReproducer(original, transformed, expressionGen, tables, reductionSeed, executor);
    }

    @Override
    protected EETQueryExecutor createDefaultExecutor() {
        return new GaussDBAEETDefaultQueryExecutor(state);
    }
}