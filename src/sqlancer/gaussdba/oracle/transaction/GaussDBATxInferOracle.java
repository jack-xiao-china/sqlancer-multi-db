package sqlancer.gaussdba.oracle.transaction;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.oracle.TxBase;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxTestExecutionResult;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.transaction.GaussDBAIsolation.GaussDBAIsolationLevel;
import sqlancer.gaussdba.transaction.GaussDBATxTestExecutor;
import sqlancer.gaussdba.transaction.GaussDBATxTestGenerator;

/**
 * GaussDB-A TX_INFER Oracle.
 * Detects transaction isolation level bugs by inferring expected results
 * through MVCC version chain simulation using auxiliary tables.
 */
public class GaussDBATxInferOracle extends TxBase<GaussDBAGlobalState> {

    public GaussDBATxInferOracle(GaussDBAGlobalState state) {
        super(state);
    }

    @Override
    public void check() throws SQLException {
        logger.writeCurrent("\n================= Generate new transaction list =================");
        GaussDBATxTestGenerator txTestGenerator = new GaussDBATxTestGenerator(state);
        List<Transaction> transactions = txTestGenerator.generateTransactions();
        for (Transaction tx : transactions) {
            logger.writeCurrent(tx.toString());
        }
        List<List<TxStatement>> schedules = txTestGenerator.genSchedules(transactions);

        try {
            for (List<TxStatement> schedule : schedules) {
                logger.writeCurrent("Input schedule: " + schedule.stream().map(TxStatement::getStmtId).
                        collect(Collectors.joining(", ", "[", "]")));
                GaussDBAIsolationLevel isoLevel = Randomly.fromOptions(GaussDBAIsolationLevel.values());
                logger.writeCurrent("Isolation level: " + isoLevel);

                GaussDBATxTestExecutor testExecutor = new GaussDBATxTestExecutor(
                        state, transactions, schedule, isoLevel);
                TxTestExecutionResult testResult = testExecutor.execute();

                reproduceDatabase(state.getState().getStatements());
                GaussDBATxInfer infer = new GaussDBATxInfer(state, transactions, testResult, isoLevel);
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
                    throw new AssertionError(compareResultInfo);
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
