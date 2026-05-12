package sqlancer.gaussdba.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;

/**
 * Generator for ALTER TABLE statements in GaussDB-A (Oracle Compatibility Mode).
 * Uses PostgreSQL-style ALTER TABLE syntax since GaussDB-A is based on openGauss.
 * Note: Oracle compatibility mode may have some DDL syntax differences.
 */
public class GaussDBAAlterTable {

    private GaussDBAAlterTable() {
    }

    private enum Action {
        ADD_COLUMN,
        DROP_COLUMN,
        MODIFY_COLUMN,
        RENAME_COLUMN,
        RENAME_TABLE
    }

    public static SQLQueryAdapter create(GaussDBAGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        GaussDBATable table = globalState.getSchema().getRandomTable(t -> !t.isView());
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
            GaussDBAColumn column = table.getRandomColumn();
            sb.append("ALTER COLUMN ").append(column.getName());
            sb.append(" TYPE ").append(getRandomColumnType());
            errors.add("cannot alter type");
            errors.add("out of range");
            break;
        case RENAME_COLUMN:
            GaussDBAColumn col = table.getRandomColumn();
            sb.append("RENAME COLUMN ").append(col.getName());
            sb.append(" TO new_").append(col.getName()).append("_").append(Randomly.smallNumber());
            errors.add("does not exist");
            errors.add("already exists");
            break;
        case RENAME_TABLE:
            sb.append("RENAME TO ").append(table.getName()).append("_").append(Randomly.smallNumber());
            errors.add("already exists");
            break;
        default:
            throw new AssertionError(action);
        }

        addCommonAlterErrors(errors);
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }

    private static String getRandomColumnType() {
        return Randomly.fromOptions(
                "NUMBER",
                "VARCHAR2(100)",
                "CHAR(50)",
                "DATE",
                "TIMESTAMP",
                "CLOB",
                "BLOB",
                "RAW(50)",
                "LONG"
        );
    }

    private static void addCommonAlterErrors(ExpectedErrors errors) {
        errors.add("cannot alter table");
        errors.add("does not exist");
        errors.add("invalid input syntax");
    }
}