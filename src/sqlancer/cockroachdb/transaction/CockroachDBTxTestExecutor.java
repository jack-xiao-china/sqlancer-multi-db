package sqlancer.cockroachdb.transaction;

import sqlancer.cockroachdb.CockroachDBProvider;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxTestExecutor;

import java.sql.SQLException;
import java.util.List;

public class CockroachDBTxTestExecutor extends TxTestExecutor<CockroachDBProvider.CockroachDBGlobalState> {

    public CockroachDBTxTestExecutor(CockroachDBProvider.CockroachDBGlobalState globalState, List<Transaction> transactions,
                                     List<TxStatement> schedule, CockroachDBIsolation.CockroachDBIsolationLevel isoLevel) {
        super(globalState, transactions, schedule, isoLevel);
    }

    @Override
    protected void generateIsolationLevel() throws SQLException {

    }

    @Override
    protected void setTimeout() throws SQLException {

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