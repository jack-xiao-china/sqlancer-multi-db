package sqlancer.mysql.transaction;

import java.sql.SQLException;
import java.util.List;

import sqlancer.common.query.SQLancerResultSet;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxTestExecutor;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.gen.transaction.MySQLIsolationLevelGenerator;
import sqlancer.mysql.transaction.MySQLIsolation.MySQLIsolationLevel;

public class MySQLTxTestExecutor extends TxTestExecutor<MySQLGlobalState> {

    public MySQLTxTestExecutor(MySQLGlobalState globalState, List<Transaction> transactions,
                               List<TxStatement> schedule, MySQLIsolationLevel isoLevel) {
        super(globalState, transactions, schedule, isoLevel);
    }

    @Override
    protected void generateIsolationLevel() throws SQLException {
        MySQLIsolationLevelGenerator isoLevelGenerator = new MySQLIsolationLevelGenerator(isoLevel);
        for (Transaction tx : transactions) {
            TxSQLQueryAdapter isoQuery = new TxSQLQueryAdapter(isoLevelGenerator.getQuery());
            isoQuery.execute(tx);
        }
    }

    @Override
    protected void setTimeout() throws SQLException {
        for (Transaction tx : transactions) {
            TxSQLQueryAdapter timeoutQuery = new TxSQLQueryAdapter("SET innodb_lock_wait_timeout = 100");
            timeoutQuery.execute(tx);
        }
    }

    @Override
    protected SQLancerResultSet showWarnings(Transaction transaction) throws SQLException {
        TxSQLQueryAdapter showSql = new TxSQLQueryAdapter("SHOW WARNINGS");
        return showSql.executeAndGet(transaction);
    }

    @Override
    protected void handleAbortedTxn(Transaction transaction) throws SQLException {
        // MySQL handles deadlock automatically
    }
}