package sqlancer.fucci.eval;

import java.util.regex.Pattern;

import net.sf.jsqlparser.expression.AllValue;
import net.sf.jsqlparser.expression.AnalyticExpression;
import net.sf.jsqlparser.expression.AnyComparisonExpression;
import net.sf.jsqlparser.expression.ArrayConstructor;
import net.sf.jsqlparser.expression.ArrayExpression;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.CollateExpression;
import net.sf.jsqlparser.expression.ConnectByRootOperator;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DateTimeLiteralExpression;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitor;
import net.sf.jsqlparser.expression.ExtractExpression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.HexValue;
import net.sf.jsqlparser.expression.IntervalExpression;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.JsonAggregateFunction;
import net.sf.jsqlparser.expression.JsonExpression;
import net.sf.jsqlparser.expression.JsonFunction;
import net.sf.jsqlparser.expression.KeepExpression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.MySQLGroupConcat;
import net.sf.jsqlparser.expression.NextValExpression;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.NumericBind;
import net.sf.jsqlparser.expression.OracleHierarchicalExpression;
import net.sf.jsqlparser.expression.OracleHint;
import net.sf.jsqlparser.expression.OracleNamedFunctionParameter;
import net.sf.jsqlparser.expression.OverlapsCondition;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.RowConstructor;
import net.sf.jsqlparser.expression.RowGetExpression;
import net.sf.jsqlparser.expression.SafeCastExpression;
import net.sf.jsqlparser.expression.SignedExpression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeKeyExpression;
import net.sf.jsqlparser.expression.TimeValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.TimezoneExpression;
import net.sf.jsqlparser.expression.TryCastExpression;
import net.sf.jsqlparser.expression.UserVariable;
import net.sf.jsqlparser.expression.ValueListExpression;
import net.sf.jsqlparser.expression.VariableAssignment;
import net.sf.jsqlparser.expression.WhenClause;
import net.sf.jsqlparser.expression.XMLSerializeExpr;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseAnd;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseLeftShift;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseOr;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseRightShift;
import net.sf.jsqlparser.expression.operators.arithmetic.BitwiseXor;
import net.sf.jsqlparser.expression.operators.arithmetic.Concat;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.IntegerDivision;
import net.sf.jsqlparser.expression.operators.arithmetic.Modulo;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.conditional.XorExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.FullTextSearch;
import net.sf.jsqlparser.expression.operators.relational.GeometryDistance;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsBooleanExpression;
import net.sf.jsqlparser.expression.operators.relational.IsDistinctExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.JsonOperator;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.Matches;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.expression.operators.relational.RegExpMatchOperator;
import net.sf.jsqlparser.expression.operators.relational.RegExpMySQLOperator;
import net.sf.jsqlparser.expression.operators.relational.SimilarToExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.SubSelect;

/**
 * 基于 JSqlParser 的 SQL 表达式内存求值器。
 * 对单行 Object[] 数据求值表达式 AST，返回 Boolean（谓词）或 Comparable（值表达式）。
 *
 * <p>回退策略：遇到未支持的表达式类型时设置 evaluationFailed = true，
 * 调用方应视为"包含该行"（保守策略，避免误报）。</p>
 */
public class ExpressionEvalVisitor implements ExpressionVisitor {

    private final ColumnResolver resolver;
    private final Object[] rowData;
    private Object result;
    private boolean evaluationFailed;

    public ExpressionEvalVisitor(ColumnResolver resolver, Object[] rowData) {
        this.resolver = resolver;
        this.rowData = rowData;
    }

    public Object getResult() {
        return result;
    }

    public boolean isEvaluationFailed() {
        return evaluationFailed;
    }

    public void reset() {
        this.result = null;
        this.evaluationFailed = false;
    }

    // ==================== 列引用 ====================

    @Override
    public void visit(Column column) {
        int idx = resolver.resolve(column.getColumnName());
        if (idx < 0 || idx >= rowData.length) {
            evaluationFailed = true;
            return;
        }
        result = rowData[idx];
    }

    // ==================== 字面量 ====================

    @Override
    public void visit(LongValue val) {
        result = val.getValue();
    }

    @Override
    public void visit(DoubleValue val) {
        result = val.getValue();
    }

