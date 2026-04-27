package sqlancer.gaussdba;

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
import sqlancer.gaussdba.ast.GaussDBAConstant;
import sqlancer.gaussdba.ast.GaussDBADataType;

public class GaussDBASchema extends AbstractSchema<GaussDBAGlobalState, GaussDBASchema.GaussDBATable> {

    private static final int NR_SCHEMA_READ_TRIES = 10;

    // Oracle风格数据类型
    public enum GaussDBACompositeDataType {
        NUMBER, VARCHAR2, DATE, TIMESTAMP, CLOB, BLOB;

        public GaussDBADataType getPrimitiveDataType() {
            switch (this) {
            case NUMBER:
                return GaussDBADataType.NUMBER;
            case VARCHAR2:
                return GaussDBADataType.VARCHAR2;
            case DATE:
                return GaussDBADataType.DATE;
            case TIMESTAMP:
                return GaussDBADataType.TIMESTAMP;
            case CLOB:
                return GaussDBADataType.CLOB;
            case BLOB:
                return GaussDBADataType.BLOB;
            default:
                throw new AssertionError(this);
            }
        }

        public static GaussDBACompositeDataType getRandom() {
            return Randomly.fromOptions(values());
        }
    }

    public static class GaussDBAColumn extends AbstractTableColumn<GaussDBATable, GaussDBADataType> {

        public GaussDBAColumn(String name, GaussDBADataType columnType) {
            super(name, null, columnType);
        }

        public static GaussDBAColumn createDummy(String name) {
            return new GaussDBAColumn(name, GaussDBADataType.NUMBER);
        }
    }

    public static class GaussDBATables extends AbstractTables<GaussDBATable, GaussDBAColumn> {

        public GaussDBATables(List<GaussDBATable> tables) {
            super(tables);
        }

        public GaussDBARowValue getRandomRowValue(SQLConnection con, GaussDBAGlobalState state) throws SQLException {
            String randomRow = String.format(
                    "SELECT %s FROM %s ORDER BY RANDOM() LIMIT 1",
                    columnNamesAsString(c -> c.getTable().getName() + "." + c.getName() + " AS "
                            + c.getTable().getName() + c.getName()),
                    tableNamesAsString());
            Map<GaussDBAColumn, GaussDBAConstant> values = new HashMap<>();
            try (Statement s = con.createStatement()) {
                ResultSet randomRowValues = s.executeQuery(randomRow);
                if (!randomRowValues.next()) {
                    throw new AssertionError("could not find random row! " + randomRow + "\n");
                }
                for (int i = 0; i < getColumns().size(); i++) {
                    GaussDBAColumn column = getColumns().get(i);
                    int columnIndex = randomRowValues.findColumn(column.getTable().getName() + column.getName());
                    assert columnIndex == i + 1;
                    GaussDBAConstant constant;
                    if (randomRowValues.getString(columnIndex) == null) {
                        constant = GaussDBAConstant.createNullConstant();
                    } else {
                        switch (column.getType()) {
                        case NUMBER:
                            constant = GaussDBAConstant.createNumberConstant(randomRowValues.getLong(columnIndex));
                            break;
                        case VARCHAR2:
                            String strVal = randomRowValues.getString(columnIndex);
                            // Oracle语义：空字符串被视为NULL
                            if (strVal.isEmpty()) {
                                constant = GaussDBAConstant.createNullConstant();
                            } else {
                                constant = GaussDBAConstant.createVarchar2Constant(strVal);
                            }
                            break;
                        case DATE:
                            constant = GaussDBAConstant
                                    .createDateConstant(randomRowValues.getDate(columnIndex).toLocalDate());
                            break;
                        case TIMESTAMP:
                            constant = GaussDBAConstant.createTimestampConstant(
                                    randomRowValues.getTimestamp(columnIndex).toLocalDateTime());
                            break;
                        default:
                            throw new IgnoreMeException();
                        }
                    }
                    values.put(column, constant);
                }
                assert !randomRowValues.next();
                return new GaussDBARowValue(this, values);
            }
        }
    }

    public static class GaussDBARowValue
            extends AbstractRowValue<GaussDBATables, GaussDBAColumn, GaussDBAConstant> {
        GaussDBARowValue(GaussDBATables tables, Map<GaussDBAColumn, GaussDBAConstant> values) {
            super(tables, values);
        }
    }

    public static class GaussDBATable
            extends AbstractRelationalTable<GaussDBAColumn, GaussDBAIndex, GaussDBAGlobalState> {

        public enum TableType {
            STANDARD, TEMPORARY
        }

        private final TableType tableType;

        public GaussDBATable(String tableName, List<GaussDBAColumn> columns, List<GaussDBAIndex> indexes,
                TableType tableType, boolean isView) {
            super(tableName, columns, indexes, isView);
            this.tableType = tableType;
        }

        public TableType getTableType() {
            return tableType;
        }
    }

    public static final class GaussDBAIndex extends TableIndex {
        private GaussDBAIndex(String indexName) {
            super(indexName);
        }

        public static GaussDBAIndex create(String indexName) {
            return new GaussDBAIndex(indexName);
        }
    }

