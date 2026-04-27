package sqlancer.postgres;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.postgresql.util.PSQLException;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.common.DBMSCommon;
import sqlancer.common.schema.AbstractRelationalTable;
import sqlancer.common.schema.AbstractRowValue;
import sqlancer.common.schema.AbstractSchema;
import sqlancer.common.schema.AbstractTableColumn;
import sqlancer.common.schema.AbstractTables;
import sqlancer.common.schema.TableIndex;
import sqlancer.postgres.PostgresSchema.PostgresTable;
import sqlancer.postgres.PostgresSchema.PostgresTable.TableType;
import sqlancer.postgres.ast.PostgresConstant;

public class PostgresSchema extends AbstractSchema<PostgresGlobalState, PostgresTable> {

    private final String databaseName;

    public enum PostgresDataType {
        INT, BOOLEAN, TEXT, VARCHAR, CHAR, DECIMAL, FLOAT, REAL, RANGE, MONEY, BIT, INET, //
        DATE, TIME, TIMETZ, TIMESTAMP, TIMESTAMPTZ, INTERVAL, //
        JSON, JSONB, //
        UUID, BYTEA, //
        ARRAY, //
        ENUM;

        public static PostgresDataType getRandomType() {
            List<PostgresDataType> dataTypes = new ArrayList<>(Arrays.asList(values()));
            dataTypes.remove(PostgresDataType.ARRAY);
            return Randomly.fromList(dataTypes);
        }
    }

    public static class PostgresColumn extends AbstractTableColumn<PostgresTable, PostgresDataType> {

        private final PostgresCompoundDataType compoundType;

        public PostgresColumn(String name, PostgresDataType columnType) {
            super(name, null, columnType);
            this.compoundType = PostgresCompoundDataType.create(columnType);
        }

        public PostgresColumn(String name, PostgresCompoundDataType compoundType) {
            super(name, null, compoundType.getDataType());
            this.compoundType = compoundType;
        }

        public static PostgresColumn createDummy(String name) {
            return new PostgresColumn(name, PostgresCompoundDataType.create(PostgresDataType.INT));
        }

        public PostgresCompoundDataType getCompoundType() {
            return compoundType;
        }

    }

    public static class PostgresTables extends AbstractTables<PostgresTable, PostgresColumn> {

        public PostgresTables(List<PostgresTable> tables) {
            super(tables);
        }

        public PostgresRowValue getRandomRowValue(SQLConnection con) throws SQLException {
            String randomRow = String.format("SELECT %s FROM %s ORDER BY RANDOM() LIMIT 1", columnNamesAsString(
                    c -> c.getTable().getName() + "." + c.getName() + " AS " + c.getTable().getName() + c.getName()),
                    // columnNamesAsString(c -> "typeof(" + c.getTable().getName() + "." +
                    // c.getName() + ")")
                    tableNamesAsString());
            Map<PostgresColumn, PostgresConstant> values = new HashMap<>();
            try (Statement s = con.createStatement()) {
                ResultSet randomRowValues = s.executeQuery(randomRow);
                if (!randomRowValues.next()) {
                    throw new AssertionError("could not find random row! " + randomRow + "\n");
                }
                for (int i = 0; i < getColumns().size(); i++) {
                    PostgresColumn column = getColumns().get(i);
                    int columnIndex = randomRowValues.findColumn(column.getTable().getName() + column.getName());
                    assert columnIndex == i + 1;
                    PostgresConstant constant;
                    constant = getColumnValue(randomRowValues, columnIndex, column.getCompoundType());
                    values.put(column, constant);
                }
                assert !randomRowValues.next();
                return new PostgresRowValue(this, values);
            } catch (PSQLException e) {
                throw new IgnoreMeException();
            }

        }

    }

    public static PostgresDataType getColumnType(String typeString) {
        return getColumnCompoundType(typeString, null).getDataType();
    }

