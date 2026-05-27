package sqlancer.gaussdba.oracle.transaction;

import java.sql.SQLException;

import sqlancer.common.oracle.TestOracle;
import sqlancer.fucci.FucciGlobalState;
import sqlancer.fucci.FucciOptions;
import sqlancer.fucci.bridge.GaussDBAFucciAdapter;
import sqlancer.fucci.oracle.FucciCompositeOracle;
import sqlancer.gaussdba.GaussDBAGlobalState;
import sqlancer.gaussdba.GaussDBAOptions;

/**
 * GaussDB-A Fucci Oracle包装类。
 * 将Fucci Composite Oracle适配到GaussDB-A GlobalState。
 */
public class GaussDBAFucciOracle implements TestOracle<GaussDBAGlobalState> {

    /** Fucci GlobalState包装 */
    private FucciGlobalState fucciState;

    /** 内部Fucci Oracle */
    private FucciCompositeOracle innerOracle;

    public GaussDBAFucciOracle(GaussDBAGlobalState gaussdbaState) throws SQLException {
        // 创建Fucci GlobalState包装GaussDB-A State
        this.fucciState = new FucciGlobalState(gaussdbaState, "gaussdba");
        this.fucciState.setConnection(gaussdbaState.getConnection());
        this.fucciState.setFucciAdapter(new GaussDBAFucciAdapter());

        // 复制配置
        this.fucciState.setRandomly(gaussdbaState.getRandomly());
        this.fucciState.setMainOptions(gaussdbaState.getOptions());
        this.fucciState.setStateLogger(gaussdbaState.getLogger());
        this.fucciState.setState(gaussdbaState.getState());
        this.fucciState.setManager(gaussdbaState.getManager());
        this.fucciState.setDatabaseName(gaussdbaState.getDatabaseName());

        // 从GaussDBAOptions配置FucciOptions
        GaussDBAOptions gaussdbaOptions = gaussdbaState.getDbmsSpecificOptions();
        FucciOptions fucciOptions = fucciState.getFucciOptions();
        fucciOptions.oracleType = gaussdbaOptions.fucciOracleType;
        fucciOptions.isolationLevel = gaussdbaOptions.fucciIsolationLevel;
        fucciOptions.scheduleCount = gaussdbaOptions.fucciScheduleCount;

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
    public sqlancer.Reproducer<GaussDBAGlobalState> getLastReproducer() {
        return null;
    }
}