package sqlancer.gaussdbm.gen;

import java.util.ArrayList;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMErrors;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBDataType;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBExpression;

public final class GaussDBMUpdateGenerator {

    private GaussDBMUpdateGenerator() {
    }

    public static SQLQueryAdapter create(GaussDBMGlobalState globalState) {
        GaussDBTable table = globalState.getSchema().getRandomTable();
        ExpectedErrors errors = new ExpectedErrors();
        errors.add("violates foreign key constraint");
        errors.add("violates not-null constraint");
        errors.add("violates unique constraint");
        errors.add("duplicate key");
        // Add datetime/date/time value errors for WHERE clause evaluation and SET value assignment
        GaussDBMErrors.addExpressionErrors(errors);
        GaussDBMErrors.addInsertUpdateErrors(errors);

        StringBuilder sb = new StringBuilder("UPDATE ");
        sb.append(table.getName());
        sb.append(" SET ");

        List<GaussDBColumn> columns = new ArrayList<>(table.getRandomNonEmptyColumnSubset());
        for (int i = 0; i < columns.size(); i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(columns.get(i).getName());
            sb.append(" = ");
            sb.append(getRandomValueForType(columns.get(i).getType(), globalState));
        }

        if (Randomly.getBoolean()) {
            sb.append(" WHERE ");
            GaussDBMExpressionGenerator gen = new GaussDBMExpressionGenerator(globalState)
                    .setColumns(table.getColumns());
            GaussDBExpression where = gen.generateExpression();
            sb.append(GaussDBToStringVisitor.asString(where));
        }

        return new SQLQueryAdapter(sb.toString(), errors);
    }

    private static String getRandomValueForType(GaussDBDataType type, GaussDBMGlobalState globalState) {
        if (Randomly.getBooleanWithSmallProbability()) {
            return "NULL";
        }
        switch (type) {
        case INT:
            return String.valueOf(Randomly.getNotCachedInteger(-1000, 1000));
        case VARCHAR:
            String s = globalState.getRandomly().getString();
            return "'" + s.substring(0, Math.min(10, s.length())).replace("\\", "\\\\").replace("'", "''") + "'";
        case FLOAT:
            return String.valueOf(Randomly.getNotCachedInteger(-100, 100)) + "." + Randomly.getNotCachedInteger(0, 99);
        case DOUBLE:
            return String.valueOf(Randomly.getNotCachedInteger(-1000, 1000)) + "."
                    + Randomly.getNotCachedInteger(0, 999);
        case DECIMAL:
            return String.valueOf(Randomly.getNotCachedInteger(-1000, 1000)) + "."
                    + String.format("%02d", (int) Randomly.getNotCachedInteger(0, 99));
        case DATE:
            int year = (int) Randomly.getNotCachedInteger(1970, 2038);
            int month = (int) Randomly.getNotCachedInteger(1, 12);
            int day = (int) Randomly.getNotCachedInteger(1, 28);
            return "'" + String.format("%04d-%02d-%02d", year, month, day) + "'";
        case TIME:
            int hour = (int) Randomly.getNotCachedInteger(0, 23);
            int minute = (int) Randomly.getNotCachedInteger(0, 59);
            int second = (int) Randomly.getNotCachedInteger(0, 59);
            return "'" + String.format("%02d:%02d:%02d", hour, minute, second) + "'";
        case DATETIME:
            year = (int) Randomly.getNotCachedInteger(1970, 2038);
            month = (int) Randomly.getNotCachedInteger(1, 12);
            day = (int) Randomly.getNotCachedInteger(1, 28);
            hour = (int) Randomly.getNotCachedInteger(0, 23);
            minute = (int) Randomly.getNotCachedInteger(0, 59);
            second = (int) Randomly.getNotCachedInteger(0, 59);
            return "'" + String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second) + "'";
        case TIMESTAMP:
            year = (int) Randomly.getNotCachedInteger(1970, 2038);
            month = (int) Randomly.getNotCachedInteger(1, 12);
            day = (int) Randomly.getNotCachedInteger(1, 28);
            hour = (int) Randomly.getNotCachedInteger(0, 23);
            minute = (int) Randomly.getNotCachedInteger(0, 59);
            second = (int) Randomly.getNotCachedInteger(0, 59);
            return "'" + String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second) + "'";
        case YEAR:
            return "'" + String.format("%04d", (int) Randomly.getNotCachedInteger(1901, 2155)) + "'";
        default:
            throw new AssertionError(type);
        }
    }
}