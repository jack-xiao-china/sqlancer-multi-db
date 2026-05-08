package sqlancer.gaussdba.gen.transaction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import sqlancer.IgnoreMeException;
import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLQueryProvider;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxStatement;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBAToStringVisitor;
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.gen.GaussDBADeleteGenerator;
import sqlancer.gaussdba.gen.GaussDBAInsertGenerator;
import sqlancer.gaussdba.gen.GaussDBARandomQueryGenerator;
import sqlancer.gaussdba.gen.GaussDBAUpdateGenerator;
import sqlancer.gaussdba.transaction.GaussDBATxStatement;

public class GaussDBATransactionProvider {

    public enum GaussDBATransactionAction {
        BEGIN(GaussDBABeginGenerator::getQuery),
        COMMIT(GaussDBACommitGenerator::getQuery),
        ROLLBACK(GaussDBARollbackGenerator::getQuery),
        INSERT(GaussDBAInsertGenerator::insertRow),
        SELECT((state) -> {
            GaussDBASelect select = GaussDBARandomQueryGenerator.createRandomQuery(
                    Randomly.smallNumber() + 1, state);
            return new SQLQueryAdapter(GaussDBAToStringVisitor.asString(select));
        }),
        SELECT_FOR_UPDATE((state) -> {
            GaussDBASelect select = GaussDBARandomQueryGenerator.createRandomQuery(
                    Randomly.smallNumber() + 1, state);
            return new SQLQueryAdapter(GaussDBAToStringVisitor.asString(select) + " FOR UPDATE");
        }),
        SELECT_FOR_SHARE((state) -> {
            GaussDBASelect select = GaussDBARandomQueryGenerator.createRandomQuery(
                    Randomly.smallNumber() + 1, state);
            return new SQLQueryAdapter(GaussDBAToStringVisitor.asString(select) + " FOR SHARE");
        }),
        DELETE(GaussDBADeleteGenerator::create),
        UPDATE(GaussDBAUpdateGenerator::create);

        private final SQLQueryProvider<GaussDBAGlobalState> sqlQueryProvider;

        GaussDBATransactionAction(SQLQueryProvider<GaussDBAGlobalState> sqlQueryProvider) {
            this.sqlQueryProvider = sqlQueryProvider;
        }

        public SQLQueryAdapter getQuery(GaussDBAGlobalState state) throws Exception {
            return sqlQueryProvider.getQuery(state);
        }
    }

    private static final int TX_SIZE_MIN = 1;
    private static final int TX_SIZE_MAX = 5;

    public static Transaction generateTransaction(GaussDBAGlobalState state) throws SQLException {
        SQLConnection con = createNewConnection(state);
        Transaction tx = new Transaction(con);

        int stmtNum = (int) Randomly.getNotCachedInteger(TX_SIZE_MIN, TX_SIZE_MAX + 1);

        try {
            SQLQueryAdapter beginQuery = GaussDBATransactionAction.BEGIN.getQuery(state);
            TxStatement beginStmt = new GaussDBATxStatement(tx, beginQuery);
            tx.addStatement(beginStmt);

            GaussDBATransactionAction[] availActions = new GaussDBATransactionAction[] {
                    GaussDBATransactionAction.INSERT,
                    GaussDBATransactionAction.SELECT,
                    GaussDBATransactionAction.SELECT_FOR_UPDATE,
                    GaussDBATransactionAction.SELECT_FOR_SHARE,
                    GaussDBATransactionAction.DELETE,
                    GaussDBATransactionAction.UPDATE
            };

            for (int i = 1; i <= stmtNum; i++) {
                int actionId = (int) Randomly.getNotCachedInteger(0, availActions.length);
                GaussDBATransactionAction action = availActions[actionId];
                try {
                    SQLQueryAdapter query = action.getQuery(state);
                    ExpectedErrors errors = query.getExpectedErrors();
                    errors.add("deadlock detected");
                    errors.add("could not serialize access");
                    errors.add("canceling statement due to lock timeout");
                    SQLQueryAdapter finalQuery = new SQLQueryAdapter(query.getQueryString(), errors);
                    TxStatement txStmt = new GaussDBATxStatement(tx, finalQuery);
                    tx.addStatement(txStmt);
                } catch (IgnoreMeException e) {
                    i--;
                }
            }

            SQLQueryAdapter endQuery = GaussDBATransactionAction.COMMIT.getQuery(state);
            if (Randomly.getBoolean()) {
                endQuery = GaussDBATransactionAction.ROLLBACK.getQuery(state);
            }
            TxStatement endStmt = new GaussDBATxStatement(tx, endQuery);
            tx.addStatement(endStmt);
        } catch (Exception e) {
            throw new IgnoreMeException();
        }
        return tx;
    }

    public static SQLConnection createNewConnection(GaussDBAGlobalState state) throws SQLException {
        String host = state.getOptions().getHost();
        int port = state.getOptions().getPort();
        String username = state.getOptions().getUserName();
        String password = state.getOptions().getPassword();

        if (host == null) {
            host = "localhost";
        }
        if (port == MainOptions.NO_SET_PORT) {
            port = 8000;
        }

        String databaseName = state.getDbmsSpecificOptions().targetDatabase;
        if (databaseName == null) {
            databaseName = state.getDatabaseName();
        }
        String baseParams = "sslmode=disable&connectTimeout=10";

        String url = String.format("jdbc:opengauss://%s:%d/%s?%s", host, port, databaseName, baseParams);

        Properties props = new Properties();
        if (username != null) {
            props.setProperty("user", username);
        }
        if (password != null) {
            props.setProperty("password", password);
        }

        Connection con = DriverManager.getConnection(url, props);
        return new SQLConnection(con);
    }
}