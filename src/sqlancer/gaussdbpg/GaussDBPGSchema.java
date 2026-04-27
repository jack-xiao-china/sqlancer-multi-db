package sqlancer.gaussdbpg;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.common.schema.AbstractRelationalTable;
import sqlancer.common.schema.AbstractRowValue;
import sqlancer.common.schema.AbstractSchema;
import sqlancer.common.schema.AbstractTableColumn;
import sqlancer.common.schema.AbstractTables;
import sqlancer.common.schema.TableIndex;
import sqlancer.gaussdbpg.ast.GaussDBPGConstant;
import sqlancer.gaussdbpg.ast.GaussDBPGDataType;

public class GaussDBPGSchema extends AbstractSchema<GaussDBPGGlobalState, GaussDBPGSchema.GaussDBPGTable> {

    private static final int NR_SCHEMA_READ_TRIES = 10;

    public enum GaussDBPGCompositeDataType {
        INT, BOOLEAN, TEXT, DECIMAL, FLOAT, REAL, DATE, TIME, TIMESTAMP, TIMESTAMPTZ, INTERVAL;

        public GaussDBPGDataType getPrimitiveDataType() {
            switch (this) {
            case INT:
                return GaussDBPGDataType.INT;
            case BOOLEAN:
                return GaussDBPGDataType.BOOLEAN;
            case TEXT:
                return GaussDBPGDataType.TEXT;
            case DECIMAL:
                return GaussDBPGDataType.DECIMAL;
            case FLOAT:
                return GaussDBPGDataType.FLOAT;
            case REAL:
                return GaussDBPGDataType.REAL;
            case DATE:
                return GaussDBPGDataType.DATE;
            case TIME:
                return GaussDBPGDataType.TIME;
            case TIMESTAMP:
                return GaussDBPGDataType.TIMESTAMP;
            case TIMESTAMPTZ:
                return GaussDBPGDataType.TIMESTAMPTZ;
            case INTERVAL:
                return GaussDBPGDataType.INTERVAL;
            default:
                throw new AssertionError(this);
            }
        }

        public static GaussDBPGCompositeDataType getRandom() {
            return Randomly.fromOptions(values());
        }
    }

    public static class GaussDBPGColumn extends AbstractTableColumn<GaussDBPGTable, GaussDBPGDataType> {

        public GaussDBPGColumn(String name, GaussDBPGDataType columnType) {
            super(name, null, columnType);
        }

        public static GaussDBPGColumn createDummy(String name) {
            return new GaussDBPGColumn(name, GaussDBPGDataType.INT);
        }
    }

    public static class GaussDBPGTables extends AbstractTables<GaussDBPGTable, GaussDBPGColumn> {

        public GaussDBPGTables(List<GaussDBPGTable> tables) {
            super(tables);
        }

        public GaussDBPGRowValue getRandomRowValue(SQLConnection con, GaussDBPGGlobalState state) throws SQLException {
            String randomRow = String.format(
                    "SELECT %s FROM %s ORDER BY RANDOM() LIMIT 1",
                    columnNamesAsString(c -> c.getTable().getName() + "." + c.getName() + " AS "
                            + c.getTable().getName() + c.getName()),
                    tableNamesAsString());
            Map<GaussDBPGColumn, GaussDBPGConstant> values = new HashMap<>();
            try (Statement s = con.createStatement()) {
                ResultSet randomRowValues = s.executeQuery(randomRow);
                if (!randomRowValues.next()) {
                    throw new AssertionError("could not find random row! " + randomRow + "\n");
                }
                for (int i = 0; i < getColumns().size(); i++) {
                    GaussDBPGColumn column = getColumns().get(i);
                    int columnIndex = randomRowValues.findColumn(column.getTable().getName() + column.getName());
                    assert columnIndex == i + 1;
                    GaussDBPGConstant constant;
                    if (randomRowValues.getString(columnIndex) == null) {
                        constant = GaussDBPGConstant.createNullConstant();
                    } else {
                        switch (column.getType()) {
                        case INT:
                            constant = GaussDBPGConstant.createIntConstant(randomRowValues.getLong(columnIndex));
                            break;
                        case BOOLEAN:
                            constant = GaussDBPGConstant.createBooleanConstant(randomRowValues.getBoolean(columnIndex));
                            break;
                        case TEXT:
                            constant = GaussDBPGConstant.createTextConstant(randomRowValues.getString(columnIndex));
                            break;
                        case DECIMAL:
                            constant = GaussDBPGConstant
                                    .createDecimalConstant(randomRowValues.getBigDecimal(columnIndex));
                            break;
                        case FLOAT:
                        case REAL:
                            constant = GaussDBPGConstant.createFloatConstant(randomRowValues.getDouble(columnIndex));
                            break;
                        case DATE:
                            constant = GaussDBPGConstant
                                    .createDateConstant(randomRowValues.getDate(columnIndex).toLocalDate());
                            break;
                        case TIME:
                            constant = GaussDBPGConstant
                                    .createTimeConstant(randomRowValues.getTime(columnIndex).toLocalTime());
                            break;
                        case TIMESTAMP:
                            constant = GaussDBPGConstant.createTimestampConstant(
                                    randomRowValues.getTimestamp(columnIndex).toLocalDateTime());
                            break;
                        default:
                            throw new IgnoreMeException();
                        }
                    }
                    values.put(column, constant);
                }
                assert !randomRowValues.next();
                return new GaussDBPGRowValue(this, values);
            }
        }
    }

