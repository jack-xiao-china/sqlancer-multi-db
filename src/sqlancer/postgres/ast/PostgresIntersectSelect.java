package sqlancer.postgres.ast;

import java.util.ArrayList;
import java.util.List;

import sqlancer.postgres.PostgresSchema.PostgresDataType;

/**
 * INTERSECT / INTERSECT ALL set operation.
 * Follows the same pattern as PostgresUnionSelect.
 */
public final class PostgresIntersectSelect implements PostgresExpression {

    private final List<PostgresSelect> selects;
    private final boolean intersectAll;

    public PostgresIntersectSelect(List<PostgresSelect> selects, boolean intersectAll) {
        if (selects == null || selects.isEmpty()) {
            throw new IllegalArgumentException("selects must be non-empty");
        }
        this.selects = new ArrayList<>(selects);
        this.intersectAll = intersectAll;
    }

    public List<PostgresSelect> getSelects() {
        return selects;
    }

    public boolean isIntersectAll() {
        return intersectAll;
    }

    @Override
    public PostgresDataType getExpressionType() {
        return null;
    }
}