package sqlancer.fucci.lock;

/**
 * 锁对象定义。
 * 表示锁定的范围，可以是表、行、间隙等。
 */
public class LockObject {

    /** 锁对象类型 */
    private LockObjectType objectType;

    /** 表名 */
    private String tableName;

    /** 索引名(可选) */
    private String indexName;

    /** 行ID(行锁时使用) */
    private Integer rowId;

    /** 间隙起始ID(Gap锁时使用) */
    private Integer gapStartId;

    /** 间隙结束ID(Gap锁时使用) */
    private Integer gapEndId;

    /**
     * 锁对象类型枚举
     */
    public enum LockObjectType {
        /** 表级锁 */
        TABLE,
        /** 行级锁 */
        ROW,
        /** 间隙锁 */
        GAP,
        /** 下一键锁(行+间隙) */
        NEXT_KEY,
        /** 插入意向锁 */
        INSERT_INTENTION
    }

    public LockObject() {
    }

    public LockObject(String tableName) {
        this.objectType = LockObjectType.TABLE;
        this.tableName = tableName;
    }

    public LockObject(String tableName, int rowId) {
        this.objectType = LockObjectType.ROW;
        this.tableName = tableName;
        this.rowId = rowId;
    }

    public LockObject(String tableName, int gapStartId, int gapEndId, boolean isGap) {
        this.objectType = LockObjectType.GAP;
        this.tableName = tableName;
        this.gapStartId = gapStartId;
        this.gapEndId = gapEndId;
    }

    public static LockObject createGapLock(String tableName, int gapStartId, int gapEndId) {
        LockObject obj = new LockObject();
        obj.objectType = LockObjectType.GAP;
        obj.tableName = tableName;
        obj.gapStartId = gapStartId;
        obj.gapEndId = gapEndId;
        return obj;
    }

    public static LockObject createNextKeyLock(String tableName, int rowId, int gapEndId) {
        LockObject obj = new LockObject();
        obj.objectType = LockObjectType.NEXT_KEY;
        obj.tableName = tableName;
        obj.rowId = rowId;
        obj.gapEndId = gapEndId;
        return obj;
    }

    public boolean isCompatibleWith(LockObject other) {
        if (!tableName.equals(other.tableName)) {
            return true;
        }
        if (objectType == other.objectType) {
            switch (objectType) {
                case TABLE:
                    return true;
                case ROW:
                    return rowId.equals(other.rowId);
                case GAP:
                    return !intervalsOverlap(gapStartId, gapEndId, other.gapStartId, other.gapEndId);
                case NEXT_KEY:
                    return rowId.equals(other.rowId);
                default:
                    return true;
            }
        }
        if (objectType == LockObjectType.TABLE || other.objectType == LockObjectType.TABLE) {
            return false;
        }
        if (objectType == LockObjectType.ROW && other.objectType == LockObjectType.GAP) {
            return !isRowInGap(rowId, other.gapStartId, other.gapEndId);
        }
        if (objectType == LockObjectType.GAP && other.objectType == LockObjectType.ROW) {
            return !isRowInGap(other.rowId, gapStartId, gapEndId);
        }
        return true;
    }

    private boolean intervalsOverlap(int start1, int end1, int start2, int end2) {
        return start1 <= end2 && start2 <= end1;
    }

    private boolean isRowInGap(int rowId, int gapStart, int gapEnd) {
        return rowId > gapStart && rowId < gapEnd;
    }

    // Getter methods
    public LockObjectType getObjectType() { return objectType; }
    public String getTableName() { return tableName; }
    public String getIndexName() { return indexName; }
    public Integer getRowId() { return rowId; }
    public Integer getGapStartId() { return gapStartId; }
    public Integer getGapEndId() { return gapEndId; }

    // Setter methods
    public void setObjectType(LockObjectType objectType) { this.objectType = objectType; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }
    public void setRowId(Integer rowId) { this.rowId = rowId; }
    public void setGapStartId(Integer gapStartId) { this.gapStartId = gapStartId; }
    public void setGapEndId(Integer gapEndId) { this.gapEndId = gapEndId; }

    @Override
    public String toString() {
        switch (objectType) {
            case TABLE:
                return String.format("LockObject{TABLE, table=%s}", tableName);
            case ROW:
                return String.format("LockObject{ROW, table=%s, row=%d}", tableName, rowId);
            case GAP:
                return String.format("LockObject{GAP, table=%s, (%d,%d)}", tableName, gapStartId, gapEndId);
            case NEXT_KEY:
                return String.format("LockObject{NEXT_KEY, table=%s, row=%d, gapEnd=%d}", tableName, rowId, gapEndId);
            default:
                return String.format("LockObject{type=%s, table=%s}", objectType, tableName);
        }
    }

    public String toSimpleString() {
        switch (objectType) {
            case TABLE:
                return tableName;
            case ROW:
                return tableName + "[" + rowId + "]";
            case GAP:
                return tableName + "(" + gapStartId + "," + gapEndId + ")";
            default:
                return tableName;
        }
    }
}