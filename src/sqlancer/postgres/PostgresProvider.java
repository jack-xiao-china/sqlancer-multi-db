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
import sqlancer.postgres.gen.PostgresAlterIndexGenerator;
import sqlancer.postgres.gen.PostgresAlterTableGenerator;
import sqlancer.postgres.gen.PostgresAnalyzeGenerator;
import sqlancer.postgres.gen.PostgresClusterGenerator;
import sqlancer.postgres.gen.PostgresCommentGenerator;
import sqlancer.postgres.gen.PostgresCopyGenerator;
import sqlancer.postgres.gen.PostgresDeleteGenerator;
import sqlancer.postgres.gen.PostgresDiscardGenerator;
import sqlancer.postgres.gen.PostgresDropIndexGenerator;
import sqlancer.postgres.gen.PostgresDropTableGenerator;
import sqlancer.postgres.gen.PostgresDropViewGenerator;
import sqlancer.postgres.gen.PostgresExplainGenerator;
import sqlancer.postgres.gen.PostgresForeignKeySetupGenerator;
import sqlancer.postgres.gen.PostgresFunctionGenerator;
import sqlancer.postgres.gen.PostgresIndexGenerator;
import sqlancer.postgres.gen.PostgresInsertGenerator;
import sqlancer.postgres.gen.PostgresMergeGenerator;
import sqlancer.postgres.gen.PostgresNotifyGenerator;
import sqlancer.postgres.gen.PostgresPartitionGenerator;
import sqlancer.postgres.gen.PostgresRandomQueryGenerator;
import sqlancer.postgres.gen.PostgresReindexGenerator;
import sqlancer.postgres.gen.PostgresRuleGenerator;
import sqlancer.postgres.gen.PostgresSequenceGenerator;
import sqlancer.postgres.gen.PostgresSetGenerator;
import sqlancer.postgres.gen.PostgresStatisticsGenerator;
import sqlancer.postgres.gen.PostgresTableGenerator;
import sqlancer.postgres.gen.PostgresTableSpaceGenerator;
import sqlancer.postgres.gen.PostgresTransactionGenerator;
import sqlancer.postgres.gen.PostgresTruncateGenerator;
import sqlancer.postgres.gen.PostgresTypeGenerator;
import sqlancer.postgres.gen.PostgresUpdateGenerator;
import sqlancer.postgres.gen.PostgresVacuumGenerator;
import sqlancer.postgres.gen.PostgresViewGenerator;

// EXISTS
// IN
@AutoService(DatabaseProvider.class)
public class PostgresProvider extends SQLProviderAdapter<PostgresGlobalState, PostgresOptions> {

