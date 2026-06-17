package sqlancer.gaussdba.gen;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdba.GaussDBAErrors;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;
import sqlancer.gaussdba.GaussDBAToStringVisitor;
import sqlancer.gaussdba.ast.GaussDBAExpression;

public final class GaussDBADeleteGenerator {

    private GaussDBADeleteGenerator() {
    }

    public static SQLQueryAdapter create(GaussDBAGlobalState globalState) {
        GaussDBATable table = globalState.getSchema().getRandomTableOrBailout(t -> !t.isView());

        ExpectedErrors errors = new ExpectedErrors();
        errors.add("violates foreign key constraint");
        errors.add("violates not-null constraint");
        errors.add("violates unique constraint");
        errors.add("division by zero");
        errors.add("invalid input syntax");
        errors.add("cannot cast");
        errors.add("out of range");
        GaussDBAErrors.addExpressionErrors(errors);

        StringBuilder sb = new StringBuilder("DELETE FROM ");
        sb.append(table.getName());

        if (!Randomly.getBooleanWithSmallProbability()) {
            sb.append(" WHERE ");
            GaussDBAExpressionGenerator gen = new GaussDBAExpressionGenerator(globalState)
                    .setColumns(table.getColumns());
            GaussDBAExpression where = gen.generateBooleanExpression();
            sb.append(GaussDBAToStringVisitor.asString(where));
        }

        return new SQLQueryAdapter(sb.toString(), errors, true);
    }
}