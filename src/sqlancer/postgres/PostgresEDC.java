package sqlancer.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import sqlancer.SQLConnection;
import sqlancer.postgres.PostgresSchema.PostgresTable;

/**
 * PostgreSQL Raw Database Construction Helper for EDC Oracle.
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
 * This allows comparing optimized query execution (original DB with constraints)
 * against non-optimized execution (raw DB without constraints) to detect
 * optimizer bugs where constraints are incorrectly handled.
 */
public class PostgresEDC {

    private final PostgresGlobalState state;

    /**
     * Map of table names to their CREATE TABLE statements (used for raw DB construction).
     */
    public final Map<String, String> createTableStatements = new HashMap<>();

    public PostgresEDC(PostgresGlobalState state) {
        this.state = state;
    }

    /**
     * Create raw database without constraints.
     *
     * @return PostgresGlobalState for the raw database
     */
    public PostgresGlobalState createRawDB() throws SQLException {
        state.getState().logStatement("========Create RawDB========");

        // 1. Build connection to PostgreSQL server
        String url = String.format("jdbc:postgresql://%s:%d/postgres?ssl=false",
                state.getOptions().getHost(), state.getOptions().getPort());
        Connection conn = DriverManager.getConnection(url, state.getOptions().getUserName(),
                state.getOptions().getPassword());
        Statement statement = conn.createStatement();

        // 2. Create raw database with "_raw" suffix
        String rawDB = state.getDatabaseName() + "_raw";
        state.getState().logStatement("DROP DATABASE IF EXISTS " + rawDB);
        state.getState().logStatement("CREATE DATABASE " + rawDB);
        statement.execute("DROP DATABASE IF EXISTS " + rawDB);
        statement.execute("CREATE DATABASE " + rawDB);

        // 3. Close current connection and connect to raw database
        statement.close();
        conn.close();

        // 4. Connect to raw database
        String rawUrl = String.format("jdbc:postgresql://%s:%d/%s?ssl=false",
                state.getOptions().getHost(), state.getOptions().getPort(), rawDB);
        Connection rawConn = DriverManager.getConnection(rawUrl, state.getOptions().getUserName(),
                state.getOptions().getPassword());
        Statement rawStatement = rawConn.createStatement();

        // 5. Copy enum types first (needed for table creation)
        copyEnumTypes(rawStatement);

        // 6. Copy tables WITHOUT constraints
        for (PostgresTable table : state.getSchema().getDatabaseTablesWithoutViews()) {
            if (!table.isPartition() && !table.isPartitioned()) {
                copyTableWithoutConstraints(rawStatement, table);
            }
        }

        state.getState().logStatement("========Finish Create========");
        rawStatement.close();

        // 6. Create and return GlobalState for raw DB
        PostgresGlobalState rawState = new PostgresGlobalState();
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
     * Copy enum types to raw database.
     *
     * Enum types must be created before tables that use them.
     *
     * @param statement SQL statement for execution (connected to raw DB)
     */
    private void copyEnumTypes(Statement statement) throws SQLException {
        // Query enum types from original database
        try (Statement origStatement = state.getConnection().createStatement()) {
            ResultSet enumRs = origStatement.executeQuery(
                    "SELECT t.typname, array_agg(e.enumlabel ORDER BY e.enumsortorder) as enumlabels " +
                    "FROM pg_type t " +
                    "JOIN pg_enum e ON t.oid = e.enumtypid " +
                    "JOIN pg_namespace n ON n.oid = t.typnamespace " +
                    "WHERE n.nspname = 'public' AND t.typtype = 'e' " +
                    "GROUP BY t.typname");

            while (enumRs.next()) {
                String enumName = enumRs.getString("typname");
                Object[] labels = (Object[]) enumRs.getArray("enumlabels").getArray();

                StringBuilder createEnum = new StringBuilder();
                createEnum.append("CREATE TYPE ").append(enumName).append(" AS ENUM (");
                for (int i = 0; i < labels.length; i++) {
                    if (i > 0) {
                        createEnum.append(", ");
                    }
                    createEnum.append("'").append(labels[i].toString().replace("'", "''")).append("'");
                }
                createEnum.append(")");

                state.getState().logStatement(createEnum.toString());
                statement.execute(createEnum.toString());
            }
            enumRs.close();
        }
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
     * Note: PostgreSQL doesn't support cross-database queries, so we use a different
     * approach - we use PostgreSQL's COPY command through STDIN/STDOUT for efficient
     * data transfer between databases.
     *
     * @param statement SQL statement for execution (connected to raw DB)
     * @param table Table to copy
     */
    private void copyTableWithoutConstraints(Statement statement, PostgresTable table) throws SQLException {
        String tableName = table.getName();

        // Get column information from original database using PostgreSQL system catalogs
        StringBuilder createTableBuilder = new StringBuilder();
        createTableBuilder.append("CREATE TABLE ");
        createTableBuilder.append(tableName);
        createTableBuilder.append(" (");

        // Query column information from the ORIGINAL database (state's connection)
        try (Statement origStatement = state.getConnection().createStatement()) {
            ResultSet resultSet = origStatement.executeQuery(
                    "SELECT column_name, data_type, udt_name, " +
                    "pg_catalog.format_type(atttypid, atttypmod) as formatted_type " +
                    "FROM information_schema.columns " +
                    "JOIN pg_catalog.pg_attribute ON attname = column_name " +
                    "JOIN pg_catalog.pg_class ON pg_class.oid = attrelid AND pg_class.relname = '" + tableName + "' " +
                    "JOIN pg_catalog.pg_namespace ON pg_namespace.oid = pg_class.relnamespace AND pg_namespace.nspname = 'public' " +
                    "WHERE table_schema = 'public' AND table_name = '" + tableName + "' " +
                    "AND attnum > 0 ORDER BY ordinal_position");

            boolean firstColumn = true;
            while (resultSet.next()) {
                String columnName = resultSet.getString("column_name");
                String dataType = resultSet.getString("data_type");
                String formattedType = resultSet.getString("formatted_type");

                if (!firstColumn) {
                    createTableBuilder.append(", ");
                }
                firstColumn = false;

                createTableBuilder.append(columnName);
                createTableBuilder.append(" ");

                // Use formatted_type for accurate type representation
                if (formattedType != null && !formattedType.isEmpty()) {
                    createTableBuilder.append(formattedType);
                } else if ("USER-DEFINED".equalsIgnoreCase(dataType)) {
                    // For enum types, use text as fallback
                    createTableBuilder.append("text");
                } else {
                    createTableBuilder.append(dataType);
                }

                // NO constraints added - pure data columns only
            }
            resultSet.close();
        }

        createTableBuilder.append(")");

        // Create table in raw DB
        state.getState().logStatement(createTableBuilder.toString());
        statement.execute(createTableBuilder.toString());

        // Copy data using efficient INSERT with subquery
        // We use a workaround: read data into memory and batch insert
        try (Statement origStatement = state.getConnection().createStatement()) {
            ResultSet dataRs = origStatement.executeQuery("SELECT * FROM " + tableName);
            java.sql.ResultSetMetaData metaData = dataRs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Batch inserts for efficiency
            StringBuilder batchInsert = new StringBuilder();
            int batchSize = 0;
            final int MAX_BATCH_SIZE = 100; // Insert in batches of 100 rows

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

                    // Use getString() for more robust handling of special values (NaN, Infinity)
                    // Check for NULL first
                    String strValue = dataRs.getString(i);
                    if (dataRs.wasNull() || strValue == null) {
                        batchInsert.append("NULL");
                    } else {
                        // Determine column type for proper formatting
                        String columnType = metaData.getColumnTypeName(i).toLowerCase();

                        // Handle numeric types - avoid quoting
                        if (columnType.contains("int") || columnType.contains("serial")
                                || columnType.contains("smallint") || columnType.contains("bigint")
                                || columnType.contains("integer")) {
                            batchInsert.append(strValue);
                        } else if (columnType.contains("real") || columnType.contains("double")
                                || columnType.contains("float") || columnType.contains("numeric")
                                || columnType.contains("decimal") || columnType.contains("money")) {
                            // For float types, handle NaN and Infinity specially
                            if (strValue.equalsIgnoreCase("NaN") || strValue.equalsIgnoreCase("Infinity")
                                    || strValue.equalsIgnoreCase("-Infinity")) {
                                batchInsert.append("'").append(strValue).append("'::")
                                        .append(metaData.getColumnTypeName(i));
                            } else {
                                batchInsert.append(strValue);
                            }
                        } else if (columnType.contains("bool")) {
                            batchInsert.append(strValue);
                        } else if (columnType.contains("json") || columnType.contains("jsonb")) {
                            // JSON needs proper quoting
                            batchInsert.append("'").append(strValue.replace("'", "''")).append("'::")
                                    .append(metaData.getColumnTypeName(i));
                        } else if (columnType.contains("bytea")) {
                            // Bytea needs hex format or escape format
                            batchInsert.append("'").append(strValue.replace("'", "''")).append("'");
                        } else if (columnType.contains("bit") || columnType.contains("varbit")) {
                            // Bit string - B'...' format
                            batchInsert.append("B'").append(strValue.replace("'", "''")).append("'");
                        } else if (columnType.contains("uuid") || columnType.contains("inet")
                                || columnType.contains("cidr") || columnType.contains("macaddr")
                                || columnType.contains("macaddr8")) {
                            // Network types need casting
                            batchInsert.append("'").append(strValue.replace("'", "''")).append("'::")
                                    .append(metaData.getColumnTypeName(i));
                        } else {
                            // All other types (text, varchar, char, date, time, timestamp, etc.)
                            batchInsert.append("'").append(strValue.replace("'", "''")).append("'");
                        }
                    }
                }
                batchInsert.append(")");
                batchSize++;

                // Execute batch when reaching max size
                if (batchSize >= MAX_BATCH_SIZE) {
                    state.getState().logStatement(batchInsert.toString() + "; -- batch of " + batchSize + " rows");
                    statement.execute(batchInsert.toString());
                    batchInsert.setLength(0);
                    batchSize = 0;
                }
            }

            // Execute remaining rows
            if (batchSize > 0) {
                state.getState().logStatement(batchInsert.toString() + "; -- final batch of " + batchSize + " rows");
                statement.execute(batchInsert.toString());
            }

            dataRs.close();
        }

        // Record CREATE TABLE statement for potential reuse
        createTableStatements.put(tableName, createTableBuilder.toString());
    }
}