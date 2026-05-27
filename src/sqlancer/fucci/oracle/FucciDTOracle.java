package sqlancer.fucci.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.common.oracle.TxBase;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxTestExecutionResult;
import sqlancer.fucci.FucciGlobalState;
import sqlancer.fucci.FucciIsolation.FucciIsolationLevel;
import sqlancer.fucci.bridge.DBMSFucciAdapter;
import sqlancer.fucci.transaction.FucciTxTestExecutor;
import sqlancer.fucci.transaction.FucciTxTestGenerator;

/**
 * Fucci DT Oracle (差异测试Oracle) - 简化实现。
 */
@SuppressWarnings("unused")
public class FucciDTOracle extends TxBase<FucciGlobalState> {

    private SQLConnection refConnection;
    private DBMSFucciAdapter adapter;

    public FucciDTOracle(FucciGlobalState state) {
        super(state);
        this.adapter = state.getFucciAdapter();
    }

    @Override
    public void check() throws SQLException {
        logger.writeCurrent("\n================= Fucci DT Oracle Check =================");

        if (!state.getFucciOptions().hasRefDbConfig()) {
            logger.writeCurrent("DT Oracle requires reference database configuration.");
            return;
        }

        try {
            FucciTxTestGenerator txTestGenerator = new FucciTxTestGenerator(state);
            List<Transaction> transactions = txTestGenerator.generateTransactions();

            for (Transaction tx : transactions) {
                logger.writeCurrent(tx.toString());
            }

            List<List<TxStatement>> schedules = txTestGenerator.genSchedules(transactions);

            for (List<TxStatement> schedule : schedules) {
                logger.writeCurrent("Input schedule: " + schedule.stream().map(TxStatement::getStmtId)
                        .collect(Collectors.joining(", ", "[", "]")));

                FucciIsolationLevel isoLevel = selectIsolationLevel();
                logger.writeCurrent("Isolation level: " + isoLevel);

                FucciTxTestExecutor testExecutor = new FucciTxTestExecutor(state, transactions, schedule, isoLevel);
                TxTestExecutionResult testResult = testExecutor.execute();

                reproduceDatabase(state.getState().getStatements());

                TxTestExecutionResult refResult = new TxTestExecutionResult();

                String compareResult = compareResults(testResult, refResult, isoLevel);

                if (!compareResult.isEmpty()) {
                    reportBug(transactions, schedule, compareResult, testResult, refResult);
                    throw new AssertionError("DT Oracle detected inconsistency");
                } else {
                    logger.writeCurrent("============ DT Oracle: Results Match =============");
                }

                reproduceDatabase(state.getState().getStatements());
            }
        } finally {
            if (refConnection != null) {
                refConnection.close();
            }
            for (Transaction tx : new ArrayList<Transaction>()) {
                tx.closeConnection();
            }
        }
    }

    private FucciIsolationLevel selectIsolationLevel() {
        String isolationStr = state.getFucciOptions().getIsolationLevel();
        if (isolationStr == null || isolationStr.equalsIgnoreCase("RANDOM")) {
            return Randomly.fromList(state.getSupportedIsolationLevels());
        }
        return FucciIsolationLevel.fromString(isolationStr);
    }

    private TxTestExecutionResult executeOnRefDatabase(List<Transaction> transactions,
            List<TxStatement> schedule, FucciIsolationLevel isoLevel) throws SQLException {
        logger.writeCurrent("Executing on reference database...");
        return new TxTestExecutionResult();
    }

    private String compareResults(TxTestExecutionResult testResult, TxTestExecutionResult refResult,
            FucciIsolationLevel isoLevel) {
        if (isoLevel == FucciIsolationLevel.SERIALIZABLE) {
            return compareAllResults(testResult, refResult);
        } else {
            return compareWriteTxResults(testResult, refResult);
        }
    }

    private void reportBug(List<Transaction> transactions, List<TxStatement> schedule,
            String compareResult, TxTestExecutionResult testResult, TxTestExecutionResult refResult) {
        state.getState().getLocalState().log("============ Fucci DT Oracle Bug Report =============");
        for (Transaction tx : transactions) {
            state.getState().getLocalState().log(tx.toString());
        }
        state.getState().getLocalState().log("Schedule: " + schedule.stream().map(TxStatement::getStmtId)
                .collect(Collectors.joining(", ", "[", "]")));
        state.getState().getLocalState().log(compareResult);
    }
}