package sqlancer.gaussdbm.gen;

import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBIndex;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;

/**
 * Generator for DROP INDEX statements in GaussDB-M.
 * Uses PostgreSQL-style DROP INDEX syntax.
 */
public final class GaussDBMDropIndexGenerator {

    private GaussDBMDropIndexGenerator() {
    }

    public static SQLQueryAdapter generate(GaussDBMGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        GaussDBTable table = globalState.getSchema().getRandomTableOrBailout(t -> !t.isView() && t.getIndexes() != null && !t.getIndexes().isEmpty());
        if (table == null) {
            throw new IgnoreMeException();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("DROP INDEX ");
        if (Randomly.getBoolean()) {
            sb.append("IF EXISTS ");
        }

        // Get a random index from the table
        List<GaussDBIndex> indexes = table.getIndexes();
        if (indexes.isEmpty()) {
            throw new IgnoreMeException();
        }
        GaussDBIndex index = Randomly.fromList(indexes);
        sb.append(index.getIndexName());

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