    @Override
    public void visit(StringValue val) {
        result = val.getValue();
    }

    @Override
    public void visit(NullValue val) {
        result = null;
    }

    @Override
    public void visit(HexValue val) {
        result = val.getValue();
    }

    @Override
    public void visit(DateValue val) {
        result = val.getValue();
    }

    @Override
    public void visit(TimeValue val) {
        result = val.getValue();
    }

    @Override
    public void visit(TimestampValue val) {
        result = val.getValue();
    }

    @Override
    public void visit(AllValue val) {
        evaluationFailed = true;
    }

    // ==================== 比较操作符 ====================

    @Override
    public void visit(EqualsTo expr) {
        expr.getLeftExpression().accept(this);
        Object left = result;
        expr.getRightExpression().accept(this);
        Object right = result;
        if (left == null || right == null) {
            result = null;
        } else {
            result = compareValues(left, right) == 0;
        }
    }

    @Override
    public void visit(NotEqualsTo expr) {
        expr.getLeftExpression().accept(this);
        Object left = result;
        expr.getRightExpression().accept(this);
        Object right = result;
        if (left == null || right == null) {
            result = null;
        } else {
            result = compareValues(left, right) != 0;
        }
    }

    @Override
    public void visit(GreaterThan expr) {
        expr.getLeftExpression().accept(this);
        Object left = result;
        expr.getRightExpression().accept(this);
        Object right = result;
        if (left == null || right == null) {
            result = null;
        } else {
            result = compareValues(left, right) > 0;
        }
    }

    @Override
    public void visit(GreaterThanEquals expr) {
        expr.getLeftExpression().accept(this);
        Object left = result;
        expr.getRightExpression().accept(this);
        Object right = result;
        if (left == null || right == null) {
            result = null;
        } else {
            result = compareValues(left, right) >= 0;
        }
    }

    @Override
    public void visit(MinorThan expr) {
        expr.getLeftExpression().accept(this);
        Object left = result;
        expr.getRightExpression().accept(this);
        Object right = result;
        if (left == null || right == null) {
            result = null;
        } else {
            result = compareValues(left, right) < 0;
        }
    }

    @Override
    public void visit(MinorThanEquals expr) {
        expr.getLeftExpression().accept(this);
        Object left = result;
        expr.getRightExpression().accept(this);
        Object right = result;
        if (left == null || right == null) {
            result = null;
        } else {
            result = compareValues(left, right) <= 0;
        }
    }

    // ==================== 逻辑操作符 ====================

    @Override
    public void visit(AndExpression expr) {
        expr.getLeftExpression().accept(this);
        Object left = result;
        expr.getRightExpression().accept(this);
        Object right = result;

        Boolean l = toBoolean(left);
        Boolean r = toBoolean(right);

        if (l != null && !l) {
            result = Boolean.FALSE;
        } else if (r != null && !r) {
            result = Boolean.FALSE;
        } else if (l == null || r == null) {
            result = null;
        } else {
            result = l && r;
        }
    }

    @Override
    public void visit(OrExpression expr) {
        expr.getLeftExpression().accept(this);
        Object left = result;
        expr.getRightExpression().accept(this);
        Object right = result;

        Boolean l = toBoolean(left);
        Boolean r = toBoolean(right);

        if (l != null && l) {
            result = Boolean.TRUE;
        } else if (r != null && r) {
            result = Boolean.TRUE;
        } else if (l == null || r == null) {
            result = null;
        } else {
            result = l || r;
        }
    }

    @Override
    public void visit(XorExpression expr) {
        expr.getLeftExpression().accept(this);
        Object left = result;
        expr.getRightExpression().accept(this);
        Object right = result;

        Boolean l = toBoolean(left);
        Boolean r = toBoolean(right);

        if (l == null || r == null) {
            result = null;
        } else {
            result = l ^ r;
        }
    }

    @Override
    public void visit(NotExpression expr) {
        expr.getExpression().accept(this);
        Boolean b = toBoolean(result);
        result = (b == null) ? null : !b;
    }

    // ==================== NULL 检查 ====================

    @Override
    public void visit(IsNullExpression expr) {
        expr.getLeftExpression().accept(this);
        boolean isNull = (result == null);
        result = expr.isNot() ? !isNull : isNull;
    }

