package sqlancer.postgres.oracle.ext.eet;

import sqlancer.Reproducer;
import sqlancer.common.oracle.TestOracleUtils;
import sqlancer.common.oracle.eet.EETOracleBase;
import sqlancer.common.oracle.eet.EETQueryExecutor;
import sqlancer.common.schema.AbstractTables;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresVisitor;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.gen.PostgresExpressionGenerator;

public final class PostgresEETOracle
        extends EETOracleBase<PostgresGlobalState, PostgresExpression, AbstractTables<PostgresTable, PostgresColumn>, PostgresColumn> {

    public PostgresEETOracle(PostgresGlobalState state) {
        super(state, null);
    }

    public PostgresEETOracle(PostgresGlobalState state, EETQueryExecutor executor) {
        super(state, executor);
    }

    @Override
    protected AbstractTables<PostgresTable, PostgresColumn> getRandomTables() {
        return TestOracleUtils.getRandomTableNonEmptyTables(state.getSchema());
    }

    @Override
    protected Object createGenerator(AbstractTables<PostgresTable, PostgresColumn> tables) {
        return new PostgresExpressionGenerator(state).setTablesAndColumns(tables);
    }

    @Override
    protected PostgresExpression generateRootQuery(PostgresGlobalState state, Object gen,
            AbstractTables<PostgresTable, PostgresColumn> tables) {
        PostgresExpressionGenerator expressionGen = (PostgresExpressionGenerator) gen;
        return PostgresEETQueryGenerator.generateEETQueryRandomShape(state, expressionGen, tables);
    }

    @Override
    protected Object createTransformer(Object gen, AbstractTables<PostgresTable, PostgresColumn> tables) {
        PostgresExpressionGenerator expressionGen = (PostgresExpressionGenerator) gen;
        return new PostgresEETTransformer(expressionGen, tables);
    }

    @Override
    protected Object createQueryTransformer(Object transformer) {
        return new PostgresEETQueryTransformer((PostgresEETTransformer) transformer);
    }

    @Override
    protected PostgresExpression transformRoot(Object queryTransformer, PostgresExpression root) {
        return ((PostgresEETQueryTransformer) queryTransformer).eqTransformRoot(root);
    }

    @Override
    protected String asString(PostgresExpression expr) {
        return PostgresVisitor.asString(expr);
    }

    @Override
    protected Reproducer<PostgresGlobalState> createReproducer(PostgresExpression original,
            PostgresExpression transformed, Object gen, AbstractTables<PostgresTable, PostgresColumn> tables,
            long reductionSeed) {
        PostgresExpressionGenerator expressionGen = (PostgresExpressionGenerator) gen;
        return new PostgresEETReproducer(original, transformed, expressionGen, tables, reductionSeed, executor);
    }

    @Override
    protected EETQueryExecutor createDefaultExecutor() {
        return new PostgresEETDefaultQueryExecutor(state);
    }
}