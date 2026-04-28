package sqlancer.gaussdbm.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBDataType;

public final class GaussDBMTableGenerator {

    private GaussDBMTableGenerator() {
    }

    public static SQLQueryAdapter generate(GaussDBMGlobalState globalState, String tableName) {
        int nrColumns = (int) Randomly.getNotCachedInteger(1, 4);
        List<String> columnDefs = new ArrayList<>();
        for (int i = 0; i < nrColumns; i++) {
            String col = "c" + i;
            GaussDBDataType type = GaussDBDataType.getRandom(globalState);
            String columnDef = getColumnDefinition(col, type);
            columnDefs.add(columnDef);
        }
        String sql = "CREATE TABLE " + tableName + " (" + columnDefs.stream().collect(Collectors.joining(", ")) + ")";
        return new SQLQueryAdapter(sql, true);
    }

    private static String getColumnDefinition(String colName, GaussDBDataType type) {
        switch (type) {
        case INT:
            return colName + " INT";
        case VARCHAR:
            return colName + " VARCHAR(20)";
        case FLOAT:
            return colName + " FLOAT";
        case DOUBLE:
            return colName + " DOUBLE";
        case DECIMAL:
            return colName + " DECIMAL(10, 2)";
        case DATE:
            return colName + " DATE";
        case TIME:
            return colName + " TIME";
        case DATETIME:
            return colName + " DATETIME";
        case TIMESTAMP:
            return colName + " TIMESTAMP";
        case YEAR:
            return colName + " YEAR";
        default:
            throw new AssertionError(type);
        }
    }
}
