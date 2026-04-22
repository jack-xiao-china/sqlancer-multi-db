package sqlancer.mysql;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
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
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.MySQLSchema.MySQLTable.MySQLEngine;
import sqlancer.mysql.ast.MySQLConstant;

public class MySQLSchema extends AbstractSchema<MySQLGlobalState, MySQLTable> {

    private static final int NR_SCHEMA_READ_TRIES = 10;

    public enum MySQLDataType {
        INT, VARCHAR, FLOAT, DOUBLE, DECIMAL, DATE, TIME, DATETIME, TIMESTAMP, YEAR, BIT, ENUM, SET, JSON, BINARY, VARBINARY, BLOB;

        public static MySQLDataType getRandom(MySQLGlobalState globalState) {
            List<MySQLDataType> dataTypes = new ArrayList<>(Arrays.asList(values()));
            MySQLOptions options = globalState.getDbmsSpecificOptions();
            // 根据参数过滤类型（参数默认全部开启）
            if (!options.testBit) {
                dataTypes.remove(MySQLDataType.BIT);
            }
            if (!options.testEnums) {
                dataTypes.remove(MySQLDataType.ENUM);
            }
            if (!options.testSets) {
                dataTypes.remove(MySQLDataType.SET);
            }
            if (!options.testJSONDataType) {
                dataTypes.remove(MySQLDataType.JSON);
            }
            if (!options.testBinary) {
                dataTypes.remove(MySQLDataType.BINARY);
                dataTypes.remove(MySQLDataType.VARBINARY);
                dataTypes.remove(MySQLDataType.BLOB);
            }
            if (!options.testDates) {
                dataTypes.remove(MySQLDataType.DATE);
                dataTypes.remove(MySQLDataType.TIME);
                dataTypes.remove(MySQLDataType.DATETIME);
                dataTypes.remove(MySQLDataType.TIMESTAMP);
                dataTypes.remove(MySQLDataType.YEAR);
            }
            // 所有类型在所有 oracle 中可用（包括 PQS）
            return Randomly.fromList(dataTypes);
        }

        public boolean isNumeric() {
            switch (this) {
            case INT:
            case DOUBLE:
            case FLOAT:
            case DECIMAL:
            case BIT:  // BIT 类型也可以进行数值比较
                return true;
            case VARCHAR:
            case DATE:
            case TIME:
            case DATETIME:
            case TIMESTAMP:
            case YEAR:
            case ENUM:
            case SET:
            case JSON:
            case BINARY:
            case VARBINARY:
            case BLOB:
                return false;
            default:
                throw new AssertionError(this);
            }
        }

    }

    public static class MySQLColumn extends AbstractTableColumn<MySQLTable, MySQLDataType> {

        private final boolean isPrimaryKey;
        private final int precision;
        private List<String> enumValues;  // ENUM 值列表
        private List<String> setValues;   // SET 值列表
        private int bitWidth;             // BIT 宽度

        public enum CollateSequence {
            NOCASE, RTRIM, BINARY;

            public static CollateSequence random() {
                return Randomly.fromOptions(values());
            }
        }

        public MySQLColumn(String name, MySQLDataType columnType, boolean isPrimaryKey, int precision) {
            super(name, null, columnType);
            this.isPrimaryKey = isPrimaryKey;
            this.precision = precision;
        }

        public int getPrecision() {
            return precision;
        }

        public boolean isPrimaryKey() {
            return isPrimaryKey;
        }

        public List<String> getEnumValues() {
            return enumValues;
        }

        public void setEnumValues(List<String> enumValues) {
            this.enumValues = enumValues;
        }

        public List<String> getSetValues() {
            return setValues;
        }

        public void setSetValues(List<String> setValues) {
            this.setValues = setValues;
        }

        public int getBitWidth() {
            return bitWidth;
        }

        public void setBitWidth(int bitWidth) {
            this.bitWidth = bitWidth;
        }

    }

    public static class MySQLTables extends AbstractTables<MySQLTable, MySQLColumn> {

        public MySQLTables(List<MySQLTable> tables) {
            super(tables);
        }

