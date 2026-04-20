package sqlancer.postgres.ast;

import sqlancer.postgres.PostgresCompoundDataType;
import sqlancer.postgres.PostgresSchema.PostgresColumn;
import sqlancer.postgres.PostgresSchema.PostgresDataType;

public class PostgresColumnReference implements PostgresExpression {
    private final PostgresColumn c;

    public PostgresColumnReference(PostgresColumn c) {
        this.c = c;
    }

    @Override
    public PostgresDataType getExpressionType() {
        return c.getType();
    }

    @Override
    public PostgresCompoundDataType getExpressionCompoundType() {
        return c.getCompoundType();
    }

    public PostgresColumn getColumn() {
        return c;
    }
}
