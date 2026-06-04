package sqlancer.fucci.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.oracle.TxBase;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxStatementExecutionResult;
import sqlancer.common.transaction.TxTestExecutionResult;
import sqlancer.fucci.FucciGlobalState;
import sqlancer.fucci.FucciIsolation.FucciIsolationLevel;
import sqlancer.fucci.FucciTable;
import sqlancer.fucci.mvcc.MVCCSimulator;
import sqlancer.fucci.mvcc.Version;
import sqlancer.fucci.mvcc.VisibilityRule;
import sqlancer.fucci.transaction.FucciTransaction;
import sqlancer.fucci.transaction.FucciTxStatement;
import sqlancer.fucci.transaction.FucciTxTestExecutor;
import sqlancer.fucci.transaction.FucciTxTestGenerator;

/**
 * Fucci MT Oracle (变形测试Oracle) - 简化实现。
 */
public class FucciMTOracle extends TxBase<FucciGlobalState> {

    private Map<String, Map<Integer, List<Version>>> versionChains;
    private List<FucciTransaction> activeTransactions;
    private MVCCSimulator mvccSimulator;

    public FucciMTOracle(FucciGlobalState state) {
        super(state);
        this.versionChains = new HashMap<>();
        this.activeTransactions = new ArrayList<>();
    }

    @Override
    public void check() throws SQLException {
        logger.writeCurrent("\n================= Fucci MT Oracle Check =================");
        initializeVersionChains();

        try {
            FucciTxTestGenerator txTestGenerator = new FucciTxTestGenerator(state);
            List<Transaction> transactions = txTestGenerator.generateTransactions();

            for (Transaction tx : transactions) {
                if (tx instanceof FucciTransaction) {
                    activeTransactions.add((FucciTransaction) tx);
                }
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

                TxTestExecutionResult simulatedResult = simulateMVCCExecution(transactions, schedule, isoLevel);
                String compareResult = compareWithSimulation(testResult, simulatedResult, isoLevel);

                if (!compareResult.isEmpty()) {
                    reportBug(transactions, schedule, compareResult, testResult, simulatedResult);
                    throw new AssertionError("MT Oracle detected MVCC visibility violation");
                } else {
                    logger.writeCurrent("============ MT Oracle: MVCC Simulation Matches =============");
                }

                reproduceDatabase(state.getState().getStatements());
                resetVersionChains();
            }
        } finally {
            for (Transaction tx : new ArrayList<Transaction>(activeTransactions)) {
                tx.closeConnection();
            }
            activeTransactions.clear();
        }
    }

    private void initializeVersionChains() throws SQLException {
        versionChains.clear();
        List<FucciTable> tables = state.getSchema().getDatabaseTables();
        for (FucciTable table : tables) {
            String tableName = table.getName();
            Map<Integer, List<Version>> tableVersions = new HashMap<>();

            String query = "SELECT * FROM " + tableName;
            SQLQueryAdapter sql = new SQLQueryAdapter(query);
            SQLancerResultSet rs = sql.executeAndGet(state);

            if (rs != null) {
                int rowId = 0;
                while (rs.next()) {
                    int columnCount = rs.getMetaData().getColumnCount();
                    Object[] rowData = new Object[columnCount];
                    for (int i = 0; i < columnCount; i++) {
                        rowData[i] = rs.getObject(i + 1);
                    }

                    Version initVersion = new Version(rowData, "initial", false);
                    List<Version> versions = new ArrayList<>();
                    versions.add(initVersion);
                    tableVersions.put(rowId, versions);
                    rowId++;
                }
                rs.close();
            }
            versionChains.put(tableName, tableVersions);
        }
        mvccSimulator = new MVCCSimulator(versionChains);
    }

    private void resetVersionChains() throws SQLException {
        initializeVersionChains();
    }

    private FucciIsolationLevel selectIsolationLevel() {
        String isolationStr = state.getFucciOptions().getIsolationLevel();
        if (isolationStr == null || isolationStr.equalsIgnoreCase("RANDOM")) {
            return Randomly.fromList(state.getSupportedIsolationLevels());
        }
        return FucciIsolationLevel.fromString(isolationStr);
    }

    private TxTestExecutionResult simulateMVCCExecution(List<Transaction> transactions,
            List<TxStatement> schedule, FucciIsolationLevel isoLevel) {
        TxTestExecutionResult simulatedResult = new TxTestExecutionResult();
        simulatedResult.setIsolationLevel(isoLevel);

        for (TxStatement stmt : schedule) {
            if (stmt instanceof FucciTxStatement) {
                TxStatementExecutionResult stmtResult = new TxStatementExecutionResult(stmt);
                simulatedResult.getStatementExecutionResults().add(stmtResult);
            }
        }

        // Use MVCCSimulator to compute final states with isolation-level-aware visibility
        Map<String, List<Object>> finalStates = mvccSimulator.computeFinalStates();
        simulatedResult.setDbFinalStates(finalStates);

        return simulatedResult;
    }

    private String compareWithSimulation(TxTestExecutionResult testResult,
            TxTestExecutionResult simulatedResult, FucciIsolationLevel isoLevel) {
        // Use VisibilityRule to determine comparison strategy:
        // - SERIALIZABLE: compare all results (including SELECT)
        // - Others: compare only write results (SELECT results vary by isolation level)
        VisibilityRule rule = VisibilityRule.fromIsolationLevel(
                isoLevel != null ? isoLevel.getName() : null);
        if (rule == VisibilityRule.COMMITTED_WITH_LOCK) {
            return compareAllResults(testResult, simulatedResult);
        } else {
            return compareWriteTxResults(testResult, simulatedResult);
        }
    }

    private void reportBug(List<Transaction> transactions, List<TxStatement> schedule,
            String compareResult, TxTestExecutionResult testResult, TxTestExecutionResult simulatedResult) {
        state.getState().getLocalState().log("============ Fucci MT Oracle Bug Report =============");
        for (Transaction tx : transactions) {
            state.getState().getLocalState().log(tx.toString());
        }
        state.getState().getLocalState().log("Schedule: " + schedule.stream().map(TxStatement::getStmtId)
                .collect(Collectors.joining(", ", "[", "]")));
        state.getState().getLocalState().log(compareResult);
    }
}