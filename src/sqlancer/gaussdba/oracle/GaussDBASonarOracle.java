package sqlancer.gaussdba.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.common.oracle.NoRECBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATables;
import sqlancer.gaussdba.GaussDBAToStringVisitor;
import sqlancer.gaussdba.ast.GaussDBAColumnReference;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.ast.GaussDBAManuelPredicate;
import sqlancer.gaussdba.ast.GaussDBAPostfixText;
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.ast.GaussDBATableReference;
import sqlancer.gaussdba.gen.GaussDBAExpressionGenerator;

/**
 * GaussDB-A SONAR Oracle implementation.
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
public class GaussDBASonarOracle extends NoRECBase<GaussDBAGlobalState> implements TestOracle<GaussDBAGlobalState> {

    GaussDBASchema s;
    GaussDBATables targetTables;
    GaussDBAExpressionGenerator gen;

    public GaussDBASonarOracle(GaussDBAGlobalState globalState) {
        super(globalState);
        this.s = globalState.getSchema();
    }

    @Override
    public void check() throws SQLException {
        s = state.getSchema();
        targetTables = s.getRandomTableNonEmptyTables();
        List<GaussDBAColumn> columns = targetTables.getColumns();
        gen = new GaussDBAExpressionGenerator(state).setColumns(columns);

        GaussDBASelect select = new GaussDBASelect();
        List<GaussDBAExpression> fetchColumns = new ArrayList<>();
        GaussDBAColumnReference colRef = GaussDBAColumnReference.create(Randomly.fromList(columns), null);
        String alias = "_" + colRef.getColumn().getName();
        fetchColumns.add(new GaussDBAPostfixText(colRef, alias));
        select.setFetchColumns(fetchColumns);

        List<GaussDBATable> tables = targetTables.getTables();
        List<GaussDBAExpression> tableList = tables.stream().map(t -> GaussDBATableReference.create(t))
                .collect(Collectors.toList());
        select.setFromList(tableList);

        select.setWhereClause(gen.generateExpression(0));

        optimizedQueryString = GaussDBAToStringVisitor.asString(select);
        unoptimizedQueryString = getUnoptimizedQuery(select);

        List<String> optimizedResultSet = ComparatorHelper.getResultSetFirstColumnAsString(optimizedQueryString, errors,
                state);
        List<String> unoptimizedResultSet = ComparatorHelper.getResultSetFirstColumnAsString(unoptimizedQueryString,
                errors, state);

        ComparatorHelper.assumeResultSetsAreEqual(optimizedResultSet, unoptimizedResultSet, optimizedQueryString,
                Arrays.asList(unoptimizedQueryString), state);
    }

    private String getUnoptimizedQuery(GaussDBASelect select) throws SQLException {
        GaussDBASelect outerSelect = new GaussDBASelect();
        outerSelect.setFetchColumns(Arrays.asList(
                new GaussDBAManuelPredicate(((GaussDBAPostfixText) select.getFetchColumns().get(0)).getText())));

        String flagName = "(" + GaussDBAToStringVisitor.asString(select.getWhereClause()) + ") IS TRUE AS flag";
        select.getFetchColumns().add(new GaussDBAManuelPredicate(flagName));

        select.setWhereClause(null);
        outerSelect.setFromList(Arrays.asList(select));
        outerSelect.setWhereClause(new GaussDBAManuelPredicate("flag=1"));

        return GaussDBAToStringVisitor.asString(outerSelect);
    }
}