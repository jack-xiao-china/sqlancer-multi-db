package sqlancer.gaussdbm;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.common.schema.AbstractRelationalTable;
import sqlancer.common.schema.AbstractRowValue;
import sqlancer.common.schema.AbstractSchema;
import sqlancer.common.schema.AbstractTableColumn;
import sqlancer.common.schema.AbstractTables;
import sqlancer.common.schema.TableIndex;
import sqlancer.gaussdbm.ast.GaussDBConstant;

public class GaussDBMSchema extends AbstractSchema<GaussDBMGlobalState, GaussDBMSchema.GaussDBTable> {

    private static final int NR_SCHEMA_READ_TRIES = 10;

    public enum GaussDBDataType {
        INT, VARCHAR, FLOAT, DOUBLE, DECIMAL, DATE, TIME, DATETIME, TIMESTAMP, YEAR;

        public static GaussDBDataType getRandom(GaussDBMGlobalState globalState) {
            if (globalState.usesPQS()) {
                return Randomly.fromOptions(GaussDBDataType.INT, GaussDBDataType.VARCHAR);
            }
            return Randomly.fromOptions(values());
        }

        public boolean isNumeric() {
            switch (this) {
            case INT:
            case DOUBLE:
            case FLOAT:
            case DECIMAL:
                return true;
            case VARCHAR:
            case DATE:
            case TIME:
            case DATETIME:
            case TIMESTAMP:
            case YEAR:
                return false;
            default:
                throw new AssertionError(this);
            }
        }
    }

    public static class GaussDBColumn extends AbstractTableColumn<GaussDBTable, GaussDBDataType> {
        private final boolean isPrimaryKey;
        private final int precision;

        public GaussDBColumn(String name, GaussDBDataType columnType, boolean isPrimaryKey, int precision) {
            super(name, null, columnType);
            this.isPrimaryKey = isPrimaryKey;
            this.precision = precision;
        }

        public boolean isPrimaryKey() {
            return isPrimaryKey;
        }

        public int getPrecision() {
            return precision;
        }
    }

    public static class GaussDBTables extends AbstractTables<GaussDBTable, GaussDBColumn> {
        public GaussDBTables(List<GaussDBTable> tables) {
            super(tables);
        }

        public GaussDBRowValue getRandomRowValue(SQLConnection con) throws SQLException {
            String randomRow = String.format("SELECT %s FROM %s ORDER BY RAND() LIMIT 1", columnNamesAsString(
                    c -> c.getTable().getName() + "." + c.getName() + " AS " + c.getTable().getName() + c.getName()),
                    tableNamesAsString());
            Map<GaussDBColumn, GaussDBConstant> values = new HashMap<>();
            try (Statement s = con.createStatement()) {
                ResultSet randomRowValues = s.executeQuery(randomRow);
                if (!randomRowValues.next()) {
                    throw new AssertionError("could not find random row! " + randomRow + "\n");
                }
                for (int i = 0; i < getColumns().size(); i++) {
                    GaussDBColumn column = getColumns().get(i);
                    int columnIndex = randomRowValues.findColumn(column.getTable().getName() + column.getName());
                    assert columnIndex == i + 1;
                    GaussDBConstant constant;
                    if (randomRowValues.getString(columnIndex) == null) {
                        constant = GaussDBConstant.createNullConstant();
                    } else {
                        switch (column.getType()) {
                        case INT:
                            constant = GaussDBConstant.createIntConstant(randomRowValues.getLong(columnIndex));
                            break;
                        case VARCHAR:
                            constant = GaussDBConstant.createStringConstant(randomRowValues.getString(columnIndex));
                            break;
                        case DATE: {
                            Date d = randomRowValues.getDate(columnIndex);
                            constant = GaussDBConstant.createStringConstant(d.toString());
                            break;
                        }
                        case TIME: {
                            Time t = randomRowValues.getTime(columnIndex);
                            constant = GaussDBConstant.createStringConstant(t.toString());
                            break;
                        }
                        case DATETIME:
                        case TIMESTAMP: {
                            Timestamp ts = randomRowValues.getTimestamp(columnIndex);
                            constant = GaussDBConstant.createStringConstant(ts.toString());
                            break;
                        }
                        case YEAR: {
                            int y = randomRowValues.getInt(columnIndex);
                            constant = GaussDBConstant.createStringConstant(String.format("%04d", y));
                            break;
                        }
                        default:
                            throw new AssertionError(column.getType());
                        }
                    }
                    values.put(column, constant);
                }
                assert !randomRowValues.next();
                return new GaussDBRowValue(this, values);
            }
        }
    }

    public static class GaussDBRowValue extends AbstractRowValue<GaussDBTables, GaussDBColumn, GaussDBConstant> {
        GaussDBRowValue(GaussDBTables tables, Map<GaussDBColumn, GaussDBConstant> values) {
            super(tables, values);
        }
    }

