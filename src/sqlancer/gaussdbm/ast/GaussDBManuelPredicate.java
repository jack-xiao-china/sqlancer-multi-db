package sqlancer.gaussdbm.ast;

/**
 * Manual SQL fragment wrapper for wrapping arbitrary SQL text fragments. This class is used to inject raw SQL text into
 * the AST.
 */
public class GaussDBManuelPredicate implements GaussDBExpression {

    private final String predicate;

    public GaussDBManuelPredicate(String predicate) {
        this.predicate = predicate;
    }

    public String getString() {
        return predicate;
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        throw new AssertionError("GaussDBManuelPredicate");
    }
}