        public MySQLRowValue getRandomRowValue(SQLConnection con) throws SQLException {
            String randomRow = String.format("SELECT %s FROM %s ORDER BY RAND() LIMIT 1", columnNamesAsString(
                    c -> c.getTable().getName() + "." + c.getName() + " AS " + c.getTable().getName() + c.getName()),
                    // columnNamesAsString(c -> "typeof(" + c.getTable().getName() + "." +
                    // c.getName() + ")")
                    tableNamesAsString());
            Map<MySQLColumn, MySQLConstant> values = new HashMap<>();
            try (Statement s = con.createStatement()) {
                ResultSet randomRowValues = s.executeQuery(randomRow);
                if (!randomRowValues.next()) {
                    throw new AssertionError("could not find random row! " + randomRow + "\n");
                }
                for (int i = 0; i < getColumns().size(); i++) {
                    MySQLColumn column = getColumns().get(i);
                    Object value;
                    int columnIndex = randomRowValues.findColumn(column.getTable().getName() + column.getName());
                    assert columnIndex == i + 1;
                    MySQLConstant constant;
                    if (randomRowValues.getString(columnIndex) == null) {
                        constant = MySQLConstant.createNullConstant();
                    } else {
                        switch (column.getType()) {
                        case INT:
                            value = randomRowValues.getLong(columnIndex);
                            constant = MySQLConstant.createIntConstant((long) value);
                            break;
                        case VARCHAR:
                            value = randomRowValues.getString(columnIndex);
                            constant = MySQLConstant.createStringConstant((String) value);
                            break;
                        case DATE: {
                            Date d = randomRowValues.getDate(columnIndex);
                            constant = MySQLConstant.createStringConstant(d.toString());
                            break;
                        }
                        case TIME: {
                            Time t = randomRowValues.getTime(columnIndex);
                            if (t == null) {
                                constant = MySQLConstant.createNullConstant();
                            } else {
                                constant = MySQLConstant.createStringConstant(t.toString());
                            }
                            break;
                        }
                        case DATETIME: {
                            Timestamp ts = randomRowValues.getTimestamp(columnIndex);
                            if (ts == null) {
                                constant = MySQLConstant.createNullConstant();
                            } else {
                                constant = MySQLConstant.createStringConstant(formatSqlDateTimeLiteral(ts));
                            }
                            break;
                        }
                        case TIMESTAMP: {
                            Timestamp ts = randomRowValues.getTimestamp(columnIndex);
                            if (ts == null) {
                                constant = MySQLConstant.createNullConstant();
                            } else {
                                constant = MySQLConstant.createStringConstant(formatSqlDateTimeLiteral(ts));
                            }
                            break;
                        }
                        case YEAR: {
                            int y = randomRowValues.getInt(columnIndex);
                            constant = MySQLConstant.createStringConstant(String.format("%04d", y));
                            break;
                        }
                        default:
                            throw new AssertionError(column.getType());
                        }
                    }
                    values.put(column, constant);
                }
                assert !randomRowValues.next();
                return new MySQLRowValue(this, values);
            }

        }

    }

    /** Formats JDBC {@link Timestamp} as a MySQL-compatible datetime literal string (date, space, time). */
    private static String formatSqlDateTimeLiteral(Timestamp ts) {
        if (ts == null) {
            return null;
        }
        return ts.toString();
    }

    private static MySQLDataType getColumnType(String typeString) {
        switch (typeString) {
        case "tinyint":
        case "smallint":
        case "mediumint":
        case "int":
        case "bigint":
            return MySQLDataType.INT;
        case "varchar":
        case "tinytext":
        case "mediumtext":
        case "text":
        case "longtext":
            return MySQLDataType.VARCHAR;
        case "double":
            return MySQLDataType.DOUBLE;
        case "float":
            return MySQLDataType.FLOAT;
        case "decimal":
            return MySQLDataType.DECIMAL;
        case "date":
            return MySQLDataType.DATE;
        case "time":
            return MySQLDataType.TIME;
        case "datetime":
            return MySQLDataType.DATETIME;
        case "timestamp":
            return MySQLDataType.TIMESTAMP;
        case "year":
            return MySQLDataType.YEAR;
        case "bit":
            return MySQLDataType.BIT;
        case "enum":
            return MySQLDataType.ENUM;
        case "set":
            return MySQLDataType.SET;
        case "json":
            return MySQLDataType.JSON;
        case "binary":
            return MySQLDataType.BINARY;
        case "varbinary":
            return MySQLDataType.VARBINARY;
        case "blob":
        case "tinyblob":
        case "mediumblob":
        case "longblob":
            return MySQLDataType.BLOB;
        default:
            throw new AssertionError(typeString);
        }
    }

