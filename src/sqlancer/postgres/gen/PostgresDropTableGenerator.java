package sqlancer.postgres.gen;

import sqlancer.Randomly;
import sqlancer.common.DBMSCommon;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema.PostgresTable;

public final class PostgresDropTableGenerator {

    private PostgresDropTableGenerator() {
    }

    public static SQLQueryAdapter create(PostgresGlobalState globalState) {
        StringBuilder sb = new StringBuilder("DROP TABLE ");
        if (Randomly.getBoolean()) {
            sb.append("IF EXISTS ");
        }
        if (globalState.getSchema().getDatabaseTables().stream().filter(t -> !t.isView()).count() > 1
                && Randomly.getBoolean()) {
            PostgresTable table = globalState.getSchema().getRandomTable(t -> !t.isView());
            sb.append(table.getName());
        } else {
            sb.append(DBMSCommon.createTableName(Randomly.smallNumber()));
        }
        if (Randomly.getBoolean()) {
            sb.append(" ");
            sb.append(Randomly.fromOptions("CASCADE", "RESTRICT"));
        }
        ExpectedErrors errors = ExpectedErrors.from("does not exist", "cannot drop table",
                "cannot drop desired object(s) because other objects depend on them", "is not a table",
                "is not a table, composite type, or foreign table", "because other objects depend on it");
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }
}
