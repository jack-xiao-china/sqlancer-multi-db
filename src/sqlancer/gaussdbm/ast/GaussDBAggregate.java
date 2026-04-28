package sqlancer.gaussdbm.ast;

import java.util.List;

public class GaussDBAggregate implements GaussDBExpression {

    public enum GaussDBAggregateFunction {
        COUNT("COUNT", null, false), COUNT_DISTINCT("COUNT", "DISTINCT", true), SUM("SUM", null, false),
        SUM_DISTINCT("SUM", "DISTINCT", false), MIN("MIN", null, false), MIN_DISTINCT("MIN", "DISTINCT", false),
        MAX("MAX", null, false), MAX_DISTINCT("MAX", "DISTINCT", false), AVG("AVG", null, false),
        AVG_DISTINCT("AVG", "DISTINCT", false),
        // Bitwise aggregate functions
        BIT_AND("BIT_AND", null, false), BIT_AND_DISTINCT("BIT_AND", "DISTINCT", false), BIT_OR("BIT_OR", null, false),
        BIT_OR_DISTINCT("BIT_OR", "DISTINCT", false), BIT_XOR("BIT_XOR", null, false),
        BIT_XOR_DISTINCT("BIT_XOR", "DISTINCT", false),
        // Statistical functions (STDDEV/VARIANCE series)
        // Note: These functions require actual data to compute, so getExpectedValue() returns null
        STDDEV("STDDEV", null, false), STDDEV_POP("STDDEV_POP", null, false), STDDEV_SAMP("STDDEV_SAMP", null, false),
        STD("STD", null, false), // MySQL alias for STDDEV
        VARIANCE("VARIANCE", null, false), VAR_POP("VAR_POP", null, false), VAR_SAMP("VAR_SAMP", null, false);

        private final String name;
        private final String option;
        private final boolean variadic;

        GaussDBAggregateFunction(String name, String option, boolean variadic) {
            this.name = name;
            this.option = option;
            this.variadic = variadic;
        }

        public String getName() {
            return name;
        }

        public String getOption() {
            return option;
        }

        public boolean isVariadic() {
            return variadic;
        }
    }

    private final List<GaussDBExpression> exprs;
    private final GaussDBAggregateFunction func;

    public GaussDBAggregate(List<GaussDBExpression> exprs, GaussDBAggregateFunction func) {
        this.exprs = exprs;
        this.func = func;
    }

    public List<GaussDBExpression> getExprs() {
        return exprs;
    }

    public GaussDBAggregateFunction getFunc() {
        return func;
    }

    /**
     * Returns null as aggregate functions require actual data to compute expected values. This is consistent with the
     * behavior of all aggregate functions (including statistics functions like STDDEV, VARIANCE) which cannot be
     * evaluated without actual row data.
     *
     * @return null (expected value cannot be computed without actual data)
     */
    @Override
    public GaussDBConstant getExpectedValue() {
        return null;
    }
}
