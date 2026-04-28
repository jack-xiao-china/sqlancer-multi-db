package sqlancer.gaussdbm.ast;

import sqlancer.Randomly;

/**
 * Represents binary arithmetic operations (+, -, *, /, %) in GaussDB-M (MySQL compatibility mode). Handles NULL
 * propagation, type conversion, and division by zero.
 */
public class GaussDBBinaryArithmeticOperation implements GaussDBExpression {

    public enum GaussDBArithmeticOperator {
        ADD("+") {
            @Override
            public GaussDBConstant apply(GaussDBConstant left, GaussDBConstant right) {
                if (left.isNull() || right.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                long leftVal = getNumericValue(left);
                long rightVal = getNumericValue(right);
                return GaussDBConstant.createIntConstant(leftVal + rightVal);
            }
        },
        SUB("-") {
            @Override
            public GaussDBConstant apply(GaussDBConstant left, GaussDBConstant right) {
                if (left.isNull() || right.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                long leftVal = getNumericValue(left);
                long rightVal = getNumericValue(right);
                return GaussDBConstant.createIntConstant(leftVal - rightVal);
            }
        },
        MUL("*") {
            @Override
            public GaussDBConstant apply(GaussDBConstant left, GaussDBConstant right) {
                if (left.isNull() || right.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                long leftVal = getNumericValue(left);
                long rightVal = getNumericValue(right);
                return GaussDBConstant.createIntConstant(leftVal * rightVal);
            }
        },
        DIV("/") {
            @Override
            public GaussDBConstant apply(GaussDBConstant left, GaussDBConstant right) {
                if (left.isNull() || right.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                long leftVal = getNumericValue(left);
                long rightVal = getNumericValue(right);
                // Division by zero returns NULL in MySQL/GaussDB-M
                if (rightVal == 0) {
                    return GaussDBConstant.createNullConstant();
                }
                return GaussDBConstant.createIntConstant(leftVal / rightVal);
            }
        },
        MOD("%") {
            @Override
            public GaussDBConstant apply(GaussDBConstant left, GaussDBConstant right) {
                if (left.isNull() || right.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                long leftVal = getNumericValue(left);
                long rightVal = getNumericValue(right);
                // Modulo by zero returns NULL in MySQL/GaussDB-M
                if (rightVal == 0) {
                    return GaussDBConstant.createNullConstant();
                }
                return GaussDBConstant.createIntConstant(leftVal % rightVal);
            }
        };

        private final String textRepresentation;

        GaussDBArithmeticOperator(String textRepresentation) {
            this.textRepresentation = textRepresentation;
        }

        public String getTextRepresentation() {
            return textRepresentation;
        }

        /**
         * Extracts numeric value from a constant. Handles INT, STRING (via lenient conversion), and BOOLEAN types.
         */
        private static long getNumericValue(GaussDBConstant constant) {
            if (constant.isInt()) {
                return constant.asIntNotNull();
            }
            if (constant.isString()) {
                return ((GaussDBConstant.GaussDBStringConstant) constant).asLongLenient();
            }
            if (constant.isBoolean()) {
                return constant.asBooleanNotNull() ? 1 : 0;
            }
            throw new UnsupportedOperationException("Unsupported constant type for arithmetic operation: " + constant);
        }

        public abstract GaussDBConstant apply(GaussDBConstant left, GaussDBConstant right);

        public static GaussDBArithmeticOperator getRandom() {
            return Randomly.fromOptions(values());
        }
    }

    private final GaussDBExpression left;
    private final GaussDBExpression right;
    private final GaussDBArithmeticOperator op;

    public GaussDBBinaryArithmeticOperation(GaussDBExpression left, GaussDBExpression right,
            GaussDBArithmeticOperator op) {
        this.left = left;
        this.right = right;
        this.op = op;
    }

    public GaussDBExpression getLeft() {
        return left;
    }

    public GaussDBExpression getRight() {
        return right;
    }

    public GaussDBArithmeticOperator getOp() {
        return op;
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        GaussDBConstant leftExpected = left.getExpectedValue();
        GaussDBConstant rightExpected = right.getExpectedValue();
        if (leftExpected == null || rightExpected == null) {
            return null;
        }
        return op.apply(leftExpected, rightExpected);
    }
}