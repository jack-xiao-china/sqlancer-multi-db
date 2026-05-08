package sqlancer.cockroachdb.gen.transaction;

import sqlancer.cockroachdb.CockroachDBProvider;
import sqlancer.common.query.SQLQueryAdapter;

import java.sql.SQLException;

public class CockroachDBCommitGenerator {

    public static SQLQueryAdapter getQuery(CockroachDBProvider.CockroachDBGlobalState globalState) throws SQLException {
        return new SQLQueryAdapter("COMMIT");
    }

}