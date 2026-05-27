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
                || expr instanceof GaussDBAInOperation || expr instanceof GaussDBAExists;
    }

    @Override
    public boolean isRule7NoChange(GaussDBAExpression expr) {
        return expr instanceof GaussDBATableReference;
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
        if (!(subquery instanceof GaussDBASelect)) {
            return null;
        }
        GaussDBASelect select = (GaussDBASelect) subquery;
        if (select.getFetchColumns() == null || select.getFetchColumns().isEmpty()) {
            return null;
        }
        GaussDBAExpression selected = select.getFetchColumns().get(0);
        GaussDBAExpression lhs = in.getExpr();
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
        return false; // GaussDB-A Oracle mode uses INTERSECT but no AST node yet
    }

    @Override
    public boolean isExcept(GaussDBAExpression expr) {
        return false; // GaussDB-A uses MINUS, no AST node yet
    }

    @Override
    public GaussDBAExpression applyIntersectToExistsTransform(GaussDBAExpression expr) throws IgnoreMeException {
        return null; // Not yet supported (no INTERSECT AST node)
    }

    @Override
    public GaussDBAExpression applyExceptToNotExistsTransform(GaussDBAExpression expr) throws IgnoreMeException {
        return null; // Not yet supported (no MINUS AST node)
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
        case "ExceptToNotExists":
            return false; // No INTERSECT/MINUS AST nodes yet
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
        return null; // Stub
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