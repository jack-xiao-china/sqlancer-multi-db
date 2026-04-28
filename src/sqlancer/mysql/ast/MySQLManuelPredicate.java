package sqlancer.mysql.ast;

/**
 * Manual SQL fragment wrapper for wrapping arbitrary SQL text fragments. This class is used to inject raw SQL text into
 * the AST.
 */
public class MySQLManuelPredicate implements MySQLExpression {

    private final String predicate;

    public MySQLManuelPredicate(String predicate) {
        this.predicate = predicate;
    }

    public String getString() {
        return predicate;
    }

}