    private static final int BOMBARD_MAX_TABLES = 12;
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
        ALTER_INDEX(PostgresAlterIndexGenerator::create), //
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
        CREATE_TYPE(PostgresTypeGenerator::createCompositeType), //
        DROP_STATISTICS(PostgresStatisticsGenerator::remove), //
        ALTER_STATISTICS(PostgresStatisticsGenerator::alter), //
        CREATE_FUNCTION(PostgresFunctionGenerator::createFunction), //
        CREATE_RULE(PostgresRuleGenerator::create), //
        DELETE(PostgresDeleteGenerator::create), //
        DISCARD(PostgresDiscardGenerator::create), //
        DROP_INDEX(PostgresDropIndexGenerator::create), //
        DROP_TABLE(PostgresDropTableGenerator::create), //
        DROP_VIEW(PostgresDropViewGenerator::create), //
        CREATE_PARTITION(PostgresPartitionGenerator::createPartition), //
        ATTACH_PARTITION(PostgresPartitionGenerator::attachPartition), //
        DETACH_PARTITION(PostgresPartitionGenerator::detachPartition), //
        DROP_PARTITION(PostgresPartitionGenerator::dropPartition), //
        INSERT(PostgresInsertGenerator::insert), //
        MERGE(PostgresMergeGenerator::create), //
        COPY(PostgresCopyGenerator::create), //
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
        DROP_SEQUENCE(PostgresSequenceGenerator::dropSequence), //
        ALTER_SEQUENCE(PostgresSequenceGenerator::alterSequence), //
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
            nrPerformed = r.getInteger(1, 5);
            break;
        case ALTER_INDEX:
            nrPerformed = hasAnyIndex(globalState) ? r.getInteger(0, 3) : 0;
            break;
        case CLUSTER:
            // CLUSTER can be very expensive (reorders entire table)
            nrPerformed = Randomly.getBooleanWithSmallProbability() ? r.getInteger(0, 1) : 0;
            break;
        case CREATE_STATISTICS:
            // CREATE STATISTICS can be expensive for large tables
            nrPerformed = Randomly.getBooleanWithSmallProbability() ? r.getInteger(0, 1) : 0;
            break;
        case CREATE_TYPE:
        case CREATE_FUNCTION:
        case CREATE_RULE:
            nrPerformed = r.getInteger(0, 2);
            break;
        case ALTER_STATISTICS:
            nrPerformed = r.getInteger(0, 3);
            break;
        case DISCARD:
        case DROP_INDEX:
            nrPerformed = r.getInteger(0, 6);
            break;
        case DROP_TABLE:
        case DROP_VIEW:
        case DROP_SEQUENCE:
            nrPerformed = Randomly.getBooleanWithRatherLowProbability() ? r.getInteger(0, 1) : 0;
            break;
        case CREATE_PARTITION:
            nrPerformed = PostgresPartitionGenerator.hasCreatePartitionCandidate(globalState) ? r.getInteger(1, 5) : 0;
            break;
        case ATTACH_PARTITION:
            nrPerformed = PostgresPartitionGenerator.hasAttachPartitionCandidate(globalState) ? r.getInteger(0, 3) : 0;
            break;
        case DETACH_PARTITION:
        case DROP_PARTITION:
            nrPerformed = Randomly.getBooleanWithRatherLowProbability() ? r.getInteger(0, 2) : 0;
            break;
        case COMMIT:
            nrPerformed = r.getInteger(0, 0);
            break;
        case ALTER_TABLE:
            nrPerformed = r.getInteger(3, 12);
            break;
        case REINDEX:
            // REINDEX can be expensive (rebuilds entire index)
            nrPerformed = Randomly.getBooleanWithSmallProbability() ? r.getInteger(0, 1) : 0;
            break;
        case RESET:
            nrPerformed = r.getInteger(0, 4);
            break;
        case DELETE:
        case RESET_ROLE:
        case SET:
            nrPerformed = r.getInteger(1, 7);
            break;
        case MERGE:
            nrPerformed = r.getInteger(0, 2);
            break;
        case COPY:
            nrPerformed = Randomly.getBooleanWithRatherLowProbability() ? r.getInteger(0, 1) : 0;
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
        case ALTER_SEQUENCE:
        case DROP_STATISTICS:
            nrPerformed = r.getInteger(0, 3);
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
            nrPerformed = r.getInteger(3, 10);
            break;
        case INSERT:
            nrPerformed = r.getInteger(Math.max(1, globalState.getDbmsSpecificOptions().getPgGenerateSqlNum() / 3),
                    globalState.getDbmsSpecificOptions().getPgGenerateSqlNum());
            break;
        default:
            throw new AssertionError(a);
        }
        return nrPerformed;

    }

    private static boolean hasAnyIndex(PostgresGlobalState globalState) {
        return globalState.getSchema().getDatabaseTables().stream().anyMatch(t -> !t.getIndexes().isEmpty());
    }

    @Override
    public void generateDatabase(PostgresGlobalState globalState) throws Exception {
        createEnumTypes(globalState);
        readFunctions(globalState);
        int numTables = globalState.getDbmsSpecificOptions().getPgTables();
        createTables(globalState, numTables);
        PostgresForeignKeySetupGenerator.setup(globalState);
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

        databaseName = globalState.getDatabaseName();
        try {
            String entryDatabaseName = "postgres";
            ConnectionInfo connectionInfo = resolveConnectionInfo(globalState.getOptions(), opts, "postgres");
            entryURL = connectionInfo.url;
            entryPath = connectionInfo.path;
            testURL = connectionInfo.url;
            username = connectionInfo.username;
            password = connectionInfo.password;
            host = connectionInfo.host;
            port = connectionInfo.port;

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

            globalState.getState().logStatement(String.format("\\c %s;", databaseName));
            return createDatabaseConnection(globalState.getOptions(), opts, databaseName);
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    public SQLConnection createDatabaseConnection(MainOptions options, PostgresOptions postgresOptions, String databaseName)
            throws SQLException {
        try {
            ConnectionInfo connectionInfo = resolveConnectionInfo(options, postgresOptions, databaseName);
            username = connectionInfo.username;
            password = connectionInfo.password;
            host = connectionInfo.host;
            port = connectionInfo.port;
            testURL = connectionInfo.url;
            Connection con = DriverManager.getConnection("jdbc:" + connectionInfo.url, connectionInfo.username,
                    connectionInfo.password);
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
                createInitialPartitions(globalState);
            } catch (IgnoreMeException e) {

            }
        }
    }

    private static void createInitialPartitions(PostgresGlobalState globalState) throws Exception {
        if (!PostgresPartitionGenerator.hasCreatePartitionCandidate(globalState)) {
            return;
        }
        int nrPartitions = Randomly.fromOptions(2, 3, 4);
        for (int i = 0; i < nrPartitions; i++) {
            try {
                globalState.executeStatement(PostgresPartitionGenerator.createPartition(globalState));
            } catch (IgnoreMeException ignored) {
                return;
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

    public void bootstrapBombardDatabase(PostgresGlobalState globalState) throws Exception {
        createEnumTypes(globalState);
        readFunctions(globalState);
        globalState.updateSchema();
        createTables(globalState, Randomly.fromOptions(2, 3));
        globalState.updateSchema();
    }

    public SQLQueryAdapter getQueryForBombard(PostgresGlobalState globalState, long workerId, long sequence)
            throws Exception {
        int tableCount = globalState.getSchema().getDatabaseTables().size();
        if (tableCount < BOMBARD_MAX_TABLES && shouldCreateBombardTable(tableCount)) {
            return PostgresTableGenerator.generate(getBombardTableName(workerId, sequence), globalState.getSchema(),
                    globalState);
        }
        if (!globalState.getSchema().getDatabaseTables().isEmpty() && Randomly.getBoolean()) {
            String query = PostgresVisitor
                    .asString(PostgresRandomQueryGenerator.createRandomQuery(Randomly.smallNumber() + 1, globalState))
                    + ";";
            return new SQLQueryAdapter(query);
        }
        return getWeightedBombardAction(globalState).getQuery(globalState);
    }

    static boolean isExcludedBombardAction(Action action) {
        switch (action) {
        case DISCARD:
        case CREATE_TABLESPACE:
        case TRUNCATE:
        case VACUUM:
        case CLUSTER:
        case REINDEX:
        case CREATE_PARTITION:
        case ATTACH_PARTITION:
        case DETACH_PARTITION:
        case DROP_PARTITION:
        case DROP_TABLE:
        case DROP_VIEW:
        case DROP_SEQUENCE:
        case COPY:
            return true;
        default:
            return false;
        }
    }

    private boolean shouldCreateBombardTable(int tableCount) {
        if (tableCount == 0) {
            return true;
        }
        if (tableCount < 2) {
            return Randomly.getBooleanWithSmallProbability();
        }
        return tableCount < BOMBARD_MAX_TABLES && Randomly.getBooleanWithRatherLowProbability();
    }

    private Action getWeightedBombardAction(PostgresGlobalState globalState) {
        List<Action> availableActions = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int totalWeight = 0;
        for (Action action : Action.values()) {
            if (isExcludedBombardAction(action)) {
                continue;
            }
            int weight = mapActions(globalState, action);
            if (weight <= 0) {
                continue;
            }
            availableActions.add(action);
            weights.add(weight);
            totalWeight += weight;
        }
        if (availableActions.isEmpty()) {
            throw new AssertionError("No PostgreSQL bombard actions available");
        }
        int selection = globalState.getRandomly().getInteger(0, totalWeight);
        int current = 0;
        for (int i = 0; i < availableActions.size(); i++) {
            current += weights.get(i);
            if (selection < current) {
                return availableActions.get(i);
            }
        }
        return availableActions.get(availableActions.size() - 1);
    }

    static String getBombardTableName(long workerId, long sequence) {
        return String.format("tb%d_%d", workerId, sequence);
    }

    private String getCreateDatabaseCommand(PostgresGlobalState state) {
        return "CREATE DATABASE " + databaseName;
    }

    private ConnectionInfo resolveConnectionInfo(MainOptions options, PostgresOptions postgresOptions, String targetDatabase)
            throws URISyntaxException {
        String resolvedUsername = options.getUserName();
        String resolvedPassword = options.getPassword();
        String resolvedHost = options.getHost();
        int resolvedPort = options.getPort();
        String resolvedEntryURL = postgresOptions.connectionURL;
        if (resolvedEntryURL.startsWith("jdbc:")) {
            resolvedEntryURL = resolvedEntryURL.substring(5);
        }
        URI uri = new URI(resolvedEntryURL);
        String userInfoURI = uri.getUserInfo();
        if (userInfoURI != null) {
            if (userInfoURI.contains(":")) {
                String[] userInfo = userInfoURI.split(":", 2);
                resolvedUsername = userInfo[0];
                resolvedPassword = userInfo[1];
            } else {
                resolvedUsername = userInfoURI;
                resolvedPassword = null;
            }
        }
        if (resolvedHost == null) {
            resolvedHost = uri.getHost();
        }
        if (resolvedHost == null) {
            resolvedHost = PostgresOptions.DEFAULT_HOST;
        }
        if (resolvedPort == MainOptions.NO_SET_PORT) {
            resolvedPort = uri.getPort();
        }
        if (resolvedPort == -1) {
            resolvedPort = PostgresOptions.DEFAULT_PORT;
        }
        URI dbUri = new URI(uri.getScheme(), null, resolvedHost, resolvedPort, "/" + targetDatabase, uri.getQuery(),
                uri.getFragment());
        return new ConnectionInfo(resolvedUsername, resolvedPassword, resolvedHost, resolvedPort, dbUri.toString(),
                dbUri.getPath());
    }

    private static final class ConnectionInfo {
        private final String username;
        private final String password;
        private final String host;
        private final int port;
        private final String url;
        private final String path;

        private ConnectionInfo(String username, String password, String host, int port, String url, String path) {
            this.username = username;
            this.password = password;
            this.host = host;
            this.port = port;
            this.url = url;
            this.path = path;
        }
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
