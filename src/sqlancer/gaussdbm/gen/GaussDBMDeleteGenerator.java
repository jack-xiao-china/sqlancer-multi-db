package sqlancer.gaussdbm.gen;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBExpression;

public final class GaussDBMDeleteGenerator {

    private GaussDBMDeleteGenerator() {
    }

    public static SQLQueryAdapter create(GaussDBMGlobalState globalState) {
        GaussDBTable table = globalState.getSchema().getRandomTable();
        ExpectedErrors errors = new ExpectedErrors();
        errors.add("violates foreign key constraint");
        errors.add("delete");
        StringBuilder sb = new StringBuilder("DELETE FROM ");
        sb.append(table.getName());
        if (Randomly.getBoolean()) {
            sb.append(" WHERE ");
            GaussDBMExpressionGenerator gen = new GaussDBMExpressionGenerator(globalState)
                    .setColumns(table.getColumns());
            GaussDBExpression where = gen.generateExpression();
            sb.append(GaussDBToStringVisitor.asString(where));
        }
        return new SQLQueryAdapter(sb.toString(), errors);
    }
}