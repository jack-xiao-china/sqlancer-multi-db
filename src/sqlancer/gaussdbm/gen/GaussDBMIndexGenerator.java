package sqlancer.gaussdbm.gen;

import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBColumn;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;

/**
 * Generator for CREATE INDEX statements in GaussDB-M.
 * GaussDB-M uses PostgreSQL-style index creation syntax since it's based on openGauss.
 */
public class GaussDBMIndexGenerator {

    private GaussDBMIndexGenerator() {
    }

    public static SQLQueryAdapter create(GaussDBMGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        GaussDBTable table = globalState.getSchema().getRandomTable(t -> !t.isView());
        if (table == null) {
            throw new IgnoreMeException();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE ");
        if (Randomly.getBoolean()) {
            sb.append("UNIQUE ");
            errors.add("duplicate key value");
            errors.add("could not create unique index");
        }
        sb.append("INDEX i").append(Randomly.smallNumber());
        sb.append(" ON ");
        sb.append(table.getName());

        // Index type (PostgreSQL style)
        if (Randomly.getBoolean()) {
            sb.append(" USING ");
            sb.append(Randomly.fromOptions("btree", "hash", "gin", "gist"));
        }

        sb.append("(");
        List<GaussDBColumn> columns = table.getRandomNonEmptyColumnSubset();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(columns.get(i).getName());
            if (Randomly.getBoolean()) {
                sb.append(" ");
                sb.append(Randomly.fromOptions("ASC", "DESC"));
            }
        }
        sb.append(")");

        addCommonIndexErrors(errors);
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }

    private static void addCommonIndexErrors(ExpectedErrors errors) {
        errors.add("already exists");
        errors.add("does not exist");
        errors.add("multiple primary keys");
        errors.add("cannot create index");
        errors.add("has no default operator class");
        errors.add("does not support");
        errors.add("out of range");
        errors.add("invalid input syntax");
    }
}