package sqlancer.mysql;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.service.AutoService;

import sqlancer.AbstractAction;
import sqlancer.DatabaseProvider;
import sqlancer.IgnoreMeException;
import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.SQLProviderAdapter;
import sqlancer.StatementExecutor;
import sqlancer.common.DBMSCommon;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLQueryProvider;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.gen.MySQLAlterTable;
import sqlancer.mysql.gen.MySQLDeleteGenerator;
import sqlancer.mysql.gen.MySQLDropIndex;
import sqlancer.mysql.gen.MySQLExplainGenerator;
import sqlancer.mysql.gen.MySQLInsertGenerator;
import sqlancer.mysql.gen.MySQLSetGenerator;
import sqlancer.mysql.gen.MySQLTableGenerator;
import sqlancer.mysql.gen.MySQLTruncateTableGenerator;
import sqlancer.mysql.gen.MySQLUpdateGenerator;
import sqlancer.mysql.gen.admin.MySQLFlush;
import sqlancer.mysql.gen.admin.MySQLReset;
import sqlancer.mysql.gen.datadef.MySQLIndexGenerator;
import sqlancer.mysql.gen.tblmaintenance.MySQLAnalyzeTable;
import sqlancer.mysql.gen.tblmaintenance.MySQLCheckTable;
import sqlancer.mysql.gen.tblmaintenance.MySQLChecksum;
import sqlancer.mysql.gen.MySQLDropViewGenerator;
import sqlancer.mysql.gen.MySQLViewGenerator;
import sqlancer.mysql.gen.tblmaintenance.MySQLOptimize;
import sqlancer.mysql.gen.tblmaintenance.MySQLRepair;

@AutoService(DatabaseProvider.class)
public class MySQLProvider extends SQLProviderAdapter<MySQLGlobalState, MySQLOptions> {

    public MySQLProvider() {
        super(MySQLGlobalState.class, MySQLOptions.class);
    }

    enum Action implements AbstractAction<MySQLGlobalState> {
        SHOW_TABLES((g) -> new SQLQueryAdapter("SHOW TABLES")), //
        INSERT(MySQLInsertGenerator::insertRow), //
        SET_VARIABLE(MySQLSetGenerator::set), //
        REPAIR(MySQLRepair::repair), //
        OPTIMIZE(MySQLOptimize::optimize), //
        CHECKSUM(MySQLChecksum::checksum), //
        CHECK_TABLE(MySQLCheckTable::check), //
        ANALYZE_TABLE(MySQLAnalyzeTable::analyze), //
        FLUSH(MySQLFlush::create), RESET(MySQLReset::create), CREATE_INDEX(MySQLIndexGenerator::create), //
        ALTER_TABLE(MySQLAlterTable::create), //
        TRUNCATE_TABLE(MySQLTruncateTableGenerator::generate), //
        SELECT_INFO((g) -> new SQLQueryAdapter(
                "select TABLE_NAME, ENGINE from information_schema.TABLES where table_schema = '" + g.getDatabaseName()
                        + "'")), //
        UPDATE(MySQLUpdateGenerator::create), //
        DELETE(MySQLDeleteGenerator::delete), //
        DROP_INDEX(MySQLDropIndex::generate), //
        CREATE_VIEW(MySQLViewGenerator::create), //
        DROP_VIEW(MySQLDropViewGenerator::drop);

        private final SQLQueryProvider<MySQLGlobalState> sqlQueryProvider;

        Action(SQLQueryProvider<MySQLGlobalState> sqlQueryProvider) {
            this.sqlQueryProvider = sqlQueryProvider;
        }

        @Override
        public SQLQueryAdapter getQuery(MySQLGlobalState globalState) throws Exception {
            return sqlQueryProvider.getQuery(globalState);
        }
    }

