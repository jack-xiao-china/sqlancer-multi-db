package sqlancer.mysql.ast;

/**
 * Scalar subquery expression: (SELECT ...) used as a value in an expression.
 * Example: SELECT (SELECT MAX(x) FROM t1) + 1 FROM t2
 */
public class MySQLScalarSubquery implements MySQLExpression {

    private final MySQLSelect subquery;

    public MySQLScalarSubquery(MySQLSelect subquery) {
        this.subquery = subquery;
    }

    public MySQLSelect getSubquery() {
        return subquery;
    }

    @Override
    public MySQLConstant getExpectedValue() {
        // Scalar subquery expected value cannot be computed at expression level
        return null;
    }

    public static MySQLScalarSubquery create(MySQLSelect subquery) {
        return new MySQLScalarSubquery(subquery);
    }
}