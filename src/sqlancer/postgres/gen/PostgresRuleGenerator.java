package sqlancer.postgres.gen;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema.PostgresTable;

public final class PostgresRuleGenerator {

    private PostgresRuleGenerator() {
    }

    public static SQLQueryAdapter create(PostgresGlobalState globalState) {
        PostgresTable table = globalState.getSchema().getRandomTableOrBailout(t -> !t.isView());
        StringBuilder sb = new StringBuilder("CREATE OR REPLACE RULE ");
        sb.append("prule");
        sb.append(Randomly.smallNumber());
        sb.append(" AS ON ");
        sb.append(Randomly.fromOptions("INSERT", "UPDATE", "DELETE"));
        sb.append(" TO ");
        sb.append(table.getName());
        sb.append(" DO ALSO NOTHING");
        ExpectedErrors errors = ExpectedErrors.from("already exists",
                "rules on INSERT, UPDATE, DELETE are not supported", "cannot have multiple RETURNING lists in a rule",
                "permission denied", "does not exist");
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }
}
