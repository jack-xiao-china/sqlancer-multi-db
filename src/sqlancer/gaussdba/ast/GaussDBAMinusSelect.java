package sqlancer.gaussdba.ast;

import java.util.List;

/**
 * MINUS query expression: SELECT ... MINUS SELECT ...
 * GaussDB-A (Oracle compatibility) uses MINUS instead of EXCEPT.
 */
public final class GaussDBAMinusSelect implements GaussDBAExpression {

    private final List<GaussDBASelect> selects;
    private final boolean minusAll;

    public GaussDBAMinusSelect(List<GaussDBASelect> selects, boolean minusAll) {
        if (selects == null || selects.isEmpty()) {
            throw new IllegalArgumentException("selects must not be null/empty");
        }
        this.selects = selects;
        this.minusAll = minusAll;
    }

    public List<GaussDBASelect> getSelects() {
        return selects;
    }

    public boolean isMinusAll() {
        return minusAll;
    }

    @Override
    public GaussDBADataType getExpressionType() {
        return null;
    }
}