package sqlancer.fucci;

import java.util.ArrayList;
import java.util.List;

import sqlancer.common.schema.TableIndex;

/**
 * Fucci索引包装类。
 * 继承TableIndex类。
 */
public class FucciIndex extends TableIndex {

    private List<String> columnNames;
    private boolean unique;
    private boolean primaryKey;

    public FucciIndex(String indexName) {
        super(indexName);
        this.columnNames = new ArrayList<>();
    }

    public FucciIndex(String indexName, List<String> columnNames) {
        super(indexName);
        this.columnNames = new ArrayList<>(columnNames);
    }

    public List<String> getColumnNames() { return columnNames; }
    public void addColumnName(String columnName) { columnNames.add(columnName); }
    public boolean isUnique() { return unique; }
    public void setUnique(boolean unique) { this.unique = unique; }
    public boolean isPrimaryKey() { return primaryKey; }
    public void setPrimaryKey(boolean primaryKey) { this.primaryKey = primaryKey; }
    public int getColumnCount() { return columnNames.size(); }

    @Override
    public String toString() {
        return String.format("FucciIndex{name=%s, columns=%s, unique=%s, pk=%s}", getIndexName(), columnNames, unique, primaryKey);
    }
}