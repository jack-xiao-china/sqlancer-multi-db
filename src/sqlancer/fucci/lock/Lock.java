package sqlancer.fucci.lock;

/**
 * 锁数据结构。
 * 表示事务持有的某个锁对象上的锁。
 */
public class Lock {

    /** 持有此锁的事务ID */
    private long transactionId;

    /** 锁对象 */
    private LockObject lockObject;

    /** 锁类型 */
    private LockType lockType;

    /** 锁请求时间戳 */
    private long requestTimestamp;

    /** 锁授予时间戳 */
    private long grantTimestamp;

    /** 锁状态 */
    private LockStatus status;

    /** 是否等待中 */
    private boolean waiting;

    /**
     * 锁状态枚举
     */
    public enum LockStatus {
        WAITING,
        GRANTED,
        RELEASED,
        BLOCKED
    }

    public Lock() {
        this.requestTimestamp = System.currentTimeMillis();
        this.status = LockStatus.WAITING;
    }

    public Lock(long transactionId, LockObject lockObject, LockType lockType) {
        this.transactionId = transactionId;
        this.lockObject = lockObject;
        this.lockType = lockType;
        this.requestTimestamp = System.currentTimeMillis();
        this.status = LockStatus.WAITING;
    }

    public boolean isCompatibleWith(Lock other) {
        if (!lockObject.isCompatibleWith(other.lockObject)) {
            return false;
        }
        return areLockTypesCompatible(lockType, other.lockType);
    }

    private boolean areLockTypesCompatible(LockType type1, LockType type2) {
        if (type1 == LockType.SHARED_LOCK && type2 == LockType.SHARED_LOCK) {
            return true;
        }
        if (type1 == LockType.SHARED_LOCK && type2 == LockType.INTENTION_SHARED) {
            return true;
        }
        if (type1 == LockType.INTENTION_SHARED && type2 == LockType.SHARED_LOCK) {
            return true;
        }
        if (type1 == LockType.INTENTION_SHARED && type2 == LockType.INTENTION_SHARED) {
            return true;
        }
        if (type1 == LockType.EXCLUSIVE_LOCK || type2 == LockType.EXCLUSIVE_LOCK) {
            return false;
        }
        if (type1 == LockType.GAP_LOCK && type2 == LockType.GAP_LOCK) {
            return true;
        }
        if (type1 == LockType.INSERT_INTENTION || type2 == LockType.INSERT_INTENTION) {
            if (type1 == LockType.GAP_LOCK || type2 == LockType.GAP_LOCK) {
                return false;
            }
        }
        return false;
    }

    public void grant() {
        this.status = LockStatus.GRANTED;
        this.grantTimestamp = System.currentTimeMillis();
        this.waiting = false;
    }

    public void release() {
        this.status = LockStatus.RELEASED;
    }

    public void block() {
        this.status = LockStatus.BLOCKED;
        this.waiting = true;
    }

    public boolean isGranted() {
        return status == LockStatus.GRANTED;
    }

    public boolean isReleased() {
        return status == LockStatus.RELEASED;
    }

    public boolean isWaiting() {
        return status == LockStatus.WAITING || waiting;
    }

    public long getHoldDuration() {
        if (status == LockStatus.GRANTED) {
            return System.currentTimeMillis() - grantTimestamp;
        }
        return 0;
    }

    public long getWaitDuration() {
        if (grantTimestamp > 0) {
            return grantTimestamp - requestTimestamp;
        }
        return System.currentTimeMillis() - requestTimestamp;
    }

    // Getter methods
    public long getTransactionId() { return transactionId; }
    public LockObject getLockObject() { return lockObject; }
    public LockType getLockType() { return lockType; }
    public long getRequestTimestamp() { return requestTimestamp; }
    public long getGrantTimestamp() { return grantTimestamp; }
    public LockStatus getStatus() { return status; }
    public boolean isWaitingFlag() { return waiting; }

    // Setter methods
    public void setTransactionId(long transactionId) { this.transactionId = transactionId; }
    public void setLockObject(LockObject lockObject) { this.lockObject = lockObject; }
    public void setLockType(LockType lockType) { this.lockType = lockType; }
    public void setRequestTimestamp(long requestTimestamp) { this.requestTimestamp = requestTimestamp; }
    public void setGrantTimestamp(long grantTimestamp) { this.grantTimestamp = grantTimestamp; }
    public void setStatus(LockStatus status) { this.status = status; }
    public void setWaiting(boolean waiting) { this.waiting = waiting; }

    @Override
    public String toString() {
        return String.format("Lock{tx=%d, obj=%s, type=%s, status=%s, waiting=%s}",
                transactionId, lockObject.toSimpleString(), lockType, status, waiting);
    }

    public String toSimpleString() {
        return String.format("T%d-%s-%s", transactionId, lockObject.toSimpleString(), lockType);
    }
}