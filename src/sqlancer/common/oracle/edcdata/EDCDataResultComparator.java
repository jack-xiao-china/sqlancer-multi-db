package sqlancer.common.oracle.edcdata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result comparator for EDC_DATA test scenarios.
 * Compares query results with sorting-based approach.
 * Equivalent to EDC Python's result comparison logic.
 */
public class EDCDataResultComparator {

    /**
     * Compare two result lists for equality.
     * Sorts both results before comparison to handle order-independent comparison.
     *
     * @param result1 First result list (each element is a row)
     * @param result2 Second result list (each element is a row)
     * @return true if results are equal, false otherwise
     */
    public boolean areEqual(List<String> result1, List<String> result2) {
        if (result1 == null && result2 == null) {
            return true;
        }
        if (result1 == null || result2 == null) {
            return false;
        }

        // Sort both results before comparison
        List<String> sorted1 = new ArrayList<>(result1);
        List<String> sorted2 = new ArrayList<>(result2);
        Collections.sort(sorted1);
        Collections.sort(sorted2);

        if (sorted1.size() != sorted2.size()) {
            return false;
        }

        for (int i = 0; i < sorted1.size(); i++) {
            if (!normalizeValue(sorted1.get(i)).equals(normalizeValue(sorted2.get(i)))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Normalize a result value for comparison.
     * Handles NULL, trailing zeros, and whitespace.
     *
     * @param value Value to normalize
     * @return Normalized value
     */
    private String normalizeValue(String value) {
        if (value == null || value.equalsIgnoreCase("NULL")) {
            return "NULL";
        }

        // Remove trailing zeros: "1.000" -> "1"
        value = value.replaceAll("\\.0+$", "");

        // Trim whitespace
        value = value.trim();

        return value;
    }
}
