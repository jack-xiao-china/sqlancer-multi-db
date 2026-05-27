package sqlancer.postgres.ast;

/**
 * Scalar subquery expression: (SELECT ...) used as a value in an expression.
 * Example: SELECT (SELECT MAX(x) FROM t1) + 1 FROM t2
 */
public class PostgresScalarSubquery implements PostgresExpression {

    private final PostgresSelect subquery;

    public PostgresScalarSubquery(PostgresSelect subquery) {
        this.subquery = subquery;
    }

    public PostgresSelect getSubquery() {
        return subquery;
    }

    @Override
    public PostgresConstant getExpectedValue() {
        // Scalar subquery expected value cannot be computed at expression level
        return null;
    }

    public static PostgresScalarSubquery create(PostgresSelect subquery) {
        return new PostgresScalarSubquery(subquery);
    }
}