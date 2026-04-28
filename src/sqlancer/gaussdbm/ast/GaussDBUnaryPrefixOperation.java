package sqlancer.gaussdbm.ast;

public class GaussDBUnaryPrefixOperation implements GaussDBExpression {

    public enum UnaryPrefixOperator {
        NOT("NOT") {
            @Override
            public GaussDBConstant applyNotNull(GaussDBConstant expr) {
                return GaussDBConstant.createBooleanConstant(!expr.asBooleanNotNull());
            }
        },
        PLUS("+") {
            @Override
            public GaussDBConstant applyNotNull(GaussDBConstant expr) {
                return expr;
            }
        },
        MINUS("-") {
            @Override
            public GaussDBConstant applyNotNull(GaussDBConstant expr) {
                if (expr.isInt()) {
                    return GaussDBConstant.createIntConstant(-expr.asIntNotNull());
                }
                if (expr.isString()) {
                    long val = ((GaussDBConstant.GaussDBStringConstant) expr).asLongLenient();
                    return GaussDBConstant.createIntConstant(-val);
                }
                throw new AssertionError(expr);
            }
        };

        private final String text;

        UnaryPrefixOperator(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public abstract GaussDBConstant applyNotNull(GaussDBConstant expr);
    }

    private final GaussDBExpression expr;
    private final UnaryPrefixOperator op;

    public GaussDBUnaryPrefixOperation(GaussDBExpression expr, UnaryPrefixOperator op) {
        this.expr = expr;
        this.op = op;
    }

    public GaussDBExpression getExpr() {
        return expr;
    }

    public UnaryPrefixOperator getOp() {
        return op;
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        GaussDBConstant v = expr.getExpectedValue();
        if (v.isNull()) {
            return GaussDBConstant.createNullConstant();
        }
        return op.applyNotNull(v);
    }
}
