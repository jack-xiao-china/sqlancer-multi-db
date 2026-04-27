package sqlancer.gaussdba;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import com.google.auto.service.AutoService;

import sqlancer.DatabaseProvider;
import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.SQLProviderAdapter;
import sqlancer.common.DBMSCommon;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdba.gen.GaussDBADeleteGenerator;
import sqlancer.gaussdba.gen.GaussDBAInsertGenerator;
import sqlancer.gaussdba.gen.GaussDBATableGenerator;
import sqlancer.gaussdba.gen.GaussDBAUpdateGenerator;

@AutoService(DatabaseProvider.class)
public class GaussDBAProvider extends SQLProviderAdapter<GaussDBAGlobalState, GaussDBAOptions> {

    private static boolean driverLoaded = false;

    public GaussDBAProvider() {
        super(GaussDBAGlobalState.class, GaussDBAOptions.class);
    }

    private static synchronized void loadDriver() {
        if (driverLoaded) {
            return;
        }
        // Load openGauss JDBC driver (supports SM3/sha256 authentication)
        try {
            Class.forName("org.opengauss.Driver");
            System.err.println("[INFO] Loaded openGauss JDBC driver (org.opengauss.Driver)");
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            throw new AssertionError("JDBC driver not found. Please ensure lib/opengauss-jdbc.jar is in classpath.", e);
        }
    }

    @Override
    public void generateDatabase(GaussDBAGlobalState globalState) throws Exception {
        while (globalState.getSchema().getDatabaseTables().size() < (int) Randomly.getNotCachedInteger(1, 3)) {
            String tableName = DBMSCommon.createTableName(globalState.getSchema().getDatabaseTables().size());
            SQLQueryAdapter createTable = GaussDBATableGenerator.generate(globalState, tableName);
            globalState.executeStatement(createTable);
            // Force schema update after each table creation
            globalState.updateSchema();
        }
        int inserts = (int) Randomly.getNotCachedInteger(1, globalState.getOptions().getMaxNumberInserts());
        for (int i = 0; i < inserts; i++) {
            globalState.executeStatement(GaussDBAInsertGenerator.insertRow(globalState));
        }

        // Add UPDATE operations for DML testing coverage
        int updates = (int) Randomly.getNotCachedInteger(0, globalState.getOptions().getMaxNumberInserts() / 2);
        for (int i = 0; i < updates; i++) {
            try {
                globalState.executeStatement(GaussDBAUpdateGenerator.create(globalState));
            } catch (sqlancer.IgnoreMeException e) {
                // Table may not have updatable columns, skip
            }
        }

        // Add DELETE operations for DML testing coverage
        int deletes = (int) Randomly.getNotCachedInteger(0, globalState.getOptions().getMaxNumberInserts() / 4);
        for (int i = 0; i < deletes; i++) {
            try {
                globalState.executeStatement(GaussDBADeleteGenerator.create(globalState));
            } catch (sqlancer.IgnoreMeException e) {
                // Table may not have deletable rows, skip
            }
        }

        globalState.executeStatement(new SQLQueryAdapter("COMMIT", true));
    }

    /**
     * Verify database compatibility mode is 'A' (Oracle-compatible)
     */
    private void verifyCompatibilityMode(Connection con, String databaseName) throws SQLException {
        try (Statement s = con.createStatement()) {
            ResultSet rs = s.executeQuery(
                "SELECT datcompatibility FROM pg_database WHERE datname = '" + databaseName + "'");
            if (rs.next()) {
                String compatibility = rs.getString("datcompatibility");
                System.err.println("[INFO] Database '" + databaseName + "' compatibility mode: " + compatibility);
                if (!"A".equalsIgnoreCase(compatibility)) {
                    System.err.println("[WARN] Database compatibility mode is '" + compatibility + "', not 'A' (Oracle-compatible)");
                    System.err.println("[WARN] GaussDB-A module is designed for A-compatible databases");
                    System.err.println("[WARN] Some Oracle-style SQL features may not work correctly");
                    System.err.println("[WARN] To create A-compatible database: CREATE DATABASE ta WITH dbcompatibility 'A'");
                    System.err.println("[WARN] Or use --target-database option to specify an A-compatible database");
                }
            } else {
                System.err.println("[WARN] Could not verify compatibility mode for database '" + databaseName + "'");
            }
            rs.close();
        } catch (SQLException e) {
            System.err.println("[WARN] Could not query database compatibility: " + e.getMessage());
        }
    }

