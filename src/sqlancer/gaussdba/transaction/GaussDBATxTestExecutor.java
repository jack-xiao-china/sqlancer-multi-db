package sqlancer.gaussdba.transaction;

import java.sql.SQLException;
import java.util.List;

import sqlancer.common.query.SQLancerResultSet;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxTestExecutor;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.transaction.GaussDBAIsolation.GaussDBAIsolationLevel;

public class GaussDBATxTestExecutor extends TxTestExecutor<GaussDBAGlobalState> {

    public GaussDBATxTestExecutor(GaussDBAGlobalState globalState, List<Transaction> transactions,
                                  List<TxStatement> schedule, GaussDBAIsolationLevel isoLevel) {
        super(globalState, transactions, schedule, isoLevel);
    }

    @Override
    protected void generateIsolationLevel() throws SQLException {
        for (Transaction tx : transactions) {
            TxSQLQueryAdapter isoQuery = new TxSQLQueryAdapter(
                    "SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL " + isoLevel.getName());
            isoQuery.execute(tx);
        }
    }

    @Override
    protected void setTimeout() throws SQLException {
        // GaussDB-A timeout handled by statement_timeout
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