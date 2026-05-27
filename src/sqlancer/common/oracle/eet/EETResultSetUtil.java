package sqlancer.common.oracle.eet;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import sqlancer.common.query.SQLancerResultSet;

/**
 * Reads full result sets into row lists for EET multiset comparison.
 */
public final class EETResultSetUtil {

    private EETResultSetUtil() {
    }

    public static List<List<String>> readAllRows(SQLancerResultSet rs) throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        if (rs == null) {
            return rows;
        }
        int cols = rs.getColumnCount();
        while (rs.next()) {
            List<String> row = new ArrayList<>(cols);
            for (int i = 1; i <= cols; i++) {
                row.add(rs.getString(i));
            }
            rows.add(row);
        }
        return rows;
    }
}