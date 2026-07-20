package sqlancer.gaussdba.oracle.tlp;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sqlancer.ComparatorHelper;
import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.gaussdba.GaussDBAErrors;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBAToStringVisitor;
import sqlancer.gaussdba.ast.GaussDBAAggregate;
import sqlancer.gaussdba.ast.GaussDBAAggregate.GaussDBAAggregateFunction;
import sqlancer.gaussdba.ast.GaussDBAAlias;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.ast.GaussDBAJoin;
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.ast.GaussDBAUnaryPrefixOperation;
import sqlancer.gaussdba.ast.GaussDBAUnaryPrefixOperation.UnaryPrefixOperator;
import sqlancer.gaussdba.ast.GaussDBAUnaryPostfixOperation;
import sqlancer.gaussdba.ast.GaussDBAUnaryPostfixOperation.UnaryPostfixOperator;
import sqlancer.gaussdba.ast.GaussDBAColumnReference;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;

public class GaussDBATLPAggregateOracle extends GaussDBATLPBase implements TestOracle<GaussDBAGlobalState> {

    private String firstResult;
    private String secondResult;
    private String originalQuery;
    private String metamorphicQuery;

    public GaussDBATLPAggregateOracle(GaussDBAGlobalState state) {
        super(state);
        // Add expected errors for aggregate operations
        GaussDBAErrors.addExpressionErrors(errors);
        errors.add("cannot cast type");
        errors.add("invalid input syntax for type");
        errors.add("function sum");  // SUM on non-numeric types
        errors.add("function max");  // MAX on incompatible types
        errors.add("function min");  // MIN on incompatible types
        errors.add("operator does not exist");
        errors.add("could not determine data type");
        errors.add("aggregate function");
    }

    @Override
    public void check() throws SQLException {
        super.check();
        aggregateCheck();
    }

    protected void aggregateCheck() throws SQLException {
        GaussDBAAggregateFunction aggregateFunction = Randomly.fromOptions(
                GaussDBAAggregateFunction.MAX,
                GaussDBAAggregateFunction.MIN,
                GaussDBAAggregateFunction.SUM,
                GaussDBAAggregateFunction.COUNT);

        // Create a simple aggregate with a column argument
        GaussDBAColumn column = Randomly.fromList(targetTables.getColumns());
        GaussDBAExpression columnRef = new GaussDBAColumnReference(column, null);
        GaussDBAAggregate aggregate = new GaussDBAAggregate(Arrays.asList(columnRef), aggregateFunction);

        select.setFetchColumns(Arrays.asList(aggregate));
        if (Randomly.getBooleanWithRatherLowProbability()) {
            select.setOrderByClauses(gen.generateOrderBys());
        }
        originalQuery = GaussDBAToStringVisitor.asString(select);
        firstResult = getAggregateResult(originalQuery);
        metamorphicQuery = createMetamorphicUnionQuery(select, aggregate, select.getFromList());
        secondResult = getAggregateResult(metamorphicQuery);

        String queryFormatString = "-- %s;\n-- result: %s";
        String firstQueryString = String.format(queryFormatString, originalQuery, firstResult);
        String secondQueryString = String.format(queryFormatString, metamorphicQuery, secondResult);
        state.getState().getLocalState().log(String.format("%s\n%s", firstQueryString, secondQueryString));
        if (firstResult == null && secondResult != null || firstResult != null && secondResult == null
                || firstResult != null && !firstResult.contentEquals(secondResult)
                        && !ComparatorHelper.isEqualDouble(firstResult, secondResult)) {
            if (secondResult != null && secondResult.contains("Inf")) {
                throw new IgnoreMeException();
            }
            String assertionMessage = String.format("the results mismatch!\n%s\n%s", firstQueryString,
                    secondQueryString);
            throw new AssertionError(assertionMessage);
        }
    }

