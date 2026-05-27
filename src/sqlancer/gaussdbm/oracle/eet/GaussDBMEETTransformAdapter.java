package sqlancer.gaussdbm.oracle.eet;

import java.util.function.Function;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.common.oracle.eet.EETTransformAdapter;
import sqlancer.common.schema.AbstractTables;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBDataType;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBBetweenOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryComparisonOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryComparisonOperation.BinaryComparisonOperator;
import sqlancer.gaussdbm.ast.GaussDBBinaryLogicalOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryLogicalOperation.GaussDBBinaryLogicalOperator;
import sqlancer.gaussdbm.ast.GaussDBCaseWhen;
import sqlancer.gaussdbm.ast.GaussDBColumnReference;
import sqlancer.gaussdbm.ast.GaussDBConstant;
import sqlancer.gaussdbm.ast.GaussDBExists;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBInOperation;
import sqlancer.gaussdbm.ast.GaussDBPrintedExpression;
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.ast.GaussDBText;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation.UnaryPostfixOperator;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation.UnaryPrefixOperator;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

public class GaussDBMEETTransformAdapter implements EETTransformAdapter<GaussDBExpression> {

    private final GaussDBMExpressionGenerator gen;
    private final AbstractTables<GaussDBTable, GaussDBColumn> targetTables;

    public GaussDBMEETTransformAdapter(GaussDBMExpressionGenerator gen,
            AbstractTables<GaussDBTable, GaussDBColumn> targetTables) {
        this.gen = gen;
        this.targetTables = targetTables;
    }

    // --- Factory methods for AST node creation ---

    @Override
    public GaussDBExpression createBinaryLogicalOp(GaussDBExpression left, GaussDBExpression right,
            BinaryLogicalOperator op) {
        GaussDBBinaryLogicalOperator myOp = op == BinaryLogicalOperator.AND ? GaussDBBinaryLogicalOperator.AND
                : GaussDBBinaryLogicalOperator.OR;
        return new GaussDBBinaryLogicalOperation(left, right, myOp);
    }

    @Override
    public GaussDBExpression createNot(GaussDBExpression expr) {
        return new GaussDBUnaryPrefixOperation(expr, UnaryPrefixOperator.NOT);
    }

