package sqlancer.gaussdbm.oracle.transaction;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.oracle.TxBase;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxTestExecutionResult;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.transaction.GaussDBMIsolation.GaussDBMIsolationLevel;
import sqlancer.gaussdbm.transaction.GaussDBMTxTestExecutor;
import sqlancer.gaussdbm.transaction.GaussDBMTxTestGenerator;

/**
 * GaussDB-M TX_INFER Oracle.
 * Inherits MySQL implementation since GaussDB-M uses MySQL-compatible mode.
 */
public class GaussDBMTxInferOracle extends TxBase<GaussDBMGlobalState> {

    public GaussDBMTxInferOracle(GaussDBMGlobalState state) {
        super(state);
    }

    @Override
    public void check() throws SQLException {
        logger.writeCurrent("\n================= Generate new transaction list =================");
        GaussDBMTxTestGenerator txTestGenerator = new GaussDBMTxTestGenerator(state);
        List<Transaction> transactions = txTestGenerator.generateTransactions();
        for (Transaction tx : transactions) {
            logger.writeCurrent(tx.toString());
        }
        List<List<TxStatement>> schedules = txTestGenerator.genSchedules(transactions);

        try {
            for (List<TxStatement> schedule : schedules) {
                logger.writeCurrent("Input schedule: " + schedule.stream().map(TxStatement::getStmtId).
                        collect(Collectors.joining(", ", "[", "]")));
                GaussDBMIsolationLevel isoLevel = Randomly.fromOptions(GaussDBMIsolationLevel.values());
                logger.writeCurrent("Isolation level: " + isoLevel);

                GaussDBMTxTestExecutor testExecutor = new GaussDBMTxTestExecutor(state, transactions, schedule, isoLevel);
                TxTestExecutionResult testResult = testExecutor.execute();

                reproduceDatabase(state.getState().getStatements());
                GaussDBMTxInfer infer = new GaussDBMTxInfer(state, transactions, testResult, isoLevel);
                TxTestExecutionResult oracleResult = infer.inferOracle();

                String compareResultInfo = compareAllResults(testResult, oracleResult);
                if (compareResultInfo.equals("")) {
                    state.getLogger().writeCurrent("============Is Same============");
                } else {
                    state.getState().getLocalState().log("============Bug Report============");
                    for (Transaction tx : transactions) {
                        state.getState().getLocalState().log(tx.toString());
                    }
                    state.getState().getLocalState().log("Input schedule: " + schedule.stream().map(TxStatement::getStmtId).
                            collect(Collectors.joining(", ", "[", "]")));
                    state.getState().getLocalState().log("Isolation level: " + isoLevel);
                    state.getState().getLocalState().log(compareResultInfo);
                    state.getState().getLocalState().log("Execution Result:");
                    state.getState().getLocalState().log(testResult.toString());
                    state.getState().getLocalState().log("Oracle Result:");
                    state.getState().getLocalState().log(oracleResult.toString());
                    throw new AssertionError("Transaction execution mismatches its oracle");
                }

                reproduceDatabase(state.getState().getStatements());
            }
        } finally {
            for (Transaction tx : transactions) {
                tx.closeConnection();
            }
        }
    }
}