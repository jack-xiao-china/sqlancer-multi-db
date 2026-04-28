package sqlancer.gaussdbm;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility for normalizing boolean results in GaussDB M-compatibility mode. In M-compatibility, boolean values can be
 * returned as 't'/'f' (PG style) or 1/0 (MySQL style).
 */
public final class GaussDBMBooleanNormalizer {

    /**
     * Normalize a single result value. 't' or 'true' -> '1' 'f' or 'false' -> '0'
     */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ("t".equals(trimmed) || "true".equalsIgnoreCase(trimmed)) {
            return "1";
        }
        if ("f".equals(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return "0";
        }
        return value;
    }

    /**
     * Normalize a list of result values.
     */
    public static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream().map(GaussDBMBooleanNormalizer::normalize).collect(Collectors.toList());
    }

    /**
     * Compare two result values after normalization.
     */
    public static boolean equals(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na == null && nb == null) {
            return true;
        }
        if (na == null || nb == null) {
            return false;
        }
        return na.equals(nb);
    }

    /**
     * Compare two result lists after normalization.
     */
    public static boolean equalsList(List<String> a, List<String> b) {
        List<String> na = normalizeList(a);
        List<String> nb = normalizeList(b);
        if (na == null && nb == null) {
            return true;
        }
        if (na == null || nb == null) {
            return false;
        }
        if (na.size() != nb.size()) {
            return false;
        }
        for (int i = 0; i < na.size(); i++) {
            if (!na.get(i).equals(nb.get(i))) {
                return false;
            }
        }
        return true;
    }
}