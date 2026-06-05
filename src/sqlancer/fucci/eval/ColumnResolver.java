package sqlancer.fucci.eval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 列名到 Object[] 数组索引的映射器。
 * 大小写不敏感（MySQL 默认 + PG 未引用标识符折叠）。
 * 支持 "col" 和 "table.col" 形式的列引用。
 */
public final class ColumnResolver {

    private final Map<String, Integer> nameToIndex;
    private final String tableName;

    /**
     * 从列名列表构建。
     *
     * @param tableName   表名
     * @param columnNames 列名列表（顺序对应 Object[] 索引）
     */
    public ColumnResolver(String tableName, List<String> columnNames) {
        this.tableName = tableName;
        this.nameToIndex = new HashMap<>();
        for (int i = 0; i < columnNames.size(); i++) {
            nameToIndex.put(columnNames.get(i).toLowerCase(), i);
        }
    }

    /**
     * 解析列名到数组索引。
     * 处理 "col"、"table.col"、"TABLE.COL" 等形式。
     *
     * @param columnName 列名
     * @return 列索引，未找到返回 -1
     */
    public int resolve(String columnName) {
        if (columnName == null) {
            return -1;
        }
        String normalized = columnName.toLowerCase();

        // 处理 "table.column" 形式
        int dotIdx = normalized.indexOf('.');
        if (dotIdx != -1) {
            String tablePart = normalized.substring(0, dotIdx);
            String colPart = normalized.substring(dotIdx + 1);
            if (tableName != null && tablePart.equals(tableName.toLowerCase())) {
                return nameToIndex.getOrDefault(colPart, -1);
            }
            return -1;
        }

        return nameToIndex.getOrDefault(normalized, -1);
    }

    public String getTableName() {
        return tableName;
    }

    public int getColumnCount() {
        return nameToIndex.size();
    }
}
