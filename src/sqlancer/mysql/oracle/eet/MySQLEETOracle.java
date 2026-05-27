package sqlancer.mysql.oracle.eet;

import sqlancer.Reproducer;
import sqlancer.common.oracle.TestOracleUtils;
import sqlancer.common.oracle.eet.EETOracleBase;
import sqlancer.common.oracle.eet.EETQueryExecutor;
import sqlancer.common.schema.AbstractTables;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.MySQLVisitor;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

public class MySQLEETOracle
        extends EETOracleBase<MySQLGlobalState, MySQLExpression, AbstractTables<MySQLTable, MySQLColumn>, MySQLColumn> {

    public MySQLEETOracle(MySQLGlobalState state) {
        super(state, null);
    }

    public MySQLEETOracle(MySQLGlobalState state, EETQueryExecutor executor) {
        super(state, executor);
    }

    @Override
    protected AbstractTables<MySQLTable, MySQLColumn> getRandomTables() {
        return TestOracleUtils.getRandomTableNonEmptyTables(state.getSchema());
    }

    @Override
    protected Object createGenerator(AbstractTables<MySQLTable, MySQLColumn> tables) {
        return new MySQLExpressionGenerator(state).setTablesAndColumns(tables);
    }

    @Override
    protected MySQLExpression generateRootQuery(MySQLGlobalState state, Object gen,
            AbstractTables<MySQLTable, MySQLColumn> tables) {
        MySQLExpressionGenerator expressionGen = (MySQLExpressionGenerator) gen;
        return MySQLEETQueryGenerator.generateEETQueryRandomShape(state, expressionGen, tables);
    }

    @Override
    protected Object createTransformer(Object gen, AbstractTables<MySQLTable, MySQLColumn> tables) {
        MySQLExpressionGenerator expressionGen = (MySQLExpressionGenerator) gen;
        return new MySQLEETTransformer(expressionGen, tables);
    }

    @Override
    protected Object createQueryTransformer(Object transformer) {
        return new MySQLEETQueryTransformer((MySQLEETTransformer) transformer);
    }

    @Override
    protected MySQLExpression transformRoot(Object queryTransformer, MySQLExpression root) {
        return ((MySQLEETQueryTransformer) queryTransformer).eqTransformRoot(root);
    }

    @Override
    protected String asString(MySQLExpression expr) {
        return MySQLVisitor.asString(expr);
    }

    @Override
    protected Reproducer<MySQLGlobalState> createReproducer(MySQLExpression original, MySQLExpression transformed,
            Object gen, AbstractTables<MySQLTable, MySQLColumn> tables, long reductionSeed) {
        MySQLExpressionGenerator expressionGen = (MySQLExpressionGenerator) gen;
        return new MySQLEETReproducer(original, transformed, expressionGen, tables, reductionSeed, executor);
    }

    @Override
    protected EETQueryExecutor createDefaultExecutor() {
        return new EETDefaultQueryExecutor(state);
    }
}