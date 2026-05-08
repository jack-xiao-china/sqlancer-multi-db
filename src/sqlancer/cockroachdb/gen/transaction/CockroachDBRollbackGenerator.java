package sqlancer.cockroachdb.gen.transaction;

import sqlancer.cockroachdb.CockroachDBProvider;
import sqlancer.common.query.SQLQueryAdapter;

import java.sql.SQLException;

public class CockroachDBRollbackGenerator {

    public static SQLQueryAdapter getQuery(CockroachDBProvider.CockroachDBGlobalState globalState) throws SQLException {
        return new SQLQueryAdapter("ROLLBACK");
    }

}