package sqlancer.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import sqlancer.SQLConnection;
import sqlancer.mysql.MySQLSchema.MySQLTable;

/**
 * MySQL Raw Database Construction Helper for EDC Oracle.
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
public class MySQLEDC {

    private final MySQLGlobalState state;

    /**
     * Map of table names to their CREATE TABLE statements (used for raw DB construction).
     */
    public final Map<String, String> createTableStatements = new HashMap<>();

    public MySQLEDC(MySQLGlobalState state) {
        this.state = state;
    }

    /**
     * Create raw database without constraints.
     *
     * @return MySQLGlobalState for the raw database
     */
    public MySQLGlobalState createRawDB() throws SQLException {
        state.getState().logStatement("========Create RawDB========");

        // 1. Build connection to MySQL server
        String url = String.format(
                "jdbc:mysql://%s:%d?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true",
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

        // 3. Switch to raw database
        state.getState().logStatement("USE " + rawDB);
        statement.execute("USE " + rawDB);

        // 4. Copy tables WITHOUT constraints
        for (MySQLTable table : state.getSchema().getDatabaseTablesWithoutViews()) {
            copyTableWithoutConstraints(statement, table);
        }

        state.getState().logStatement("========Finish Create========");
        statement.close();

        // 5. Create and return GlobalState for raw DB
        MySQLGlobalState rawState = new MySQLGlobalState();
        rawState.setDatabaseName(rawDB);
        rawState.setConnection(new SQLConnection(conn));
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
     * @param statement SQL statement for execution
     * @param table Table to copy
     */
    private void copyTableWithoutConstraints(Statement statement, MySQLTable table) throws SQLException {
        String tableName = table.getName();

        // Get column information from original database
        StringBuilder createTableBuilder = new StringBuilder();
        createTableBuilder.append("CREATE TABLE ");
        createTableBuilder.append(tableName);
        createTableBuilder.append("(");

        ResultSet resultSet = statement.executeQuery(
                "SHOW FULL COLUMNS FROM " + state.getDatabaseName() + "." + tableName);

        while (resultSet.next()) {
            String columnName = resultSet.getString("Field"); // Column name
            String columnType = resultSet.getString("Type"); // Column type (includes width, precision)
            String collation = resultSet.getString("Collation"); // Collation if applicable

            createTableBuilder.append(columnName);
            createTableBuilder.append(" ").append(columnType);

            // Add collation if non-default (utf8mb4_0900_ai_ci is MySQL default)
            if (collation != null && !collation.equals("utf8mb4_0900_ai_ci")) {
                createTableBuilder.append(" COLLATE \"").append(collation).append("\"");
            }

            // NO constraints added - pure data columns only
            createTableBuilder.append(",");
        }

        String createTableString = createTableBuilder.toString();
        createTableString = createTableString.substring(0, createTableString.length() - 1); // Remove last comma
        createTableString += ")";

        // Create table in raw DB
        state.getState().logStatement(createTableString);
        statement.execute(createTableString);

        // Copy data from original table
        String copyData = String.format("INSERT INTO %s SELECT * FROM %s.%s", tableName, state.getDatabaseName(),
                tableName);
        state.getState().logStatement(copyData);
        statement.execute(copyData);

        // Record CREATE TABLE statement for potential reuse
        createTableStatements.put(tableName, createTableString);
    }
}