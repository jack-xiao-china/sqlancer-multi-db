package sqlancer.gaussdbm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import com.google.auto.service.AutoService;

import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.SQLProviderAdapter;
import sqlancer.common.DBMSCommon;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.gaussdbm.gen.GaussDBMInsertGenerator;
import sqlancer.gaussdbm.gen.GaussDBMTableGenerator;

/**
 * GaussDB-M (M-Compatibility) provider.
 */
@AutoService(sqlancer.DatabaseProvider.class)
public class GaussDBMProvider extends SQLProviderAdapter<GaussDBMGlobalState, GaussDBMOptions> {

    private static boolean driverLoaded = false;

    public GaussDBMProvider() {
        super(GaussDBMGlobalState.class, GaussDBMOptions.class);
    }

    private static synchronized void loadDriver() {
        if (driverLoaded) {
            return;
        }
        // Load openGauss JDBC driver (supports all GaussDB variants)
        try {
            Class.forName("org.opengauss.Driver");
            System.err.println("[INFO] Loaded openGauss JDBC driver (org.opengauss.Driver)");
            driverLoaded = true;
            return;
        } catch (ClassNotFoundException e) {
            System.err.println("[INFO] openGauss driver not found, trying MySQL driver...");
        }
        // Fallback to MySQL driver for M-compatibility mode
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.err.println("[INFO] Loaded MySQL JDBC driver (com.mysql.cj.jdbc.Driver)");
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            throw new AssertionError("JDBC driver not found. Please ensure lib/opengauss-jdbc.jar is in classpath.", e);
        }
    }

    @Override
    public void generateDatabase(GaussDBMGlobalState globalState) throws Exception {
        // Create tables - similar to MySQLProvider's approach
        while (globalState.getSchema().getDatabaseTables().size() < (int) Randomly.getNotCachedInteger(1, 2)) {
            String tableName = DBMSCommon.createTableName(globalState.getSchema().getDatabaseTables().size());
            SQLQueryAdapter createTable = GaussDBMTableGenerator.generate(globalState, tableName);
            globalState.executeStatement(createTable);
            // Update schema after each table creation to avoid duplicate table names
            globalState.updateSchema();
        }
        int inserts = (int) Randomly.getNotCachedInteger(1, globalState.getOptions().getMaxNumberInserts());
        for (int i = 0; i < inserts; i++) {
            globalState.executeStatement(GaussDBMInsertGenerator.insertRow(globalState));
        }
    }

    @Override
    public SQLConnection createDatabase(GaussDBMGlobalState globalState) throws SQLException {
        loadDriver();

        MainOptions options = globalState.getOptions();
        String username = options.getUserName();
        String password = options.getPassword();
        String host = options.getHost();
        int port = options.getPort();

        // Default connection to postgres for creating test databases
        String baseConnectionDatabase = "postgres";

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
            if (!jdbcUrl.contains("sslmode")) {
                jdbcUrl = jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + baseParams;
            }
            System.err.println("[INFO] Using configured URL: " + jdbcUrl);

            Properties props = parseJdbcProperties(options.getJdbcProperties());
            if (username != null) {
                props.setProperty("user", username);
            }
            if (password != null) {
                props.setProperty("password", password);
            }

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
                jdbcUrl = String.format("jdbc:%s://%s:%d/%s?%s", scheme, host, port, baseConnectionDatabase, baseParams);
                System.err.println("[INFO] Trying connection URL: " + jdbcUrl);

                Properties props = parseJdbcProperties(options.getJdbcProperties());
                if (username != null) {
                    props.setProperty("user", username);
                }
                if (password != null) {
                    props.setProperty("password", password);
                }

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
            String msg = "Connection failed to GaussDB-M. Last error: " + (lastError != null ? lastError.getMessage() : "null");
            msg += "\n\nPossible solutions:";
            msg += "\n1. Ensure lib/opengauss-jdbc.jar is in classpath";
            msg += "\n2. Use --connection-url to specify full JDBC URL (jdbc:opengauss://host:port/database)";
            msg += "\n3. Verify host, port, username, password are correct";
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

        // Get test database name - use lowercase for consistent information_schema matching
        String databaseName = globalState.getDatabaseName().toLowerCase();

        // Create M-compatible test database (like MySQL does)
        globalState.getState().logStatement("DROP DATABASE IF EXISTS " + databaseName);
        String createDbSql = "CREATE DATABASE " + databaseName + " DBCOMPATIBILITY 'M'";
        globalState.getState().logStatement(createDbSql);
        globalState.getState().logStatement("USE " + databaseName);

        try (Statement s = con.createStatement()) {
            try {
                s.execute("DROP DATABASE IF EXISTS " + databaseName);
            } catch (SQLException e) {
                // Ignore if database doesn't exist
            }
            s.execute(createDbSql);
            System.err.println("[INFO] Created M-compatible database: " + databaseName);
        }

        // Reconnect to the new M-compatible database
        String newUrl;
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            // Replace database name in configured URL
            newUrl = jdbcUrl;
            // Try to replace the database name in the URL
            if (jdbcUrl.contains("/postgres?")) {
                newUrl = jdbcUrl.replace("/postgres?", "/" + databaseName + "?");
            } else if (jdbcUrl.contains("/postgres")) {
                newUrl = jdbcUrl.replace("/postgres", "/" + databaseName);
            } else {
                // Extract the database part and replace it
                int lastSlash = jdbcUrl.lastIndexOf('/');
                int questionMark = jdbcUrl.indexOf('?');
                if (lastSlash > 0) {
                    if (questionMark > lastSlash) {
                        newUrl = jdbcUrl.substring(0, lastSlash + 1) + databaseName + jdbcUrl.substring(questionMark);
                    } else {
                        newUrl = jdbcUrl.substring(0, lastSlash + 1) + databaseName;
                    }
                }
            }
        } else {
            // Build new URL with test database
            newUrl = String.format("jdbc:opengauss://%s:%d/%s?%s", host, port, databaseName, baseParams);
        }

        System.err.println("[INFO] Reconnecting to: " + newUrl);
        con.close();

        Properties props = parseJdbcProperties(options.getJdbcProperties());
        if (username != null) {
            props.setProperty("user", username);
        }
        if (password != null) {
            props.setProperty("password", password);
        }

        con = DriverManager.getConnection(newUrl, props);
        System.err.println("[INFO] Connected to M-compatible database: " + databaseName);

        // Verify M-compatibility
        try (Statement s = con.createStatement()) {
            try (ResultSet rs = s.executeQuery(
                    "SELECT datcompatibility FROM pg_database WHERE datname = '" + databaseName + "'")) {
                if (rs.next()) {
                    String compat = rs.getString("datcompatibility");
                    System.err.println("[INFO] Verified database compatibility mode: " + compat);
                    if (!"M".equals(compat)) {
                        System.err.println("[WARN] Database is not M-compatible! Expected 'M' but got '" + compat + "'");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[WARN] Could not verify compatibility mode: " + e.getMessage());
        }

        return new SQLConnection(con);
    }

    private static Properties parseJdbcProperties(String propString) {
        Properties props = new Properties();
        if (propString == null || propString.isBlank()) {
            return props;
        }
        String[] parts = propString.split(";");
        for (String p : parts) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] kv = trimmed.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            props.setProperty(kv[0].trim(), kv[1].trim());
        }
        return props;
    }

    @Override
    public String getDBMSName() {
        return "gaussdb-m";
    }

    public static void printOracleHelp() {
        System.out.println();
        System.out.println("GaussDB-M --oracle choices: " + java.util.Arrays.toString(GaussDBMOracleFactory.values()));
    }

    @Override
    public boolean addRowsToAllTables(GaussDBMGlobalState globalState) throws Exception {
        for (GaussDBMSchema.GaussDBTable table : globalState.getSchema().getDatabaseTables()) {
            if (table.getNrRows(globalState) == 0) {
                globalState.executeStatement(GaussDBMInsertGenerator.insertRow(globalState, table));
            }
        }
        return true;
    }
}

