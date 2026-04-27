package sqlancer.postgres.gen;

import sqlancer.Randomly;
import sqlancer.common.DBMSCommon;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresCompoundDataType;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresSchema.PostgresDataType;

public final class PostgresTypeGenerator {

    private PostgresTypeGenerator() {
    }

    public static SQLQueryAdapter createCompositeType(PostgresGlobalState globalState) {
        ExpectedErrors errors = ExpectedErrors.from("already exists", "does not exist", "does not accept data type",
                "collations are not supported");
        StringBuilder sb = new StringBuilder("CREATE TYPE ");
        sb.append("ct");
        sb.append(Randomly.smallNumber());
        sb.append(" AS (");
        int nrAttributes = Randomly.smallNumber() + 1;
        for (int i = 0; i < nrAttributes; i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(DBMSCommon.createColumnName(i));
            sb.append(" ");
            PostgresDataType type = Randomly.fromOptions(PostgresDataType.INT, PostgresDataType.BOOLEAN,
                    PostgresDataType.TEXT, PostgresDataType.VARCHAR, PostgresDataType.CHAR, PostgresDataType.DECIMAL,
                    PostgresDataType.FLOAT, PostgresDataType.REAL, PostgresDataType.DATE, PostgresDataType.TIME,
                    PostgresDataType.TIMESTAMP, PostgresDataType.TIMESTAMPTZ, PostgresDataType.INTERVAL,
                    PostgresDataType.UUID, PostgresDataType.BYTEA);
            PostgresCommon.appendDataType(PostgresCompoundDataType.create(type), sb, false, globalState.getCollates());
        }
        sb.append(")");
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }
}
