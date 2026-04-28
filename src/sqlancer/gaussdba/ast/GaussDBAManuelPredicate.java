package sqlancer.gaussdba.ast;

/**
 * Manual SQL fragment wrapper for wrapping arbitrary SQL text fragments. This class is used to inject raw SQL text into
 * the AST.
 */
public class GaussDBAManuelPredicate implements GaussDBAExpression {

    private final String predicate;

    public GaussDBAManuelPredicate(String predicate) {
        this.predicate = predicate;
    }

    public String getString() {
        return predicate;
    }

    @Override
    public GaussDBADataType getExpressionType() {
        return null;
    }
}