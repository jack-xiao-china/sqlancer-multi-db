package sqlancer.gaussdbm.transaction;

import java.sql.SQLException;

import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxTestGenerator;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.gen.transaction.GaussDBMTransactionProvider;

public class GaussDBMTxTestGenerator extends TxTestGenerator<GaussDBMGlobalState> {

    public GaussDBMTxTestGenerator(GaussDBMGlobalState globalState) {
        super(globalState);
    }

    @Override
    protected Transaction generateTransaction() throws SQLException {
        return GaussDBMTransactionProvider.generateTransaction(globalState);
    }
}