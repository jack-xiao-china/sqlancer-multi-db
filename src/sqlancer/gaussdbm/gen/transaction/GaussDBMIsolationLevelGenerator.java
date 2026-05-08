package sqlancer.gaussdbm.gen.transaction;

import java.sql.SQLException;

import sqlancer.common.transaction.IsolationLevel;
import sqlancer.common.query.SQLQueryAdapter;

public class GaussDBMIsolationLevelGenerator {

    private final IsolationLevel isolationLevel;

    public GaussDBMIsolationLevelGenerator(IsolationLevel isolationLevel) {
        this.isolationLevel = isolationLevel;
    }

    public SQLQueryAdapter getQuery() throws SQLException {
        String sql = "SET SESSION TRANSACTION ISOLATION LEVEL " + isolationLevel.getName();
        return new SQLQueryAdapter(sql);
    }
}