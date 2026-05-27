package sqlancer.postgres.ast;

import sqlancer.postgres.PostgresSchema.PostgresDataType;

/**
 * LATERAL subquery: LATERAL (SELECT ...) allows the subquery to reference columns from outer query tables.
 * Example: SELECT * FROM t1, LATERAL (SELECT * FROM t2 WHERE t2.x = t1.x)
 */
public final class PostgresLateralSubquery implements PostgresExpression {

    private final PostgresExpression subquery;

    public PostgresLateralSubquery(PostgresExpression subquery) {
        if (subquery == null) {
            throw new IllegalArgumentException("subquery must not be null");
        }
        this.subquery = subquery;
    }

    public PostgresExpression getSubquery() {
        return subquery;
    }

    @Override
    public PostgresDataType getExpressionType() {
        return null; // LATERAL subquery returns a table, not a scalar value
    }

    @Override
    public PostgresConstant getExpectedValue() {
        return null;
    }
}