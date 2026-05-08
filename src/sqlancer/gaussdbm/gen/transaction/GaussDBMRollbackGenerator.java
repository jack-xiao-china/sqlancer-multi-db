package sqlancer.gaussdbm.gen.transaction;

import java.sql.SQLException;

import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdbm.GaussDBMGlobalState;

public class GaussDBMRollbackGenerator {

    public static SQLQueryAdapter getQuery(GaussDBMGlobalState globalState) throws SQLException {
        return new SQLQueryAdapter("ROLLBACK");
    }
}