    @Override
    public GaussDBExpression createIsNull(GaussDBExpression expr) {
        return new GaussDBUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NULL);
    }

    @Override
    public GaussDBExpression createIsNotNull(GaussDBExpression expr) {
        return new GaussDBUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NOT_NULL);
    }

    @Override
    public GaussDBExpression createCaseWhen(GaussDBExpression condition, GaussDBExpression thenExpr,
            GaussDBExpression elseExpr) {
        return new GaussDBCaseWhen(condition, thenExpr, elseExpr);
    }

    @Override
    public GaussDBExpression createPrintedExpression(GaussDBExpression expr) {
        return new GaussDBPrintedExpression(expr);
    }

    @Override
    public GaussDBExpression createConstantInt(long value) {
        return GaussDBConstant.createIntConstant(value);
    }

    @Override
    public GaussDBExpression createConstantBoolean(boolean value) {
        return GaussDBConstant.createBooleanConstant(value);
    }

    @Override
    public GaussDBExpression createComparisonOp(GaussDBExpression left, GaussDBExpression right,
            ComparisonOperator op) {
        BinaryComparisonOperator myOp;
        switch (op) {
        case EQUALS:
            myOp = BinaryComparisonOperator.EQUALS;
            break;
        case NOT_EQUALS:
            myOp = BinaryComparisonOperator.NOT_EQUALS;
            break;
        case LESS:
            myOp = BinaryComparisonOperator.SMALLER;
            break;
        case LESS_EQUALS:
            myOp = BinaryComparisonOperator.SMALLER_EQUALS;
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
        return new GaussDBBinaryComparisonOperation(left, right, myOp);
    }

    @Override
    public GaussDBExpression createBetween(GaussDBExpression expr, GaussDBExpression low, GaussDBExpression high) {
        return new GaussDBBetweenOperation(expr, low, high, false);
    }

    @Override
    public GaussDBExpression createExistsSubquery(GaussDBExpression subquery) {
        return new GaussDBExists(subquery);
    }

    @Override
    public GaussDBExpression createInSubquery(GaussDBExpression lhs, GaussDBExpression subquery) {
        return new GaussDBInOperation(lhs, List.of(subquery), true);
    }

    // --- Type query methods ---

    @Override
    public boolean isBooleanLike(GaussDBExpression expr) {
        return expr instanceof GaussDBBinaryLogicalOperation || expr instanceof GaussDBBinaryComparisonOperation
                || expr instanceof GaussDBUnaryPrefixOperation || expr instanceof GaussDBUnaryPostfixOperation
                || expr instanceof GaussDBInOperation || expr instanceof GaussDBExists;
    }

    @Override
    public boolean isRule7NoChange(GaussDBExpression expr) {
        return expr instanceof GaussDBTableReference || expr instanceof GaussDBText;
    }

    @Override
    public boolean isConstant(GaussDBExpression expr) {
        return expr instanceof GaussDBConstant;
    }

    @Override
    public boolean isNullConstant(GaussDBExpression expr) {
        return expr instanceof GaussDBConstant && ((GaussDBConstant) expr).isNull();
    }

    @Override
    public boolean isZeroOrOneInt(GaussDBExpression expr) {
        if (!(expr instanceof GaussDBConstant)) {
            return false;
        }
        GaussDBConstant c = (GaussDBConstant) expr;
        if (!c.isInt()) {
            return false;
        }
        long v = c.asIntNotNull();
        return v == 0 || v == 1;
    }

    @Override
    public boolean asBooleanNotNull(GaussDBExpression expr) {
        if (!(expr instanceof GaussDBConstant)) {
            return false;
        }
        return ((GaussDBConstant) expr).asBooleanNotNull();
    }

    @Override
    public boolean isBooleanConstant(GaussDBExpression expr) {
        // GaussDB-M uses MySQL-style boolean (TRUE/FALSE), handled by ConstBool rule
        return expr instanceof GaussDBConstant && ((GaussDBConstant) expr).isBoolean();
    }

    @Override
    public boolean isAndOp(GaussDBExpression expr) {
        return expr instanceof GaussDBBinaryLogicalOperation
                && ((GaussDBBinaryLogicalOperation) expr).getOp() == GaussDBBinaryLogicalOperator.AND;
    }

    @Override
    public boolean isOrOp(GaussDBExpression expr) {
        return expr instanceof GaussDBBinaryLogicalOperation
                && ((GaussDBBinaryLogicalOperation) expr).getOp() == GaussDBBinaryLogicalOperator.OR;
    }

    @Override
    public boolean isBetween(GaussDBExpression expr) {
        return expr instanceof GaussDBBetweenOperation;
    }

    @Override
    public boolean isInOperation(GaussDBExpression expr) {
        return expr instanceof GaussDBInOperation;
    }

    @Override
    public boolean isExists(GaussDBExpression expr) {
        return expr instanceof GaussDBExists;
    }

    @Override
    public GaussDBExpression[] getBinaryLogicalOpChildren(GaussDBExpression expr) {
        if (expr instanceof GaussDBBinaryLogicalOperation) {
            GaussDBBinaryLogicalOperation op = (GaussDBBinaryLogicalOperation) expr;
            return new GaussDBExpression[] { op.getLeft(), op.getRight() };
        }
        return null;
    }

    @Override
    public GaussDBExpression[] getBetweenChildren(GaussDBExpression expr) {
        if (expr instanceof GaussDBBetweenOperation) {
            GaussDBBetweenOperation b = (GaussDBBetweenOperation) expr;
            return new GaussDBExpression[] { b.getExpr(), b.getLeft(), b.getRight() };
        }
        return null;
    }

    // --- Semantic transforms ---

    @Override
    public GaussDBExpression applyExistsToInTransform(GaussDBExpression existsExpr) throws IgnoreMeException {
        if (!(existsExpr instanceof GaussDBExists)) {
            return null;
        }
        GaussDBExists ex = (GaussDBExists) existsExpr;
        GaussDBExpression subquery = ex.getExpr();
        if (!(subquery instanceof GaussDBSelect)) {
            return null;
        }
        GaussDBSelect select = (GaussDBSelect) subquery;
        if (select.getGroupByExpressions() != null && !select.getGroupByExpressions().isEmpty()) {
            return null;
        }
        GaussDBExpression predicate = select.getWhereClause();
        if (predicate == null) {
            return null;
        }
        // CASE WHEN (predicate IS NULL) THEN FALSE ELSE predicate END
        GaussDBExpression isNullPred = new GaussDBUnaryPostfixOperation(predicate, UnaryPostfixOperator.IS_NULL);
        GaussDBExpression falseVal = GaussDBConstant.createBooleanConstant(false);
        GaussDBExpression caseExpr = new GaussDBCaseWhen(isNullPred, falseVal, predicate);
        // Modify subquery: replace select list with CASE, set WHERE to TRUE
        GaussDBSelect modifiedSelect = shallowCopyGaussDBSelect(select);
        modifiedSelect.setFetchColumns(List.of(caseExpr));
        modifiedSelect.setWhereClause(GaussDBConstant.createBooleanConstant(true));
        // TRUE IN (modified_subquery)
        GaussDBExpression trueVal = GaussDBConstant.createBooleanConstant(true);
        return new GaussDBInOperation(trueVal, List.of(modifiedSelect), true);
    }

    @Override
    public GaussDBExpression applyInToExistsTransform(GaussDBExpression inExpr) throws IgnoreMeException {
        if (!(inExpr instanceof GaussDBInOperation)) {
            return null;
        }
        GaussDBInOperation in = (GaussDBInOperation) inExpr;
        if (in.getListElements().size() != 1) {
            return null;
        }
        GaussDBExpression subquery = in.getListElements().get(0);
        if (!(subquery instanceof GaussDBSelect)) {
            return null;
        }
        GaussDBSelect select = (GaussDBSelect) subquery;
        if (select.getFetchColumns() == null || select.getFetchColumns().isEmpty()) {
            return null;
        }
        GaussDBExpression selected = select.getFetchColumns().get(0);
        GaussDBExpression lhs = in.getExpr();
        GaussDBExpression predicate = select.getWhereClause();
        // (selected = lhs) AND predicate
        GaussDBExpression eqExpr = new GaussDBBinaryComparisonOperation(selected, lhs,
                BinaryComparisonOperator.EQUALS);
        GaussDBExpression newPredicate;
        if (predicate != null) {
            newPredicate = new GaussDBBinaryLogicalOperation(eqExpr, predicate, GaussDBBinaryLogicalOperator.AND);
        } else {
            newPredicate = eqExpr;
        }
        // EXISTS(SELECT ... WHERE newPredicate)
        GaussDBSelect existsSelect = shallowCopyGaussDBSelect(select);
        existsSelect.setWhereClause(newPredicate);
        GaussDBExpression existsExpr = new GaussDBExists(existsSelect);
        // CASE WHEN (lhs IN subquery) IS NOT NULL THEN EXISTS_result ELSE NULL END
        GaussDBExpression originalIn = new GaussDBInOperation(lhs, List.of(subquery), in.isTrue());
        GaussDBExpression isNotNull = new GaussDBUnaryPostfixOperation(originalIn, UnaryPostfixOperator.IS_NOT_NULL);
        GaussDBExpression nullVal = GaussDBConstant.createNullConstant();
        return new GaussDBCaseWhen(isNotNull, existsExpr, nullVal);
    }

    @Override
    public boolean isIntersect(GaussDBExpression expr) {
        return false; // GaussDB-M (MySQL compat) does not support INTERSECT
    }

    @Override
    public boolean isExcept(GaussDBExpression expr) {
        return false; // GaussDB-M (MySQL compat) does not support EXCEPT
    }

    @Override
    public GaussDBExpression applyIntersectToExistsTransform(GaussDBExpression expr) throws IgnoreMeException {
        return null; // Not supported in MySQL-compatible mode
    }

    @Override
    public GaussDBExpression applyExceptToNotExistsTransform(GaussDBExpression expr) throws IgnoreMeException {
        return null; // Not supported in MySQL-compatible mode
    }

    private static GaussDBSelect shallowCopyGaussDBSelect(GaussDBSelect s) {
        GaussDBSelect copy = new GaussDBSelect();
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
    public GaussDBExpression generateBooleanExpression() {
        return gen.generateBooleanExpression();
    }

    @Override
    public GaussDBExpression generateExpression() {
        return gen.generateExpression();
    }

    @Override
    public GaussDBExpression generateExpressionWithType(Object type) {
        return gen.generateExpression();
    }

    @Override
    public GaussDBExpression generateSameTypeExpression(GaussDBExpression expr) {
        GaussDBDataType t = inferDataType(expr);
        if (t == null) {
            return gen.generateExpression();
        }
        for (int i = 0; i < 200; i++) {
            try {
                GaussDBExpression e = gen.generateExpression();
                GaussDBDataType t2 = inferDataType(e);
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
    public GaussDBExpression sameTypeFallback(Object type) {
        GaussDBDataType t = (GaussDBDataType) type;
        switch (t) {
        case INT:
            return GaussDBConstant.createIntConstant(0);
        case VARCHAR:
            return GaussDBConstant.createStringConstant("eet");
        case FLOAT:
        case DOUBLE:
            return GaussDBConstant.createStringConstant("0e0");
        case DECIMAL:
            return GaussDBConstant.createIntConstant(0);
        case DATE:
            return GaussDBConstant.createStringConstant("2000-01-01");
        case TIME:
            return GaussDBConstant.createStringConstant("00:00:00");
        case DATETIME:
        case TIMESTAMP:
            return GaussDBConstant.createStringConstant("2000-01-01 00:00:00");
        case YEAR:
            return GaussDBConstant.createStringConstant("2000");
        default:
            throw new IgnoreMeException();
        }
    }

    @Override
    public GaussDBExpression mapChildren(Function<GaussDBExpression, GaussDBExpression> transformFn,
            GaussDBExpression expr) {
        return GaussDBMEETExpressionTree.mapChildren(transformFn, expr);
    }

    @Override
    public String asString(GaussDBExpression expr) {
        return GaussDBToStringVisitor.asString(expr);
    }

    @Override
    public AbstractTables<GaussDBTable, GaussDBColumn> getTargetTables() {
        return targetTables;
    }

    @Override
    public Object getExpressionType(GaussDBExpression expr) {
        return inferDataType(expr);
    }

    @Override
    public String getExpressionTypeName(GaussDBExpression expr) {
        return expr.getClass().getSimpleName();
    }

    @Override
    public boolean supportsRule(String ruleName) {
        switch (ruleName) {
        case "IntersectToExists":
        case "ExceptToNotExists":
            return false; // MySQL-compatible mode lacks INTERSECT/EXCEPT
        default:
            return true;
        }
    }

    private static GaussDBDataType inferDataType(GaussDBExpression expr) {
        try {
            if (expr instanceof GaussDBColumnReference) {
                return ((GaussDBColumnReference) expr).getColumn().getType();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    // -- COALESCE/NULLIF stub implementations --

    @Override
    public GaussDBExpression createNullLiteral() {
        return GaussDBConstant.createNullConstant();
    }

    @Override
    public GaussDBExpression createCoalesce(GaussDBExpression[] args) {
        return null; // Stub
    }

    @Override
    public GaussDBExpression createIsNullCheck(GaussDBExpression expr, boolean isNotNull) {
        if (isNotNull) {
            return new GaussDBUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NOT_NULL);
        } else {
            return new GaussDBUnaryPostfixOperation(expr, UnaryPostfixOperator.IS_NULL);
        }
    }

    @Override
    public boolean isCoalesce(GaussDBExpression expr) {
        return false; // Stub
    }

    @Override
    public boolean isNullif(GaussDBExpression expr) {
        return false; // Stub
    }

    @Override
    public GaussDBExpression[] getCoalesceArguments(GaussDBExpression expr) {
        return null;
    }

    @Override
    public GaussDBExpression[] getNullifArguments(GaussDBExpression expr) {
        return null;
    }
}