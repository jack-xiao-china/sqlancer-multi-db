package sqlancer.postgres.oracle.ext.eet;

import java.util.List;
import java.util.function.Function;

import sqlancer.IgnoreMeException;
import sqlancer.common.oracle.eet.EETTransformAdapter;
import sqlancer.common.schema.AbstractTables;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresDataType;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresVisitor;
import sqlancer.postgres.ast.PostgresBetweenOperation;
import sqlancer.postgres.ast.PostgresBinaryComparisonOperation;
import sqlancer.postgres.ast.PostgresBinaryComparisonOperation.PostgresBinaryComparisonOperator;
import sqlancer.postgres.ast.PostgresBinaryLogicalOperation;
import sqlancer.postgres.ast.PostgresBinaryRangeOperation;
import sqlancer.postgres.ast.PostgresCaseWhen;
import sqlancer.postgres.ast.PostgresConstant;
import sqlancer.postgres.ast.PostgresExceptSelect;
import sqlancer.postgres.ast.PostgresExists;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.ast.PostgresInOperation;
import sqlancer.postgres.ast.PostgresIntersectSelect;
import sqlancer.postgres.ast.PostgresJsonContainOperation;
import sqlancer.postgres.ast.PostgresPostfixOperation;
import sqlancer.postgres.ast.PostgresPostfixOperation.PostfixOperator;
import sqlancer.postgres.ast.PostgresPrefixOperation;
import sqlancer.postgres.ast.PostgresPrefixOperation.PrefixOperator;
import sqlancer.postgres.ast.PostgresPrintedExpression;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.ast.PostgresTableReference;
import sqlancer.postgres.ast.PostgresText;
import sqlancer.postgres.ast.PostgresUnionSelect;
import sqlancer.postgres.ast.PostgresWithSelect;
import sqlancer.postgres.gen.PostgresExpressionGenerator;

public class PostgresEETTransformAdapter implements EETTransformAdapter<PostgresExpression> {

    private final PostgresExpressionGenerator gen;
    private final AbstractTables<PostgresTable, PostgresColumn> targetTables;

    public PostgresEETTransformAdapter(PostgresExpressionGenerator gen,
            AbstractTables<PostgresTable, PostgresColumn> targetTables) {
        this.gen = gen;
        this.targetTables = targetTables;
    }

    @Override
    public PostgresExpression createBinaryLogicalOp(PostgresExpression left, PostgresExpression right,
            EETTransformAdapter.BinaryLogicalOperator op) {
        // Map from EETTransformAdapter enum to Postgres dialect enum
        PostgresBinaryLogicalOperation.BinaryLogicalOperator pgOp;
        if (op == EETTransformAdapter.BinaryLogicalOperator.AND) {
            pgOp = PostgresBinaryLogicalOperation.BinaryLogicalOperator.AND;
        } else {
            pgOp = PostgresBinaryLogicalOperation.BinaryLogicalOperator.OR;
        }
        return new PostgresBinaryLogicalOperation(left, right, pgOp);
    }

    @Override
    public PostgresExpression createNot(PostgresExpression expr) {
        return new PostgresPrefixOperation(expr, PrefixOperator.NOT);
    }

    @Override
    public PostgresExpression createIsNull(PostgresExpression expr) {
        return new PostgresPostfixOperation(expr, PostfixOperator.IS_NULL);
    }

    @Override
    public PostgresExpression createIsNotNull(PostgresExpression expr) {
        return new PostgresPostfixOperation(expr, PostfixOperator.IS_NOT_NULL);
    }

    @Override
    public PostgresExpression createCaseWhen(PostgresExpression condition, PostgresExpression thenExpr,
            PostgresExpression elseExpr) {
        return new PostgresCaseWhen(condition, thenExpr, elseExpr);
    }

    @Override
    public PostgresExpression createPrintedExpression(PostgresExpression expr) {
        return new PostgresPrintedExpression(expr);
    }

    @Override
    public PostgresExpression createConstantInt(long value) {
        return PostgresConstant.createIntConstant(value);
    }

