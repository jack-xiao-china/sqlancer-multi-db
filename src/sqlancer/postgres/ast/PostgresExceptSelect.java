package sqlancer.postgres.ast;

import java.util.ArrayList;
import java.util.List;

import sqlancer.postgres.PostgresSchema.PostgresDataType;

/**
 * EXCEPT / EXCEPT ALL set operation.
 * Follows the same pattern as PostgresUnionSelect.
 */
public final class PostgresExceptSelect implements PostgresExpression {

    private final List<PostgresSelect> selects;
    private final boolean exceptAll;

    public PostgresExceptSelect(List<PostgresSelect> selects, boolean exceptAll) {
        if (selects == null || selects.isEmpty()) {
            throw new IllegalArgumentException("selects must be non-empty");
        }
        this.selects = new ArrayList<>(selects);
        this.exceptAll = exceptAll;
    }

    public List<PostgresSelect> getSelects() {
        return selects;
    }

    public boolean isExceptAll() {
        return exceptAll;
    }

    @Override
    public PostgresDataType getExpressionType() {
        return null;
    }
}