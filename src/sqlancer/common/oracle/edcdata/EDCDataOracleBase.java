package sqlancer.common.oracle.edcdata;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.Reproducer;
import sqlancer.SQLGlobalState;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Abstract base class implementing the core EDC data operation testing algorithm.
 * Equivalent to EDC Python's main.py algorithm.
 *
 * @param <S> GlobalState type for the specific DBMS
 */
public abstract class EDCDataOracleBase<S extends SQLGlobalState<?, ?>> implements TestOracle<S> {

    protected final S state;
    protected final EDCDataConfig config;
    protected final EDCDataExpressionBuilder exprBuilder;
    protected final EDCDataQueryBuilder queryBuilder;
    protected final EDCDataTableBuilder<S> tableBuilder;
    protected final EDCDataResultComparator resultComparator;
    protected final ExpectedErrors errors;

    private Reproducer<S> reproducer;
    private String lastQueryString;
    private int scenarioCounter = 0;

    /**
     * Default set of zero-argument functions supported across DBMS.
     * Subclasses can override {@link #getZeroArgFunctions()} to customize.
     */
    private static final Set<String> DEFAULT_ZERO_ARG_FUNCTIONS = Set.of(
            "PI", "NOW", "DATABASE", "USER", "UUID", "UUID_SHORT",
            "CONNECTION_ID", "FOUND_ROWS", "VERSION",
            "LOCALTIME", "LOCALTIMESTAMP", "SCHEMA", "SYSTEM_USER", "UTC_DATE",
            "CURDATE", "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURTIME"
    );

    public EDCDataOracleBase(S state, EDCDataConfig config, EDCDataExpressionBuilder exprBuilder,
                              EDCDataQueryBuilder queryBuilder, EDCDataTableBuilder<S> tableBuilder) {
        this.state = state;
        this.config = config;
        this.exprBuilder = exprBuilder;
        this.queryBuilder = queryBuilder;
        this.tableBuilder = tableBuilder;
        this.resultComparator = new EDCDataResultComparator();
        this.errors = new ExpectedErrors();
        addCommonExpectedErrors();
    }

    /**
     * Convenience constructor using factory methods.
     */
    public EDCDataOracleBase(S state, EDCDataConfig config) {
        this.state = state;
        this.config = config;
        this.exprBuilder = createExpressionBuilder(config);
        this.queryBuilder = createQueryBuilder(config, this.exprBuilder);
        this.tableBuilder = createTableBuilder(config, this.exprBuilder);
        this.resultComparator = new EDCDataResultComparator();
        this.errors = new ExpectedErrors();
        addCommonExpectedErrors();
    }

    protected abstract EDCDataExpressionBuilder createExpressionBuilder(EDCDataConfig config);

    protected abstract EDCDataQueryBuilder createQueryBuilder(EDCDataConfig config, EDCDataExpressionBuilder exprBuilder);

    protected abstract EDCDataTableBuilder<S> createTableBuilder(EDCDataConfig config, EDCDataExpressionBuilder exprBuilder);

    /**
     * Add common expected errors. DBMS-specific subclasses can add more.
     */
    protected void addCommonExpectedErrors() {
        errors.add("Data too long for column");
        errors.add("Truncated incorrect");
        errors.add("Out of range value");
        errors.add("Division by zero");
        errors.add("Invalid use of group function");
        errors.add("Unknown column");
        errors.add("Table doesn't exist");
        errors.add("Incorrect datetime value");
        errors.add("Incorrect date value");
        errors.add("Incorrect time value");
    }

    /**
     * Return the set of zero-argument function names.
     * Subclasses can override to customize (e.g. PostgreSQL uses different names).
     */
    protected Set<String> getZeroArgFunctions() {
        return DEFAULT_ZERO_ARG_FUNCTIONS;
    }

