package sqlancer.fucci.eval;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

/**
 * WHERE 子句谓词求值门面。
 * 使用 JSqlParser 解析 WHERE 表达式，在内存中对 View 数据逐行求值。
 *
 * <p>保守策略：解析或求值失败时返回所有行（避免误报）。</p>
 */
public final class PredicateEvaluator {

    private PredicateEvaluator() {
    }

    /**
     * 对 data 中所有行求值 whereClause，返回满足条件的 rowId 集合。
     *
     * @param whereClause WHERE 条件字符串（不含 "WHERE" 关键字）
     * @param resolver    列名到索引的映射器
     * @param data        rowId → 行数据映射
     * @return 满足条件的 rowId 集合；解析或求值失败时返回全部 rowId
     */
    public static Set<Integer> evaluate(String whereClause, ColumnResolver resolver,
            Map<Integer, Object[]> data) {
        if (whereClause == null || whereClause.trim().isEmpty()) {
            return new HashSet<>(data.keySet());
        }

        Expression parsed;
        try {
            parsed = CCJSqlParserUtil.parseExpression(whereClause);
        } catch (Exception e) {
            return new HashSet<>(data.keySet());
        }

        return evaluate(parsed, resolver, data);
    }

    /**
     * 对 data 中所有行求值已解析的表达式。
     *
     * @param predicate 已解析的 JSqlParser 表达式
     * @param resolver  列名到索引的映射器
     * @param data      rowId → 行数据映射
     * @return 满足条件的 rowId 集合
     */
    public static Set<Integer> evaluate(Expression predicate, ColumnResolver resolver,
            Map<Integer, Object[]> data) {
        Set<Integer> matchingRows = new HashSet<>();

        for (Map.Entry<Integer, Object[]> entry : data.entrySet()) {
            int rowId = entry.getKey();
            Object[] rowData = entry.getValue();

            ExpressionEvalVisitor visitor = new ExpressionEvalVisitor(resolver, rowData);
            predicate.accept(visitor);

            if (visitor.isEvaluationFailed()) {
                matchingRows.add(rowId);
            } else {
                Object evalResult = visitor.getResult();
                if (evalResult instanceof Boolean && (Boolean) evalResult) {
                    matchingRows.add(rowId);
                }
            }
        }

        return matchingRows;
    }

    /**
     * 对单行数据求值 whereClause。
     *
     * @param whereClause WHERE 条件字符串
     * @param resolver    列名到索引的映射器
     * @param rowData     行数据
     * @return true 表示满足条件或求值失败（保守）
     */
    public static boolean evaluateSingle(String whereClause, ColumnResolver resolver,
            Object[] rowData) {
        if (whereClause == null || whereClause.trim().isEmpty()) {
            return true;
        }

        Expression parsed;
        try {
            parsed = CCJSqlParserUtil.parseExpression(whereClause);
        } catch (Exception e) {
            return true;
        }

        ExpressionEvalVisitor visitor = new ExpressionEvalVisitor(resolver, rowData);
        parsed.accept(visitor);

        if (visitor.isEvaluationFailed()) {
            return true;
        }

        Object evalResult = visitor.getResult();
        return evalResult instanceof Boolean && (Boolean) evalResult;
    }
}
