package sqlancer.common.oracle.eet;

import java.util.function.Function;

import sqlancer.IgnoreMeException;
import sqlancer.common.schema.AbstractTables;

/**
 * Dialect adapter that decouples EET transformation logic from dialect-specific AST types.
 * Each dialect (MySQL, PostgreSQL, GaussDB-M, GaussDB-A) implements this interface
 * to provide factory methods for AST node creation and type-specific queries.
 */
public interface EETTransformAdapter<E> {

    // -- AST creation: boolean/logical --

    E createBinaryLogicalOp(E left, E right, BinaryLogicalOperator op);

    E createNot(E expr);

    E createIsNull(E expr);

    E createIsNotNull(E expr);

    // -- AST creation: value --

    E createCaseWhen(E condition, E thenExpr, E elseExpr);

    E createPrintedExpression(E expr);

    E createConstantInt(long value);

    E createConstantBoolean(boolean value);

    E createNullLiteral();

    // -- AST creation: COALESCE/NULLIF --

    E createCoalesce(E[] args);

    E createIsNullCheck(E expr, boolean isNotNull);

    // -- Type queries: COALESCE/NULLIF --

    boolean isCoalesce(E expr);

    boolean isNullif(E expr);

    E[] getCoalesceArguments(E expr);

    E[] getNullifArguments(E expr);

    // -- AST creation: comparison (for semantic rewrite rules) --

    E createComparisonOp(E left, E right, ComparisonOperator op);

    E createBetween(E expr, E low, E high);

    // -- AST creation: subquery (for semantic rewrite rules) --

    E createExistsSubquery(E subquery);

    E createInSubquery(E lhs, E subquery);

    // -- Type queries --

    boolean isBooleanLike(E expr);

    boolean isRule7NoChange(E expr);

    boolean isConstant(E expr);

    boolean isNullConstant(E expr);

    boolean isZeroOrOneInt(E expr);

    boolean asBooleanNotNull(E expr);

    /** Check if the expression is a boolean constant (TRUE/FALSE), not just int 0/1. */
    boolean isBooleanConstant(E expr);

    boolean isAndOp(E expr);

    boolean isOrOp(E expr);

    boolean isBetween(E expr);

    boolean isInOperation(E expr);

    boolean isExists(E expr);

    /** Extract left and right children of a binary logical operation (AND/OR). */
    E[] getBinaryLogicalOpChildren(E expr);

    /** Extract the subject (mhs), lower bound (lhs), and upper bound (rhs) of a BETWEEN expression. */
    E[] getBetweenChildren(E expr);

    /**
     * Apply EXISTS→IN transformation dialect-specifically.
     * EXISTS(SELECT...WHERE p) → TRUE IN (SELECT CASE WHEN p IS NULL THEN FALSE ELSE p END FROM ... WHERE TRUE)
     *
     * Returns null if the transformation cannot apply (GROUP BY, window, unioned subquery).
     */
    E applyExistsToInTransform(E existsExpr) throws IgnoreMeException;

    /**
     * Apply IN→EXISTS transformation dialect-specifically.
     * lhs IN (SELECT sel FROM ... WHERE pred) →
     * CASE WHEN (lhs IN subquery) IS NOT NULL
     *   THEN EXISTS(SELECT ... WHERE sel=lhs AND pred)
     *   ELSE NULL END
     *
     * Returns null if the transformation cannot apply.
     */
    E applyInToExistsTransform(E inExpr) throws IgnoreMeException;

    /** Check if the expression is an INTERSECT set operation. */
    boolean isIntersect(E expr);

    /** Check if the expression is an EXCEPT set operation. */
    boolean isExcept(E expr);

    /**
     * Apply INTERSECT→EXISTS transformation dialect-specifically.
     * Q1 INTERSECT Q2 → Q1 WHERE EXISTS(Q2 WHERE col_eq_or_null AND Q2_pred) AND Q1_pred
     *
     * Returns null if the transformation cannot apply (GROUP BY, window functions on either side).
     */
    E applyIntersectToExistsTransform(E intersectExpr) throws IgnoreMeException;

    /**
     * Apply EXCEPT→NOT EXISTS transformation dialect-specifically.
     * Q1 EXCEPT Q2 → Q1 WHERE NOT EXISTS(Q2 WHERE col_eq_or_null AND Q2_pred) AND Q1_pred
     *
     * Returns null if the transformation cannot apply.
     */
    E applyExceptToNotExistsTransform(E exceptExpr) throws IgnoreMeException;

    /** Get the dialect-specific data type of an expression, for type-aware generation. */
    Object getExpressionType(E expr);

    /** Get the expression type name for impedance tracking (e.g., "MySQLComputableFunction"). */
    String getExpressionTypeName(E expr);

    /** Check if the expression is a query-level node (UNION, INTERSECT, EXCEPT, WITH, SELECT).
     * Query-level nodes should be returned after branch recursion without trying wrapping rules,
     * because wrapping them in CASE WHEN or boolean tautology produces invalid SQL.
     * This matches the native EET behavior where equivalent_transform on a unioned_query
     * only transforms each branch and returns the UNION unchanged. */
    boolean isQueryLevelNode(E expr);

    /** Check if a dialect supports a specific semantic rewrite rule. */
    boolean supportsRule(String ruleName);

    // -- Expression generation delegation --

    E generateBooleanExpression();

    E generateExpression();

    E generateExpressionWithType(Object type);

    E generateSameTypeExpression(E expr);

    E sameTypeFallback(Object type);

    // -- AST traversal delegation --

    E mapChildren(Function<E, E> transformFn, E expr);

    // -- Utility --

    String asString(E expr);

    /** Get tables/columns information for the current test context. */
    AbstractTables<?, ?> getTargetTables();

    // -- Enums --

    enum BinaryLogicalOperator {
        AND, OR
    }

    enum ComparisonOperator {
        EQUALS, NOT_EQUALS, LESS, LESS_EQUALS, GREATER, GREATER_EQUALS
    }
}