    @Override
    public void check() throws Exception {
        // 1. Generate test scenario
        EDCDataTestScenario scenario = generateTestScenario();

        try {
            // 2. Create original table
            tableBuilder.createOriginalTable(scenario, errors);

            // 3. Insert test row to validate expression
            tableBuilder.insertRandomData(scenario, errors);

            // 4. Validate test expression
            if (!tableBuilder.validateTestExpression(scenario, errors)) {
                throw new IgnoreMeException();
            }

            // 5. Insert more random data
            int additionalRows = state.getRandomly().getInteger(1, 30);
            for (int i = 0; i < additionalRows; i++) {
                tableBuilder.insertRandomData(scenario, errors);
            }

            // 6. Create derived table
            tableBuilder.createDerivedTable(scenario, errors);

            // 7. Get derived column type
            String exprType = tableBuilder.getDerivedColumnType(scenario, errors);

            // 8. Run SELECT iterations
            for (int i = 0; i < config.getSelectCount(); i++) {
                String[] queries = generateQueries(scenario, exprType);
                String baseQuery = queries[0];
                String derivedQuery = queries[1];

                List<String> baseResult = executeQuery(baseQuery);
                List<String> derivedResult = executeQuery(derivedQuery);

                if (!resultComparator.areEqual(baseResult, derivedResult)) {
                    reproducer = new EDCDataReproducer<>(scenario, baseQuery, derivedQuery);
                    lastQueryString = baseQuery;
                    throw new AssertionError(formatBugReport(scenario, baseQuery, derivedQuery,
                            baseResult, derivedResult));
                }
            }
        } finally {
            // Cleanup: drop tables
            cleanupTables(scenario);
        }
    }

    /**
     * Generate a random test scenario.
     */
    protected EDCDataTestScenario generateTestScenario() {
        scenarioCounter++;

        // Select operation type
        EDCDataOperationType opType = Randomly.fromOptions(EDCDataOperationType.values());

        // Select operation from seed file
        String op;
        List<EDCDataOperationDefinition> opList;
        switch (opType) {
            case AGGREGATE:
                opList = config.getAggregates();
                break;
            case FUNCTION:
                opList = config.getFunctions();
                break;
            case PREDICATE:
                opList = config.getPredicates();
                break;
            default:
                throw new IllegalStateException("Unknown operation type");
        }
        op = Randomly.fromList(opList).getName();

        // Generate column count
        int testColumn = state.getRandomly().getInteger(1, config.getTestColumnCount() + 1);
        int otherColumn = state.getRandomly().getInteger(0, config.getOtherColumnCount() + 1);

        // For 0-arg functions (PI, NOW, UUID, etc.), set testColumn = 0
        if (opType == EDCDataOperationType.FUNCTION && getZeroArgFunctions().contains(op)) {
            testColumn = 0;
        }

        // For AGGREGATE, PREDICATE, and 0-arg FUNCTION, ensure at least 1 other column
        if (opType == EDCDataOperationType.AGGREGATE || opType == EDCDataOperationType.PREDICATE
                || (opType == EDCDataOperationType.FUNCTION && testColumn == 0)) {
            otherColumn = Math.max(1, otherColumn);
        }

        // For PREDICATE, ensure we have enough test columns for the operation
        if (opType == EDCDataOperationType.PREDICATE) {
            if (op.equals("IS NULL") || op.equals("IS NOT NULL")) {
                testColumn = 1;
            } else if (op.equals("LIKE") || op.equals("NOT LIKE") || op.equals("IS")
                    || op.equals(">") || op.equals("<") || op.equals("<=") || op.equals(">=") || op.equals("<>")
                    || op.equals("AND") || op.equals("OR") || op.equals("XOR")
                    || op.equals("<=>") || op.equals("REGEXP") || op.equals("NOT REGEXP")
                    || op.equals("ILIKE")) {
                testColumn = Math.max(2, testColumn);
            } else if (op.equals("BETWEEN")) {
                testColumn = Math.max(3, testColumn);
            } else if (op.equals("IN") || op.equals("NOT IN")) {
                testColumn = Math.max(2, testColumn);
            }
        }

        // Generate column types and names
        List<String> columnTypes = new ArrayList<>();
        List<String> columnNames = new ArrayList<>();
        for (int i = 0; i < testColumn + otherColumn; i++) {
            String columnType = Randomly.fromList(config.getDataTypes());

            // Handle special types
            if (columnType.startsWith("ENUM")) {
                int enumSize = state.getRandomly().getInteger(2, 6);
                List<String> enumValues = new ArrayList<>();
                for (int j = 0; j < enumSize; j++) {
                    enumValues.add("'val" + j + "'");
                }
                columnType = "ENUM(" + String.join(",", enumValues) + ")";
            }
            if (columnType.startsWith("CHAR") || columnType.startsWith("VAR") || columnType.equals("BINARY")) {
                int columnLength = state.getRandomly().getInteger(1, 31);
                columnType = columnType + "(" + columnLength + ")";
            }
            if (columnType.equals("VECTOR")) {
                int columnLength = state.getRandomly().getInteger(1, 11);
                columnType = "VECTOR(" + columnLength + ")";
            }

            columnTypes.add(columnType);
            columnNames.add("c" + i);
        }

        List<String> testColumnNames = columnNames.subList(0, testColumn);
        List<String> otherColumnNames = columnNames.subList(testColumn, columnNames.size());
        List<String> otherColumnTypes = columnTypes.subList(testColumn, columnTypes.size());

        // Generate test expression
        String testExpr;
        try {
            testExpr = generateTestExpression(op, opType, columnTypes, testColumnNames);
        } catch (IllegalArgumentException e) {
            throw new IgnoreMeException(); // Skip invalid scenarios
        }

        // Generate table names
        String baseTableName = "edc_t0_" + scenarioCounter;
        String derivedTableName = "edc_t1_" + scenarioCounter;
        String derivedColumnName = "c0";

        return new EDCDataTestScenario(op, opType, columnNames, columnTypes, testExpr,
                baseTableName, derivedTableName, derivedColumnName, testColumnNames,
                otherColumnNames, otherColumnTypes);
    }

