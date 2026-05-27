package sqlancer.common.oracle.eet;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks each transformation step for back-transform (reversible reduction).
 * Each record captures the original expression, the transformed expression,
 * and the rule that was applied, enabling later reversal for bug minimization.
 */
public final class EETBackTransformTracker {

    private final List<TransformRecord> records = new ArrayList<>();

    public void record(Object originalExpr, Object transformedExpr, String ruleName, boolean isSemanticRewrite) {
        records.add(new TransformRecord(originalExpr, transformedExpr, ruleName, isSemanticRewrite));
    }

    public List<TransformRecord> getRecords() {
        return new ArrayList<>(records);
    }

    public int size() {
        return records.size();
    }

    public void clear() {
        records.clear();
    }

    /** Get records for semantic rewrite rules only. */
    public List<TransformRecord> getSemanticRewriteRecords() {
        List<TransformRecord> result = new ArrayList<>();
        for (TransformRecord r : records) {
            if (r.isSemanticRewrite) {
                result.add(r);
            }
        }
        return result;
    }

    public static final class TransformRecord {
        public final Object originalExpr;
        public final Object transformedExpr;
        public final String ruleName;
        public final boolean isSemanticRewrite;

        public TransformRecord(Object originalExpr, Object transformedExpr, String ruleName,
                boolean isSemanticRewrite) {
            this.originalExpr = originalExpr;
            this.transformedExpr = transformedExpr;
            this.ruleName = ruleName;
            this.isSemanticRewrite = isSemanticRewrite;
        }
    }
}