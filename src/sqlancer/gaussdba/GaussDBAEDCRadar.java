package sqlancer.gaussdba;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import sqlancer.SQLConnection;
import sqlancer.gaussdba.GaussDBASchema.GaussDBATable;

/**
 * GaussDB-A Raw Database (Schema) Construction Helper for EDC_RADAR Oracle.
 *
 * Creates a "raw schema" within the same target database, containing copies of all tables
 * WITHOUT any constraints:
 * - NOT NULL constraints removed
 * - UNIQUE constraints removed
 * - PRIMARY KEY constraints removed
 * - FOREIGN KEY constraints removed
 * - CHECK constraints removed
 *
 * GaussDB-A uses Oracle compatibility mode with schema isolation (not database isolation).
 * This helper creates a new schema within the target database and opens a separate connection
 * with search_path pointing to the raw schema.
 */
public class GaussDBAEDCRadar {

    private final GaussDBAGlobalState state;

    public GaussDBAEDCRadar(GaussDBAGlobalState state) {
        this.state = state;
    }

    /**
     * Create raw schema without constraints within the same target database.
     *
     * @return GaussDBAGlobalState for the raw schema (separate connection, search_path set to raw schema)
     */
    public GaussDBAGlobalState createRawDB() throws SQLException {
        state.getState().logStatement("========Create RawDB========");

        GaussDBAOptions gaussdbaOptions = state.getDbmsSpecificOptions();
        String targetDatabase = gaussdbaOptions.targetDatabase;
        String host = state.getOptions().getHost();
        int port = state.getOptions().getPort();
        String username = state.getOptions().getUserName();
        String password = state.getOptions().getPassword();

        String baseParams = "sslmode=disable&connectTimeout=10&socketTimeout=30";
        String url = String.format("jdbc:opengauss://%s:%d/%s?%s", host, port, targetDatabase, baseParams);

        Properties props = new Properties();
        props.setProperty("user", username != null ? username : "");
        props.setProperty("password", password != null ? password : "");

        Connection rawConn = DriverManager.getConnection(url, props);
        Statement rawStatement = rawConn.createStatement();

        // Create raw schema with unique name
        String originalSchema = state.getDatabaseName().toLowerCase();
        SimpleDateFormat sdf = new SimpleDateFormat("HHmmssSSS");
        String rawSchema = originalSchema + "_raw_" + sdf.format(new Date());
        rawSchema = rawSchema.replace("-", "").replace("_raw", "_raw").substring(0, Math.min(rawSchema.length(), 63));

        String dropSql = "DROP SCHEMA IF EXISTS " + rawSchema + " CASCADE";
        String createSql = "CREATE SCHEMA " + rawSchema;

        state.getState().logStatement(dropSql);
        rawStatement.execute(dropSql);

        state.getState().logStatement(createSql);
        rawStatement.execute(createSql);

        // Set search_path to raw schema
        rawStatement.execute("SET search_path TO " + rawSchema);

        // Copy tables WITHOUT constraints from original schema
        for (GaussDBATable table : state.getSchema().getDatabaseTablesWithoutViews()) {
            copyTableWithoutConstraints(rawConn, originalSchema, rawSchema, table);
        }

        state.getState().logStatement("========Finish Create RawDB========");
        rawStatement.close();

        // Create and return GlobalState for raw schema
        GaussDBAGlobalState rawState = new GaussDBAGlobalState();
        rawState.setDatabaseName(rawSchema);
        rawState.setConnection(new SQLConnection(rawConn));
        rawState.setMainOptions(state.getOptions());
        rawState.setRandomly(state.getRandomly());
        rawState.setDbmsSpecificOptions(state.getDbmsSpecificOptions());
        rawState.setStateLogger(state.getLogger());
        rawState.setState(state.getState());

        return rawState;
    }

