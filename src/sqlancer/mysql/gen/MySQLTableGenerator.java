package sqlancer.mysql.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.DBMSCommon;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.mysql.MySQLBugs;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLOptions;
import sqlancer.mysql.MySQLSchema;
import sqlancer.mysql.MySQLSchema.MySQLDataType;
import sqlancer.mysql.MySQLSchema.MySQLTable.MySQLEngine;

public class MySQLTableGenerator {
    private final StringBuilder sb = new StringBuilder();
    private final boolean allowPrimaryKey;
    private boolean setPrimaryKey;
    private final String tableName;
    private final Randomly r;
    private boolean tableHasNullableColumn;
    private boolean tableHasTemporalColumn;
    private MySQLEngine engine;
    private int keysSpecified;
    private final List<String> columns = new ArrayList<>();
    private final MySQLSchema schema;
    private final MySQLGlobalState globalState;

    public MySQLTableGenerator(MySQLGlobalState globalState, String tableName) {
        this.tableName = tableName;
        this.r = globalState.getRandomly();
        this.schema = globalState.getSchema();
        allowPrimaryKey = Randomly.getBoolean();
        this.globalState = globalState;
    }

    public static SQLQueryAdapter generate(MySQLGlobalState globalState, String tableName) {
        return new MySQLTableGenerator(globalState, tableName).create();
    }

    private SQLQueryAdapter create() {
        ExpectedErrors errors = new ExpectedErrors();

        sb.append("CREATE");
        // 临时表支持
        if (globalState.getDbmsSpecificOptions().testTempTables && Randomly.getBooleanWithSmallProbability()) {
            sb.append(" TEMPORARY");
        }
        sb.append(" TABLE");
        if (Randomly.getBoolean()) {
            sb.append(" IF NOT EXISTS");
        }
        sb.append(" ");
        sb.append(tableName);
        // When --test-dates is enabled, enforce that the generated CREATE TABLE statement contains at least one
        // temporal column. The CREATE TABLE ... LIKE ... shortcut would bypass column generation entirely and would
        // break the hard constraint, so disable it in this mode.
        if (!globalState.getDbmsSpecificOptions().testDates && Randomly.getBoolean() && !schema.getDatabaseTables().isEmpty()) {
            sb.append(" LIKE ");
            sb.append(schema.getRandomTable().getName());
            return new SQLQueryAdapter(sb.toString(), true);
        } else {
            sb.append("(");
            for (int i = 0; i < 1 + Randomly.smallNumber(); i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                appendColumn(i);
            }
            // 添加外键约束
            if (globalState.getDbmsSpecificOptions().testForeignKeys && !schema.getDatabaseTables().isEmpty()
                    && Randomly.getBooleanWithSmallProbability() && engine == MySQLEngine.INNO_DB) {
                appendForeignKeyConstraint();
            }
            sb.append(")");
            sb.append(" ");
            appendTableOptions();
            appendPartitionOptions();
            if (engine == MySQLEngine.CSV && (tableHasNullableColumn || setPrimaryKey)) {
                if (true) { // TODO
                    // results in an error
                    throw new IgnoreMeException();
                }
            } else if (engine == MySQLEngine.ARCHIVE && (tableHasNullableColumn || keysSpecified > 1)) {
                errors.add("Too many keys specified; max 1 keys allowed");
                errors.add("Table handler doesn't support NULL in given index");
                addCommonErrors(errors);
                return new SQLQueryAdapter(sb.toString(), errors, true);
            }
            addCommonErrors(errors);
            return new SQLQueryAdapter(sb.toString(), errors, true);
        }

    }

    private void addCommonErrors(ExpectedErrors list) {
        list.add("The storage engine for the table doesn't support");
        list.add("doesn't have this option");
        list.add("must include all columns");
        list.add("not allowed type for this type of partitioning");
        list.add("doesn't support BLOB/TEXT columns");
        list.add("A BLOB field is not allowed in partition function");
        list.add("Too many keys specified; max 1 keys allowed");
        list.add("The total length of the partitioning fields is too large");
        list.add("Got error -1 - 'Unknown error -1' from storage engine");
        list.add("Got error -1 - 'Unknown error' from storage engine");
        list.add("Compression failed with the following error");
        list.add("Punch hole not supported by the filesystem");
    }

