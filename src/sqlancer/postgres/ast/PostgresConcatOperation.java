package sqlancer.postgres.ast;

import sqlancer.common.ast.BinaryNode;
import sqlancer.postgres.PostgresSchema.PostgresDataType;

public class PostgresConcatOperation extends BinaryNode<PostgresExpression> implements PostgresExpression {

    public PostgresConcatOperation(PostgresExpression left, PostgresExpression right) {
        super(left, right);
    }

    @Override
    public PostgresDataType getExpressionType() {
        return PostgresDataType.TEXT;
    }

    @Override
    public PostgresConstant getExpectedValue() {
        PostgresConstant leftExpectedValue = getLeft().getExpectedValue();
        PostgresConstant rightExpectedValue = getRight().getExpectedValue();
        if (leftExpectedValue == null || rightExpectedValue == null) {
            return null;
        }
        if (leftExpectedValue.isNull() || rightExpectedValue.isNull()) {
            return PostgresConstant.createNullConstant();
        }
        PostgresConstant leftText = leftExpectedValue.cast(PostgresDataType.TEXT);
        PostgresConstant rightText = rightExpectedValue.cast(PostgresDataType.TEXT);
        if (leftText == null || rightText == null) {
            return null;
        }
        String leftStr = leftText.getUnquotedTextRepresentation();
        String rightStr = rightText.getUnquotedTextRepresentation();
        return PostgresConstant.createTextConstant(leftStr + rightStr);
    }

    @Override
    public String getOperatorRepresentation() {
        return "||";
    }

}