    private static int mapActions(MySQLGlobalState globalState, Action a) {
        Randomly r = globalState.getRandomly();
        int nrPerformed = 0;
        switch (a) {
        case DROP_INDEX:
            nrPerformed = r.getInteger(0, 2);
            break;
        case SHOW_TABLES:
            nrPerformed = r.getInteger(0, 1);
            break;
        case INSERT:
            nrPerformed = r.getInteger(0, globalState.getOptions().getMaxNumberInserts());
            break;
        case REPAIR:
            nrPerformed = r.getInteger(0, 1);
            break;
        case SET_VARIABLE:
            nrPerformed = r.getInteger(0, 5);
            break;
        case CREATE_INDEX:
            nrPerformed = r.getInteger(0, 5);
            break;
        case FLUSH:
            nrPerformed = Randomly.getBooleanWithSmallProbability() ? r.getInteger(0, 1) : 0;
            break;
        case OPTIMIZE:
            // seems to yield low CPU utilization
            nrPerformed = Randomly.getBooleanWithSmallProbability() ? r.getInteger(0, 1) : 0;
            break;
        case RESET:
            // affects the global state, so do not execute
            nrPerformed = globalState.getOptions().getNumberConcurrentThreads() == 1 ? r.getInteger(0, 1) : 0;
            break;
        case CHECKSUM:
        case CHECK_TABLE:
        case ANALYZE_TABLE:
            nrPerformed = r.getInteger(0, 2);
            break;
        case ALTER_TABLE:
            nrPerformed = r.getInteger(0, 5);
            break;
        case TRUNCATE_TABLE:
            nrPerformed = r.getInteger(0, 2);
            break;
        case SELECT_INFO:
            nrPerformed = r.getInteger(0, 10);
            break;
        case UPDATE:
            nrPerformed = r.getInteger(0, 10);
            break;
        case DELETE:
            nrPerformed = r.getInteger(0, 10);
            break;
        case CREATE_VIEW:
            nrPerformed = globalState.getDbmsSpecificOptions().testViews() ? r.getInteger(0, 2) : 0;
            break;
        case DROP_VIEW:
            nrPerformed = globalState.getDbmsSpecificOptions().testViews() ? r.getInteger(0, 1) : 0;
            break;
        default:
            throw new AssertionError(a);
        }
        return nrPerformed;
    }

    @Override
    public void generateDatabase(MySQLGlobalState globalState) throws Exception {
        while (globalState.getSchema().getDatabaseTables().size() < Randomly.getNotCachedInteger(1, 2)) {
            String tableName = DBMSCommon.createTableName(globalState.getSchema().getDatabaseTables().size());
            SQLQueryAdapter createTable = MySQLTableGenerator.generate(globalState, tableName);
            globalState.executeStatement(createTable);
        }

        StatementExecutor<MySQLGlobalState, Action> se = new StatementExecutor<>(globalState, Action.values(),
                MySQLProvider::mapActions, (q) -> {
                    if (globalState.getSchema().getDatabaseTables().isEmpty()) {
                        throw new IgnoreMeException();
                    }
                });
        se.executeStatements();

        if (globalState.getDbmsSpecificOptions().getTestOracleFactory().stream()
                .anyMatch((o) -> o == MySQLOracleFactory.CERT)) {
            // Enfore statistic collected for all tables
            ExpectedErrors errors = new ExpectedErrors();
            MySQLErrors.addExpressionErrors(errors);
            for (MySQLTable table : globalState.getSchema().getDatabaseTables()) {
                StringBuilder sb = new StringBuilder();
                sb.append("ANALYZE TABLE ");
                sb.append(table.getName());
                sb.append(" UPDATE HISTOGRAM ON ");
                String columns = table.getColumns().stream().map(MySQLColumn::getName)
                        .collect(Collectors.joining(", "));
                sb.append(columns + ";");
                globalState.executeStatement(new SQLQueryAdapter(sb.toString(), errors));
            }
        }
    }

    @Override
    public SQLConnection createDatabase(MySQLGlobalState globalState) throws SQLException {
        String username = globalState.getOptions().getUserName();
        String password = globalState.getOptions().getPassword();
        String host = globalState.getOptions().getHost();
        int port = globalState.getOptions().getPort();
        if (host == null) {
            host = MySQLOptions.DEFAULT_HOST;
        }
        if (port == MainOptions.NO_SET_PORT) {
            port = MySQLOptions.DEFAULT_PORT;
        }
        String databaseName = globalState.getDatabaseName();
        globalState.getState().logStatement("DROP DATABASE IF EXISTS " + databaseName);
        globalState.getState().logStatement("CREATE DATABASE " + databaseName);
        globalState.getState().logStatement("USE " + databaseName);
        String url = String.format(
                "jdbc:mysql://%s:%d?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true&zeroDateTimeBehavior=CONVERT_TO_NULL",
                host, port);
        Connection con = DriverManager.getConnection(url, username, password);
        try (Statement s = con.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + databaseName);
        }
        try (Statement s = con.createStatement()) {
            s.execute("CREATE DATABASE " + databaseName);
        }
        try (Statement s = con.createStatement()) {
            s.execute("USE " + databaseName);
        }

