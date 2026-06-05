package sqlancer.common.transaction;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import sqlancer.SQLGlobalState;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.common.schema.AbstractTable;

public abstract class TxTestExecutor<S extends SQLGlobalState<?, ?>> {
    protected static final int WAIT_THRESHOLD = 2;
    private final long awaitTime = 5 * 1000;
    protected final S globalState;
    protected final List<Transaction> transactions;
    protected final List<TxStatement> schedule;
    protected final IsolationLevel isoLevel;

    public TxTestExecutor(S globalState, List<Transaction> transactions, List<TxStatement> schedule,
                          IsolationLevel isoLevel) {
        this.globalState = globalState;
        this.transactions = transactions;
        this.schedule = schedule;
        this.isoLevel = isoLevel;
    }

    public TxTestExecutionResult execute() throws SQLException {
        ExecutorService executor = Executors.newFixedThreadPool(transactions.size());
        Map<Transaction, Future<TxStatementExecutionResult>> blockedTxs = new HashMap<>();
        List<Integer> rollbackTxs = new ArrayList<>();

        List<TxStatement> submittedStmts = new ArrayList<>();
        List<TxStatementExecutionResult> stmtExecutionResults = new ArrayList<>();

        generateIsolationLevel();

        setTimeout();

        while (hasStmtToSchedule(submittedStmts, rollbackTxs)) {
            for (TxStatement curStmt : schedule) {
                if (submittedStmts.contains(curStmt)) {
                    continue;
                }

                Transaction curTx = curStmt.getTransaction();
                if (blockedTxs.containsKey(curTx)) {
                    continue;
                }

                if (rollbackTxs.contains(curTx.getId())) {
                    continue;
                }

                submittedStmts.add(curStmt);
                Future<TxStatementExecutionResult> stmtFuture = executor.submit(new TxStmtExecutor(curStmt));
                TxStatementExecutionResult stmtResult;
                try {
                    stmtResult = stmtFuture.get(WAIT_THRESHOLD, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    blockedTxs.put(curTx, stmtFuture);
                    continue;
                } catch (ExecutionException e) {
                    // Handle execution exception - check if it's an AssertionError from expected errors
                    Throwable cause = e.getCause();
                    if (cause instanceof AssertionError) {
                        // Expected errors not matched - create error result
                        stmtResult = new TxStatementExecutionResult(curStmt);
                        stmtResult.setErrorInfo(cause.getMessage());
                    } else {
                        throw new RuntimeException("Transaction statement returning result exception: ", e);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException("Transaction statement interrupted exception: ", e);
                }

                if (stmtResult != null) {
                    stmtExecutionResults.add(stmtResult);
                    postStatementComplete(curStmt);
                    if(stmtResult.reportDeadlock() || stmtResult.reportRollback()) {
                        handleAbortedTxn(stmtResult.getStatement().getTransaction());
                        rollbackTxs.add(stmtResult.getStatement().getTransaction().getId());
                    }

                    boolean hasResumedTxs = false;
                    Iterator<Transaction> txIterator = blockedTxs.keySet().iterator();
                    while (txIterator.hasNext()) {
                        Transaction blockedTx = txIterator.next();
                        Future<TxStatementExecutionResult> blockedStmtFuture = blockedTxs.get(blockedTx);
                        TxStatementExecutionResult blockedStmtResult = null;
                        try {
                            blockedStmtResult = blockedStmtFuture.get(WAIT_THRESHOLD, TimeUnit.SECONDS);
                        } catch (TimeoutException e) {
                            // ignore
                        } catch (ExecutionException e) {
                            Throwable cause = e.getCause();
                            if (cause instanceof AssertionError) {
                                blockedStmtResult = new TxStatementExecutionResult(blockedTx.getStatements().get(0));
                                blockedStmtResult.setErrorInfo(cause.getMessage());
                            } else {
                                throw new RuntimeException("Transaction blocked statement returning result exception: ", e);
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException("Transaction blocked statement interrupted exception: ", e);
                        }

                        if (blockedStmtResult != null) {
                            hasResumedTxs = true;
                            txIterator.remove();
                            stmtExecutionResults.add(blockedStmtResult);
                            postStatementComplete(blockedTx.getStatements().get(0));
                            if(blockedStmtResult.reportDeadlock() || blockedStmtResult.reportRollback()) {
                                handleAbortedTxn(blockedStmtResult.getStatement().getTransaction());
                                rollbackTxs.add(blockedStmtResult.getStatement().getTransaction().getId());
                            }
                        }
                    }
                    if (hasResumedTxs) {
                        break;
                    }
                }
            }
        }

        TxTestExecutionResult txResult = new TxTestExecutionResult();
        txResult.setIsolationLevel(isoLevel);
        txResult.setStatementExecutionResults(stmtExecutionResults);
        txResult.setDbFinalStates(getDBState());

        try {
            executor.shutdown();
            if(!executor.awaitTermination(awaitTime, TimeUnit.MILLISECONDS)){
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        return txResult;
    }

    protected class TxStmtExecutor implements Callable<TxStatementExecutionResult> {
        private TxStatement txStmt;

        public TxStmtExecutor(TxStatement txStmt) {
            this.txStmt = txStmt;
        }

        @Override
        public TxStatementExecutionResult call() {
            try {
                TxStatementExecutionResult stmtResult = new TxStatementExecutionResult(txStmt);
                if (txStmt.isSelectType()) {
                    SQLancerResultSet queryResult = null;
                    try {
                        queryResult = txStmt.getTxQueryAdapter().executeAndGet(txStmt.getTransaction());
                    } catch (SQLException e) {
                        stmtResult.setErrorInfo(e.getMessage());
                    } catch (AssertionError e) {
                        // Expected errors not matched - treat as execution error
                        stmtResult.setErrorInfo(e.getMessage());
                    }
                    stmtResult.setResult(QueryResultUtil.getQueryResult(queryResult));
                } else {
                    try {
                        txStmt.getTxQueryAdapter().execute(txStmt.getTransaction());
                    } catch (SQLException e) {
                        stmtResult.setErrorInfo(e.getMessage());
                    } catch (AssertionError e) {
                        // Expected errors not matched - treat as execution error
                        stmtResult.setErrorInfo(e.getMessage());
                    }
                }
                SQLancerResultSet warningResult = showWarnings(txStmt.getTransaction());
                stmtResult.setWarningInfo(QueryResultUtil.getQueryResult(warningResult));
                return stmtResult;
            } catch (SQLException e) {
                throw new RuntimeException("Transaction statement execution exception: ", e);
            }
        }
    }

    protected abstract void generateIsolationLevel() throws SQLException;

    protected abstract void setTimeout() throws SQLException;

    protected abstract void handleAbortedTxn(Transaction transaction) throws SQLException;

    protected abstract SQLancerResultSet showWarnings(Transaction transaction) throws SQLException;

    /**
     * Hook called after a statement completes successfully.
     * Override in subclasses to perform analysis (lock tracking, MVCC updates, etc.).
     *
     * @param stmt the completed statement
     */
    protected void postStatementComplete(TxStatement stmt) {
        // default: no-op
    }

    private boolean hasStmtToSchedule(List<TxStatement> submittedStmts, List<Integer> rollbackTxs) {
        for (TxStatement stmt : schedule) {
            Transaction curTx = stmt.getTransaction();
            if (submittedStmts.contains(stmt)) {
                continue;
            } else if (rollbackTxs.contains(curTx.getId())) {
                continue;
            } else {
                return true;
            }
        }
        return false;
    }

    protected Map<String, List<Object>> getDBState() throws SQLException {
        Map<String, List<Object>> dbStates = new HashMap<>();
        for (AbstractTable<?, ?, ?> table : globalState.getSchema().getDatabaseTables()) {
            String query = "SELECT * FROM " + table.getName();
            SQLQueryAdapter sql = new SQLQueryAdapter(query);
            SQLancerResultSet tableState = sql.executeAndGet(globalState);
            dbStates.put(table.getName(), QueryResultUtil.getQueryResult(tableState));
        }
        return dbStates;
    }
}