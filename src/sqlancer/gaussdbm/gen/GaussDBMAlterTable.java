package sqlancer.gaussdbm.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;

/**
 * Generator for ALTER TABLE statements in GaussDB-M.
 * Uses PostgreSQL-style ALTER TABLE syntax since GaussDB-M is based on openGauss.
 */
public class GaussDBMAlterTable {

    private GaussDBMAlterTable() {
    }

    private enum Action {
        ADD_COLUMN,
        DROP_COLUMN,
        MODIFY_COLUMN,
        SET_SCHEMA,
        RENAME_COLUMN,
        RENAME_TABLE,
        SET_STORAGE_PARAMETER
    }

    public static SQLQueryAdapter create(GaussDBMGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        GaussDBTable table = globalState.getSchema().getRandomTable(t -> !t.isView());
        if (table == null) {
            throw new IgnoreMeException();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ");
        sb.append(table.getName());
        sb.append(" ");

        List<Action> actions = new ArrayList<>(Arrays.asList(Action.values()));
        if (table.getColumns().size() <= 1) {
            actions.remove(Action.DROP_COLUMN);
        }

        Action action = Randomly.fromList(actions);
        switch (action) {
        case ADD_COLUMN:
            sb.append("ADD COLUMN c").append(Randomly.smallNumber());
            sb.append(" ").append(getRandomColumnType());
            if (Randomly.getBoolean()) {
                sb.append(" ").append(Randomly.fromOptions("NULL", "NOT NULL"));
            }
            errors.add("already exists");
            break;
        case DROP_COLUMN:
            sb.append("DROP COLUMN ");
            if (Randomly.getBoolean()) {
                sb.append("IF EXISTS ");
            }
            sb.append(table.getRandomColumn().getName());
            if (Randomly.getBoolean()) {
                sb.append(" ").append(Randomly.fromOptions("CASCADE", "RESTRICT"));
            }
            errors.add("does not exist");
            errors.add("cannot drop column");
            errors.add("because other objects depend on it");
            break;
        case MODIFY_COLUMN:
            GaussDBColumn column = table.getRandomColumn();
            sb.append("ALTER COLUMN ").append(column.getName());
            sb.append(" TYPE ").append(getRandomColumnType());
            errors.add("cannot alter type");
            errors.add("out of range");
            break;
        case SET_SCHEMA:
            sb.append("SET SCHEMA public");
            errors.add("already exists in schema");
            break;
        case RENAME_COLUMN:
            GaussDBColumn col = table.getRandomColumn();
            sb.append("RENAME COLUMN ").append(col.getName());
            sb.append(" TO new_").append(col.getName()).append("_").append(Randomly.smallNumber());
            errors.add("does not exist");
            errors.add("already exists");
            break;
        case RENAME_TABLE:
            sb.append("RENAME TO ").append(table.getName()).append("_").append(Randomly.smallNumber());
            errors.add("already exists");
            break;
        case SET_STORAGE_PARAMETER:
            sb.append("SET (fillfactor=").append(globalState.getRandomly().getInteger(10, 100)).append(")");
            errors.add("unrecognized parameter");
            break;
        default:
            throw new AssertionError(action);
        }

        addCommonAlterErrors(errors);
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }

    private static String getRandomColumnType() {
        return Randomly.fromOptions(
                "INT",
                "BIGINT",
                "VARCHAR(100)",
                "TEXT",
                "FLOAT",
                "DOUBLE PRECISION",
                "DECIMAL(10,2)",
                "DATE",
                "TIMESTAMP",
                "BOOLEAN"
        );
    }

    private static void addCommonAlterErrors(ExpectedErrors errors) {
        errors.add("cannot alter table");
        errors.add("does not exist");
        errors.add("invalid input syntax");
    }
}