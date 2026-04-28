package sqlancer.mysql.ast;

import sqlancer.Randomly;

/**
 * Window function AST node for MySQL. Represents window functions like ROW_NUMBER(), SUM() OVER (PARTITION BY ...),
 * etc.
 */
public class MySQLWindowFunction implements MySQLExpression {
    private final MySQLWindowFunction.MySQLFunction func;
    private final MySQLExpression expr;

    private final MySQLExpression partitionBy;

    public MySQLWindowFunction(MySQLWindowFunction.MySQLFunction func, MySQLExpression expr, MySQLExpression column) {
        this.func = func;
        this.expr = expr;
        this.partitionBy = column;
    }

    public MySQLWindowFunction.MySQLFunction getFunction() {
        return func;
    }

    public MySQLExpression getExpr() {
        return expr;
    }

    public MySQLExpression getPartitionBy() {
        return partitionBy;
    }

    public enum MySQLFunction {

        ROW_NUMBER("ROW_NUMBER", 0), RANK("RANK", 0), DENSE_RANK("DENSE_RANK", 0), CUME_DIST("CUME_DIST", 0),
        PERCENT_RANK("PERCENT_RANK", 0), LAG("LAG"), LEAD("LEAD"), AVG("AVG"), BIT_AND("BIT_AND"), BIT_OR("BIT_OR"),
        BIT_XOR("BIT_XOR"), COUNT("COUNT"), MAX("MAX"), MIN("MIN"), STD("STD"), STDDEV_POP("STDDEV_POP"),
        STDDEV_SAMP("STDDEV_SAMP"), STDDEV("STD"), VAR_POP("VAR_POP"), VAR_SAMP("VAR_SAMP"), VARIATION("VARIANCE"),
        SUM("SUM");

        private final String functionName;
        private final int args;

        MySQLFunction(String functionName, int args) {
            this.functionName = functionName;
            this.args = args;
        }

        MySQLFunction(String functionName) {
            this.functionName = functionName;
            this.args = 1;
        }

        public static MySQLWindowFunction.MySQLFunction getRandomFunction() {
            return Randomly.fromOptions(values());
        }

        @Override
        public String toString() {
            return functionName;
        }

        public String getName() {
            return functionName;
        }

        public int getArgs() {
            return args;
        }
    }

    @Override
    public MySQLConstant getExpectedValue() {
        return null;
    }
}