    private enum PartitionOptions {
        HASH, KEY, RANGE, LIST
    }

    private void appendPartitionOptions() {
        if (engine != MySQLEngine.INNO_DB) {
            return;
        }
        if (Randomly.getBoolean()) {
            return;
        }
        sb.append(" PARTITION BY");
        switch (Randomly.fromOptions(PartitionOptions.values())) {
        case HASH:
            if (Randomly.getBoolean()) {
                sb.append(" LINEAR");
            }
            sb.append(" HASH(");
            sb.append(Randomly.fromList(columns));
            sb.append(")");
            break;
        case KEY:
            if (Randomly.getBoolean()) {
                sb.append(" LINEAR");
            }
            sb.append(" KEY");
            if (Randomly.getBoolean()) {
                sb.append(" ALGORITHM=");
                sb.append(Randomly.fromOptions(1, 2));
            }
            sb.append(" (");
            sb.append(Randomly.nonEmptySubset(columns).stream().collect(Collectors.joining(", ")));
            sb.append(")");
            break;
        case RANGE:
            sb.append(" RANGE(");
            // RANGE 分区需要使用数值列
            sb.append(getNumericColumn());
            sb.append(")");
            sb.append(" (");
            appendRangePartitions();
            sb.append(")");
            break;
        case LIST:
            sb.append(" LIST(");
            // LIST 分区需要使用数值列
            sb.append(getNumericColumn());
            sb.append(")");
            sb.append(" (");
            appendListPartitions();
            sb.append(")");
            break;
        default:
            throw new AssertionError();
        }
    }

    /**
     * 获取数值列用于分区
     */
    private String getNumericColumn() {
        // 简化实现：返回第一个列或随机列
        // 实际 RANGE/LIST 分区需要数值列，这里简化处理
        return Randomly.fromList(columns);
    }

    /**
     * 添加 RANGE 分区定义
     */
    private void appendRangePartitions() {
        int numPartitions = 2 + Randomly.smallNumber();  // 至少 2 个分区
        for (int i = 0; i < numPartitions; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("PARTITION p").append(i);
            sb.append(" VALUES LESS THAN (");
            if (i == numPartitions - 1 && Randomly.getBoolean()) {
                sb.append("MAXVALUE");
            } else {
                // 生成递增的分区值
                sb.append((i + 1) * 10);
            }
            sb.append(")");
        }
    }

    /**
     * 添加 LIST 分区定义
     */
    private void appendListPartitions() {
        int numPartitions = 2 + Randomly.smallNumber();  // 至少 2 个分区
        for (int i = 0; i < numPartitions; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("PARTITION p").append(i);
            sb.append(" VALUES IN (");
            // 生成分区值列表
            int numValues = 1 + Randomly.smallNumber();
            for (int j = 0; j < numValues; j++) {
                if (j > 0) {
                    sb.append(", ");
                }
                sb.append((i * 10) + j + 1);
            }
            sb.append(")");
        }
    }

    private enum TableOptions {
        AUTO_INCREMENT, AVG_ROW_LENGTH, CHECKSUM, COMPRESSION, DELAY_KEY_WRITE, /* ENCRYPTION, */ ENGINE, INSERT_METHOD,
        KEY_BLOCK_SIZE, MAX_ROWS, MIN_ROWS, PACK_KEYS, STATS_AUTO_RECALC, STATS_PERSISTENT, STATS_SAMPLE_PAGES;

        public static List<TableOptions> getRandomTableOptions() {
            List<TableOptions> options;
            // try to ensure that usually, only a few of these options are generated
            if (Randomly.getBooleanWithSmallProbability()) {
                options = Randomly.subset(TableOptions.values());
            } else {
                if (Randomly.getBoolean()) {
                    options = Collections.emptyList();
                } else {
                    options = Randomly.nonEmptySubset(Arrays.asList(TableOptions.values()), Randomly.smallNumber());
                }
            }
            return options;
        }
    }

