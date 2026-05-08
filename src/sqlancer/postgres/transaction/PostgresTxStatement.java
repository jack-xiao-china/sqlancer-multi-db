package sqlancer.postgres.transaction;

import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxStatementType;

public class PostgresTxStatement extends TxStatement {

    public enum PostgresStatementType implements TxStatementType {
        BEGIN, COMMIT, ROLLBACK,
        SELECT, SELECT_FOR_SHARE, SELECT_FOR_UPDATE,
        UPDATE, DELETE, INSERT,
        SET,
        UNKNOWN
    }

    public PostgresTxStatement(Transaction transaction, TxSQLQueryAdapter txQueryAdapter) {
        super(transaction, txQueryAdapter);
    }

    public PostgresTxStatement(Transaction transaction, SQLQueryAdapter queryAdapter) {
        super(transaction, queryAdapter);
    }

    @Override
    public boolean isSelectType() {
        return this.type == PostgresStatementType.SELECT
                || this.type == PostgresStatementType.SELECT_FOR_SHARE
                || this.type == PostgresStatementType.SELECT_FOR_UPDATE;
    }

    @Override
    public boolean isNoErrorType() {
        return false;
    }

    @Override
    public boolean isEndTxType() {
        return this.type == PostgresStatementType.COMMIT || this.type == PostgresStatementType.ROLLBACK;
    }

    @Override
    protected void setStatementType() {
        String stmt = txQueryAdapter.getQueryString().replace(";", "").toUpperCase();
        PostgresStatementType realType;
        try {
            realType = PostgresStatementType.valueOf(stmt.split(" ")[0]);
        } catch (IllegalArgumentException e) {
            realType = PostgresStatementType.UNKNOWN;
        }
        if (realType == PostgresStatementType.SELECT) {
            int forIdx = stmt.indexOf("FOR ");
            if (forIdx != -1) {
                String postfix = stmt.substring(forIdx);
                if (postfix.equals("FOR SHARE")) {
                    realType = PostgresStatementType.SELECT_FOR_SHARE;
                } else if (postfix.equals("FOR UPDATE")) {
                    realType = PostgresStatementType.SELECT_FOR_UPDATE;
                }
            }
        }
        this.type = realType;
    }

    @Override
    protected boolean reportDeadlock(String errorInfo) {
        if (errorInfo == null) {
            return false;
        }
        return errorInfo.contains("deadlock") || errorInfo.contains("could not serialize");
    }

    @Override
    protected boolean reportRollback(String errorInfo) {
        if (errorInfo == null) {
            return false;
        }
        return errorInfo.contains("current transaction is aborted");
    }
}