package sqlancer.postgres;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import sqlancer.IgnoreMeException;
import sqlancer.Main;
import sqlancer.MainOptions;
import sqlancer.Randomly;
import sqlancer.SQLConnection;
import sqlancer.common.query.SQLQueryAdapter;

public final class PostgresBombard {

    static final int SCHEMA_REFRESH_INTERVAL = 15;

    private final PostgresProvider provider;
    private final MainOptions options;
    private final PostgresOptions postgresOptions;
    private final String databaseName;
    private final long seed;
    private final String runDirectoryName;
    private final AtomicLong remainingQueries;

    public PostgresBombard(PostgresProvider provider, MainOptions options, PostgresOptions postgresOptions,
            String databaseName, long seed, String runDirectoryName) {
        this.provider = provider;
        this.options = options;
        this.postgresOptions = postgresOptions;
        this.databaseName = databaseName;
        this.seed = seed;
        this.runDirectoryName = runDirectoryName;
        this.remainingQueries = new AtomicLong(options.getNrQueries());
    }

    public void run() throws Exception {
        PostgresGlobalState bootstrapState = createWorkerState(-1);
        try (SQLConnection con = provider.createDatabase(bootstrapState)) {
            Main.nrDatabases.incrementAndGet();
            bootstrapState.setConnection(con);
            bootstrapState.setManager(new Main.QueryManager<>(bootstrapState));
            provider.bootstrapBombardDatabase(bootstrapState);
        } finally {
            closeQuietly(bootstrapState);
        }

        int workerCount = Math.max(1, postgresOptions.getBombardWorkers());
        ExecutorService workerPool = Executors.newFixedThreadPool(workerCount);
        List<Future<?>> futures = new ArrayList<>();
        for (int workerId = 0; workerId < workerCount; workerId++) {
            final int localWorkerId = workerId;
            futures.add(workerPool.submit(() -> runWorker(localWorkerId)));
        }
        try {
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            workerPool.shutdownNow();
        }
    }

    private void runWorker(int workerId) {
        PostgresGlobalState state = createWorkerState(workerId);
        long sequence = 0;
        long refreshCounter = 0;
        try {
            if (!connect(state)) {
                return;
            }
            while (!Thread.currentThread().isInterrupted() && remainingQueries.getAndDecrement() > 0) {
                try {
                    SQLQueryAdapter query = provider.getQueryForBombard(state, workerId, sequence++);
                    boolean success = query.execute(state, false);
                    Main.nrQueries.incrementAndGet();
                    if (query.couldAffectSchema() || refreshCounter++ % SCHEMA_REFRESH_INTERVAL == 0) {
                        refreshSchema(state);
                    }
                    if (!success && state.getSchema().getDatabaseTables().isEmpty()) {
                        refreshSchema(state);
                    }
                } catch (IgnoreMeException ignored) {
                    refreshSchemaQuietly(state);
                } catch (SQLException e) {
                    Main.nrUnsuccessfulActions.incrementAndGet();
                    if (isConnectionException(e)) {
                        return;
                    }
                    refreshSchemaQuietly(state);
                } catch (AssertionError ignored) {
                    refreshSchemaQuietly(state);
                } catch (Throwable ignored) {
                    refreshSchemaQuietly(state);
                }
            }
        } finally {
            closeQuietly(state);
        }
    }

    private PostgresGlobalState createWorkerState(int workerId) {
        PostgresGlobalState state = new PostgresGlobalState();
        state.setDatabaseName(databaseName);
        state.setMainOptions(options);
        state.setDbmsSpecificOptions(postgresOptions);
        state.setRandomly(new Randomly(seed + workerId + 1L));
        state.setState(provider.getStateToReproduce(databaseName));
        String loggerName = workerId < 0 ? databaseName + "-bootstrap" : databaseName + "-w" + workerId;
        state.setStateLogger(new Main.StateLogger(loggerName, provider, options,
                options.getLogDir() != null ? new java.io.File(options.getLogDir()) : null, runDirectoryName));
        state.setManager(new Main.QueryManager<>(state));
        return state;
    }

    private boolean connect(PostgresGlobalState state) {
        closeQuietly(state);
        try {
            state.setConnection(provider.createDatabaseConnection(options, postgresOptions, databaseName));
            refreshSchema(state);
            return true;
        } catch (SQLException e) {
            closeQuietly(state);
            return false;
        } catch (Throwable ignored) {
            closeQuietly(state);
            return false;
        }
    }

    private void refreshSchema(PostgresGlobalState state) throws Exception {
        provider.readFunctions(state);
        state.updateSchema();
    }

    private void refreshSchemaQuietly(PostgresGlobalState state) {
        try {
            refreshSchema(state);
        } catch (Exception ignored) {
            // A stale local schema is acceptable in bombard mode.
        }
    }

    private void closeQuietly(PostgresGlobalState state) {
        if (state.getConnection() == null) {
            return;
        }
        try {
            state.getConnection().close();
        } catch (SQLException ignored) {
            // ignore
        } finally {
            state.setConnection(null);
        }
    }

    private boolean isConnectionException(SQLException e) {
        String sqlState = e.getSQLState();
        if (sqlState != null && sqlState.startsWith("08")) {
            return true;
        }
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("connection reset") || lower.contains("connection refused")
                        || lower.contains("connection is closed") || lower.contains("terminating connection")
                        || lower.contains("broken pipe")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