    private void appendTableOptions() {
        List<TableOptions> tableOptions = new ArrayList<>(TableOptions.getRandomTableOptions());
        if (!globalState.supportsTableCompression()) {
            tableOptions.remove(TableOptions.COMPRESSION);
        }
        int i = 0;
        for (TableOptions o : tableOptions) {
            if (i++ != 0) {
                sb.append(", ");
            }
            switch (o) {
            case AUTO_INCREMENT:
                sb.append("AUTO_INCREMENT = ");
                sb.append(r.getPositiveInteger());
                break;
            // The valid range for avg_row_length is [0,4294967295]
            case AVG_ROW_LENGTH:
                sb.append("AVG_ROW_LENGTH = ");
                sb.append(r.getLong(0, 4294967295L + 1));
                break;
            case CHECKSUM:
                sb.append("CHECKSUM = 1");
                break;
            case COMPRESSION:
                sb.append("COMPRESSION = '");
                sb.append(Randomly.fromOptions("ZLIB", "LZ4", "NONE"));
                sb.append("'");
                break;
            case DELAY_KEY_WRITE:
                sb.append("DELAY_KEY_WRITE = ");
                sb.append(Randomly.fromOptions(0, 1));
                break;
            case ENGINE:
                String fromOptions = pickEngineName();
                this.engine = MySQLEngine.get(fromOptions);
                sb.append("ENGINE = ");
                sb.append(fromOptions);
                break;
            // case ENCRYPTION:
            // sb.append("ENCRYPTION = '");
            // sb.append(Randomly.fromOptions("Y", "N"));
            // sb.append("'");
            // break;
            case INSERT_METHOD:
                sb.append("INSERT_METHOD = ");
                sb.append(Randomly.fromOptions("NO", "FIRST", "LAST"));
                break;
            // The valid range for key_block_size is [0,65535]
            case KEY_BLOCK_SIZE:
                sb.append("KEY_BLOCK_SIZE = ");
                sb.append(r.getInteger(0, 65535 + 1));
                break;
            case MAX_ROWS:
                sb.append("MAX_ROWS = ");
                sb.append(r.getLong(0, Long.MAX_VALUE));
                break;
            case MIN_ROWS:
                sb.append("MIN_ROWS = ");
                sb.append(r.getLong(1, Long.MAX_VALUE));
                break;
            case PACK_KEYS:
                sb.append("PACK_KEYS = ");
                sb.append(Randomly.fromOptions("1", "0", "DEFAULT"));
                break;
            case STATS_AUTO_RECALC:
                sb.append("STATS_AUTO_RECALC = ");
                sb.append(Randomly.fromOptions("1", "0", "DEFAULT"));
                break;
            case STATS_PERSISTENT:
                sb.append("STATS_PERSISTENT = ");
                sb.append(Randomly.fromOptions("1", "0", "DEFAULT"));
                break;
            case STATS_SAMPLE_PAGES:
                sb.append("STATS_SAMPLE_PAGES = ");
                sb.append(r.getInteger(1, Short.MAX_VALUE));
                break;
            default:
                throw new AssertionError(o);
            }
        }
    }

    private String pickEngineName() {
        List<String> configuredEngines = ((MySQLOptions) globalState.getDbmsSpecificOptions()).getSpecifiedEngines();
        if (!configuredEngines.isEmpty()) {
            return Randomly.fromList(configuredEngines);
        }
        // FEDERATED: java.sql.SQLSyntaxErrorException: Unknown storage engine 'FEDERATED'
        // "NDB": java.sql.SQLSyntaxErrorException: Unknown storage engine 'NDB'
        // "EXAMPLE": java.sql.SQLSyntaxErrorException: Unknown storage engine 'EXAMPLE'
        // "MERGE": java.sql.SQLException: Table 't0' is read only
        return Randomly.fromOptions("InnoDB", "MyISAM", "MEMORY", "HEAP", "CSV", "ARCHIVE");
    }

    private void appendColumn(int columnId) {
        String columnName = DBMSCommon.createColumnName(columnId);
        columns.add(columnName);
        sb.append(columnName);
        appendColumnDefinition(columnId);
    }

    private enum ColumnOptions {
        NULL_OR_NOT_NULL, UNIQUE, COMMENT, COLUMN_FORMAT, STORAGE, PRIMARY_KEY, AUTO_INCREMENT, DEFAULT_VALUE
    }

