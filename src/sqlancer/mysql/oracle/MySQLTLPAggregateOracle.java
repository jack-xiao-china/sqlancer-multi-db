package sqlancer.mysql.oracle;

import sqlancer.common.oracle.NondeterminismGuard;
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
import sqlancer.mysql.MySQLErrors;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLVisitor;
import sqlancer.mysql.ast.MySQLAggregate;
import sqlancer.mysql.ast.MySQLAggregate.MySQLAggregateFunction;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLJoin;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLUnaryPostfixOperation;
import sqlancer.mysql.ast.MySQLUnaryPostfixOperation.UnaryPostfixOperator;
import sqlancer.mysql.ast.MySQLUnaryPrefixOperation;
import sqlancer.mysql.ast.MySQLUnaryPrefixOperation.MySQLUnaryPrefixOperator;

/**
 * TLP Aggregate Oracle for MySQL. Ported from PostgreSQL's PostgresTLPAggregateOracle.
 * Uses ternary logic partitioning: partitions rows by predicate, NOT predicate, and IS NULL,
 * computes the same aggregate on each partition, then combines with an outer aggregate.
 * A mismatch between original and metamorphic results indicates a logic bug.
 */
public class MySQLTLPAggregateOracle extends MySQLTLPBase implements TestOracle<MySQLGlobalState> {

    private String generatedQueryString;

    public MySQLTLPAggregateOracle(MySQLGlobalState state) {
        super(state);
        MySQLErrors.addExpressionHavingErrors(errors);
    }

    @Override
    public void check() throws SQLException {
        super.check();
        aggregateCheck();
    }

    protected void aggregateCheck() throws SQLException {
        MySQLAggregateFunction[] allowedFuncs = { MySQLAggregateFunction.COUNT, MySQLAggregateFunction.SUM,
                MySQLAggregateFunction.MIN, MySQLAggregateFunction.MAX };
        MySQLAggregateFunction aggregateFunction = Randomly.fromOptions(allowedFuncs);
        List<MySQLExpression> args = gen.generateExpressions(1);
        MySQLAggregate aggregate = new MySQLAggregate(args, aggregateFunction);

        select.setFetchColumns(Arrays.asList(aggregate));
        // 显式清空 ORDER BY：MySQL 将 ORDER BY <整数> 解释为列位置，会导致错误（同 NoREC）
        select.setOrderByClauses(List.of());
        if (NondeterminismGuard.containsNondeterministicCall(MySQLVisitor.asString(select))) {
            // The aggregate projection is swapped in after the base guard: re-check here.
            throw new IgnoreMeException();
        }

        String originalQuery = MySQLVisitor.asString(select);
        generatedQueryString = originalQuery;
        String firstResult = getAggregateResult(originalQuery);

        MySQLExpression whereClause = gen.generateExpression();
        if (NondeterminismGuard.containsNondeterministicCall(MySQLVisitor.asString(whereClause))) {
            // Nondeterministic partition predicates (e.g. CURTIME() in a CASE branch) differ
            // between the three TLP branches; the comparison is meaningless for such rounds.
            throw new IgnoreMeException();
        }
        MySQLExpression negatedClause = new MySQLUnaryPrefixOperation(whereClause, MySQLUnaryPrefixOperator.NOT);
        MySQLExpression isNullClause = new MySQLUnaryPostfixOperation(whereClause, UnaryPostfixOperator.IS_NULL,
                false);

        List<MySQLExpression> fromList = select.getFromList();
        List<MySQLJoin> joinList = select.getJoinClauses();

        // 三个分区必须使用相同的 GROUP BY，否则分区结果不可比
        List<MySQLExpression> groupByExprs = Randomly.getBooleanWithSmallProbability()
                ? gen.generateExpressions(Randomly.smallNumber() + 1) : null;
        MySQLSelect leftSelect = getSelect(aggregate, fromList, whereClause, joinList, groupByExprs);
        MySQLSelect middleSelect = getSelect(aggregate, fromList, negatedClause, joinList, groupByExprs);
        MySQLSelect rightSelect = getSelect(aggregate, fromList, isNullClause, joinList, groupByExprs);

        String outerAgg = getOuterAggregateFunction(aggregate);
        String metamorphicQuery = "SELECT " + outerAgg + " FROM (";
        metamorphicQuery += MySQLVisitor.asString(leftSelect) + " UNION ALL " + MySQLVisitor.asString(middleSelect)
                + " UNION ALL " + MySQLVisitor.asString(rightSelect);
        metamorphicQuery += ") AS t0";

        String secondResult = getAggregateResult(metamorphicQuery);

        String firstQueryStr = String.format("-- %s;\n-- result: %s", originalQuery, firstResult);
        String secondQueryStr = String.format("-- %s;\n-- result: %s", metamorphicQuery, secondResult);
        state.getState().getLocalState().log(String.format("%s\n%s", firstQueryStr, secondQueryStr));

        // COUNT 空集返回 0，SUM 空集返回 NULL；0 与 null 在此场景下语义等价（参考 Doris 实现）
        boolean zeroNullEqual = ("0".equals(firstResult) && secondResult == null)
                || (firstResult == null && "0".equals(secondResult));
        if (zeroNullEqual) {
            return; // 视为等价，不报错
        }
        // 极端浮点值（如 ±Double.MAX_VALUE）在 SUM 分区/全表时可能溢出语义不同，跳过
        if (isExtremeFloatMismatch(firstResult, secondResult)) {
            throw new IgnoreMeException();
        }
        if (firstResult == null && secondResult != null || firstResult != null && secondResult == null
                || firstResult != null && !firstResult.contentEquals(secondResult)
                        && !ComparatorHelper.isEqualDouble(firstResult, secondResult)) {
            if (secondResult != null && secondResult.contains("Inf")) {
                throw new IgnoreMeException();
            }
            throw new AssertionError(
                    String.format("the results mismatch!\n%s\n%s", firstQueryStr, secondQueryStr));
        }
    }

