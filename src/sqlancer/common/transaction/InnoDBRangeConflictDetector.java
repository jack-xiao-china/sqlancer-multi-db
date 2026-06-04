package sqlancer.common.transaction;

/**
 * Detects range conflicts (phantom reads) between transactions under
 * InnoDB REPEATABLE_READ and SERIALIZABLE isolation levels.
 *
 * References Troc's Lock.isRangeConflict() which uses a snapshot-before-after
 * comparison to detect phantom rows.
 *
 * This implementation uses a simplified heuristic:
 * - Tracks each transaction's last locking SELECT (FOR UPDATE / FOR SHARE)
 * - When another transaction executes a write (INSERT/UPDATE/DELETE),
 *   checks if the write targets the same table as the locked range
 * - Reports potential conflicts conservatively (may produce false positives
 *   but avoids false negatives)
 *
 * Limitations:
 * - Does not model exact gap lock ranges (InnoDB internal behavior)
 * - Does not model INSERT INTENTION lock semantics precisely
 * - Only applies to MySQL/GaussDB-M (InnoDB-compatible engines)
 */
public final class InnoDBRangeConflictDetector {

    private InnoDBRangeConflictDetector() {
    }

    /**
     * Check if a write statement would conflict with another transaction's range lock.
     *
     * InnoDB RR/SER acquires Next-Key Locks (record + gap) for SELECT FOR UPDATE/SHARE.
     * A write from another transaction that targets the same table may be blocked
     * by these gap locks, causing a range conflict (phantom read scenario).
     *
     * @param writeStmtType    the type of the write statement (INSERT/UPDATE/DELETE)
     * @param writeTableName   the table targeted by the write statement
     * @param rangeLockTable   the table with an active range lock (from SELECT FOR UPDATE/SHARE)
     * @param rangeLockType    the type of range lock (RANGE_EXCLUSIVE or RANGE_SHARE)
     * @return true if a potential range conflict exists
     */
    public static boolean hasRangeConflict(TxStatementType writeStmtType, String writeTableName,
                                           String rangeLockTable, LockType rangeLockType) {
        // Range locks only apply under RR/SER isolation (caller should check)
        if (rangeLockType == null || !rangeLockType.isRangeLock()) {
            return false;
        }

        // Must be a write statement
        if (!isWriteType(writeStmtType)) {
            return false;
        }

        // Must target the same table
        if (!writeTableName.equalsIgnoreCase(rangeLockTable)) {
            return false;
        }

        // Conservative heuristic: any write to the same table with an active
        // range lock is a potential conflict. This may produce false positives
        // for writes that don't overlap with the locked range, but avoids
        // false negatives for phantom reads.
        return true;
    }

    /**
     * Check if a statement type is a locking SELECT (SELECT FOR UPDATE / SELECT FOR SHARE).
     * Under RR/SER isolation, these acquire Next-Key Locks.
     *
     * @param stmtType the statement type to check
     * @return true if the statement is a locking SELECT
     */
    public static boolean isLockingSelect(TxStatementType stmtType) {
        String typeName = stmtType.toString().toUpperCase();
        return typeName.equals("SELECT_FOR_UPDATE") || typeName.equals("SELECT_FOR_SHARE");
    }

    /**
     * Determine the lock type for a locking SELECT statement.
     *
     * @param stmtType the statement type
     * @return RANGE_EXCLUSIVE for SELECT FOR UPDATE, RANGE_SHARE for SELECT FOR SHARE,
     *         or NONE if not a locking SELECT
     */
    public static LockType getRangeLockType(TxStatementType stmtType) {
        String typeName = stmtType.toString().toUpperCase();
        if (typeName.equals("SELECT_FOR_UPDATE")) {
            return LockType.RANGE_EXCLUSIVE;
        }
        if (typeName.equals("SELECT_FOR_SHARE")) {
            return LockType.RANGE_SHARE;
        }
        return LockType.NONE;
    }

    /**
     * Check if a statement type is a write operation (INSERT, UPDATE, DELETE, REPLACE).
     */
    private static boolean isWriteType(TxStatementType stmtType) {
        String typeName = stmtType.toString().toUpperCase();
        return typeName.equals("INSERT") || typeName.equals("UPDATE")
                || typeName.equals("DELETE") || typeName.equals("REPLACE");
    }
}
