package sqlancer.gaussdba.ast;

import sqlancer.Randomly;

public class GaussDBABinaryArithmeticOperation implements GaussDBAExpression {

    public enum GaussDBAArithmeticOperator {
        ADDITION("+"), SUBTRACTION("-"), MULTIPLICATION("*"), DIVISION("/");

        private final String text;

        GaussDBAArithmeticOperator(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }

        public static GaussDBAArithmeticOperator getRandom() {
            return Randomly.fromOptions(values());
        }
    }

    private final GaussDBAExpression left;
    private final GaussDBAExpression right;
    private final GaussDBAArithmeticOperator op;

    public GaussDBABinaryArithmeticOperation(GaussDBAExpression left, GaussDBAExpression right,
            GaussDBAArithmeticOperator op) {
        this.left = left;
        this.right = right;
        this.op = op;
    }

    public GaussDBAExpression getLeft() {
        return left;
    }

    public GaussDBAExpression getRight() {
        return right;
    }

    public GaussDBAArithmeticOperator getOp() {
        return op;
    }

    @Override
    public GaussDBAConstant getExpectedValue() {
        GaussDBAConstant leftVal = left.getExpectedValue();
        GaussDBAConstant rightVal = right.getExpectedValue();
        if (leftVal == null || rightVal == null) {
            return null;
        }
        if (leftVal.isNull() || rightVal.isNull()) {
            return GaussDBAConstant.createNullConstant();
        }
        // Use exact BigDecimal arithmetic — NOT asNumber()/long, which truncates (0.5 -> 0, 7/2 -> 3)
        // and diverges from the DB's high-precision NUMBER semantics. A truncated prediction makes
        // callers (e.g. PQS rectification) build predicates that the DB evaluates differently.
        if (!leftVal.isNumber() || !rightVal.isNumber()) {
            return null; // non-numeric operand: cannot reliably predict the arithmetic result
        }
        java.math.BigDecimal l = leftVal.asBigDecimal();
        java.math.BigDecimal r = rightVal.asBigDecimal();
        switch (op) {
        case ADDITION:
            return GaussDBAConstant.createNumberConstant(l.add(r));
        case SUBTRACTION:
            return GaussDBAConstant.createNumberConstant(l.subtract(r));
        case MULTIPLICATION:
            return GaussDBAConstant.createNumberConstant(l.multiply(r));
        case DIVISION:
            if (r.signum() == 0) {
                return GaussDBAConstant.createNullConstant();
            }
            // Division may yield a non-terminating decimal whose scale/rounding need not match the
            // DB exactly; predicting the precise value is unsafe, so decline to predict (return null).
            return null;
        default:
            throw new AssertionError(op);
        }
    }

    @Override
    public GaussDBADataType getExpressionType() {
        return GaussDBADataType.NUMBER;
    }

    public static GaussDBABinaryArithmeticOperation create(GaussDBAExpression left, GaussDBAExpression right,
            GaussDBAArithmeticOperator op) {
        return new GaussDBABinaryArithmeticOperation(left, right, op);
    }
}