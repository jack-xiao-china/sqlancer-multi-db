package sqlancer.gaussdbm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import sqlancer.SQLConnection;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;

/**
 * GaussDB-M Raw Database Construction Helper for EDC Oracle.
 *
 * Creates "raw database" (equivalent database without constraints) for EDC testing.
 * Raw DB contains only pure data copies of tables, removing all constraints:
 * - NOT NULL constraints removed
 * - UNIQUE constraints removed
 * - PRIMARY KEY constraints removed
 * - FOREIGN KEY constraints removed
 * - CHECK constraints removed
 * - GENERATED columns converted to regular columns
 *
 * GaussDB-M uses OpenGauss JDBC driver with M-compatibility SQL syntax.
 *
 * This allows comparing optimized query execution (original DB with constraints)
 * against non-optimized execution (raw DB without constraints) to detect
 * optimizer bugs where constraints are incorrectly handled.
 */
public class GaussDBMEDC {

    private final GaussDBMGlobalState state;

    /**
     * Map of table names to their CREATE TABLE statements (used for raw DB construction).
     */
    public final Map<String, String> createTableStatements = new HashMap<>();

    public GaussDBMEDC(GaussDBMGlobalState state) {
        this.state = state;
    }

    /**
     * Create raw database without constraints.
     * Uses unique naming with timestamp suffix to avoid connection conflicts.
     *
     * @return GaussDBMGlobalState for the raw database
     */
    public GaussDBMGlobalState createRawDB() throws SQLException {
        state.getState().logStatement("========Create RawDB========");

        // 1. Build connection to GaussDB-M server using OpenGauss JDBC driver
        String baseParams = "sslmode=disable&connectTimeout=10&socketTimeout=30";
        String url = String.format("jdbc:opengauss://%s:%d/postgres?%s",
                state.getOptions().getHost(), state.getOptions().getPort(), baseParams);

        Properties props = new Properties();
        props.setProperty("user", state.getOptions().getUserName());
        props.setProperty("password", state.getOptions().getPassword());

        Connection conn = DriverManager.getConnection(url, props);
        Statement statement = conn.createStatement();

        // 2. Create raw database with unique suffix (timestamp + random) to avoid connection conflicts
        // GaussDB doesn't allow dropping databases with active connections, so we use unique names
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        String uniqueSuffix = "_raw_" + sdf.format(new Date()) + "_" + System.nanoTime() % 10000;
        String rawDB = state.getDatabaseName().toLowerCase() + uniqueSuffix;
        state.getState().logStatement("CREATE DATABASE " + rawDB + " DBCOMPATIBILITY 'M'");

        // Create the raw database (unique name avoids conflicts)
        statement.execute("CREATE DATABASE " + rawDB + " DBCOMPATIBILITY 'M'");

        // 3. Close current connection and connect to raw database
        statement.close();
        conn.close();

        String rawUrl = String.format("jdbc:opengauss://%s:%d/%s?%s",
                state.getOptions().getHost(), state.getOptions().getPort(), rawDB, baseParams);
        Connection rawConn = DriverManager.getConnection(rawUrl, props);
        Statement rawStatement = rawConn.createStatement();

        // 4. Copy tables WITHOUT constraints
        for (GaussDBTable table : state.getSchema().getDatabaseTablesWithoutViews()) {
            copyTableWithoutConstraints(rawStatement, table);
        }

        state.getState().logStatement("========Finish Create========");
        rawStatement.close();

        // 5. Create and return GlobalState for raw DB
        GaussDBMGlobalState rawState = new GaussDBMGlobalState();
        rawState.setDatabaseName(rawDB);
        rawState.setConnection(new SQLConnection(rawConn));
        rawState.setMainOptions(state.getOptions());
        rawState.setRandomly(state.getRandomly());
        rawState.setDbmsSpecificOptions(state.getDbmsSpecificOptions());
        rawState.setStateLogger(state.getLogger());
        rawState.setState(state.getState());

        return rawState;
    }

    /**
     * Copy table to raw DB without constraints.
     *
     * Creates a table with only column types and collation information.
     * All constraints are removed:
     * - No NOT NULL
     * - No UNIQUE
     * - No PRIMARY KEY
     * - No FOREIGN KEY
     * - No CHECK constraints
     * - GENERATED columns converted to regular columns (data copied directly)
     *
     * Note: GaussDB-M uses PostgreSQL-style system catalogs for cross-database queries.
     *
     * @param statement SQL statement for execution (connected to raw DB)
     * @param table Table to copy
     */
    private void copyTableWithoutConstraints(Statement statement, GaussDBTable table) throws SQLException {
        String tableName = table.getName();

        // Get column information from original database using MySQL-style SHOW COLUMNS
        // (GaussDB-M supports this in M-compatibility mode)
        StringBuilder createTableBuilder = new StringBuilder();
        createTableBuilder.append("CREATE TABLE ");
        createTableBuilder.append(tableName);
        createTableBuilder.append("(");

        try (Statement origStatement = state.getConnection().createStatement()) {
            ResultSet resultSet = origStatement.executeQuery("SHOW FULL COLUMNS FROM " + tableName);

            boolean firstColumn = true;
            while (resultSet.next()) {
                String columnName = resultSet.getString("Field");
                String columnType = resultSet.getString("Type");
                String collation = resultSet.getString("Collation");

                if (!firstColumn) {
                    createTableBuilder.append(", ");
                }
                firstColumn = false;

                createTableBuilder.append(columnName);
                createTableBuilder.append(" ").append(columnType);

                // Add collation if non-default
                if (collation != null && !collation.isEmpty() && !collation.equals("utf8mb4_0900_ai_ci")) {
                    createTableBuilder.append(" COLLATE '").append(collation).append("'");
                }

                // NO constraints added - pure data columns only
            }
            resultSet.close();
        }

        createTableBuilder.append(")");

        // Create table in raw DB
        state.getState().logStatement(createTableBuilder.toString());
        statement.execute(createTableBuilder.toString());

        // Copy data - GaussDB-M doesn't support cross-database queries like MySQL
        // Use INSERT with values from original table
        try (Statement origStatement = state.getConnection().createStatement()) {
            ResultSet dataRs = origStatement.executeQuery("SELECT * FROM " + tableName);
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

                        if (columnType.contains("int") || columnType.contains("serial")) {
                            batchInsert.append(strValue);
                        } else if (columnType.contains("double") || columnType.contains("float")
                                || columnType.contains("real") || columnType.contains("decimal")
                                || columnType.contains("numeric") || columnType.contains("money")) {
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
                    statement.execute(batchInsert.toString());
                    batchInsert.setLength(0);
                    batchSize = 0;
                }
            }

            if (batchSize > 0) {
                state.getState().logStatement(batchInsert.toString() + "; -- final batch of " + batchSize + " rows");
                statement.execute(batchInsert.toString());
            }

            dataRs.close();
        }

        createTableStatements.put(tableName, createTableBuilder.toString());
    }
}