    public static PostgresCompoundDataType getColumnCompoundType(String typeString, String elementTypeString) {
        if (typeString == null) {
            throw new AssertionError("typeString must not be null");
        }
        String normalizedType = normalizeTypeName(typeString);
        if ("array".equals(normalizedType)) {
            if (elementTypeString == null) {
                throw new AssertionError(typeString);
            }
            PostgresCompoundDataType arrayType = PostgresCompoundDataType
                    .createArray(getColumnCompoundType(stripArrayUdtPrefix(elementTypeString), null));
            if (!arrayType.isSupportedArrayType()) {
                throw new IgnoreMeException();
            }
            return arrayType;
        }
        if (normalizedType.endsWith("[]")) {
            PostgresCompoundDataType arrayType = PostgresCompoundDataType
                    .createArray(getColumnCompoundType(normalizedType.substring(0, normalizedType.length() - 2), null));
            if (!arrayType.isSupportedArrayType()) {
                throw new IgnoreMeException();
            }
            return arrayType;
        }
        switch (normalizedType) {
        case "smallint":
        case "int2":
        case "integer":
        case "int4":
        case "bigint":
        case "int8":
            return PostgresCompoundDataType.create(PostgresDataType.INT);
        case "boolean":
        case "bool":
            return PostgresCompoundDataType.create(PostgresDataType.BOOLEAN);
        case "name":
        case "regclass":
            return PostgresCompoundDataType.create(PostgresDataType.TEXT);
        case "text":
            return PostgresCompoundDataType.create(PostgresDataType.TEXT);
        case "character varying":
        case "varchar":
            return PostgresCompoundDataType.create(PostgresDataType.VARCHAR);
        case "character":
        case "bpchar":
            return PostgresCompoundDataType.create(PostgresDataType.CHAR);
        case "numeric":
            return PostgresCompoundDataType.create(PostgresDataType.DECIMAL);
        case "double precision":
            return PostgresCompoundDataType.create(PostgresDataType.FLOAT);
        case "real":
            return PostgresCompoundDataType.create(PostgresDataType.REAL);
        case "int4range":
            return PostgresCompoundDataType.create(PostgresDataType.RANGE);
        case "money":
            return PostgresCompoundDataType.create(PostgresDataType.MONEY);
        case "bit":
        case "bit varying":
        case "varbit":
            return PostgresCompoundDataType.create(PostgresDataType.BIT);
        case "inet":
            return PostgresCompoundDataType.create(PostgresDataType.INET);
        case "date":
            return PostgresCompoundDataType.create(PostgresDataType.DATE);
        case "time":
        case "time without time zone":
            return PostgresCompoundDataType.create(PostgresDataType.TIME);
        case "time with time zone":
        case "timetz":
            return PostgresCompoundDataType.create(PostgresDataType.TIMETZ);
        case "timestamp":
        case "timestamp without time zone":
            return PostgresCompoundDataType.create(PostgresDataType.TIMESTAMP);
        case "timestamp with time zone":
        case "timestamptz":
            return PostgresCompoundDataType.create(PostgresDataType.TIMESTAMPTZ);
        case "interval":
            return PostgresCompoundDataType.create(PostgresDataType.INTERVAL);
        case "json":
            return PostgresCompoundDataType.create(PostgresDataType.JSON);
        case "jsonb":
            return PostgresCompoundDataType.create(PostgresDataType.JSONB);
        case "uuid":
            return PostgresCompoundDataType.create(PostgresDataType.UUID);
        case "bytea":
            return PostgresCompoundDataType.create(PostgresDataType.BYTEA);
        case "USER-DEFINED":
        case "user-defined":
            return PostgresCompoundDataType.create(PostgresDataType.ENUM);
        default:
            throw new AssertionError(typeString);
        }
    }

    private static String normalizeTypeName(String typeString) {
        String normalizedType = typeString.trim().toLowerCase();
        int parenIndex = normalizedType.indexOf('(');
        if (parenIndex != -1) {
            normalizedType = normalizedType.substring(0, parenIndex).trim();
        }
        return normalizedType;
    }

