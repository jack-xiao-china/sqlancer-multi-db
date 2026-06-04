package sqlancer.common.transaction;

/**
 * Lock types for transaction conflict modeling.
 *
 * References Troc's Lock.java LockType enum, extended with InnoDB-specific
 * range lock types that arise under REPEATABLE_READ and SERIALIZABLE isolation.
 */
public enum LockType {

    /** No lock held. */
    NONE,

    /** Shared lock: SELECT FOR SHARE / LOCK IN SHARE MODE. */
    ROW_SHARE,

    /** Exclusive lock: UPDATE / DELETE / INSERT on specific rows. */
    ROW_EXCLUSIVE,

    /**
     * Range share lock: SELECT FOR SHARE under RR/SER isolation.
     * InnoDB acquires Next-Key Lock (record lock + gap lock) on the selected range.
     */
    RANGE_SHARE,

    /**
     * Range exclusive lock: SELECT FOR UPDATE under RR/SER isolation.
     * InnoDB acquires Next-Key Lock on the selected range.
     */
    RANGE_EXCLUSIVE,

    /**
     * Insert intention lock: implicitly acquired by INSERT.
     * Conflicts with existing gap locks (RANGE_SHARE, RANGE_EXCLUSIVE).
     */
    INSERT_INTENTION;

    /**
     * Check if this lock type represents a range lock (gap + record lock).
     */
    public boolean isRangeLock() {
        return this == RANGE_SHARE || this == RANGE_EXCLUSIVE;
    }

    /**
     * Check if this lock type conflicts with the given lock type.
     *
     * Conflict matrix (simplified InnoDB model):
     * - ROW_EXCLUSIVE conflicts with any non-NONE lock on the same row
     * - ROW_SHARE conflicts with ROW_EXCLUSIVE
     * - RANGE_EXCLUSIVE conflicts with any lock on the same range
     * - RANGE_SHARE conflicts with ROW_EXCLUSIVE and RANGE_EXCLUSIVE
     * - INSERT_INTENTION conflicts with RANGE_SHARE and RANGE_EXCLUSIVE
     */
    public boolean conflictsWith(LockType other) {
        if (this == NONE || other == NONE) {
            return false;
        }
        if (this == ROW_EXCLUSIVE || other == ROW_EXCLUSIVE) {
            return true;
        }
        if (this == INSERT_INTENTION && other.isRangeLock()) {
            return true;
        }
        if (other == INSERT_INTENTION && this.isRangeLock()) {
            return true;
        }
        if (this == RANGE_EXCLUSIVE || other == RANGE_EXCLUSIVE) {
            return true;
        }
        // ROW_SHARE vs ROW_SHARE: no conflict
        // RANGE_SHARE vs RANGE_SHARE: no conflict
        return false;
    }
}
