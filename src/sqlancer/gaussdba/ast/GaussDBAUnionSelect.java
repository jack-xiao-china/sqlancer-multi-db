package sqlancer.gaussdba.ast;

import java.util.List;

/**
 * UNION query expression: SELECT ... UNION [ALL] SELECT ...
 * GaussDB-A (Oracle compatibility) supports UNION and UNION ALL.
 */
public final class GaussDBAUnionSelect implements GaussDBAExpression {

    private final List<GaussDBASelect> selects;
    private final boolean unionAll;

    public GaussDBAUnionSelect(List<GaussDBASelect> selects, boolean unionAll) {
        if (selects == null || selects.isEmpty()) {
            throw new IllegalArgumentException("selects must not be null/empty");
        }
        this.selects = selects;
        this.unionAll = unionAll;
    }

    public List<GaussDBASelect> getSelects() {
        return selects;
    }

    public boolean isUnionAll() {
        return unionAll;
    }

    @Override
    public GaussDBADataType getExpressionType() {
        return null;
    }
}