package sqlancer.cockroachdb.transaction;

import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxStatementType;

public class CockroachDBTxStatement extends TxStatement {

    public enum CockroachDBStatementType implements TxStatementType {
        BEGIN, COMMIT, ROLLBACK,
        SELECT,
        UPDATE, DELETE, INSERT, UPSERT,
        SET,
        UNKNOWN

    }

    public CockroachDBTxStatement(Transaction transaction, TxSQLQueryAdapter txQueryAdapter) {
        super(transaction, txQueryAdapter);
    }

    public CockroachDBTxStatement(Transaction transaction, SQLQueryAdapter queryAdapter) {
        this(transaction, new TxSQLQueryAdapter(queryAdapter));
    }

    @Override
    public boolean isSelectType() {
        return this.type == CockroachDBTxStatement.CockroachDBStatementType.SELECT;
    }

    @Override
    public boolean isNoErrorType() {
        return false;
    }

    @Override
    public boolean isEndTxType() {
        return this.type == CockroachDBStatementType.COMMIT || this.type == CockroachDBStatementType.ROLLBACK;
    }

    @Override
    protected void setStatementType() {
        String stmt = txQueryAdapter.getQueryString().replace(";", "").toUpperCase();
        CockroachDBTxStatement.CockroachDBStatementType realType = CockroachDBTxStatement.CockroachDBStatementType.valueOf(stmt.split(" ")[0]);
        this.type = realType;
    }

    @Override
    protected boolean reportDeadlock(String errorInfo) {
        if (errorInfo == null) {
            return false;
        }
        return errorInfo.contains("TransactionAbortedError") || errorInfo.contains("WriteTooOldError")
                || errorInfo.contains("RETRY_SERIALIZABLE");
    }

    @Override
    protected boolean reportRollback(String errorInfo) {
        if (errorInfo == null) {
            return false;
        }
        return errorInfo.contains("current transaction is aborted");
    }

}