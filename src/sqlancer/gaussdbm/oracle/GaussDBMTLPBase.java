package sqlancer.gaussdbm.oracle;

import sqlancer.common.oracle.NondeterminismGuard;
import sqlancer.IgnoreMeException;
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
        List<GaussDBTable> tables = new java.util.ArrayList<>(targetTables.getTables());
        List<GaussDBJoin> joinStatements = GaussDBJoin.getRandomJoinClauses(tables, state);
        // Build the FROM list from the post-removal tables: getRandomJoinClauses removes the
        // picked join tables from the list, and listing them in FROM as well duplicates the
        // table name ("Table name ... is specified more than once").
        List<GaussDBExpression> tableList = tables.stream().map(GaussDBTableReference::create)
                .collect(Collectors.toList());
        select.setJoinList(joinStatements.stream().map(j -> (GaussDBExpression) j).collect(Collectors.toList()));
        select.setFromList(tableList);
        select.setWhereClause(null);
        select.setOrderByClauses(java.util.Collections.emptyList());
        if (NondeterminismGuard.containsNondeterministicCall(select.asString())) {
            // Nondeterministic projections (NOW()/CURTIME()/...) differ between the original and
            // the partitioned executions; the TLP comparison is meaningless for such rounds.
            throw new IgnoreMeException();
        }
    }

    List<GaussDBExpression> generateFetchColumns() {
        return Arrays.asList(GaussDBColumnReference.create(targetTables.getColumns().get(0), null));
    }

    @Override
    protected ExpressionGenerator<GaussDBExpression> getGen() {
        return gen;
    }
}
