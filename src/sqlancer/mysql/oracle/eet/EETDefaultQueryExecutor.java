package sqlancer.mysql.oracle.eet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import sqlancer.IgnoreMeException;
import sqlancer.common.oracle.eet.EETQueryExecutor;
import sqlancer.common.oracle.eet.EETResultSetUtil;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.mysql.MySQLGlobalState;

public class EETDefaultQueryExecutor implements EETQueryExecutor {

    private final MySQLGlobalState state;
    private static final EETFailureStats FAILURE_STATS = new EETFailureStats();

    public EETDefaultQueryExecutor(MySQLGlobalState state) {
        this.state = state;
    }

    @Override
    public List<List<String>> executeQuery(String sql) throws SQLException {
        SQLQueryAdapter adapter = new SQLQueryAdapter(sql);
        // reportException=false: any SQL error returns null; oracle treats as IgnoreMeException (do not AssertionError
        // on unlisted MySQL messages — EET generates diverse shapes).
        try (SQLancerResultSet rs = adapter.executeAndGet(state, false)) {
            if (rs == null) {
                FAILURE_STATS.recordSqlFailure(state, sql);
                throw new IgnoreMeException();
            }
            return EETResultSetUtil.readAllRows(rs);
        } catch (SQLException e) {
            FAILURE_STATS.recordException(state, e);
            throw e;
        }
    }

    /**
     * Best-effort failure stats for EET "skipped" cases. This is intentionally isolated from oracle logic.
     * It writes aggregated counts into a file to help understand why EET success rate is low.
     */
    static final class EETFailureStats {
        private static final long PRINT_EVERY = 200;
        private static final Pattern SINGLE_QUOTED = Pattern.compile("'[^']*'");
        private static final Pattern NUMERIC = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");
        private static final Pattern HEX = Pattern.compile("0x[0-9a-fA-F]+");
        private static final Pattern WHITESPACE = Pattern.compile("\\s+");

        private final Map<String, LongAdder> counts = new ConcurrentHashMap<>();
        private final AtomicLong total = new AtomicLong();

        void recordSqlFailure(MySQLGlobalState state, String sql) {
            // Re-run only to capture SQLException message (do not affect oracle decisions).
            try {
                // Using a plain statement avoids SQLQueryAdapter's expected-error logic and returns the true DB error.
                state.getConnection().createStatement().executeQuery(sql);
            } catch (SQLException e) {
                recordException(state, e);
            } catch (Exception e) {
                recordMessage(state, "Non-SQL exception: " + safeMsg(e));
            }
        }

        void recordException(MySQLGlobalState state, SQLException e) {
            recordMessage(state, safeMsg(e));
        }

        private void recordMessage(MySQLGlobalState state, String rawMsg) {
            String key = normalize(rawMsg);
            counts.computeIfAbsent(key, k -> new LongAdder()).increment();
            long now = total.incrementAndGet();
            if (now % PRINT_EVERY == 0) {
                writeSummary(state, now);
            }
        }

        private static String normalize(String msg) {
            if (msg == null || msg.isBlank()) {
                return "<empty message>";
            }
            String s = msg;
            s = SINGLE_QUOTED.matcher(s).replaceAll("'?'");
            s = HEX.matcher(s).replaceAll("0x?");
            s = NUMERIC.matcher(s).replaceAll("?");
            s = WHITESPACE.matcher(s).replaceAll(" ").trim();
            // Keep it readable and stable as a map key.
            if (s.length() > 220) {
                s = s.substring(0, 220) + "...";
            }
            return s;
        }

        private static String safeMsg(Throwable t) {
            String m = t.getMessage();
            if (m != null && !m.isBlank()) {
                return m;
            }
            return t.getClass().getName();
        }

        private static Path resolveOutputPath(MySQLGlobalState state) {
            // Prefer <project>/target/eet-failures.txt when running from module root.
            Path p1 = Paths.get("target", "eet-failures.txt");
            if (Files.exists(p1.getParent())) {
                return p1;
            }
            // When running from the shaded jar in target/, write next to it.
            return Paths.get("eet-failures.txt");
        }

        private void writeSummary(MySQLGlobalState state, long now) {
            Path out = resolveOutputPath(state);
            StringBuilder sb = new StringBuilder();
            sb.append("\n=== EET failure stats (skipped queries) ===\n");
            sb.append("total_failures=").append(now).append("\n");
            // Print top 10
            counts.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()))
                    .limit(10)
                    .forEach(e -> sb.append(e.getValue().sum()).append("\t").append(e.getKey()).append("\n"));
            try {
                Files.writeString(out, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // Avoid affecting oracle execution.
            }
        }
    }
}
