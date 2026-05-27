package sqlancer.fucci.lock;

/**
 * 锁类型枚举。
 * 定义MVCC中可能出现的各种锁类型。
 */
public enum LockType {
    /** 共享锁 - 用于SELECT FOR SHARE */
    SHARED_LOCK,

    /** 排他锁 - 用于SELECT FOR UPDATE, UPDATE, DELETE, INSERT */
    EXCLUSIVE_LOCK,

    /** 意向共享锁 - 表级别 */
    INTENTION_SHARED,

    /** 意向排他锁 - 表级别 */
    INTENTION_EXCLUSIVE,

    /** 间隙锁 - MySQL RR级别防止幻读 */
    GAP_LOCK,

    /** 记录锁 - 锁定具体行 */
    RECORD_LOCK,

    /** 插入意向锁 */
    INSERT_INTENTION,

    /** 下一键锁 - Record + Gap */
    NEXT_KEY_LOCK,

    /** 自增锁 */
    AUTO_INC_LOCK
}