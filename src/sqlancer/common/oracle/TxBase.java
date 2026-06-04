package sqlancer.common.oracle;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import sqlancer.Main;
import sqlancer.MainOptions;
import sqlancer.SQLGlobalState;
import sqlancer.common.query.Query;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxStatementExecutionResult;
import sqlancer.common.transaction.TxTestExecutionResult;

public abstract class TxBase<S extends SQLGlobalState<?, ?>> implements TestOracle<S> {

    protected final S state;
    protected final MainOptions options;
    protected final Main.StateLogger logger;

    /** Count of discrepancies classified as Undecided and skipped. */
    protected int undecidedCount = 0;
    /** Count of confirmed bugs detected. */
    protected int bugCount = 0;

    public TxBase(S state) {
        this.state = state;
        this.options = state.getOptions();
        this.logger = state.getLogger();
    }

    public void reproduceDatabase(List<Query<?>> dbInitQueries) throws SQLException {
        for (Query<?> query : dbInitQueries) {
            SQLQueryAdapter queryAdapter = (SQLQueryAdapter) query;
            queryAdapter.execute(state);
        }
        try {
            state.updateSchema();
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    public String compareAllResults(TxTestExecutionResult testResult, TxTestExecutionResult oracleResult) {
        List<TxStatementExecutionResult> stmtExecResults = testResult.getStatementExecutionResults();
        List<TxStatementExecutionResult> stmtOracleResults = oracleResult.getStatementExecutionResults();
        for (TxStatementExecutionResult stmtExecResult : stmtExecResults) {
            if (stmtExecResult.isBlocked()) {
                continue;
            }
            TxStatementExecutionResult stmtOracleResult = findMatchingResult(stmtExecResult, stmtOracleResults);
            if (stmtOracleResult == null) {
                continue;
            }
            TxStatement stmt = stmtExecResult.getStatement();

            if (stmt.isNoErrorType()) {
                continue;
            }

            // --- Undecided classification (from Troc compareOracles) ---

            // 1. Deadlock discrepancy: exec deadlocked but oracle didn't expect it
            TxBugType deadlockType = TxDiscrepancyClassifier.classifyDeadlock(
                    stmtExecResult.reportDeadlock(), stmtOracleResult.reportDeadlock());
            if (deadlockType != null) {
                if (deadlockType.isUndecided()) {
                    logUndecided(deadlockType, stmt);
                    continue;
                }
                bugCount++;
                return deadlockType.getMessage() + "\n" + stmt;
            }

            // 2. Abort discrepancy
            TxBugType abortType = TxDiscrepancyClassifier.classifyAbort(stmtExecResult, stmtOracleResult);
            if (abortType != null) {
                if (abortType.isUndecided()) {
                    logUndecided(abortType, stmt);
                    continue;
                }
                bugCount++;
                return abortType.getMessage() + "\n" + stmt + "\n"
                        + "Exec: " + stmtExecResult.getErrorInfo() + "\n"
                        + "Oracle: " + stmtOracleResult.getErrorInfo();
            }

            // 3. Block discrepancy
            TxBugType blockType = TxDiscrepancyClassifier.classifyBlock(stmtExecResult, stmtOracleResult);
            if (blockType != null) {
                if (blockType.isUndecided()) {
                    logUndecided(blockType, stmt);
                    continue;
                }
                bugCount++;
                return blockType.getMessage() + "\n" + stmt;
            }

            // --- Original comparison logic ---

            String compareErrorResult = compareErrors(stmtExecResult, stmtOracleResult);
            if (!compareErrorResult.equals("")) {
                bugCount++;
                return compareErrorResult;
            }

            StringBuilder compareResult = new StringBuilder();
            if (stmt.isSelectType()) {
                if (!stmtExecResult.reportDeadlock()) {
                    String selectCompareResult = compareQueryResult(stmtExecResult.getResult(),
                            stmtOracleResult.getResult());
                    if (!selectCompareResult.isEmpty()) {
                        compareResult.append(TxBugType.INCONSISTENT_QUERY_RESULT.getMessage()).append("\n");
                        compareResult.append(stmt).append("\n");
                        compareResult.append(selectCompareResult);
                        bugCount++;
                        return compareResult.toString();
                    }
                }
            }
        }

        return compareFinalDBState(testResult, oracleResult);
    }

    public String compareWriteTxResults(TxTestExecutionResult testResult, TxTestExecutionResult oracleResult) {
        List<TxStatementExecutionResult> stmtExecResults = testResult.getStatementExecutionResults();
        List<TxStatementExecutionResult> stmtOracleResults = oracleResult.getStatementExecutionResults();
        for (TxStatementExecutionResult stmtExecResult : stmtExecResults) {
            if (stmtExecResult.isBlocked()) {
                continue;
            }
            TxStatementExecutionResult stmtOracleResult = findMatchingResult(stmtExecResult, stmtOracleResults);
            if (stmtOracleResult == null) {
                continue;
            }
            TxStatement stmt = stmtExecResult.getStatement();
            if (stmt.isSelectType()) {
                continue;
            }

            if (stmt.isNoErrorType()) {
                continue;
            }

            // --- Undecided classification ---
            TxBugType deadlockType = TxDiscrepancyClassifier.classifyDeadlock(
                    stmtExecResult.reportDeadlock(), stmtOracleResult.reportDeadlock());
            if (deadlockType != null && deadlockType.isUndecided()) {
                logUndecided(deadlockType, stmt);
                continue;
            }

            TxBugType abortType = TxDiscrepancyClassifier.classifyAbort(stmtExecResult, stmtOracleResult);
            if (abortType != null) {
                if (abortType.isUndecided()) {
                    logUndecided(abortType, stmt);
                    continue;
                }
                bugCount++;
                return abortType.getMessage() + "\n" + stmt;
            }

            TxBugType blockType = TxDiscrepancyClassifier.classifyBlock(stmtExecResult, stmtOracleResult);
            if (blockType != null) {
                if (blockType.isUndecided()) {
                    logUndecided(blockType, stmt);
                    continue;
                }
                bugCount++;
                return blockType.getMessage() + "\n" + stmt;
            }

            String compareResult = compareErrors(stmtExecResult, stmtOracleResult);
            if (!compareResult.equals("")) {
                bugCount++;
                return compareResult;
            }
        }

        return compareFinalDBState(testResult, oracleResult);
    }

    public String compareFinalDBState(TxTestExecutionResult testResult, TxTestExecutionResult oracleResult) {
        for (Map.Entry<String, List<Object>> finalState : testResult.getDbFinalStates().entrySet()) {
            List<Object> execFinalState = finalState.getValue();
            List<Object> oracleFinalState = oracleResult.getDbFinalStates().get(finalState.getKey());
            String compareResultInfo = compareQueryResult(execFinalState, oracleFinalState);
            if (!compareResultInfo.isEmpty()) {
                bugCount++;
                return TxBugType.INCONSISTENT_FINAL_STATE.getMessage() + "\n" + compareResultInfo;
            }
        }
        return "";
    }

    private String compareErrors(TxStatementExecutionResult stmtExecResult, TxStatementExecutionResult stmtOracleResult) {
        TxStatement stmt = stmtExecResult.getStatement();
        StringBuilder compareResult = new StringBuilder();
        if (stmtExecResult.reportDeadlock()) {
            return "";
        }
        if ((!stmtExecResult.reportError() && stmtOracleResult.reportError())
                || (stmtExecResult.reportError() && !stmtOracleResult.reportError())) {
            compareResult.append("Error: Inconsistent reporting error\n");
        } else if (stmtExecResult.reportError() && stmtOracleResult.reportError()
                && !stmtExecResult.getErrorInfo().equals(stmtOracleResult.getErrorInfo())) {
            compareResult.append("Error: Inconsistent error info\n");
        }
        if (compareResult.length() > 0) {
            compareResult.append(stmt.toString()).append("\n");
            compareResult.append("Exec: ").append(stmtExecResult.getErrorInfo()).append("\n");
            compareResult.append("Oracle: ").append(stmtOracleResult.getErrorInfo()).append("\n");
            return compareResult.toString();
        }

        if (stmtExecResult.reportWarning() && !stmtOracleResult.reportWarning()
                || !stmtExecResult.reportWarning() && stmtOracleResult.reportWarning()) {
            compareResult.append("Error: Inconsistent reporting warning\n");
        } else if (stmtExecResult.reportWarning() && stmtOracleResult.reportWarning()
                && !stmtExecResult.getWarningInfo().equals(stmtOracleResult.getWarningInfo())) {
            compareResult.append("Error: Inconsistent warning info\n");
        }
        if (compareResult.length() > 0) {
            compareResult.append(stmt.toString()).append("\n");
            compareResult.append("Exec: ").append(stmtExecResult.getWarningInfo().toString()).append("\n");
            compareResult.append("Oracle: ").append(stmtOracleResult.getWarningInfo().toString()).append("\n");
            return compareResult.toString();
        }
        return "";
    }

    private String compareQueryResult(List<Object> queryResult1, List<Object> queryResult2) {
        if (queryResult1 == null && queryResult2 == null) {
            return "";
        } else if (queryResult1 == null || queryResult2 == null) {
            return "Error: One query result is NULL";
        }
        if (queryResult1.size() != queryResult2.size()) {
            return "Error: The size of query results is different";
        }
        List<String> qRes1 = preprocessQueryResult(queryResult1);
        List<String> qRes2 = preprocessQueryResult(queryResult2);
        for (int i = 0; i < qRes1.size(); i++) {
            String r1 = qRes1.get(i);
            String r2 = qRes2.get(i);
            if (!r1.equals(r2)) {
                return "Error: (" + i + ")th values is different [" + r1 + ", " + r2 + "]";
            }
        }
        return "";
    }

    private static List<String> preprocessQueryResult(List<Object> resultSet) {
        return resultSet.stream().map(o -> {
            if (o == null) {
                return "[NULL]";
            } else {
                return o.toString();
            }
        }).sorted().collect(Collectors.toList());
    }

    /**
     * Find the oracle result matching a given execution result (same statement + same transaction).
     */
    private TxStatementExecutionResult findMatchingResult(
            TxStatementExecutionResult target,
            List<TxStatementExecutionResult> candidates) {
        for (TxStatementExecutionResult candidate : candidates) {
            if (candidate.getStatement().equals(target.getStatement())
                    && candidate.getStatement().getTransaction().getId()
                    == target.getStatement().getTransaction().getId()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Log an Undecided discrepancy for later review.
     * Undecided cases are skipped (not reported as bugs) but tracked for statistics.
     */
    protected void logUndecided(TxBugType type, TxStatement stmt) {
        undecidedCount++;
        logger.writeCurrent("[Undecided] " + type.getMessage() + " | " + stmt);
    }

    /** Returns the total number of Undecided discrepancies encountered. */
    public int getUndecidedCount() {
        return undecidedCount;
    }

    /** Returns the total number of confirmed bugs detected. */
    public int getBugCount() {
        return bugCount;
    }
}