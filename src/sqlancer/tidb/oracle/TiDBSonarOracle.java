package sqlancer.tidb.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.common.oracle.NoRECBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.tidb.TiDBErrors;
import sqlancer.tidb.TiDBExpressionGenerator;
import sqlancer.tidb.TiDBProvider;
import sqlancer.tidb.TiDBSchema;
import sqlancer.tidb.TiDBSchema.TiDBColumn;
import sqlancer.tidb.TiDBSchema.TiDBTable;
import sqlancer.tidb.TiDBSchema.TiDBTables;
import sqlancer.tidb.ast.TiDBBinaryComparisonOperation;
import sqlancer.tidb.ast.TiDBColumnReference;
import sqlancer.tidb.ast.TiDBExpression;
import sqlancer.tidb.ast.TiDBJoin;
import sqlancer.tidb.ast.TiDBManuelPredicate;
import sqlancer.tidb.ast.TiDBPostfixText;
import sqlancer.tidb.ast.TiDBSelect;
import sqlancer.tidb.ast.TiDBTableReference;
import sqlancer.tidb.visitor.TiDBVisitor;

/**
 * TiDB SONAR Oracle implementation.
 *
 * SONAR (Select Optimization N-gram Analysis Runtime) is a testing technique that compares the results of optimized
 * queries (with WHERE clause filtering) against unoptimized queries (using flag-based filtering in outer query). This
 * helps detect optimization bugs where the database optimizer incorrectly transforms queries.
 *
 * The oracle generates: 1. Optimized query: SELECT ... FROM ... WHERE condition 2. Unoptimized query: SELECT ... FROM
 * (SELECT ..., condition IS TRUE AS flag FROM ...) WHERE flag=1
 *
 * If the result sets differ, a bug is detected.
 */
