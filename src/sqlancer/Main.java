package sqlancer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.JCommander.Builder;
import com.beust.jcommander.Parameter;

import sqlancer.citus.CitusProvider;
import sqlancer.clickhouse.ClickHouseProvider;
import sqlancer.cnosdb.CnosDBProvider;
import sqlancer.cockroachdb.CockroachDBProvider;
import sqlancer.common.log.Loggable;
import sqlancer.common.query.Query;
import sqlancer.common.query.SQLancerResultSet;
import sqlancer.databend.DatabendProvider;
import sqlancer.doris.DorisProvider;
import sqlancer.duckdb.DuckDBProvider;
import sqlancer.gaussdbm.GaussDBMProvider;
import sqlancer.h2.H2Provider;
import sqlancer.hive.HiveProvider;
import sqlancer.hsqldb.HSQLDBProvider;
import sqlancer.mariadb.MariaDBProvider;
import sqlancer.materialize.MaterializeProvider;
import sqlancer.mysql.MySQLProvider;
import sqlancer.mysql.MySQLOptions;
import sqlancer.mysql.MySQLOracleFactory;
import sqlancer.oceanbase.OceanBaseProvider;
import sqlancer.postgres.PostgresProvider;
import sqlancer.presto.PrestoProvider;
import sqlancer.questdb.QuestDBProvider;
import sqlancer.sqlite3.SQLite3Provider;
import sqlancer.tidb.TiDBProvider;
import sqlancer.yugabyte.ycql.YCQLProvider;
import sqlancer.yugabyte.ysql.YSQLProvider;

public final class Main {

    public static final File LOG_DIRECTORY = new File("logs");
    public static volatile AtomicLong nrQueries = new AtomicLong();
    public static volatile AtomicLong nrDatabases = new AtomicLong();
    public static volatile AtomicLong nrSuccessfulActions = new AtomicLong();
    public static volatile AtomicLong nrUnsuccessfulActions = new AtomicLong();
    public static volatile AtomicLong threadsShutdown = new AtomicLong();
    static boolean progressMonitorStarted;

    static {
        System.setProperty(org.slf4j.simple.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "ERROR");
        if (!LOG_DIRECTORY.exists()) {
            LOG_DIRECTORY.mkdir();
        }
    }

    private Main() {
    }

    public static final class StateLogger {

        private final File loggerFile;
        private File curFile;
        private File queryPlanFile;
        private File reduceFile;
        private FileWriter logFileWriter;
        public FileWriter currentFileWriter;
        private FileWriter queryPlanFileWriter;
        private FileWriter reduceFileWriter;
        private Path reproduceFilePath;

        private final boolean logEachSelect;
        private final boolean logQueryPlan;

        private final boolean useReducer;
        private final DatabaseProvider<?, ?, ?> databaseProvider;

        private static final class AlsoWriteToConsoleFileWriter extends FileWriter {

            AlsoWriteToConsoleFileWriter(File file) throws IOException {
                super(file);
            }

            @Override
            public Writer append(CharSequence arg0) throws IOException {
                System.err.println(arg0);
                return super.append(arg0);
            }

            @Override
            public void write(String str) throws IOException {
                System.err.println(str);
                super.write(str);
            }
        }

        public StateLogger(String databaseName, DatabaseProvider<?, ?, ?> provider, MainOptions options) {
            this(databaseName, provider, options, null, null);
        }