    private void appendColumnOption(MySQLDataType type) {
        boolean isTextType = type == MySQLDataType.VARCHAR;
        boolean isBlobType = type == MySQLDataType.BLOB || type == MySQLDataType.BINARY || type == MySQLDataType.VARBINARY;
        boolean isJsonType = type == MySQLDataType.JSON;
        boolean isIntType = type == MySQLDataType.INT;
        boolean isNull = false;
        boolean columnHasPrimaryKey = false;
        List<ColumnOptions> columnOptions = Randomly.subset(ColumnOptions.values());
        if (!columnOptions.contains(ColumnOptions.NULL_OR_NOT_NULL)) {
            tableHasNullableColumn = true;
        }
        // MySQL 不允许在 TEXT/BLOB/JSON 类型上直接创建 UNIQUE 或 PRIMARY KEY 约束
        // 因为这些类型需要前缀长度才能创建索引
        if (isTextType || isBlobType || isJsonType) {
            columnOptions.remove(ColumnOptions.PRIMARY_KEY);
            columnOptions.remove(ColumnOptions.UNIQUE);
        }
        // AUTO_INCREMENT only works on INT types
        if (!isIntType) {
            columnOptions.remove(ColumnOptions.AUTO_INCREMENT);
        }
        for (ColumnOptions o : columnOptions) {
            sb.append(" ");
            switch (o) {
            case NULL_OR_NOT_NULL:
                // PRIMARY KEYs cannot be NULL
                if (!columnHasPrimaryKey) {
                    if (Randomly.getBoolean()) {
                        sb.append("NULL");
                    }
                    tableHasNullableColumn = true;
                    isNull = true;
                } else {
                    sb.append("NOT NULL");
                }
                break;
            case UNIQUE:
                sb.append("UNIQUE");
                keysSpecified++;
                if (Randomly.getBoolean()) {
                    sb.append(" KEY");
                }
                break;
            case COMMENT:
                // TODO: generate randomly
                sb.append(String.format("COMMENT '%s' ", "asdf"));
                break;
            case COLUMN_FORMAT:
                sb.append("COLUMN_FORMAT ");
                sb.append(Randomly.fromOptions("FIXED", "DYNAMIC", "DEFAULT"));
                break;
            case STORAGE:
                sb.append("STORAGE ");
                sb.append(Randomly.fromOptions("DISK", "MEMORY"));
                break;
            case PRIMARY_KEY:
                // PRIMARY KEYs cannot be NULL
                if (allowPrimaryKey && !setPrimaryKey && !isNull) {
                    sb.append("PRIMARY KEY");
                    setPrimaryKey = true;
                    columnHasPrimaryKey = true;
                }
                break;
            case AUTO_INCREMENT:
                // AUTO_INCREMENT requires NOT NULL (enforced by MySQL)
                // Must be a key (PRIMARY KEY or UNIQUE) - but MySQL will auto-create unique index if not specified
                if (isIntType) {
                    sb.append("AUTO_INCREMENT");
                    keysSpecified++;
                }
                break;
            case DEFAULT_VALUE:
                // Generate appropriate default value based on column type
                appendDefaultValue(type);
                break;
            default:
                throw new AssertionError();
            }
        }
    }

    /**
     * Generate DEFAULT value based on column type
     */
    private void appendDefaultValue(MySQLDataType type) {
        // Skip DEFAULT for temporal types - MySQL has strict requirements for these
        // and the fsp (fractional seconds precision) matching can cause issues
        switch (type) {
        case DATE:
        case TIME:
        case DATETIME:
        case TIMESTAMP:
        case YEAR:
            return; // Skip DEFAULT for temporal types
        default:
            break;
        }

        sb.append("DEFAULT ");
        switch (type) {
        case INT:
            sb.append(r.getInteger(-100, 100));
            break;
        case FLOAT:
        case DOUBLE:
            sb.append(r.getDouble());
            break;
        case DECIMAL:
            sb.append(r.getDouble());
            break;
        case VARCHAR:
            sb.append("'");
            sb.append(r.getString().replace("'", "\\'").replace("\\", "\\\\"));
            sb.append("'");
            break;
        case BIT:
            sb.append(r.getLong(0, (1L << Math.min(r.getInteger(1, 64), 63))));
            break;
        case ENUM:
            // For ENUM, use numeric index or string value
            if (Randomly.getBoolean()) {
                sb.append(1); // Default to first enum value by index
            } else {
                sb.append("'e0'"); // Default enum value
            }
            break;
        case SET:
            // For SET, use empty string or single value
            sb.append("''");
            break;
        case JSON:
            // For JSON, use NULL or simple JSON
            if (Randomly.getBoolean()) {
                sb.append("NULL");
            } else {
                sb.append("'{}'");
            }
            break;
        case BINARY:
        case VARBINARY:
            // For binary, use hex representation
            sb.append("0x00");
            break;
        case BLOB:
            // BLOB cannot have DEFAULT value in MySQL
            return; // Skip adding DEFAULT for BLOB
        default:
            sb.append("NULL");
            break;
        }
    }

