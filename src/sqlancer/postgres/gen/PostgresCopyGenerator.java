package sqlancer.postgres.gen;

import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema.PostgresTable;

public final class PostgresCopyGenerator {

    private PostgresCopyGenerator() {
    }

    public static SQLQueryAdapter create(PostgresGlobalState globalState) {
        PostgresTable table = globalState.getSchema()
                .getRandomTableOrBailout(t -> !t.isView() && !t.getColumns().isEmpty());
        StringBuilder sb = new StringBuilder("COPY ");
        sb.append(table.getName());
        if (Randomly.getBoolean()) {
            sb.append("(");
            sb.append(table.getRandomNonEmptyColumnSubset().stream().map(c -> c.getName())
                    .collect(Collectors.joining(", ")));
            sb.append(")");
        }
        sb.append(" TO STDOUT");
        if (Randomly.getBoolean()) {
            String format = Randomly.fromOptions("TEXT", "CSV", "BINARY");
            sb.append(" WITH (FORMAT ");
            sb.append(format);
            if ("CSV".equals(format) && Randomly.getBoolean()) {
                sb.append(", HEADER ");
                sb.append(Randomly.fromOptions("TRUE", "FALSE"));
            }
            sb.append(")");
        }
        ExpectedErrors errors = ExpectedErrors.from("COPY commands are only supported using the CopyManager API",
                "must be superuser", "permission denied", "cannot copy from view",
                "cannot copy from materialized view");
        return new SQLQueryAdapter(sb.toString(), errors);
    }
}
