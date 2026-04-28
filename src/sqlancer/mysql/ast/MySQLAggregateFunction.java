package sqlancer.mysql.ast;

import sqlancer.Randomly;

/**
 * Aggregate function AST node for MySQL. Note: This is distinct from SQLancer's existing MySQLAggregate class. This
 * class provides a simpler interface with MySQLFunction enum for aggregate functions.
 */
public class MySQLAggregateFunction implements MySQLExpression {

    private final MySQLFunction func;
    private final MySQLExpression expr;

    public MySQLAggregateFunction(MySQLFunction func, MySQLExpression expr) {
        this.expr = expr;
        this.func = func;
    }

    public MySQLFunction getFunction() {
        return func;
    }

    public MySQLExpression getExpr() {
        return expr;
    }

    public enum MySQLFunction {

        AVG("AVG"), BIT_AND("BIT_AND"), BIT_OR("BIT_OR"), BIT_XOR("BIT_XOR"), COUNT("COUNT"), MAX("MAX"), MIN("MIN"),
        STD("STD"), STDDEV_POP("STDDEV_POP"), STDDEV_SAMP("STDDEV_SAMP"), STDDEV("STD"), VAR_POP("VAR_POP"),
        VAR_SAMP("VAR_SAMP"), VARIATION("VARIANCE"), SUM("SUM");

        private final String functionName;

        MySQLFunction(String functionName) {
            this.functionName = functionName;
        }

        public static MySQLFunction getRandomFunction() {
            return Randomly.fromOptions(values());
        }

        @Override
        public String toString() {
            return functionName;
        }

        public String getName() {
            return functionName;
        }
    }

    @Override
    public MySQLConstant getExpectedValue() {
        return null;
    }
}