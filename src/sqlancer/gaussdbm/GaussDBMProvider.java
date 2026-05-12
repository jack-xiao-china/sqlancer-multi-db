package sqlancer.gaussdbm;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;

import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.SQLProviderAdapter;
import sqlancer.StatementExecutor;
import sqlancer.AbstractAction;
import sqlancer.IgnoreMeException;
import sqlancer.common.DBMSCommon;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLQueryProvider;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.gaussdbm.gen.GaussDBMAlterTable;
import sqlancer.gaussdbm.gen.GaussDBMDeleteGenerator;
import sqlancer.gaussdbm.gen.GaussDBMDropIndexGenerator;
import sqlancer.gaussdbm.gen.GaussDBMExplainGenerator;
import sqlancer.gaussdbm.gen.GaussDBMIndexGenerator;
import sqlancer.gaussdbm.gen.GaussDBMInsertGenerator;
import sqlancer.gaussdbm.gen.GaussDBMTableGenerator;
import sqlancer.gaussdbm.gen.GaussDBMUpdateGenerator;

/**
 * GaussDB-M (M-Compatibility) provider.
 */
@AutoService(sqlancer.DatabaseProvider.class)
public class GaussDBMProvider extends SQLProviderAdapter<GaussDBMGlobalState, GaussDBMOptions> {

    private static boolean driverLoaded = false;

    public GaussDBMProvider() {
        super(GaussDBMGlobalState.class, GaussDBMOptions.class);
    }

    // ==================== Action Enumeration for QPG ====================
    public enum Action implements AbstractAction<GaussDBMGlobalState> {
        INSERT(GaussDBMInsertGenerator::insertRow),
        UPDATE((g) -> GaussDBMUpdateGenerator.create(g)),
        DELETE((g) -> GaussDBMDeleteGenerator.create(g)),
        CREATE_INDEX(GaussDBMIndexGenerator::create),
        DROP_INDEX(GaussDBMDropIndexGenerator::generate),
        ALTER_TABLE(GaussDBMAlterTable::create),
        ANALYZE_TABLE((g) -> new SQLQueryAdapter("ANALYZE TABLE " + g.getSchema().getRandomTableNoViewOrBailout().getName())),
        TRUNCATE((g) -> new SQLQueryAdapter("TRUNCATE TABLE " + g.getSchema().getRandomTableNoViewOrBailout().getName()));

        private final SQLQueryProvider<GaussDBMGlobalState> sqlQueryProvider;

        Action(SQLQueryProvider<GaussDBMGlobalState> sqlQueryProvider) {
            this.sqlQueryProvider = sqlQueryProvider;
        }

        @Override
        public SQLQueryAdapter getQuery(GaussDBMGlobalState globalState) throws Exception {
            return sqlQueryProvider.getQuery(globalState);
        }
    }

    // Map actions to number of executions per database generation
    private static int mapActions(GaussDBMGlobalState globalState, Action a) {
        Randomly r = globalState.getRandomly();
        int nrPerformed;
        switch (a) {
        case INSERT:
            nrPerformed = r.getInteger(1, globalState.getOptions().getMaxNumberInserts());
            break;
        case UPDATE:
        case DELETE:
            nrPerformed = r.getInteger(0, 10);
            break;
        case CREATE_INDEX:
            nrPerformed = r.getInteger(0, 5);
            break;
        case DROP_INDEX:
            nrPerformed = r.getInteger(0, 2);
            break;
        case ALTER_TABLE:
            nrPerformed = r.getInteger(0, 5);
            break;
        case ANALYZE_TABLE:
            nrPerformed = r.getInteger(0, 2);
            break;
        case TRUNCATE:
            nrPerformed = r.getInteger(0, 1);
            break;
        default:
            throw new AssertionError(a);
        }
        return nrPerformed;
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
        while (globalState.getSchema().getDatabaseTables().size() < Randomly.getNotCachedInteger(1, 2)) {
            String tableName = DBMSCommon.createTableName(globalState.getSchema().getDatabaseTables().size());
            SQLQueryAdapter createTable = GaussDBMTableGenerator.generate(globalState, tableName);
            globalState.executeStatement(createTable);
            // Update schema after each table creation to avoid duplicate table names
            globalState.updateSchema();
        }

        // Execute mutation actions using StatementExecutor (for QPG support)
        StatementExecutor<GaussDBMGlobalState, Action> se = new StatementExecutor<>(globalState, Action.values(),
                GaussDBMProvider::mapActions, (q) -> {
                    if (globalState.getSchema().getDatabaseTables().isEmpty()) {
                        throw new IgnoreMeException();
                    }
                });
        se.executeStatements();
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
                jdbcUrl = String.format("jdbc:%s://%s:%d/%s?%s", scheme, host, port, baseConnectionDatabase,
                        baseParams);
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
            String msg = "Connection failed to GaussDB-M. Last error: "
                    + (lastError != null ? lastError.getMessage() : "null");
            msg += "\n\nPossible solutions:";
            msg += "\n1. Ensure lib/opengauss-jdbc.jar is in classpath";
            msg += "\n2. Use --connection-url to specify full JDBC URL (jdbc:opengauss://host:port/database)";
            msg += "\n3. Verify host, port, username, password are correct";
            throw new SQLException(msg, lastError);
        }

