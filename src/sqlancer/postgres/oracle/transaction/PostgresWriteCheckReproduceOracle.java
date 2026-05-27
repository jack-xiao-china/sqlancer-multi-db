package sqlancer.postgres.oracle.transaction;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.Query;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxTestExecutionResult;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.gen.PostgresCommon;
import sqlancer.postgres.gen.transaction.PostgresTransactionProvider;
import sqlancer.postgres.transaction.PostgresIsolation.PostgresIsolationLevel;
import sqlancer.postgres.transaction.PostgresTxStatement;
import sqlancer.postgres.transaction.PostgresTxTestExecutor;

public class PostgresWriteCheckReproduceOracle extends PostgresWriteCheckOracle {

    public PostgresWriteCheckReproduceOracle(PostgresGlobalState state) {
        super(state);
    }

    @Override
    public void check() throws SQLException {

        Scanner scanner;
        try {
            File caseFile = new File(options.getCaseFile());
            scanner = new Scanner(caseFile);
            logger.writeCurrent("Read database and transaction from file: " + options.getCaseFile());
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Read case from file failed: ", e);
        }
        List<Query<?>> dbInitQueries = prepareTableFromScanner(scanner);
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < state.getOptions().getNrTransactions(); i++) {
            transactions.add(readTransactionFromScanner(scanner));
        }
        String scheduleStr = readOrderFromScanner(scanner);
        List<TxStatement> schedule = checkOrder(scheduleStr, transactions);
        boolean detectBug = false;

