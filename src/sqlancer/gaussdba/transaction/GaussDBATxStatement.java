package sqlancer.gaussdba.transaction;

import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxStatementType;

public class GaussDBATxStatement extends TxStatement {

    public enum GaussDBAStatementType implements TxStatementType {
        BEGIN, COMMIT, ROLLBACK,
        SELECT, SELECT_FOR_SHARE, SELECT_FOR_UPDATE,
        UPDATE, DELETE, INSERT,
        SET,
        UNKNOWN
    }

    public GaussDBATxStatement(Transaction transaction, TxSQLQueryAdapter txQueryAdapter) {
        super(transaction, txQueryAdapter);
    }

    public GaussDBATxStatement(Transaction transaction, SQLQueryAdapter queryAdapter) {
        super(transaction, queryAdapter);
    }

    @Override
    public boolean isSelectType() {
        return this.type == GaussDBAStatementType.SELECT
                || this.type == GaussDBAStatementType.SELECT_FOR_SHARE
                || this.type == GaussDBAStatementType.SELECT_FOR_UPDATE;
    }

    @Override
    public boolean isNoErrorType() {
        return false;
    }

    @Override
    public boolean isEndTxType() {
        return this.type == GaussDBAStatementType.COMMIT || this.type == GaussDBAStatementType.ROLLBACK;
    }

    @Override
    protected void setStatementType() {
        String stmt = txQueryAdapter.getQueryString().replace(";", "").toUpperCase();
        GaussDBAStatementType realType;
        try {
            realType = GaussDBAStatementType.valueOf(stmt.split(" ")[0]);
        } catch (IllegalArgumentException e) {
            realType = GaussDBAStatementType.UNKNOWN;
        }
        if (realType == GaussDBAStatementType.SELECT) {
            int forIdx = stmt.indexOf("FOR ");
            if (forIdx != -1) {
                String postfix = stmt.substring(forIdx);
                if (postfix.equals("FOR SHARE")) {
                    realType = GaussDBAStatementType.SELECT_FOR_SHARE;
                } else if (postfix.equals("FOR UPDATE")) {
                    realType = GaussDBAStatementType.SELECT_FOR_UPDATE;
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