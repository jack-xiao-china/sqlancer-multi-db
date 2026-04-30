package sqlancer.mysql.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.mysql.MySQLBugs;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLSchema;
import sqlancer.mysql.MySQLSchema.MySQLTable;

public class MySQLAlterTable {

    private final MySQLGlobalState globalState;
    private final MySQLSchema schema;
    private final StringBuilder sb = new StringBuilder();
    boolean couldAffectSchema;
    private List<Action> selectedActions;

    public MySQLAlterTable(MySQLGlobalState globalState) {
        this.globalState = globalState;
        this.schema = globalState.getSchema();
    }

    public static SQLQueryAdapter create(MySQLGlobalState globalState) {
        return new MySQLAlterTable(globalState).create();
    }

    private enum Action {
        ALGORITHM, //
        CHECKSUM, //
        COMPRESSION, //
        DISABLE_ENABLE_KEYS("Data truncated for functional index"), /* ignore due to http://bugs.mysql.com/?id=96295 */
        DROP_COLUMN("Cannot drop column", "ALGORITHM=INPLACE is not supported.", "ALGORITHM=INSTANT is not supported.",
                "Duplicate entry", "has a partitioning function dependency and cannot be dropped or renamed.",
                "A primary key index cannot be invisible" /*
                                                           * this error should not occur, see
                                                           * https://bugs.mysql.com/bug.php?id=95897
                                                           */,
                "Field in list of fields for partition function not found in table", "in 'partition function'",
                "has a functional index dependency and cannot be dropped or renamed."),
        FORCE, //
        // ORDER_BY is supported, see below
        DELAY_KEY_WRITE, //
        INSERT_METHOD, //
        ROW_FORMAT, //
        STATS_AUTO_RECALC, //
        STATS_PERSISTENT, //
        PACK_KEYS, RENAME("doesn't exist", "already exists"), /* WITH_WITHOUT_VALIDATION , */
        DROP_PRIMARY_KEY(
                "ALGORITHM=INSTANT is not supported. Reason: Dropping a primary key is not allowed without also adding a new primary key. Try ALGORITHM=COPY/INPLACE."),
        MODIFY_COLUMN("Cannot convert", "Data truncated", "ALGORITHM=INSTANT is not supported",
                "ALGORITHM=INPLACE is not supported", "Incorrect column specifier"),
        CHANGE_COLUMN("Cannot convert", "Data truncated", "ALGORITHM=INSTANT is not supported",
                "ALGORITHM=INPLACE is not supported", "Unknown column", "Incorrect column specifier",
                "has a functional index dependency and cannot be dropped or renamed."),
        ADD_COLUMN("Duplicate column name", "ALGORITHM=INSTANT is not supported", "ALGORITHM=INPLACE is not supported");

        private String[] potentialErrors;

        Action(String... couldCauseErrors) {
            this.potentialErrors = couldCauseErrors.clone();
        }

    }

