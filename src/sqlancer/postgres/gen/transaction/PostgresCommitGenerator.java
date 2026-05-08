package sqlancer.postgres.gen.transaction;

import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresGlobalState;

public class PostgresCommitGenerator {

    public static SQLQueryAdapter getQuery(PostgresGlobalState state) {
        return new SQLQueryAdapter("COMMIT");
    }
}