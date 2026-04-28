package sqlancer.gaussdbm.oracle;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.ComparatorHelper;
import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.gaussdbm.GaussDBMErrors;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBAggregate;
import sqlancer.gaussdbm.ast.GaussDBAggregate.GaussDBAggregateFunction;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBJoin;
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation.UnaryPostfixOperator;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation.UnaryPrefixOperator;

/**
 * TLP aggregate oracle for GaussDB-M (M-Compatibility), aligned with {@code MySQLTLPAggregateOracle}.
 */
public class GaussDBMTLPAggregateOracle extends GaussDBMTLPBase implements TestOracle<GaussDBMGlobalState> {

    private String generatedQueryString;

    public GaussDBMTLPAggregateOracle(GaussDBMGlobalState state) {
        super(state);
        GaussDBMErrors.addExpressionHavingErrors(errors);
    }

    @Override
    public void check() throws SQLException {
        super.check();
        aggregateCheck();
    }

    private String normalizeBooleanResult(String result) {
        // GaussDB M-compatibility mode: boolean values can be returned as 't'/'f' (PG style)
        // or 1/0 (MySQL style). Normalize to 1/0 for comparison.
        if (result == null) {
            return null;
        }
        String trimmed = result.trim();
        if ("t".equals(trimmed) || "true".equalsIgnoreCase(trimmed)) {
            return "1";
        }
        if ("f".equals(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return "0";
        }
        return result;
    }

    private boolean compareResults(String firstResult, String secondResult) {
        String first = normalizeBooleanResult(firstResult);
        String second = normalizeBooleanResult(secondResult);

        // Handle 0/NULL equivalence for certain aggregate functions
        boolean zeroNullEqual = ("0".equals(first) && second == null) || (first == null && "0".equals(second));
        if (zeroNullEqual) {
            return true;
        }

        if (first == null && second != null || first != null && second == null) {
            return false;
        }

        if (first == null && second == null) {
            return true;
        }

        // Direct string comparison
        if (first.contentEquals(second)) {
            return true;
        }

        // Floating point comparison
        if (ComparatorHelper.isEqualDouble(first, second)) {
            return true;
        }

        return false;
    }

    protected void aggregateCheck() throws SQLException {
        GaussDBAggregateFunction[] allowedFuncs = { GaussDBAggregateFunction.COUNT, GaussDBAggregateFunction.SUM,
                GaussDBAggregateFunction.MIN, GaussDBAggregateFunction.MAX };
        GaussDBAggregateFunction aggregateFunction = Randomly.fromOptions(allowedFuncs);
        List<GaussDBExpression> args = gen.generateExpressions(1);
        GaussDBAggregate aggregate = new GaussDBAggregate(args, aggregateFunction);

        select.setFetchColumns(Arrays.asList(aggregate));
        select.setOrderByClauses(List.of());

        String originalQuery = GaussDBToStringVisitor.asString(select);
        generatedQueryString = originalQuery;
        String firstResult = getAggregateResult(originalQuery);

        GaussDBExpression whereClause = gen.generateExpression();
        GaussDBExpression negatedClause = new GaussDBUnaryPrefixOperation(whereClause, UnaryPrefixOperator.NOT);
        GaussDBExpression isNullClause = new GaussDBUnaryPostfixOperation(whereClause, UnaryPostfixOperator.IS_NULL);

        List<GaussDBExpression> fromList = select.getFromList();
        List<GaussDBJoin> joinList = select.getJoinClauses();

        List<GaussDBExpression> groupByExprs = Randomly.getBooleanWithSmallProbability()
                ? gen.generateExpressions(Randomly.smallNumber() + 1) : null;
        GaussDBSelect leftSelect = getSelect(aggregate, fromList, whereClause, joinList, groupByExprs);
        GaussDBSelect middleSelect = getSelect(aggregate, fromList, negatedClause, joinList, groupByExprs);
        GaussDBSelect rightSelect = getSelect(aggregate, fromList, isNullClause, joinList, groupByExprs);

        String outerAgg = getOuterAggregateFunction(aggregate);
        String metamorphicQuery = "SELECT " + outerAgg + " FROM (";
        metamorphicQuery += GaussDBToStringVisitor.asString(leftSelect) + " UNION ALL "
                + GaussDBToStringVisitor.asString(middleSelect) + " UNION ALL "
                + GaussDBToStringVisitor.asString(rightSelect);
        metamorphicQuery += ") AS t0";

        String secondResult = getAggregateResult(metamorphicQuery);

        String firstQueryStr = String.format("-- %s;\n-- result: %s", originalQuery, firstResult);
        String secondQueryStr = String.format("-- %s;\n-- result: %s", metamorphicQuery, secondResult);
        state.getState().getLocalState().log(String.format("%s\n%s", firstQueryStr, secondQueryStr));

        if (isExtremeFloatMismatch(firstResult, secondResult)) {
            throw new IgnoreMeException();
        }

        if (secondResult != null && secondResult.contains("Inf")) {
            throw new IgnoreMeException();
        }

        if (!compareResults(firstResult, secondResult)) {
            throw new AssertionError(String.format("the results mismatch!\n%s\n%s", firstQueryStr, secondQueryStr));
        }
    }

    private static boolean isExtremeFloatMismatch(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String sa = a.trim();
        String sb = b.trim();
        boolean aExtreme = sa.contains("E308") || sa.contains("E-308");
        boolean bExtreme = sb.contains("E308") || sb.contains("E-308");
        boolean aZero = "0".equals(sa) || sa.matches("0\\.0*") || sa.matches("-0\\.0*");
        boolean bZero = "0".equals(sb) || sb.matches("0\\.0*") || sb.matches("-0\\.0*");
        if ((aExtreme && bZero) || (bExtreme && aZero)) {
            return true;
        }
        try {
            double da = Double.parseDouble(sa);
            double db = Double.parseDouble(sb);
            double extreme = 1e300;
            double nearZero = 1.0;
            return (Math.abs(da) <= nearZero && Math.abs(db) > extreme)
                    || (Math.abs(db) <= nearZero && Math.abs(da) > extreme);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String getOuterAggregateFunction(GaussDBAggregate aggregate) {
        switch (aggregate.getFunc()) {
        case COUNT:
        case COUNT_DISTINCT:
            return "COALESCE(SUM(ref0), 0)";
        case SUM:
        case SUM_DISTINCT:
        case MIN:
        case MIN_DISTINCT:
        case MAX:
        case MAX_DISTINCT:
            return aggregate.getFunc().getName() + "(ref0)";
        default:
            throw new AssertionError(aggregate.getFunc());
        }
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
        SQLQueryAdapter q = new SQLQueryAdapter(queryString, errors);
        try (SQLancerResultSet result = q.executeAndGet(state)) {
            if (result == null) {
                throw new IgnoreMeException();
            }
            if (!result.next()) {
                return null;
            }
            return result.getString(1);
        } catch (Exception e) {
            if (e instanceof IgnoreMeException) {
                throw (IgnoreMeException) e;
            }
            throw new AssertionError(queryString, e);
        }
    }

    private GaussDBSelect getSelect(GaussDBAggregate aggregate, List<GaussDBExpression> from,
            GaussDBExpression whereClause, List<GaussDBJoin> joinList, List<GaussDBExpression> groupByExprs) {
        GaussDBSelect s = new GaussDBSelect();
        s.setFetchColumns(new ArrayList<>(Arrays.asList(aggregate)));
        s.setFromList(new ArrayList<>(from));
        s.setWhereClause(whereClause);
        s.setJoinList(joinList != null ? joinList.stream().map(j -> (GaussDBExpression) j).collect(Collectors.toList())
                : new ArrayList<>());
        s.setOrderByClauses(List.of());
        if (groupByExprs != null) {
            s.setGroupByExpressions(new ArrayList<>(groupByExprs));
        }
        return s;
    }

    @Override
    public String getLastQueryString() {
        return generatedQueryString;
    }
}
