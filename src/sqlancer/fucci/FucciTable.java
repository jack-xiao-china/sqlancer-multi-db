package sqlancer.fucci;

import java.util.ArrayList;
import java.util.List;

import sqlancer.common.schema.AbstractTable;
import sqlancer.common.schema.AbstractTableColumn;

/**
 * Fucci表包装类。
 */
public class FucciTable extends AbstractTable<FucciColumn, FucciIndex, FucciGlobalState> {

    private AbstractTable<?, ?, ?> originalTable;
    private List<FucciColumn> fucciColumns;
    private List<FucciIndex> fucciIndexes;
    private String primaryKeyColumn;

    public FucciTable(String name, List<FucciColumn> columns, List<FucciIndex> indexes, boolean isView) {
        super(name, columns != null ? columns : new ArrayList<>(), indexes != null ? indexes : new ArrayList<>(), isView);
        this.fucciColumns = columns != null ? columns : new ArrayList<>();
        this.fucciIndexes = indexes != null ? indexes : new ArrayList<>();
    }

    public FucciTable(AbstractTable<?, ?, ?> originalTable) {
        super(originalTable.getName(), convertColumns(originalTable), new ArrayList<>(), false);
        this.originalTable = originalTable;
        this.fucciColumns = convertColumns(originalTable);
        this.fucciIndexes = new ArrayList<>();
    }

    private static List<FucciColumn> convertColumns(AbstractTable<?, ?, ?> originalTable) {
        List<FucciColumn> columns = new ArrayList<>();
        if (originalTable != null && originalTable.getColumns() != null) {
            for (AbstractTableColumn<?, ?> column : originalTable.getColumns()) {
                columns.add(new FucciColumn(column));
            }
        }
        return columns;
    }

    @Override
    public List<FucciColumn> getColumns() {
        return fucciColumns;
    }

    @Override
    public List<FucciIndex> getIndexes() {
        return fucciIndexes;
    }

    @Override
    public long getNrRows(FucciGlobalState globalState) {
        return rowCount;
    }

    public AbstractTable<?, ?, ?> getOriginalTable() { return originalTable; }
    public void addColumn(FucciColumn column) { fucciColumns.add(column); }
    public void addIndex(FucciIndex index) { fucciIndexes.add(index); }
    public String getPrimaryKeyColumn() { return primaryKeyColumn; }
    public void setPrimaryKeyColumn(String primaryKeyColumn) { this.primaryKeyColumn = primaryKeyColumn; }
    public int getColumnCount() { return fucciColumns.size(); }

    public FucciColumn getColumn(String columnName) {
        for (FucciColumn column : fucciColumns) {
            if (column.getName().equals(columnName)) {
                return column;
            }
        }
        return null;
    }

    public FucciColumn getColumn(int index) {
        if (index >= 0 && index < fucciColumns.size()) {
            return fucciColumns.get(index);
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("FucciTable{name=%s, columns=%d, indexes=%d}", getName(), fucciColumns.size(), fucciIndexes.size());
    }
}