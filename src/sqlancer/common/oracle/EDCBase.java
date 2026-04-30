package sqlancer.common.oracle;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import sqlancer.ComparatorHelper;
import sqlancer.IgnoreMeException;
import sqlancer.MainOptions;
import sqlancer.SQLConnection;
import sqlancer.SQLGlobalState;
import sqlancer.Main.StateLogger;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;

/**
 * EDC (Equivalent Database Construction) Oracle Base Class.
 *
 * EDC Oracle detects optimizer bugs by creating an "equivalent" database without constraints
 * (NOT NULL, UNIQUE, FOREIGN KEY, CHECK, GENERATED columns) and comparing query results:
 * - Original DB: Has constraints that affect query optimization
 * - Raw DB: No constraints, pure data copy (non-optimized execution)
 *
 * Result mismatch indicates potential optimizer bug where constraints are incorrectly
 * optimized away or handled improperly.
 *
 * @param <S> GlobalState type for the specific DBMS
 */
public abstract class EDCBase<S extends SQLGlobalState<?, ?>> implements TestOracle<S> {

    protected final S originalState;
    protected S equivalentState; // Raw database state (without constraints)
    protected final ExpectedErrors errors = new ExpectedErrors();
    protected final StateLogger logger;
    protected final MainOptions options;
    protected final SQLConnection con;
    protected String queryString;

    public EDCBase(S originalState) {
        this.originalState = originalState;
        this.con = originalState.getConnection();
        this.logger = originalState.getLogger();
        this.options = originalState.getOptions();
    }

    /**
     * Main check method - generates query and compares results between optimized and
     * non-optimized databases.
     */
    @Override
    public void check() throws Exception {
        // Ensure equivalent state (raw DB) is constructed before checking
        if (equivalentState == null) {
            constructEquivalentState();
        }

        // 1. Generate random SELECT query
        queryString = generateQueryString(originalState);
        logger.writeCurrent(queryString);

        // 2. Execute on original DB (with constraints, optimized)
        List<String> optimizedResult = getOptimizedResult(originalState);

        // 3. Execute on equivalent raw DB (without constraints, non-optimized)
        List<String> nonOptimizedResult = getNonOptimizedResult(equivalentState);

        // 4. Compare results - mismatch indicates optimizer bug
        ComparatorHelper.assumeResultSetsAreEqual(optimizedResult, nonOptimizedResult, queryString,
                List.of(equivalentState.getDatabaseName()), originalState);
    }

    /**
     * Create equivalent state (raw DB) before checking.
     */
    public void constructEquivalentState() {
        try {
            originalState.updateSchema();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.equivalentState = constructEquivalentState(originalState);
    }

    /**
     * Close equivalent state connection after checking.
     */
    public void closeEquState() throws SQLException {
        if (equivalentState != null) {
            equivalentState.getConnection().close();
        }
    }

    // ==================== Abstract Methods (DBMS-specific implementations) ====================

    /**
     * Obtain table schemas with constraint metadata for diversity checking.
     *
     * @param state The database state
     * @return Map of table name to column/constraint metadata
     */
    public abstract Map<String, Map<String, List<String>>> obtainTableSchemas(S state) throws SQLException;

    /**
     * Construct equivalent state (raw database without constraints).
     *
     * @param state The original database state
     * @return New GlobalState for the raw database
     */
    public abstract S constructEquivalentState(S state);

    /**
     * Generate random SELECT query string for testing.
     *
     * @param state The database state
     * @return SQL query string
     */
    public abstract String generateQueryString(S state);

    // ==================== Helper Methods ====================

    /**
     * Execute query and get result list from optimized (original) database.
     */
    public List<String> getOptimizedResult(S state) throws SQLException {
        List<String> resultSet = new ArrayList<>();
        SQLQueryAdapter q = new SQLQueryAdapter(queryString, errors);
        SQLancerResultSet result = null;
        try {
            result = q.executeAndGet(state);
            if (result == null) {
                throw new IgnoreMeException(); // avoid too many false positives
            }
            ResultSetMetaData metaData = result.getMetaData();
            int columns = metaData.getColumnCount();
            while (result.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    String resultTemp = result.getString(i);
                    if (resultTemp != null) {
                        // Remove trailing zeros as many DBMS treat it as non-bugs
                        resultTemp = resultTemp.replaceAll("[\\.]0+$", "");
                    }
                    row.append(resultTemp).append(",");
                }
                resultSet.add(row.toString());
            }
        } catch (Exception e) {
            if (e instanceof IgnoreMeException) {
                throw e;
            }
            if (e.getMessage() == null) {
                throw new AssertionError(queryString, e);
            }
            if (errors.errorIsExpected(e.getMessage())) {
                throw new IgnoreMeException();
            }
            throw new AssertionError(queryString, e);
        } finally {
            if (result != null && !result.isClosed()) {
                result.close();
            }
        }
        return resultSet;
    }

