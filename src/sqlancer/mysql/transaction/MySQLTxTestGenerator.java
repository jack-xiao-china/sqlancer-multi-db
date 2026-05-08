package sqlancer.mysql.transaction;

import java.sql.SQLException;

import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxTestGenerator;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.gen.transaction.MySQLTransactionProvider;

public class MySQLTxTestGenerator extends TxTestGenerator<MySQLGlobalState> {

    public MySQLTxTestGenerator(MySQLGlobalState globalState) {
        super(globalState);
    }

    @Override
    protected Transaction generateTransaction() throws SQLException {
        return MySQLTransactionProvider.generateTransaction(globalState);
    }
}