    public static class MySQLRowValue extends AbstractRowValue<MySQLTables, MySQLColumn, MySQLConstant> {

        MySQLRowValue(MySQLTables tables, Map<MySQLColumn, MySQLConstant> values) {
            super(tables, values);
        }

    }

    public static class MySQLTable extends AbstractRelationalTable<MySQLColumn, MySQLIndex, MySQLGlobalState> {

        public enum MySQLEngine {
            INNO_DB("InnoDB"), MY_ISAM("MyISAM"), MEMORY("MEMORY"), HEAP("HEAP"), CSV("CSV"), MERGE("MERGE"),
            ARCHIVE("ARCHIVE"), FEDERATED("FEDERATED"), UNKNOWN("UNKNOWN");

            private String s;

            MySQLEngine(String s) {
                this.s = s;
            }

            public static MySQLEngine get(String val) {
                return Stream.of(values()).filter(engine -> engine.s.equalsIgnoreCase(val)).findFirst().orElse(UNKNOWN);
            }

        }

        private final MySQLEngine engine;
        private final boolean isTemporary;

        public MySQLTable(String tableName, List<MySQLColumn> columns, List<MySQLIndex> indexes, MySQLEngine engine) {
            super(tableName, columns, indexes, false);
            this.engine = engine;
            this.isTemporary = false;
        }

        public MySQLTable(String tableName, List<MySQLColumn> columns, List<MySQLIndex> indexes, MySQLEngine engine, boolean isView) {
            super(tableName, columns, indexes, isView);
            this.engine = engine;
            this.isTemporary = false;
        }

        public MySQLTable(String tableName, List<MySQLColumn> columns, List<MySQLIndex> indexes, MySQLEngine engine, boolean isView, boolean isTemporary) {
            super(tableName, columns, indexes, isView);
            this.engine = engine;
            this.isTemporary = isTemporary;
        }

        public MySQLEngine getEngine() {
            return engine;
        }

        public boolean hasPrimaryKey() {
            return getColumns().stream().anyMatch(c -> c.isPrimaryKey());
        }

        public boolean isTemporary() {
            return isTemporary;
        }

    }

    public static final class MySQLIndex extends TableIndex {

        private MySQLIndex(String indexName) {
            super(indexName);
        }

        public static MySQLIndex create(String indexName) {
            return new MySQLIndex(indexName);
        }

        @Override
        public String getIndexName() {
            if (super.getIndexName().contentEquals("PRIMARY")) {
                return "`PRIMARY`";
            } else {
                return super.getIndexName();
            }
        }

    }

    public static MySQLSchema fromConnection(SQLConnection con, String databaseName) throws SQLException {
        Exception ex = null;
        /* the loop is a workaround for https://bugs.mysql.com/bug.php?id=95929 */
        for (int i = 0; i < NR_SCHEMA_READ_TRIES; i++) {
            try {
                List<MySQLTable> databaseTables = new ArrayList<>();
                try (Statement s = con.createStatement()) {
                    // 获取 TABLE_TYPE 字段来区分表和视图
                    try (ResultSet rs = s.executeQuery(
                            "select TABLE_NAME, ENGINE, TABLE_TYPE from information_schema.TABLES where table_schema = '"
                                    + databaseName + "';")) {
                        while (rs.next()) {
                            String tableName = rs.getString("TABLE_NAME");
                            String tableEngineStr = rs.getString("ENGINE");
                            String tableType = rs.getString("TABLE_TYPE");
                            MySQLEngine engine = MySQLEngine.get(tableEngineStr);
                            boolean isView = tableType != null && tableType.equals("VIEW");

                            List<MySQLColumn> databaseColumns = getTableColumns(con, tableName, databaseName);
                            List<MySQLIndex> indexes = getIndexes(con, tableName, databaseName);
                            MySQLTable t = new MySQLTable(tableName, databaseColumns, indexes, engine, isView);
                            for (MySQLColumn c : databaseColumns) {
                                c.setTable(t);
                            }
                            databaseTables.add(t);
                        }
                    }
                }
                return new MySQLSchema(databaseTables);
            } catch (SQLIntegrityConstraintViolationException e) {
                ex = e;
            }
        }
        throw new AssertionError(ex);
    }

