package sqlancer.postgres.gen;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresGlobalState;

public final class PostgresFunctionGenerator {

    private PostgresFunctionGenerator() {
    }

    public static SQLQueryAdapter createFunction(PostgresGlobalState globalState) {
        String functionName = "pf";
        functionName += Randomly.smallNumber();
        StringBuilder sb = new StringBuilder("CREATE OR REPLACE FUNCTION ");
        sb.append(functionName);
        if (Randomly.getBoolean()) {
            sb.append("(integer) RETURNS integer LANGUAGE SQL ");
            sb.append(Randomly.fromOptions("IMMUTABLE", "STABLE", "VOLATILE"));
            sb.append(" AS $$ SELECT $1 $$");
        } else {
            sb.append("() RETURNS boolean LANGUAGE SQL ");
            sb.append(Randomly.fromOptions("IMMUTABLE", "STABLE", "VOLATILE"));
            sb.append(" AS $$ SELECT ");
            sb.append(Randomly.fromOptions("TRUE", "FALSE", "NULL"));
            sb.append("::boolean $$");
        }
        return new SQLQueryAdapter(sb.toString(),
                ExpectedErrors.from("cannot change name of input parameter", "must be superuser", "permission denied"),
                true);
    }

}