    @Override
    public PostgresExpression createConstantBoolean(boolean value) {
        return value ? PostgresConstant.createTrue() : PostgresConstant.createFalse();
    }

    @Override
    public PostgresExpression createComparisonOp(PostgresExpression left, PostgresExpression right,
            ComparisonOperator op) {
        PostgresBinaryComparisonOperator pgOp;
        switch (op) {
        case EQUALS:
            pgOp = PostgresBinaryComparisonOperator.EQUALS;
            break;
        case NOT_EQUALS:
            pgOp = PostgresBinaryComparisonOperator.NOT_EQUALS;
            break;
        case LESS:
            pgOp = PostgresBinaryComparisonOperator.LESS;
            break;
        case LESS_EQUALS:
            pgOp = PostgresBinaryComparisonOperator.LESS_EQUALS;
            break;
        case GREATER:
            pgOp = PostgresBinaryComparisonOperator.GREATER;
            break;
        case GREATER_EQUALS:
            pgOp = PostgresBinaryComparisonOperator.GREATER_EQUALS;
            break;
        default:
            throw new AssertionError("Unknown comparison operator: " + op);
        }
        return new PostgresBinaryComparisonOperation(left, right, pgOp);
    }

    @Override
    public PostgresExpression createBetween(PostgresExpression expr, PostgresExpression low, PostgresExpression high) {
        return new PostgresBetweenOperation(expr, low, high, false);
    }

    @Override
    public PostgresExpression createExistsSubquery(PostgresExpression subquery) {
        // PostgreSQL EXISTS is handled via PostgresPrefixOperation with EXISTS semantics
        // For semantic rewrite rules, will need a dedicated AST node in Phase 3
        // Currently using a placeholder: wrap the subquery in a PostgresConstant string representation
        // This will be properly implemented when semantic rewrite rules are added
        return null; // not yet supported, will be added in Phase 3
    }

    @Override
    public PostgresExpression createInSubquery(PostgresExpression lhs, PostgresExpression subquery) {
        // Same as EXISTS - placeholder for semantic rewrite rules
        return new PostgresInOperation(lhs, java.util.List.of(subquery), true);
    }

    @Override
    public boolean isBooleanLike(PostgresExpression expr) {
        if (expr == null) {
            return false;
        }
        PostgresDataType t = expr.getExpressionType();
        if (t == PostgresDataType.BOOLEAN) {
            return true;
        }
        return expr instanceof PostgresBinaryLogicalOperation || expr instanceof PostgresPostfixOperation
                || expr instanceof PostgresPrefixOperation
                || expr instanceof PostgresBinaryComparisonOperation
                || expr instanceof PostgresInOperation || expr instanceof PostgresExists
                || expr instanceof PostgresBinaryRangeOperation
                || expr instanceof PostgresJsonContainOperation;
    }

    @Override
    public boolean isRule7NoChange(PostgresExpression expr) {
        return expr instanceof PostgresTableReference || expr instanceof PostgresText;
    }

    @Override
    public boolean isQueryLevelNode(PostgresExpression expr) {
        return expr instanceof PostgresUnionSelect || expr instanceof PostgresIntersectSelect
                || expr instanceof PostgresExceptSelect || expr instanceof PostgresWithSelect
                || expr instanceof PostgresSelect;
    }

    @Override
    public boolean isConstant(PostgresExpression expr) {
        return expr instanceof PostgresConstant;
    }

    @Override
    public boolean isNullConstant(PostgresExpression expr) {
        return expr instanceof PostgresConstant && ((PostgresConstant) expr).isNull();
    }

    @Override
    public boolean isZeroOrOneInt(PostgresExpression expr) {
        if (!(expr instanceof PostgresConstant)) {
            return false;
        }
        PostgresConstant c = (PostgresConstant) expr;
        if (c.isNull() || !c.isInt()) {
            return false;
        }
        long v = c.asInt();
        return v == 0 || v == 1;
    }

    @Override
    public boolean asBooleanNotNull(PostgresExpression expr) {
        if (!(expr instanceof PostgresConstant)) {
            return false;
        }
        return ((PostgresConstant) expr).asBoolean();
    }

