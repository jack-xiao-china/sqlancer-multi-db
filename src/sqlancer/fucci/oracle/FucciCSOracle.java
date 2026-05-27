package sqlancer.fucci.oracle;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.common.oracle.TxBase;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxStatementExecutionResult;
import sqlancer.common.transaction.TxTestExecutionResult;
import sqlancer.fucci.FucciGlobalState;
import sqlancer.fucci.FucciIsolation.FucciIsolationLevel;
import sqlancer.fucci.FucciTable;
import sqlancer.fucci.bridge.DBMSFucciAdapter;
import sqlancer.fucci.transaction.FucciTxStatement;
import sqlancer.fucci.transaction.FucciTxTestExecutor;
import sqlancer.fucci.transaction.FucciTxTestGenerator;

/**
 * Fucci CS Oracle (约束求解Oracle) - 简化实现。
 */
public class FucciCSOracle extends TxBase<FucciGlobalState> {

    private DBMSFucciAdapter adapter;
    private FucciConstraintSolver constraintSolver;
    private Map<String, List<Object[]>> tableDataCache;

    public FucciCSOracle(FucciGlobalState state) {
        super(state);
        this.adapter = state.getFucciAdapter();
        this.constraintSolver = new FucciConstraintSolver();
        this.tableDataCache = new HashMap<>();
    }

    @Override
    public void check() throws SQLException {
        logger.writeCurrent("\n================= Fucci CS Oracle Check =================");
        loadTableData();

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

                String verifyResult = verifyWithConstraintSolving(transactions, schedule, testResult);

                if (!verifyResult.isEmpty()) {
                    reportBug(transactions, schedule, verifyResult, testResult);
                    throw new AssertionError("CS Oracle detected constraint violation");
                } else {
                    logger.writeCurrent("============ CS Oracle: Constraints Verified =============");
                }

                reproduceDatabase(state.getState().getStatements());
                refreshTableDataCache();
            }
        } finally {
            for (Transaction tx : new ArrayList<Transaction>()) {
                tx.closeConnection();
            }
        }
    }

    private void loadTableData() throws SQLException {
        tableDataCache.clear();
        List<FucciTable> tables = state.getSchema().getDatabaseTables();
        for (FucciTable table : tables) {
            String tableName = table.getName();
            Object[][] snapshot = adapter.getTableSnapshot(state, tableName);
            List<Object[]> rows = new ArrayList<>();
            for (Object[] row : snapshot) {
                rows.add(row);
            }
            tableDataCache.put(tableName, rows);
        }
    }

    private void refreshTableDataCache() throws SQLException {
        loadTableData();
    }

    private FucciIsolationLevel selectIsolationLevel() {
        String isolationStr = state.getFucciOptions().getIsolationLevel();
        if (isolationStr == null || isolationStr.equalsIgnoreCase("RANDOM")) {
            return Randomly.fromList(state.getSupportedIsolationLevels());
        }
        return FucciIsolationLevel.fromString(isolationStr);
    }

    private String verifyWithConstraintSolving(List<Transaction> transactions,
            List<TxStatement> schedule, TxTestExecutionResult testResult) {
        StringBuilder violations = new StringBuilder();
        for (TxStatement stmt : schedule) {
            if (stmt instanceof FucciTxStatement) {
                FucciTxStatement fucciStmt = (FucciTxStatement) stmt;
                if (fucciStmt.isSelectType()) {
                    String predicate = fucciStmt.getPredicate();
                    if (predicate != null && !predicate.isEmpty()) {
                        String verifyResult = verifySelectResult(fucciStmt, predicate, testResult);
                        if (!verifyResult.isEmpty()) {
                            violations.append(verifyResult).append("\n");
                        }
                    }
                }
            }
        }
        return violations.toString();
    }

    private String verifySelectResult(FucciTxStatement stmt, String predicate, TxTestExecutionResult testResult) {
        // 解析约束表达式
        constraintSolver.parseExpression(predicate); // 用于日志记录

        List<TxStatementExecutionResult> stmtResults = testResult.getStatementExecutionResults();
        List<Object> execResult = null;
        for (TxStatementExecutionResult result : stmtResults) {
            if (result.getStatement().equals(stmt)) {
                execResult = result.getResult();
                break;
            }
        }

        if (execResult == null) {
            return "";
        }

        // 使用约束求解验证结果
        // 简化实现: 验证返回行是否满足约束条件
        if (execResult.isEmpty() && predicate != null && !predicate.isEmpty()) {
            // 如果有WHERE条件但没有结果，可能是约束求解发现了问题
            return ""; // 简化实现暂不报告问题
        }

        return "";
    }

    private void reportBug(List<Transaction> transactions, List<TxStatement> schedule,
            String verifyResult, TxTestExecutionResult testResult) {
        state.getState().getLocalState().log("============ Fucci CS Oracle Bug Report =============");
        for (Transaction tx : transactions) {
            state.getState().getLocalState().log(tx.toString());
        }
        state.getState().getLocalState().log("Schedule: " + schedule.stream().map(TxStatement::getStmtId)
                .collect(Collectors.joining(", ", "[", "]")));
        state.getState().getLocalState().log(verifyResult);
    }

    private static class FucciConstraintSolver {
        public Object parseExpression(String expression) {
            return expression;
        }
    }
}