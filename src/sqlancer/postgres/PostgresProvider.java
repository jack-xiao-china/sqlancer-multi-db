package sqlancer.postgres;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.HashMap;
import java.util.Map;
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
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLQueryProvider;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.postgres.gen.PostgresAlterTableGenerator;
import sqlancer.postgres.gen.PostgresAnalyzeGenerator;
import sqlancer.postgres.gen.PostgresClusterGenerator;
import sqlancer.postgres.gen.PostgresCommentGenerator;
import sqlancer.postgres.gen.PostgresDeleteGenerator;
import sqlancer.postgres.gen.PostgresDiscardGenerator;
import sqlancer.postgres.gen.PostgresDropIndexGenerator;
import sqlancer.postgres.gen.PostgresExplainGenerator;
import sqlancer.postgres.gen.PostgresIndexGenerator;
import sqlancer.postgres.gen.PostgresInsertGenerator;
import sqlancer.postgres.gen.PostgresNotifyGenerator;
import sqlancer.postgres.gen.PostgresReindexGenerator;
import sqlancer.postgres.gen.PostgresSequenceGenerator;
import sqlancer.postgres.gen.PostgresSetGenerator;
import sqlancer.postgres.gen.PostgresStatisticsGenerator;
import sqlancer.postgres.gen.PostgresTableGenerator;
import sqlancer.postgres.gen.PostgresTableSpaceGenerator;
import sqlancer.postgres.gen.PostgresTransactionGenerator;
import sqlancer.postgres.gen.PostgresTruncateGenerator;
import sqlancer.postgres.gen.PostgresUpdateGenerator;
import sqlancer.postgres.gen.PostgresVacuumGenerator;
import sqlancer.postgres.gen.PostgresViewGenerator;

// EXISTS
// IN
@AutoService(DatabaseProvider.class)
public class PostgresProvider extends SQLProviderAdapter<PostgresGlobalState, PostgresOptions> {

    private static final Object ENUM_TYPES_LOCK = new Object();
    private static final List<String> enumTypeNames = new ArrayList<>();
    private static final Map<String, List<String>> enumTypeLabels = new HashMap<>();

    protected String entryURL;
    protected String username;
    protected String password;
    protected String entryPath;
    protected String host;
    protected int port;
    protected String testURL;
    protected String databaseName;
    protected String createDatabaseCommand;
    protected String extensionsList;

    public PostgresProvider() {
        super(PostgresGlobalState.class, PostgresOptions.class);
    }

    protected PostgresProvider(Class<PostgresGlobalState> globalClass, Class<PostgresOptions> optionClass) {
        super(globalClass, optionClass);
    }

