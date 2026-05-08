package sqlancer.gaussdba.gen.transaction;

import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdba.GaussDBAGlobalState;

public class GaussDBABeginGenerator {

    public static SQLQueryAdapter getQuery(GaussDBAGlobalState state) {
        return new SQLQueryAdapter("BEGIN");
    }
}