package sqlancer.gaussdbm.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.common.oracle.TestOracle;
import sqlancer.gaussdbm.GaussDBMBooleanNormalizer;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBColumnReference;
import sqlancer.gaussdbm.ast.GaussDBExpression;

/**
 * TLP GROUP BY oracle for GaussDB-M, aligned with {@code MySQLTLPGroupByOracle}.
 */
public class GaussDBMTLPGroupByOracle extends GaussDBMTLPBase implements TestOracle<GaussDBMGlobalState> {

    private String generatedQueryString;

    public GaussDBMTLPGroupByOracle(GaussDBMGlobalState state) {
        super(state);
    }

    @Override
    public void check() throws SQLException {
        super.check();
        select.setGroupByExpressions(select.getFetchColumns());
        select.setWhereClause(null);
        select.setOrderByClauses(List.of());
        String originalQueryString = GaussDBToStringVisitor.asString(select);
        generatedQueryString = originalQueryString;
        List<String> resultSet = ComparatorHelper.getResultSetFirstColumnAsString(originalQueryString, errors, state);
        // Normalize boolean values for M-compatibility mode
        resultSet = GaussDBMBooleanNormalizer.normalizeList(resultSet);

        select.setWhereClause(predicate);
        String firstQueryString = GaussDBToStringVisitor.asString(select);
        select.setWhereClause(negatedPredicate);
        String secondQueryString = GaussDBToStringVisitor.asString(select);
        select.setWhereClause(isNullPredicate);
        String thirdQueryString = GaussDBToStringVisitor.asString(select);
        List<String> combinedString = new ArrayList<>();
        List<String> secondResultSet = ComparatorHelper.getCombinedResultSetNoDuplicates(firstQueryString,
                secondQueryString, thirdQueryString, combinedString, true, state, errors);
        // Normalize boolean values for M-compatibility mode
        secondResultSet = GaussDBMBooleanNormalizer.normalizeList(secondResultSet);
        ComparatorHelper.assumeResultSetsAreEqual(resultSet, secondResultSet, originalQueryString, combinedString,
                state);
    }

    @Override
    List<GaussDBExpression> generateFetchColumns() {
        return Randomly.nonEmptySubset(targetTables.getColumns()).stream()
                .map(c -> GaussDBColumnReference.create(c, null)).collect(Collectors.toList());
    }

    @Override
    public String getLastQueryString() {
        return generatedQueryString;
    }
}
