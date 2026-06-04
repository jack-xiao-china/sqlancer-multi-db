package sqlancer.common.oracle;

import sqlancer.common.transaction.TxStatementExecutionResult;

/**
 * Classifies discrepancies between execution results and oracle results as either
 * a definite Bug or an Undecided case that should be skipped.
 *
 * References Troc's TrocChecker.compareOracles() logic, specifically:
 * - shouldNotAbort(): determines if an unexpected abort is a bug or legitimate DBMS behavior
 * - shouldNotBlock(): determines if an unexpected block is a bug or legitimate DBMS behavior
 * - Deadlock mismatch: always treated as Undecided (detection timing varies by DBMS)
 */
public final class TxDiscrepancyClassifier {

    private TxDiscrepancyClassifier() {
    }

    /**
     * Classify deadlock discrepancy.
     *
     * When execution detects a deadlock but oracle doesn't expect one, this is always
     * Undecided because deadlock detection timing varies across DBMS implementations.
     *
     * @return UNDECIDED_DEADLOCK if exec deadlocked but oracle didn't; null otherwise
     */
    public static TxBugType classifyDeadlock(boolean execDeadlocked, boolean oracleDeadlocked) {
        if (execDeadlocked && !oracleDeadlocked) {
            return TxBugType.UNDECIDED_DEADLOCK;
        }
        return null;
    }

    /**
     * Classify abort discrepancy.
     *
     * Decision tree:
     * - Both abort or both succeed → null (no discrepancy)
     * - Oracle says abort, exec doesn't → MISSING_ABORT (definite bug)
     * - Exec aborts, oracle doesn't → UNNECESSARY_ABORT (bug) or UNDECIDED_ABORT (legitimate)
     *
     * @return Bug type if discrepancy found; null if both sides agree
     */
    public static TxBugType classifyAbort(TxStatementExecutionResult execResult,
                                          TxStatementExecutionResult oracleResult) {
        boolean execAborted = execResult.reportError() && !execResult.reportDeadlock();
        boolean oracleAborted = oracleResult.reportError() && !oracleResult.reportDeadlock();

        if (execAborted == oracleAborted) {
            return null;
        }
        if (!execAborted && oracleAborted) {
            return TxBugType.MISSING_ABORT;
        }
        // execAborted && !oracleAborted
        if (canBeLegitimateAbort(execResult)) {
            return TxBugType.UNDECIDED_ABORT;
        }
        return TxBugType.UNNECESSARY_ABORT;
    }

    /**
     * Classify block discrepancy.
     *
     * Decision tree:
     * - Both block or both proceed → null (no discrepancy)
     * - Oracle says block, exec doesn't → MISSING_LOCK (definite bug)
     * - Exec blocks, oracle doesn't → UNNECESSARY_LOCK (bug) or UNDECIDED_BLOCK (legitimate)
     *
     * @return Bug type if discrepancy found; null if both sides agree
     */
    public static TxBugType classifyBlock(TxStatementExecutionResult execResult,
                                          TxStatementExecutionResult oracleResult) {
        boolean execBlocked = execResult.isBlocked();
        boolean oracleBlocked = oracleResult.isBlocked();

        if (execBlocked == oracleBlocked) {
            return null;
        }
        if (!execBlocked && oracleBlocked) {
            return TxBugType.MISSING_LOCK;
        }
        // execBlocked && !oracleBlocked
        if (canBeLegitimateBlock(execResult)) {
            return TxBugType.UNDECIDED_BLOCK;
        }
        return TxBugType.UNNECESSARY_LOCK;
    }

    /**
     * Determine if an abort could be a legitimate DBMS behavior.
     *
     * Covers known error patterns across all 4 supported DBMS:
     * - MySQL/GaussDB-M: deadlock victim rollback, lock wait timeout
     * - PostgreSQL/GaussDB-A: SSI serialization failure, statement/lock timeout
     * - GaussDB-A: Oracle-compatible error codes (ORA-00060, ORA-04020)
     */
    private static boolean canBeLegitimateAbort(TxStatementExecutionResult result) {
        if (!result.reportError()) {
            return false;
        }
        String error = result.getErrorInfo();
        if (error == null) {
            return false;
        }
        // MySQL/GaussDB-M: InnoDB deadlock victim rollback
        if (error.contains("Deadlock") || error.contains("deadlock")
                || error.contains("try restarting transaction")) {
            return true;
        }
        // MySQL/GaussDB-M: lock wait timeout
        if (error.contains("Lock wait timeout")) {
            return true;
        }
        // PostgreSQL/GaussDB-A: SSI serialization failure
        if (error.contains("could not serialize") || error.contains("serialization")) {
            return true;
        }
        // PostgreSQL/GaussDB-A: statement or lock timeout
        if (error.contains("statement timeout") || error.contains("lock timeout")
                || error.contains("canceling statement due to statement timeout")) {
            return true;
        }
        // GaussDB-A: Oracle-compatible deadlock/resource error codes
        if (error.contains("ORA-00060") || error.contains("ORA-04020")) {
            return true;
        }
        // PG: "current transaction is aborted" (cascading error from earlier failure)
        if (error.contains("current transaction is aborted")) {
            return true;
        }
        return false;
    }

    /**
     * Determine if a block could be a legitimate DBMS behavior.
     *
     * Unexpected blocking is generally treated as Undecided because lock acquisition
     * timing depends on DBMS-internal scheduling that is difficult to model precisely.
     */
    private static boolean canBeLegitimateBlock(TxStatementExecutionResult result) {
        return result.isBlocked();
    }
}
