package sqlancer.fucci.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.oracle.TxBase;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxStatement;
import sqlancer.fucci.FucciGlobalState;
import sqlancer.fucci.FucciIsolation.FucciIsolationLevel;
import sqlancer.fucci.FucciOptions.FucciOracleType;
import sqlancer.fucci.transaction.FucciTxTestGenerator;

/**
 * Fucci组合Oracle - 简化实现。
 */
public class FucciCompositeOracle extends TxBase<FucciGlobalState> {

    private FucciDTOracle dtOracle;
    private FucciMTOracle mtOracle;
    private FucciCSOracle csOracle;

    public FucciCompositeOracle(FucciGlobalState state) {
        super(state);
        initializeOracles(state);
    }

    private void initializeOracles(FucciGlobalState state) {
        FucciOracleType oracleType = state.getFucciOptions().getOracleType();

        if (oracleType == FucciOracleType.ALL || oracleType == FucciOracleType.DT) {
            dtOracle = new FucciDTOracle(state);
        }
        if (oracleType == FucciOracleType.ALL || oracleType == FucciOracleType.MT) {
            mtOracle = new FucciMTOracle(state);
        }
        if (oracleType == FucciOracleType.ALL || oracleType == FucciOracleType.CS) {
            csOracle = new FucciCSOracle(state);
        }
    }

    @Override
    public void check() throws SQLException {
        logger.writeCurrent("\n================= Fucci Composite Oracle Check =================");
        logger.writeCurrent("Oracle types enabled: " + getEnabledOracleTypes());

        FucciTxTestGenerator txTestGenerator = new FucciTxTestGenerator(state);
        List<Transaction> transactions = txTestGenerator.generateTransactions();

        for (Transaction tx : transactions) {
            logger.writeCurrent(tx.toString());
        }

        List<List<TxStatement>> schedules = txTestGenerator.genSchedules(transactions);

        try {
            for (List<TxStatement> schedule : schedules) {
                logger.writeCurrent("Input schedule: " + schedule.stream().map(TxStatement::getStmtId)
                        .collect(Collectors.joining(", ", "[", "]")));

                FucciIsolationLevel isoLevel = selectIsolationLevel();
                logger.writeCurrent("Isolation level: " + isoLevel);

                List<String> allViolations = new ArrayList<>();

                if (dtOracle != null) {
                    try {
                        dtOracle.check();
                    } catch (AssertionError e) {
                        allViolations.add("DT Oracle: " + e.getMessage());
                    }
                }

                if (mtOracle != null) {
                    try {
                        mtOracle.check();
                    } catch (AssertionError e) {
                        allViolations.add("MT Oracle: " + e.getMessage());
                    }
                }

                if (csOracle != null) {
                    try {
                        csOracle.check();
                    } catch (AssertionError e) {
                        allViolations.add("CS Oracle: " + e.getMessage());
                    }
                }

                if (!allViolations.isEmpty()) {
                    reportCompositeBug(transactions, schedule, allViolations);
                    throw new AssertionError("Fucci Composite Oracle detected violations");
                } else {
                    logger.writeCurrent("============ Composite Oracle: All Checks Passed =============");
                }

                reproduceDatabase(state.getState().getStatements());
            }
        } finally {
            for (Transaction tx : transactions) {
                tx.closeConnection();
            }
        }
    }

    private String getEnabledOracleTypes() {
        List<String> enabled = new ArrayList<>();
        if (dtOracle != null) enabled.add("DT");
        if (mtOracle != null) enabled.add("MT");
        if (csOracle != null) enabled.add("CS");
        return enabled.stream().collect(Collectors.joining(", "));
    }

    private FucciIsolationLevel selectIsolationLevel() {
        String isolationStr = state.getFucciOptions().getIsolationLevel();
        if (isolationStr == null || isolationStr.equalsIgnoreCase("RANDOM")) {
            return Randomly.fromList(state.getSupportedIsolationLevels());
        }
        return FucciIsolationLevel.fromString(isolationStr);
    }

    private void reportCompositeBug(List<Transaction> transactions, List<TxStatement> schedule,
            List<String> violations) {
        state.getState().getLocalState().log("============ Fucci Composite Oracle Bug Report =============");
        for (Transaction tx : transactions) {
            state.getState().getLocalState().log(tx.toString());
        }
        state.getState().getLocalState().log("Schedule: " + schedule.stream().map(TxStatement::getStmtId)
                .collect(Collectors.joining(", ", "[", "]")));
        for (String violation : violations) {
            state.getState().getLocalState().log(violation);
        }
    }
}