    @Override
    public boolean isBooleanConstant(PostgresExpression expr) {
        return expr instanceof PostgresConstant && ((PostgresConstant) expr).isBoolean();
    }

    @Override
    public boolean isAndOp(PostgresExpression expr) {
        return expr instanceof PostgresBinaryLogicalOperation
                && ((PostgresBinaryLogicalOperation) expr).getOp() == PostgresBinaryLogicalOperation.BinaryLogicalOperator.AND;
    }

    @Override
    public boolean isOrOp(PostgresExpression expr) {
        return expr instanceof PostgresBinaryLogicalOperation
                && ((PostgresBinaryLogicalOperation) expr).getOp() == PostgresBinaryLogicalOperation.BinaryLogicalOperator.OR;
    }

    @Override
    public boolean isBetween(PostgresExpression expr) {
        return expr instanceof PostgresBetweenOperation;
    }

    @Override
    public boolean isInOperation(PostgresExpression expr) {
        return expr instanceof PostgresInOperation;
    }

    @Override
    public boolean isExists(PostgresExpression expr) {
        return expr instanceof PostgresExists;
    }

    @Override
    public PostgresExpression[] getBinaryLogicalOpChildren(PostgresExpression expr) {
        if (expr instanceof PostgresBinaryLogicalOperation) {
            PostgresBinaryLogicalOperation op = (PostgresBinaryLogicalOperation) expr;
            return new PostgresExpression[] { op.getLeft(), op.getRight() };
        }
        return null;
    }

    @Override
    public PostgresExpression[] getBetweenChildren(PostgresExpression expr) {
        if (expr instanceof PostgresBetweenOperation) {
            PostgresBetweenOperation b = (PostgresBetweenOperation) expr;
            return new PostgresExpression[] { b.getExpr(), b.getLeft(), b.getRight() };
        }
        return null;
    }

    @Override
    public PostgresExpression applyExistsToInTransform(PostgresExpression expr) throws IgnoreMeException {
        if (!(expr instanceof PostgresExists)) {
            return null;
        }
        PostgresExists ex = (PostgresExists) expr;
        PostgresExpression subquery = ex.getSubquery();
        if (!(subquery instanceof PostgresSelect)) {
            return null;
        }
        PostgresSelect select = (PostgresSelect) subquery;
        if (select.getGroupByExpressions() != null && !select.getGroupByExpressions().isEmpty()) {
            return null;
        }
        PostgresExpression predicate = select.getWhereClause();
        if (predicate == null) {
            return null;
        }
        PostgresExpression isNullPred = new PostgresPostfixOperation(predicate, PostfixOperator.IS_NULL);
        PostgresExpression falseVal = PostgresConstant.createFalse();
        PostgresExpression caseExpr = new PostgresCaseWhen(isNullPred, falseVal, predicate);
        PostgresSelect modifiedSelect = shallowCopyPostgresSelect(select);
        modifiedSelect.setFetchColumns(java.util.List.of(caseExpr));
        modifiedSelect.setWhereClause(PostgresConstant.createTrue());
        PostgresExpression trueVal = PostgresConstant.createTrue();
        return new PostgresInOperation(trueVal, java.util.List.of(modifiedSelect), true);
    }