    public static boolean isSupportedArrayElementType(PostgresDataType type) {
        switch (type) {
        case INT:
        case BOOLEAN:
        case TEXT:
        case VARCHAR:
        case CHAR:
        case DATE:
        case TIME:
        case TIMESTAMP:
        case TIMESTAMPTZ:
        case INTERVAL:
            return true;
        default:
            return false;
        }
    }

    private static String stripArrayUdtPrefix(String elementTypeString) {
        String normalizedType = elementTypeString.trim().toLowerCase();
        while (normalizedType.startsWith("_")) {
            normalizedType = normalizedType.substring(1);
        }
        return normalizedType;
    }

    public static class PostgresRowValue extends AbstractRowValue<PostgresTables, PostgresColumn, PostgresConstant> {

        protected PostgresRowValue(PostgresTables tables, Map<PostgresColumn, PostgresConstant> values) {
            super(tables, values);
        }

    }

    public static class PostgresTable
            extends AbstractRelationalTable<PostgresColumn, PostgresIndex, PostgresGlobalState> {

        public enum TableType {
            STANDARD, TEMPORARY
        }

        public enum PartitionStrategy {
            NONE, RANGE, LIST, HASH
        }

        private final TableType tableType;
        private final List<PostgresStatisticsObject> statistics;
        private final List<PostgresConstraint> constraints;
        private final boolean isInsertable;
        private final boolean isPartitioned;
        private final boolean isPartition;
        private final String partitionParent;
        private final PartitionStrategy partitionStrategy;
        private final List<String> partitionKeyColumns;

        public PostgresTable(String tableName, List<PostgresColumn> columns, List<PostgresIndex> indexes,
                TableType tableType, List<PostgresStatisticsObject> statistics, boolean isView, boolean isInsertable) {
            this(tableName, columns, indexes, tableType, statistics, List.of(), isView, isInsertable, false);
        }

        public PostgresTable(String tableName, List<PostgresColumn> columns, List<PostgresIndex> indexes,
                TableType tableType, List<PostgresStatisticsObject> statistics, List<PostgresConstraint> constraints,
                boolean isView, boolean isInsertable) {
            this(tableName, columns, indexes, tableType, statistics, constraints, isView, isInsertable, false);
        }

        public PostgresTable(String tableName, List<PostgresColumn> columns, List<PostgresIndex> indexes,
                TableType tableType, List<PostgresStatisticsObject> statistics, List<PostgresConstraint> constraints,
                boolean isView, boolean isInsertable, boolean isPartitioned) {
            this(tableName, columns, indexes, tableType, statistics, constraints, isView, isInsertable, isPartitioned,
                    false, null, PartitionStrategy.NONE, List.of());
        }

        public PostgresTable(String tableName, List<PostgresColumn> columns, List<PostgresIndex> indexes,
                TableType tableType, List<PostgresStatisticsObject> statistics, List<PostgresConstraint> constraints,
                boolean isView, boolean isInsertable, boolean isPartitioned, boolean isPartition,
                String partitionParent, PartitionStrategy partitionStrategy, List<String> partitionKeyColumns) {
            super(tableName, columns, indexes, isView);
            this.statistics = statistics;
            this.constraints = constraints;
            this.isInsertable = isInsertable;
            this.tableType = tableType;
            this.isPartitioned = isPartitioned;
            this.isPartition = isPartition;
            this.partitionParent = partitionParent;
            this.partitionStrategy = partitionStrategy;
            this.partitionKeyColumns = partitionKeyColumns;
        }

        public List<PostgresStatisticsObject> getStatistics() {
            return statistics;
        }

        public TableType getTableType() {
            return tableType;
        }

        public List<PostgresConstraint> getConstraints() {
            return constraints;
        }

        public boolean isInsertable() {
            return isInsertable;
        }

        public boolean isPartitioned() {
            return isPartitioned;
        }

        public boolean isPartition() {
            return isPartition;
        }

        public String getPartitionParent() {
            return partitionParent;
        }

        public PartitionStrategy getPartitionStrategy() {
            return partitionStrategy;
        }

        public List<String> getPartitionKeyColumns() {
            return partitionKeyColumns;
        }

        public boolean hasSimplePartitionKey() {
            return partitionKeyColumns.size() == 1;
        }

    }