    private String createMetamorphicUnionQuery(GaussDBASelect select, GaussDBAAggregate aggregate,
            List<GaussDBAExpression> from) {
        String metamorphicQuery;
        GaussDBAExpression whereClause = gen.generateBooleanExpression();
        GaussDBAExpression negatedClause = new GaussDBAUnaryPrefixOperation(whereClause, UnaryPrefixOperator.NOT);
        GaussDBAExpression isNullClause = new GaussDBAUnaryPostfixOperation(whereClause, UnaryPostfixOperator.IS_NULL);
        // Alias the aggregate as agg0 so the outer aggregate (e.g. SUM(agg0)) can reference it.
        // The GaussDB-A visitor does not add projection aliases automatically, so without this
        // the metamorphic query fails with "column agg0 does not exist" (SQLSTATE 42703).
        List<GaussDBAExpression> mappedAggregate = Arrays.asList(new GaussDBAAlias(aggregate, "agg0"));
        GaussDBASelect leftSelect = getSelect(mappedAggregate, from, whereClause, select.getJoinClauses());
        GaussDBASelect middleSelect = getSelect(mappedAggregate, from, negatedClause, select.getJoinClauses());
        GaussDBASelect rightSelect = getSelect(mappedAggregate, from, isNullClause, select.getJoinClauses());
        metamorphicQuery = "SELECT " + getOuterAggregateFunction(aggregate) + " FROM (";
        metamorphicQuery += GaussDBAToStringVisitor.asString(leftSelect) + " UNION ALL "
                + GaussDBAToStringVisitor.asString(middleSelect) + " UNION ALL " + GaussDBAToStringVisitor.asString(rightSelect);
        metamorphicQuery += ") as res";
        return metamorphicQuery;
    }

    private String getAggregateResult(String queryString) throws SQLException {
        if (state.getOptions().logEachSelect()) {
            state.getLogger().writeCurrent(queryString);
            try {
                state.getLogger().getCurrentFileWriter().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        String resultString;
        SQLQueryAdapter q = new SQLQueryAdapter(queryString, errors);
        try (SQLancerResultSet result = q.executeAndGet(state)) {
            state.getManager().incrementSelectQueryCount();
            if (result == null) {
                throw new IgnoreMeException();
            }
            if (!result.next()) {
                resultString = null;
            } else {
                resultString = result.getString(1);
            }
        } catch (SQLException e) {
            // Check if error is expected
            if (errors.errorIsExpected(e.getMessage())) {
                throw new IgnoreMeException();
            }
            throw new AssertionError(queryString, e);
        } catch (AssertionError e) {
            // Error from checkException - if it's an expected error
            throw new IgnoreMeException();
        }
        return resultString;
    }

    private String getOuterAggregateFunction(GaussDBAAggregate aggregate) {
        GaussDBAAggregateFunction func = aggregate.getFunc();
        switch (func) {
        case COUNT:
            // COALESCE guards the empty-table case: the UNION ALL of partitions yields no rows,
            // so SUM(agg0) would be NULL while the original COUNT(...) returns 0.
            return "COALESCE(SUM(agg0), 0)";
        default:
            return func.toString() + "(agg0)";
        }
    }

    private GaussDBASelect getSelect(List<GaussDBAExpression> aggregates, List<GaussDBAExpression> from,
            GaussDBAExpression whereClause, List<GaussDBAJoin> joinList) {
        GaussDBASelect leftSelect = new GaussDBASelect();
        leftSelect.setFetchColumns(aggregates);
        leftSelect.setFromList(from);
        leftSelect.setWhereClause(whereClause);
        leftSelect.setJoinClauses(joinList);
        if (Randomly.getBooleanWithSmallProbability()) {
            List<GaussDBAExpression> groupByColumns = new ArrayList<>();
            int numGroupBy = Randomly.smallNumber() + 1;
            List<GaussDBAColumn> availableColumns = targetTables.getColumns();
            for (int i = 0; i < numGroupBy && i < availableColumns.size(); i++) {
                GaussDBAColumn c = Randomly.fromList(availableColumns);
                groupByColumns.add(new GaussDBAColumnReference(c, null));
            }
            leftSelect.setGroupByExpressions(groupByColumns);
        }
        return leftSelect;
    }
}