    public static class GaussDBPGRowValue
            extends AbstractRowValue<GaussDBPGTables, GaussDBPGColumn, GaussDBPGConstant> {
        GaussDBPGRowValue(GaussDBPGTables tables, Map<GaussDBPGColumn, GaussDBPGConstant> values) {
            super(tables, values);
        }
    }

    public static class GaussDBPGTable
            extends AbstractRelationalTable<GaussDBPGColumn, GaussDBPGIndex, GaussDBPGGlobalState> {

        public enum TableType {
            STANDARD, TEMPORARY
        }

        private final TableType tableType;

        public GaussDBPGTable(String tableName, List<GaussDBPGColumn> columns, List<GaussDBPGIndex> indexes,
                TableType tableType, boolean isView) {
            super(tableName, columns, indexes, isView);
            this.tableType = tableType;
        }

        public TableType getTableType() {
            return tableType;
        }
    }

    public static final class GaussDBPGIndex extends TableIndex {
        private GaussDBPGIndex(String indexName) {
            super(indexName);
        }

        public static GaussDBPGIndex create(String indexName) {
            return new GaussDBPGIndex(indexName);
        }
    }

    public static GaussDBPGSchema fromConnection(SQLConnection con, String databaseName) throws SQLException {
        Exception ex = null;
        for (int i = 0; i < NR_SCHEMA_READ_TRIES; i++) {
            try {
                List<GaussDBPGTable> databaseTables = new ArrayList<>();
                try (Statement s = con.createStatement()) {
                    // PG-style schema query: use information_schema.tables with the actual schema name
                    // Use LOWER() for case-insensitive matching
                    String schemaNameLower = databaseName.toLowerCase();
                    try (ResultSet rs = s.executeQuery(
                            "SELECT table_name, table_schema, table_type FROM information_schema.tables "
                                    + "WHERE LOWER(table_schema)='" + schemaNameLower + "' OR table_schema LIKE 'pg_temp_%' "
                                    + "ORDER BY table_name;")) {
                        while (rs.next()) {
                            String tableName = rs.getString("table_name");
                            String tableSchema = rs.getString("table_schema");
                            String tableTypeStr = rs.getString("table_type");
                            boolean isView = tableTypeStr.contains("VIEW");
                            GaussDBPGTable.TableType tableType = getTableType(tableSchema);
                            List<GaussDBPGColumn> databaseColumns = getTableColumns(con, tableName, databaseName);
                            List<GaussDBPGIndex> indexes = getIndexes(con, tableName);
                            GaussDBPGTable t = new GaussDBPGTable(tableName, databaseColumns, indexes, tableType,
                                    isView);
                            for (GaussDBPGColumn c : databaseColumns) {
                                c.setTable(t);
                            }
                            databaseTables.add(t);
                        }
                    }
                }
                return new GaussDBPGSchema(databaseTables);
            } catch (SQLException e) {
                ex = e;
            }
        }
        throw new AssertionError(ex);
    }