    @Override
    public PostgresExpression applyInToExistsTransform(PostgresExpression expr) throws IgnoreMeException {
        if (!(expr instanceof PostgresInOperation)) {
            return null;
        }
        PostgresInOperation in = (PostgresInOperation) expr;
        if (in.getListElements().size() != 1) {
            return null;
        }
        PostgresExpression subquery = in.getListElements().get(0);
        PostgresExpression lhs = in.getExpr();

        // Handle UNION subquery (matching native EET in_query.cc lines 108-139)
        if (subquery instanceof PostgresUnionSelect) {
            PostgresUnionSelect union = (PostgresUnionSelect) subquery;
            java.util.List<PostgresSelect> modifiedSelects = new java.util.ArrayList<>();
            for (PostgresSelect branch : union.getSelects()) {
                if (branch.getFetchColumns() == null || branch.getFetchColumns().isEmpty()) {
                    return null;
                }
                PostgresExpression branchSelected = branch.getFetchColumns().get(0);
                PostgresExpression eqExpr = new PostgresBinaryComparisonOperation(branchSelected, lhs,
                        PostgresBinaryComparisonOperator.EQUALS);
                PostgresExpression branchPredicate = branch.getWhereClause();
                PostgresExpression newPredicate;
                if (branchPredicate != null) {
                    newPredicate = new PostgresBinaryLogicalOperation(eqExpr, branchPredicate,
                            PostgresBinaryLogicalOperation.BinaryLogicalOperator.AND);
                } else {
                    newPredicate = eqExpr;
                }
                PostgresSelect modifiedBranch = shallowCopyPostgresSelect(branch);
                modifiedBranch.setWhereClause(newPredicate);
                modifiedSelects.add(modifiedBranch);
            }
            PostgresExpression modifiedUnion = new PostgresUnionSelect(modifiedSelects, union.isUnionAll());
            PostgresExpression existsExpr = new PostgresExists(modifiedUnion);
            // CASE WHEN (lhs IN subquery) IS NOT NULL THEN EXISTS ELSE NULL END
            PostgresExpression originalIn = new PostgresInOperation(lhs, java.util.List.of(subquery), in.isTrue());
            PostgresExpression isNotNull = new PostgresPostfixOperation(originalIn, PostfixOperator.IS_NOT_NULL);
            PostgresExpression nullVal = PostgresConstant.createNullConstant();
            return new PostgresCaseWhen(isNotNull, existsExpr, nullVal);
        }

        // Handle simple SELECT subquery
        if (!(subquery instanceof PostgresSelect)) {
            return null;
        }
        PostgresSelect select = (PostgresSelect) subquery;
        if (select.getFetchColumns() == null || select.getFetchColumns().isEmpty()) {
            return null;
        }
        PostgresExpression selected = select.getFetchColumns().get(0);
        PostgresExpression predicate = select.getWhereClause();
        PostgresExpression eqExpr = new PostgresBinaryComparisonOperation(selected, lhs,
                PostgresBinaryComparisonOperator.EQUALS);
        PostgresExpression newPredicate;
        if (predicate != null) {
            newPredicate = new PostgresBinaryLogicalOperation(eqExpr, predicate,
                    PostgresBinaryLogicalOperation.BinaryLogicalOperator.AND);
        } else {
            newPredicate = eqExpr;
        }
        PostgresSelect existsSelect = shallowCopyPostgresSelect(select);
        existsSelect.setWhereClause(newPredicate);
        PostgresExpression existsExpr = new PostgresExists(existsSelect);
        PostgresExpression originalIn = new PostgresInOperation(lhs, java.util.List.of(subquery), in.isTrue());
        PostgresExpression isNotNull = new PostgresPostfixOperation(originalIn, PostfixOperator.IS_NOT_NULL);
        PostgresExpression nullVal = PostgresConstant.createNullConstant();
        return new PostgresCaseWhen(isNotNull, existsExpr, nullVal);
    }

    @Override
    public boolean isIntersect(PostgresExpression expr) {
        return expr instanceof PostgresIntersectSelect;
    }

    @Override
    public boolean isExcept(PostgresExpression expr) {
        return expr instanceof PostgresExceptSelect;
    }