    public static final class PostgresConstraint {
        private final String name;
        private final boolean validatable;

        public PostgresConstraint(String name, boolean validatable) {
            this.name = name;
            this.validatable = validatable;
        }

        public String getName() {
            return name;
        }

        public boolean isValidatable() {
            return validatable;
        }
    }

    public static final class PostgresStatisticsObject {
        private final String name;

        public PostgresStatisticsObject(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static final class PostgresIndex extends TableIndex {

        private final boolean unique;
        private final boolean valid;
        private final boolean ready;
        private final boolean partial;
        private final boolean expression;

        private PostgresIndex(String indexName, boolean unique, boolean valid, boolean ready, boolean partial,
                boolean expression) {
            super(indexName);
            this.unique = unique;
            this.valid = valid;
            this.ready = ready;
            this.partial = partial;
            this.expression = expression;
        }

        public static PostgresIndex create(String indexName) {
            return new PostgresIndex(indexName, false, true, true, false, false);
        }

        public static PostgresIndex create(String indexName, boolean unique, boolean valid, boolean ready,
                boolean partial, boolean expression) {
            return new PostgresIndex(indexName, unique, valid, ready, partial, expression);
        }

        @Override
        public String getIndexName() {
            if (super.getIndexName().contentEquals("PRIMARY")) {
                return "`PRIMARY`";
            } else {
                return super.getIndexName();
            }
        }

        public boolean isUnique() {
            return unique;
        }

        public boolean isValid() {
            return valid;
        }

        public boolean isReady() {
            return ready;
        }

        public boolean isPartial() {
            return partial;
        }

        public boolean isExpression() {
            return expression;
        }

        public boolean canBeUsedForAddConstraintUsingIndex() {
            return unique && valid && ready && !partial && !expression;
        }

        public boolean canBeUsedForReplicaIdentity() {
            return unique && valid && ready && !partial && !expression;
        }

    }

    public static PostgresSchema fromConnection(SQLConnection con, String databaseName) throws SQLException {
        try {
            List<PostgresTable> databaseTables = new ArrayList<>();
            Map<String, String> partitionParents = getPartitionParents(con);
            Map<String, PartitionInfo> partitionedTables = getPartitionedTables(con);
            try (Statement s = con.createStatement()) {
                try (ResultSet rs = s.executeQuery(
                        "SELECT t.table_name, t.table_schema, t.table_type, t.is_insertable_into, c.relkind "
                                + "FROM information_schema.tables t "
                                + "JOIN pg_class c ON c.relname = t.table_name "
                                + "JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = t.table_schema "
                                + "WHERE t.table_schema='public' OR t.table_schema LIKE 'pg_temp_%' ORDER BY t.table_name;")) {
                    while (rs.next()) {
                        String tableName = rs.getString("table_name");
                        String tableTypeSchema = rs.getString("table_schema");
                        String relationType = rs.getString("table_type");
                        String relKind = rs.getString("relkind");
                        boolean isInsertable = rs.getBoolean("is_insertable_into");
                        boolean isPartitioned = "p".equals(relKind);
                        boolean isPartition = partitionParents.containsKey(tableName);
                        String partitionParent = partitionParents.get(tableName);
                        PartitionInfo partitionInfo = partitionedTables.get(tableName);
                        if (partitionInfo == null) {
                            partitionInfo = new PartitionInfo(PostgresTable.PartitionStrategy.NONE, List.of());
                        }
                        boolean isView = "VIEW".equalsIgnoreCase(relationType) || "v".equals(relKind)
                                || "m".equals(relKind);
                        PostgresTable.TableType tableType = getTableType(tableTypeSchema);
                        List<PostgresColumn> databaseColumns = getTableColumns(con, tableName);
                        List<PostgresIndex> indexes = getIndexes(con, tableName);
                        List<PostgresStatisticsObject> statistics = getStatistics(con, tableName);
                        List<PostgresConstraint> constraints = getConstraints(con, tableName);
                        PostgresTable t = new PostgresTable(tableName, databaseColumns, indexes, tableType, statistics,
                                constraints, isView, isInsertable, isPartitioned, isPartition, partitionParent,
                                partitionInfo.strategy, partitionInfo.keyColumns);
                        for (PostgresColumn c : databaseColumns) {
                            c.setTable(t);
                        }
                        databaseTables.add(t);
                    }
                }
            }
            return new PostgresSchema(databaseTables, databaseName);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new AssertionError(e);
        }
    }

    private static Map<String, String> getPartitionParents(SQLConnection con) throws SQLException {
        Map<String, String> partitionParents = new HashMap<>();
        try (Statement s = con.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT child.relname AS child_name, parent.relname AS parent_name "
                    + "FROM pg_inherits inh "
                    + "JOIN pg_class child ON child.oid = inh.inhrelid "
                    + "JOIN pg_namespace child_ns ON child_ns.oid = child.relnamespace "
                    + "JOIN pg_class parent ON parent.oid = inh.inhparent "
                    + "JOIN pg_namespace parent_ns ON parent_ns.oid = parent.relnamespace "
                    + "WHERE (child_ns.nspname='public' OR child_ns.nspname LIKE 'pg_temp_%') "
                    + "AND (parent_ns.nspname='public' OR parent_ns.nspname LIKE 'pg_temp_%') "
                    + "AND child.relispartition;")) {
                while (rs.next()) {
                    partitionParents.put(rs.getString("child_name"), rs.getString("parent_name"));
                }
            }
        }
        return partitionParents;
    }

