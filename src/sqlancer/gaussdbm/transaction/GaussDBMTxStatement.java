package sqlancer.gaussdbm.transaction;

import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxStatementType;

public class GaussDBMTxStatement extends TxStatement {

    public enum GaussDBMStatementType implements TxStatementType {
        BEGIN, COMMIT, ROLLBACK,
        SELECT, SELECT_FOR_SHARE, SELECT_FOR_UPDATE,
        UPDATE, DELETE, INSERT, REPLACE,
        SET,
        UNKNOWN
    }

    public GaussDBMTxStatement(Transaction transaction, TxSQLQueryAdapter txQueryAdapter) {
        super(transaction, txQueryAdapter);
    }

    public GaussDBMTxStatement(Transaction transaction, SQLQueryAdapter queryAdapter) {
        super(transaction, queryAdapter);
    }

    @Override
    public boolean isSelectType() {
        return this.type == GaussDBMStatementType.SELECT
                || this.type == GaussDBMStatementType.SELECT_FOR_SHARE
                || this.type == GaussDBMStatementType.SELECT_FOR_UPDATE;
    }

    @Override
    public boolean isNoErrorType() {
        return false;
    }

    @Override
    public boolean isEndTxType() {
        return this.type == GaussDBMStatementType.COMMIT || this.type == GaussDBMStatementType.ROLLBACK;
    }

    @Override
    protected void setStatementType() {
        String stmt = txQueryAdapter.getQueryString().replace(";", "").toUpperCase();
        GaussDBMStatementType realType;
        try {
            realType = GaussDBMStatementType.valueOf(stmt.split(" ")[0]);
        } catch (IllegalArgumentException e) {
            realType = GaussDBMStatementType.UNKNOWN;
        }
        if (realType == GaussDBMStatementType.SELECT) {
            int forIdx = stmt.indexOf("FOR ");
            if (forIdx != -1) {
                String postfix = stmt.substring(forIdx);
                if (postfix.contains("FOR SHARE") || postfix.contains("LOCK IN SHARE MODE")) {
                    realType = GaussDBMStatementType.SELECT_FOR_SHARE;
                } else if (postfix.contains("FOR UPDATE")) {
                    realType = GaussDBMStatementType.SELECT_FOR_UPDATE;
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
        return errorInfo.contains("deadlock") || errorInfo.contains("Deadlock");
    }

    @Override
    protected boolean reportRollback(String errorInfo) {
        if (errorInfo == null) {
            return false;
        }
        return errorInfo.contains("rollback") || errorInfo.contains("Rollback");
    }
}