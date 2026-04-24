package sqlancer.postgres.gen;

import java.util.List;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema.PostgresIndex;
import sqlancer.postgres.PostgresSchema.PostgresTable;

public final class PostgresAlterIndexGenerator {

    private PostgresAlterIndexGenerator() {
    }

    public static SQLQueryAdapter create(PostgresGlobalState globalState) {
        PostgresTable table = globalState.getSchema().getRandomTableOrBailout(t -> !t.getIndexes().isEmpty());
        List<PostgresIndex> indexes = table.getIndexes();
        String indexName = Randomly.fromList(indexes).getIndexName();
        StringBuilder sb = new StringBuilder("ALTER INDEX ");
        if (Randomly.getBoolean()) {
            sb.append("IF EXISTS ");
        }
        sb.append(indexName);
        switch (Randomly.fromOptions("RENAME", "SET", "RESET", "ALTER_COLUMN")) {
        case "RENAME":
            sb.append(" RENAME TO ");
            sb.append(indexName);
            sb.append("_r");
            break;
        case "SET":
            sb.append(" SET (fillfactor = ");
            sb.append(globalState.getRandomly().getInteger(10, 100));
            sb.append(")");
            break;
        case "RESET":
            sb.append(" RESET (fillfactor)");
            break;
        case "ALTER_COLUMN":
            sb.append(" ALTER COLUMN ");
            sb.append(globalState.getRandomly().getInteger(1, Math.max(2, table.getColumns().size() + 1)));
            sb.append(" SET STATISTICS ");
            sb.append(globalState.getRandomly().getInteger(0, 10000));
            break;
        default:
            throw new AssertionError();
        }
        ExpectedErrors errors = ExpectedErrors.from("does not exist", "already exists", "is not an index",
                "unrecognized parameter", "cannot alter index", "cannot alter system column");
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }
}