    @Override
    public PostgresExpression applyIntersectToExistsTransform(PostgresExpression expr) throws IgnoreMeException {
        if (!(expr instanceof PostgresIntersectSelect)) {
            return null;
        }
        PostgresIntersectSelect intersect = (PostgresIntersectSelect) expr;
        List<PostgresSelect> selects = intersect.getSelects();
        if (selects.size() < 2) {
            return null;
        }

        // Get left (Q1) and right (Q2) queries
        PostgresSelect q1 = selects.get(0);
        PostgresSelect q2 = selects.get(1);

        // Guard: skip if either has GROUP BY or window functions
        if ((q1.getGroupByExpressions() != null && !q1.getGroupByExpressions().isEmpty())
                || (q2.getGroupByExpressions() != null && !q2.getGroupByExpressions().isEmpty())) {
            return null;
        }

        // Get column lists from both sides
        List<PostgresExpression> q1Columns = q1.getFetchColumns();
        List<PostgresExpression> q2Columns = q2.getFetchColumns();
        if (q1Columns == null || q2Columns == null || q1Columns.size() != q2Columns.size()) {
            return null;
        }
        if (q1Columns.isEmpty()) {
            return null;
        }

        // Build NULL-safe column equality predicates for all columns
        // (q1.col = q2.col) OR (q1.col IS NULL AND q2.col IS NULL)
        PostgresExpression columnEquality = buildNullSafeColumnEquality(q1Columns, q2Columns);
        if (columnEquality == null) {
            return null;
        }

        // Construct Q2 WHERE predicate: columnEquality AND Q2.original_predicate
        PostgresExpression q2Predicate = q2.getWhereClause();
        PostgresExpression newQ2Predicate;
        if (q2Predicate != null) {
            newQ2Predicate = new PostgresBinaryLogicalOperation(columnEquality, q2Predicate,
                    PostgresBinaryLogicalOperation.BinaryLogicalOperator.AND);
        } else {
            newQ2Predicate = columnEquality;
        }

        // Create modified Q2 for EXISTS subquery
        PostgresSelect modifiedQ2 = shallowCopyPostgresSelect(q2);
        modifiedQ2.setWhereClause(newQ2Predicate);

        // Create EXISTS predicate
        PostgresExpression existsExpr = new PostgresExists(modifiedQ2);

        // Modify Q1 WHERE: EXISTS(Q2) AND Q1.original_predicate
        PostgresExpression q1Predicate = q1.getWhereClause();
        PostgresExpression newQ1Predicate;
        if (q1Predicate != null) {
            newQ1Predicate = new PostgresBinaryLogicalOperation(existsExpr, q1Predicate,
                    PostgresBinaryLogicalOperation.BinaryLogicalOperator.AND);
        } else {
            newQ1Predicate = existsExpr;
        }

        // Return modified Q1 (without INTERSECT, just SELECT with EXISTS)
        PostgresSelect resultQ1 = shallowCopyPostgresSelect(q1);
        resultQ1.setWhereClause(newQ1Predicate);
        return resultQ1;
    }

    @Override
    public PostgresExpression applyExceptToNotExistsTransform(PostgresExpression expr) throws IgnoreMeException {
        if (!(expr instanceof PostgresExceptSelect)) {
            return null;
        }
        PostgresExceptSelect except = (PostgresExceptSelect) expr;
        List<PostgresSelect> selects = except.getSelects();
        if (selects.size() < 2) {
            return null;
        }

        // Get left (Q1) and right (Q2) queries
        PostgresSelect q1 = selects.get(0);
        PostgresSelect q2 = selects.get(1);

        // Guard: skip if either has GROUP BY or window functions
        if ((q1.getGroupByExpressions() != null && !q1.getGroupByExpressions().isEmpty())
                || (q2.getGroupByExpressions() != null && !q2.getGroupByExpressions().isEmpty())) {
            return null;
        }

        // Get column lists from both sides
        List<PostgresExpression> q1Columns = q1.getFetchColumns();
        List<PostgresExpression> q2Columns = q2.getFetchColumns();
        if (q1Columns == null || q2Columns == null || q1Columns.size() != q2Columns.size()) {
            return null;
        }
        if (q1Columns.isEmpty()) {
            return null;
        }

        // Build NULL-safe column equality predicates for all columns
        PostgresExpression columnEquality = buildNullSafeColumnEquality(q1Columns, q2Columns);
        if (columnEquality == null) {
            return null;
        }

        // Construct Q2 WHERE predicate: columnEquality AND Q2.original_predicate
        PostgresExpression q2Predicate = q2.getWhereClause();
        PostgresExpression newQ2Predicate;
        if (q2Predicate != null) {
            newQ2Predicate = new PostgresBinaryLogicalOperation(columnEquality, q2Predicate,
                    PostgresBinaryLogicalOperation.BinaryLogicalOperator.AND);
        } else {
            newQ2Predicate = columnEquality;
        }

        // Create modified Q2 for EXISTS subquery
        PostgresSelect modifiedQ2 = shallowCopyPostgresSelect(q2);
        modifiedQ2.setWhereClause(newQ2Predicate);

        // Create EXISTS predicate, then wrap in NOT
        PostgresExpression existsExpr = new PostgresExists(modifiedQ2);
        PostgresExpression notExistsExpr = new PostgresPrefixOperation(existsExpr, PrefixOperator.NOT);

        // Modify Q1 WHERE: NOT EXISTS(Q2) AND Q1.original_predicate
        PostgresExpression q1Predicate = q1.getWhereClause();
        PostgresExpression newQ1Predicate;
        if (q1Predicate != null) {
            newQ1Predicate = new PostgresBinaryLogicalOperation(notExistsExpr, q1Predicate,
                    PostgresBinaryLogicalOperation.BinaryLogicalOperator.AND);
        } else {
            newQ1Predicate = notExistsExpr;
        }

        // Return modified Q1 (without EXCEPT, just SELECT with NOT EXISTS)
        PostgresSelect resultQ1 = shallowCopyPostgresSelect(q1);
        resultQ1.setWhereClause(newQ1Predicate);
        return resultQ1;
    }

