package sqlancer.postgres.ast;

import sqlancer.postgres.PostgresSchema.PostgresDataType;

/**
 * AST node representing {@code *} in SELECT fetch columns (e.g., {@code SELECT * FROM t}).
 * Used by the JIR Oracle for 50/50 SELECT * vs multi-column fetch pattern,
 * matching the original GeneralJIROracle's {@code GeneralColumn("*")} design.
 *
 * <p>
 * Unlike MySQL, PostgreSQL's ToStringVisitor does not append {@code AS ref<N>} aliases
 * to fetch columns, so PostgresWildcard renders cleanly as just {@code *} without
 * needing alias-skip logic.
 */
public class PostgresWildcard implements PostgresExpression {

    @Override
    public PostgresDataType getExpressionType() {
        throw new AssertionError();
    }

    @Override
    public PostgresConstant getExpectedValue() {
        return null;
    }
}
