package sqlancer.mysql.ast;

/**
 * Represents ANY/ALL comparison subquery expressions like:
 * - expr > ALL (SELECT ...)
 * - expr = ANY (SELECT ...)
 * - expr >= SOME (SELECT ...) (SOME is synonym for ANY in MySQL)
 */
public class MySQLAnyAllSubquery implements MySQLExpression {

    private final MySQLExpression lhs;
    private final MySQLExpression subquery;
    private final ComparisonOperator comparisonOp;
    private final Quantifier quantifier;
    private final MySQLConstant expectedValue;

    public enum ComparisonOperator {
        EQUALS("="), NOT_EQUALS("<>"), LESS("<"), GREATER(">"), LESS_EQUALS("<="), GREATER_EQUALS(">=");

        private final String operator;

        ComparisonOperator(String operator) {
            this.operator = operator;
        }

        public String getOperator() {
            return operator;
        }
    }

    public enum Quantifier {
        ANY("ANY"), ALL("ALL"), SOME("SOME"); // SOME is synonym for ANY

        private final String quantifier;

        Quantifier(String quantifier) {
            this.quantifier = quantifier;
        }

        public String getQuantifier() {
            return quantifier;
        }
    }

    public MySQLAnyAllSubquery(MySQLExpression lhs, MySQLExpression subquery, ComparisonOperator comparisonOp,
            Quantifier quantifier, MySQLConstant expectedValue) {
        this.lhs = lhs;
        this.subquery = subquery;
        this.comparisonOp = comparisonOp;
        this.quantifier = quantifier;
        this.expectedValue = expectedValue;
    }

    public MySQLExpression getLhs() {
        return lhs;
    }

    public MySQLExpression getSubquery() {
        return subquery;
    }

    public ComparisonOperator getComparisonOp() {
        return comparisonOp;
    }

    public Quantifier getQuantifier() {
        return quantifier;
    }

    @Override
    public MySQLConstant getExpectedValue() {
        return expectedValue;
    }
}