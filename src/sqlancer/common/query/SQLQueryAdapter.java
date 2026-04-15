package sqlancer.common.query;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

import sqlancer.GlobalState;
import sqlancer.Main;
import sqlancer.SQLConnection;

public class SQLQueryAdapter extends Query<SQLConnection> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * SQLSTATE-based fallback to handle localized error messages (e.g., non-English servers/clients).
     * These states commonly occur during random query generation and are typically filtered via ExpectedErrors.
     */
    private static final Set<String> COMMON_EXPECTED_SQLSTATES = Set.of(
            // Data exception (general - covers range errors, type mismatches, etc.)
            "22000",
            // Data exception: numeric value out of range
            "22003",
            // Data exception: division by zero
            "22012",
            // Data exception: interval field overflow
            "22015",
            // Data exception: invalid argument for various functions
            "2201E", "2201F", "2201G", "2201H", "2201I", "2201J", "2201K", "2201L", "2201M", "2201N",
            // Data exception: datetime field overflow / invalid time format
            "22008", "22009",
            // Data exception: invalid escape character / escape sequence
            "22019", "22025",
            // Data exception: character not in repertoire / invalid character value
            "22021", "2202B",
            // Data exception: string data length mismatch / right truncation
            "22026", "22001",
            // Data exception: invalid indicator parameter value
            "22010",
            // Data exception: null value not allowed in aggregate / set function
            "22004", "22005",
            // Data exception: floating point exception
            "22P01",
            // Data exception: invalid text representation (type conversion errors)
            "22P02",
            // Data exception: invalid binary representation
            "22P03",
            // Data exception: bad JSON / JSON text
            "22P04", "22P05",
            // Data exception: invalid XML / XML comment
            "2200N", "2200S", "2200T",
            // Undefined function/operator
            "42883",
            // Cannot coerce
            "42846",
            // Datatype mismatch (ALTER TABLE type conversion, etc.)
            "42804",
            // Undefined object (e.g., operator class / parameter / type)
            "42704",
            // Syntax error
            "42601",
            // Undefined table
            "42P01",
            // Invalid column reference / constraint does not exist (ON CONFLICT errors)
            "42P10",
            // Unique violation
            "23505",
            // Foreign key violation
            "23503",
            // Check violation
            "23514",
            // Feature not supported
            "0A000",
            // Collation mismatch / ambiguous collation
            "42P22", "42P09");

    private final String query;
    private final ExpectedErrors expectedErrors;
    private final boolean couldAffectSchema;

    public SQLQueryAdapter(String query) {
        this(query, new ExpectedErrors());
    }

    public SQLQueryAdapter(String query, boolean couldAffectSchema) {
        this(query, new ExpectedErrors(), couldAffectSchema);
    }

    public SQLQueryAdapter(String query, ExpectedErrors expectedErrors) {
        this(query, expectedErrors, guessAffectSchemaFromQuery(query));
    }

    private static boolean guessAffectSchemaFromQuery(String query) {
        return query.contains("CREATE TABLE") && !query.startsWith("EXPLAIN");
    }

    public SQLQueryAdapter(String query, ExpectedErrors expectedErrors, boolean couldAffectSchema) {
        this(query, expectedErrors, couldAffectSchema, true);
    }

    public SQLQueryAdapter(String query, ExpectedErrors expectedErrors, boolean couldAffectSchema,
            boolean canonicalizeString) {
        if (canonicalizeString) {
            this.query = canonicalizeString(query);
        } else {
            this.query = query;
        }
        this.expectedErrors = expectedErrors;
        this.couldAffectSchema = couldAffectSchema;
        checkQueryString();
    }

    private String canonicalizeString(String s) {
        if (s.endsWith(";")) {
            return s;
        } else if (!s.contains("--")) {
            return s + ";";
        } else {
            // query contains a comment
            return s;
        }
    }

    private void checkQueryString() {
        if (!couldAffectSchema && guessAffectSchemaFromQuery(query)) {
            throw new AssertionError("CREATE TABLE statements should set couldAffectSchema to true");
        }
    }

    @Override
    public String getQueryString() {
        return query;
    }

    @Override
    public String getUnterminatedQueryString() {
        String result;
        if (query.endsWith(";")) {
            result = query.substring(0, query.length() - 1);
        } else {
            result = query;
        }
        assert !result.endsWith(";");
        return result;
    }

    /**
     * This method is used to mostly oracles, which need to report exceptions. We set the reportException parameter to
     * true by default meaning that exceptions are reported.
     *
     * @param globalState
     * @param fills
     *
     * @return whether the query was executed successfully
     *
     * @param <G>
     *
     * @throws SQLException
     */
    @Override
    public <G extends GlobalState<?, ?, SQLConnection>> boolean execute(G globalState, String... fills)
            throws SQLException {
        return execute(globalState, true, fills);
    }

    /**
     * This method is used to DQE oracles, DQE does not check exception separately, while other testing methods may
     * need. We use reportException to control this behavior. For a specific DBMS used DQE oracle, we call this method
     * and pass a boolean value of false as an argument.
     *
     * @param globalState
     * @param reportException
     * @param fills
     *
     * @return whether the query was executed successfully
     *
     * @param <G>
     *
     * @throws SQLException
     */
    public <G extends GlobalState<?, ?, SQLConnection>> boolean execute(G globalState, boolean reportException,
            String... fills) throws SQLException {
        return internalExecute(globalState.getConnection(), reportException, fills);
    }

    protected <G extends GlobalState<?, ?, SQLConnection>> boolean internalExecute(SQLConnection connection,
            boolean reportException, String... fills) throws SQLException {
        Statement s;
        if (fills.length > 0) {
            s = connection.prepareStatement(fills[0]);
            for (int i = 1; i < fills.length; i++) {
                ((PreparedStatement) s).setString(i, fills[i]);
            }
        } else {
            s = connection.createStatement();
        }
        try {
            if (fills.length > 0) {
                ((PreparedStatement) s).execute();
            } else {
                s.execute(query);
            }
            Main.nrSuccessfulActions.addAndGet(1);
            return true;
        } catch (Exception e) {
            Main.nrUnsuccessfulActions.addAndGet(1);
            if (reportException) {
                checkException(e);
            }
            return false;
        } finally {
            s.close();
        }
    }

    public void checkException(Exception e) throws AssertionError {
        Throwable ex = e;

        while (ex != null) {
            if (expectedErrors.errorIsExpected(ex.getMessage())) {
                return;
            }
            if (ex instanceof SQLException) {
                String state = ((SQLException) ex).getSQLState();
                if (state != null && COMMON_EXPECTED_SQLSTATES.contains(state)) {
                    return;
                }
            }
            ex = ex.getCause();
        }

        throw new AssertionError(query, e);
    }

    @Override
    public <G extends GlobalState<?, ?, SQLConnection>> SQLancerResultSet executeAndGet(G globalState, String... fills)
            throws SQLException {
        return executeAndGet(globalState, true, fills);
    }

    public <G extends GlobalState<?, ?, SQLConnection>> SQLancerResultSet executeAndGet(G globalState,
            boolean reportException, String... fills) throws SQLException {
        return internalExecuteAndGet(globalState.getConnection(), reportException, fills);
    }

    protected <G extends GlobalState<?, ?, SQLConnection>> SQLancerResultSet internalExecuteAndGet(
            SQLConnection connection, boolean reportException, String... fills) throws SQLException {
        Statement s;
        if (fills.length > 0) {
            s = connection.prepareStatement(fills[0]);
            for (int i = 1; i < fills.length; i++) {
                ((PreparedStatement) s).setString(i, fills[i]);
            }
        } else {
            s = connection.createStatement();
        }
        ResultSet result;
        try {
            if (fills.length > 0) {
                result = ((PreparedStatement) s).executeQuery();
            } else {
                result = s.executeQuery(query);
            }
            Main.nrSuccessfulActions.addAndGet(1);
            if (result == null) {
                return null;
            }
            return new SQLancerResultSet(result);
        } catch (Exception e) {
            s.close();
            Main.nrUnsuccessfulActions.addAndGet(1);
            if (reportException) {
                checkException(e);
            }
            return null;
        }
    }

    @Override
    public boolean couldAffectSchema() {
        return couldAffectSchema;
    }

    @Override
    public ExpectedErrors getExpectedErrors() {
        return expectedErrors;
    }

    @Override
    public String getLogString() {
        return getQueryString();
    }
}
