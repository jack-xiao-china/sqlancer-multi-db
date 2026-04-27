package sqlancer.gaussdbpg;

import java.sql.Connection;
import java.sql.DriverManager;
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
import sqlancer.gaussdbpg.gen.GaussDBPGInsertGenerator;
import sqlancer.gaussdbpg.gen.GaussDBPGTableGenerator;

@AutoService(DatabaseProvider.class)
public class GaussDBPGProvider extends SQLProviderAdapter<GaussDBPGGlobalState, GaussDBPGOptions> {

    private static boolean driverLoaded = false;

    public GaussDBPGProvider() {
        super(GaussDBPGGlobalState.class, GaussDBPGOptions.class);
    }

    private static synchronized void loadDriver() {
        if (driverLoaded) {
            return;
        }
        // Load openGauss JDBC driver
        try {
            Class.forName("org.opengauss.Driver");
            System.err.println("[INFO] Loaded openGauss JDBC driver (org.opengauss.Driver)");
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            throw new AssertionError("JDBC driver not found. Please ensure lib/opengauss-jdbc.jar is in classpath.", e);
        }
    }

    @Override
    public void generateDatabase(GaussDBPGGlobalState globalState) throws Exception {
        while (globalState.getSchema().getDatabaseTables().size() < (int) Randomly.getNotCachedInteger(1, 3)) {
            String tableName = DBMSCommon.createTableName(globalState.getSchema().getDatabaseTables().size());
            SQLQueryAdapter createTable = GaussDBPGTableGenerator.generate(globalState, tableName);
            globalState.executeStatement(createTable);
        }
        int inserts = (int) Randomly.getNotCachedInteger(1, globalState.getOptions().getMaxNumberInserts());
        for (int i = 0; i < inserts; i++) {
            globalState.executeStatement(GaussDBPGInsertGenerator.insertRow(globalState));
        }
        globalState.executeStatement(new SQLQueryAdapter("COMMIT", true));
    }

    @Override
    public SQLConnection createDatabase(GaussDBPGGlobalState globalState) throws SQLException {
        loadDriver();

        MainOptions options = globalState.getOptions();
        String username = options.getUserName();
        String password = options.getPassword();
        String host = options.getHost();
        int port = options.getPort();

        // Get the target database name (PG-compatible database)
        // User should create this database first: CREATE DATABASE tpg WITH dbcompatibility 'pg';
        GaussDBPGOptions gaussdbOptions = globalState.getDbmsSpecificOptions();
        String targetDatabase = gaussdbOptions != null && gaussdbOptions.targetDatabase != null
                ? gaussdbOptions.targetDatabase : "postgres";

        // Build JDBC URL using opengauss scheme (preferred) or postgresql as fallback
        String baseParams = "sslmode=disable&connectTimeout=10&socketTimeout=30";

        Connection con = null;
        SQLException lastError = null;
        String jdbcUrl = null;

        String configuredUrl = options.getConnectionURL();
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
            // Try multiple URL schemes: opengauss first, then postgresql as fallback
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
            String msg = "Connection failed to GaussDB-PG. Last error: " + (lastError != null ? lastError.getMessage() : "null");
            msg += "\n\nPossible solutions:";
            msg += "\n1. Ensure lib/opengauss-jdbc.jar is in classpath";
            msg += "\n2. Use --connection-url to specify full JDBC URL";
            msg += "\n3. Create a PG-compatible database: CREATE DATABASE tpg WITH dbcompatibility 'pg';";
            msg += "\n4. Use --target-database option to specify your PG-compatible database";
            msg += "\n5. Verify host, port, username, password are correct";
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

        String schemaName = globalState.getDatabaseName().toLowerCase();

        // Create schema for test isolation
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
        return "gaussdb-pg";
    }

    @Override
    public boolean addRowsToAllTables(GaussDBPGGlobalState globalState) throws Exception {
        for (GaussDBPGSchema.GaussDBPGTable table : globalState.getSchema().getDatabaseTables()) {
            if (table.getNrRows(globalState) == 0) {
                globalState.executeStatement(GaussDBPGInsertGenerator.insertRow(globalState, table));
            }
        }
        return true;
    }

    public static void printOracleHelp() {
        System.out.println();
        System.out.println("GaussDB PG --oracle choices: " + java.util.Arrays.toString(GaussDBPGOracleFactory.values()));
    }
}