public class TiDBSonarOracle extends NoRECBase<TiDBProvider.TiDBGlobalState>
        implements TestOracle<TiDBProvider.TiDBGlobalState> {

    TiDBSchema s;
    TiDBTables targetTables;

    public TiDBSonarOracle(TiDBProvider.TiDBGlobalState globalState) {
        super(globalState);
        this.s = globalState.getSchema();
        TiDBErrors.addExpressionErrors(errors);
    }

    List<TiDBExpression> generateFetchColumns(List<TiDBColumn> columns) {
        List<TiDBExpression> list = new ArrayList<>();
        TiDBColumnReference tiDBColumnName = new TiDBColumnReference(Randomly.fromList(columns));
        String alias = "_" + tiDBColumnName.getColumn().getName();
        list.add(new TiDBPostfixText(tiDBColumnName, alias));
        return list;
    }

    @Override
    public void check() throws SQLException {
        boolean useFetchColumnsExp = Randomly.getBoolean();
        TiDBPostfixText asText = null;
        TiDBExpression whereExp = null;
        TiDBTables randomTables = s.getRandomTableNonEmptyTables();
        List<TiDBColumn> columns = randomTables.getColumns();
        TiDBExpressionGenerator gen = new TiDBExpressionGenerator(this.state).setColumns(columns);
        TiDBSelect select = new TiDBSelect();

        if (useFetchColumnsExp) {
            TiDBExpression exp = gen.generateFetchColumnExpression(columns.get(0));
            asText = new TiDBPostfixText(exp, " AS f1");
            List<TiDBExpression> fetchColumns = new ArrayList<>();
            fetchColumns.add(asText);
            select.setFetchColumns(fetchColumns);
        } else {
            select.setFetchColumns(generateFetchColumns(columns));
        }

        List<TiDBTable> tables = randomTables.getTables();
        List<TiDBExpression> tableList = tables.stream().map(t -> new TiDBTableReference(t))
                .collect(Collectors.toList());
        select.setFromList(tableList);
        List<TiDBJoin> joins = TiDBJoin.getJoins(new ArrayList<>(tableList), state);
        select.setJoinClauses(joins);

        if (useFetchColumnsExp) {
            whereExp = gen.generateWhereColumnExpression(asText);
            optimizedQueryString = getOptimizedQuery(select, asText, whereExp);
        } else {
            select.setWhereClause(gen.generateExpression());
            optimizedQueryString = getOptimizedQuery(select);
        }

        unoptimizedQueryString = getUnoptimizedQuery(select, useFetchColumnsExp, asText, whereExp);

        List<String> optimizedResultSet = ComparatorHelper.getResultSetFirstColumnAsString(optimizedQueryString, errors,
                state);
        List<String> unoptimizedResultSet = ComparatorHelper.getResultSetFirstColumnAsString(unoptimizedQueryString,
                errors, state);

        ComparatorHelper.assumeResultSetsAreEqual(optimizedResultSet, unoptimizedResultSet, optimizedQueryString,
                Arrays.asList(unoptimizedQueryString), state);
    }

    private String getUnoptimizedQuery(TiDBSelect select, Boolean useFetchColumnsExp, TiDBPostfixText asText,
            TiDBExpression whereExp) throws SQLException {

        if (useFetchColumnsExp && whereExp != null) {
            if (whereExp instanceof TiDBManuelPredicate) {
                String flagName = "("
                        + TiDBVisitor.asString(((TiDBPostfixText) select.getFetchColumns().get(0)).getExpr()) + ")"
                        + " IS TRUE AS flag";
                select.getFetchColumns().add(new TiDBManuelPredicate(flagName));
            } else {
                TiDBExpression right = null;
                if (whereExp instanceof TiDBBinaryComparisonOperation) {
                    right = ((TiDBBinaryComparisonOperation) whereExp).getRight();
                    TiDBBinaryComparisonOperation.TiDBComparisonOperator op = ((TiDBBinaryComparisonOperation) whereExp)
                            .getOp();
                    TiDBBinaryComparisonOperation fetchColumn = new TiDBBinaryComparisonOperation(
                            ((TiDBPostfixText) select.getFetchColumns().get(0)).getExpr(), right, op);
                    String flagName = "(" + TiDBVisitor.asString(fetchColumn) + ")" + " IS TRUE AS flag";
                    select.getFetchColumns().add(new TiDBManuelPredicate(flagName));
                }
            }

            TiDBSelect outerSelect = new TiDBSelect();
            outerSelect.setFetchColumns(Arrays.asList(new TiDBManuelPredicate(asText.getText())));
            outerSelect.setFromList(Arrays.asList(select));
            outerSelect.setWhereClause(new TiDBManuelPredicate("flag=1"));
            unoptimizedQueryString = TiDBVisitor.asString(outerSelect);
            return unoptimizedQueryString;

        } else {
            TiDBSelect outerSelect = new TiDBSelect();
            outerSelect.setFetchColumns(Arrays
                    .asList(new TiDBManuelPredicate(((TiDBPostfixText) select.getFetchColumns().get(0)).getText())));

            String flagName = "(" + TiDBVisitor.asString(select.getWhereClause()) + ")" + " IS TRUE AS flag";
            select.getFetchColumns().add(new TiDBManuelPredicate(flagName));

            select.setWhereClause(null);
            outerSelect.setFromList(Arrays.asList(select));
            outerSelect.setWhereClause(new TiDBManuelPredicate("flag=1"));
            unoptimizedQueryString = TiDBVisitor.asString(outerSelect);
            return unoptimizedQueryString;
        }
    }

    private String getOptimizedQuery(TiDBSelect select, TiDBPostfixText asText, TiDBExpression whereExp)
            throws SQLException {
        TiDBSelect outerSelect = new TiDBSelect();
        outerSelect.setFetchColumns(Arrays.asList(new TiDBManuelPredicate(asText.getText())));
        outerSelect.setFromList(Arrays.asList(select));
        outerSelect.setWhereClause(whereExp);
        optimizedQueryString = TiDBVisitor.asString(outerSelect);
        return optimizedQueryString;
    }

    private String getOptimizedQuery(TiDBSelect select) throws SQLException {
        optimizedQueryString = TiDBVisitor.asString(select);
        return optimizedQueryString;
    }
}