    /**
     * Execute query and get result from non-optimized (raw) database.
     */
    public List<String> getNonOptimizedResult(S state) throws SQLException {
        return getOptimizedResult(state);
    }

    /**
     * Check if database structure is new (for testing diversity).
     * Avoids testing same constraint structures repeatedly.
     */
    public boolean containsNewDatabaseStructure(Set<Integer> databaseStructureSet) throws SQLException {
        Map<String, Map<String, List<String>>> tableSchemas = obtainTableSchemas(originalState);
        List<TableStructure> tableStructures = new ArrayList<>();
        boolean isRawDb = true;

        for (String tableName : tableSchemas.keySet()) {
            List<String> columns = new ArrayList<>();
            List<String> tables = new ArrayList<>();
            for (String columnRef : tableSchemas.get(tableName).keySet()) {
                List<String> metaElements = tableSchemas.get(tableName).get(columnRef);
                String metadata = String.join(" ", metaElements).toUpperCase();
                if (isRawDb) {
                    // Check if any constraints exist
                    if (metadata.contains("NOT NULL") || metadata.contains("GENERATED")
                            || metadata.contains("UNIQUE") || metadata.contains("FOREIGN KEY")
                            || metadata.contains("PRIMARY") || metadata.contains("CHECK")
                            || metadata.contains("KEY") || metadata.contains("INDEX")) {
                        isRawDb = false;
                    }
                }
                // Column reference pattern (like c0, c1, or `c0`, `c1`)
                if (columnRef.matches("c\\d+") || columnRef.matches("`c\\d+`")) {
                    columns.add(metadata);
                } else {
                    tables.add(metadata);
                }
            }
            tableStructures.add(new TableStructure(columns, tables));
        }

        if (isRawDb) {
            throw new IgnoreMeException(); // do not test raw database (no constraints)
        }

        DatabaseStructure databaseStructure = new DatabaseStructure(tableStructures);
        int hashcode = databaseStructure.hashCode;
        if (databaseStructureSet.contains(hashcode)) {
            return false;
        } else {
            databaseStructureSet.add(hashcode);
            return true;
        }
    }

    // ==================== Inner Classes for Structure Hashing ====================

    /**
     * Table structure for diversity checking.
     */
    public static class TableStructure implements Comparable<TableStructure> {
        private final List<String> columns;
        private final List<String> tables;
        private final int hashCode;

        public TableStructure(List<String> columns, List<String> tables) {
            this.columns = new ArrayList<>(columns);
            this.tables = new ArrayList<>(tables);
            this.hashCode = Objects.hash(this.columns, this.tables);
        }

        @Override
        public int compareTo(TableStructure that) {
            return Integer.compare(this.hashCode(), that.hashCode());
        }

        @Override
        public boolean equals(Object that) {
            if (this == that) {
                return true;
            }
            if (that == null || getClass() != that.getClass()) {
                return false;
            }
            return this.hashCode == ((TableStructure) that).hashCode;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    /**
     * Database structure for diversity checking.
     */
    public static class DatabaseStructure {
        private final List<TableStructure> tableStructures;
        private final int hashCode;

        public DatabaseStructure(List<TableStructure> tableStructures) {
            this.tableStructures = new ArrayList<>(tableStructures);
            this.hashCode = Objects.hash(this.tableStructures);
        }

        @Override
        public boolean equals(Object that) {
            if (this == that) {
                return true;
            }
            if (that == null || getClass() != that.getClass()) {
                return false;
            }
            return this.hashCode == ((DatabaseStructure) that).hashCode;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}