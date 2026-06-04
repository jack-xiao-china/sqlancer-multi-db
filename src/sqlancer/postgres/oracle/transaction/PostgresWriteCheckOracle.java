package sqlancer.postgres.oracle.transaction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.common.oracle.TxBase;
import sqlancer.common.query.Query;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxStatementExecutionResult;
import sqlancer.common.transaction.TxTestExecutionResult;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresOptions;
import sqlancer.postgres.transaction.PostgresIsolation.PostgresIsolationLevel;
import sqlancer.postgres.transaction.PostgresTxStatement;
import sqlancer.postgres.transaction.PostgresTxTestExecutor;
import sqlancer.postgres.transaction.PostgresTxTestGenerator;
import sqlancer.postgres.gen.transaction.PostgresTransactionProvider;

public class PostgresWriteCheckOracle extends TxBase<PostgresGlobalState> {

    public PostgresWriteCheckOracle(PostgresGlobalState state) {
        super(state);
    }

    @Override
    public void check() throws SQLException {
        List<Query<?>> dbQueries = reviseDBQueries();

        logger.writeCurrent("\n================= Generate new transaction list =================");
        PostgresTxTestGenerator txTestGenerator = new PostgresTxTestGenerator(state);
        List<Transaction> transactions = txTestGenerator.generateTransactions();
        for (Transaction tx : transactions) {
            logger.writeCurrent(tx.toString());
        }
        List<List<TxStatement>> schedules = txTestGenerator.genSchedules(transactions);

        try {
            for (List<TxStatement> schedule : schedules) {
                logger.writeCurrent("Input schedule: " + schedule.stream().map(TxStatement::getStmtId).
                        collect(Collectors.joining(", ", "[", "]")));
                PostgresIsolationLevel isoLevel = Randomly.fromOptions(PostgresIsolationLevel.values());
                logger.writeCurrent("Isolation level: " + isoLevel);
                PostgresTxTestExecutor testExecutor = new PostgresTxTestExecutor(state, transactions, schedule, isoLevel);

                TxTestExecutionResult testResult = testExecutor.execute();
                recreateDatabase(transactions);
                reproduceDatabase(dbQueries);

                List<TxStatement> oracleSchedule = genOracleSchedule(testResult);
                PostgresTxTestExecutor oracleExecutor = new PostgresTxTestExecutor(state, transactions, oracleSchedule, isoLevel);
                TxTestExecutionResult oracleResult = oracleExecutor.execute();
                recreateDatabase(transactions);
                reproduceDatabase(dbQueries);

                List<TxStatement> oracleWithoutCommitAndRollbackSchedule = genOracleWithoutCommitAndRollbackSchedule(testResult);
                PostgresTxTestExecutor oracleWithoutCommitAndRollbackExecutor = new PostgresTxTestExecutor(state, transactions,
                        oracleWithoutCommitAndRollbackSchedule, isoLevel);
                TxTestExecutionResult oracleWithoutCommitAndRollbackResult = oracleWithoutCommitAndRollbackExecutor.execute();
                recreateDatabase(transactions);
                reproduceDatabase(dbQueries);

                String compareResultInfo;
                if (isoLevel == PostgresIsolationLevel.SERIALIZABLE) {
                    compareResultInfo = compareAllResults(testResult, oracleResult);
                } else {
                    compareResultInfo = compareWriteTxResults(testResult, oracleResult);
                }
                if (!compareResultInfo.equals("")) {
                    state.getState().getLocalState().log("============Bug Report============");
                    state.getState().getLocalState().log("==Oracle With Commit And Rollback==");
                    for (Transaction tx : transactions) {
                        state.getState().getLocalState().log(tx.toString());
                    }
                    state.getState().getLocalState().log("Input schedule: " + schedule.stream().map(TxStatement::getStmtId).
                            collect(Collectors.joining(", ", "[", "]")));
                    state.getState().getLocalState().log(compareResultInfo);
                    state.getState().getLocalState().log("Execution Result:");
                    state.getState().getLocalState().log(testResult.toString());
                    state.getState().getLocalState().log("Oracle Result:");
                    state.getState().getLocalState().log(oracleResult.toString());
                    throw new AssertionError(compareResultInfo);
                } else {
                    state.getLogger().writeCurrent("============Is Same============");
                }

                if (isoLevel == PostgresIsolationLevel.SERIALIZABLE) {
                    compareResultInfo = compareAllResults(testResult, oracleWithoutCommitAndRollbackResult);
                } else {
                    compareResultInfo = compareWriteTxResults(testResult, oracleWithoutCommitAndRollbackResult);
                }
                if (!compareResultInfo.equals("")) {
                    state.getState().getLocalState().log("============Bug Report============");
                    state.getState().getLocalState().log("==Oracle Without Commit And Rollback==");
                    for (Transaction tx : transactions) {
                        state.getState().getLocalState().log(tx.toString());
                    }
                    state.getState().getLocalState().log("Input schedule: " + schedule.stream().map(TxStatement::getStmtId).
                            collect(Collectors.joining(", ", "[", "]")));
                    state.getState().getLocalState().log(compareResultInfo);
                    state.getState().getLocalState().log("Execution Result:");
                    state.getState().getLocalState().log(testResult.toString());
                    state.getState().getLocalState().log("Oracle Result:");
                    state.getState().getLocalState().log(oracleWithoutCommitAndRollbackResult.toString());
                    throw new AssertionError(compareResultInfo);
                } else {
                    state.getLogger().writeCurrent("============Is Same============");
                }
            }
        } finally {
            for (Transaction tx : transactions) {
                tx.closeConnection();
            }
        }
    }

