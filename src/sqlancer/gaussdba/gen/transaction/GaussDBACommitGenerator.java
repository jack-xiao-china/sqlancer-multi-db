package sqlancer.gaussdba.gen.transaction;

import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdba.GaussDBAGlobalState;

public class GaussDBACommitGenerator {

    public static SQLQueryAdapter getQuery(GaussDBAGlobalState state) {
        return new SQLQueryAdapter("COMMIT");
    }
}