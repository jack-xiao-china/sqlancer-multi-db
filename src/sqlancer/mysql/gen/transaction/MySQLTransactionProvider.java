package sqlancer.mysql.gen.transaction;

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
import sqlancer.mysql.MySQLOptions;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLVisitor;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.gen.MySQLDeleteGenerator;
import sqlancer.mysql.gen.MySQLInsertGenerator;
import sqlancer.mysql.gen.MySQLRandomQuerySynthesizer;
import sqlancer.mysql.gen.MySQLUpdateGenerator;
import sqlancer.mysql.transaction.MySQLTxStatement;

public class MySQLTransactionProvider {

    public enum MySQLTransactionAction {
        BEGIN(MySQLBeginGenerator::getQuery),
        COMMIT(MySQLCommitGenerator::getQuery),
        ROLLBACK(MySQLRollbackGenerator::getQuery),
        INSERT(MySQLInsertGenerator::insertRow),
        SELECT((state) -> {
            MySQLSelect select = MySQLRandomQuerySynthesizer.generate(state, Randomly.smallNumber() + 1);
            return new SQLQueryAdapter(MySQLVisitor.asString(select));
        }),
        SELECT_FOR_UPDATE((state) -> {
            MySQLSelect select = MySQLRandomQuerySynthesizer.generate(state, Randomly.smallNumber() + 1);
            return new SQLQueryAdapter(MySQLVisitor.asString(select) + " FOR UPDATE");
        }),
        SELECT_FOR_SHARE((state) -> {
            MySQLSelect select = MySQLRandomQuerySynthesizer.generate(state, Randomly.smallNumber() + 1);
            return new SQLQueryAdapter(MySQLVisitor.asString(select) + " LOCK IN SHARE MODE");
        }),
        DELETE(MySQLDeleteGenerator::delete),
        UPDATE(MySQLUpdateGenerator::create);

        private final SQLQueryProvider<MySQLGlobalState> sqlQueryProvider;

        MySQLTransactionAction(SQLQueryProvider<MySQLGlobalState> sqlQueryProvider) {
            this.sqlQueryProvider = sqlQueryProvider;
        }

        public SQLQueryAdapter getQuery(MySQLGlobalState state) throws Exception {
            return sqlQueryProvider.getQuery(state);
        }
    }

    private static final int TX_SIZE_MIN = 1;
    private static final int TX_SIZE_MAX = 5;

    public static Transaction generateTransaction(MySQLGlobalState state) throws SQLException {
        SQLConnection con = createNewConnection(state);
        Transaction tx = new Transaction(con);

        int stmtNum = (int) Randomly.getNotCachedInteger(TX_SIZE_MIN, TX_SIZE_MAX + 1);

        try {
            SQLQueryAdapter beginQuery = MySQLTransactionAction.BEGIN.getQuery(state);
            TxStatement beginStmt = new MySQLTxStatement(tx, beginQuery);
            tx.addStatement(beginStmt);

            MySQLTransactionAction[] availActions = new MySQLTransactionAction[] {
                    MySQLTransactionAction.INSERT,
                    MySQLTransactionAction.SELECT,
                    MySQLTransactionAction.SELECT_FOR_UPDATE,
                    MySQLTransactionAction.SELECT_FOR_SHARE,
                    MySQLTransactionAction.DELETE,
                    MySQLTransactionAction.UPDATE
            };

            for (int i = 1; i <= stmtNum; i++) {
                int actionId = (int) Randomly.getNotCachedInteger(0, availActions.length);
                MySQLTransactionAction action = availActions[actionId];
                try {
                    SQLQueryAdapter query = action.getQuery(state);
                    ExpectedErrors errors = query.getExpectedErrors();
                    // Transaction-related errors
                    errors.add("Deadlock found");
                    errors.add("Lock wait timeout exceeded");
                    // MySQL strict mode errors
                    errors.add("only_full_group_by");
                    errors.add("incompatible with sql_mode=only_full_group_by");
                    errors.add("Incorrect DATE value");
                    errors.add("Incorrect DATETIME value");
                    errors.add("Incorrect TIME value");
                    errors.add("Incorrect TIMESTAMP value");
                    errors.add("Incorrect integer value");
                    errors.add("Incorrect double value");
                    errors.add("Incorrect decimal value");
                    errors.add("Incorrect varchar value");
                    errors.add("Truncated incorrect");
                    errors.add("Data truncated for column");
                    errors.add("Out of range value for column");
                    errors.add("Invalid use of NULL value");
                    errors.add("Expression #1 of ORDER BY clause is not in GROUP BY clause");
                    errors.add("contains nonaggregated column");
                    errors.add("is not functionally dependent on columns in GROUP BY clause");
                    errors.add("cannot be null");
                    errors.add("Column cannot be null");
                    errors.add("doesn't have a default value");
                    errors.add("doesn't have a default value");
                    // Syntax errors
                    errors.add("You have an error in your SQL syntax");
                    errors.add("Unknown column");
                    errors.add("Unknown table");
                    errors.add("doesn't exist");
                    errors.add("Duplicate entry");
                    errors.add("Duplicate key");
                    SQLQueryAdapter finalQuery = new SQLQueryAdapter(query.getQueryString(), errors);
                    TxStatement txStmt = new MySQLTxStatement(tx, finalQuery);
                    tx.addStatement(txStmt);
                } catch (IgnoreMeException e) {
                    i--;
                }
            }

            SQLQueryAdapter endQuery = MySQLTransactionAction.COMMIT.getQuery(state);
            if (Randomly.getBoolean()) {
                endQuery = MySQLTransactionAction.ROLLBACK.getQuery(state);
            }
            TxStatement endStmt = new MySQLTxStatement(tx, endQuery);
            tx.addStatement(endStmt);
        } catch (Exception e) {
            throw new IgnoreMeException();
        }
        return tx;
    }

    public static SQLConnection createNewConnection(MySQLGlobalState state) throws SQLException {
        String host = state.getOptions().getHost();
        int port = state.getOptions().getPort();
        if (host == null) {
            host = MySQLOptions.DEFAULT_HOST;
        }
        if (port == MainOptions.NO_SET_PORT) {
            port = MySQLOptions.DEFAULT_PORT;
        }
        String databaseName = state.getDatabaseName();
        String url = String.format(
                "jdbc:mysql://%s:%d/%s?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true&zeroDateTimeBehavior=CONVERT_TO_NULL",
                host, port, databaseName);
        Connection con = DriverManager.getConnection(url, state.getOptions().getUserName(),
                state.getOptions().getPassword());
        return new SQLConnection(con);
    }
}