    public List<TxStatement> genOracleSchedule(TxTestExecutionResult testResult) {
        List<TxStatement> oracleSchedule = new ArrayList<>();
        for (TxStatementExecutionResult stmtResult : testResult.getStatementExecutionResults()) {
            Transaction stmtTx = stmtResult.getStatement().getTransaction();
            if (!stmtResult.isBlocked()) {
                if (stmtResult.reportDeadlock() || stmtResult.reportRollback()) {
                    for (TxStatement stmt : stmtTx.getStatements()) {
                        if (!stmt.isEndTxType()) {
                            oracleSchedule.add(stmt);
                        }
                        if (stmtResult.getStatement().equals(stmt)) {
                            break;
                        }
                    }
                    TxStatement rollbackStmt = new PostgresTxStatement(stmtTx, new TxSQLQueryAdapter("ROLLBACK"));
                    oracleSchedule.add(rollbackStmt);
                } else if (stmtResult.getStatement().getType() == PostgresTxStatement.PostgresStatementType.COMMIT
                        || stmtResult.getStatement().getType() == PostgresTxStatement.PostgresStatementType.ROLLBACK) {
                    oracleSchedule.addAll(stmtTx.getStatements());
                }
            }
        }
        return oracleSchedule;
    }

    public List<TxStatement> genOracleWithoutCommitAndRollbackSchedule(TxTestExecutionResult testResult) {
        List<TxStatement> oracleSchedule = new ArrayList<>();
        for (TxStatementExecutionResult stmtResult : testResult.getStatementExecutionResults()) {
            Transaction stmtTx = stmtResult.getStatement().getTransaction();
            if (!stmtResult.isBlocked() && !stmtResult.reportDeadlock()
                    && stmtResult.getStatement().getType() == PostgresTxStatement.PostgresStatementType.COMMIT) {
                List<TxStatement> txStatements = new ArrayList<>(stmtTx.getStatements());
                txStatements.remove(0);
                txStatements.remove(txStatements.size() - 1);
                oracleSchedule.addAll(txStatements);
            }
        }
        return oracleSchedule;
    }

    protected void recreateDatabase(List<Transaction> transactions) throws SQLException {
        for (Transaction tx : transactions) {
            tx.closeConnection();
        }
        state.getConnection().close();
        String databaseName = state.getDatabaseName();
        String username = state.getOptions().getUserName();
        String password = state.getOptions().getPassword();
        String host = state.getOptions().getHost();
        int port = state.getOptions().getPort();
        if (host == null) {
            host = PostgresOptions.DEFAULT_HOST;
        }
        if (port == MainOptions.NO_SET_PORT) {
            port = PostgresOptions.DEFAULT_PORT;
        }
        String entryURL = String.format("postgresql://%s:%d/test", host, port);
        Connection con = DriverManager.getConnection("jdbc:" + entryURL, username, password);
        try (Statement s = con.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + databaseName);
        }
        try (Statement s = con.createStatement()) {
            s.execute("CREATE DATABASE " + databaseName);
        }
        con.close();
        entryURL = String.format("postgresql://%s:%d/%s", host, port, databaseName);
        con = DriverManager.getConnection("jdbc:" + entryURL, username, password);
        SQLConnection newConnection = new SQLConnection(con);
        state.setConnection(newConnection);
        for (Transaction tx : transactions) {
            SQLConnection txCon = PostgresTransactionProvider.createNewConnection(state);
            tx.setConnection(txCon);
        }
    }

    protected List<Query<?>> reviseDBQueries() {
        List<Query<?>> dbInitQueries = state.getState().getStatements();
        List<Query<?>> dbQueries = new ArrayList<>();
        int i = 0;
        for (Query<?> query : dbInitQueries) {
            i++;
            if (i < 5) {
                continue;
            }
            dbQueries.add(query);
        }
        return dbQueries;
    }
}