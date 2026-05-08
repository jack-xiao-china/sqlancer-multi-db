package sqlancer.common.transaction;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sqlancer.Randomly;
import sqlancer.SQLGlobalState;

public abstract class TxTestGenerator<S extends SQLGlobalState<?, ?>> {
    protected final S globalState;

    public TxTestGenerator(S globalState) {
        this.globalState = globalState;
    }

    public List<Transaction> generateTransactions() throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        int[] candidateTxNums = {1, 2, 2, 2, 2, 3, 3, 3, 4, 5};
        int txNum = candidateTxNums[(int)Randomly.getNotCachedInteger(0, candidateTxNums.length)];
        if (globalState.getOptions().useFixedNumTransaction()) {
            txNum = globalState.getOptions().getNrTransactions();
        }
        for (int i = 1; i <= txNum; i++) {
            transactions.add(generateTransaction());
        }
        return transactions;
    }

    private List<TxStatement> genOneSchedule(List<Transaction> transactions) {
        List<Transaction> txs = new ArrayList<>(transactions);
        List<TxStatement> schedule = new ArrayList<>();
        Map<Transaction, Integer> stmtIndexes = new HashMap<>();
        for (Transaction tx : txs) {
            stmtIndexes.put(tx, 0);
        }

        while (!txs.isEmpty()) {
            Transaction tx = Randomly.fromList(txs);
            int stmtIndex = stmtIndexes.get(tx);
            schedule.add(tx.getStatements().get(stmtIndex));
            if (stmtIndex + 1 < tx.getStatements().size()) {
                stmtIndexes.put(tx, stmtIndex + 1);
            } else {
                txs.remove(tx);
            }
        }

        return schedule;
    }

    public List<List<TxStatement>> genSchedules(List<Transaction> transactions) {
        List<List<TxStatement>> schedules = new ArrayList<>();
        int num = globalState.getOptions().getNrSchedules();
        Integer maxNum = countSchedules(transactions);
        num = Math.min(num, maxNum);

        int count = 0;
        while (count < num) {
            List<TxStatement> schedule = genOneSchedule(transactions);
            if (!schedules.contains(schedule)) {
                schedules.add(schedule);
                count++;
            }
        }
        return schedules;
    }

    public Integer countSchedules(List<Transaction> txs) {
        int totalStmts = 0;
        for (Transaction tx : txs) {
            totalStmts += tx.getStatements().size();
        }

        BigInteger totalSchedules = factorial(totalStmts);
        for (Transaction tx : txs) {
            totalSchedules = totalSchedules.divide(factorial(tx.getStatements().size()));
        }

        int refinedNum = Integer.MAX_VALUE;
        try {
            refinedNum = totalSchedules.intValueExact();
        } catch (Exception ignored) {
        }
        return refinedNum;
    }

    private BigInteger factorial(int n) {
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }

    protected abstract Transaction generateTransaction() throws SQLException;
}