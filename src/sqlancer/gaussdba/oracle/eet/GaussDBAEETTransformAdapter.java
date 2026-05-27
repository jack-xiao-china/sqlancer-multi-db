package sqlancer.gaussdba.oracle.eet;

import java.util.function.Function;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.common.oracle.eet.EETTransformAdapter;
import sqlancer.common.schema.AbstractTables;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;
import sqlancer.gaussdba.GaussDBAToStringVisitor;
import sqlancer.gaussdba.ast.GaussDBABetweenOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryComparisonOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryComparisonOperation.GaussDBABinaryComparisonOperator;
import sqlancer.gaussdba.ast.GaussDBABinaryLogicalOperation;
import sqlancer.gaussdba.ast.GaussDBABinaryLogicalOperation.GaussDBABinaryLogicalOperator;
import sqlancer.gaussdba.ast.GaussDBACaseWhen;
import sqlancer.gaussdba.ast.GaussDBAColumnReference;
import sqlancer.gaussdba.ast.GaussDBAConstant;
import sqlancer.gaussdba.ast.GaussDBADataType;
import sqlancer.gaussdba.ast.GaussDBAExists;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.ast.GaussDBAInOperation;
import sqlancer.gaussdba.ast.GaussDBAPrintedExpression;
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.ast.GaussDBAUnionSelect;
import sqlancer.gaussdba.ast.GaussDBAMinusSelect;
import sqlancer.gaussdba.ast.GaussDBALikeOperation;
import sqlancer.gaussdba.ast.GaussDBAManuelPredicate;
import sqlancer.gaussdba.ast.GaussDBAText;
import sqlancer.gaussdba.ast.GaussDBACteTableReference;
import sqlancer.gaussdba.ast.GaussDBAWithSelect;
import sqlancer.gaussdba.ast.GaussDBATableReference;
import sqlancer.gaussdba.ast.GaussDBAUnaryPostfixOperation;
import sqlancer.gaussdba.ast.GaussDBAUnaryPostfixOperation.UnaryPostfixOperator;
import sqlancer.gaussdba.ast.GaussDBAUnaryPrefixOperation;
import sqlancer.gaussdba.ast.GaussDBAUnaryPrefixOperation.UnaryPrefixOperator;
import sqlancer.gaussdba.gen.GaussDBAExpressionGenerator;

public class GaussDBAEETTransformAdapter implements EETTransformAdapter<GaussDBAExpression> {

    private final GaussDBAExpressionGenerator gen;
    private final AbstractTables<GaussDBATable, GaussDBAColumn> targetTables;

    public GaussDBAEETTransformAdapter(GaussDBAExpressionGenerator gen,
            AbstractTables<GaussDBATable, GaussDBAColumn> targetTables) {
        this.gen = gen;
        this.targetTables = targetTables;
    }

    // --- Factory methods for AST node creation ---

    @Override
    public GaussDBAExpression createBinaryLogicalOp(GaussDBAExpression left, GaussDBAExpression right,
            BinaryLogicalOperator op) {
        GaussDBABinaryLogicalOperator myOp = op == BinaryLogicalOperator.AND ? GaussDBABinaryLogicalOperator.AND
                : GaussDBABinaryLogicalOperator.OR;
        return new GaussDBABinaryLogicalOperation(left, right, myOp);
    }

    @Override
    public GaussDBAExpression createNot(GaussDBAExpression expr) {
        return new GaussDBAUnaryPrefixOperation(expr, UnaryPrefixOperator.NOT);
    }

