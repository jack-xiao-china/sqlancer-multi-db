package sqlancer.mariadb.ast;

/**
 * Manual SQL fragment wrapper for wrapping arbitrary SQL text fragments. This class is used to inject raw SQL text into
 * the AST.
 */
public class MariaDBManuelPredicate implements MariaDBExpression {

    private final String predicate;

    public MariaDBManuelPredicate(String predicate) {
        this.predicate = predicate;
    }

    public String getString() {
        return predicate;
    }
}