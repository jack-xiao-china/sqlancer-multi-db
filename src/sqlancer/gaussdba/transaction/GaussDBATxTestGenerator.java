package sqlancer.gaussdba.transaction;

import java.sql.SQLException;

import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxTestGenerator;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.gen.transaction.GaussDBATransactionProvider;

public class GaussDBATxTestGenerator extends TxTestGenerator<GaussDBAGlobalState> {

    public GaussDBATxTestGenerator(GaussDBAGlobalState globalState) {
        super(globalState);
    }

    @Override
    protected Transaction generateTransaction() throws SQLException {
        return GaussDBATransactionProvider.generateTransaction(globalState);
    }
}