package sqlancer.gaussdbm.oracle.eet;

import java.sql.SQLException;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.common.oracle.eet.EETQueryExecutor;
import sqlancer.common.oracle.eet.EETResultSetUtil;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.gaussdbm.GaussDBMGlobalState;

public class GaussDBMEETDefaultQueryExecutor implements EETQueryExecutor {

    private final GaussDBMGlobalState state;
    private static final EETFailureStats FAILURE_STATS = new EETFailureStats();

    public GaussDBMEETDefaultQueryExecutor(GaussDBMGlobalState state) {
        this.state = state;
    }

    @Override
    public List<List<String>> executeQuery(String sql) throws SQLException {
        SQLQueryAdapter adapter = new SQLQueryAdapter(sql);
        try (SQLancerResultSet rs = adapter.executeAndGet(state, false)) {
            if (rs == null) {
                FAILURE_STATS.recordSqlFailure(state, sql);
                throw new IgnoreMeException();
            }
            return EETResultSetUtil.readAllRows(rs);
        } catch (SQLException e) {
            FAILURE_STATS.recordException(state, e);
            throw e;
        }
    }
}