    @Override
    public GaussDBAExpression createIsNull(GaussDBAExpression expr) {
        return new GaussDBAUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NULL);
    }

    @Override
    public GaussDBAExpression createIsNotNull(GaussDBAExpression expr) {
        return new GaussDBAUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NOT_NULL);
    }

    @Override
    public GaussDBAExpression createCaseWhen(GaussDBAExpression condition, GaussDBAExpression thenExpr,
            GaussDBAExpression elseExpr) {
        return GaussDBACaseWhen.create(condition, thenExpr, elseExpr);
    }

    @Override
    public GaussDBAExpression createPrintedExpression(GaussDBAExpression expr) {
        return new GaussDBAPrintedExpression(expr);
    }

    @Override
    public GaussDBAExpression createConstantInt(long value) {
        return GaussDBAConstant.createNumberConstant(value);
    }

    @Override
    public GaussDBAExpression createConstantBoolean(boolean value) {
        return GaussDBAConstant.createBooleanConstant(value);
    }

    @Override
    public GaussDBAExpression createComparisonOp(GaussDBAExpression left, GaussDBAExpression right,
            ComparisonOperator op) {
        GaussDBABinaryComparisonOperator myOp;
        switch (op) {
        case EQUALS:
            myOp = GaussDBABinaryComparisonOperator.EQUALS;
            break;
        case NOT_EQUALS:
            myOp = GaussDBABinaryComparisonOperator.NOT_EQUALS;
            break;
        case LESS:
            myOp = GaussDBABinaryComparisonOperator.LESS_THAN;
            break;
        case LESS_EQUALS:
            myOp = GaussDBABinaryComparisonOperator.LESS_EQUALS;
            break;
        case GREATER:
            myOp = GaussDBABinaryComparisonOperator.GREATER_THAN;
            break;
        case GREATER_EQUALS:
            myOp = GaussDBABinaryComparisonOperator.GREATER_EQUALS;
            break;
        default:
            throw new AssertionError("Unknown comparison operator: " + op);
        }
        return new GaussDBABinaryComparisonOperation(left, right, myOp);
    }

    @Override
    public GaussDBAExpression createBetween(GaussDBAExpression expr, GaussDBAExpression low, GaussDBAExpression high) {
        return new GaussDBABetweenOperation(expr, low, high, false);
    }

    @Override
    public GaussDBAExpression createExistsSubquery(GaussDBAExpression subquery) {
        return new GaussDBAExists(subquery);
    }

    @Override
    public GaussDBAExpression createInSubquery(GaussDBAExpression lhs, GaussDBAExpression subquery) {
        return new GaussDBAInOperation(lhs, List.of(subquery), false);
    }

    // --- Type query methods ---

    @Override
    public boolean isBooleanLike(GaussDBAExpression expr) {
        return expr instanceof GaussDBABinaryLogicalOperation || expr instanceof GaussDBABinaryComparisonOperation
                || expr instanceof GaussDBAUnaryPrefixOperation || expr instanceof GaussDBAUnaryPostfixOperation
                || expr instanceof GaussDBAInOperation || expr instanceof GaussDBAExists
                || expr instanceof GaussDBALikeOperation;
    }

    @Override
    public boolean isRule7NoChange(GaussDBAExpression expr) {
        return expr instanceof GaussDBATableReference || expr instanceof GaussDBAText
                || expr instanceof GaussDBACteTableReference || expr instanceof GaussDBAManuelPredicate;
    }

    @Override
    public boolean isQueryLevelNode(GaussDBAExpression expr) {
        return expr instanceof GaussDBAUnionSelect || expr instanceof GaussDBAMinusSelect
                || expr instanceof GaussDBAWithSelect || expr instanceof GaussDBASelect;
    }

    @Override
    public boolean isConstant(GaussDBAExpression expr) {
        return expr instanceof GaussDBAConstant;
    }

    @Override
    public boolean isNullConstant(GaussDBAExpression expr) {
        return expr instanceof GaussDBAConstant && ((GaussDBAConstant) expr).isNull();
    }

    @Override
    public boolean isZeroOrOneInt(GaussDBAExpression expr) {
        if (!(expr instanceof GaussDBAConstant)) {
            return false;
        }
        GaussDBAConstant c = (GaussDBAConstant) expr;
        if (!c.isNumber()) {
            return false;
        }
        long v = c.asNumber();
        return v == 0 || v == 1;
    }

    @Override
    public boolean asBooleanNotNull(GaussDBAExpression expr) {
        if (!(expr instanceof GaussDBAConstant)) {
            return false;
        }
        return ((GaussDBAConstant) expr).asBoolean();
    }

    @Override
    public boolean isBooleanConstant(GaussDBAExpression expr) {
        // GaussDB-A uses NUMBER(1) for boolean, so boolean constants are number constants with 0/1
        // Handled via isZeroOrOneInt instead
        return false;
    }

    @Override
    public boolean isAndOp(GaussDBAExpression expr) {
        return expr instanceof GaussDBABinaryLogicalOperation
                && ((GaussDBABinaryLogicalOperation) expr).getOp() == GaussDBABinaryLogicalOperator.AND;
    }

    @Override
    public boolean isOrOp(GaussDBAExpression expr) {
        return expr instanceof GaussDBABinaryLogicalOperation
                && ((GaussDBABinaryLogicalOperation) expr).getOp() == GaussDBABinaryLogicalOperator.OR;
    }

    @Override
    public boolean isBetween(GaussDBAExpression expr) {
        return expr instanceof GaussDBABetweenOperation;
    }

    @Override
    public boolean isInOperation(GaussDBAExpression expr) {
        return expr instanceof GaussDBAInOperation;
    }

    @Override
    public boolean isExists(GaussDBAExpression expr) {
        return expr instanceof GaussDBAExists;
    }

    @Override
    public GaussDBAExpression[] getBinaryLogicalOpChildren(GaussDBAExpression expr) {
        if (expr instanceof GaussDBABinaryLogicalOperation) {
            GaussDBABinaryLogicalOperation op = (GaussDBABinaryLogicalOperation) expr;
            return new GaussDBAExpression[] { op.getLeft(), op.getRight() };
        }
        return null;
    }

    @Override
    public GaussDBAExpression[] getBetweenChildren(GaussDBAExpression expr) {
        if (expr instanceof GaussDBABetweenOperation) {
            GaussDBABetweenOperation b = (GaussDBABetweenOperation) expr;
            return new GaussDBAExpression[] { b.getExpr(), b.getLeft(), b.getRight() };
        }
        return null;
    }

    // --- Semantic transforms ---

    @Override
    public GaussDBAExpression applyExistsToInTransform(GaussDBAExpression existsExpr) throws IgnoreMeException {
        if (!(existsExpr instanceof GaussDBAExists)) {
            return null;
        }
        GaussDBAExists ex = (GaussDBAExists) existsExpr;
        GaussDBAExpression subquery = ex.getSubquery();
        if (!(subquery instanceof GaussDBASelect)) {
            return null;
        }
        GaussDBASelect select = (GaussDBASelect) subquery;
        if (select.getGroupByExpressions() != null && !select.getGroupByExpressions().isEmpty()) {
            return null;
        }
        GaussDBAExpression predicate = select.getWhereClause();
        if (predicate == null) {
            return null;
        }
        // CASE WHEN (predicate IS NULL) THEN 0 ELSE predicate END
        GaussDBAExpression isNullPred = new GaussDBAUnaryPostfixOperation(predicate, UnaryPostfixOperator.IS_NULL);
        GaussDBAExpression falseVal = GaussDBAConstant.createNumberConstant(0);
        GaussDBAExpression caseExpr = GaussDBACaseWhen.create(isNullPred, falseVal, predicate);
        // Modify subquery: replace select list with CASE, set WHERE to TRUE (1)
        GaussDBASelect modifiedSelect = shallowCopyGaussDBASelect(select);
        modifiedSelect.setFetchColumns(List.of(caseExpr));
        modifiedSelect.setWhereClause(GaussDBAConstant.createNumberConstant(1));
        // TRUE IN (modified_subquery)
        GaussDBAExpression trueVal = GaussDBAConstant.createNumberConstant(1);
        return new GaussDBAInOperation(trueVal, List.of(modifiedSelect), false);
    }

    @Override
    public GaussDBAExpression applyInToExistsTransform(GaussDBAExpression inExpr) throws IgnoreMeException {
        if (!(inExpr instanceof GaussDBAInOperation)) {
            return null;
        }
        GaussDBAInOperation in = (GaussDBAInOperation) inExpr;
        if (in.getListElements().size() != 1) {
            return null;
        }
        GaussDBAExpression subquery = in.getListElements().get(0);
        GaussDBAExpression lhs = in.getExpr();

        // Handle UNION subquery (matching native EET in_query.cc lines 108-139)
        if (subquery instanceof GaussDBAUnionSelect) {
            GaussDBAUnionSelect union = (GaussDBAUnionSelect) subquery;
            List<GaussDBASelect> modifiedSelects = new java.util.ArrayList<>();
            for (GaussDBASelect branch : union.getSelects()) {
                if (branch.getFetchColumns() == null || branch.getFetchColumns().isEmpty()) {
                    return null;
                }
                GaussDBAExpression branchSelected = branch.getFetchColumns().get(0);
                GaussDBAExpression eqExpr = new GaussDBABinaryComparisonOperation(branchSelected, lhs,
                        GaussDBABinaryComparisonOperator.EQUALS);
                GaussDBAExpression branchPredicate = branch.getWhereClause();
                GaussDBAExpression newPredicate;
                if (branchPredicate != null) {
                    newPredicate = new GaussDBABinaryLogicalOperation(eqExpr, branchPredicate,
                            GaussDBABinaryLogicalOperator.AND);
                } else {
                    newPredicate = eqExpr;
                }
                GaussDBASelect modifiedBranch = shallowCopyGaussDBASelect(branch);
                modifiedBranch.setWhereClause(newPredicate);
                modifiedSelects.add(modifiedBranch);
            }
            GaussDBAExpression modifiedUnion = new GaussDBAUnionSelect(modifiedSelects, union.isUnionAll());
            GaussDBAExpression existsExpr = new GaussDBAExists(modifiedUnion);
            GaussDBAExpression originalIn = new GaussDBAInOperation(lhs, List.of(subquery), in.isNegated());
            GaussDBAExpression isNotNull = new GaussDBAUnaryPostfixOperation(originalIn, UnaryPostfixOperator.IS_NOT_NULL);
            GaussDBAExpression nullVal = GaussDBAConstant.createNullConstant();
            return GaussDBACaseWhen.create(isNotNull, existsExpr, nullVal);
        }

        // Handle simple SELECT subquery
        if (!(subquery instanceof GaussDBASelect)) {
            return null;
        }
        GaussDBASelect select = (GaussDBASelect) subquery;
        if (select.getFetchColumns() == null || select.getFetchColumns().isEmpty()) {
            return null;
        }
        GaussDBAExpression selected = select.getFetchColumns().get(0);
        GaussDBAExpression predicate = select.getWhereClause();
        // (selected = lhs) AND predicate
        GaussDBAExpression eqExpr = new GaussDBABinaryComparisonOperation(selected, lhs,
                GaussDBABinaryComparisonOperator.EQUALS);
        GaussDBAExpression newPredicate;
        if (predicate != null) {
            newPredicate = new GaussDBABinaryLogicalOperation(eqExpr, predicate, GaussDBABinaryLogicalOperator.AND);
        } else {
            newPredicate = eqExpr;
        }
        // EXISTS(SELECT ... WHERE newPredicate)
        GaussDBASelect existsSelect = shallowCopyGaussDBASelect(select);
        existsSelect.setWhereClause(newPredicate);
        GaussDBAExpression existsExpr = new GaussDBAExists(existsSelect);
        // CASE WHEN (lhs IN subquery) IS NOT NULL THEN EXISTS_result ELSE NULL END
        GaussDBAExpression originalIn = new GaussDBAInOperation(lhs, List.of(subquery), in.isNegated());
        GaussDBAExpression isNotNull = new GaussDBAUnaryPostfixOperation(originalIn, UnaryPostfixOperator.IS_NOT_NULL);
        GaussDBAExpression nullVal = GaussDBAConstant.createNullConstant();
        return GaussDBACaseWhen.create(isNotNull, existsExpr, nullVal);
    }

    @Override
    public boolean isIntersect(GaussDBAExpression expr) {
        return false; // GaussDB-A Oracle mode doesn't support INTERSECT keyword
    }

    @Override
    public boolean isExcept(GaussDBAExpression expr) {
        return expr instanceof GaussDBAMinusSelect;
    }

    @Override
    public GaussDBAExpression applyIntersectToExistsTransform(GaussDBAExpression expr) throws IgnoreMeException {
        return null; // GaussDB-A Oracle mode doesn't support INTERSECT keyword
    }

    @Override
    public GaussDBAExpression applyExceptToNotExistsTransform(GaussDBAExpression expr) throws IgnoreMeException {
        if (!(expr instanceof GaussDBAMinusSelect)) {
            return null;
        }
        GaussDBAMinusSelect minus = (GaussDBAMinusSelect) expr;
        List<GaussDBASelect> selects = minus.getSelects();
        if (selects.size() < 2) {
            return null;
        }
        GaussDBASelect q1 = selects.get(0);
        GaussDBASelect q2 = selects.get(1);
        if ((q1.getGroupByExpressions() != null && !q1.getGroupByExpressions().isEmpty())
                || (q2.getGroupByExpressions() != null && !q2.getGroupByExpressions().isEmpty())) {
            return null;
        }
        List<GaussDBAExpression> q1Columns = q1.getFetchColumns();
        List<GaussDBAExpression> q2Columns = q2.getFetchColumns();
        if (q1Columns == null || q2Columns == null || q1Columns.size() != q2Columns.size() || q1Columns.isEmpty()) {
            return null;
        }
        // Build NULL-safe column equality: (q1.col = q2.col) OR (q1.col IS NULL AND q2.col IS NULL)
        GaussDBAExpression columnEquality = buildNullSafeColumnEquality(q1Columns, q2Columns);
        if (columnEquality == null) {
            return null;
        }
        // Q2 WHERE: columnEquality AND Q2.original_predicate
        GaussDBAExpression q2Predicate = q2.getWhereClause();
        GaussDBAExpression newQ2Predicate;
        if (q2Predicate != null) {
            newQ2Predicate = new GaussDBABinaryLogicalOperation(columnEquality, q2Predicate,
                    GaussDBABinaryLogicalOperator.AND);
        } else {
            newQ2Predicate = columnEquality;
        }
        GaussDBASelect modifiedQ2 = shallowCopyGaussDBASelect(q2);
        modifiedQ2.setWhereClause(newQ2Predicate);
        // NOT EXISTS(Q2)
        GaussDBAExpression existsExpr = new GaussDBAExists(modifiedQ2);
        GaussDBAExpression notExistsExpr = new GaussDBAUnaryPrefixOperation(existsExpr, UnaryPrefixOperator.NOT);
        // Q1 WHERE: NOT EXISTS AND Q1.original_predicate
        GaussDBAExpression q1Predicate = q1.getWhereClause();
        GaussDBAExpression newQ1Predicate;
        if (q1Predicate != null) {
            newQ1Predicate = new GaussDBABinaryLogicalOperation(notExistsExpr, q1Predicate,
                    GaussDBABinaryLogicalOperator.AND);
        } else {
            newQ1Predicate = notExistsExpr;
        }
        GaussDBASelect resultQ1 = shallowCopyGaussDBASelect(q1);
        resultQ1.setWhereClause(newQ1Predicate);
        return resultQ1;
    }

    private GaussDBAExpression buildNullSafeColumnEquality(List<GaussDBAExpression> q1Columns,
            List<GaussDBAExpression> q2Columns) {
        GaussDBAExpression combined = null;
        for (int i = 0; i < q1Columns.size(); i++) {
            GaussDBAExpression q1Col = q1Columns.get(i);
            GaussDBAExpression q2Col = q2Columns.get(i);
            GaussDBAExpression equality = new GaussDBABinaryComparisonOperation(q1Col, q2Col,
                    GaussDBABinaryComparisonOperator.EQUALS);
            GaussDBAExpression q1IsNull = new GaussDBAUnaryPostfixOperation(q1Col, UnaryPostfixOperator.IS_NULL);
            GaussDBAExpression q2IsNull = new GaussDBAUnaryPostfixOperation(q2Col, UnaryPostfixOperator.IS_NULL);
            GaussDBAExpression bothNull = new GaussDBABinaryLogicalOperation(q1IsNull, q2IsNull,
                    GaussDBABinaryLogicalOperator.AND);
            GaussDBAExpression nullSafeEq = new GaussDBABinaryLogicalOperation(equality, bothNull,
                    GaussDBABinaryLogicalOperator.OR);
            combined = combined == null ? nullSafeEq : new GaussDBABinaryLogicalOperation(combined, nullSafeEq,
                    GaussDBABinaryLogicalOperator.AND);
        }
        return combined;
    }

    private static GaussDBASelect shallowCopyGaussDBASelect(GaussDBASelect s) {
        GaussDBASelect copy = new GaussDBASelect();
        copy.setSelectType(s.getSelectType());
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

    // --- Expression generation delegation ---

    @Override
    public GaussDBAExpression generateBooleanExpression() {
        return gen.generateBooleanExpression();
    }

    @Override
    public GaussDBAExpression generateExpression() {
        return gen.generateExpression(0);
    }

    @Override
    public GaussDBAExpression generateExpressionWithType(Object type) {
        if (type instanceof GaussDBADataType) {
            return gen.generateExpressionWithExpectedResult((GaussDBADataType) type);
        }
        return gen.generateExpression(0);
    }

    @Override
    public GaussDBAExpression generateSameTypeExpression(GaussDBAExpression expr) {
        GaussDBADataType t = inferDataType(expr);
        if (t == null) {
            return gen.generateExpression(0);
        }
        for (int i = 0; i < 200; i++) {
            try {
                GaussDBAExpression e = gen.generateExpression(0);
                GaussDBADataType t2 = inferDataType(e);
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
    public GaussDBAExpression sameTypeFallback(Object type) {
        GaussDBADataType t = (GaussDBADataType) type;
        switch (t) {
        case NUMBER:
            return GaussDBAConstant.createNumberConstant(0);
        case VARCHAR2:
            return GaussDBAConstant.createVarchar2Constant("eet");
        case DATE:
            return GaussDBAConstant.createDateConstant(java.time.LocalDate.of(2000, 1, 1));
        case TIMESTAMP:
            return GaussDBAConstant.createTimestampConstant(java.time.LocalDateTime.of(2000, 1, 1, 0, 0));
        default:
            throw new IgnoreMeException();
        }
    }

    @Override
    public GaussDBAExpression mapChildren(Function<GaussDBAExpression, GaussDBAExpression> transformFn,
            GaussDBAExpression expr) {
        return GaussDBAEETExpressionTree.mapChildren(transformFn, expr);
    }

    @Override
    public String asString(GaussDBAExpression expr) {
        return GaussDBAToStringVisitor.asString(expr);
    }

    @Override
    public AbstractTables<GaussDBATable, GaussDBAColumn> getTargetTables() {
        return targetTables;
    }

    @Override
    public Object getExpressionType(GaussDBAExpression expr) {
        return expr.getExpressionType();
    }

    @Override
    public String getExpressionTypeName(GaussDBAExpression expr) {
        return expr.getClass().getSimpleName();
    }

    @Override
    public boolean supportsRule(String ruleName) {
        switch (ruleName) {
        case "IntersectToExists":
            return false; // GaussDB-A Oracle mode doesn't support INTERSECT keyword
        case "ExceptToNotExists":
            return true; // MINUS is GaussDB-A's EXCEPT equivalent
        default:
            return true;
        }
    }

    private static GaussDBADataType inferDataType(GaussDBAExpression expr) {
        try {
            if (expr instanceof GaussDBAColumnReference) {
                return ((GaussDBAColumnReference) expr).getColumn().getType();
            }
            return expr.getExpressionType();
        } catch (Exception e) {
            return null;
        }
    }

    // -- COALESCE/NULLIF stub implementations --

    @Override
    public GaussDBAExpression createNullLiteral() {
        return GaussDBAConstant.createNullConstant();
    }

    @Override
    public GaussDBAExpression createCoalesce(GaussDBAExpression[] args) {
        // COALESCE(a,b) → CASE WHEN a IS NOT NULL THEN a ELSE b END
        if (args.length == 2) {
            GaussDBAExpression isNotNull = createIsNullCheck(args[0], true);
            return createCaseWhen(isNotNull, args[0], args[1]);
        }
        // For more arguments, recursive CASE
        GaussDBAExpression isNotNull = createIsNullCheck(args[0], true);
        GaussDBAExpression[] remaining = new GaussDBAExpression[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            remaining[i - 1] = args[i];
        }
        GaussDBAExpression remainingCoalesce = createCoalesce(remaining);
        return createCaseWhen(isNotNull, args[0], remainingCoalesce);
    }

    @Override
    public GaussDBAExpression createIsNullCheck(GaussDBAExpression expr, boolean isNotNull) {
        if (isNotNull) {
            return new GaussDBAUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NOT_NULL);
        } else {
            return new GaussDBAUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NULL);
        }
    }

    @Override
    public boolean isCoalesce(GaussDBAExpression expr) {
        return false; // Stub
    }

    @Override
    public boolean isNullif(GaussDBAExpression expr) {
        return false; // Stub
    }

    @Override
    public GaussDBAExpression[] getCoalesceArguments(GaussDBAExpression expr) {
        return null;
    }

    @Override
    public GaussDBAExpression[] getNullifArguments(GaussDBAExpression expr) {
        return null;
    }
}