    public static class GaussDBTable extends AbstractRelationalTable<GaussDBColumn, GaussDBIndex, GaussDBMGlobalState> {
        public enum GaussDBEngine {
            INNO_DB("InnoDB"), MY_ISAM("MyISAM"), MEMORY("MEMORY"), HEAP("HEAP"), CSV("CSV"), MERGE("MERGE"),
            ARCHIVE("ARCHIVE"), FEDERATED("FEDERATED"), UNKNOWN("UNKNOWN");

            private final String s;

            GaussDBEngine(String s) {
                this.s = s;
            }

            public static GaussDBEngine get(String val) {
                return Stream.of(values()).filter(engine -> engine.s.equalsIgnoreCase(val)).findFirst().orElse(UNKNOWN);
            }
        }

        private final GaussDBEngine engine;

        public GaussDBTable(String tableName, List<GaussDBColumn> columns, List<GaussDBIndex> indexes,
                GaussDBEngine engine) {
            super(tableName, columns, indexes, false);
            this.engine = engine;
        }

        public GaussDBEngine getEngine() {
            return engine;
        }
    }

    public static final class GaussDBIndex extends TableIndex {
        private GaussDBIndex(String indexName) {
            super(indexName);
        }

        public static GaussDBIndex create(String indexName) {
            return new GaussDBIndex(indexName);
        }
    }

    public static GaussDBMSchema fromConnection(SQLConnection con, String databaseName) throws SQLException {
        Exception ex = null;
        for (int i = 0; i < NR_SCHEMA_READ_TRIES; i++) {
            try {
                List<GaussDBTable> databaseTables = new ArrayList<>();
                try (Statement s = con.createStatement()) {
                    // For M-compatibility mode, use SHOW TABLES or query with database name as schema
                    // Try SHOW TABLES first (MySQL style), then fall back to information_schema
                    ResultSet rs = null;
                    try {
                        // MySQL-style query for M-compatibility
                        rs = s.executeQuery("SHOW TABLES");
                        while (rs.next()) {
                            String tableName = rs.getString(1);
                            List<GaussDBColumn> databaseColumns = getTableColumns(con, tableName, databaseName);
                            List<GaussDBIndex> indexes = getIndexes(con, tableName, databaseName);
                            GaussDBTable t = new GaussDBTable(tableName, databaseColumns, indexes, GaussDBTable.GaussDBEngine.UNKNOWN);
                            for (GaussDBColumn c : databaseColumns) {
                                c.setTable(t);
                            }
                            databaseTables.add(t);
                        }
                    } catch (SQLException e) {
                        // Fall back to information_schema query
                        if (rs != null) {
                            rs.close();
                        }
                        // Use LOWER() for case-insensitive matching
                        String dbNameLower = databaseName.toLowerCase();
                        try {
                            rs = s.executeQuery(
                                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE LOWER(table_schema) = '"
                                + dbNameLower + "' OR LOWER(table_schema) = LOWER(DATABASE())");
                            while (rs.next()) {
                                String tableName = rs.getString("TABLE_NAME");
                                List<GaussDBColumn> databaseColumns = getTableColumns(con, tableName, databaseName);
                                List<GaussDBIndex> indexes = getIndexes(con, tableName, databaseName);
                                GaussDBTable t = new GaussDBTable(tableName, databaseColumns, indexes, GaussDBTable.GaussDBEngine.UNKNOWN);
                                for (GaussDBColumn c : databaseColumns) {
                                    c.setTable(t);
                                }
                                databaseTables.add(t);
                            }
                        } catch (SQLException e2) {
                            // Last resort: try pg_tables for GaussDB
                            rs = s.executeQuery(
                                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' OR schemaname = current_schema()");
                            while (rs.next()) {
                                String tableName = rs.getString(1);
                                List<GaussDBColumn> databaseColumns = getTableColumns(con, tableName, databaseName);
                                List<GaussDBIndex> indexes = getIndexes(con, tableName, databaseName);
                                GaussDBTable t = new GaussDBTable(tableName, databaseColumns, indexes, GaussDBTable.GaussDBEngine.UNKNOWN);
                                for (GaussDBColumn c : databaseColumns) {
                                    c.setTable(t);
                                }
                                databaseTables.add(t);
                            }
                        }
                    }
                    if (rs != null) {
                        rs.close();
                    }
                }
                return new GaussDBMSchema(databaseTables);
            } catch (SQLIntegrityConstraintViolationException e) {
                ex = e;
            }
        }
        throw new AssertionError(ex);
    }

    private static List<GaussDBIndex> getIndexes(SQLConnection con, String tableName, String databaseName)
            throws SQLException {
        List<GaussDBIndex> indexes = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            try {
                // Try MySQL-style SHOW INDEX
                try (ResultSet rs = s.executeQuery("SHOW INDEX FROM " + tableName)) {
                    while (rs.next()) {
                        String indexName = rs.getString("Key_name");
                        if (!"PRIMARY".equals(indexName)) {
                            indexes.add(GaussDBIndex.create(indexName));
                        }
                    }
                }
            } catch (SQLException e) {
                // Fall back to information_schema
                try (ResultSet rs = s.executeQuery(String.format(
                    "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS WHERE (TABLE_SCHEMA = '%s' OR TABLE_SCHEMA = DATABASE()) AND TABLE_NAME='%s'",
                    databaseName, tableName))) {
                    while (rs.next()) {
                        String indexName = rs.getString("INDEX_NAME");
                        indexes.add(GaussDBIndex.create(indexName));
                    }
                }
            }
        } catch (SQLException e) {
            // Ignore index query errors
        }
        return indexes;
    }