    @Override
    public void visit(IsBooleanExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(IsDistinctExpression expr) {
        evaluationFailed = true;
    }

    // ==================== IN ====================

    @Override
    public void visit(InExpression expr) {
        expr.getLeftExpression().accept(this);
        Object leftVal = result;

        if (leftVal == null) {
            result = null;
            return;
        }

        boolean found = false;
        if (expr.getRightItemsList() instanceof ExpressionList) {
            ExpressionList list = (ExpressionList) expr.getRightItemsList();
            for (Expression item : list.getExpressions()) {
                item.accept(this);
                if (compareValues(leftVal, result) == 0) {
                    found = true;
                    break;
                }
            }
        } else {
            evaluationFailed = true;
            return;
        }
        result = expr.isNot() ? !found : found;
    }

    // ==================== BETWEEN ====================

    @Override
    public void visit(Between expr) {
        expr.getLeftExpression().accept(this);
        Object val = result;
        expr.getBetweenExpressionStart().accept(this);
        Object start = result;
        expr.getBetweenExpressionEnd().accept(this);
        Object end = result;

        if (val == null || start == null || end == null) {
            result = null;
            return;
        }
        boolean between = compareValues(val, start) >= 0 && compareValues(val, end) <= 0;
        result = expr.isNot() ? !between : between;
    }

    // ==================== LIKE ====================

    @Override
    public void visit(LikeExpression expr) {
        expr.getLeftExpression().accept(this);
        Object leftVal = result;
        expr.getRightExpression().accept(this);
        Object patternVal = result;

        if (leftVal == null || patternVal == null) {
            result = null;
            return;
        }

        String str = leftVal.toString();
        String pattern = patternVal.toString();
        String regex = likeToRegex(pattern);
        boolean matches = Pattern.matches(regex, str);
        result = expr.isNot() ? !matches : matches;
    }

    // ==================== 算术操作符 ====================

    @Override
    public void visit(Addition expr) {
        evalBinaryArithmetic(expr.getLeftExpression(), expr.getRightExpression(), '+');
    }

    @Override
    public void visit(Subtraction expr) {
        evalBinaryArithmetic(expr.getLeftExpression(), expr.getRightExpression(), '-');
    }

    @Override
    public void visit(Multiplication expr) {
        evalBinaryArithmetic(expr.getLeftExpression(), expr.getRightExpression(), '*');
    }

    @Override
    public void visit(Division expr) {
        evalBinaryArithmetic(expr.getLeftExpression(), expr.getRightExpression(), '/');
    }

    @Override
    public void visit(IntegerDivision expr) {
        evalBinaryArithmetic(expr.getLeftExpression(), expr.getRightExpression(), '/');
        if (result instanceof Number) {
            result = ((Number) result).longValue();
        }
    }

    @Override
    public void visit(Modulo expr) {
        evalBinaryArithmetic(expr.getLeftExpression(), expr.getRightExpression(), '%');
    }

    // ==================== 括号 ====================

    @Override
    public void visit(Parenthesis expr) {
        expr.getExpression().accept(this);
    }

    // ==================== CASE WHEN ====================

    @Override
    public void visit(CaseExpression expr) {
        for (WhenClause when : expr.getWhenClauses()) {
            when.getWhenExpression().accept(this);
            Boolean cond = toBoolean(result);
            if (cond != null && cond) {
                when.getThenExpression().accept(this);
                return;
            }
        }
        if (expr.getElseExpression() != null) {
            expr.getElseExpression().accept(this);
        } else {
            result = null;
        }
    }

    @Override
    public void visit(WhenClause expr) {
        expr.getWhenExpression().accept(this);
    }

    // ==================== 符号表达式 ====================

    @Override
    public void visit(SignedExpression expr) {
        expr.getExpression().accept(this);
        if (result instanceof Number && expr.getSign() == '-') {
            Number num = (Number) result;
            if (num instanceof Long) {
                result = -num.longValue();
            } else if (num instanceof Integer) {
                result = -num.intValue();
            } else {
                result = -num.doubleValue();
            }
        }
    }

    // ==================== 字符串连接 ====================

    @Override
    public void visit(Concat expr) {
        expr.getLeftExpression().accept(this);
        Object left = result;
        expr.getRightExpression().accept(this);
        Object right = result;
        if (left == null || right == null) {
            result = null;
        } else {
            result = left.toString() + right.toString();
        }
    }

    // ==================== 未支持的类型(回退) ====================

    @Override
    public void visit(BitwiseAnd expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(BitwiseOr expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(BitwiseXor expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(BitwiseLeftShift expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(BitwiseRightShift expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(Function function) {
        evaluationFailed = true;
    }

    @Override
    public void visit(SubSelect subSelect) {
        evaluationFailed = true;
    }

    @Override
    public void visit(ExistsExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(AnyComparisonExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(JdbcParameter parameter) {
        evaluationFailed = true;
    }

    @Override
    public void visit(JdbcNamedParameter parameter) {
        evaluationFailed = true;
    }

    @Override
    public void visit(ExtractExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(IntervalExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(OracleHierarchicalExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(RegExpMatchOperator expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(RegExpMySQLOperator expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(JsonExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(JsonOperator expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(UserVariable var) {
        evaluationFailed = true;
    }

    @Override
    public void visit(NumericBind bind) {
        evaluationFailed = true;
    }

    @Override
    public void visit(KeepExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(MySQLGroupConcat groupConcat) {
        evaluationFailed = true;
    }

    @Override
    public void visit(AnalyticExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(OracleHint hint) {
        evaluationFailed = true;
    }

    @Override
    public void visit(TimeKeyExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(DateTimeLiteralExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(RowConstructor expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(OracleNamedFunctionParameter parameter) {
        evaluationFailed = true;
    }

    @Override
    public void visit(CastExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(ConnectByRootOperator expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(XMLSerializeExpr expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(Matches expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(FullTextSearch expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(SimilarToExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(GeometryDistance expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(JsonFunction expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(JsonAggregateFunction expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(TimezoneExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(VariableAssignment expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(ArrayConstructor expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(ArrayExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(CollateExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(NextValExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(RowGetExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(ValueListExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(SafeCastExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(TryCastExpression expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(OverlapsCondition expr) {
        evaluationFailed = true;
    }

    @Override
    public void visit(AllColumns allColumns) {
        evaluationFailed = true;
    }

    @Override
    public void visit(AllTableColumns allTableColumns) {
        evaluationFailed = true;
    }

    // ==================== 核心工具方法 ====================

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compareValues(Object left, Object right) {
        if (left == null || right == null) {
            return Integer.MIN_VALUE;
        }
        if (left instanceof Number && right instanceof Number) {
            return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
        }
        if (left instanceof Comparable && right instanceof Comparable
                && left.getClass().equals(right.getClass())) {
            return ((Comparable) left).compareTo(right);
        }
        return left.toString().compareTo(right.toString());
    }

    private Boolean toBoolean(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return null;
    }

    private double toNumber(Object val) {
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        if (val instanceof String) {
            try {
                return Double.parseDouble((String) val);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private void evalBinaryArithmetic(Expression left, Expression right, char op) {
        left.accept(this);
        Object lVal = result;
        right.accept(this);
        Object rVal = result;

        if (lVal == null || rVal == null) {
            result = null;
            return;
        }

        double l = toNumber(lVal);
        double r = toNumber(rVal);

        switch (op) {
            case '+':
                result = l + r;
                break;
            case '-':
                result = l - r;
                break;
            case '*':
                result = l * r;
                break;
            case '/':
                if (r == 0) {
                    result = null;
                } else {
                    result = l / r;
                }
                break;
            case '%':
                if (r == 0) {
                    result = null;
                } else {
                    result = l % r;
                }
                break;
            default:
                evaluationFailed = true;
        }
    }

    private String likeToRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '%':
                    regex.append(".*");
                    break;
                case '_':
                    regex.append(".");
                    break;
                case '.':
                case '\\':
                case '[':
                case ']':
                case '(':
                case ')':
                case '{':
                case '}':
                case '^':
                case '$':
                case '|':
                case '?':
                case '*':
                case '+':
                    regex.append("\\").append(c);
                    break;
                default:
                    regex.append(c);
                    break;
            }
        }
        return regex.toString();
    }
}
