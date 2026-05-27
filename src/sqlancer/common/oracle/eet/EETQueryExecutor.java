package sqlancer.common.oracle.eet;

import java.sql.SQLException;
import java.util.List;

/**
 * Abstraction for executing a SQL string and returning row multisets (for testing and production).
 */
public interface EETQueryExecutor {

    List<List<String>> executeQuery(String sql) throws SQLException;
}