        // Print connection info
        try {
            java.sql.DatabaseMetaData md = con.getMetaData();
            System.err.println(
                    "[INFO] Connected to: " + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
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
        // Note: GaussDB-M (PostgreSQL-based) does not support MySQL's "USE database" syntax
        // We reconnect to the new database instead

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
            try (ResultSet rs = s
                    .executeQuery("SELECT datcompatibility FROM pg_database WHERE datname = '" + databaseName + "'")) {
                if (rs.next()) {
                    String compat = rs.getString("datcompatibility");
                    System.err.println("[INFO] Verified database compatibility mode: " + compat);
                    if (!"M".equals(compat)) {
                        System.err
                                .println("[WARN] Database is not M-compatible! Expected 'M' but got '" + compat + "'");
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

    // ==================== QPG (Query Plan Guidance) Methods ====================

    @Override
    public String getQueryPlan(String selectStr, GaussDBMGlobalState globalState) throws Exception {
        String queryPlan = "";
        if (globalState.getOptions().logEachSelect()) {
            globalState.getLogger().writeCurrent(selectStr);
            try {
                globalState.getLogger().getCurrentFileWriter().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        SQLQueryAdapter q = new SQLQueryAdapter(GaussDBMExplainGenerator.explain(selectStr), null);
        try (SQLancerResultSet rs = q.executeAndGet(globalState)) {
            if (rs != null) {
                while (rs.next()) {
                    queryPlan += rs.getString(1);
                }
            }
        } catch (SQLException | AssertionError e) {
            queryPlan = "";
        }
        return formatQueryPlan(queryPlan);
    }

    @Override
    protected double[] initializeWeightedAverageReward() {
        return new double[Action.values().length];
    }

    @Override
    protected void executeMutator(int index, GaussDBMGlobalState globalState) throws Exception {
        SQLQueryAdapter queryMutateTable = Action.values()[index].getQuery(globalState);
        globalState.executeStatement(queryMutateTable);
    }

    /**
     * Format PostgreSQL-style EXPLAIN JSON output into a simplified string of node types.
     * Uses BFS traversal to extract "Node Type" from each plan node.
     *
     * @param queryPlan the raw JSON string from EXPLAIN (FORMAT JSON)
     * @return formatted query plan string
     */
    public String formatQueryPlan(String queryPlan) throws IOException {
        if (queryPlan == null || queryPlan.isEmpty()) {
            return "";
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(queryPlan).get(0).get("Plan");
        List<String> nodeTypes = extractNodeTypesIterative(root);
        return String.join(" ", nodeTypes);
    }

    /**
     * BFS algorithm for traversing the Json Query Plan.
     */
    private static List<String> extractNodeTypesIterative(JsonNode root) {
        List<String> result = new ArrayList<>();
        Queue<JsonNode> queue = new LinkedList<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            JsonNode node = queue.poll();
            if (node.has("Node Type")) {
                result.add(node.get("Node Type").asText());
            }
            if (node.has("Plans") && node.get("Plans").isArray()) {
                for (JsonNode plan : node.get("Plans")) {
                    queue.add(plan);
                }
            }
        }
        return result;
    }
}
