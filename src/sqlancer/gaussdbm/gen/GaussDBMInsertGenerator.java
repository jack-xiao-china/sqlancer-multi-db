package sqlancer.gaussdbm.gen;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBDataType;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;

public final class GaussDBMInsertGenerator {

    private GaussDBMInsertGenerator() {
    }

    public static SQLQueryAdapter insertRow(GaussDBMGlobalState globalState) throws SQLException {
        GaussDBTable table = globalState.getSchema().getRandomTable();
        return insertRow(globalState, table);
    }

    public static SQLQueryAdapter insertRow(GaussDBMGlobalState globalState, GaussDBTable table) throws SQLException {
        List<GaussDBColumn> columns = table.getRandomNonEmptyColumnSubset();
        String cols = columns.stream().map(GaussDBColumn::getName).collect(Collectors.joining(", "));
        String vals = columns.stream().map(c -> getRandomValueForType(c.getType())).collect(Collectors.joining(", "));
        String sql = "INSERT INTO " + table.getName() + "(" + cols + ") VALUES (" + vals + ")";
        return new SQLQueryAdapter(sql);
    }

    private static String getRandomValueForType(GaussDBDataType type) {
        // Occasionally return NULL
        if (Randomly.getBooleanWithSmallProbability()) {
            return "NULL";
        }
        switch (type) {
        case INT:
            return String.valueOf(Randomly.getNotCachedInteger(-1000, 1000));
        case VARCHAR:
            String s = new Randomly().getString();
            return "'" + s.substring(0, Math.min(10, s.length())).replace("\\", "\\\\").replace("'", "''") + "'";
        case FLOAT:
            return String.valueOf(Randomly.getNotCachedInteger(-100, 100)) + "." + Randomly.getNotCachedInteger(0, 99);
        case DOUBLE:
            return String.valueOf(Randomly.getNotCachedInteger(-1000, 1000)) + "."
                    + Randomly.getNotCachedInteger(0, 999);
        case DECIMAL:
            return String.valueOf(Randomly.getNotCachedInteger(-1000, 1000)) + "."
                    + String.format("%02d", (int) Randomly.getNotCachedInteger(0, 99));
        case DATE:
            // Format: YYYY-MM-DD
            int year = (int) Randomly.getNotCachedInteger(1970, 2038);
            int month = (int) Randomly.getNotCachedInteger(1, 12);
            int day = (int) Randomly.getNotCachedInteger(1, 28);
            return "'" + String.format("%04d-%02d-%02d", year, month, day) + "'";
        case TIME:
            // Format: HH:MM:SS
            int hour = (int) Randomly.getNotCachedInteger(0, 23);
            int minute = (int) Randomly.getNotCachedInteger(0, 59);
            int second = (int) Randomly.getNotCachedInteger(0, 59);
            return "'" + String.format("%02d:%02d:%02d", hour, minute, second) + "'";
        case DATETIME:
            // Format: YYYY-MM-DD HH:MM:SS
            year = (int) Randomly.getNotCachedInteger(1970, 2038);
            month = (int) Randomly.getNotCachedInteger(1, 12);
            day = (int) Randomly.getNotCachedInteger(1, 28);
            hour = (int) Randomly.getNotCachedInteger(0, 23);
            minute = (int) Randomly.getNotCachedInteger(0, 59);
            second = (int) Randomly.getNotCachedInteger(0, 59);
            return "'" + String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second) + "'";
        case TIMESTAMP:
            // Same as DATETIME for GaussDB M
            year = (int) Randomly.getNotCachedInteger(1970, 2038);
            month = (int) Randomly.getNotCachedInteger(1, 12);
            day = (int) Randomly.getNotCachedInteger(1, 28);
            hour = (int) Randomly.getNotCachedInteger(0, 23);
            minute = (int) Randomly.getNotCachedInteger(0, 59);
            second = (int) Randomly.getNotCachedInteger(0, 59);
            return "'" + String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second) + "'";
        case YEAR:
            // Format: YYYY (1901-2155)
            return "'" + String.format("%04d", (int) Randomly.getNotCachedInteger(1901, 2155)) + "'";
        default:
            throw new AssertionError(type);
        }
    }
}