    public enum Action implements AbstractAction<PostgresGlobalState> {
        ANALYZE(PostgresAnalyzeGenerator::create), //
        ALTER_TABLE(g -> PostgresAlterTableGenerator.create(g.getSchema().getRandomTable(t -> !t.isView()), g)), //
        CLUSTER(PostgresClusterGenerator::create), //
        COMMIT(g -> {
            SQLQueryAdapter query;
            if (Randomly.getBoolean()) {
                query = new SQLQueryAdapter("COMMIT", true);
            } else if (Randomly.getBoolean()) {
                query = PostgresTransactionGenerator.executeBegin();
            } else {
                query = new SQLQueryAdapter("ROLLBACK", true);
            }
            return query;
        }), //
        CREATE_STATISTICS(PostgresStatisticsGenerator::insert), //
        DROP_STATISTICS(PostgresStatisticsGenerator::remove), //
        ALTER_STATISTICS(PostgresStatisticsGenerator::alter), //
        DELETE(PostgresDeleteGenerator::create), //
        DISCARD(PostgresDiscardGenerator::create), //
        DROP_INDEX(PostgresDropIndexGenerator::create), //
        INSERT(PostgresInsertGenerator::insert), //
        UPDATE(PostgresUpdateGenerator::create), //
        TRUNCATE(PostgresTruncateGenerator::create), //
        VACUUM(PostgresVacuumGenerator::create), //
        REINDEX(PostgresReindexGenerator::create), //
        SET(PostgresSetGenerator::create), //
        CREATE_INDEX(PostgresIndexGenerator::generate), //
        SET_CONSTRAINTS((g) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("SET CONSTRAINTS ALL ");
            sb.append(Randomly.fromOptions("DEFERRED", "IMMEDIATE"));
            return new SQLQueryAdapter(sb.toString());
        }), //
        RESET_ROLE((g) -> new SQLQueryAdapter("RESET ROLE")), //
        COMMENT_ON(PostgresCommentGenerator::generate), //
        RESET((g) -> new SQLQueryAdapter("RESET ALL") /*
                                                       * https://www.postgresql.org/docs/13/sql-reset.html TODO: also
                                                       * configuration parameter
                                                       */), //
        NOTIFY(PostgresNotifyGenerator::createNotify), //
        LISTEN((g) -> PostgresNotifyGenerator.createListen()), //
        UNLISTEN((g) -> PostgresNotifyGenerator.createUnlisten()), //
        CREATE_SEQUENCE(PostgresSequenceGenerator::createSequence), //
        CREATE_VIEW(PostgresViewGenerator::create), //
        CREATE_TABLESPACE(PostgresTableSpaceGenerator::generate);

        private final SQLQueryProvider<PostgresGlobalState> sqlQueryProvider;

        Action(SQLQueryProvider<PostgresGlobalState> sqlQueryProvider) {
            this.sqlQueryProvider = sqlQueryProvider;
        }

