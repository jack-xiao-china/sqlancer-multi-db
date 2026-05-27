package sqlancer.postgres.ast;

import sqlancer.postgres.PostgresSchema.PostgresDataType;

/**
 * EXISTS subquery predicate.
 * Follows the same pattern as MySQLExists.
 */
public final class PostgresExists implements PostgresExpression {

    private final PostgresExpression subquery;

    public PostgresExists(PostgresExpression subquery) {
        this.subquery = subquery;
    }

    public PostgresExpression getSubquery() {
        return subquery;
    }

    @Override
    public PostgresDataType getExpressionType() {
        return PostgresDataType.BOOLEAN;
    }
}