        public StateLogger(String databaseName, DatabaseProvider<?, ?, ?> provider, MainOptions options, File logBaseDir,
                String runDirectoryName) {
            File base = logBaseDir != null ? logBaseDir : LOG_DIRECTORY;
            // Directory layout: <base>/<dbms>/<runDirectoryName>/
            // e.g. logs/mysql/eet_2026_0409_1453/
            File dbmsDir = new File(base, provider.getDBMSName());
            File dir = runDirectoryName != null ? new File(dbmsDir, runDirectoryName) : dbmsDir;
            if (dir.exists() && !dir.isDirectory()) {
                throw new AssertionError(dir);
            }
            ensureExists(dir);
            loggerFile = new File(dir, databaseName + ".log");
            logEachSelect = options.logEachSelect();
            if (logEachSelect) {
                curFile = new File(dir, databaseName + "-cur.log");
            }
            logQueryPlan = options.logQueryPlan();
            if (logQueryPlan) {
                queryPlanFile = new File(dir, databaseName + "-plan.log");
            }
            this.useReducer = options.useReducer();
            if (useReducer) {
                File reduceFileDir = new File(dir, "reduce");
                if (!reduceFileDir.exists()) {
                    reduceFileDir.mkdir();
                }
                this.reduceFile = new File(reduceFileDir, databaseName + "-reduce.log");
            }
            if (options.serializeReproduceState()) {
                File reproduceFileDir = new File(dir, "reproduce");
                if (!reproduceFileDir.exists()) {
                    reproduceFileDir.mkdir();
                }
                reproduceFilePath = new File(reproduceFileDir, databaseName + ".ser").toPath();
            }
            this.databaseProvider = provider;
        }