    protected static GaussDBPGTable.TableType getTableType(String tableSchema) {
        if (tableSchema.equals("public") || tableSchema.startsWith("tb")) {
            // public schema or our test schemas (tb0, tb1, etc.)
            return GaussDBPGTable.TableType.STANDARD;
        } else if (tableSchema.startsWith("pg_temp")) {
            return GaussDBPGTable.TableType.TEMPORARY;
        } else {
            // Unknown schema - treat as standard
            return GaussDBPGTable.TableType.STANDARD;
        }
    }

    protected static List<GaussDBPGIndex> getIndexes(SQLConnection con, String tableName) throws SQLException {
        List<GaussDBPGIndex> indexes = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            // PG-style index query: use pg_indexes
            try (ResultSet rs = s.executeQuery(String.format(
                    "SELECT indexname FROM pg_indexes WHERE tablename='%s' ORDER BY indexname;", tableName))) {
                while (rs.next()) {
                    String indexName = rs.getString("indexname");
                    indexes.add(GaussDBPGIndex.create(indexName));
                }
            }
        }
        return indexes;
    }

    protected static List<GaussDBPGColumn> getTableColumns(SQLConnection con, String tableName, String schemaName) throws SQLException {
        List<GaussDBPGColumn> columns = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            // PG-style column query: use information_schema.columns
            // Use LOWER() for case-insensitive matching
            String schemaNameLower = schemaName.toLowerCase();
            try (ResultSet rs = s.executeQuery(
                    "SELECT column_name, data_type FROM information_schema.columns "
                            + "WHERE table_name = '" + tableName + "' AND LOWER(table_schema) = '" + schemaNameLower + "' "
                            + "ORDER BY column_name")) {
                while (rs.next()) {
                    String columnName = rs.getString("column_name");
                    String dataType = rs.getString("data_type");
                    GaussDBPGDataType resolved = getColumnType(dataType);
                    GaussDBPGColumn c = new GaussDBPGColumn(columnName, resolved);
                    columns.add(c);
                }
            }
        }
        return columns;
    }

    public static GaussDBPGDataType getColumnType(String typeString) {
        // PG-style type mapping
        switch (typeString.toLowerCase()) {
        case "smallint":
        case "integer":
        case "bigint":
        case "int":
        case "int2":
        case "int4":
        case "int8":
            return GaussDBPGDataType.INT;
        case "boolean":
        case "bool":
            return GaussDBPGDataType.BOOLEAN;
        case "text":
        case "character varying":
        case "varchar":
        case "character":
        case "char":
        case "name":
            return GaussDBPGDataType.TEXT;
        case "numeric":
        case "decimal":
            return GaussDBPGDataType.DECIMAL;
        case "double precision":
        case "float8":
            return GaussDBPGDataType.FLOAT;
        case "real":
        case "float4":
            return GaussDBPGDataType.REAL;
        case "date":
            return GaussDBPGDataType.DATE;
        case "time":
        case "time without time zone":
            return GaussDBPGDataType.TIME;
        case "timestamp":
        case "timestamp without time zone":
            return GaussDBPGDataType.TIMESTAMP;
        case "timestamp with time zone":
        case "timestamptz":
            return GaussDBPGDataType.TIMESTAMPTZ;
        case "interval":
            return GaussDBPGDataType.INTERVAL;
        case "array":
            // Arrays - treat as TEXT for simplicity
            return GaussDBPGDataType.TEXT;
        case "user-defined":
            // Enums and other - treat as TEXT
            return GaussDBPGDataType.TEXT;
        default:
            // Default to TEXT for unknown types
            return GaussDBPGDataType.TEXT;
        }
    }

    public GaussDBPGSchema(List<GaussDBPGTable> databaseTables) {
        super(databaseTables);
    }

    public GaussDBPGTables getRandomTableNonEmptyTables() {
        return new GaussDBPGTables(Randomly.nonEmptySubset(getDatabaseTables()));
    }

    public GaussDBPGTable getRandomDatabaseTable() {
        return Randomly.fromList(getDatabaseTables());
    }
}