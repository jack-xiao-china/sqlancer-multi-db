package sqlancer.postgres.gen.transaction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import sqlancer.IgnoreMeException;
import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLQueryProvider;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxStatement;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresOptions;
import sqlancer.postgres.PostgresVisitor;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.gen.PostgresDeleteGenerator;
import sqlancer.postgres.gen.PostgresInsertGenerator;
import sqlancer.postgres.gen.PostgresRandomQueryGenerator;
import sqlancer.postgres.gen.PostgresUpdateGenerator;
import sqlancer.postgres.transaction.PostgresTxStatement;

public class PostgresTransactionProvider {

    public enum PostgresTransactionAction {
        BEGIN(PostgresBeginGenerator::getQuery),
        COMMIT(PostgresCommitGenerator::getQuery),
        ROLLBACK(PostgresRollbackGenerator::getQuery),
        INSERT(PostgresInsertGenerator::insert),
        SELECT((state) -> {
            PostgresSelect select = PostgresRandomQueryGenerator.createRandomQuery(
                    Randomly.smallNumber() + 1, state);
            return new SQLQueryAdapter(PostgresVisitor.asString(select));
        }),
        SELECT_FOR_UPDATE((state) -> {
            PostgresSelect select = PostgresRandomQueryGenerator.createRandomQuery(
                    Randomly.smallNumber() + 1, state);
            return new SQLQueryAdapter(PostgresVisitor.asString(select) + " FOR UPDATE");
        }),
        SELECT_FOR_SHARE((state) -> {
            PostgresSelect select = PostgresRandomQueryGenerator.createRandomQuery(
                    Randomly.smallNumber() + 1, state);
            return new SQLQueryAdapter(PostgresVisitor.asString(select) + " FOR SHARE");
        }),
        DELETE(PostgresDeleteGenerator::create),
        UPDATE(PostgresUpdateGenerator::create);

        private final SQLQueryProvider<PostgresGlobalState> sqlQueryProvider;

        PostgresTransactionAction(SQLQueryProvider<PostgresGlobalState> sqlQueryProvider) {
            this.sqlQueryProvider = sqlQueryProvider;
        }

        public SQLQueryAdapter getQuery(PostgresGlobalState state) throws Exception {
            return sqlQueryProvider.getQuery(state);
        }
    }

    private static final int TX_SIZE_MIN = 1;
    private static final int TX_SIZE_MAX = 5;

    public static Transaction generateTransaction(PostgresGlobalState state) throws SQLException {
        SQLConnection con = createNewConnection(state);
        Transaction tx = new Transaction(con);

        int stmtNum = (int) Randomly.getNotCachedInteger(TX_SIZE_MIN, TX_SIZE_MAX + 1);

        try {
            SQLQueryAdapter beginQuery = PostgresTransactionAction.BEGIN.getQuery(state);
            TxStatement beginStmt = new PostgresTxStatement(tx, beginQuery);
            tx.addStatement(beginStmt);

            PostgresTransactionAction[] availActions = new PostgresTransactionAction[] {
                    PostgresTransactionAction.INSERT,
                    PostgresTransactionAction.SELECT,
                    PostgresTransactionAction.SELECT_FOR_UPDATE,
                    PostgresTransactionAction.SELECT_FOR_SHARE,
                    PostgresTransactionAction.DELETE,
                    PostgresTransactionAction.UPDATE
            };

            for (int i = 1; i <= stmtNum; i++) {
                int actionId = (int) Randomly.getNotCachedInteger(0, availActions.length);
                PostgresTransactionAction action = availActions[actionId];
                try {
                    SQLQueryAdapter query = action.getQuery(state);
                    ExpectedErrors errors = query.getExpectedErrors();
                    errors.add("deadlock detected");
                    errors.add("could not serialize access");
                    errors.add("canceling statement due to lock timeout");
                    SQLQueryAdapter finalQuery = new SQLQueryAdapter(query.getQueryString(), errors);
                    TxStatement txStmt = new PostgresTxStatement(tx, finalQuery);
                    tx.addStatement(txStmt);
                } catch (IgnoreMeException e) {
                    i--;
                }
            }

            SQLQueryAdapter endQuery = PostgresTransactionAction.COMMIT.getQuery(state);
            if (Randomly.getBoolean()) {
                endQuery = PostgresTransactionAction.ROLLBACK.getQuery(state);
            }
            TxStatement endStmt = new PostgresTxStatement(tx, endQuery);
            tx.addStatement(endStmt);
        } catch (Exception e) {
            throw new IgnoreMeException();
        }
        return tx;
    }

    public static SQLConnection createNewConnection(PostgresGlobalState state) throws SQLException {
        String host = state.getOptions().getHost();
        int port = state.getOptions().getPort();
        if (host == null) {
            host = PostgresOptions.DEFAULT_HOST;
        }
        if (port == MainOptions.NO_SET_PORT) {
            port = PostgresOptions.DEFAULT_PORT;
        }
        String databaseName = state.getDatabaseName();
        String url = String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
        Connection con = DriverManager.getConnection(url, state.getOptions().getUserName(),
                state.getOptions().getPassword());
        return new SQLConnection(con);
    }
}