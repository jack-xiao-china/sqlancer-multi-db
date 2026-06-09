package sqlancer.common.oracle.jir;

import java.util.Collections;
import java.util.List;

/**
 * Holds the source query and its target transformation queries for a JIR rule check.
 */
public class JIRQuerySet {

    private final String sourceQuery;
    private final List<String> targetQueries;
    private final JIRResultType resultType;
    private final JIRRule rule;

    public JIRQuerySet(String sourceQuery, List<String> targetQueries, JIRResultType resultType, JIRRule rule) {
        this.sourceQuery = sourceQuery;
        this.targetQueries = Collections.unmodifiableList(targetQueries);
        this.resultType = resultType;
        this.rule = rule;
    }

    public String getSourceQuery() {
        return sourceQuery;
    }

    public List<String> getTargetQueries() {
        return targetQueries;
    }

    public JIRResultType getResultType() {
        return resultType;
    }

    public JIRRule getRule() {
        return rule;
    }
}
