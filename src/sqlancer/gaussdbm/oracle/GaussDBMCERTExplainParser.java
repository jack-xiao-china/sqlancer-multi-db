package sqlancer.gaussdbm.oracle;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

import sqlancer.common.oracle.CERTOracle;
import sqlancer.common.query.SQLancerResultSet;

/**
 * Maps EXPLAIN result columns by label (GaussDB-M / MySQL-compatible layouts differ from fixed JDBC indices).
 */
public final class GaussDBMCERTExplainParser {

    private GaussDBMCERTExplainParser() {
    }

    public static CERTOracle.CheckedFunction<SQLancerResultSet, Optional<Long>> rowCountParser() {
        return rs -> {
            int idx = findColumnIndex(rs, "rows");
            if (idx >= 0) {
                try {
                    return Optional.of(rs.getLong(idx));
                } catch (SQLException e) {
                    String s = rs.getString(idx);
                    if (s == null || s.isEmpty()) {
                        return Optional.empty();
                    }
                    try {
                        return Optional.of(Long.parseLong(s.trim()));
                    } catch (NumberFormatException ex) {
                        return Optional.empty();
                    }
                }
            }
            // PG-style "normal" EXPLAIN: a single text column whose plan lines look like
            // "Seq Scan on t  (cost=0.00..21.10 rows=296 width=62)". Extract the root estimate
            // (getRow keeps the first present value, i.e. the top plan node = result cardinality).
            return extractRowsFromText(rs.getString(1));
        };
    }

    /**
     * One token per EXPLAIN row for plan-shape comparison (aligned with MySQL using a stable per-row identifier).
     */
    public static CERTOracle.CheckedFunction<SQLancerResultSet, Optional<String>> queryPlanParser() {
        return rs -> {
            int idx = findColumnIndex(rs, "type", "select_type", "operation");
            if (idx < 0) {
                idx = findColumnIndex(rs, "table", "TABLE");
            }
            if (idx >= 0) {
                return Optional.ofNullable(rs.getString(idx));
            }
            // PG-style "normal" EXPLAIN: use the leading token of each plan line (e.g. "Seq",
            // "Index", "Hash") as a stable per-row plan-shape identifier. Skip marker lines
            // like "[Parameterized]" and blanks.
            String line = rs.getString(1);
            if (line == null) {
                return Optional.empty();
            }
            line = line.trim();
            if (line.isEmpty() || line.startsWith("[")) {
                return Optional.empty();
            }
            String[] parts = line.split("\\s+");
            return parts.length > 0 ? Optional.of(parts[0]) : Optional.empty();
        };
    }

    private static int findColumnIndex(SQLancerResultSet rs, String... candidates) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        for (int i = 1; i <= n; i++) {
            String label = md.getColumnLabel(i);
            if (label == null || label.isEmpty()) {
                label = md.getColumnName(i);
            }
            if (label == null) {
                continue;
            }
            String norm = label.toLowerCase(Locale.ROOT);
            for (String c : candidates) {
                if (norm.equals(c.toLowerCase(Locale.ROOT))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Optional<Long> extractRowsFromText(String line) {
        if (line == null) {
            return Optional.empty();
        }
        int idx = line.indexOf("rows=");
        if (idx < 0) {
            return Optional.empty();
        }
        StringBuilder num = new StringBuilder();
        for (int i = idx + "rows=".length(); i < line.length(); i++) {
            char ch = line.charAt(i);
            if (Character.isDigit(ch)) {
                num.append(ch);
            } else {
                break;
            }
        }
        if (num.length() == 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(num.toString()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