    /**
     * Copy table from original schema to raw schema without constraints.
     *
     * Uses information_schema.columns to get column metadata and creates
     * constraint-free tables in the raw schema.
     */
    private void copyTableWithoutConstraints(Connection rawConn, String originalSchema, String rawSchema,
            GaussDBATable table) throws SQLException {
        String tableName = table.getName();
        String qualifiedName = originalSchema + "." + tableName;

        // Query column metadata from information_schema
        StringBuilder createTableSql = new StringBuilder();
        createTableSql.append("CREATE TABLE ").append(tableName).append("(");

        try (Statement origStatement = state.getConnection().createStatement()) {
            String metaQuery = "SELECT column_name, data_type, character_maximum_length, numeric_precision, "
                    + "numeric_scale, datetime_precision FROM information_schema.columns "
                    + "WHERE table_schema = '" + originalSchema + "' AND table_name = '" + tableName + "' "
                    + "ORDER BY ordinal_position";

            ResultSet metaRs = origStatement.executeQuery(metaQuery);
            boolean firstColumn = true;

            while (metaRs.next()) {
                String colName = metaRs.getString("column_name");
                String dataType = metaRs.getString("data_type").toUpperCase();
                String charMaxLen = metaRs.getString("character_maximum_length");
                String numPrec = metaRs.getString("numeric_precision");
                String numScale = metaRs.getString("numeric_scale");

                if (!firstColumn) {
                    createTableSql.append(", ");
                }
                firstColumn = false;

                createTableSql.append("\"").append(colName).append("\" ");

                // Map data type
                String colType = mapDataType(dataType, charMaxLen, numPrec, numScale);
                createTableSql.append(colType);
                // NO constraints added
            }
            metaRs.close();
        }

        createTableSql.append(")");

        state.getState().logStatement(createTableSql.toString());
        try (Statement s = rawConn.createStatement()) {
            s.execute(createTableSql.toString());
        }

        // Copy data from original schema table to raw schema table
        try (Statement origStatement = state.getConnection().createStatement()) {
            String selectAll = "SELECT * FROM " + qualifiedName;
            ResultSet dataRs = origStatement.executeQuery(selectAll);
            java.sql.ResultSetMetaData metaData = dataRs.getMetaData();
            int columnCount = metaData.getColumnCount();

            StringBuilder batchInsert = new StringBuilder();
            int batchSize = 0;
            final int MAX_BATCH_SIZE = 100;

            while (dataRs.next()) {
                if (batchSize == 0) {
                    batchInsert.append("INSERT INTO ").append(tableName).append(" VALUES ");
                } else {
                    batchInsert.append(", ");
                }

                batchInsert.append("(");
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) {
                        batchInsert.append(", ");
                    }

                    String strValue = dataRs.getString(i);
                    if (dataRs.wasNull() || strValue == null) {
                        batchInsert.append("NULL");
                    } else {
                        String columnType = metaData.getColumnTypeName(i).toLowerCase();

                        if (columnType.contains("int") || columnType.contains("serial") || columnType.contains("number")
                                || columnType.contains("numeric") || columnType.contains("float")
                                || columnType.contains("double") || columnType.contains("real")
                                || columnType.contains("decimal") || columnType.contains("smallint")
                                || columnType.contains("bigint") || columnType.contains("tinyint")) {
                            batchInsert.append(strValue);
                        } else if (columnType.contains("bool")) {
                            batchInsert.append(strValue);
                        } else {
                            batchInsert.append("'").append(strValue.replace("'", "''")).append("'");
                        }
                    }
                }
                batchInsert.append(")");
                batchSize++;

                if (batchSize >= MAX_BATCH_SIZE) {
                    state.getState().logStatement(batchInsert.toString() + "; -- batch of " + batchSize + " rows");
                    try (Statement s = rawConn.createStatement()) {
                        s.execute(batchInsert.toString());
                    }
                    batchInsert.setLength(0);
                    batchSize = 0;
                }
            }

            if (batchSize > 0) {
                state.getState().logStatement(
                        batchInsert.toString() + "; -- final batch of " + batchSize + " rows");
                try (Statement s = rawConn.createStatement()) {
                    s.execute(batchInsert.toString());
                }
            }

            dataRs.close();
        }
    }

    /**
     * Map GaussDB-A/Oracle data types for raw table creation.
     */
    private String mapDataType(String dataType, String charMaxLen, String numPrec, String numScale) {
        switch (dataType) {
        case "VARCHAR2":
        case "CHARACTER VARYING":
            if (charMaxLen != null) {
                return "VARCHAR2(" + charMaxLen + ")";
            }
            return "VARCHAR2(255)";
        case "CHAR":
        case "CHARACTER":
            if (charMaxLen != null) {
                return "CHAR(" + charMaxLen + ")";
            }
            return "CHAR(1)";
        case "NUMBER":
        case "NUMERIC":
        case "DECIMAL":
            if (numPrec != null && numScale != null) {
                return dataType + "(" + numPrec + "," + numScale + ")";
            } else if (numPrec != null) {
                return dataType + "(" + numPrec + ")";
            }
            return "NUMBER";
        case "INTEGER":
        case "INT":
        case "INT4":
            return "INTEGER";
        case "BIGINT":
        case "INT8":
            return "BIGINT";
        case "SMALLINT":
        case "INT2":
            return "SMALLINT";
        case "TINYINT":
            return "TINYINT";
        case "FLOAT":
        case "FLOAT8":
        case "DOUBLE PRECISION":
            return "FLOAT";
        case "REAL":
        case "FLOAT4":
            return "REAL";
        case "DATE":
            return "DATE";
        case "TIME":
        case "TIME WITHOUT TIME ZONE":
            return "TIME";
        case "TIMESTAMP":
        case "TIMESTAMP WITHOUT TIME ZONE":
            return "TIMESTAMP";
        case "BOOLEAN":
            return "BOOLEAN";
        case "TEXT":
            return "TEXT";
        case "CLOB":
            return "CLOB";
        case "BLOB":
            return "BLOB";
        case "RAW":
            return "RAW";
        case "JSON":
            return "JSON";
        default:
            // Fall back to the data type as-is
            return dataType;
        }
    }
}