    private SQLQueryAdapter create() {
        ExpectedErrors errors = ExpectedErrors.from("does not support the create option", "doesn't have this option",
                "is not supported for this operation", "Data truncation", "Specified key was too long");
        errors.add("Data truncated for functional index ");
        errors.add("Compression failed with the following error");
        errors.add("Punch hole not supported by the filesystem");
        errors.add("For float(M,D), double(M,D) or decimal(M,D), M must be >= D");
        sb.append("ALTER TABLE ");
        MySQLTable table = schema.getRandomTableNoViewOrBailout();
        sb.append(table.getName());
        sb.append(" ");
        List<Action> list = new ArrayList<>(Arrays.asList(Action.values()));
        if (!table.hasPrimaryKey() || MySQLBugs.bug95894) {
            list.remove(Action.DROP_PRIMARY_KEY);
        }
        if (table.getColumns().size() == 1) {
            list.remove(Action.DROP_COLUMN);
        }
        if (!globalState.supportsTableCompression()) {
            list.remove(Action.COMPRESSION);
        }
        selectedActions = Randomly.subset(list);
        int i = 0;
        for (Action a : selectedActions) {
            if (i++ != 0) {
                sb.append(", ");
            }
            switch (a) {
            case ALGORITHM:
                sb.append("ALGORITHM ");
                sb.append(Randomly.fromOptions("INSTANT", "INPLACE", "COPY", "DEFAULT"));
                break;
            case CHECKSUM:
                sb.append("CHECKSUM ");
                sb.append(Randomly.fromOptions(0, 1));
                break;
            case COMPRESSION:
                sb.append("COMPRESSION ");
                sb.append("'");
                sb.append(Randomly.fromOptions("ZLIB", "LZ4", "NONE"));
                sb.append("'");
                break;
            case DELAY_KEY_WRITE:
                sb.append("DELAY_KEY_WRITE ");
                sb.append(Randomly.fromOptions(0, 1));
                break;
            case DROP_COLUMN:
                sb.append("DROP ");
                if (Randomly.getBoolean()) {
                    sb.append("COLUMN ");
                }
                sb.append(table.getRandomColumn().getName());
                couldAffectSchema = true;
                break;
            case DISABLE_ENABLE_KEYS:
                sb.append(Randomly.fromOptions("DISABLE", "ENABLE"));
                sb.append(" KEYS");
                break;
            case DROP_PRIMARY_KEY:
                assert table.hasPrimaryKey();
                sb.append("DROP PRIMARY KEY");
                couldAffectSchema = true;
                break;
            case FORCE:
                sb.append("FORCE");
                break;
            case INSERT_METHOD:
                sb.append("INSERT_METHOD ");
                sb.append(Randomly.fromOptions("NO", "FIRST", "LAST"));
                break;
            case ROW_FORMAT:
                sb.append("ROW_FORMAT ");
                sb.append(Randomly.fromOptions("DEFAULT", "DYNAMIC", "FIXED", "COMPRESSED", "REDUNDANT", "COMPACT"));
                break;
            case STATS_AUTO_RECALC:
                sb.append("STATS_AUTO_RECALC ");
                sb.append(Randomly.fromOptions("0", "1", "DEFAULT"));
                break;
            case STATS_PERSISTENT:
                sb.append("STATS_PERSISTENT ");
                sb.append(Randomly.fromOptions("0", "1", "DEFAULT"));
                break;
            case PACK_KEYS:
                sb.append("PACK_KEYS ");
                sb.append(Randomly.fromOptions("0", "1", "DEFAULT"));
                break;
            case MODIFY_COLUMN:
                sb.append("MODIFY ");
                if (Randomly.getBoolean()) {
                    sb.append("COLUMN ");
                }
                MySQLSchema.MySQLColumn modifyCol = table.getRandomColumn();
                sb.append(modifyCol.getName());
                sb.append(" ");
                appendColumnDefinition(modifyCol.isPrimaryKey());
                couldAffectSchema = true;
                break;
            case CHANGE_COLUMN:
                sb.append("CHANGE ");
                if (Randomly.getBoolean()) {
                    sb.append("COLUMN ");
                }
                MySQLSchema.MySQLColumn oldCol = table.getRandomColumn();
                sb.append(oldCol.getName());
                sb.append(" ");
                // Generate new column name
                sb.append("c").append(Randomly.smallNumber());
                sb.append(" ");
                appendColumnDefinition(oldCol.isPrimaryKey());
                couldAffectSchema = true;
                break;
            case ADD_COLUMN:
                sb.append("ADD ");
                if (Randomly.getBoolean()) {
                    sb.append("COLUMN ");
                }
                sb.append("c").append(Randomly.smallNumber());
                sb.append(" ");
                appendColumnDefinition(false); // New column is not a primary key
                couldAffectSchema = true;
                break;
            case RENAME:
                sb.append("RENAME ");
                if (Randomly.getBoolean()) {
                    sb.append(Randomly.fromOptions("TO", "AS"));
                    sb.append(" ");
                }
                sb.append("t");
                sb.append(Randomly.smallNumber());
                couldAffectSchema = true;
                break;
            default:
                throw new AssertionError(a);
            }
        }
        if (Randomly.getBooleanWithSmallProbability()) {
            if (i != 0) {
                sb.append(", ");
            }
            // should be given as last option
            sb.append(" ORDER BY ");
            sb.append(table.getRandomNonEmptyColumnSubset().stream().map(c -> c.getName())
                    .collect(Collectors.joining(", ")));
        }
        for (Action a : selectedActions) {
            for (String error : a.potentialErrors) {
                errors.add(error);
            }
        }
        return new SQLQueryAdapter(sb.toString(), errors, couldAffectSchema);
    }

    /**
     * Append a column definition (type and options) for MODIFY/CHANGE/ADD COLUMN operations.
     * @param isPrimaryKey whether this column is part of a primary key (affects NULL option)
     */
    private void appendColumnDefinition(boolean isPrimaryKey) {
        Randomly r = globalState.getRandomly();
        // Generate a random column type
        MySQLSchema.MySQLDataType type = MySQLSchema.MySQLDataType.getRandom(globalState);
        appendColumnType(type, r);
        // Optionally add column options
        appendAlterColumnOptions(type, r, isPrimaryKey);
    }

