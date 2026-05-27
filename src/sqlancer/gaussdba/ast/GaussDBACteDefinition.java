package sqlancer.gaussdba.ast;

/**
 * CTE (Common Table Expression) definition for WITH clause.
 * WITH cte_name AS (SELECT ...) ...
 */
public final class GaussDBACteDefinition {

    private final String name;
    private final GaussDBASelect select;

    public GaussDBACteDefinition(String name, GaussDBASelect select) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null/blank");
        }
        if (select == null) {
            throw new IllegalArgumentException("select must not be null");
        }
        this.name = name;
        this.select = select;
    }

    public String getName() {
        return name;
    }

    public GaussDBASelect getSelect() {
        return select;
    }
}