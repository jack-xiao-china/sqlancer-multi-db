package sqlancer.common.oracle.eet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Crash detection and logging for EET oracle.
 *
 * EET native distinguishes logic bugs from crash bugs. In SQLancer, SQLExceptions
 * are normally caught and silently ignored. This tracker identifies crash-level
 * errors (internal errors, connection drops, timeouts) that may indicate DBMS crashes,
 * distinct from normal syntax/type/constraint errors.
 *
 * Crash-level SQL error codes (common across MySQL and PostgreSQL):
 * - 08xxx: Connection exception
 * - XXxxx: Internal error (PostgreSQL)
 * - HYxxx: CLI-specific errors
 * - MySQL error 1040-1099: Connection/handshake errors
 * - MySQL error 2000+: Client errors indicating server-side crash
 */
public final class EETCrashTracker {

    private static final File CRASH_LOG_FILE = new File("eet_crash_log.txt");
    private static final EETCrashTracker INSTANCE = new EETCrashTracker();

    private final BufferedWriter writer;

    private EETCrashTracker() {
        try {
            writer = new BufferedWriter(new FileWriter(CRASH_LOG_FILE, true));
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize EET crash log: " + e.getMessage(), e);
        }
    }

    public static EETCrashTracker getInstance() {
        return INSTANCE;
    }

    /** Determine if a SQLException state/code indicates a potential crash. */
    public static boolean isCrashError(String sqlState, int errorCode) {
        if (sqlState == null) {
            return false;
        }
        // Connection exceptions (08xxx)
        if (sqlState.startsWith("08")) {
            return true;
        }
        // Internal errors (XXxxx — PostgreSQL-specific)
        if (sqlState.startsWith("XX")) {
            return true;
        }
        // System errors (58xxx — PostgreSQL)
        if (sqlState.startsWith("58")) {
            return true;
        }
        // MySQL server crash indicators
        if (errorCode >= 1040 && errorCode <= 1099) {
            return true;
        }
        if (errorCode >= 2000) {
            return true;
        }
        return false;
    }

    /** Log a potential crash event. */
    public void logCrash(String dbms, String sqlState, int errorCode, String query, String message) {
        try {
            writer.write(String.format("[%s] DBMS=%s SQLState=%s ErrorCode=%d Query=%s Message=%s\n",
                    LocalDateTime.now(), dbms, sqlState, errorCode, abbreviate(query, 200), message));
            writer.flush();
        } catch (IOException e) {
            // Best effort — don't crash the oracle for logging failures
        }
    }

    private static String abbreviate(String s, int maxLen) {
        if (s == null) {
            return "<null>";
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...";
    }
}