    private static Map<String, PartitionInfo> getPartitionedTables(SQLConnection con) throws SQLException {
        Map<String, PartitionInfo> partitionedTables = new HashMap<>();
        try (Statement s = con.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT cls.relname, pt.partstrat, "
                    + "pg_get_partkeydef(cls.oid) AS partition_key "
                    + "FROM pg_partitioned_table pt "
                    + "JOIN pg_class cls ON cls.oid = pt.partrelid "
                    + "JOIN pg_namespace ns ON ns.oid = cls.relnamespace "
                    + "WHERE ns.nspname='public' OR ns.nspname LIKE 'pg_temp_%';")) {
                while (rs.next()) {
                    PostgresTable.PartitionStrategy strategy = parsePartitionStrategy(rs.getString("partstrat"));
                    List<String> keyColumns = parseSimplePartitionKeyColumns(rs.getString("partition_key"));
                    partitionedTables.put(rs.getString("relname"), new PartitionInfo(strategy, keyColumns));
                }
            }
        }
        return partitionedTables;
    }

    private static PostgresTable.PartitionStrategy parsePartitionStrategy(String partstrat) {
        if ("r".equals(partstrat)) {
            return PostgresTable.PartitionStrategy.RANGE;
        } else if ("l".equals(partstrat)) {
            return PostgresTable.PartitionStrategy.LIST;
        } else if ("h".equals(partstrat)) {
            return PostgresTable.PartitionStrategy.HASH;
        }
        return PostgresTable.PartitionStrategy.NONE;
    }

    private static List<String> parseSimplePartitionKeyColumns(String partitionKey) {
        if (partitionKey == null) {
            return List.of();
        }
        int open = partitionKey.indexOf('(');
        int close = partitionKey.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return List.of();
        }
        String inside = partitionKey.substring(open + 1, close).trim();
        if (inside.isEmpty() || inside.contains("(") || inside.contains(")")) {
            return List.of();
        }
        List<String> columns = new ArrayList<>();
        for (String column : inside.split(",")) {
            String normalized = column.trim();
            if (normalized.isEmpty() || normalized.contains(" ")) {
                return List.of();
            }
            columns.add(normalized.replace("\"", ""));
        }
        return columns;
    }

    private static final class PartitionInfo {
        private final PostgresTable.PartitionStrategy strategy;
        private final List<String> keyColumns;

        private PartitionInfo(PostgresTable.PartitionStrategy strategy, List<String> keyColumns) {
            this.strategy = strategy;
            this.keyColumns = keyColumns;
        }
    }

    protected static List<PostgresStatisticsObject> getStatistics(SQLConnection con, String tableName)
            throws SQLException {
        List<PostgresStatisticsObject> statistics = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            try (ResultSet rs = s.executeQuery(String.format(
                    "SELECT stx.stxname FROM pg_statistic_ext stx "
                            + "JOIN pg_class rel ON rel.oid = stx.stxrelid "
                            + "JOIN pg_namespace n ON n.oid = rel.relnamespace "
                            + "WHERE rel.relname = '%s' AND (n.nspname='public' OR n.nspname LIKE 'pg_temp_%%') "
                            + "ORDER BY stx.stxname;",
                    tableName))) {
                while (rs.next()) {
                    statistics.add(new PostgresStatisticsObject(rs.getString("stxname")));
                }
            }
        }
        return statistics;
    }

    protected static List<PostgresConstraint> getConstraints(SQLConnection con, String tableName) throws SQLException {
        List<PostgresConstraint> constraints = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            try (ResultSet rs = s.executeQuery(String.format(
                    "SELECT con.conname, con.contype FROM pg_constraint con "
                            + "JOIN pg_class rel ON rel.oid = con.conrelid "
                            + "JOIN pg_namespace n ON n.oid = rel.relnamespace "
                            + "WHERE rel.relname = '%s' AND (n.nspname='public' OR n.nspname LIKE 'pg_temp_%%') "
                            + "ORDER BY con.conname;",
                    tableName))) {
                while (rs.next()) {
                    String name = rs.getString("conname");
                    String type = rs.getString("contype");
                    boolean validatable = "c".equals(type) || "f".equals(type);
                    constraints.add(new PostgresConstraint(name, validatable));
                }
            }
        }
        return constraints;
    }

    protected static PostgresTable.TableType getTableType(String tableTypeStr) throws AssertionError {
        PostgresTable.TableType tableType;
        if (tableTypeStr.contentEquals("public")) {
            tableType = TableType.STANDARD;
        } else if (tableTypeStr.startsWith("pg_temp")) {
            tableType = TableType.TEMPORARY;
        } else {
            throw new AssertionError(tableTypeStr);
        }
        return tableType;
    }

    protected static List<PostgresIndex> getIndexes(SQLConnection con, String tableName) throws SQLException {
        List<PostgresIndex> indexes = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            try (ResultSet rs = s.executeQuery(String
                    .format("SELECT i.indexname, ix.indisunique, ix.indisvalid, ix.indisready, "
                            + "(ix.indpred IS NOT NULL) AS is_partial, (ix.indexprs IS NOT NULL) AS is_expression "
                            + "FROM pg_indexes i "
                            + "JOIN pg_class tbl ON tbl.relname = i.tablename "
                            + "JOIN pg_namespace n ON n.oid = tbl.relnamespace AND n.nspname = i.schemaname "
                            + "JOIN pg_class idx ON idx.relname = i.indexname AND idx.relnamespace = n.oid "
                            + "JOIN pg_index ix ON ix.indexrelid = idx.oid "
                            + "WHERE i.tablename='%s' AND (i.schemaname='public' OR i.schemaname LIKE 'pg_temp_%%') "
                            + "ORDER BY i.indexname;",
                            tableName))) {
                while (rs.next()) {
                    String indexName = rs.getString("indexname");
                    if (DBMSCommon.matchesIndexName(indexName)) {
                        indexes.add(PostgresIndex.create(indexName, rs.getBoolean("indisunique"),
                                rs.getBoolean("indisvalid"), rs.getBoolean("indisready"),
                                rs.getBoolean("is_partial"), rs.getBoolean("is_expression")));
                    }
                }
            }
        }
        return indexes;
    }

    protected static List<PostgresColumn> getTableColumns(SQLConnection con, String tableName) throws SQLException {
        List<PostgresColumn> columns = new ArrayList<>();
        try (Statement s = con.createStatement()) {
            try (ResultSet rs = s.executeQuery(
                    "select c.column_name, c.data_type, c.udt_name, "
                            + "pg_catalog.format_type(a.atttypid, a.atttypmod) as formatted_type, a.attndims "
                            + "from INFORMATION_SCHEMA.COLUMNS c "
                            + "join pg_catalog.pg_namespace n on n.nspname = c.table_schema "
                            + "join pg_catalog.pg_class cl on cl.relname = c.table_name and cl.relnamespace = n.oid "
                            + "join pg_catalog.pg_attribute a on a.attrelid = cl.oid and a.attname = c.column_name "
                            + "where c.table_name = '" + tableName + "' and a.attnum > 0 and not a.attisdropped "
                            + "ORDER BY c.column_name")) {
                while (rs.next()) {
                    String columnName = rs.getString("column_name");
                    String dataType = rs.getString("data_type");
                    String udtName = rs.getString("udt_name");
                    String formattedType = rs.getString("formatted_type");
                    int arrayDimensions = rs.getInt("attndims");

                    PostgresCompoundDataType resolved;
                    if ("USER-DEFINED".equalsIgnoreCase(dataType)) {
                        resolved = PostgresCompoundDataType.create(PostgresDataType.ENUM);
                    } else if ("ARRAY".equalsIgnoreCase(dataType) && arrayDimensions > 0) {
                        String elementTypeString = udtName;
                        if (elementTypeString == null && formattedType != null) {
                            elementTypeString = formattedType;
                        }
                        resolved = getColumnCompoundType(dataType, elementTypeString);
                    } else {
                        resolved = getColumnCompoundType(formattedType != null ? formattedType : dataType, udtName);
                    }

                    PostgresColumn c = new PostgresColumn(columnName, resolved);
                    columns.add(c);
                }
            }
        }
        return columns;
    }

    public PostgresSchema(List<PostgresTable> databaseTables, String databaseName) {
        super(databaseTables);
        this.databaseName = databaseName;
    }

    private static PostgresConstant getColumnValue(ResultSet rs, int columnIndex, PostgresCompoundDataType compoundType)
            throws SQLException {
        if (compoundType.isArray()) {
            Array array = rs.getArray(columnIndex);
            if (array == null) {
                return PostgresConstant.createNullConstant();
            }
            return PostgresConstant.createArrayConstant(array, compoundType);
        }
        if (rs.getString(columnIndex) == null) {
            return PostgresConstant.createNullConstant();
        }
        switch (compoundType.getDataType()) {
        case INT:
            return PostgresConstant.createIntConstant(rs.getLong(columnIndex));
        case BOOLEAN:
            return PostgresConstant.createBooleanConstant(rs.getBoolean(columnIndex));
        case TEXT:
        case VARCHAR:
        case CHAR:
            return PostgresConstant.createTextConstant(rs.getString(columnIndex));
        case DATE:
            return PostgresConstant.createDateConstant(rs.getString(columnIndex));
        case TIME:
            return PostgresConstant.createTimeConstant(rs.getString(columnIndex));
        case TIMETZ:
            return PostgresConstant.createTimeWithTimeZoneConstant(rs.getString(columnIndex));
        case TIMESTAMP:
            return PostgresConstant.createTimestampConstant(rs.getString(columnIndex));
        case TIMESTAMPTZ:
            return PostgresConstant.createTimestampWithTimeZoneConstant(rs.getString(columnIndex));
        case INTERVAL:
            return PostgresConstant.createIntervalConstant(rs.getString(columnIndex));
        case JSON:
        case JSONB:
        case UUID:
        case BYTEA:
        case ENUM:
            return PostgresConstant.createTextConstant(rs.getString(columnIndex));
        default:
            throw new IgnoreMeException();
        }
    }

    public PostgresTables getRandomTableNonEmptyTables() {
        return new PostgresTables(Randomly.nonEmptySubset(getDatabaseTables()));
    }

    public String getDatabaseName() {
        return databaseName;
    }

}
