package sqlancer.mysql.oracle.eet;


import sqlancer.IgnoreMeException;
import java.util.function.Function;

import sqlancer.common.oracle.eet.EETTransformAdapter;
import sqlancer.common.schema.AbstractTables;
import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLDataType;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.MySQLVisitor;
import sqlancer.mysql.ast.MySQLBinaryComparisonOperation;
import sqlancer.mysql.ast.MySQLBinaryComparisonOperation.BinaryComparisonOperator;
import sqlancer.mysql.ast.MySQLBinaryLogicalOperation;
import sqlancer.mysql.ast.MySQLBinaryLogicalOperation.MySQLBinaryLogicalOperator;
import sqlancer.mysql.ast.MySQLAnyAllSubquery;
import sqlancer.mysql.ast.MySQLBetweenOperation;
import sqlancer.mysql.ast.MySQLCaseOperator;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLConstant;
import sqlancer.mysql.ast.MySQLExists;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLInOperation;
import sqlancer.mysql.ast.MySQLPrintedExpression;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.ast.MySQLText;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLComputableFunction;
import sqlancer.mysql.ast.MySQLUnaryPostfixOperation;
import sqlancer.mysql.ast.MySQLUnaryPostfixOperation.UnaryPostfixOperator;
import sqlancer.mysql.ast.MySQLUnaryPrefixOperation;
import sqlancer.mysql.ast.MySQLUnaryPrefixOperation.MySQLUnaryPrefixOperator;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

public class MySQLEETTransformAdapter implements EETTransformAdapter<MySQLExpression> {

    private final MySQLExpressionGenerator gen;
    private final AbstractTables<MySQLTable, MySQLColumn> targetTables;

    public MySQLEETTransformAdapter(MySQLExpressionGenerator gen, AbstractTables<MySQLTable, MySQLColumn> targetTables) {
        this.gen = gen;
        this.targetTables = targetTables;
    }

    @Override
    public MySQLExpression createBinaryLogicalOp(MySQLExpression left, MySQLExpression right, BinaryLogicalOperator op) {
        MySQLBinaryLogicalOperator myOp = op == BinaryLogicalOperator.AND ? MySQLBinaryLogicalOperator.AND
                : MySQLBinaryLogicalOperator.OR;
        return new MySQLBinaryLogicalOperation(left, right, myOp);
    }

    @Override
    public MySQLExpression createNot(MySQLExpression expr) {
        return new MySQLUnaryPrefixOperation(expr, MySQLUnaryPrefixOperator.NOT);
    }