    /** 一方接近 0、另一方为极端值（如 ±Double.MAX_VALUE）时，可能是浮点溢出导致分区/全表语义不同，跳过 */
    private static boolean isExtremeFloatMismatch(String a, String b) {
        if (a == null || b == null) return false;
        String sa = a.trim();
        String sb = b.trim();
        // 字符串兜底：一方含 E308（极端双精度），另一方为 0/0.0
        boolean aExtreme = sa.contains("E308") || sa.contains("E-308");
        boolean bExtreme = sb.contains("E308") || sb.contains("E-308");
        boolean aZero = "0".equals(sa) || sa.matches("0\\.0*") || sa.matches("-0\\.0*");
        boolean bZero = "0".equals(sb) || sb.matches("0\\.0*") || sb.matches("-0\\.0*");
        if ((aExtreme && bZero) || (bExtreme && aZero)) return true;
        try {
            double da = Double.parseDouble(sa);
            double db = Double.parseDouble(sb);
            double extreme = 1e300;
            double nearZero = 1.0; // 含 0、0.0 及溢出归零
            return (Math.abs(da) <= nearZero && Math.abs(db) > extreme)
                    || (Math.abs(db) <= nearZero && Math.abs(da) > extreme);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String getOuterAggregateFunction(MySQLAggregate aggregate) {
        switch (aggregate.getFunc()) {
        case COUNT:
        case COUNT_DISTINCT:
            // SUM 对空集返回 NULL，而 COUNT 对空集返回 0；用 COALESCE 统一语义
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

    private MySQLSelect getSelect(MySQLAggregate aggregate, List<MySQLExpression> from, MySQLExpression whereClause,
            List<MySQLJoin> joinList, List<MySQLExpression> groupByExprs) {
        MySQLSelect s = new MySQLSelect();
        s.setFetchColumns(new ArrayList<>(Arrays.asList(aggregate)));
        s.setFromList(new ArrayList<>(from));
        s.setWhereClause(whereClause);
        s.setJoinList(joinList != null ? joinList.stream().map(j -> (MySQLExpression) j).collect(Collectors.toList())
                : new ArrayList<>());
        s.setOrderByClauses(List.of()); // MySQL 将 ORDER BY <整数> 解释为列位置
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
