package sqlancer.fucci.mvcc;

import java.util.Arrays;

/**
 * MVCC版本数据结构。
 * 表示一行数据在某个事务下的版本。
 */
public class Version {

    /** 行数据 */
    private Object[] data;

    /** 创建此版本的事务 */
    private Object transaction;

    /** 是否已删除 */
    private boolean deleted;

    /** 版本创建时间戳 */
    private long timestamp;

    public Version(Object[] data, Object transaction, boolean deleted) {
        this.data = data;
        this.transaction = transaction;
        this.deleted = deleted;
        this.timestamp = System.currentTimeMillis();
    }

    public Version(Object[] data, Object transaction, boolean deleted, long timestamp) {
        this.data = data;
        this.transaction = transaction;
        this.deleted = deleted;
        this.timestamp = timestamp;
    }

    public Version copy() {
        Object[] dataCopy = data != null ? data.clone() : null;
        return new Version(dataCopy, transaction, deleted, timestamp);
    }

    public Object getColumnValue(int columnIndex) {
        if (data == null || columnIndex < 0 || columnIndex >= data.length) {
            return null;
        }
        return data[columnIndex];
    }

    public void setColumnValue(int columnIndex, Object value) {
        if (data != null && columnIndex >= 0 && columnIndex < data.length) {
            data[columnIndex] = value;
        }
    }

    // Getter methods
    public Object[] getData() { return data; }
    public Object getTransaction() { return transaction; }
    public boolean isDeleted() { return deleted; }
    public long getTimestamp() { return timestamp; }

    // Setter methods
    public void setData(Object[] data) { this.data = data; }
    public void setTransaction(Object transaction) { this.transaction = transaction; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Version{");
        sb.append("data=").append(Arrays.toString(data));
        sb.append(", tx=").append(transaction != null ? transaction.getClass().getSimpleName() : "null");
        sb.append(", deleted=").append(deleted);
        sb.append(", ts=").append(timestamp);
        sb.append("}");
        return sb.toString();
    }

    public String toSimpleString(int rowId) {
        return String.format("V{row=%d, del=%s, tx=%s}", rowId, deleted,
                transaction != null ? transaction.toString() : "init");
    }
}