package sqlancer.gaussdbm.oracle.edcdata;

import sqlancer.IgnoreMeException;
import sqlancer.common.oracle.edcdata.EDCDataConfig;
import sqlancer.common.oracle.edcdata.EDCDataExpressionBuilder;
import sqlancer.common.oracle.edcdata.EDCDataOperationType;
import sqlancer.common.oracle.edcdata.EDCDataTableBuilder;
import sqlancer.common.oracle.edcdata.EDCDataTestScenario;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdbm.GaussDBMGlobalState;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * GaussDB-M (MySQL-compatible) table builder for EDC_DATA test scenarios.
 *
 * GaussDB-M does NOT support expressions in CREATE TABLE AS SELECT (CTAS).
 * Uses a two-step approach: CREATE TABLE with explicit types + INSERT INTO SELECT.
 * Expression types are inferred via probe query with ResultSetMetaData.
 */
public class GaussDBMEDCDataTableBuilder extends EDCDataTableBuilder<GaussDBMGlobalState> {

    public GaussDBMEDCDataTableBuilder(GaussDBMGlobalState state, EDCDataConfig config, EDCDataExpressionBuilder exprBuilder) {
        super(state, config, exprBuilder);
    }

    @Override
    protected String generateCreateTableSQL(EDCDataTestScenario scenario) {
        StringBuilder columns = new StringBuilder();
        List<String> columnNames = scenario.getColumnNames();
        List<String> columnTypes = scenario.getColumnTypes();

        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0) columns.append(", ");
            columns.append(columnNames.get(i)).append(" ").append(columnTypes.get(i));
        }

        return "CREATE TABLE " + scenario.getBaseTableName() + " (" + columns + ")";
    }

    @Override
    protected String generateDerivedTableSQL(EDCDataTestScenario scenario, String testExpr,
                                              String derivedColumn, String otherColumnsWithAlias,
                                              String groupByClause) {
        // Not used — createDerivedTable() is overridden for two-step approach
        return "";
    }

    @Override
    protected String generateInsertSQL(EDCDataTestScenario scenario) {
        List<String> columnNames = scenario.getColumnNames();
        List<String> columnTypes = scenario.getColumnTypes();

        StringBuilder values = new StringBuilder();
        for (int i = 0; i < columnTypes.size(); i++) {
            if (i > 0) values.append(", ");
            values.append(exprBuilder.generateRandomValue(columnTypes.get(i)));
        }

        return "INSERT INTO " + scenario.getBaseTableName() +
                " (" + String.join(", ", columnNames) + ") VALUES (" + values + ")";
    }

    @Override
    protected String generateDerivedTypeQuery(EDCDataTestScenario scenario) {
        return "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE " +
                "TABLE_SCHEMA = current_schema AND " +
                "TABLE_NAME = '" + scenario.getDerivedTableName() + "' AND " +
                "COLUMN_NAME = '" + scenario.getDerivedColumnName() + "'";
    }

    @Override
    protected boolean requiresSeparateInsert() {
        return true;
    }

    /**
     * Override: GaussDB-M cannot use CTAS with expressions.
     * Two-step approach: infer types via probe query → CREATE TABLE → INSERT INTO SELECT.
     */
    @Override
    public void createDerivedTable(EDCDataTestScenario scenario, ExpectedErrors errors) throws SQLException {
        String testExpr = scenario.getTestExpression();
        String derivedColumn = scenario.getDerivedColumnName();
        List<String> otherColumnNames = scenario.getOtherColumnNames();

        // 1. Build SELECT expression parts
        String selectExpr = "(" + testExpr + ") AS " + derivedColumn;
        String otherSelect = "";
        String groupByClause = "";
        if (!otherColumnNames.isEmpty()) {
            List<String> aliases = new ArrayList<>();
            for (String col : otherColumnNames) {
                aliases.add(col + " AS " + col);
            }
            otherSelect = ", " + String.join(", ", aliases);
        }
        if (scenario.getOpType() == EDCDataOperationType.AGGREGATE && !otherColumnNames.isEmpty()) {
            groupByClause = "GROUP BY " + String.join(", ", otherColumnNames);
        }

        String fullSelect = selectExpr + otherSelect;
        String baseTable = scenario.getBaseTableName();

        // 2. Infer column types via probe query
        List<String> columnTypes;
        try (Statement probeStmt = state.getConnection().createStatement()) {
            ResultSet probeRs = probeStmt.executeQuery(
                    "SELECT " + fullSelect + " FROM " + baseTable + " " + groupByClause + " LIMIT 0");
            ResultSetMetaData md = probeRs.getMetaData();
            columnTypes = new ArrayList<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                columnTypes.add(mapType(md.getColumnTypeName(i), md.getColumnDisplaySize(i)));
            }
            probeRs.close();
        } catch (SQLException e) {
            state.getLogger().writeCurrent("-- Probe query failed: " + e.getMessage());
            throw new IgnoreMeException();
        }

        // 3. Build column names list
        List<String> allDerivedColumns = new ArrayList<>();
        allDerivedColumns.add(derivedColumn);
        allDerivedColumns.addAll(otherColumnNames);

        // 4. CREATE TABLE with explicit types
        StringBuilder createSQL = new StringBuilder("CREATE TABLE " + scenario.getDerivedTableName() + " (");
        for (int i = 0; i < allDerivedColumns.size(); i++) {
            if (i > 0) createSQL.append(", ");
            createSQL.append(allDerivedColumns.get(i)).append(" ").append(columnTypes.get(i));
        }
        createSQL.append(")");

        SQLQueryAdapter q = new SQLQueryAdapter(createSQL.toString(), errors, true);
        boolean success = q.execute(state);
        if (!success) {
            state.getLogger().writeCurrent("-- Failed to create derived table: " + createSQL);
            throw new IgnoreMeException();
        }

        // 5. INSERT INTO SELECT
        String insertSQL = "INSERT INTO " + scenario.getDerivedTableName() +
                " SELECT " + fullSelect + " FROM " + baseTable + " " + groupByClause;
        SQLQueryAdapter insertQ = new SQLQueryAdapter(insertSQL, errors, true);
        success = insertQ.execute(state);
        if (!success) {
            state.getLogger().writeCurrent("-- Failed to insert into derived table: " + insertSQL);
            throw new IgnoreMeException();
        }
    }

    /**
     * Map JDBC type names to GaussDB-M compatible CREATE TABLE type names.
     */
    private String mapType(String jdbcTypeName, int displaySize) {
        if (jdbcTypeName == null) {
            return "text";
        }
        switch (jdbcTypeName.toLowerCase()) {
        case "int8":
        case "bigint":
            return "bigint";
        case "int4":
        case "integer":
        case "int":
            return "integer";
        case "int2":
        case "smallint":
            return "smallint";
        case "float8":
        case "double precision":
        case "double":
            return "double precision";
        case "float4":
        case "real":
        case "float":
            return "real";
        case "numeric":
        case "decimal":
            return "numeric";
        case "bool":
        case "boolean":
            return "boolean";
        case "text":
        case "longvarchar":
        case "clob":
            return "text";
        case "varchar":
        case "character varying":
            return "varchar(" + Math.max(displaySize, 255) + ")";
        case "char":
        case "character":
            return "char(" + Math.max(displaySize, 1) + ")";
        case "date":
            return "date";
        case "timestamp":
        case "timestamptz":
        case "timestamp without time zone":
        case "timestamp with time zone":
            return "timestamp";
        case "time":
        case "timetz":
            return "time";
        case "bytea":
            return "bytea";
        case "int8 unsigned":
        case "bigint unsigned":
            return "bigint unsigned";
        case "int4 unsigned":
        case "integer unsigned":
        case "int unsigned":
            return "integer unsigned";
        case "int2 unsigned":
        case "smallint unsigned":
            return "smallint unsigned";
        case "int1":
        case "int1 unsigned":
        case "tinyint unsigned":
            return "tinyint unsigned";
        case "year":
            return "year";
        case "bit":
            return "bit";
        case "enum":
        case "set":
            return "text";
        default:
            return "text";
        }
    }
}
