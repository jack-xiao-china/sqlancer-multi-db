package sqlancer.common.oracle;

/**
 * Transaction detection Bug type classification.
 *
 * References Troc's compareOracles() method which defines 6 Bug types + 3 Undecided categories.
 * This classification enables fine-grained Bug reporting and explicit filtering of
 * undecidable discrepancies (reducing false positives by an estimated 30-50%).
 */
public enum TxBugType {

    // === Definite Bugs (6 types) ===

    MISSING_ABORT("Error: Missing abort - execution should have aborted but didn't"),
    UNNECESSARY_ABORT("Error: Unnecessary abort - execution aborted without cause"),
    INCONSISTENT_QUERY_RESULT("Error: Inconsistent query result"),
    MISSING_LOCK("Error: Missing lock - execution should have blocked but didn't"),
    UNNECESSARY_LOCK("Error: Unnecessary lock - execution blocked without cause"),
    INCONSISTENT_FINAL_STATE("Error: Inconsistent final database state"),

    // === Undecided (3 types) ===
    // These represent discrepancies that cannot be definitively classified as bugs
    // because the DBMS behavior may be a legitimate implementation choice.

    UNDECIDED_DEADLOCK("Ignore: Undecided - execution deadlock, oracle no deadlock"),
    UNDECIDED_ABORT("Ignore: Undecided - execution aborted, oracle didn't expect abort"),
    UNDECIDED_BLOCK("Ignore: Undecided - execution blocked, oracle didn't expect block");

    private final String message;

    TxBugType(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Whether this type represents an undecidable discrepancy (not a confirmed bug).
     */
    public boolean isUndecided() {
        return this == UNDECIDED_DEADLOCK || this == UNDECIDED_ABORT || this == UNDECIDED_BLOCK;
    }

    /**
     * Whether this type represents a confirmed bug.
     */
    public boolean isBug() {
        return !isUndecided();
    }
}
