package sqlancer.cockroachdb.gen.transaction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import sqlancer.IgnoreMeException;
import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.cockroachdb.CockroachDBOptions;
import sqlancer.cockroachdb.CockroachDBProvider;
import sqlancer.cockroachdb.gen.CockroachDBDeleteGenerator;
import sqlancer.cockroachdb.gen.CockroachDBInsertGenerator;
import sqlancer.cockroachdb.gen.CockroachDBRandomQuerySynthesizer;
import sqlancer.cockroachdb.gen.CockroachDBUpdateGenerator;
import sqlancer.cockroachdb.transaction.CockroachDBTxStatement;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLQueryProvider;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxStatement;

public class CockroachDBTransactionProvider {

    public enum CockroachDBTransactionAction {

        BEGIN(CockroachDBBeginGenerator::getQuery),
        COMMIT(CockroachDBCommitGenerator::getQuery),
        ROLLBACK(CockroachDBRollbackGenerator::getQuery),
        INSERT(CockroachDBInsertGenerator::insert),
        SELECT((state) -> CockroachDBRandomQuerySynthesizer.generate(state, Randomly.smallNumber() + 1)),
        DELETE(CockroachDBDeleteGenerator::delete),
        UPDATE(CockroachDBUpdateGenerator::gen);

        private final SQLQueryProvider<CockroachDBProvider.CockroachDBGlobalState> sqlQueryProvider;

        CockroachDBTransactionAction(SQLQueryProvider<CockroachDBProvider.CockroachDBGlobalState> sqlQueryProvider) {
            this.sqlQueryProvider = sqlQueryProvider;
        }

        public SQLQueryAdapter getQuery(CockroachDBProvider.CockroachDBGlobalState state) throws Exception {
            return sqlQueryProvider.getQuery(state);
        }
    }

    private static final int TX_SIZE_MIN = 1;
    private static final int TX_SIZE_MAX = 5;

    public static Transaction generateTransaction(CockroachDBProvider.CockroachDBGlobalState state) throws SQLException {
        SQLConnection con = createNewConnection(state);
        Transaction tx = new Transaction(con);

        int stmtNum = (int) Randomly.getNotCachedInteger(TX_SIZE_MIN, TX_SIZE_MAX + 1);

        try {
            SQLQueryAdapter beginQuery = CockroachDBTransactionAction.BEGIN.getQuery(state);
            TxStatement beginStmt = new CockroachDBTxStatement(tx, beginQuery);
            tx.addStatement(beginStmt);

            CockroachDBTransactionAction[] availActions = new CockroachDBTransactionAction[] {
                    CockroachDBTransactionAction.INSERT,
                    CockroachDBTransactionAction.SELECT,
                    CockroachDBTransactionAction.DELETE,
                    CockroachDBTransactionAction.UPDATE
            };

            for (int i = 1; i <= stmtNum; i++) {
                int actionId = (int) Randomly.getNotCachedInteger(0, availActions.length);
                CockroachDBTransactionAction action = availActions[actionId];
                try {
                    SQLQueryAdapter query = action.getQuery(state);
                    ExpectedErrors errors = query.getExpectedErrors();
                    errors.add("TransactionAbortedError");
                    errors.add("WriteTooOldError");
                    errors.add("RETRY_SERIALIZABLE");
                    SQLQueryAdapter finalQuery = new SQLQueryAdapter(query.getQueryString(), errors);
                    TxStatement txStmt = new CockroachDBTxStatement(tx, finalQuery);
                    tx.addStatement(txStmt);
                } catch (IgnoreMeException e) {
                    i--;
                }
            }

            SQLQueryAdapter endQuery = CockroachDBTransactionAction.COMMIT.getQuery(state);
            if (Randomly.getBoolean()) {
                endQuery = CockroachDBTransactionAction.ROLLBACK.getQuery(state);
            }
            ExpectedErrors errors = endQuery.getExpectedErrors();
            SQLQueryAdapter finalQuery = new SQLQueryAdapter(endQuery.getQueryString(), errors);
            TxStatement endStmt = new CockroachDBTxStatement(tx, finalQuery);
            tx.addStatement(endStmt);
        } catch (Exception e) {
            throw new IgnoreMeException();
        }
        return tx;
    }

    public static SQLConnection createNewConnection(CockroachDBProvider.CockroachDBGlobalState state) throws SQLException {
        String host = state.getOptions().getHost();
        int port = state.getOptions().getPort();
        if (host == null) {
            host = CockroachDBOptions.DEFAULT_HOST;
        }
        if (port == MainOptions.NO_SET_PORT) {
            port = CockroachDBOptions.DEFAULT_PORT;
        }
        String databaseName = state.getDatabaseName();
        String url = String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
        Connection con = DriverManager.getConnection(url, state.getOptions().getUserName(),
                state.getOptions().getPassword());
        return new SQLConnection(con);
    }

}