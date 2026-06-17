package sqlancer.gaussdba.gen;

import java.util.Arrays;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.common.gen.AbstractUpdateGenerator;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdba.GaussDBAErrors;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;
import sqlancer.gaussdba.GaussDBAToStringVisitor;
import sqlancer.gaussdba.ast.GaussDBAExpression;

public final class GaussDBAUpdateGenerator extends AbstractUpdateGenerator<GaussDBAColumn> {

    private final GaussDBAGlobalState globalState;
    private GaussDBATable randomTable;

    private GaussDBAUpdateGenerator(GaussDBAGlobalState globalState) {
        this.globalState = globalState;
        errors.addAll(Arrays.asList(
                "conflicting key value violates exclusion constraint",
                "violates foreign key constraint",
                "violates not-null constraint",
                "violates unique constraint",
                "out of range",
                "cannot cast",
                "division by zero",
                "invalid input syntax",
                "value too large",
                "check constraint violated",
                "unique constraint violated"));
        GaussDBAErrors.addInsertUpdateErrors(errors);
        GaussDBAErrors.addExpressionErrors(errors);
    }

    public static SQLQueryAdapter create(GaussDBAGlobalState globalState) {
        return new GaussDBAUpdateGenerator(globalState).generate();
    }

    private SQLQueryAdapter generate() {
        randomTable = globalState.getSchema().getRandomTableOrBailout(t -> !t.isView());
        List<GaussDBAColumn> columns = randomTable.getRandomNonEmptyColumnSubset();
        sb.append("UPDATE ");
        sb.append(randomTable.getName());
        sb.append(" SET ");
        updateColumns(columns);

        if (!Randomly.getBooleanWithSmallProbability()) {
            sb.append(" WHERE ");
            GaussDBAExpressionGenerator gen = new GaussDBAExpressionGenerator(globalState)
                    .setColumns(randomTable.getColumns());
            GaussDBAExpression where = gen.generateBooleanExpression();
            sb.append(GaussDBAToStringVisitor.asString(where));
        }

        return new SQLQueryAdapter(sb.toString(), errors, true);
    }

    @Override
    protected void updateValue(GaussDBAColumn column) {
        if (Randomly.getBoolean()) {
            // Use a constant value
            GaussDBAExpressionGenerator gen = new GaussDBAExpressionGenerator(globalState);
            GaussDBAExpression constant = gen.generateConstant(column.getType());
            sb.append(GaussDBAToStringVisitor.asString(constant));
        } else if (Randomly.getBooleanWithSmallProbability()) {
            // Use DEFAULT (Oracle A-compatible supports DEFAULT)
            sb.append("DEFAULT");
        } else {
            // Use an expression
            sb.append("(");
            GaussDBAExpressionGenerator gen = new GaussDBAExpressionGenerator(globalState)
                    .setColumns(randomTable.getColumns());
            GaussDBAExpression expr = gen.generateExpression(0);
            sb.append(GaussDBAToStringVisitor.asString(expr));
            sb.append(")");
        }
    }
}