    @Override
    public MySQLExpression createIsNull(MySQLExpression expr) {
        return new MySQLUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NULL, false);
    }

    @Override
    public MySQLExpression createIsNotNull(MySQLExpression expr) {
        return new MySQLUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NULL, true);
    }

    @Override
    public MySQLExpression createCaseWhen(MySQLExpression condition, MySQLExpression thenExpr, MySQLExpression elseExpr) {
        return new MySQLCaseOperator(null, java.util.List.of(condition), java.util.List.of(thenExpr), elseExpr);
    }

    @Override
    public MySQLExpression createPrintedExpression(MySQLExpression expr) {
        return new MySQLPrintedExpression(expr);
    }

    @Override
    public MySQLExpression createConstantInt(long value) {
        return MySQLConstant.createIntConstant(value);
    }

    @Override
    public MySQLExpression createConstantBoolean(boolean value) {
        return MySQLConstant.createIntConstant(value ? 1 : 0);
    }

    @Override
    public MySQLExpression createComparisonOp(MySQLExpression left, MySQLExpression right, ComparisonOperator op) {
        BinaryComparisonOperator myOp;
        switch (op) {
        case EQUALS:
            myOp = BinaryComparisonOperator.EQUALS;
            break;
        case NOT_EQUALS:
            myOp = BinaryComparisonOperator.NOT_EQUALS;
            break;
        case LESS:
            myOp = BinaryComparisonOperator.LESS;
            break;
        case LESS_EQUALS:
            myOp = BinaryComparisonOperator.LESS_EQUALS;
            break;
        case GREATER:
            myOp = BinaryComparisonOperator.GREATER;
            break;
        case GREATER_EQUALS:
            myOp = BinaryComparisonOperator.GREATER_EQUALS;
            break;
        default:
            throw new AssertionError("Unknown comparison operator: " + op);
        }
        return new MySQLBinaryComparisonOperation(left, right, myOp);
    }

    @Override
    public MySQLExpression createBetween(MySQLExpression expr, MySQLExpression low, MySQLExpression high) {
        return new MySQLBetweenOperation(expr, low, high);
    }

    @Override
    public MySQLExpression createExistsSubquery(MySQLExpression subquery) {
        return new MySQLExists(subquery);
    }

    @Override
    public MySQLExpression createInSubquery(MySQLExpression lhs, MySQLExpression subquery) {
        return new MySQLInOperation(lhs, java.util.List.of(subquery), true);
    }

    @Override
    public boolean isBooleanLike(MySQLExpression expr) {
        return expr instanceof MySQLBinaryLogicalOperation || expr instanceof MySQLBinaryComparisonOperation
                || expr instanceof MySQLUnaryPrefixOperation || expr instanceof MySQLUnaryPostfixOperation
                || expr instanceof MySQLInOperation || expr instanceof MySQLExists
                || expr instanceof MySQLAnyAllSubquery;
    }

    @Override
    public boolean isRule7NoChange(MySQLExpression expr) {
        return expr instanceof MySQLTableReference || expr instanceof MySQLText;
    }

    @Override
    public boolean isConstant(MySQLExpression expr) {
        return expr instanceof MySQLConstant;
    }

    @Override
    public boolean isNullConstant(MySQLExpression expr) {
        return expr instanceof MySQLConstant && ((MySQLConstant) expr).isNull();
    }

    @Override
    public boolean isZeroOrOneInt(MySQLExpression expr) {
        if (!(expr instanceof MySQLConstant)) {
            return false;
        }
        MySQLConstant c = (MySQLConstant) expr;
        if (!c.isInt()) {
            return false;
        }
        long v = c.getInt();
        return v == 0 || v == 1;
    }

    @Override
    public boolean asBooleanNotNull(MySQLExpression expr) {
        return expr instanceof MySQLConstant && ((MySQLConstant) expr).asBooleanNotNull();
    }

    @Override
    public boolean isBooleanConstant(MySQLExpression expr) {
        // MySQL doesn't have separate TRUE/FALSE constants — they're int 1/0
        // So this always returns false; const_bool handles them via isZeroOrOneInt
        return false;
    }

    @Override
    public boolean isAndOp(MySQLExpression expr) {
        return expr instanceof MySQLBinaryLogicalOperation
                && ((MySQLBinaryLogicalOperation) expr).getOp() == MySQLBinaryLogicalOperator.AND;
    }

    @Override
    public boolean isOrOp(MySQLExpression expr) {
        return expr instanceof MySQLBinaryLogicalOperation
                && ((MySQLBinaryLogicalOperation) expr).getOp() == MySQLBinaryLogicalOperator.OR;
    }

    @Override
    public boolean isBetween(MySQLExpression expr) {
        return expr instanceof MySQLBetweenOperation;
    }

    @Override
    public boolean isInOperation(MySQLExpression expr) {
        return expr instanceof MySQLInOperation;
    }

    @Override
    public boolean isExists(MySQLExpression expr) {
        return expr instanceof MySQLExists;
    }

    @Override
    public MySQLExpression[] getBinaryLogicalOpChildren(MySQLExpression expr) {
        if (expr instanceof MySQLBinaryLogicalOperation) {
            MySQLBinaryLogicalOperation op = (MySQLBinaryLogicalOperation) expr;
            return new MySQLExpression[] { op.getLeft(), op.getRight() };
        }
        return null;
    }

    @Override
    public MySQLExpression[] getBetweenChildren(MySQLExpression expr) {
        if (expr instanceof MySQLBetweenOperation) {
            MySQLBetweenOperation b = (MySQLBetweenOperation) expr;
            return new MySQLExpression[] { b.getExpr(), b.getLeft(), b.getRight() };
        }
        return null;
    }

    @Override
    public MySQLExpression applyExistsToInTransform(MySQLExpression existsExpr) throws IgnoreMeException {
        if (!(existsExpr instanceof MySQLExists)) {
            return null;
        }
        MySQLExists ex = (MySQLExists) existsExpr;
        MySQLExpression subquery = ex.getExpr();
        // Only transform simple SELECT (not unioned query)
        if (!(subquery instanceof MySQLSelect)) {
            return null;
        }
        MySQLSelect select = (MySQLSelect) subquery;
        // Skip if GROUP BY is present (EET native exclusion)
        if (select.getGroupByExpressions() != null && !select.getGroupByExpressions().isEmpty()) {
            return null;
        }
        MySQLExpression predicate = select.getWhereClause();
        if (predicate == null) {
            return null;
        }
        // Construct: CASE WHEN (predicate IS NULL) THEN FALSE ELSE predicate END
        MySQLExpression isNullPred = new MySQLUnaryPostfixOperation(predicate, UnaryPostfixOperator.IS_NULL, false);
        MySQLExpression falseVal = MySQLConstant.createIntConstant(0);
        MySQLExpression caseExpr = new MySQLCaseOperator(null, java.util.List.of(isNullPred),
                java.util.List.of(falseVal), predicate);
        // Modify the subquery: replace select list with CASE, set WHERE to TRUE
        MySQLSelect modifiedSelect = shallowCopyMySQLSelect(select);
        modifiedSelect.setFetchColumns(java.util.List.of(caseExpr));
        modifiedSelect.setWhereClause(MySQLConstant.createIntConstant(1)); // WHERE TRUE
        // Construct: TRUE IN (modified_subquery)
        MySQLExpression trueVal = MySQLConstant.createIntConstant(1);
        return new MySQLInOperation(trueVal, java.util.List.of(modifiedSelect), true);
    }

    @Override
    public MySQLExpression applyInToExistsTransform(MySQLExpression inExpr) throws IgnoreMeException {
        if (!(inExpr instanceof MySQLInOperation)) {
            return null;
        }
        MySQLInOperation in = (MySQLInOperation) inExpr;
        // Only transform IN with a single subquery element
        if (in.getListElements().size() != 1) {
            return null;
        }
        MySQLExpression subquery = in.getListElements().get(0);
        if (!(subquery instanceof MySQLSelect)) {
            return null;
        }
        MySQLSelect select = (MySQLSelect) subquery;
        // Need at least one column in select list to use as the comparison target
        if (select.getFetchColumns() == null || select.getFetchColumns().isEmpty()) {
            return null;
        }
        MySQLExpression selected = select.getFetchColumns().get(0);
        MySQLExpression lhs = in.getExpr();
        MySQLExpression predicate = select.getWhereClause();

        // Construct: (selected = lhs) AND predicate
        MySQLExpression eqExpr = new MySQLBinaryComparisonOperation(selected, lhs, BinaryComparisonOperator.EQUALS);
        MySQLExpression newPredicate;
        if (predicate != null) {
            newPredicate = new MySQLBinaryLogicalOperation(eqExpr, predicate, MySQLBinaryLogicalOperator.AND);
        } else {
            newPredicate = eqExpr;
        }

        // Construct EXISTS(SELECT ... WHERE newPredicate)
        MySQLSelect existsSelect = shallowCopyMySQLSelect(select);
        existsSelect.setWhereClause(newPredicate);
        MySQLExpression existsExpr = new MySQLExists(existsSelect);

        // Construct: CASE WHEN (lhs IN subquery) IS NOT NULL THEN EXISTS_result ELSE NULL END
        MySQLExpression originalIn = new MySQLInOperation(lhs, java.util.List.of(subquery), in.isTrue());
        MySQLExpression isNotNull = new MySQLUnaryPostfixOperation(originalIn, UnaryPostfixOperator.IS_NULL, true);
        MySQLExpression nullVal = MySQLConstant.createNullConstant();
        return new MySQLCaseOperator(null, java.util.List.of(isNotNull), java.util.List.of(existsExpr), nullVal);
    }

    @Override
    public boolean isIntersect(MySQLExpression expr) {
        return false; // MySQL does not support INTERSECT
    }

    @Override
    public boolean isExcept(MySQLExpression expr) {
        return false; // MySQL does not support EXCEPT
    }

    @Override
    public MySQLExpression applyIntersectToExistsTransform(MySQLExpression expr) throws IgnoreMeException {
        return null; // Not supported in MySQL
    }

    @Override
    public MySQLExpression applyExceptToNotExistsTransform(MySQLExpression expr) throws IgnoreMeException {
        return null; // Not supported in MySQL
    }

    private static MySQLSelect shallowCopyMySQLSelect(MySQLSelect s) {
        MySQLSelect copy = new MySQLSelect();
        copy.setFromOptions(s.getFromOptions());
        copy.setModifiers(s.getModifiers());
        copy.setHint(s.getHint());
        copy.setFetchColumns(new java.util.ArrayList<>(s.getFetchColumns()));
        copy.setFromList(new java.util.ArrayList<>(s.getFromList()));
        copy.setJoinList(new java.util.ArrayList<>(s.getJoinList()));
        copy.setWhereClause(s.getWhereClause());
        copy.setGroupByExpressions(new java.util.ArrayList<>(s.getGroupByExpressions()));
        copy.setHavingClause(s.getHavingClause());
        copy.setOrderByClauses(new java.util.ArrayList<>(s.getOrderByClauses()));
        copy.setLimitClause(s.getLimitClause());
        copy.setOffsetClause(s.getOffsetClause());
        return copy;
    }

    @Override
    public Object getExpressionType(MySQLExpression expr) {
        return inferDataType(expr);
    }

    @Override
    public String getExpressionTypeName(MySQLExpression expr) {
        return expr.getClass().getSimpleName();
    }

    @Override
    public boolean supportsRule(String ruleName) {
        switch (ruleName) {
        case "IntersectToExists":
        case "ExceptToNotExists":
            return false;
        default:
            return true;
        }
    }

    @Override
    public MySQLExpression generateBooleanExpression() {
        return gen.generateBooleanExpression();
    }

    @Override
    public MySQLExpression generateExpression() {
        return gen.generateExpression();
    }

    @Override
    public MySQLExpression generateExpressionWithType(Object type) {
        return gen.generateExpression();
    }

    @Override
    public MySQLExpression generateSameTypeExpression(MySQLExpression expr) {
        MySQLDataType t = inferDataType(expr);
        if (t == null) {
            return gen.generateExpression();
        }
        for (int i = 0; i < 200; i++) {
            try {
                MySQLExpression e = gen.generateExpression();
                MySQLDataType t2 = inferDataType(e);
                if (t2 != null && t2 == t) {
                    return e;
                }
            } catch (IgnoreMeException e) {
                // retry
            }
        }
        return sameTypeFallback(t);
    }

    @Override
    public MySQLExpression sameTypeFallback(Object type) {
        MySQLDataType t = (MySQLDataType) type;
        switch (t) {
        case INT:
            return MySQLConstant.createIntConstant(0);
        case VARCHAR:
            return MySQLConstant.createStringConstant("eet");
        case FLOAT:
        case DOUBLE:
            return new MySQLConstant.MySQLDoubleConstant(0.0);
        case DECIMAL:
            return MySQLConstant.createIntConstant(0);
        case DATE:
            return new MySQLConstant.MySQLDateConstant(2000, 1, 1);
        case TIME:
            return new MySQLConstant.MySQLTimeConstant(0, 0, 0, 0, 0);
        case DATETIME:
            return new MySQLConstant.MySQLDateTimeConstant(2000, 1, 1, 0, 0, 0, 0, 0);
        case TIMESTAMP:
            return new MySQLConstant.MySQLTimestampConstant(2000, 1, 1, 0, 0, 0, 0, 0);
        case YEAR:
            return new MySQLConstant.MySQLYearConstant(2000);
        case BIT:
            return MySQLConstant.createBitConstant(0, 1);
        case ENUM:
            return MySQLConstant.createEnumConstant("e0", 0);
        case SET:
            return MySQLConstant.createSetConstant(java.util.Collections.singleton("s0"), 1);
        case JSON:
            return MySQLConstant.createJSONConstant("{}");
        case BINARY:
        case VARBINARY:
        case BLOB:
            return MySQLConstant.createBinaryConstant(new byte[]{0});
        default:
            throw new IgnoreMeException();
        }
    }

    @Override
    public MySQLExpression mapChildren(Function<MySQLExpression, MySQLExpression> transformFn, MySQLExpression expr) {
        return MySQLEETExpressionTree.mapChildren(transformFn, expr);
    }

    @Override
    public String asString(MySQLExpression expr) {
        return MySQLVisitor.asString(expr);
    }

    @Override
    public AbstractTables<MySQLTable, MySQLColumn> getTargetTables() {
        return targetTables;
    }

    private static MySQLDataType inferDataType(MySQLExpression expr) {
        try {
            if (expr instanceof MySQLColumnReference) {
                return ((MySQLColumnReference) expr).getColumn().getType();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    // -- COALESCE/NULLIF stub implementations --

    @Override
    public MySQLExpression createNullLiteral() {
        return MySQLConstant.createNullConstant();
    }

    @Override
    public MySQLExpression createCoalesce(MySQLExpression[] args) {
        // COALESCE(a,b) → CASE WHEN a IS NOT NULL THEN a ELSE b END
        if (args.length == 2) {
            MySQLExpression isNotNull = createIsNullCheck(args[0], true);
            return createCaseWhen(isNotNull, args[0], args[1]);
        }
        // For more arguments, recursive CASE
        MySQLExpression isNotNull = createIsNullCheck(args[0], true);
        MySQLExpression[] remaining = new MySQLExpression[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            remaining[i - 1] = args[i];
        }
        MySQLExpression remainingCoalesce = createCoalesce(remaining);
        return createCaseWhen(isNotNull, args[0], remainingCoalesce);
    }

    @Override
    public MySQLExpression createIsNullCheck(MySQLExpression expr, boolean isNotNull) {
        if (isNotNull) {
            return new MySQLUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NULL, true); // negate=true for IS NOT NULL
        } else {
            return new MySQLUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NULL, false);
        }
    }

    @Override
    public boolean isCoalesce(MySQLExpression expr) {
        // COALESCE is represented as MySQLComputableFunction in MySQL
        if (expr instanceof MySQLComputableFunction) {
            MySQLComputableFunction func = (MySQLComputableFunction) expr;
            return func.getFunction() == MySQLComputableFunction.MySQLFunction.COALESCE;
        }
        return false;
    }

    @Override
    public boolean isNullif(MySQLExpression expr) {
        // NULLIF is not a separate AST node in MySQL; stub returns false
        return false;
    }

    @Override
    public MySQLExpression[] getCoalesceArguments(MySQLExpression expr) {
        if (expr instanceof MySQLComputableFunction) {
            MySQLComputableFunction func = (MySQLComputableFunction) expr;
            if (func.getFunction() == MySQLComputableFunction.MySQLFunction.COALESCE) {
                return func.getArguments();
            }
        }
        return null;
    }

    @Override
    public MySQLExpression[] getNullifArguments(MySQLExpression expr) {
        // NULLIF not supported in MySQL AST; return null
        return null;
    }
}