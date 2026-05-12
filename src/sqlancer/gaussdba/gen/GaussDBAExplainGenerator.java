package sqlancer.gaussdba.gen;

/**
 * Generator for EXPLAIN statements in GaussDB-A (Oracle Compatibility Mode).
 * GaussDB-A is based on openGauss (PostgreSQL-based) and likely supports PostgreSQL-style EXPLAIN.
 * Note: This needs to be verified on actual GaussDB-A instance as Oracle compatibility mode
 * may have different EXPLAIN syntax support.
 */
public final class GaussDBAExplainGenerator {

    private GaussDBAExplainGenerator() {
    }

    /**
     * Generate an EXPLAIN statement with JSON format for GaussDB-A.
     * Uses PostgreSQL-style EXPLAIN syntax. May need adjustment if Oracle compatibility
     * mode requires different syntax (e.g., EXPLAIN PLAN FOR).
     *
     * @param selectStr the SELECT statement to explain
     * @return EXPLAIN (FORMAT JSON) statement
     */
    public static String explain(String selectStr) {
        return "EXPLAIN (FORMAT JSON) " + selectStr;
    }
}