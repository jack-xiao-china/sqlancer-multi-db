package sqlancer.postgres.gen;

import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema.PostgresTable;

public final class PostgresDropViewGenerator {

    private PostgresDropViewGenerator() {
    }

    public static SQLQueryAdapter create(PostgresGlobalState globalState) {
        List<PostgresTable> views = globalState.getSchema().getDatabaseTables().stream().filter(PostgresTable::isView)
                .collect(Collectors.toList());
        StringBuilder sb = new StringBuilder("DROP ");
        boolean materialized = Randomly.getBooleanWithRatherLowProbability();
        if (materialized) {
            sb.append("MATERIALIZED ");
        }
        sb.append("VIEW ");
        if (Randomly.getBoolean() || views.isEmpty()) {
            sb.append("IF EXISTS ");
        }
        if (!views.isEmpty() && Randomly.getBoolean()) {
            sb.append(Randomly.fromList(views).getName());
        } else {
            sb.append("v");
            sb.append(Randomly.smallNumber());
        }
        if (Randomly.getBoolean()) {
            sb.append(" ");
            sb.append(Randomly.fromOptions("CASCADE", "RESTRICT"));
        }
        ExpectedErrors errors = ExpectedErrors.from("does not exist", "is not a view", "is not a materialized view",
                "cannot drop desired object(s) because other objects depend on them",
                "because other objects depend on it");
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }
}
