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
        long leftNum = leftVal.asNumber();
        long rightNum = rightVal.asNumber();
        switch (op) {
        case ADDITION:
            return GaussDBAConstant.createNumberConstant(leftNum + rightNum);
        case SUBTRACTION:
            return GaussDBAConstant.createNumberConstant(leftNum - rightNum);
        case MULTIPLICATION:
            return GaussDBAConstant.createNumberConstant(leftNum * rightNum);
        case DIVISION:
            if (rightNum == 0) {
                return GaussDBAConstant.createNullConstant();
            }
            return GaussDBAConstant.createNumberConstant(leftNum / rightNum);
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