    @Override
    public SQLConnection createDatabase(GaussDBAGlobalState globalState) throws SQLException {
        loadDriver();

        MainOptions options = globalState.getOptions();
        String username = options.getUserName();
        String password = options.getPassword();
        String host = options.getHost();
        int port = options.getPort();

        GaussDBAOptions gaussdbaOptions = globalState.getDbmsSpecificOptions();
        String targetDatabase = gaussdbaOptions != null ? gaussdbaOptions.targetDatabase : null;

        // Require user to specify A-compatible target database
        if (targetDatabase == null || targetDatabase.isBlank()) {
            String msg = "ERROR: --target-database is REQUIRED for GaussDB-A testing.\n\n";
            msg += "Please create an A-compatible database first:\n";
            msg += "  CREATE DATABASE <db_name> WITH dbcompatibility 'A';\n\n";
            msg += "Then specify it with:\n";
            msg += "  java -jar sqlancer.jar gaussdb-a --target-database <db_name> ...\n\n";
            msg += "Example:\n";
            msg += "  CREATE DATABASE gaussdb_a_test WITH dbcompatibility 'A';\n";
            msg += "  java -jar sqlancer.jar gaussdb-a --target-database gaussdb_a_test --oracle QUERY_PARTITIONING";
            throw new SQLException(msg);
        }

        // Build JDBC URL using opengauss scheme
        String baseParams = "sslmode=disable&connectTimeout=10&socketTimeout=30";
        String jdbcUrl;
        String configuredUrl = options.getConnectionURL();

        Connection con = null;
        SQLException lastError = null;

        if (configuredUrl != null && !configuredUrl.isBlank()) {
            jdbcUrl = configuredUrl.trim();
            if (!jdbcUrl.startsWith("jdbc:")) {
                jdbcUrl = "jdbc:" + jdbcUrl;
            }
            // Add base params if not already present
            if (!jdbcUrl.contains("sslmode")) {
                jdbcUrl = jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + baseParams;
            }
            System.err.println("[INFO] Using configured URL: " + jdbcUrl);

            Properties props = new Properties();
            props.setProperty("user", username != null ? username : "");
            props.setProperty("password", password != null ? password : "");

            try {
                con = DriverManager.getConnection(jdbcUrl, props);
            } catch (SQLException e) {
                lastError = e;
                System.err.println("[ERROR] Connection failed: " + e.getMessage());
            }
        } else {
            // Use opengauss JDBC URL scheme
            String[] urlSchemes = { "opengauss" };

            for (String scheme : urlSchemes) {
                jdbcUrl = String.format("jdbc:%s://%s:%d/%s?%s", scheme, host, port, targetDatabase, baseParams);
                System.err.println("[INFO] Trying connection URL: " + jdbcUrl);

                Properties props = new Properties();
                props.setProperty("user", username != null ? username : "");
                props.setProperty("password", password != null ? password : "");

                try {
                    con = DriverManager.getConnection(jdbcUrl, props);
                    if (con != null) {
                        System.err.println("[INFO] Connected successfully using " + scheme + " scheme");
                        break;
                    }
                } catch (SQLException e) {
                    lastError = e;
                    System.err.println("[WARN] Connection failed with " + scheme + " scheme: " + e.getMessage());
                }
            }
        }

        if (con == null) {
            String msg = "Connection failed to GaussDB-A database '" + targetDatabase + "'.\n";
            msg += "Last error: " + (lastError != null ? lastError.getMessage() : "null") + "\n\n";
            msg += "Possible solutions:\n";
            msg += "1. Verify the database exists and is A-compatible:\n";
            msg += "   SELECT datcompatibility FROM pg_database WHERE datname = '" + targetDatabase + "';\n";
            msg += "   (Should return 'A')\n\n";
            msg += "2. If not A-compatible, create it:\n";
            msg += "   CREATE DATABASE " + targetDatabase + " WITH dbcompatibility 'A';\n\n";
            msg += "3. Verify host, port, username, password are correct\n";
            msg += "4. Check network connectivity to GaussDB server";
            throw new SQLException(msg, lastError);
        }

        // Print connection info
        try {
            java.sql.DatabaseMetaData md = con.getMetaData();
            System.err.println("[INFO] Connected to: " + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
            System.err.println("[INFO] JDBC Driver: " + md.getDriverName() + " " + md.getDriverVersion());
        } catch (SQLException e) {
            System.err.println("[WARN] Could not get database metadata: " + e.getMessage());
        }

        // Verify database compatibility mode
        verifyCompatibilityMode(con, targetDatabase);

        String schemaName = globalState.getDatabaseName().toLowerCase();

        // Create schema for test isolation (A兼容模式也支持schema)
        // Use lowercase to ensure consistent matching in information_schema queries
        try (Statement s = con.createStatement()) {
            String dropSql = "DROP SCHEMA IF EXISTS " + schemaName + " CASCADE";
            String createSql = "CREATE SCHEMA " + schemaName;
            String setPathSql = "SET search_path TO " + schemaName;

            globalState.getState().logStatement(dropSql);
            System.err.println("[DEBUG] Executing: " + dropSql);
            s.execute(dropSql);

            globalState.getState().logStatement(createSql);
            System.err.println("[DEBUG] Executing: " + createSql);
            s.execute(createSql);

            System.err.println("[DEBUG] Executing: " + setPathSql);
            s.execute(setPathSql);

            System.err.println("[INFO] Schema created: " + schemaName);
        }

        return new SQLConnection(con);
    }

    @Override
    public String getDBMSName() {
        return "gaussdb-a";
    }

    @Override
    public boolean addRowsToAllTables(GaussDBAGlobalState globalState) throws Exception {
        for (GaussDBASchema.GaussDBATable table : globalState.getSchema().getDatabaseTables()) {
            if (table.getNrRows(globalState) == 0) {
                globalState.executeStatement(GaussDBAInsertGenerator.insertRow(globalState, table));
            }
        }
        return true;
    }

    public static void printOracleHelp() {
        System.out.println();
        System.out.println("GaussDB-A --oracle choices: " + java.util.Arrays.toString(GaussDBAOracleFactory.values()));
    }
}