package sqlancer.postgres.gen.transaction;

import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresGlobalState;

public class PostgresRollbackGenerator {

    public static SQLQueryAdapter getQuery(PostgresGlobalState state) {
        return new SQLQueryAdapter("ROLLBACK");
    }
}