package sqlancer.gaussdba.ast;

/**
 * Reference to a CTE table in FROM clause.
 * Used after WITH cte_name AS (...) SELECT ... FROM cte_name
 */
public final class GaussDBACteTableReference implements GaussDBAExpression {

    private final String name;

    public GaussDBACteTableReference(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null/blank");
        }
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public GaussDBADataType getExpressionType() {
        return null;
    }
}