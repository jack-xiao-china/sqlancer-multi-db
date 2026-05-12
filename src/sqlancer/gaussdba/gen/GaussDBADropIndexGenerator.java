package sqlancer.gaussdba.gen;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;

/**
 * Generator for DROP INDEX statements in GaussDB-A (Oracle Compatibility Mode).
 * Uses PostgreSQL-style DROP INDEX syntax.
 */
public final class GaussDBADropIndexGenerator {

    private GaussDBADropIndexGenerator() {
    }

    public static SQLQueryAdapter generate(GaussDBAGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        GaussDBATable table = globalState.getSchema().getRandomTableOrBailout(t -> !t.isView() && t.getIndexes() != null && !t.getIndexes().isEmpty());
        if (table == null) {
            throw new IgnoreMeException();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("DROP INDEX ");
        if (Randomly.getBoolean()) {
            sb.append("IF EXISTS ");
        }

        // Get a random index from the table
        if (table.getIndexes().isEmpty()) {
            throw new IgnoreMeException();
        }
        sb.append(Randomly.fromList(table.getIndexes()).getIndexName());

        if (Randomly.getBoolean()) {
            sb.append(" ");
            sb.append(Randomly.fromOptions("CASCADE", "RESTRICT"));
        }

        errors.add("does not exist");
        errors.add("cannot drop index");
        errors.add("because other objects depend on it");

        return new SQLQueryAdapter(sb.toString(), errors, true);
    }
}