        @Override
        public SQLQueryAdapter getQuery(PostgresGlobalState state) throws Exception {
            return sqlQueryProvider.getQuery(state);
        }
    }

    protected static int mapActions(PostgresGlobalState globalState, Action a) {
        Randomly r = globalState.getRandomly();
        int nrPerformed;
        switch (a) {
        case CREATE_INDEX:
            nrPerformed = r.getInteger(0, 3);
            break;
        case CLUSTER:
            // CLUSTER can be very expensive (reorders entire table)
            nrPerformed = Randomly.getBooleanWithSmallProbability() ? r.getInteger(0, 1) : 0;
            break;
        case CREATE_STATISTICS:
            // CREATE STATISTICS can be expensive for large tables
            nrPerformed = Randomly.getBooleanWithSmallProbability() ? r.getInteger(0, 1) : 0;
            break;
        case ALTER_STATISTICS:
            nrPerformed = r.getInteger(0, 2);
            break;
        case DISCARD:
        case DROP_INDEX:
            nrPerformed = r.getInteger(0, 5);
            break;
        case COMMIT:
            nrPerformed = r.getInteger(0, 0);
            break;
        case ALTER_TABLE:
            nrPerformed = r.getInteger(0, 5);
            break;
        case REINDEX:
            // REINDEX can be expensive (rebuilds entire index)
            nrPerformed = Randomly.getBooleanWithSmallProbability() ? r.getInteger(0, 1) : 0;
            break;
        case RESET:
            nrPerformed = r.getInteger(0, 3);
            break;
        case DELETE:
        case RESET_ROLE:
        case SET:
            nrPerformed = r.getInteger(0, 5);
            break;
        case ANALYZE:
            // ANALYZE collects statistics and can be slow
            nrPerformed = Randomly.getBooleanWithSmallProbability() ? r.getInteger(0, 1) : 0;
            break;
        case VACUUM:
            // VACUUM (especially FREEZE) can take 18+ seconds
            nrPerformed = Randomly.getBooleanWithSmallProbability() ? r.getInteger(0, 1) : 0;
            break;
        case SET_CONSTRAINTS:
        case COMMENT_ON:
        case NOTIFY:
        case LISTEN:
        case UNLISTEN:
        case CREATE_SEQUENCE:
        case DROP_STATISTICS:
            nrPerformed = r.getInteger(0, 2);
            break;
        case TRUNCATE:
            // TRUNCATE clears table data, may affect test oracle
            nrPerformed = Randomly.getBooleanWithSmallProbability() ? r.getInteger(0, 1) : 0;
            break;
        case CREATE_VIEW:
            nrPerformed = Randomly.getBooleanWithSmallProbability() ? r.getInteger(0, 1) : 0;
            break;
        case CREATE_TABLESPACE:
            // CREATE TABLESPACE involves filesystem operations
            nrPerformed = Randomly.getBooleanWithSmallProbability() ? r.getInteger(0, 1) : 0;
            break;
        case UPDATE:
            nrPerformed = r.getInteger(0, 10);
            break;
        case INSERT:
            nrPerformed = r.getInteger(0, globalState.getOptions().getMaxNumberInserts());
            break;
        default:
            throw new AssertionError(a);
        }
        return nrPerformed;

    }

    @Override
    public void generateDatabase(PostgresGlobalState globalState) throws Exception {
        createEnumTypes(globalState);
        readFunctions(globalState);
        createTables(globalState, Randomly.fromOptions(4, 5, 6));
        prepareTables(globalState);

        extensionsList = globalState.getDbmsSpecificOptions().extensions;
        if (!extensionsList.isEmpty()) {
            String[] extensionNames = extensionsList.split(",");

            /*
             * To avoid of a test interference with an extension objects, create them in a separate schema. Of course,
             * they must be truly relocatable.
             */
            globalState.executeStatement(new SQLQueryAdapter("CREATE SCHEMA extensions;", true));
            for (int i = 0; i < extensionNames.length; i++) {
                globalState.executeStatement(new SQLQueryAdapter(
                        "CREATE EXTENSION " + extensionNames[i] + " WITH SCHEMA extensions;", true));
            }
        }
    }

    @Override
    public SQLConnection createDatabase(PostgresGlobalState globalState) throws SQLException {
        PostgresOptions opts = globalState.getDbmsSpecificOptions();
        opts.validate();

        username = globalState.getOptions().getUserName();
        password = globalState.getOptions().getPassword();
        host = globalState.getOptions().getHost();
        port = globalState.getOptions().getPort();
        entryURL = globalState.getDbmsSpecificOptions().connectionURL;
        // trim URL to exclude "jdbc:"
        if (entryURL.startsWith("jdbc:")) {
            entryURL = entryURL.substring(5);
        }
        databaseName = globalState.getDatabaseName();

        try {
            URI uri = new URI(entryURL);
            String userInfoURI = uri.getUserInfo();
            if (userInfoURI != null) {
                // username and password specified in URL take precedence
                if (userInfoURI.contains(":")) {
                    String[] userInfo = userInfoURI.split(":", 2);
                    username = userInfo[0];
                    password = userInfo[1];
                } else {
                    username = userInfoURI;
                    password = null;
                }
                int userInfoIndex = entryURL.indexOf(userInfoURI);
                String preUserInfo = entryURL.substring(0, userInfoIndex);
                String postUserInfo = entryURL.substring(userInfoIndex + userInfoURI.length() + 1);
                entryURL = preUserInfo + postUserInfo;
            }
            if (host == null) {
                host = uri.getHost();
            }
            if (port == MainOptions.NO_SET_PORT) {
                port = uri.getPort();
            }

            String entryDatabaseName = "postgres";
            URI entryUri = new URI(uri.getScheme(), null, host, port, "/" + entryDatabaseName, uri.getQuery(),
                    uri.getFragment());
            entryURL = entryUri.toString();
            entryPath = entryUri.getPath();

            Connection con = DriverManager.getConnection("jdbc:" + entryURL, username, password);
            globalState.getState().logStatement(String.format("\\c %s;", entryDatabaseName));
            try (Statement s = con.createStatement()) {
                s.execute("SET lc_messages TO 'C'");
            } catch (SQLException ignored) {
                // Some installations/users might not be allowed to change lc_messages.
            }

            String dropCommand = "DROP DATABASE";
            boolean forceDrop = Randomly.getBoolean();
            if (forceDrop) {
                dropCommand += " FORCE";
            }
            dropCommand += " IF EXISTS " + databaseName;

            globalState.getState().logStatement(dropCommand + ";");
            try (Statement s = con.createStatement()) {
                s.execute(dropCommand);
            } catch (SQLException e) {
                // If force fails, fall back to regular drop
                if (forceDrop) {
                    String fallbackDrop = "DROP DATABASE IF EXISTS " + databaseName;
                    globalState.getState().logStatement(fallbackDrop + ";");
                    try (Statement s = con.createStatement()) {
                        s.execute(fallbackDrop);
                    }
                } else {
                    throw e;
                }
            }

            // Create database section
            createDatabaseCommand = getCreateDatabaseCommand(globalState);
            globalState.getState().logStatement(createDatabaseCommand + ";");
            try (Statement s = con.createStatement()) {
                s.execute(createDatabaseCommand);
            }
            con.close();

            URI testUri = new URI(uri.getScheme(), null, host, port, "/" + databaseName, uri.getQuery(),
                    uri.getFragment());
            testURL = testUri.toString();
            globalState.getState().logStatement(String.format("\\c %s;", databaseName));

            con = DriverManager.getConnection("jdbc:" + testURL, username, password);
            try (Statement s = con.createStatement()) {
                s.execute("SET lc_messages TO 'C'");
            } catch (SQLException ignored) {
                // Some installations/users might not be allowed to change lc_messages.
            }
            // Session determinism settings (best-effort; keep existing lc_messages logic).
            try (Statement s = con.createStatement()) {
                s.execute("SET TimeZone='UTC'");
                s.execute("SET DateStyle='ISO, YMD'");
                s.execute("SET IntervalStyle='postgres'");
            } catch (SQLException ignored) {
                // Some installations/users might not be allowed to change these settings.
            }
            return new SQLConnection(con);
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    protected void readFunctions(PostgresGlobalState globalState) throws SQLException {
        SQLQueryAdapter query = new SQLQueryAdapter("SELECT proname, provolatile FROM pg_proc;");
        SQLancerResultSet rs = query.executeAndGet(globalState);
        while (rs.next()) {
            String functionName = rs.getString(1);
            Character functionType = rs.getString(2).charAt(0);
            globalState.addFunctionAndType(functionName, functionType);
        }
    }

    private static void createEnumTypes(PostgresGlobalState globalState) throws Exception {
        synchronized (ENUM_TYPES_LOCK) {
            enumTypeNames.clear();
            enumTypeLabels.clear();
        }

        int count = Randomly.fromOptions(1, 2);
        for (int i = 0; i < count; i++) {
            String typeName = "e" + i;
            List<String> labels = Arrays.asList("a", "b", "c", "d");
            synchronized (ENUM_TYPES_LOCK) {
                enumTypeNames.add(typeName);
                enumTypeLabels.put(typeName, labels);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TYPE ");
            sb.append(typeName);
            sb.append(" AS ENUM (");
            for (int j = 0; j < labels.size(); j++) {
                if (j != 0) {
                    sb.append(", ");
                }
                sb.append("'");
                sb.append(labels.get(j).replace("'", "''"));
                sb.append("'");
            }
            sb.append(");");
            globalState.executeStatement(new SQLQueryAdapter(sb.toString(), true));
        }
    }

    public static List<String> getEnumTypeNames() {
        synchronized (ENUM_TYPES_LOCK) {
            return new ArrayList<>(enumTypeNames);
        }
    }

    public static String getRandomEnumTypeName() {
        synchronized (ENUM_TYPES_LOCK) {
            if (enumTypeNames.isEmpty()) {
                // Defensive fallback; should not happen after createEnumTypes ran.
                return "text";
            }
            return Randomly.fromList(enumTypeNames);
        }
    }

    public static String getRandomEnumLabel(String type, Randomly r) {
        if (type == null) {
            return r.getString();
        }
        synchronized (ENUM_TYPES_LOCK) {
            List<String> labels = enumTypeLabels.get(type);
            if (labels == null || labels.isEmpty()) {
                return r.getString();
            }
            return Randomly.fromList(labels);
        }
    }

    public static int getEnumLabelIndex(String type, String label) {
        if (type == null || label == null) {
            return -1;
        }
        synchronized (ENUM_TYPES_LOCK) {
            List<String> labels = enumTypeLabels.get(type);
            if (labels == null) {
                return -1;
            }
            return labels.indexOf(label);
        }
    }

    protected void createTables(PostgresGlobalState globalState, int numTables) throws Exception {
        while (globalState.getSchema().getDatabaseTables().size() < numTables) {
            try {
                String tableName = DBMSCommon.createTableName(globalState.getSchema().getDatabaseTables().size());
                SQLQueryAdapter createTable = PostgresTableGenerator.generate(tableName, globalState.getSchema(),
                        globalState);
                globalState.executeStatement(createTable);
            } catch (IgnoreMeException e) {

            }
        }
    }

    protected void prepareTables(PostgresGlobalState globalState) throws Exception {
        StatementExecutor<PostgresGlobalState, Action> se = new StatementExecutor<>(globalState, Action.values(),
                PostgresProvider::mapActions, (q) -> {
                    if (globalState.getSchema().getDatabaseTables().isEmpty()) {
                        throw new IgnoreMeException();
                    }
                });
        se.executeStatements();
        globalState.executeStatement(new SQLQueryAdapter("COMMIT", true));
        globalState.executeStatement(new SQLQueryAdapter("SET SESSION statement_timeout = 5000;\n"));
    }

    private String getCreateDatabaseCommand(PostgresGlobalState state) {
        return "CREATE DATABASE " + databaseName;
    }

    @Override
    public String getDBMSName() {
        return "postgres";
    }

    @Override
    public String getQueryPlan(String selectStr, PostgresGlobalState globalState) throws Exception {
        String queryPlan = "";
        if (globalState.getOptions().logEachSelect()) {
            globalState.getLogger().writeCurrent(selectStr);
            try {
                globalState.getLogger().getCurrentFileWriter().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        SQLQueryAdapter q = new SQLQueryAdapter(PostgresExplainGenerator.explain(selectStr), null);
        try (SQLancerResultSet rs = q.executeAndGet(globalState)) {
            while (rs.next()) {
                queryPlan += rs.getString(1);
            }
        } catch (SQLException | AssertionError e) {
            queryPlan = "";
        }
        return formatQueryPlan(queryPlan);
    }

    @Override
    protected double[] initializeWeightedAverageReward() {
        return new double[PostgresProvider.Action.values().length];
    }

    @Override
    protected void executeMutator(int index, PostgresGlobalState globalState) throws Exception {
        SQLQueryAdapter queryMutateTable = PostgresProvider.Action.values()[index].getQuery(globalState);
        globalState.executeStatement(queryMutateTable);
    }

    @Override
    protected boolean addRowsToAllTables(PostgresGlobalState globalState) throws Exception {
        List<PostgresSchema.PostgresTable> tablesNoRow = globalState.getSchema().getDatabaseTables().stream()
                .filter(t -> t.getNrRows(globalState) == 0).collect(Collectors.toList());
        for (PostgresSchema.PostgresTable table : tablesNoRow) {
            SQLQueryAdapter queryAddRows = PostgresInsertGenerator.insertRows(globalState, table);
            globalState.executeStatement(queryAddRows);
        }
        return true;
    }

    public String formatQueryPlan(String queryPlan) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(queryPlan).get(0).get("Plan");
        // Extract nodes using BFS algorithm
        List<String> nodeTypes = extractNodeTypesIterative(root);
        return String.join(" ", nodeTypes);
    }

    // BFS algorithm for traversing the Json Query Plan
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
