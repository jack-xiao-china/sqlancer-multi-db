package sqlancer.fucci.mvcc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * MVCC视图数据结构。
 * 表示某个时间点/事务可见的数据快照。
 */
public class View {

    /** 行ID到行数据的映射 */
    private Map<Integer, Object[]> data;

    /** 行ID到删除状态的映射 */
    private Map<Integer, Boolean> deleted;

    /** 视图创建时间戳 */
    private long timestamp;

    public View() {
        this.data = new HashMap<>();
        this.deleted = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }

    public View(boolean trackDeleted) {
        this.data = new HashMap<>();
        this.deleted = trackDeleted ? new HashMap<>() : null;
        this.timestamp = System.currentTimeMillis();
    }

    public void putRow(int rowId, Object[] rowData) {
        data.put(rowId, rowData);
    }

    public void putRow(int rowId, Object[] rowData, boolean isDeleted) {
        data.put(rowId, rowData);
        if (deleted != null) {
            deleted.put(rowId, isDeleted);
        }
    }

    public Object[] getRow(int rowId) {
        return data.get(rowId);
    }

    public boolean containsRow(int rowId) {
        return data.containsKey(rowId);
    }

    public boolean isDeleted(int rowId) {
        if (deleted == null) {
            return false;
        }
        Boolean del = deleted.get(rowId);
        return del != null && del;
    }

    public Set<Integer> getRowIds() {
        return data.keySet();
    }

    public int getRowCount() {
        return data.size();
    }

    public void removeRow(int rowId) {
        data.remove(rowId);
        if (deleted != null) {
            deleted.remove(rowId);
        }
    }

    public void clear() {
        data.clear();
        if (deleted != null) {
            deleted.clear();
        }
    }

    public View copy() {
        View newView = new View(deleted != null);
        for (Map.Entry<Integer, Object[]> entry : data.entrySet()) {
            Object[] rowData = entry.getValue();
            Object[] dataCopy = rowData != null ? rowData.clone() : null;
            newView.data.put(entry.getKey(), dataCopy);
        }
        if (deleted != null) {
            newView.deleted.putAll(deleted);
        }
        return newView;
    }

    public Object getColumnValue(int rowId, int columnIndex) {
        Object[] rowData = data.get(rowId);
        if (rowData == null || columnIndex < 0 || columnIndex >= rowData.length) {
            return null;
        }
        return rowData[columnIndex];
    }

    // Getter methods
    public Map<Integer, Object[]> getData() { return data; }
    public Map<Integer, Boolean> getDeleted() { return deleted; }
    public long getTimestamp() { return timestamp; }

    // Setter methods
    public void setData(Map<Integer, Object[]> data) { this.data = data; }
    public void setDeleted(Map<Integer, Boolean> deleted) { this.deleted = deleted; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("View{rows=").append(data.size()).append(", deleted=");
        sb.append(deleted != null ? deleted.size() : "N/A").append("}\n");
        for (Map.Entry<Integer, Object[]> entry : data.entrySet()) {
            int rowId = entry.getKey();
            Object[] rowData = entry.getValue();
            boolean isDel = isDeleted(rowId);
            sb.append(String.format("  Row %d: %s (del=%s)\n", rowId,
                    Arrays.toString(rowData), isDel));
        }
        return sb.toString();
    }

    public String toSimpleString() {
        return String.format("View{rows=%d}", data.size());
    }
}