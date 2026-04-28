package sqlancer.gaussdbm.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import sqlancer.ComparatorHelper;
import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.oracle.TestOracle;
import sqlancer.gaussdbm.GaussDBMBooleanNormalizer;
import sqlancer.gaussdbm.GaussDBMErrors;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBExpression;

/**
 * TLP HAVING oracle for GaussDB-M, aligned with {@code MySQLTLPHavingOracle}.
 */
public class GaussDBMTLPHavingOracle extends GaussDBMTLPBase implements TestOracle<GaussDBMGlobalState> {

    private String generatedQueryString;

    public GaussDBMTLPHavingOracle(GaussDBMGlobalState state) {
        super(state);
        GaussDBMErrors.addExpressionHavingErrors(errors);
    }

    @Override
    public void check() throws SQLException {
        super.check();
        if (Randomly.getBoolean()) {
            select.setWhereClause(gen.generateExpression());
        }
        select.setOrderByClauses(List.of());
        select.setGroupByExpressions(gen.generateExpressions(Randomly.smallNumber() + 1));
        select.setHavingClause(null);
        String originalQueryString = GaussDBToStringVisitor.asString(select);
        generatedQueryString = originalQueryString;
        List<String> resultSet = ComparatorHelper.getResultSetFirstColumnAsString(originalQueryString, errors, state);
        // Normalize boolean values for M-compatibility mode
        resultSet = GaussDBMBooleanNormalizer.normalizeList(resultSet);

        select.setHavingClause(predicate);
        String firstQueryString = GaussDBToStringVisitor.asString(select);
        select.setHavingClause(negatedPredicate);
        String secondQueryString = GaussDBToStringVisitor.asString(select);
        select.setHavingClause(isNullPredicate);
        String thirdQueryString = GaussDBToStringVisitor.asString(select);
        List<String> combinedString = new ArrayList<>();
        List<String> secondResultSet = ComparatorHelper.getCombinedResultSetNoDuplicates(firstQueryString,
                secondQueryString, thirdQueryString, combinedString, true, state, errors);
        // Normalize boolean values for M-compatibility mode
        secondResultSet = GaussDBMBooleanNormalizer.normalizeList(secondResultSet);
        try {
            ComparatorHelper.assumeResultSetsAreEqual(resultSet, secondResultSet, originalQueryString, combinedString,
                    state);
        } catch (AssertionError e) {
            if (e.getMessage() != null && e.getMessage().contains("The size of the result sets mismatch")) {
                throw new IgnoreMeException();
            }
            throw e;
        }
    }

    @Override
    protected GaussDBExpression generatePredicate() {
        return gen.generateHavingClause();
    }

    @Override
    public String getLastQueryString() {
        return generatedQueryString;
    }
}
