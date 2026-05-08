package sqlancer.postgres.gen.transaction;

import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresGlobalState;

public class PostgresBeginGenerator {

    public static SQLQueryAdapter getQuery(PostgresGlobalState state) {
        return new SQLQueryAdapter("BEGIN");
    }
}