    private static List<MySQLIndex> getIndexes(SQLConnection con, String tableName, String databaseName)
            throws SQLException {
        List<MySQLIndex> indexes = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            try (ResultSet rs = s.executeQuery(String.format(
                    "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME='%s';",
                    databaseName, tableName))) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    indexes.add(MySQLIndex.create(indexName));
                }
            }
        }
        return indexes;
    }

    private static List<MySQLColumn> getTableColumns(SQLConnection con, String tableName, String databaseName)
            throws SQLException {
        List<MySQLColumn> columns = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            try (ResultSet rs = s.executeQuery("select * from information_schema.columns where table_schema = '"
                    + databaseName + "' AND TABLE_NAME='" + tableName + "'")) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String dataType = rs.getString("DATA_TYPE");
                    String columnType = rs.getString("COLUMN_TYPE");  // 完整类型定义，包含 ENUM/SET 值列表
                    int precision = rs.getInt("NUMERIC_PRECISION");
                    boolean isPrimaryKey = rs.getString("COLUMN_KEY").equals("PRI");
                    MySQLDataType type = getColumnType(dataType);
                    MySQLColumn c = new MySQLColumn(columnName, type, isPrimaryKey, precision);

                    // 解析 ENUM/SET 值列表和 BIT 宽度
                    if (type == MySQLDataType.ENUM) {
                        List<String> enumValues = parseEnumOrSetValues(columnType);
                        c.setEnumValues(enumValues);
                    } else if (type == MySQLDataType.SET) {
                        List<String> setValues = parseEnumOrSetValues(columnType);
                        c.setSetValues(setValues);
                    } else if (type == MySQLDataType.BIT) {
                        // 从 columnType 解析 BIT 宽度，格式为 "bit(n)"
                        int bitWidth = parseBitWidth(columnType);
                        c.setBitWidth(bitWidth);
                    }

                    columns.add(c);
                }
            }
        }
        return columns;
    }

    /**
     * 从 COLUMN_TYPE 字段解析 ENUM 或 SET 值列表
     * 例如：enum('e0','e1','e2') -> ["e0", "e1", "e2"]
     */
    private static List<String> parseEnumOrSetValues(String columnType) {
        // 找到括号内的内容
        int startIndex = columnType.indexOf('(');
        int endIndex = columnType.lastIndexOf(')');
        if (startIndex == -1 || endIndex == -1) {
            return new ArrayList<>();
        }
        String valuesStr = columnType.substring(startIndex + 1, endIndex);

        // 解析值列表，格式为 'value1','value2',...
        List<String> values = new ArrayList<>();
        // 简单解析：按逗号分割，去除引号
        String[] parts = valuesStr.split(",");
        for (String part : parts) {
            // 去除单引号
            String value = part.trim();
            if (value.startsWith("'") && value.endsWith("'")) {
                value = value.substring(1, value.length() - 1);
            }
            values.add(value);
        }
        return values;
    }

    /**
     * 从 COLUMN_TYPE 字段解析 BIT 宽度
     * 例如：bit(8) -> 8
     */
    private static int parseBitWidth(String columnType) {
        int startIndex = columnType.indexOf('(');
        int endIndex = columnType.lastIndexOf(')');
        if (startIndex == -1 || endIndex == -1) {
            return 1;  // 默认宽度
        }
        String widthStr = columnType.substring(startIndex + 1, endIndex);
        try {
            return Integer.parseInt(widthStr.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public MySQLSchema(List<MySQLTable> databaseTables) {
        super(databaseTables);
    }

    public MySQLTables getRandomTableNonEmptyTables() {
        return new MySQLTables(Randomly.nonEmptySubset(getDatabaseTables()));
    }

    /**
     * 获取一个随机的非视图表（用于 INSERT/UPDATE/DELETE 操作）
     */
    public MySQLTable getRandomTableNonView() {
        List<MySQLTable> nonViewTables = getDatabaseTables().stream()
                .filter(t -> !t.isView())
                .collect(java.util.stream.Collectors.toList());
        if (nonViewTables.isEmpty()) {
            return null;  // 返回 null，由调用方处理
        }
        return Randomly.fromList(nonViewTables);
    }

}