    private static GaussDBDataType getColumnType(String typeString) {
        switch (typeString.toLowerCase()) {
        case "tinyint":
        case "smallint":
        case "mediumint":
        case "int":
        case "integer":
        case "bigint":
            return GaussDBDataType.INT;
        case "varchar":
        case "char":
        case "tinytext":
        case "mediumtext":
        case "text":
        case "longtext":
        case "enum":
        case "set":
            return GaussDBDataType.VARCHAR;
        case "double":
        case "real":
            return GaussDBDataType.DOUBLE;
        case "float":
            return GaussDBDataType.FLOAT;
        case "decimal":
        case "numeric":
            return GaussDBDataType.DECIMAL;
        case "date":
            return GaussDBDataType.DATE;
        case "time":
            return GaussDBDataType.TIME;
        case "datetime":
            return GaussDBDataType.DATETIME;
        case "timestamp":
            return GaussDBDataType.TIMESTAMP;
        case "year":
            return GaussDBDataType.YEAR;
        case "binary":
        case "varbinary":
        case "blob":
        case "tinyblob":
        case "mediumblob":
        case "longblob":
        case "bit":
            // Binary types are treated as VARCHAR for now
            return GaussDBDataType.VARCHAR;
        case "json":
            // JSON type treated as VARCHAR
            return GaussDBDataType.VARCHAR;
        default:
            throw new AssertionError("Unknown type: " + typeString);
        }
    }

    private static List<GaussDBColumn> getTableColumns(SQLConnection con, String tableName, String databaseName)
            throws SQLException {
        List<GaussDBColumn> columns = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            try {
                // Try MySQL-style DESCRIBE/SHOW COLUMNS
                try (ResultSet rs = s.executeQuery("SHOW COLUMNS FROM " + tableName)) {
                    while (rs.next()) {
                        String columnName = rs.getString("Field");
                        String dataType = rs.getString("Type");
                        // Extract base type from type string (e.g., "varchar(20)" -> "varchar")
                        String baseType = dataType.split("\\(")[0].split(" ")[0].toLowerCase();
                        String columnKey = rs.getString("Key");
                        boolean isPrimaryKey = "PRI".equals(columnKey);
                        GaussDBColumn c = new GaussDBColumn(columnName, getColumnType(baseType), isPrimaryKey, 0);
                        columns.add(c);
                    }
                }
            } catch (SQLException e) {
                // Fall back to information_schema
                // Use LOWER() for case-insensitive matching
                String dbNameLower = databaseName.toLowerCase();
                try (ResultSet rs = s.executeQuery(
                    "SELECT COLUMN_NAME, DATA_TYPE, NUMERIC_PRECISION, COLUMN_KEY FROM information_schema.columns WHERE (LOWER(table_schema) = '"
                    + dbNameLower + "' OR LOWER(table_schema) = LOWER(DATABASE())) AND TABLE_NAME='" + tableName + "'")) {
                    while (rs.next()) {
                        String columnName = rs.getString("COLUMN_NAME");
                        String dataType = rs.getString("DATA_TYPE");
                        int precision = rs.getInt("NUMERIC_PRECISION");
                        String columnKey = rs.getString("COLUMN_KEY");
                        boolean isPrimaryKey = columnKey != null && columnKey.equals("PRI");
                        GaussDBColumn c = new GaussDBColumn(columnName, getColumnType(dataType), isPrimaryKey, precision);
                        columns.add(c);
                    }
                }
            }
        }
        return columns;
    }

    public GaussDBMSchema(List<GaussDBTable> databaseTables) {
        super(databaseTables);
    }

    public GaussDBTables getRandomTableNonEmptyTables() {
        return new GaussDBTables(Randomly.nonEmptySubset(getDatabaseTables()));
    }
}