    /**
     * Generate test expression based on operation type.
     * Equivalent to EDC Python's generate_equal_expr.
     */
    protected String generateTestExpression(String op, EDCDataOperationType opType,
                                             List<String> columnTypes, List<String> columnNames) {
        switch (opType) {
            case AGGREGATE:
                return op + "(" + String.join(",", columnNames) + ")";
            case FUNCTION:
                if (getZeroArgFunctions().contains(op)) {
                    return op + "()";
                } else if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/") || op.equals("%") || op.equals("DIV")) {
                    return String.join(op, columnNames);
                } else {
                    return op + "(" + String.join(",", columnNames) + ")";
                }
            case PREDICATE:
                if ((op.equals("IS NULL") || op.equals("IS NOT NULL")) && columnNames.size() >= 1) {
                    return columnNames.get(0) + " " + op;
                } else if ((op.equals("LIKE") || op.equals("NOT LIKE") || op.equals("IS")) && columnNames.size() >= 2) {
                    return columnNames.get(0) + " " + op + " " + columnNames.get(1);
                } else if ((op.equals(">") || op.equals("<") || op.equals("<=") || op.equals(">=") || op.equals("<>")
                        || op.equals("AND") || op.equals("OR") || op.equals("XOR") || op.equals("=")
                        || op.equals("<=>") || op.equals("REGEXP") || op.equals("NOT REGEXP")
                        || op.equals("ILIKE"))
                        && columnNames.size() >= 2) {
                    return columnNames.get(0) + " " + op + " " + columnNames.get(1);
                } else if (op.equals("BETWEEN") && columnNames.size() >= 3) {
                    return columnNames.get(0) + " " + op + " " + columnNames.get(1) + " AND " + columnNames.get(2);
                } else if ((op.equals("IN") || op.equals("NOT IN")) && columnNames.size() > 1) {
                    return columnNames.get(0) + " " + op + " (" + String.join(",", columnNames.subList(1, columnNames.size())) + ")";
                } else if (columnNames.size() >= 2) {
                    // Fallback for any binary predicate
                    return columnNames.get(0) + " " + op + " " + columnNames.get(1);
                } else {
                    throw new IllegalArgumentException("Invalid operation type and op combination: " + op + " with " + columnNames.size() + " columns");
                }
            default:
                throw new IllegalStateException("Unknown operation type");
        }
    }

    /**
     * Generate base and derived queries based on operation type.
     */
    protected String[] generateQueries(EDCDataTestScenario scenario, String exprType) {
        switch (scenario.getOpType()) {
            case AGGREGATE:
                return queryBuilder.generateAggSelect(scenario, exprType);
            case FUNCTION:
                return queryBuilder.generateFuncSelect(scenario, exprType);
            case PREDICATE:
                return queryBuilder.generatePredSelect(scenario, exprType);
            default:
                throw new IllegalStateException("Unknown operation type");
        }
    }

    /**
     * Execute a query and return results as a list of strings.
     */
    protected List<String> executeQuery(String query) throws SQLException {
        List<String> result = new ArrayList<>();
        SQLQueryAdapter q = new SQLQueryAdapter(query, errors);
        SQLancerResultSet rs = q.executeAndGet(state);

        if (rs == null) {
            throw new IgnoreMeException();
        }

        try {
            int columnCount = rs.getColumnCount();
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    String value = rs.getString(i);
                    if (value != null) {
                        value = value.replaceAll("\\.0+$", "");
                    }
                    row.append(value).append(",");
                }
                result.add(row.toString());
            }
        } finally {
            rs.close();
        }

        return result;
    }

    /**
     * Clean up tables after test scenario.
     */
    protected void cleanupTables(EDCDataTestScenario scenario) {
        try {
            String dropDerived = "DROP TABLE IF EXISTS " + scenario.getDerivedTableName();
            String dropBase = "DROP TABLE IF EXISTS " + scenario.getBaseTableName();
            new SQLQueryAdapter(dropDerived, errors, true).execute(state);
            new SQLQueryAdapter(dropBase, errors, true).execute(state);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    /**
     * Format a bug report when result mismatch is detected.
     */
    protected String formatBugReport(EDCDataTestScenario scenario, String baseQuery, String derivedQuery,
                                      List<String> baseResult, List<String> derivedResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("EDC_DATA bug detected!\n");
        sb.append("Scenario: ").append(scenario).append("\n");
        sb.append("Base query: ").append(baseQuery).append("\n");
        sb.append("Derived query: ").append(derivedQuery).append("\n");
        sb.append("Base result size: ").append(baseResult.size()).append("\n");
        sb.append("Derived result size: ").append(derivedResult.size()).append("\n");

        if (baseResult.size() <= 10 && derivedResult.size() <= 10) {
            sb.append("Base results:\n");
            for (String row : baseResult) {
                sb.append("  ").append(row).append("\n");
            }
            sb.append("Derived results:\n");
            for (String row : derivedResult) {
                sb.append("  ").append(row).append("\n");
            }
        }

        return sb.toString();
    }

    @Override
    public Reproducer<S> getLastReproducer() {
        return reproducer;
    }

    @Override
    public String getLastQueryString() {
        return lastQueryString;
    }

    /**
     * Reproducer for EDC_DATA bugs.
     */
    protected static class EDCDataReproducer<S extends SQLGlobalState<?, ?>> implements Reproducer<S> {
        private final EDCDataTestScenario scenario;
        private final String baseQuery;
        private final String derivedQuery;

        public EDCDataReproducer(EDCDataTestScenario scenario, String baseQuery, String derivedQuery) {
            this.scenario = scenario;
            this.baseQuery = baseQuery;
            this.derivedQuery = derivedQuery;
        }

        @Override
        public boolean bugStillTriggers(S globalState) {
            // Simplified reproducer - just check if the scenario can be recreated
            // Full implementation would recreate tables and re-run queries
            return false;
        }

        public EDCDataTestScenario getScenario() {
            return scenario;
        }

        public String getBaseQuery() {
            return baseQuery;
        }

        public String getDerivedQuery() {
            return derivedQuery;
        }
    }
}
