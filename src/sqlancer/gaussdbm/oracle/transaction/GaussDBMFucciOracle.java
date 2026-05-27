package sqlancer.gaussdbm.oracle.transaction;

import java.sql.SQLException;

import sqlancer.common.oracle.TestOracle;
import sqlancer.fucci.FucciGlobalState;
import sqlancer.fucci.FucciOptions;
import sqlancer.fucci.bridge.GaussDBMFucciAdapter;
import sqlancer.fucci.oracle.FucciCompositeOracle;
import sqlancer.gaussdbm.GaussDBMGlobalState;
import sqlancer.gaussdbm.GaussDBMOptions;

/**
 * GaussDB-M Fucci Oracle包装类。
 * 将Fucci Composite Oracle适配到GaussDB-M GlobalState。
 */
public class GaussDBMFucciOracle implements TestOracle<GaussDBMGlobalState> {

    /** Fucci GlobalState包装 */
    private FucciGlobalState fucciState;

    /** 内部Fucci Oracle */
    private FucciCompositeOracle innerOracle;

    public GaussDBMFucciOracle(GaussDBMGlobalState gaussdbmState) throws SQLException {
        // 创建Fucci GlobalState包装GaussDB-M State
        this.fucciState = new FucciGlobalState(gaussdbmState, "gaussdbm");
        this.fucciState.setConnection(gaussdbmState.getConnection());
        this.fucciState.setFucciAdapter(new GaussDBMFucciAdapter());

        // 复制配置
        this.fucciState.setRandomly(gaussdbmState.getRandomly());
        this.fucciState.setMainOptions(gaussdbmState.getOptions());
        this.fucciState.setStateLogger(gaussdbmState.getLogger());
        this.fucciState.setState(gaussdbmState.getState());
        this.fucciState.setManager(gaussdbmState.getManager());
        this.fucciState.setDatabaseName(gaussdbmState.getDatabaseName());

        // 从GaussDBMOptions配置FucciOptions
        GaussDBMOptions gaussdbmOptions = gaussdbmState.getDbmsSpecificOptions();
        FucciOptions fucciOptions = fucciState.getFucciOptions();
        fucciOptions.oracleType = gaussdbmOptions.fucciOracleType;
        fucciOptions.isolationLevel = gaussdbmOptions.fucciIsolationLevel;
        fucciOptions.scheduleCount = gaussdbmOptions.fucciScheduleCount;

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
    public sqlancer.Reproducer<GaussDBMGlobalState> getLastReproducer() {
        return null;
    }
}