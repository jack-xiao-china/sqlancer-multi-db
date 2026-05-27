package sqlancer.gaussdba.ast;

import java.util.List;

/**
 * WITH clause SELECT: WITH cte_name AS (SELECT ...) SELECT ...
 */
public final class GaussDBAWithSelect implements GaussDBAExpression {

    private final List<GaussDBACteDefinition> ctes;
    private final GaussDBASelect mainSelect;

    public GaussDBAWithSelect(List<GaussDBACteDefinition> ctes, GaussDBASelect mainSelect) {
        if (ctes == null || ctes.isEmpty()) {
            throw new IllegalArgumentException("ctes must not be null/empty");
        }
        if (mainSelect == null) {
            throw new IllegalArgumentException("mainSelect must not be null");
        }
        this.ctes = ctes;
        this.mainSelect = mainSelect;
    }

    public List<GaussDBACteDefinition> getCtes() {
        return ctes;
    }

    public GaussDBASelect getMainSelect() {
        return mainSelect;
    }

    @Override
    public GaussDBADataType getExpressionType() {
        return null;
    }
}