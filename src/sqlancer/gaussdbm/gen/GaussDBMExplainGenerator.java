package sqlancer.gaussdbm.gen;

/**
 * Generator for EXPLAIN statements in GaussDB-M (MySQL Compatibility Mode).
 * GaussDB-M is based on openGauss (PostgreSQL-based) and supports PostgreSQL-style EXPLAIN.
 * Using EXPLAIN (FORMAT JSON) provides more complete output that can reuse PostgreSQL's JSON parsing.
 */
public final class GaussDBMExplainGenerator {

    private GaussDBMExplainGenerator() {
    }

    /**
     * Generate an EXPLAIN statement with JSON format for GaussDB-M.
     * Uses PostgreSQL-style EXPLAIN syntax since GaussDB-M is built on openGauss.
     * The JSON output structure is similar to PostgreSQL:
     * - Root: array with single element containing "Plan" node
     * - Node Type field identifies the operation type (e.g., "Seq Scan", "Index Scan")
     * - Plans array contains child nodes for recursive traversal
     *
     * @param selectStr the SELECT statement to explain
     * @return EXPLAIN (FORMAT JSON) statement
     */
    public static String explain(String selectStr) {
        return "EXPLAIN (FORMAT JSON) " + selectStr;
    }
}