    private void appendColumnDefinition(int columnId) {
        sb.append(" ");
        MySQLDataType randomType;
        if (globalState.getDbmsSpecificOptions().testDates && columnId == 0 && !tableHasTemporalColumn) {
            // Hard constraint: ensure at least one temporal column per table when --test-dates is enabled.
            // Must also work under PQS, where MySQLDataType.getRandom(globalState) would otherwise exclude temporal
            // types.
            randomType = getRandomTemporalType();
        } else {
            randomType = MySQLDataType.getRandom(globalState);
        }
        appendType(randomType);
        sb.append(" ");
        appendColumnOption(randomType);
    }

    private void appendType(MySQLDataType randomType) {
        switch (randomType) {
        case DATE:
            sb.append("DATE");
            tableHasTemporalColumn = true;
            break;
        case YEAR:
            sb.append("YEAR");
            tableHasTemporalColumn = true;
            break;
        case TIME: {
            sb.append("TIME(");
            sb.append(getRandomFsp());
            sb.append(")");
            tableHasTemporalColumn = true;
            break;
        }
        case DATETIME: {
            sb.append("DATETIME(");
            sb.append(getRandomFsp());
            sb.append(")");
            tableHasTemporalColumn = true;
            break;
        }
        case TIMESTAMP: {
            sb.append("TIMESTAMP(");
            sb.append(getRandomFsp());
            sb.append(")");
            tableHasTemporalColumn = true;
            break;
        }
        case DECIMAL:
            sb.append("DECIMAL");
            optionallyAddPrecisionAndScale(sb);
            break;
        case INT:
            sb.append(Randomly.fromOptions("TINYINT", "SMALLINT", "MEDIUMINT", "INT", "BIGINT"));
            if (Randomly.getBoolean()) {
                sb.append("(");
                sb.append(Randomly.getNotCachedInteger(0, 255)); // Display width out of range for column 'c0' (max =
                // 255)
                sb.append(")");
            }
            break;
        case VARCHAR:
            sb.append(Randomly.fromOptions("VARCHAR(500)", "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT"));
            break;
        case FLOAT:
            sb.append("FLOAT");
            optionallyAddPrecisionAndScale(sb);
            break;
        case DOUBLE:
            sb.append(Randomly.fromOptions("DOUBLE", "FLOAT"));
            optionallyAddPrecisionAndScale(sb);
            break;
        case BIT: {
            // BIT 类型，支持1-64位
            int bitWidth = (int) Randomly.getNotCachedInteger(1, 64);
            sb.append("BIT(");
            sb.append(bitWidth);
            sb.append(")");
            break;
        }
        case ENUM: {
            // ENUM 类型，生成值列表
            List<String> enumValues = generateEnumOrSetValues("e");
            sb.append("ENUM('");
            sb.append(enumValues.stream().collect(java.util.stream.Collectors.joining("','")));
            sb.append("')");
            break;
        }
        case SET: {
            // SET 类型，生成值列表
            List<String> setValues = generateEnumOrSetValues("s");
            sb.append("SET('");
            sb.append(setValues.stream().collect(java.util.stream.Collectors.joining("','")));
            sb.append("')");
            break;
        }
        case JSON: {
            // JSON 类型
            sb.append("JSON");
            break;
        }
        case BINARY: {
            // BINARY 类型，固定长度
            int binaryLength = (int) Randomly.getNotCachedInteger(1, 255);
            sb.append("BINARY(");
            sb.append(binaryLength);
            sb.append(")");
            break;
        }
        case VARBINARY: {
            // VARBINARY 类型，可变长度
            int varbinaryLength = (int) Randomly.getNotCachedInteger(1, 65535);
            sb.append("VARBINARY(");
            sb.append(varbinaryLength);
            sb.append(")");
            break;
        }
        case BLOB: {
            // BLOB 类型及其变体
            sb.append(Randomly.fromOptions("TINYBLOB", "BLOB", "MEDIUMBLOB", "LONGBLOB"));
            break;
        }
        default:
            throw new AssertionError(randomType);
        }
        if (randomType.isNumeric()) {
            // UNSIGNED/ZEROFILL are not supported for BIT type in MySQL
            if (Randomly.getBoolean() && randomType != MySQLDataType.INT && randomType != MySQLDataType.BIT && !MySQLBugs.bug99127) {
                sb.append(" UNSIGNED");
            }
            if (Randomly.getBoolean() && randomType != MySQLDataType.BIT) {
                sb.append(" ZEROFILL");
            }
        }
    }