    /**
     * Build NULL-safe column equality predicate for all columns.
     * For each column pair (q1_col, q2_col), creates:
     * (q1_col = q2_col) OR (q1_col IS NULL AND q2_col IS NULL)
     * All column equalities are ANDed together.
     *
     * This matches the native EET grammar.cc:1980-2034 implementation.
     */
    private PostgresExpression buildNullSafeColumnEquality(List<PostgresExpression> q1Columns,
            List<PostgresExpression> q2Columns) {
        PostgresExpression combinedPredicate = null;

        for (int i = 0; i < q1Columns.size(); i++) {
            PostgresExpression q1Col = q1Columns.get(i);
            PostgresExpression q2Col = q2Columns.get(i);

            // q1_col = q2_col
            PostgresExpression equality = new PostgresBinaryComparisonOperation(q1Col, q2Col,
                    PostgresBinaryComparisonOperator.EQUALS);

            // q1_col IS NULL
            PostgresExpression q1IsNull = new PostgresPostfixOperation(q1Col, PostfixOperator.IS_NULL);

            // q2_col IS NULL
            PostgresExpression q2IsNull = new PostgresPostfixOperation(q2Col, PostfixOperator.IS_NULL);

            // (q1_col IS NULL AND q2_col IS NULL)
            PostgresExpression bothNull = new PostgresBinaryLogicalOperation(q1IsNull, q2IsNull,
                    PostgresBinaryLogicalOperation.BinaryLogicalOperator.AND);

            // (q1_col = q2_col) OR (q1_col IS NULL AND q2_col IS NULL)
            PostgresExpression nullSafeEquality = new PostgresBinaryLogicalOperation(equality, bothNull,
                    PostgresBinaryLogicalOperation.BinaryLogicalOperator.OR);

            // AND all column equalities together
            if (combinedPredicate == null) {
                combinedPredicate = nullSafeEquality;
            } else {
                combinedPredicate = new PostgresBinaryLogicalOperation(combinedPredicate, nullSafeEquality,
                        PostgresBinaryLogicalOperation.BinaryLogicalOperator.AND);
            }
        }

        return combinedPredicate;
    }

    @Override
    public Object getExpressionType(PostgresExpression expr) {
        return expr.getExpressionType();
    }

    @Override
    public String getExpressionTypeName(PostgresExpression expr) {
        return expr.getClass().getSimpleName();
    }

    @Override
    public boolean supportsRule(String ruleName) {
        return true;
    }

    @Override
    public PostgresExpression generateBooleanExpression() {
        return gen.generateBooleanExpression();
    }