        try {
            for (PostgresIsolationLevel level : PostgresIsolationLevel.values()) {
                PostgresTxTestExecutor testExecutor = new PostgresTxTestExecutor(state, transactions, schedule, level);
                TxTestExecutionResult testResult = testExecutor.execute();
                recreateDatabase(transactions);
                reproduceDatabase(dbInitQueries);

                List<TxStatement> oracleSchedule = genOracleSchedule(testResult);
                PostgresTxTestExecutor oracleExecutor = new PostgresTxTestExecutor(state, transactions, oracleSchedule, level);
                TxTestExecutionResult oracleResult = oracleExecutor.execute();
                recreateDatabase(transactions);
                reproduceDatabase(dbInitQueries);

                List<TxStatement> oracleWithoutCommitAndRollbackSchedule = genOracleWithoutCommitAndRollbackSchedule(testResult);
                PostgresTxTestExecutor oracleWithoutCommitAndRollbackExecutor = new PostgresTxTestExecutor(state, transactions,
                        oracleWithoutCommitAndRollbackSchedule, level);
                TxTestExecutionResult oracleWithoutCommitAndRollbackResult = oracleWithoutCommitAndRollbackExecutor.execute();
                recreateDatabase(transactions);
                reproduceDatabase(dbInitQueries);

                String compareResultInfo;
                if (level == PostgresIsolationLevel.SERIALIZABLE) {
                    compareResultInfo = compareAllResults(testResult, oracleResult);
                } else {
                    compareResultInfo = compareWriteTxResults(testResult, oracleResult);
                }
                if (compareResultInfo.equals("")) {
                    state.getLogger().writeCurrent("============Is Same============");
                    state.getLogger().writeCurrent("==Oracle With Commit And Rollback==");
                    state.getLogger().writeCurrent("Execution Result:");
                    state.getLogger().writeCurrent(testResult.toString());
                    state.getLogger().writeCurrent("Oracle Result:");
                    state.getLogger().writeCurrent(oracleResult.toString());
                } else {
                    state.getState().getLocalState().log("============Bug Report============");
                    state.getState().getLocalState().log("==Oracle With Commit And Rollback==");
                    for (Transaction tx : transactions) {
                        state.getState().getLocalState().log(tx.toString());
                    }
                    state.getState().getLocalState().log("Input schedule: " + schedule.stream().map(o -> o.getStmtId()).
                            collect(Collectors.joining(", ", "[", "]")));
                    state.getState().getLocalState().log(compareResultInfo);
                    state.getState().getLocalState().log("Execution Result:");
                    state.getState().getLocalState().log(testResult.toString());
                    state.getState().getLocalState().log("Oracle Result:");
                    state.getState().getLocalState().log(oracleResult.toString());
                    detectBug = true;
                }

                if (level == PostgresIsolationLevel.SERIALIZABLE) {
                    compareResultInfo = compareAllResults(testResult, oracleWithoutCommitAndRollbackResult);
                } else {
                    compareResultInfo = compareWriteTxResults(testResult, oracleWithoutCommitAndRollbackResult);
                }
                if (compareResultInfo.equals("")) {
                    state.getLogger().writeCurrent("============Is Same============");
                    state.getLogger().writeCurrent("==Oracle Without Rollback==");
                    state.getLogger().writeCurrent("Execution Result:");
                    state.getLogger().writeCurrent(testResult.toString());
                    state.getLogger().writeCurrent("Oracle Result:");
                    state.getLogger().writeCurrent(oracleWithoutCommitAndRollbackResult.toString());
                } else {
                    state.getState().getLocalState().log("============Bug Report============");
                    state.getState().getLocalState().log("==Oracle Without Rollback==");
                    for (Transaction tx : transactions) {
                        state.getState().getLocalState().log(tx.toString());
                    }
                    state.getState().getLocalState().log("Input schedule: " + schedule.stream().map(o -> o.getStmtId()).
                            collect(Collectors.joining(", ", "[", "]")));
                    state.getState().getLocalState().log(compareResultInfo);
                    state.getState().getLocalState().log("Execution Result:");
                    state.getState().getLocalState().log(testResult.toString());
                    state.getState().getLocalState().log("Oracle Result:");
                    state.getState().getLocalState().log(oracleWithoutCommitAndRollbackResult.toString());
                    detectBug = true;
                }
            }
            if (detectBug) {
                throw new AssertionError("Transaction execution mismatches its oracle");
            }
        } finally {
            for (Transaction tx : transactions) {
                tx.closeConnection();
            }
        }
        System.exit(0);
    }

    private List<Query<?>> prepareTableFromScanner(Scanner input) throws SQLException {
        List<Query<?>> dbInitQueries = new ArrayList<>();
        String sql;
        do {
            sql = input.nextLine();
            if (sql.equals("")) break;
            SQLQueryAdapter queryAdapter;
            ExpectedErrors errors = new ExpectedErrors();
            PostgresCommon.addCommonExpressionErrors(errors);
            PostgresCommon.addCommonTableErrors(errors);
            if (sql.contains("CREATE") || sql.contains("ALTER TABLE") || sql.contains("DROP")) {
                queryAdapter = new SQLQueryAdapter(sql, errors, true);
            } else {
                queryAdapter = new SQLQueryAdapter(sql, errors);
            }
            try {
                queryAdapter.execute(state, false);
                dbInitQueries.add(queryAdapter);
            } catch (Exception e) {
                throw new SQLException(e);
            }
        } while (true);
        try {
            state.updateSchema();
        } catch (Exception e) {
            throw new SQLException(e);
        }
        return dbInitQueries;
    }

    private Transaction readTransactionFromScanner(Scanner input) throws SQLException {
        Transaction transaction = new Transaction(PostgresTransactionProvider.createNewConnection(state));
        List<TxStatement> statementList = new ArrayList<>();
        String txId = input.nextLine();
        transaction.setId(Integer.parseInt(txId));
        String sql;
        do {
            if (!input.hasNext()) break;
            sql = input.nextLine();
            if (sql.equals("") || sql.equals("END")) break;
            ExpectedErrors errors = new ExpectedErrors();
            PostgresCommon.addCommonExpressionErrors(errors);
            PostgresCommon.addCommonFetchErrors(errors);
            errors.add("permission denied for");
            errors.add("does not exist");
            errors.add("FOR UPDATE cannot");
            errors.add("deadlock detected");
            errors.add("could not serialize");
            errors.add("current transaction is aborted");
            errors.add("there is no unique");
            TxSQLQueryAdapter txStatement = new TxSQLQueryAdapter(sql, errors);
            TxStatement cell = new PostgresTxStatement(transaction, txStatement);
            statementList.add(cell);
        } while (true);
        transaction.setStatements(statementList);
        return transaction;
    }

    private String readOrderFromScanner(Scanner input) {
        do {
            if (!input.hasNext())
                break;
            String scheduleStr = input.nextLine();
            if (scheduleStr.equals(""))
                continue;
            if (scheduleStr.equals("END"))
                break;
            return scheduleStr;
        } while (true);
        return "";
    }

    private List<TxStatement> checkOrder(String scheduleStr,
                                           List<Transaction> transactions) {
        List<TxStatement> schedule = new ArrayList<>();
        Map<Integer, Transaction> txIdMap = new HashMap<>();
        Map<Integer, Integer> txStmtIndex = new HashMap<>();
        String[] scheduleStrArray = scheduleStr.split("-");
        int allStmtsLength = 0;
        for (Transaction transaction : transactions) {
            allStmtsLength += transaction.getStatements().size();
            txIdMap.put(transaction.getId(), transaction);
            txStmtIndex.put(transaction.getId(), 0);
        }
        if (scheduleStrArray.length != allStmtsLength) {
            throw new RuntimeException("Invalid Schedule");
        }

        for (String txIdStr : scheduleStrArray) {
            int txId = Integer.parseInt(txIdStr);
            if (txIdMap.containsKey(txId)){
                Transaction tx = txIdMap.get(txId);
                int stmtIdx = txStmtIndex.get(txId);
                schedule.add(tx.getStatements().get(stmtIdx++));
                txStmtIndex.replace(txId, stmtIdx);
            } else {
                throw new RuntimeException("Invalid Schedule");
            }
        }
        return schedule;
    }
}