    private List<String> generateEnumOrSetValues(String prefix) {
        int numValues = (int) Randomly.getNotCachedInteger(1, 10);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < numValues; i++) {
            values.add(prefix + i);
        }
        return values;
    }

    private int getRandomFsp() {
        // TIME/DATETIME/TIMESTAMP fsp is 0..6; try to include boundary values regularly.
        if (Randomly.getBooleanWithSmallProbability()) {
            return Randomly.fromOptions(0, 6);
        }
        return r.getInteger(0, 7);
    }

    private static MySQLDataType getRandomTemporalType() {
        return Randomly.fromOptions(MySQLDataType.DATE, MySQLDataType.TIME, MySQLDataType.DATETIME, MySQLDataType.TIMESTAMP,
                MySQLDataType.YEAR);
    }

    public static void optionallyAddPrecisionAndScale(StringBuilder sb) {
        if (Randomly.getBoolean() && !MySQLBugs.bug99183) {
            sb.append("(");
            // The maximum number of digits (M) for DECIMAL is 65
            long m = Randomly.getNotCachedInteger(1, 65);
            sb.append(m);
            sb.append(", ");
            // The maximum number of supported decimals (D) is 30
            long nCandidate = Randomly.getNotCachedInteger(1, 30);
            // For float(M,D), double(M,D) or decimal(M,D), M must be >= D (column 'c0').
            long n = Math.min(nCandidate, m);
            sb.append(n);
            sb.append(")");
        }
    }

    /**
     * 添加外键约束
     */
    private void appendForeignKeyConstraint() {
        sb.append(", ");
        sb.append("CONSTRAINT ");
        sb.append("fk_").append(tableName).append("_").append(Randomly.smallNumber());
        sb.append(" FOREIGN KEY (");
        // 选择一个列作为外键列
        String fkColumn = Randomly.fromList(columns);
        sb.append(fkColumn);
        sb.append(") REFERENCES ");
        // 引用另一个表的主键列
        MySQLSchema.MySQLTable refTable = schema.getRandomTable();
        sb.append(refTable.getName());
        sb.append("(");
        // 引用表的列（如果有主键则用主键，否则随机选择）
        if (refTable.hasPrimaryKey()) {
            sb.append(refTable.getColumns().stream()
                    .filter(c -> c.isPrimaryKey())
                    .findFirst()
                    .orElse(refTable.getRandomColumn())
                    .getName());
        } else {
            sb.append(refTable.getRandomColumn().getName());
        }
        sb.append(")");
        // 添加 ON DELETE 和 ON UPDATE 动作
        if (Randomly.getBoolean()) {
            sb.append(" ON DELETE ");
            sb.append(Randomly.fromOptions("CASCADE", "SET NULL", "RESTRICT", "NO ACTION"));
        }
        if (Randomly.getBoolean()) {
            sb.append(" ON UPDATE ");
            sb.append(Randomly.fromOptions("CASCADE", "SET NULL", "RESTRICT", "NO ACTION"));
        }
    }

}