    @Override
    public PostgresExpression generateExpression() {
        return gen.generateExpression(0);
    }

    @Override
    public PostgresExpression generateExpressionWithType(Object type) {
        if (type instanceof PostgresDataType) {
            return gen.generateExpression(0, (PostgresDataType) type);
        }
        return gen.generateExpression(0);
    }

    @Override
    public PostgresExpression generateSameTypeExpression(PostgresExpression expr) {
        PostgresDataType type = expr.getExpressionType();
        if (type == null) {
            PostgresExpression randomBool = gen.generateBooleanExpression();
            PostgresExpression copy = new PostgresPrintedExpression(expr);
            return new PostgresCaseWhen(randomBool, copy, expr);
        }
        return gen.generateExpression(0, type);
    }

    @Override
    public PostgresExpression sameTypeFallback(Object type) {
        if (type instanceof PostgresDataType) {
            PostgresDataType pgType = (PostgresDataType) type;
            switch (pgType) {
            case INT:
                return PostgresConstant.createIntConstant(0);
            case BOOLEAN:
                return PostgresConstant.createFalse();
            case VARCHAR:
                return new PostgresConstant.StringConstant("eet");
            case FLOAT:
                return PostgresConstant.createFloatConstant((float) 0.0);
            default:
                return PostgresConstant.createIntConstant(0);
            }
        }
        return PostgresConstant.createIntConstant(0);
    }

    @Override
    public PostgresExpression mapChildren(Function<PostgresExpression, PostgresExpression> transformFn,
            PostgresExpression expr) {
        return PostgresEETExpressionTree.mapChildren(transformFn, expr);
    }

    @Override
    public String asString(PostgresExpression expr) {
        return PostgresVisitor.asString(expr);
    }

    @Override
    public AbstractTables<PostgresTable, PostgresColumn> getTargetTables() {
        return targetTables;
    }

    private static PostgresSelect shallowCopyPostgresSelect(PostgresSelect s) {
        PostgresSelect copy = new PostgresSelect();
        copy.setSelectOption(s.getSelectOption());
        copy.setFetchColumns(new java.util.ArrayList<>(s.getFetchColumns()));
        copy.setFromList(new java.util.ArrayList<>(s.getFromList()));
        copy.setJoinClauses(new java.util.ArrayList<>(s.getJoinClauses()));
        copy.setWhereClause(s.getWhereClause());
        copy.setGroupByExpressions(new java.util.ArrayList<>(s.getGroupByExpressions()));
        copy.setHavingClause(s.getHavingClause());
        copy.setOrderByClauses(new java.util.ArrayList<>(s.getOrderByClauses()));
        copy.setLimitClause(s.getLimitClause());
        copy.setOffsetClause(s.getOffsetClause());
        return copy;
    }

    // -- COALESCE/NULLIF stub implementations (TODO: implement with PostgresFunction) --

    @Override
    public PostgresExpression createNullLiteral() {
        return PostgresConstant.createNullConstant();
    }

    @Override
    public PostgresExpression createCoalesce(PostgresExpression[] args) {
        // Stub: return null for now, full implementation requires PostgresFunction
        return null;
    }

    @Override
    public PostgresExpression createIsNullCheck(PostgresExpression expr, boolean isNotNull) {
        if (isNotNull) {
            return new PostgresPostfixOperation(expr, PostfixOperator.IS_NOT_NULL);
        } else {
            return new PostgresPostfixOperation(expr, PostfixOperator.IS_NULL);
        }
    }

    @Override
    public boolean isCoalesce(PostgresExpression expr) {
        // Stub: COALESCE not yet detected in PostgreSQL AST
        return false;
    }

    @Override
    public boolean isNullif(PostgresExpression expr) {
        // Stub: NULLIF not yet detected in PostgreSQL AST
        return false;
    }

    @Override
    public PostgresExpression[] getCoalesceArguments(PostgresExpression expr) {
        return null;
    }

    @Override
    public PostgresExpression[] getNullifArguments(PostgresExpression expr) {
        return null;
    }
}