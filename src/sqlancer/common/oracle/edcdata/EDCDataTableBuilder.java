package sqlancer.common.oracle.edcdata;

import sqlancer.SQLGlobalState;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Table builder for EDC_DATA test scenarios.
 * Creates original and derived tables, inserts random data.
 */
public abstract class EDCDataTableBuilder<S extends SQLGlobalState<?, ?>> {
    protected final S state;
    protected final EDCDataConfig config;
    protected final EDCDataExpressionBuilder exprBuilder;

    public EDCDataTableBuilder(S state, EDCDataConfig config, EDCDataExpressionBuilder exprBuilder) {
        this.state = state;
        this.config = config;
        this.exprBuilder = exprBuilder;
    }

    /**
     * Create the original table with random schema.
     */
    public void createOriginalTable(EDCDataTestScenario scenario, ExpectedErrors errors) throws SQLException {
        String createSQL = generateCreateTableSQL(scenario);
        SQLQueryAdapter q = new SQLQueryAdapter(createSQL, errors, true);
        q.execute(state);
    }

    /**
     * Create the derived table with precomputed expression results.
     * Equivalent to EDC Python's construct_derived_table.
     */
    public void createDerivedTable(EDCDataTestScenario scenario, ExpectedErrors errors) throws SQLException {
        String testExpr = scenario.getTestExpression();
        String derivedColumn = scenario.getDerivedColumnName();
        List<String> otherColumnNames = scenario.getOtherColumnNames();

        String otherColumnsWithAlias = "";
        if (!otherColumnNames.isEmpty()) {
            List<String> aliases = new ArrayList<>();
            for (String col : otherColumnNames) {
                aliases.add(col + " AS " + col);
            }
            otherColumnsWithAlias = ", " + String.join(", ", aliases);
        }

        String groupByClause = "";
        if (scenario.getOpType() == EDCDataOperationType.AGGREGATE && !otherColumnNames.isEmpty()) {
            groupByClause = "GROUP BY " + String.join(", ", otherColumnNames);
        }

        String createSQL = generateDerivedTableSQL(scenario, testExpr, derivedColumn, otherColumnsWithAlias, groupByClause);
        SQLQueryAdapter q = new SQLQueryAdapter(createSQL, errors, true);
        boolean success = q.execute(state);

        if (!success) {
            // Log the failed query for debugging
            state.getLogger().writeCurrent("-- Failed to create derived table: " + createSQL);
            throw new sqlancer.IgnoreMeException(); // Skip this scenario instead of failing
        }

        // For some DBMS (like PostgreSQL), we need to insert data separately
        if (requiresSeparateInsert()) {
            String insertSQL = generateDerivedInsertSQL(scenario, testExpr, otherColumnsWithAlias, groupByClause);
            SQLQueryAdapter insertQ = new SQLQueryAdapter(insertSQL, errors, true);
            success = insertQ.execute(state);
            if (!success) {
                state.getLogger().writeCurrent("-- Failed to insert into derived table: " + insertSQL);
                throw new sqlancer.IgnoreMeException();
            }
        }
    }

    /**
     * Insert random data into the original table.
     */
    public void insertRandomData(EDCDataTestScenario scenario, ExpectedErrors errors) throws SQLException {
        int rowCount = state.getRandomly().getInteger(1, 30);
        for (int i = 0; i < rowCount; i++) {
            String insertSQL = generateInsertSQL(scenario);
            SQLQueryAdapter q = new SQLQueryAdapter(insertSQL, errors, true);
            q.execute(state);
        }
    }

    /**
     * Get the data type of the derived column from information_schema.
     */
    public String getDerivedColumnType(EDCDataTestScenario scenario, ExpectedErrors errors) throws SQLException {
        String query = generateDerivedTypeQuery(scenario);
        SQLQueryAdapter q = new SQLQueryAdapter(query, errors);
        SQLancerResultSet rs = q.executeAndGet(state);

        if (rs == null) {
            throw new RuntimeException("Failed to get derived column type");
        }

        String type = null;
        if (rs.next()) {
            type = rs.getString(1);
        }
        rs.close();

        if (type == null) {
            throw new RuntimeException("Derived column type is null for " + scenario.getDerivedTableName());
        }

        return type;
    }

    /**
     * Validate that the test expression is valid on the original table.
     */
    public boolean validateTestExpression(EDCDataTestScenario scenario, ExpectedErrors errors) throws SQLException {
        String query = "SELECT " + scenario.getTestExpression() + " FROM " + scenario.getBaseTableName();
        SQLQueryAdapter q = new SQLQueryAdapter(query, errors);
        SQLancerResultSet rs;
        try {
            rs = q.executeAndGet(state);
        } catch (Exception e) {
            return false; // Expression is invalid
        }

        if (rs == null) {
            return false; // Expression is invalid
        }
        rs.close();
        return true;
    }

    // Abstract methods for DBMS-specific implementations

    protected abstract String generateCreateTableSQL(EDCDataTestScenario scenario);

    protected abstract String generateDerivedTableSQL(EDCDataTestScenario scenario, String testExpr,
                                                        String derivedColumn, String otherColumnsWithAlias,
                                                        String groupByClause);

    protected abstract String generateInsertSQL(EDCDataTestScenario scenario);

    protected abstract String generateDerivedTypeQuery(EDCDataTestScenario scenario);

    protected boolean requiresSeparateInsert() {
        return false;
    }

    protected String generateDerivedInsertSQL(EDCDataTestScenario scenario, String testExpr,
                                               String otherColumnsWithAlias, String groupByClause) {
        return "";
    }
}
