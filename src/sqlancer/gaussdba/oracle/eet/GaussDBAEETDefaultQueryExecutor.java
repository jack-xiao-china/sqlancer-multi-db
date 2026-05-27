package sqlancer.gaussdba.oracle.eet;

import java.sql.SQLException;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.common.oracle.eet.EETQueryExecutor;
import sqlancer.common.oracle.eet.EETResultSetUtil;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.gaussdba.GaussDBAGlobalState;

public class GaussDBAEETDefaultQueryExecutor implements EETQueryExecutor {

    private final GaussDBAGlobalState state;

    public GaussDBAEETDefaultQueryExecutor(GaussDBAGlobalState state) {
        this.state = state;
    }

    @Override
    public List<List<String>> executeQuery(String sql) throws SQLException {
        SQLQueryAdapter adapter = new SQLQueryAdapter(sql);
        try (SQLancerResultSet rs = adapter.executeAndGet(state, false)) {
            if (rs == null) {
                throw new IgnoreMeException();
            }
            return EETResultSetUtil.readAllRows(rs);
        }
    }
}