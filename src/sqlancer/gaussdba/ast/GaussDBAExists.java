package sqlancer.gaussdba.ast;

/**
 * EXISTS subquery expression for GaussDB-A (Oracle compatible).
 * Created for future EET semantic rule support (ExistsToIn transform).
 */
public class GaussDBAExists implements GaussDBAExpression {

    private final GaussDBAExpression subquery;
    private final GaussDBAConstant expectedValue;

    public GaussDBAExists(GaussDBAExpression subquery) {
        this.subquery = subquery;
        GaussDBAConstant ev = null;
        try {
            ev = subquery.getExpectedValue();
        } catch (Exception e) {
            // ignore
        }
        this.expectedValue = ev;
    }

    public GaussDBAExists(GaussDBAExpression subquery, GaussDBAConstant expectedValue) {
        this.subquery = subquery;
        this.expectedValue = expectedValue;
    }

    public GaussDBAExpression getSubquery() {
        return subquery;
    }

    public GaussDBAConstant getExpectedValue() {
        return expectedValue;
    }

    @Override
    public GaussDBADataType getExpressionType() {
        return GaussDBADataType.NUMBER; // Oracle-style: boolean via NUMBER(1)
    }
}