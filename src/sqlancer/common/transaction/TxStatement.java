package sqlancer.common.transaction;

import sqlancer.common.query.SQLQueryAdapter;

public abstract class TxStatement {
    protected Transaction transaction;
    protected TxSQLQueryAdapter txQueryAdapter;
    protected TxStatementType type;

    public TxStatement(Transaction transaction, TxSQLQueryAdapter txQueryAdapter) {
        this.transaction = transaction;
        this.txQueryAdapter = txQueryAdapter;
        setStatementType();
    }

    public TxStatement(Transaction transaction, SQLQueryAdapter queryAdapter) {
        this(transaction, new TxSQLQueryAdapter(queryAdapter));
    }

    /**
     * Indicates whether the statement is a SELECT or SELECT FOR UPDATE
     */
    public abstract boolean isSelectType();

    /**
     * Indicates whether the statement is a BEGIN, COMMIT or ROLLBACK that should not report error
     * Avoid MariaDB Bug https://jira.mariadb.org/browse/MDEV-30793
     */
    public abstract boolean isNoErrorType();

    public abstract boolean isEndTxType();

    protected abstract void setStatementType();

    protected abstract boolean reportDeadlock(String errorInfo);

    protected abstract boolean reportRollback(String errorInfo);

    public Transaction getTransaction() {
        return transaction;
    }

    public TxSQLQueryAdapter getTxQueryAdapter() {
        return txQueryAdapter;
    }

    public void setTxSQLQueryAdapter(TxSQLQueryAdapter txSQLQueryAdapter) {
        this.txQueryAdapter = txSQLQueryAdapter;
    }

    public TxStatementType getType() {
        return type;
    }

    public String getStmtId() {
        return String.format("%d-%d", transaction.getId(), transaction.getStatements().indexOf(this));
    }

    @Override
    public String toString() {
        return String.format("%d-%d: %s", transaction.getId(), transaction.getStatements().indexOf(this),
                txQueryAdapter.getQueryString());
    }
}