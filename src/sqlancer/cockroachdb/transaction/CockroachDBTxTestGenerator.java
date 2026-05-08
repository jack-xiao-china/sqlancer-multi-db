package sqlancer.cockroachdb.transaction;

import sqlancer.cockroachdb.CockroachDBProvider;
import sqlancer.cockroachdb.gen.transaction.CockroachDBTransactionProvider;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxTestGenerator;

import java.sql.SQLException;

public class CockroachDBTxTestGenerator extends TxTestGenerator<CockroachDBProvider.CockroachDBGlobalState> {

    public CockroachDBTxTestGenerator(CockroachDBProvider.CockroachDBGlobalState globalState) {
        super(globalState);
    }

    @Override
    protected Transaction generateTransaction() throws SQLException {
        return CockroachDBTransactionProvider.generateTransaction(globalState);
    }

}