package sqlancer.mysql.transaction;

import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxStatementType;

public class MySQLTxStatement extends TxStatement {

    public enum MySQLStatementType implements TxStatementType {
        BEGIN, COMMIT, ROLLBACK,
        SELECT, SELECT_FOR_SHARE, SELECT_FOR_UPDATE,
        UPDATE, DELETE, INSERT, REPLACE,
        SET,
        UNKNOWN

    }

    public MySQLTxStatement(Transaction transaction, TxSQLQueryAdapter queryAdapter) {
        super(transaction, queryAdapter);
    }

    public MySQLTxStatement(Transaction transaction, SQLQueryAdapter queryAdapter) {
        this(transaction, new TxSQLQueryAdapter(queryAdapter));
    }

    @Override
    public boolean isSelectType() {
        return this.type == MySQLStatementType.SELECT
                || this.type == MySQLStatementType.SELECT_FOR_SHARE
                || this.type == MySQLStatementType.SELECT_FOR_UPDATE;
    }

    @Override
    public boolean isNoErrorType() {
        return false;
    }

    @Override
    public boolean isEndTxType() {
        return this.type == MySQLStatementType.COMMIT || this.type == MySQLStatementType.ROLLBACK;
    }

    @Override
    protected void setStatementType() {
        String stmt = txQueryAdapter.getQueryString().replace(";", "").toUpperCase();
        MySQLStatementType realType;
        try {
            realType = MySQLStatementType.valueOf(stmt.split(" ")[0]);
        } catch (IllegalArgumentException e) {
            realType = MySQLStatementType.UNKNOWN;
        }
        if (realType == MySQLStatementType.SELECT) {
            int forIdx = stmt.indexOf("FOR ");
            if (forIdx == -1) {
                forIdx = stmt.indexOf("LOCK IN SHARE MODE");
            }
            if (forIdx != -1) {
                String postfix = stmt.substring(forIdx);
                if (postfix.equals("FOR SHARE") || postfix.equals("LOCK IN SHARE MODE")) {
                    realType = MySQLStatementType.SELECT_FOR_SHARE;
                } else if (postfix.equals("FOR UPDATE")) {
                    realType = MySQLStatementType.SELECT_FOR_UPDATE;
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
        return errorInfo.contains("Deadlock") || errorInfo.contains("Lock wait timeout exceeded");
    }

    @Override
    protected boolean reportRollback(String errorInfo) {
        return false;
    }
}