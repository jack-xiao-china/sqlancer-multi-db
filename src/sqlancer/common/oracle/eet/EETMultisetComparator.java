package sqlancer.common.oracle.eet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Multiset equality for EET result comparison: order-insensitive, duplicates preserved.
 * Each row is converted to a string key by joining all column values with tab separators;
 * {@code null} cells become literal "null" for consistency with JDBC getString behavior.
 */
public final class EETMultisetComparator {

    private EETMultisetComparator() {
    }

    /**
     * Builds a stable string key for one row (tab-separated column values). {@code null} cells become literal
     * "null" for consistency with JDBC getString behavior on some drivers.
     */
    public static String rowKey(List<String> row) {
        if (row == null) {
            return "<null-row>";
        }
        List<String> parts = new ArrayList<>(row.size());
        for (String c : row) {
            parts.add(c == null ? "null" : c);
        }
        return String.join("\t", parts);
    }

    /**
     * Returns true iff the two multisets of rows are equal (same multiset of row keys).
     */
    public static boolean compareResultMultisets(List<List<String>> a, List<List<String>> b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.size() != b.size()) {
            return false;
        }
        List<String> keysA = toSortedKeys(a);
        List<String> keysB = toSortedKeys(b);
        return Objects.equals(keysA, keysB);
    }

    /**
     * Returns true iff two individual rows are equal (same values in same order).
     */
    public static boolean rowsEqual(List<String> a, List<String> b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            String va = a.get(i) == null ? "null" : a.get(i);
            String vb = b.get(i) == null ? "null" : b.get(i);
            if (!va.equals(vb)) {
                return false;
            }
        }
        return true;
    }

    static List<String> toSortedKeys(List<List<String>> rows) {
        List<String> keys = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            keys.add(rowKey(row));
        }
        Collections.sort(keys);
        return keys;
    }
}