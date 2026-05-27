package sqlancer.common.oracle.eet;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks impedance (execution failure rate) per expression type and rule,
 * enabling automatic blacklisting of high-failure-rate (>99%) production types.
 *
 * EET native tracks each production's success rate; this provides the same
 * capability by counting success/failure outcomes per expression type.
 */
public final class EETImpedanceTracker {

    private final Map<String, Stats> statsMap = new HashMap<>();
    private static final double BLACKLIST_THRESHOLD = 0.99;

    public void recordSuccess(String expressionType) {
        Stats s = statsMap.computeIfAbsent(expressionType, k -> new Stats());
        s.successes++;
    }

    public void recordFailure(String expressionType) {
        Stats s = statsMap.computeIfAbsent(expressionType, k -> new Stats());
        s.failures++;
    }

    public boolean isBlacklisted(String expressionType) {
        Stats s = statsMap.get(expressionType);
        if (s == null) {
            return false;
        }
        long total = s.successes + s.failures;
        if (total < 100) {
            return false; // need minimum sample size before blacklisting
        }
        double failureRate = (double) s.failures / total;
        return failureRate >= BLACKLIST_THRESHOLD;
    }

    public Map<String, Stats> getStats() {
        return new HashMap<>(statsMap);
    }

    public void clear() {
        statsMap.clear();
    }

    public static final class Stats {
        public long successes;
        public long failures;

        public double failureRate() {
            long total = successes + failures;
            return total == 0 ? 0.0 : (double) failures / total;
        }
    }
}