package sqlancer.gaussdba.ast;

/**
 * Derived table (subquery in FROM clause).
 * SELECT ... FROM (SELECT ...) AS alias
 */
public final class GaussDBADerivedTable implements GaussDBAExpression {

    private final GaussDBASelect select;
    private final String alias;

    public GaussDBADerivedTable(GaussDBASelect select, String alias) {
        if (select == null) {
            throw new IllegalArgumentException("select must not be null");
        }
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("alias must not be null/blank");
        }
        this.select = select;
        this.alias = alias;
    }

    public GaussDBASelect getSelect() {
        return select;
    }

    public String getAlias() {
        return alias;
    }

    @Override
    public GaussDBADataType getExpressionType() {
        return null;
    }
}