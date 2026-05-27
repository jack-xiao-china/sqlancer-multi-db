package sqlancer.fucci;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import sqlancer.SQLConnection;
import sqlancer.SQLGlobalState;
import sqlancer.fucci.bridge.DBMSFucciAdapter;
import sqlancer.fucci.bridge.GaussDBAFucciAdapter;
import sqlancer.fucci.bridge.GaussDBMFucciAdapter;
import sqlancer.fucci.bridge.MySQLFucciAdapter;
import sqlancer.fucci.bridge.PostgreSQLFucciAdapter;

/**
 * Fucci全局状态类。
 */
public class FucciGlobalState extends SQLGlobalState<FucciOptions, FucciSchema> {

    private SQLGlobalState<?, ?> originalState;
    private FucciOptions fucciOptions;
    private DBMSFucciAdapter fucciAdapter;
    private String dbmsType;
    private FucciIsolation.FucciIsolationLevel currentIsolationLevel;
    private SQLConnection refConnection;

    public FucciGlobalState() {
        this.fucciOptions = new FucciOptions();
    }

    public FucciGlobalState(SQLGlobalState<?, ?> originalState, String dbmsType) {
        this.originalState = originalState;
        this.dbmsType = dbmsType;
        this.fucciOptions = new FucciOptions();
        initializeAdapter(dbmsType);
    }

    private void initializeAdapter(String dbmsType) {
        switch (dbmsType.toLowerCase()) {
            case "mysql":
                this.fucciAdapter = new MySQLFucciAdapter();
                break;
            case "postgres":
            case "postgresql":
                this.fucciAdapter = new PostgreSQLFucciAdapter();
                break;
            case "gaussdbm":
                this.fucciAdapter = new GaussDBMFucciAdapter();
                break;
            case "gaussdba":
                this.fucciAdapter = new GaussDBAFucciAdapter();
                break;
            default:
                throw new IllegalArgumentException("Unsupported DBMS: " + dbmsType);
        }
    }

    @Override
    protected FucciSchema readSchema() throws SQLException {
        if (originalState != null) {
            return new FucciSchema(originalState.getSchema());
        }
        List<FucciTable> tables = new ArrayList<>();
        return new FucciSchema(tables);
    }

    public SQLGlobalState<?, ?> getOriginalState() { return originalState; }
    public void setOriginalState(SQLGlobalState<?, ?> originalState) { this.originalState = originalState; }
    public FucciOptions getFucciOptions() { return fucciOptions; }
    public void setFucciOptions(FucciOptions fucciOptions) { this.fucciOptions = fucciOptions; }
    public DBMSFucciAdapter getFucciAdapter() { return fucciAdapter; }
    public void setFucciAdapter(DBMSFucciAdapter fucciAdapter) { this.fucciAdapter = fucciAdapter; }
    public String getDbmsType() { return dbmsType; }
    public void setDbmsType(String dbmsType) { this.dbmsType = dbmsType; initializeAdapter(dbmsType); }
    public FucciIsolation.FucciIsolationLevel getCurrentIsolationLevel() { return currentIsolationLevel; }
    public void setCurrentIsolationLevel(FucciIsolation.FucciIsolationLevel isolationLevel) { this.currentIsolationLevel = isolationLevel; }
    public SQLConnection getRefConnection() { return refConnection; }
    public void setRefConnection(SQLConnection refConnection) { this.refConnection = refConnection; }
    public boolean isDTOracle() { return fucciOptions.isDTOracle(); }
    public boolean isMTOracle() { return fucciOptions.isMTOracle(); }
    public boolean isCSOracle() { return fucciOptions.isCSOracle(); }
    public List<FucciIsolation.FucciIsolationLevel> getSupportedIsolationLevels() { return FucciIsolation.getSupportedIsolationLevels(dbmsType); }
    public FucciIsolation.FucciIsolationLevel getRandomIsolationLevel() { return FucciIsolation.getRandomIsolationLevel(dbmsType); }

    @Override
    public String toString() {
        return String.format("FucciGlobalState{dbms=%s, oracle=%s, isolation=%s}", dbmsType, fucciOptions.getOracleType(), currentIsolationLevel);
    }
}