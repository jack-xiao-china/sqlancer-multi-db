package sqlancer.mysql.oracle;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.oracle.NondeterminismGuard;
import sqlancer.common.gen.ExpressionGenerator;
import sqlancer.common.oracle.TernaryLogicPartitioningOracleBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.mysql.MySQLErrors;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLSchema;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.MySQLSchema.MySQLTables;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLJoin;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.gen.MySQLExpressionGenerator;
import sqlancer.mysql.gen.MySQLHintGenerator;

/**
 * MySQL-compatible TLP base: builds a SELECT with FROM/JOINs and optional hints,
 * used by HAVING (and potentially other) TLP oracles. Syntax aligned with MySQL.
 */
public abstract class MySQLTLPBase extends TernaryLogicPartitioningOracleBase<MySQLExpression, MySQLGlobalState>
        implements TestOracle<MySQLGlobalState> {

    MySQLSchema schema;
    MySQLTables targetTables;
    MySQLExpressionGenerator gen;
    MySQLSelect select;

    public MySQLTLPBase(MySQLGlobalState state) {
        super(state);
        MySQLErrors.addExpressionErrors(errors);
    }

    @Override
    public void check() throws SQLException {
        schema = state.getSchema();
        targetTables = schema.getRandomTableNonEmptyTables();
        gen = new MySQLExpressionGenerator(state).setColumns(targetTables.getColumns());
        initializeTernaryPredicateVariants();
        select = new MySQLSelect();
        select.setFetchColumns(generateFetchColumns());
        List<MySQLTable> tables = targetTables.getTables();
        if (Randomly.getBoolean()) {
            MySQLHintGenerator.generateHints(select, tables);
        }

        List<MySQLTable> tablesForJoins = new java.util.ArrayList<>(tables);
        List<MySQLJoin> joinStatements = MySQLJoin.getRandomJoinClauses(tablesForJoins, state);
        // Build the FROM list from the post-removal tables: getRandomJoinClauses removes the
        // picked join tables from the list, and listing them in FROM as well duplicates the
        // table name ("Not unique table/alias").
        List<MySQLExpression> tableList = tablesForJoins.stream().map(MySQLTableReference::new)
                .collect(Collectors.toList());
        select.setJoinList(joinStatements.stream().map(j -> (MySQLExpression) j).collect(Collectors.toList()));
        select.setFromList(tableList);
        select.setWhereClause(null);
        if (NondeterminismGuard.containsNondeterministicCall(select.asString())) {
            // Nondeterministic projections (NOW()/CURTIME()/...) differ between the original and
            // the partitioned executions; the TLP comparison is meaningless for such rounds.
            throw new IgnoreMeException();
        }
        // 不添加 ORDER BY：HAVING/GROUP_BY/DISTINCT 等会生成 UNION 查询，MySQL 对含 ORDER BY 的 UNION 语法有特殊限制
        select.setOrderByClauses(java.util.Collections.emptyList());
    }

    List<MySQLExpression> generateFetchColumns() {
        return Arrays.asList(new MySQLColumnReference(targetTables.getColumns().get(0), null));
    }

    @Override
    protected ExpressionGenerator<MySQLExpression> getGen() {
        return gen;
    }
}