    public static GaussDBASchema fromConnection(SQLConnection con, String databaseName) throws SQLException {
        Exception ex = null;
        for (int i = 0; i < NR_SCHEMA_READ_TRIES; i++) {
            try {
                List<GaussDBATable> databaseTables = new ArrayList<>();
                try (Statement s = con.createStatement()) {
                    // A兼容模式也使用information_schema查询
                    // 查询当前schema（可能是测试schema或public）中的表
                    // Use LOWER() to handle case-insensitive schema matching
                    String schemaNameLower = databaseName.toLowerCase();
                    try (ResultSet rs = s.executeQuery(
                            "SELECT table_name, table_schema, table_type FROM information_schema.tables "
                                    + "WHERE LOWER(table_schema)='" + schemaNameLower + "' OR table_schema='public' OR table_schema LIKE 'pg_temp_%' "
                                    + "ORDER BY table_name;")) {
                        while (rs.next()) {
                            String tableName = rs.getString("table_name");
                            String tableSchema = rs.getString("table_schema");
                            String tableTypeStr = rs.getString("table_type");
                            boolean isView = tableTypeStr.contains("VIEW");
                            GaussDBATable.TableType tableType = getTableType(tableSchema);
                            List<GaussDBAColumn> databaseColumns = getTableColumns(con, tableName, tableSchema);
                            List<GaussDBAIndex> indexes = getIndexes(con, tableName);
                            GaussDBATable t = new GaussDBATable(tableName, databaseColumns, indexes, tableType,
                                    isView);
                            for (GaussDBAColumn c : databaseColumns) {
                                c.setTable(t);
                            }
                            databaseTables.add(t);
                        }
                    }
                }
                return new GaussDBASchema(databaseTables);
            } catch (SQLException e) {
                ex = e;
            }
        }
        throw new AssertionError(ex);
    }

    protected static GaussDBATable.TableType getTableType(String tableSchema) {
        if (tableSchema.equals("public") || tableSchema.startsWith("tb")) {
            return GaussDBATable.TableType.STANDARD;
        } else if (tableSchema.startsWith("pg_temp")) {
            return GaussDBATable.TableType.TEMPORARY;
        } else {
            // Default to STANDARD for other schemas (like test schemas)
            return GaussDBATable.TableType.STANDARD;
        }
    }

    protected static List<GaussDBAIndex> getIndexes(SQLConnection con, String tableName) throws SQLException {
        List<GaussDBAIndex> indexes = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            try (ResultSet rs = s.executeQuery(String.format(
                    "SELECT indexname FROM pg_indexes WHERE tablename='%s' ORDER BY indexname;", tableName))) {
                while (rs.next()) {
                    String indexName = rs.getString("indexname");
                    indexes.add(GaussDBAIndex.create(indexName));
                }
            }
        }
        return indexes;
    }

    protected static List<GaussDBAColumn> getTableColumns(SQLConnection con, String tableName, String tableSchema) throws SQLException {
        List<GaussDBAColumn> columns = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            // Use LOWER() for case-insensitive matching
            try (ResultSet rs = s.executeQuery(
                    "SELECT column_name, data_type FROM information_schema.columns "
                            + "WHERE table_name = '" + tableName + "' AND LOWER(table_schema) = '" + tableSchema.toLowerCase() + "' "
                            + "ORDER BY column_name")) {
                while (rs.next()) {
                    String columnName = rs.getString("column_name");
                    String dataType = rs.getString("data_type");
                    GaussDBADataType resolved = getColumnType(dataType);
                    GaussDBAColumn c = new GaussDBAColumn(columnName, resolved);
                    columns.add(c);
                }
            }
        }
        return columns;
    }

    // Oracle风格类型映射
    public static GaussDBADataType getColumnType(String typeString) {
        switch (typeString.toLowerCase()) {
        case "number":
        case "numeric":
        case "decimal":
        case "integer":
        case "int":
        case "smallint":
        case "bigint":
            return GaussDBADataType.NUMBER;
        case "varchar2":
        case "varchar":
        case "character varying":
        case "char":
        case "character":
        case "nvarchar2":
        case "text":
            return GaussDBADataType.VARCHAR2;
        case "date":
            return GaussDBADataType.DATE;
        case "timestamp":
        case "timestamp without time zone":
        case "timestamp with time zone":
            return GaussDBADataType.TIMESTAMP;
        case "clob":
        case "nclob":
            return GaussDBADataType.CLOB;
        case "blob":
        case "raw":
        case "long raw":
            return GaussDBADataType.BLOB;
        // 处理PG风格类型（A兼容可能也支持）
        case "int2":
        case "int4":
        case "int8":
            return GaussDBADataType.NUMBER;
        case "bool":
        case "boolean":
            // A模式无BOOLEAN，用NUMBER(1)模拟
            return GaussDBADataType.NUMBER;
        case "float4":
        case "float8":
        case "double precision":
        case "real":
            return GaussDBADataType.NUMBER;
        default:
            // 默认处理为VARCHAR2
            return GaussDBADataType.VARCHAR2;
        }
    }

    public GaussDBASchema(List<GaussDBATable> databaseTables) {
        super(databaseTables);
    }

    public GaussDBATables getRandomTableNonEmptyTables() {
        return new GaussDBATables(Randomly.nonEmptySubset(getDatabaseTables()));
    }

    public GaussDBATable getRandomDatabaseTable() {
        return Randomly.fromList(getDatabaseTables());
    }
}