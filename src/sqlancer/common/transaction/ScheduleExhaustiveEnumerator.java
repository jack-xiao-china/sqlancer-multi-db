package sqlancer.common.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Schedule exhaustive enumeration and reservoir sampling utility.
 *
 * References Troc's ShuffleTool.java which uses:
 * - Backtracking to enumerate all C(n1+n2, n1) valid interleavings
 * - Reservoir sampling for uniform random selection when full enumeration is too large
 *
 * HYBRID strategy:
 * - When total interleavings &lt;= EXHAUSTIVE_THRESHOLD: enumerate all
 * - When total interleavings &gt; EXHAUSTIVE_THRESHOLD: reservoir sample
 */
public final class ScheduleExhaustiveEnumerator {

    /** Threshold: enumerate all when total interleavings &lt;= this value. */
    private static final int EXHAUSTIVE_THRESHOLD = 1000;

    private ScheduleExhaustiveEnumerator() {
    }

    /**
     * Enumerate all valid interleavings of two transaction statement lists.
     * Uses backtracking (references Troc ShuffleTool.shuffle()).
     *
     * @param tx1Stmts transaction 1's statements (order preserved)
     * @param tx2Stmts transaction 2's statements (order preserved)
     * @return all C(n1+n2, n1) valid interleavings
     */
    public static List<List<TxStatement>> enumerateAll(
            List<TxStatement> tx1Stmts, List<TxStatement> tx2Stmts) {
        List<List<TxStatement>> results = new ArrayList<>();
        backtrack(results, new ArrayList<>(),
                tx1Stmts, tx1Stmts.size(), 0,
                tx2Stmts, tx2Stmts.size(), 0);
        return results;
    }

    private static void backtrack(
            List<List<TxStatement>> results, List<TxStatement> current,
            List<TxStatement> tx1, int tx1Len, int tx1Idx,
            List<TxStatement> tx2, int tx2Len, int tx2Idx) {
        if (tx1Idx == tx1Len && tx2Idx == tx2Len) {
            results.add(new ArrayList<>(current));
            return;
        }
        if (tx1Idx < tx1Len) {
            current.add(tx1.get(tx1Idx));
            backtrack(results, current, tx1, tx1Len, tx1Idx + 1, tx2, tx2Len, tx2Idx);
            current.remove(current.size() - 1);
        }
        if (tx2Idx < tx2Len) {
            current.add(tx2.get(tx2Idx));
            backtrack(results, current, tx1, tx1Len, tx1Idx, tx2, tx2Len, tx2Idx + 1);
            current.remove(current.size() - 1);
        }
    }

    /**
     * Reservoir sampling: uniformly select sampleSize schedules from allSchedules.
     * References Troc ShuffleTool.sampleSubmittedTrace().
     *
     * @param allSchedules full set of schedules
     * @param sampleSize   number to select
     * @return uniformly sampled subset
     */
    public static List<List<TxStatement>> reservoirSample(
            List<List<TxStatement>> allSchedules, int sampleSize) {
        if (allSchedules.size() <= sampleSize) {
            return allSchedules;
        }
        Random random = new Random();
        List<List<TxStatement>> reservoir = new ArrayList<>(sampleSize);
        for (int i = 0; i < sampleSize; i++) {
            reservoir.add(allSchedules.get(i));
        }
        for (int i = sampleSize; i < allSchedules.size(); i++) {
            int j = random.nextInt(i + 1);
            if (j < sampleSize) {
                reservoir.set(j, allSchedules.get(i));
            }
        }
        return reservoir;
    }

    /**
     * HYBRID strategy: enumerate all for small spaces, reservoir sample for large.
     *
     * @param tx1Stmts   transaction 1's statements
     * @param tx2Stmts   transaction 2's statements
     * @param sampleSize desired number of schedules (used when sampling)
     * @return list of schedules
     */
    public static List<List<TxStatement>> hybridGenerate(
            List<TxStatement> tx1Stmts, List<TxStatement> tx2Stmts, int sampleSize) {
        long totalSchedules = countSchedules(tx1Stmts.size(), tx2Stmts.size());
        if (totalSchedules <= EXHAUSTIVE_THRESHOLD) {
            List<List<TxStatement>> all = enumerateAll(tx1Stmts, tx2Stmts);
            if (all.size() <= sampleSize) {
                return all;
            }
            return reservoirSample(all, sampleSize);
        }
        // Large space: enumerate all then sample (memory-safe for 2 transactions with
        // typical 1-5 statements each; C(10,5)=252 is well within limits)
        List<List<TxStatement>> all = enumerateAll(tx1Stmts, tx2Stmts);
        return reservoirSample(all, sampleSize);
    }

    /**
     * Compute C(n1+n2, n1) = (n1+n2)! / (n1! * n2!).
     *
     * @return the number of valid interleavings, or Long.MAX_VALUE if overflow
     */
    public static long countSchedules(int n1, int n2) {
        int total = n1 + n2;
        if (total > 20) {
            return Long.MAX_VALUE;
        }
        long result = 1;
        int min = Math.min(n1, n2);
        for (int i = 0; i < min; i++) {
            result = result * (total - i) / (i + 1);
        }
        return result;
    }
}
