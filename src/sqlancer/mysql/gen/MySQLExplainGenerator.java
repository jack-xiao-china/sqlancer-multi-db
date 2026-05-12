package sqlancer.mysql.gen;

/**
 * Generator for EXPLAIN statements in MySQL.
 * MySQL 8.0+ supports EXPLAIN FORMAT=JSON which outputs the query plan in JSON format.
 */
public final class MySQLExplainGenerator {

    private MySQLExplainGenerator() {
    }

    /**
     * Generate an EXPLAIN statement with JSON format for MySQL.
     * The JSON output structure is different from PostgreSQL:
     * - Root: query_block containing select_id
     * - Table operations: under "table" node with "access_type" field
     * - Join operations: under "nested_loop" node
     * - Sorting: under "ordering_operation" node
     * - Grouping: under "grouping_operation" node
     *
     * @param selectStr the SELECT statement to explain
     * @return EXPLAIN FORMAT=JSON statement
     */
    public static String explain(String selectStr) {
        return "EXPLAIN FORMAT=JSON " + selectStr;
    }
}