        private void ensureExists(File dir) {
            if (!dir.exists()) {
                try {
                    Files.createDirectories(dir.toPath());
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            }
        }

        private FileWriter getLogFileWriter() {
            if (logFileWriter == null) {
                try {
                    logFileWriter = new AlsoWriteToConsoleFileWriter(loggerFile);
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            }
            return logFileWriter;
        }

        public FileWriter getCurrentFileWriter() {
            if (!logEachSelect) {
                throw new UnsupportedOperationException();
            }
            if (currentFileWriter == null) {
                try {
                    currentFileWriter = new FileWriter(curFile, false);
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            }
            return currentFileWriter;
        }

        public FileWriter getQueryPlanFileWriter() {
            if (!logQueryPlan) {
                throw new UnsupportedOperationException();
            }
            if (queryPlanFileWriter == null) {
                try {
                    queryPlanFileWriter = new FileWriter(queryPlanFile, true);
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            }
            return queryPlanFileWriter;
        }

        public FileWriter getReduceFileWriter() {
            if (!useReducer) {
                throw new UnsupportedOperationException();
            }
            if (reduceFileWriter == null) {
                try {
                    reduceFileWriter = new FileWriter(reduceFile, false);
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            }
            return reduceFileWriter;
        }

        public void writeCurrent(StateToReproduce state) {
            if (!logEachSelect) {
                throw new UnsupportedOperationException();
            }
            printState(getCurrentFileWriter(), state);
            try {
                currentFileWriter.flush();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void writeCurrent(String input) {
            write(databaseProvider.getLoggableFactory().createLoggable(input));
        }

        public void writeCurrentNoLineBreak(String input) {
            write(databaseProvider.getLoggableFactory().createLoggableWithNoLinebreak(input));
        }

        private void write(Loggable loggable) {
            if (!logEachSelect) {
                throw new UnsupportedOperationException();
            }
            try {
                getCurrentFileWriter().write(loggable.getLogString());

                currentFileWriter.flush();
            } catch (IOException e) {
                throw new AssertionError();
            }
        }

        public void writeQueryPlan(String queryPlan) {
            if (!logQueryPlan) {
                throw new UnsupportedOperationException();
            }
            try {
                getQueryPlanFileWriter().append(removeNamesFromQueryPlans(queryPlan));
                queryPlanFileWriter.flush();
            } catch (IOException e) {
                throw new AssertionError();
            }
        }

        public void logReducer(String reducerLog) {
            FileWriter reduceFileWriter = getReduceFileWriter();

            StringBuilder sb = new StringBuilder();
            sb.append("[reducer log] ");
            sb.append(reducerLog);
            try {
                reduceFileWriter.write(sb.toString());
            } catch (IOException e) {
                throw new AssertionError(e);
            } finally {
                try {
                    reduceFileWriter.flush();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        public void logReduced(StateToReproduce state) {
            FileWriter reduceFileWriter = getReduceFileWriter();

            StringBuilder sb = new StringBuilder();
            for (Query<?> s : state.getStatements()) {
                sb.append(databaseProvider.getLoggableFactory().createLoggable(s.getLogString()).getLogString());
            }
            try {
                reduceFileWriter.write(sb.toString());

            } catch (IOException e) {
                throw new AssertionError(e);
            } finally {
                try {
                    reduceFileWriter.flush();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

        }

        public void logException(Throwable reduce, StateToReproduce state) {
            Loggable stackTrace = getStackTrace(reduce);
            FileWriter logFileWriter2 = getLogFileWriter();
            try {
                logFileWriter2.write(stackTrace.getLogString());
                printState(logFileWriter2, state);
            } catch (IOException e) {
                throw new AssertionError(e);
            } finally {
                try {
                    logFileWriter2.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private Loggable getStackTrace(Throwable e1) {
            return databaseProvider.getLoggableFactory().convertStacktraceToLoggable(e1);
        }

        private void printState(FileWriter writer, StateToReproduce state) {
            StringBuilder sb = new StringBuilder();

            sb.append(databaseProvider.getLoggableFactory()
                    .getInfo(state.getDatabaseName(), state.getDatabaseVersion(), state.getSeedValue()).getLogString());

            for (Query<?> s : state.getStatements()) {
                sb.append(databaseProvider.getLoggableFactory().createLoggable(s.getLogString()).getLogString());
            }
            try {
                writer.write(sb.toString());
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        private String removeNamesFromQueryPlans(String queryPlan) {
            String result = queryPlan;
            result = result.replaceAll("t[0-9]+", "t0"); // Avoid duplicate tables
            result = result.replaceAll("v[0-9]+", "v0"); // Avoid duplicate views
            result = result.replaceAll("i[0-9]+", "i0"); // Avoid duplicate indexes
            return result + "\n";
        }

        public Path getReproduceFilePath() {
            return reproduceFilePath;
        }
    }

    public static class QueryManager<C extends SQLancerDBConnection> {

        private final GlobalState<?, ?, C> globalState;

        QueryManager(GlobalState<?, ?, C> globalState) {
            this.globalState = globalState;
        }

        public boolean execute(Query<C> q, String... fills) throws Exception {
            boolean success;
            success = q.execute(globalState, fills);
            Main.nrSuccessfulActions.addAndGet(1);
            if (globalState.getOptions().loggerPrintFailed() || success) {
                globalState.getState().logStatement(q);
            }
            return success;
        }

        public SQLancerResultSet executeAndGet(Query<C> q, String... fills) throws Exception {
            globalState.getState().logStatement(q);
            SQLancerResultSet result;
            result = q.executeAndGet(globalState, fills);
            Main.nrSuccessfulActions.addAndGet(1);
            return result;
        }

        public void incrementSelectQueryCount() {
            Main.nrQueries.addAndGet(1);
        }

        public Long getSelectQueryCount() {
            return Main.nrQueries.get();
        }

        public void incrementCreateDatabase() {
            Main.nrDatabases.addAndGet(1);
        }

    }

    public static void main(String[] args) {
        System.exit(executeMain(args));
    }

    public static class DBMSExecutor<G extends GlobalState<O, ?, C>, O extends DBMSSpecificOptions<?>, C extends SQLancerDBConnection> {

        private final DatabaseProvider<G, O, C> provider;
        private final MainOptions options;
        private final O command;
        private final String databaseName;
        private final String runDirectoryName;
        private StateLogger logger;
        private StateToReproduce stateToRepro;
        private final Randomly r;

        public DBMSExecutor(DatabaseProvider<G, O, C> provider, MainOptions options, O dbmsSpecificOptions,
                String databaseName, Randomly r, String runDirectoryName) {
            this.provider = provider;
            this.options = options;
            this.databaseName = databaseName;
            this.command = dbmsSpecificOptions;
            this.r = r;
            this.runDirectoryName = runDirectoryName;
        }

        private G createGlobalState() {
            try {
                return provider.getGlobalStateClass().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        public O getCommand() {
            return command;
        }

        public void testConnection() throws Exception {
            G state = getInitializedGlobalState(options.getRandomSeed());
            try (SQLancerDBConnection con = provider.createDatabase(state)) {
                return;
            }
        }

        public void run() throws Exception {
            G state = createGlobalState();
            stateToRepro = provider.getStateToReproduce(databaseName);
            stateToRepro.seedValue = r.getSeed();
            state.setState(stateToRepro);
            logger = createStateLogger(databaseName);
            state.setRandomly(r);
            state.setDatabaseName(databaseName);
            state.setMainOptions(options);
            state.setDbmsSpecificOptions(command);
            try (C con = provider.createDatabase(state)) {
                QueryManager<C> manager = new QueryManager<>(state);
                try {
                    stateToRepro.databaseVersion = con.getDatabaseVersion();
                } catch (Exception e) {
                    // ignore
                }
                state.setConnection(con);
                state.setStateLogger(logger);
                state.setManager(manager);
                if (options.logEachSelect()) {
                    logger.writeCurrent(state.getState());
                }
                Reproducer<G> reproducer = null;
                if (options.enableQPG()) {
                    provider.generateAndTestDatabaseWithQueryPlanGuidance(state);
                } else {
                    reproducer = provider.generateAndTestDatabase(state);
                }
                try {
                    logger.getCurrentFileWriter().close();
                    logger.currentFileWriter = null;
                } catch (IOException e) {
                    throw new AssertionError(e);
                }

                if (options.serializeReproduceState() && reproducer != null) {
                    stateToRepro.serialize(logger.getReproduceFilePath());
                }
                if (options.reduceAST() && !options.useReducer()) {
                    throw new AssertionError("To reduce AST, use-reducer option must be enabled first");
                }
                if (options.useReducer()) {
                    if (reproducer == null) {
                        logger.getReduceFileWriter().write("current oracle does not support experimental reducer.");
                        throw new IgnoreMeException();
                    }
                    G newGlobalState = createGlobalState();
                    newGlobalState.setState(stateToRepro);
                    newGlobalState.setRandomly(r);
                    newGlobalState.setDatabaseName(databaseName);
                    newGlobalState.setMainOptions(options);
                    newGlobalState.setDbmsSpecificOptions(command);
                    QueryManager<C> newManager = new QueryManager<>(newGlobalState);
                    newGlobalState.setStateLogger(createStateLogger(databaseName));
                    newGlobalState.setManager(newManager);

                    Reducer<G> reducer = new StatementReducer<>(provider);
                    reducer.reduce(state, reproducer, newGlobalState);

                    if (options.reduceAST()) {
                        Reducer<G> astBasedReducer = new ASTBasedReducer<>(provider);
                        astBasedReducer.reduce(state, reproducer, newGlobalState);
                    }

                    try {
                        logger.getReduceFileWriter().close();
                        logger.reduceFileWriter = null;
                    } catch (IOException e) {
                        throw new AssertionError(e);
                    }

                    throw new AssertionError("Found a potential bug, please check reducer log for detail.");
                }
            }
        }

        private G getInitializedGlobalState(long seed) {
            G state = createGlobalState();
            stateToRepro = provider.getStateToReproduce(databaseName);
            stateToRepro.seedValue = seed;
            state.setState(stateToRepro);
            logger = createStateLogger(databaseName);
            Randomly r = new Randomly(seed);
            state.setRandomly(r);
            state.setDatabaseName(databaseName);
            state.setMainOptions(options);
            state.setDbmsSpecificOptions(command);
            return state;
        }

        public StateLogger getLogger() {
            return logger;
        }

        public StateToReproduce getStateToReproduce() {
            return stateToRepro;
        }

        private StateLogger createStateLogger(String databaseName) {
            File configured = options.getLogDir() != null ? new File(options.getLogDir()) : null;
            return new StateLogger(databaseName, provider, options, configured, runDirectoryName);
        }
    }

    public static class DBMSExecutorFactory<G extends GlobalState<O, ?, C>, O extends DBMSSpecificOptions<?>, C extends SQLancerDBConnection> {

        private final DatabaseProvider<G, O, C> provider;
        private final MainOptions options;
        private final O command;
        private String runDirectoryName;

        public DBMSExecutorFactory(DatabaseProvider<G, O, C> provider, MainOptions options) {
            this.provider = provider;
            this.options = options;
            this.command = createCommand();
        }

        private O createCommand() {
            try {
                return provider.getOptionClass().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        public O getCommand() {
            return command;
        }

        @SuppressWarnings("unchecked")
        public DBMSExecutor<G, O, C> getDBMSExecutor(String databaseName, Randomly r) {
            try {
                return new DBMSExecutor<G, O, C>(provider.getClass().getDeclaredConstructor().newInstance(), options,
                        command, databaseName, r, getRunDirectoryName());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        private synchronized String getRunDirectoryName() {
            if (options.getLogDir() != null) {
                return null;
            }
            if (runDirectoryName == null) {
                runDirectoryName = computeRunDirectoryName(command);
            }
            return runDirectoryName;
        }

        static String computeRunDirectoryName(DBMSSpecificOptions<?> command) {
            String oracleName = "oracle";
            try {
                List<?> oracles = command.getTestOracleFactory();
                if (oracles != null && !oracles.isEmpty()) {
                    if (oracles.size() == 1) {
                        oracleName = String.valueOf(oracles.get(0));
                    } else {
                        oracleName = String.valueOf(oracles.get(0)) + "_plus";
                    }
                }
            } catch (Exception ignored) {
            }
            oracleName = oracleName.toLowerCase();
            DateFormat fmt = new SimpleDateFormat("yyyy_MMdd_HHmm");
            String ts = fmt.format(new Date());
            return oracleName + "_" + ts;
        }

        public DatabaseProvider<G, O, C> getProvider() {
            return provider;
        }

    }

    public static int executeMain(String... args) throws AssertionError {
        List<DatabaseProvider<?, ?, ?>> providers = getDBMSProviders();
        Map<String, DBMSExecutorFactory<?, ?, ?>> nameToProvider = new HashMap<>();
        MainOptions options = new MainOptions();
        Builder commandBuilder = JCommander.newBuilder().addObject(options);
        for (DatabaseProvider<?, ?, ?> provider : providers) {
            String name = provider.getDBMSName();
            DBMSExecutorFactory<?, ?, ?> executorFactory = new DBMSExecutorFactory<>(provider, options);
            commandBuilder = commandBuilder.addCommand(name, executorFactory.getCommand());
            nameToProvider.put(name, executorFactory);
        }
        JCommander jc = commandBuilder.programName("SQLancer").build();
        jc.parse(args);

        if (jc.getParsedCommand() == null || options.isHelp() || isCommandHelpRequested(jc, nameToProvider)) {
            if (jc.getParsedCommand() == null) {
                jc.usage();
            } else {
                JCommander cmdJc = jc.getCommands().get(jc.getParsedCommand());
                if (cmdJc != null) {
                    cmdJc.usage();
                } else {
                    jc.usage();
                }
            }
            printCommandSpecificHelp(jc.getParsedCommand());
            printOracleOptionsSummary(jc.getParsedCommand(), nameToProvider);
            return options.getErrorExitCode();
        }

        Randomly.initialize(options);
        if (options.printProgressInformation()) {
            startProgressMonitor();
            if (options.printProgressSummary()) {
                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

                    @Override
                    public void run() {
                        System.out.println("Overall execution statistics");
                        System.out.println("============================");
                        System.out.println(formatInteger(nrQueries.get()) + " queries");
                        System.out.println(formatInteger(nrDatabases.get()) + " databases");
                        System.out.println(
                                formatInteger(nrSuccessfulActions.get()) + " successfully-executed statements");
                        System.out.println(
                                formatInteger(nrUnsuccessfulActions.get()) + " unsuccessfully-executed statements");
                    }

                    private String formatInteger(long intValue) {
                        if (intValue > 1000) {
                            return String.format("%,9dk", intValue / 1000);
                        } else {
                            return String.format("%,10d", intValue);
                        }
                    }
                }));
            }
        }

        ExecutorService execService = Executors.newFixedThreadPool(options.getNumberConcurrentThreads());
        DBMSExecutorFactory<?, ?, ?> executorFactory = nameToProvider.get(jc.getParsedCommand());

        if (options.performConnectionTest()) {
            try {
                executorFactory.getDBMSExecutor(options.getDatabasePrefix() + "connectiontest", new Randomly())
                        .testConnection();
            } catch (Exception e) {
                System.err.println(
                        "SQLancer failed creating a test database, indicating that SQLancer might have failed connecting to the DBMS. In order to change the username, password, host and port, you can use the --username, --password, --host and --port options.\n\n");
                e.printStackTrace();
                return options.getErrorExitCode();
            }
        }
        final AtomicBoolean someOneFails = new AtomicBoolean(false);

        for (int i = 0; i < options.getTotalNumberTries(); i++) {
            final String databaseName = options.getDatabasePrefix() + i;
            final long seed;
            if (options.getRandomSeed() == -1) {
                seed = System.currentTimeMillis() + i;
            } else {
                seed = options.getRandomSeed() + i;
            }
            execService.execute(new Runnable() {

                @Override
                public void run() {
                    Thread.currentThread().setName(databaseName);
                    runThread(databaseName);
                }

                private void runThread(final String databaseName) {
                    Randomly r = new Randomly(seed);
                    try {
                        int maxNrDbs = options.getMaxGeneratedDatabases();
                        // run without a limit if maxNrDbs == -1
                        for (int i = 0; i < maxNrDbs || maxNrDbs == -1; i++) {
                            Boolean continueRunning = run(options, execService, executorFactory, r, databaseName);
                            if (!continueRunning) {
                                someOneFails.set(true);
                                break;
                            }
                        }
                    } finally {
                        threadsShutdown.addAndGet(1);
                        if (threadsShutdown.get() == options.getTotalNumberTries()) {
                            execService.shutdown();
                        }
                    }
                }

                private boolean run(MainOptions options, ExecutorService execService,
                        DBMSExecutorFactory<?, ?, ?> executorFactory, Randomly r, final String databaseName) {
                    DBMSExecutor<?, ?, ?> executor = executorFactory.getDBMSExecutor(databaseName, r);
                    try {
                        executor.run();
                        return true;
                    } catch (IgnoreMeException e) {
                        return true;
                    } catch (Throwable reduce) {
                        reduce.printStackTrace();
                        executor.getStateToReproduce().exception = reduce.getMessage();
                        executor.getLogger().logFileWriter = null;
                        executor.getLogger().logException(reduce, executor.getStateToReproduce());
                        if (options.serializeReproduceState()) {
                            executor.getStateToReproduce().logStatement(reduce.getMessage()); // add the error statement
                            executor.getStateToReproduce().serialize(executor.getLogger().getReproduceFilePath());
                        }
                        return false;
                    } finally {
                        try {
                            if (options.logEachSelect()) {
                                if (executor.getLogger().currentFileWriter != null) {
                                    executor.getLogger().currentFileWriter.close();
                                }
                                executor.getLogger().currentFileWriter = null;
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
        try {
            if (options.getTimeoutSeconds() == -1) {
                execService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            } else {
                execService.awaitTermination(options.getTimeoutSeconds(), TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return someOneFails.get() ? options.getErrorExitCode() : 0;
    }

    private static boolean isCommandHelpRequested(JCommander jc, Map<String, DBMSExecutorFactory<?, ?, ?>> nameToProvider) {
        String parsedCommand = jc.getParsedCommand();
        if (parsedCommand == null) {
            return false;
        }
        DBMSExecutorFactory<?, ?, ?> f = nameToProvider.get(parsedCommand);
        if (f == null) {
            return false;
        }
        Object cmd = f.getCommand();
        if (cmd instanceof MySQLOptions) {
            return ((MySQLOptions) cmd).isHelp();
        }
        return false;
    }

    private static void printCommandSpecificHelp(String parsedCommand) {
        if (parsedCommand == null) {
            return;
        }
        if ("mysql".equals(parsedCommand)) {
            printMySQLOracleHelp();
        } else if ("gaussdb-m".equals(parsedCommand)) {
            GaussDBMProvider.printOracleHelp();
        }
    }

    private static void printMySQLOracleHelp() {
        System.out.println();
        System.out.println("MySQL --oracle choices: " + java.util.Arrays.toString(MySQLOracleFactory.values()));
    }

    private static void printOracleOptionsSummary(String parsedCommand, Map<String, DBMSExecutorFactory<?, ?, ?>> nameToProvider) {
        List<String> commands = new ArrayList<>(nameToProvider.keySet());
        commands.sort(Comparator.naturalOrder());
        List<String> blocks = new ArrayList<>();

        for (String cmdName : commands) {
            if (parsedCommand != null && !parsedCommand.equals(cmdName)) {
                continue;
            }
            DBMSExecutorFactory<?, ?, ?> f = nameToProvider.get(cmdName);
            if (f == null) {
                continue;
            }
            Object command = f.getCommand();
            OracleHelpBlock block = OracleHelpBlock.from(cmdName, command);
            if (block != null) {
                blocks.add(block.render());
            }
        }

        if (blocks.isEmpty()) {
            return;
        }

        System.out.println();
        System.out.println("Oracle Options Summary:");
        for (String block : blocks) {
            System.out.print(block);
        }
    }

    private static final class OracleHelpBlock {
        private final String commandName;
        private final String description;
        private final String options;
        private final String defaultValue;

        private OracleHelpBlock(String commandName, String description, String options, String defaultValue) {
            this.commandName = commandName;
            this.description = description;
            this.options = options;
            this.defaultValue = defaultValue;
        }

        static OracleHelpBlock from(String commandName, Object command) {
            if (command == null) {
                return null;
            }
            Field oracleField = findOracleField(command.getClass());
            if (oracleField == null) {
                return null;
            }
            Parameter p = oracleField.getAnnotation(Parameter.class);
            if (p == null) {
                return null;
            }

            Class<? extends Enum<?>> enumClass = resolveOracleEnumClass(oracleField);
            if (enumClass == null) {
                return null;
            }

            String description = p.description();
            if (description == null || description.isBlank()) {
                description = "Specifies which test oracle should be used";
            }
            String options = buildSortedEnumOptions(enumClass);
            String defaultValue = resolveDefaultValue(command, oracleField);
            if (defaultValue == null || defaultValue.isBlank()) {
                defaultValue = "<unspecified>";
            }
            return new OracleHelpBlock(commandName, description, options, defaultValue);
        }

        private static String buildSortedEnumOptions(Class<? extends Enum<?>> enumClass) {
            Enum<?>[] constants = enumClass.getEnumConstants();
            if (constants == null || constants.length == 0) {
                return "[]";
            }
            String[] names = new String[constants.length];
            for (int i = 0; i < constants.length; i++) {
                names[i] = constants[i].name();
            }
            Arrays.sort(names);
            return "[" + String.join(", ", names) + "]";
        }

        private static Field findOracleField(Class<?> cls) {
            for (Field f : cls.getDeclaredFields()) {
                Parameter p = f.getAnnotation(Parameter.class);
                if (p == null) {
                    continue;
                }
                for (String n : p.names()) {
                    if ("--oracle".equals(n)) {
                        return f;
                    }
                }
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private static Class<? extends Enum<?>> resolveOracleEnumClass(Field oracleField) {
            Class<?> raw = oracleField.getType();
            if (raw.isEnum()) {
                return (Class<? extends Enum<?>>) raw;
            }
            if (List.class.isAssignableFrom(raw)) {
                Type t = oracleField.getGenericType();
                if (t instanceof ParameterizedType) {
                    Type[] args = ((ParameterizedType) t).getActualTypeArguments();
                    if (args.length == 1 && args[0] instanceof Class) {
                        Class<?> arg = (Class<?>) args[0];
                        if (arg.isEnum()) {
                            return (Class<? extends Enum<?>>) arg;
                        }
                    }
                }
            }
            return null;
        }

        private static String resolveDefaultValue(Object command, Field oracleField) {
            try {
                boolean old = oracleField.canAccess(command);
                oracleField.setAccessible(true);
                Object value = oracleField.get(command);
                if (!old) {
                    oracleField.setAccessible(false);
                }
                if (value == null) {
                    return null;
                }
                if (value instanceof List) {
                    return String.valueOf(value);
                }
                if (value instanceof Enum<?>) {
                    return "[" + value + "]";
                }
                return String.valueOf(value);
            } catch (Exception e) {
                return null;
            }
        }

        String render() {
            StringBuilder sb = new StringBuilder();
            sb.append("  ").append(commandName).append(":\n");
            sb.append("        Options:\n");
            sb.append("          --oracle\n");
            sb.append("            ").append(description).append(",\n");
            sb.append("            Options: ").append(options).append("\n");
            sb.append("            Default: ").append(defaultValue).append(",\n");
            return sb.toString();
        }
    }

    /**
     * To register a new provider, it is necessary to implement the DatabaseProvider interface and add an additional
     * configuration file, see https://docs.oracle.com/javase/9/docs/api/java/util/ServiceLoader.html. Currently, we use
     * an @AutoService annotation to create the configuration file automatically. This allows SQLancer to pick up
     * providers in other JARs on the classpath.
     *
     * @return The list of service providers on the classpath
     */
    static List<DatabaseProvider<?, ?, ?>> getDBMSProviders() {
        List<DatabaseProvider<?, ?, ?>> providers = new ArrayList<>();
        @SuppressWarnings("rawtypes")
        ServiceLoader<DatabaseProvider> loader = ServiceLoader.load(DatabaseProvider.class);
        for (DatabaseProvider<?, ?, ?> provider : loader) {
            providers.add(provider);
        }
        checkForIssue799(providers);
        return providers;
    }

    // see https://github.com/sqlancer/sqlancer/issues/799
    private static void checkForIssue799(List<DatabaseProvider<?, ?, ?>> providers) {
        if (providers.isEmpty()) {
            System.err.println(
                    "No DBMS implementations (i.e., instantiations of the DatabaseProvider class) were found. You likely ran into an issue described in https://github.com/sqlancer/sqlancer/issues/799. As a workaround, I now statically load all supported providers as of June 7, 2023.");
            providers.add(new CitusProvider());
            providers.add(new ClickHouseProvider());
            providers.add(new CnosDBProvider());
            providers.add(new CockroachDBProvider());
            providers.add(new DatabendProvider());
            providers.add(new DorisProvider());
            providers.add(new DuckDBProvider());
            providers.add(new GaussDBMProvider());
            providers.add(new H2Provider());
            providers.add(new HiveProvider());
            providers.add(new HSQLDBProvider());
            providers.add(new MariaDBProvider());
            providers.add(new MaterializeProvider());
            providers.add(new MySQLProvider());
            providers.add(new OceanBaseProvider());
            providers.add(new PrestoProvider());
            providers.add(new PostgresProvider());
            providers.add(new QuestDBProvider());
            providers.add(new SQLite3Provider());
            providers.add(new TiDBProvider());
            providers.add(new YCQLProvider());
            providers.add(new YSQLProvider());
        }
    }

    private static synchronized void startProgressMonitor() {
        if (progressMonitorStarted) {
            /*
             * it might be already started if, for example, the main method is called multiple times in a test (see
             * https://github.com/sqlancer/sqlancer/issues/90).
             */
            return;
        } else {
            progressMonitorStarted = true;
        }
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(new Runnable() {

            private long timeMillis = System.currentTimeMillis();
            private long lastNrQueries;
            private long lastNrDbs;

            {
                timeMillis = System.currentTimeMillis();
            }

            @Override
            public void run() {
                long elapsedTimeMillis = System.currentTimeMillis() - timeMillis;
                long currentNrQueries = nrQueries.get();
                long nrCurrentQueries = currentNrQueries - lastNrQueries;
                double throughput = nrCurrentQueries / (elapsedTimeMillis / 1000d);
                long currentNrDbs = nrDatabases.get();
                long nrCurrentDbs = currentNrDbs - lastNrDbs;
                double throughputDbs = nrCurrentDbs / (elapsedTimeMillis / 1000d);
                long successfulStatementsRatio = (long) (100.0 * nrSuccessfulActions.get()
                        / (nrSuccessfulActions.get() + nrUnsuccessfulActions.get()));
                DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                Date date = new Date();
                System.out.println(String.format(
                        "[%s] Executed %d queries (%d queries/s; %.2f/s dbs, successful statements: %2d%%). Threads shut down: %d.",
                        dateFormat.format(date), currentNrQueries, (int) throughput, throughputDbs,
                        successfulStatementsRatio, threadsShutdown.get()));
                timeMillis = System.currentTimeMillis();
                lastNrQueries = currentNrQueries;
                lastNrDbs = currentNrDbs;
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

}
