package sqlancer.gaussdbm.ast;

import sqlancer.Randomly;

public class GaussDBUnaryPostfixOperation implements GaussDBExpression {

    public enum UnaryPostfixOperator {
        IS_NULL("IS NULL") {
            @Override
            public GaussDBConstant applyNotNull(GaussDBConstant expr) {
                return GaussDBConstant.createBooleanConstant(false);
            }
        },
        IS_NOT_NULL("IS NOT NULL") {
            @Override
            public GaussDBConstant applyNotNull(GaussDBConstant expr) {
                return GaussDBConstant.createBooleanConstant(true);
            }
        },
        IS_TRUE("IS TRUE") {
            @Override
            public GaussDBConstant applyNotNull(GaussDBConstant expr) {
                return GaussDBConstant.createBooleanConstant(expr.asBooleanNotNull());
            }
        },
        IS_FALSE("IS FALSE") {
            @Override
            public GaussDBConstant applyNotNull(GaussDBConstant expr) {
                return GaussDBConstant.createBooleanConstant(!expr.asBooleanNotNull());
            }
        },
        IS_NOT_TRUE("IS NOT TRUE") {
            @Override
            public GaussDBConstant applyNotNull(GaussDBConstant expr) {
                return GaussDBConstant.createBooleanConstant(!expr.asBooleanNotNull());
            }
        },
        IS_NOT_FALSE("IS NOT FALSE") {
            @Override
            public GaussDBConstant applyNotNull(GaussDBConstant expr) {
                return GaussDBConstant.createBooleanConstant(expr.asBooleanNotNull());
            }
        };

        private final String text;

        UnaryPostfixOperator(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        /**
         * Returns a randomly selected unary postfix operator.
         *
         * @return a random UnaryPostfixOperator
         */
        public static UnaryPostfixOperator getRandom() {
            return Randomly.fromOptions(values());
        }

        /**
         * Apply the operator to a non-null value. For IS NULL/IS NOT NULL, the result is constant (false/true). For IS
         * TRUE/IS FALSE/IS NOT TRUE/IS NOT FALSE, the result depends on the boolean value.
         */
        public abstract GaussDBConstant applyNotNull(GaussDBConstant expr);
    }

    private final GaussDBExpression expr;
    private final UnaryPostfixOperator op;

    public GaussDBUnaryPostfixOperation(GaussDBExpression expr, UnaryPostfixOperator op) {
        this.expr = expr;
        this.op = op;
    }

    public GaussDBExpression getExpr() {
        return expr;
    }

    public UnaryPostfixOperator getOp() {
        return op;
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        GaussDBConstant v = expr.getExpectedValue();
        if (v.isNull()) {
            // IS TRUE and IS FALSE return FALSE for NULL (SQL standard three-valued logic)
            // IS NOT TRUE returns TRUE for NULL
            // IS NOT FALSE returns TRUE for NULL
            // IS NULL returns TRUE for NULL
            // IS NOT NULL returns FALSE for NULL
            switch (op) {
            case IS_NULL:
                return GaussDBConstant.createBooleanConstant(true);
            case IS_NOT_NULL:
                return GaussDBConstant.createBooleanConstant(false);
            case IS_TRUE:
            case IS_FALSE:
                return GaussDBConstant.createBooleanConstant(false);
            case IS_NOT_TRUE:
            case IS_NOT_FALSE:
                return GaussDBConstant.createBooleanConstant(true);
            default:
                throw new AssertionError(op);
            }
        }
        return op.applyNotNull(v);
    }
}
