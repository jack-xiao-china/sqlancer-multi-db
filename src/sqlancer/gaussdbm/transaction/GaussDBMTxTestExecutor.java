package sqlancer.gaussdbm.transaction;

import java.sql.SQLException;
import java.util.List;

import sqlancer.common.query.SQLancerResultSet;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxTestExecutor;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.transaction.GaussDBMIsolation.GaussDBMIsolationLevel;

public class GaussDBMTxTestExecutor extends TxTestExecutor<GaussDBMGlobalState> {

    public GaussDBMTxTestExecutor(GaussDBMGlobalState globalState, List<Transaction> transactions,
                                  List<TxStatement> schedule, GaussDBMIsolationLevel isoLevel) {
        super(globalState, transactions, schedule, isoLevel);
    }

    @Override
    protected void generateIsolationLevel() throws SQLException {
        for (Transaction tx : transactions) {
            TxSQLQueryAdapter isoQuery = new TxSQLQueryAdapter(
                    "SET SESSION TRANSACTION ISOLATION LEVEL " + isoLevel.getName());
            isoQuery.execute(tx);
        }
    }

    @Override
    protected void setTimeout() throws SQLException {
        // GaussDB-M timeout handled by lock_wait_timeout
    }

    @Override
    protected void handleAbortedTxn(Transaction transaction) throws SQLException {
        TxSQLQueryAdapter rollbackStmt = new TxSQLQueryAdapter("ROLLBACK");
        try {
            rollbackStmt.execute(transaction);
        } catch (SQLException e) {
            throw new RuntimeException("Transaction returning rollback exception: ", e);
        }
    }

    @Override
    protected SQLancerResultSet showWarnings(Transaction transaction) throws SQLException {
        return null;
    }
}