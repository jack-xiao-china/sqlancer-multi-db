package sqlancer.gaussdba.gen;

import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema.GaussDBAColumn;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;

/**
 * Generator for CREATE INDEX statements in GaussDB-A (Oracle Compatibility Mode).
 * Uses PostgreSQL-style index creation syntax since GaussDB-A is based on openGauss.
 * Note: Oracle compatibility mode may have some DDL syntax differences.
 */
public class GaussDBAIndexGenerator {

    private GaussDBAIndexGenerator() {
    }

    public static SQLQueryAdapter create(GaussDBAGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        GaussDBATable table = globalState.getSchema().getRandomTable(t -> !t.isView());
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
            sb.append(Randomly.fromOptions("btree", "hash"));
        }

        sb.append("(");
        List<GaussDBAColumn> columns = table.getRandomNonEmptyColumnSubset();
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