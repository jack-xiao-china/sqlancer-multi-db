package sqlancer.gaussdbm.oracle;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.common.gen.ExpressionGenerator;
import sqlancer.common.oracle.TernaryLogicPartitioningOracleBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.gaussdbm.GaussDBMErrors;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTables;
import sqlancer.gaussdbm.ast.GaussDBColumnReference;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBJoin;
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

/**
 * GaussDB-M (M-Compatibility) TLP base: builds SELECT with FROM/JOINs, no ORDER BY (avoids UNION ordering quirks).
 */
public abstract class GaussDBMTLPBase extends TernaryLogicPartitioningOracleBase<GaussDBExpression, GaussDBMGlobalState>
        implements TestOracle<GaussDBMGlobalState> {

    GaussDBMSchema schema;
    GaussDBTables targetTables;
    GaussDBMExpressionGenerator gen;
    GaussDBSelect select;

    public GaussDBMTLPBase(GaussDBMGlobalState state) {
        super(state);
        GaussDBMErrors.addExpressionErrors(errors);
    }

    @Override
    public void check() throws SQLException {
        schema = state.getSchema();
        targetTables = schema.getRandomTableNonEmptyTables();
        gen = new GaussDBMExpressionGenerator(state).setColumns(targetTables.getColumns());
        initializeTernaryPredicateVariants();
        select = new GaussDBSelect();
        select.setFetchColumns(generateFetchColumns());
        List<GaussDBTable> tables = targetTables.getTables();
        List<GaussDBExpression> tableList = tables.stream().map(GaussDBTableReference::create)
                .collect(Collectors.toList());
        List<GaussDBJoin> joinStatements = GaussDBJoin.getRandomJoinClauses(new java.util.ArrayList<>(tables), state);
        select.setJoinList(joinStatements.stream().map(j -> (GaussDBExpression) j).collect(Collectors.toList()));
        select.setFromList(tableList);
        select.setWhereClause(null);
        select.setOrderByClauses(java.util.Collections.emptyList());
    }

    List<GaussDBExpression> generateFetchColumns() {
        return Arrays.asList(GaussDBColumnReference.create(targetTables.getColumns().get(0), null));
    }

    @Override
    protected ExpressionGenerator<GaussDBExpression> getGen() {
        return gen;
    }
}