    /**
     * Append column type definition
     */
    private void appendColumnType(MySQLSchema.MySQLDataType type, Randomly r) {
        switch (type) {
        case INT:
            sb.append(Randomly.fromOptions("TINYINT", "SMALLINT", "MEDIUMINT", "INT", "BIGINT"));
            if (Randomly.getBoolean()) {
                sb.append("(");
                sb.append(Randomly.getNotCachedInteger(0, 255));
                sb.append(")");
            }
            break;
        case VARCHAR:
            sb.append(Randomly.fromOptions("VARCHAR(500)", "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT"));
            break;
        case FLOAT:
            sb.append("FLOAT");
            break;
        case DOUBLE:
            sb.append(Randomly.fromOptions("DOUBLE", "FLOAT"));
            break;
        case DECIMAL:
            sb.append("DECIMAL");
            optionallyAddPrecisionAndScale(sb, r);
            break;
        case DATE:
            sb.append("DATE");
            break;
        case TIME:
            sb.append("TIME(");
            sb.append(r.getInteger(0, 6));
            sb.append(")");
            break;
        case DATETIME:
            sb.append("DATETIME(");
            sb.append(r.getInteger(0, 6));
            sb.append(")");
            break;
        case TIMESTAMP:
            sb.append("TIMESTAMP(");
            sb.append(r.getInteger(0, 6));
            sb.append(")");
            break;
        case YEAR:
            sb.append("YEAR");
            break;
        case BIT:
            sb.append("BIT(");
            sb.append(r.getInteger(1, 64));
            sb.append(")");
            break;
        case ENUM:
            sb.append("ENUM('e0','e1','e2')");
            break;
        case SET:
            sb.append("SET('s0','s1','s2')");
            break;
        case JSON:
            sb.append("JSON");
            break;
        case BINARY:
            sb.append("BINARY(");
            sb.append(r.getInteger(1, 255));
            sb.append(")");
            break;
        case VARBINARY:
            sb.append("VARBINARY(");
            sb.append(r.getInteger(1, 500));
            sb.append(")");
            break;
        case BLOB:
            sb.append(Randomly.fromOptions("TINYBLOB", "BLOB", "MEDIUMBLOB", "LONGBLOB"));
            break;
        default:
            throw new AssertionError(type);
        }
        // Add UNSIGNED/ZEROFILL for numeric types (BIT does not support these)
        if (type.isNumeric() && type != MySQLSchema.MySQLDataType.INT && type != MySQLSchema.MySQLDataType.BIT) {
            if (Randomly.getBoolean()) {
                sb.append(" UNSIGNED");
            }
            if (Randomly.getBoolean()) {
                sb.append(" ZEROFILL");
            }
        }
    }

    /**
     * Append column options for ALTER TABLE column operations
     * @param isPrimaryKey whether this column is part of a primary key (must be NOT NULL if true)
     */
    private void appendAlterColumnOptions(MySQLSchema.MySQLDataType type, Randomly r, boolean isPrimaryKey) {
        // Add NULL/NOT NULL - PRIMARY KEY columns must be NOT NULL
        if (isPrimaryKey) {
            // Primary key columns must be NOT NULL
            sb.append(" NOT NULL");
        } else if (Randomly.getBoolean()) {
            sb.append(" ");
            sb.append(Randomly.fromOptions("NULL", "NOT NULL"));
        }
        // Add DEFAULT value (randomly)
        if (Randomly.getBooleanWithSmallProbability() && type != MySQLSchema.MySQLDataType.BLOB) {
            sb.append(" DEFAULT ");
            switch (type) {
            case INT:
                sb.append(r.getInteger(-100, 100));
                break;
            case FLOAT:
            case DOUBLE:
            case DECIMAL:
                sb.append(r.getDouble());
                break;
            case VARCHAR:
                sb.append("'");
                sb.append(r.getString().replace("'", "\\'"));
                sb.append("'");
                break;
            case DATE:
                sb.append("'2024-01-01'");
                break;
            case TIME:
                sb.append("'12:00:00'");
                break;
            case DATETIME:
                sb.append("'2024-01-01 12:00:00'");
                break;
            case TIMESTAMP:
                sb.append(Randomly.fromOptions("CURRENT_TIMESTAMP", "'2024-01-01 12:00:00'"));
                break;
            default:
                sb.append("NULL");
                break;
            }
        }
        // Add COMMENT (randomly)
        if (Randomly.getBooleanWithSmallProbability()) {
            sb.append(" COMMENT 'comment'");
        }
    }

    /**
     * Optionally add precision and scale for DECIMAL/FLOAT/DOUBLE
     */
    private static void optionallyAddPrecisionAndScale(StringBuilder sb, Randomly r) {
        if (Randomly.getBoolean()) {
            sb.append("(");
            // For decimal(M,D), M must be >= D
            long m = r.getInteger(1, 65);
            long d = Math.min(r.getInteger(0, 30), m); // D cannot exceed M
            sb.append(m);
            sb.append(", ");
            sb.append(d);
            sb.append(")");
        }
    }

}
