package sqlancer.postgres.oracle.transaction;

import java.sql.SQLException;

import sqlancer.common.oracle.TestOracle;
import sqlancer.fucci.FucciGlobalState;
import sqlancer.fucci.FucciOptions;
import sqlancer.fucci.bridge.PostgreSQLFucciAdapter;
import sqlancer.fucci.oracle.FucciCompositeOracle;
import sqlancer.postgres.PostgresGlobalState;
import sqlancer.postgres.PostgresOptions;

/**
 * PostgreSQL Fucci Oracle包装类。
 * 将Fucci Composite Oracle适配到PostgreSQL GlobalState。
 */
public class PostgresFucciOracle implements TestOracle<PostgresGlobalState> {

    /** Fucci GlobalState包装 */
    private FucciGlobalState fucciState;

    /** 内部Fucci Oracle */
    private FucciCompositeOracle innerOracle;

    public PostgresFucciOracle(PostgresGlobalState pgState) throws SQLException {
        // 创建Fucci GlobalState包装PostgreSQL State
        this.fucciState = new FucciGlobalState(pgState, "postgres");
        this.fucciState.setConnection(pgState.getConnection());
        this.fucciState.setFucciAdapter(new PostgreSQLFucciAdapter());

        // 复制配置
        this.fucciState.setRandomly(pgState.getRandomly());
        this.fucciState.setMainOptions(pgState.getOptions());
        this.fucciState.setStateLogger(pgState.getLogger());
        this.fucciState.setState(pgState.getState());
        this.fucciState.setManager(pgState.getManager());
        this.fucciState.setDatabaseName(pgState.getDatabaseName());

        // 从PostgresOptions配置FucciOptions
        PostgresOptions pgOptions = pgState.getDbmsSpecificOptions();
        FucciOptions fucciOptions = fucciState.getFucciOptions();
        fucciOptions.oracleType = pgOptions.fucciOracleType;
        fucciOptions.isolationLevel = pgOptions.fucciIsolationLevel;
        fucciOptions.scheduleCount = pgOptions.fucciScheduleCount;

        // 创建内部Fucci Oracle
        this.innerOracle = new FucciCompositeOracle(fucciState);
    }

    @Override
    public void check() throws Exception {
        innerOracle.check();
    }

    @Override
    public String getLastQueryString() {
        return innerOracle.getLastQueryString();
    }

    @Override
    public sqlancer.Reproducer<PostgresGlobalState> getLastReproducer() {
        return null;
    }
}