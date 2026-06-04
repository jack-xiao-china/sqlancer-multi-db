package sqlancer.mysql.oracle.transaction;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.oracle.TxBase;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxStatementExecutionResult;
import sqlancer.common.transaction.TxTestExecutionResult;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.transaction.MySQLIsolation.MySQLIsolationLevel;
import sqlancer.mysql.transaction.MySQLTxStatement;
import sqlancer.mysql.transaction.MySQLTxTestExecutor;
import sqlancer.mysql.transaction.MySQLTxTestGenerator;

public class MySQLWriteCheckOracle extends TxBase<MySQLGlobalState> {

    public MySQLWriteCheckOracle(MySQLGlobalState state) {
        super(state);
    }

    @Override
    public void check() throws SQLException {
        logger.writeCurrent("\n================= Generate new transaction list =================");
        MySQLTxTestGenerator txTestGenerator = new MySQLTxTestGenerator(state);
        List<Transaction> transactions = txTestGenerator.generateTransactions();
        for (Transaction tx : transactions) {
            logger.writeCurrent(tx.toString());
        }
        List<List<TxStatement>> schedules = txTestGenerator.genSchedules(transactions);

        try {
            for (List<TxStatement> schedule : schedules) {
                logger.writeCurrent("Input schedule: " + schedule.stream().map(TxStatement::getStmtId).
                        collect(Collectors.joining(", ", "[", "]")));
                MySQLIsolationLevel isoLevel = Randomly.fromOptions(MySQLIsolationLevel.values());
                logger.writeCurrent("Isolation level: " + isoLevel);
                MySQLTxTestExecutor testExecutor = new MySQLTxTestExecutor(state, transactions, schedule, isoLevel);

                TxTestExecutionResult testResult = testExecutor.execute();
                reproduceDatabase(state.getState().getStatements());

                List<TxStatement> oracleSchedule = genOracleSchedule(testResult);
                MySQLTxTestExecutor oracleExecutor = new MySQLTxTestExecutor(state, transactions, oracleSchedule, isoLevel);
                TxTestExecutionResult oracleResult = oracleExecutor.execute();
                reproduceDatabase(state.getState().getStatements());

                List<TxStatement> oracleWithoutCommitAndRollbackSchedule = genOracleWithoutCommitAndRollbackSchedule(testResult);
                MySQLTxTestExecutor oracleWithoutCommitAndRollbackExecutor = new MySQLTxTestExecutor(state, transactions,
                        oracleWithoutCommitAndRollbackSchedule, isoLevel);
                TxTestExecutionResult oracleWithoutCommitAndRollbackResult = oracleWithoutCommitAndRollbackExecutor.execute();
                reproduceDatabase(state.getState().getStatements());

                String compareResultInfo;
                if (isoLevel == MySQLIsolationLevel.SERIALIZABLE) {
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

                if (isoLevel == MySQLIsolationLevel.SERIALIZABLE) {
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
                    TxStatement rollbackStmt = new MySQLTxStatement(stmtTx, new TxSQLQueryAdapter("ROLLBACK"));
                    oracleSchedule.add(rollbackStmt);
                } else if (stmtResult.getStatement().getType() == MySQLTxStatement.MySQLStatementType.COMMIT
                        || stmtResult.getStatement().getType() == MySQLTxStatement.MySQLStatementType.ROLLBACK) {
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
                    && stmtResult.getStatement().getType() == MySQLTxStatement.MySQLStatementType.COMMIT) {
                List<TxStatement> txStatements = new ArrayList<>(stmtTx.getStatements());
                txStatements.remove(0);
                txStatements.remove(txStatements.size() - 1);
                oracleSchedule.addAll(txStatements);
            }
        }
        return oracleSchedule;
    }
}