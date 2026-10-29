package sqlancer.common.oracle;

/**
 * Detects nondeterministic function calls in a rendered query. Such calls legitimately differ
 * between the two executions of a metamorphic comparison (e.g. TLP's original vs partitioned
 * query or CODDTEST's original vs folded query), so a mismatch on such rounds is meaningless and
 * the round must be skipped.
 *
 * Visitors render function names canonically (upper case), so a plain contains() suffices; SLEEP
 * also perturbs run-to-run timing semantics.
 */
public final class NondeterminismGuard {

    private NondeterminismGuard() {
    }

    public static boolean containsNondeterministicCall(String sql) {
        for (String fn : new String[] { "CURTIME(", "CURDATE(", "NOW(", "SYSDATE(", "RAND(", "UUID(",
                "UUID_SHORT(", "UNIX_TIMESTAMP(", "CURRENT_TIMESTAMP", "SLEEP(" }) {
            if (sql.contains(fn)) {
                return true;
            }
        }
        return false;
    }
}
