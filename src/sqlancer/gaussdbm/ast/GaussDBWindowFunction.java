package sqlancer.gaussdbm.ast;

import java.util.ArrayList;
import java.util.List;

import sqlancer.Randomly;

/**
 * Window function AST node for GaussDB-M. Represents window functions like ROW_NUMBER(), SUM() OVER (PARTITION BY ...),
 * etc.
 */
public class GaussDBWindowFunction implements GaussDBExpression {

    private final GaussDBWindowFunction.GaussDBFunction func;
    private final GaussDBExpression expr;
    private final List<GaussDBExpression> partitionBy;
    private final List<GaussDBExpression> orderBy;

    public GaussDBWindowFunction(GaussDBWindowFunction.GaussDBFunction func, GaussDBExpression expr,
            List<GaussDBExpression> partitionBy, List<GaussDBExpression> orderBy) {
        this.func = func;
        this.expr = expr;
        this.partitionBy = partitionBy != null ? partitionBy : new ArrayList<>();
        this.orderBy = orderBy != null ? orderBy : new ArrayList<>();
    }

    public GaussDBWindowFunction.GaussDBFunction getFunction() {
        return func;
    }

    public GaussDBExpression getExpr() {
        return expr;
    }

    public List<GaussDBExpression> getPartitionBy() {
        return partitionBy;
    }

    public List<GaussDBExpression> getOrderBy() {
        return orderBy;
    }

    public enum GaussDBFunction {
        // Ranking functions (0 parameters)
        ROW_NUMBER("ROW_NUMBER", 0), RANK("RANK", 0), DENSE_RANK("DENSE_RANK", 0),

        // Aggregate window functions (1 parameter)
        SUM("SUM", 1), AVG("AVG", 1), COUNT("COUNT", 1), MAX("MAX", 1), MIN("MIN", 1);

        private final String functionName;
        private final int args;

        GaussDBFunction(String functionName, int args) {
            this.functionName = functionName;
            this.args = args;
        }

        public static GaussDBWindowFunction.GaussDBFunction getRandomFunction() {
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

        /**
         * Check if this is a ranking function (no arguments required).
         */
        public boolean isRankingFunction() {
            return this == ROW_NUMBER || this == RANK || this == DENSE_RANK;
        }

        /**
         * Check if this is an aggregate window function (requires one argument).
         */
        public boolean isAggregateWindowFunction() {
            return !isRankingFunction();
        }
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        // Window functions cannot be statically computed - they require actual data
        // and window context to calculate. Return null to indicate this.
        return null;
    }
}