        // Probe whether the filesystem supports punch hole (required for COMPRESSION)
        boolean compressionSupported = true;
        try (Statement s = con.createStatement()) {
            s.execute("CREATE TABLE _sqlancer_compression_probe (id INT) COMPRESSION='LZ4'");
            s.execute("DROP TABLE IF EXISTS _sqlancer_compression_probe");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Punch hole not supported") || msg.contains("Compression failed"))) {
                compressionSupported = false;
            }
            try (Statement s2 = con.createStatement()) {
                s2.execute("DROP TABLE IF EXISTS _sqlancer_compression_probe");
            } catch (SQLException ignored) {
            }
        }
        globalState.setSupportsTableCompression(compressionSupported);

        return new SQLConnection(con);
    }

    @Override
    public String getDBMSName() {
        return "mysql";
    }

    @Override
    public boolean addRowsToAllTables(MySQLGlobalState globalState) throws Exception {
        List<MySQLTable> tablesNoRow = globalState.getSchema().getDatabaseTables().stream()
                .filter(t -> !t.isView())  // 过滤掉视图
                .filter(t -> t.getNrRows(globalState) == 0).collect(Collectors.toList());
        for (MySQLTable table : tablesNoRow) {
            SQLQueryAdapter queryAddRows = MySQLInsertGenerator.insertRow(globalState, table);
            globalState.executeStatement(queryAddRows);
        }
        return true;
    }

    // ==================== QPG (Query Plan Guidance) Methods ====================

    @Override
    public String getQueryPlan(String selectStr, MySQLGlobalState globalState) throws Exception {
        String queryPlan = "";
        if (globalState.getOptions().logEachSelect()) {
            globalState.getLogger().writeCurrent(selectStr);
            try {
                globalState.getLogger().getCurrentFileWriter().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        SQLQueryAdapter q = new SQLQueryAdapter(MySQLExplainGenerator.explain(selectStr), null);
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
    protected void executeMutator(int index, MySQLGlobalState globalState) throws Exception {
        SQLQueryAdapter queryMutateTable = Action.values()[index].getQuery(globalState);
        globalState.executeStatement(queryMutateTable);
    }

    /**
     * Format MySQL EXPLAIN JSON output into a simplified string of operation types.
     * MySQL JSON structure differs from PostgreSQL:
     * - Root node: "query_block"
     * - Table access: "table" node with "access_type" field
     * - Join: "nested_loop" node
     * - Sorting: "ordering_operation" node
     * - Grouping: "grouping_operation" node
     *
     * @param queryPlan the raw JSON string from EXPLAIN FORMAT=JSON
     * @return formatted query plan string
     */
    public String formatQueryPlan(String queryPlan) throws IOException {
        if (queryPlan == null || queryPlan.isEmpty()) {
            return "";
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(queryPlan);
        List<String> nodeTypes = extractMySQLNodeTypes(root);
        return String.join(" ", nodeTypes);
    }

    /**
     * Extract node types from MySQL EXPLAIN JSON using recursive traversal.
     */
    private List<String> extractMySQLNodeTypes(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.has("query_block")) {
            extractMySQLNodeTypesRecursive(node.get("query_block"), result);
        }
        return result;
    }

    /**
     * Recursively extract operation types from MySQL EXPLAIN JSON nodes.
     */
    private void extractMySQLNodeTypesRecursive(JsonNode node, List<String> result) {
        if (node == null) {
            return;
        }
        // Extract table access type
        if (node.has("table")) {
            JsonNode table = node.get("table");
            if (table.has("access_type")) {
                result.add(table.get("access_type").asText());
            }
        }
        // Handle nested loop (join operations)
        if (node.has("nested_loop")) {
            result.add("nested_loop");
            JsonNode nestedLoop = node.get("nested_loop");
            if (nestedLoop.isArray()) {
                for (JsonNode child : nestedLoop) {
                    extractMySQLNodeTypesRecursive(child, result);
                }
            }
        }
        // Handle ordering operation
        if (node.has("ordering_operation")) {
            result.add("ordering_operation");
            extractMySQLNodeTypesRecursive(node.get("ordering_operation"), result);
        }
        // Handle grouping operation
        if (node.has("grouping_operation")) {
            result.add("grouping_operation");
            extractMySQLNodeTypesRecursive(node.get("grouping_operation"), result);
        }
        // Handle distinct operation
        if (node.has("duplicates_removal")) {
            result.add("duplicates_removal");
        }
        // Handle materialized subqueries
        if (node.has("materialized_from_subquery")) {
            result.add("materialized_from_subquery");
        }
    }

}
