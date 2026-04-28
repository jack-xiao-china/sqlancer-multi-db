package sqlancer.gaussdbm.ast;

import java.util.List;

/**
 * Represents an IN or NOT IN operation in GaussDB-M.
 *
 * @see <a href="https://dev.mysql.com/doc/refman/8.0/en/comparison-operators.html#operator_in">MySQL Comparison
 *      Operators</a>
 */
public class GaussDBInOperation implements GaussDBExpression {

    private final GaussDBExpression expr;
    private final List<GaussDBExpression> listElements;
    private final boolean isTrue; // true for IN, false for NOT IN

    public GaussDBInOperation(GaussDBExpression expr, List<GaussDBExpression> listElements, boolean isTrue) {
        this.expr = expr;
        this.listElements = listElements;
        this.isTrue = isTrue;
    }

    public GaussDBExpression getExpr() {
        return expr;
    }

    public List<GaussDBExpression> getListElements() {
        return listElements;
    }

    public boolean isTrue() {
        return isTrue;
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        GaussDBConstant leftVal = expr.getExpectedValue();
        if (leftVal == null || leftVal.isNull()) {
            return GaussDBConstant.createNullConstant();
        }

        boolean isNull = false;
        for (GaussDBExpression rightExpr : listElements) {
            GaussDBConstant rightVal = rightExpr.getExpectedValue();

            if (rightVal == null || rightVal.isNull()) {
                isNull = true;
                continue;
            }

            GaussDBConstant isEquals = leftVal.sqlEquals(rightVal);
            if (isEquals == null || isEquals.isNull()) {
                isNull = true;
            } else if (isEquals.asBooleanNotNull()) {
                return GaussDBConstant.createBooleanConstant(isTrue);
            }
        }

        if (isNull) {
            return GaussDBConstant.createNullConstant();
        }
        return GaussDBConstant.createBooleanConstant(!isTrue);
    }
}