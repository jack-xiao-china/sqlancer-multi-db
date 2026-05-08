package sqlancer.gaussdbm.gen.transaction;

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
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBToStringVisitor;
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.gen.GaussDBMDeleteGenerator;
import sqlancer.gaussdbm.gen.GaussDBMInsertGenerator;
import sqlancer.gaussdbm.gen.GaussDBMRandomQuerySynthesizer;
import sqlancer.gaussdbm.gen.GaussDBMUpdateGenerator;
import sqlancer.gaussdbm.transaction.GaussDBMTxStatement;

public class GaussDBMTransactionProvider {

    public enum GaussDBMTransactionAction {
        BEGIN(GaussDBMBeginGenerator::getQuery),
        COMMIT(GaussDBMCommitGenerator::getQuery),
        ROLLBACK(GaussDBMRollbackGenerator::getQuery),
        INSERT(GaussDBMInsertGenerator::insertRow),
        SELECT((state) -> {
            GaussDBSelect select = GaussDBMRandomQuerySynthesizer.generate(state, Randomly.smallNumber() + 1);
            return new SQLQueryAdapter(GaussDBToStringVisitor.asString(select));
        }),
        SELECT_FOR_UPDATE((state) -> {
            GaussDBSelect select = GaussDBMRandomQuerySynthesizer.generate(state, Randomly.smallNumber() + 1);
            return new SQLQueryAdapter(GaussDBToStringVisitor.asString(select) + " FOR UPDATE");
        }),
        SELECT_FOR_SHARE((state) -> {
            GaussDBSelect select = GaussDBMRandomQuerySynthesizer.generate(state, Randomly.smallNumber() + 1);
            return new SQLQueryAdapter(GaussDBToStringVisitor.asString(select) + " LOCK IN SHARE MODE");
        }),
        DELETE(GaussDBMDeleteGenerator::create),
        UPDATE(GaussDBMUpdateGenerator::create);

        private final SQLQueryProvider<GaussDBMGlobalState> sqlQueryProvider;

        GaussDBMTransactionAction(SQLQueryProvider<GaussDBMGlobalState> sqlQueryProvider) {
            this.sqlQueryProvider = sqlQueryProvider;
        }

        public SQLQueryAdapter getQuery(GaussDBMGlobalState state) throws Exception {
            return sqlQueryProvider.getQuery(state);
        }
    }

    private static final int TX_SIZE_MIN = 1;
    private static final int TX_SIZE_MAX = 5;

    public static Transaction generateTransaction(GaussDBMGlobalState state) throws SQLException {
        SQLConnection con = createNewConnection(state);
        Transaction tx = new Transaction(con);

        int stmtNum = (int) Randomly.getNotCachedInteger(TX_SIZE_MIN, TX_SIZE_MAX + 1);

        try {
            SQLQueryAdapter beginQuery = GaussDBMTransactionAction.BEGIN.getQuery(state);
            TxStatement beginStmt = new GaussDBMTxStatement(tx, beginQuery);
            tx.addStatement(beginStmt);

            GaussDBMTransactionAction[] availActions = new GaussDBMTransactionAction[] {
                    GaussDBMTransactionAction.INSERT,
                    GaussDBMTransactionAction.SELECT,
                    GaussDBMTransactionAction.SELECT_FOR_UPDATE,
                    GaussDBMTransactionAction.SELECT_FOR_SHARE,
                    GaussDBMTransactionAction.DELETE,
                    GaussDBMTransactionAction.UPDATE
            };

            for (int i = 1; i <= stmtNum; i++) {
                int actionId = (int) Randomly.getNotCachedInteger(0, availActions.length);
                GaussDBMTransactionAction action = availActions[actionId];
                try {
                    SQLQueryAdapter query = action.getQuery(state);
                    ExpectedErrors errors = query.getExpectedErrors();
                    errors.add("Deadlock found");
                    errors.add("Lock wait timeout exceeded");
                    SQLQueryAdapter finalQuery = new SQLQueryAdapter(query.getQueryString(), errors);
                    TxStatement txStmt = new GaussDBMTxStatement(tx, finalQuery);
                    tx.addStatement(txStmt);
                } catch (IgnoreMeException e) {
                    i--;
                }
            }

            SQLQueryAdapter endQuery = GaussDBMTransactionAction.COMMIT.getQuery(state);
            if (Randomly.getBoolean()) {
                endQuery = GaussDBMTransactionAction.ROLLBACK.getQuery(state);
            }
            TxStatement endStmt = new GaussDBMTxStatement(tx, endQuery);
            tx.addStatement(endStmt);
        } catch (Exception e) {
            throw new IgnoreMeException();
        }
        return tx;
    }

    public static SQLConnection createNewConnection(GaussDBMGlobalState state) throws SQLException {
        String host = state.getOptions().getHost();
        int port = state.getOptions().getPort();
        String username = state.getOptions().getUserName();
        String password = state.getOptions().getPassword();

        if (host == null) {
            host = "localhost";
        }
        if (port == MainOptions.NO_SET_PORT) {
            port = 8000; // Default GaussDB port
        }

        String databaseName = state.getDatabaseName();
        String baseParams = "sslmode=disable&connectTimeout=10";

        // Try opengauss JDBC driver first
        String url = String.format("jdbc:opengauss://%s:%d/%s?%s", host, port, databaseName, baseParams);

        Properties props = new Properties();
        if (username != null) {
            props.setProperty("user", username);
        }
        if (password != null) {
            props.setProperty("password", password);
        }

        try {
            Connection con = DriverManager.getConnection(url, props);
            return new SQLConnection(con);
        } catch (SQLException e) {
            // Try mysql JDBC driver as fallback
            url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true", host, port, databaseName);
            Connection con = DriverManager.getConnection(url, username, password);
            return new SQLConnection(con);
        }
    }
}