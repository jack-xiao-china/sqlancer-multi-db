package sqlancer.fucci.mvcc;

/**
 * Visibility rules for MVCC simulation per isolation level.
 * References Troc's TrocChecker visibility logic:
 * - RU: newView() — newest versions including uncommitted
 * - RC: buildTxView() — committed versions only
 * - RR: snapshotView() — snapshot at first SELECT
 * - SER: committed + SELECT takes SHARE lock
 */
public enum VisibilityRule {

    /** READ_UNCOMMITTED: see all versions including uncommitted. */
    NEWEST,

    /** READ_COMMITTED: see only committed versions. */
    COMMITTED,

    /** REPEATABLE_READ: see snapshot taken at first SELECT. */
    SNAPSHOT,

    /** SERIALIZABLE: same as COMMITTED + SELECT acquires SHARE lock. */
    COMMITTED_WITH_LOCK;

    /**
     * Map isolation level name to visibility rule.
     */
    public static VisibilityRule fromIsolationLevel(String isoLevel) {
        if (isoLevel == null) {
            return COMMITTED;
        }
        switch (isoLevel.toUpperCase().replace("_", " ").trim()) {
        case "READ UNCOMMITTED":
        case "READ_UNCOMMITTED":
            return NEWEST;
        case "READ COMMITTED":
        case "READ_COMMITTED":
            return COMMITTED;
        case "REPEATABLE READ":
        case "REPEATABLE_READ":
            return SNAPSHOT;
        case "SERIALIZABLE":
            return COMMITTED_WITH_LOCK;
        default:
            return COMMITTED;
        }
    }
}
