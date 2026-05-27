package sqlancer.fucci;

import sqlancer.common.schema.AbstractTableColumn;

/**
 * Fucci列包装类。
 */
public class FucciColumn extends AbstractTableColumn<FucciTable, String> {

    private boolean nullable;
    private boolean primaryKey;
    private boolean indexed;

    public FucciColumn(String name, FucciTable table, String type) {
        super(name, table, type);
    }

    public FucciColumn(AbstractTableColumn<?, ?> originalColumn) {
        super(originalColumn.getName(), null, originalColumn.getType() != null ? originalColumn.getType().toString() : "UNKNOWN");
    }

    public boolean isNullable() { return nullable; }
    public void setNullable(boolean nullable) { this.nullable = nullable; }
    public boolean isPrimaryKey() { return primaryKey; }
    public void setPrimaryKey(boolean primaryKey) { this.primaryKey = primaryKey; }
    public boolean isIndexed() { return indexed; }
    public void setIndexed(boolean indexed) { this.indexed = indexed; }

    @Override
    public String toString() {
        return String.format("FucciColumn{name=%s, type=%s, nullable=%s, pk=%s}", getName(), getType(), nullable, primaryKey);
    }
}