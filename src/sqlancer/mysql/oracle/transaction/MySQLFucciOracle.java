package sqlancer.mysql.oracle.transaction;

import java.sql.SQLException;

import sqlancer.common.oracle.TestOracle;
import sqlancer.fucci.FucciGlobalState;
import sqlancer.fucci.FucciOptions;
import sqlancer.fucci.bridge.MySQLFucciAdapter;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLOptions;
import sqlancer.fucci.oracle.FucciCompositeOracle;

/**
 * MySQL Fucci Oracle包装类。
 * 将Fucci Composite Oracle适配到MySQL GlobalState。
 */
public class MySQLFucciOracle implements TestOracle<MySQLGlobalState> {

    /** Fucci GlobalState包装 */
    private FucciGlobalState fucciState;

    /** 内部Fucci Oracle */
    private FucciCompositeOracle innerOracle;

    public MySQLFucciOracle(MySQLGlobalState mysqlState) throws SQLException {
        // 创建Fucci GlobalState包装MySQL State
        this.fucciState = new FucciGlobalState(mysqlState, "mysql");
        this.fucciState.setConnection(mysqlState.getConnection());
        this.fucciState.setFucciAdapter(new MySQLFucciAdapter());

        // 复制配置
        this.fucciState.setRandomly(mysqlState.getRandomly());
        this.fucciState.setMainOptions(mysqlState.getOptions());
        this.fucciState.setStateLogger(mysqlState.getLogger());
        this.fucciState.setState(mysqlState.getState());
        this.fucciState.setManager(mysqlState.getManager());
        this.fucciState.setDatabaseName(mysqlState.getDatabaseName());

        // 从MySQLOptions配置FucciOptions
        MySQLOptions mysqlOptions = mysqlState.getDbmsSpecificOptions();
        FucciOptions fucciOptions = fucciState.getFucciOptions();
        fucciOptions.oracleType = mysqlOptions.fucciOracleType;
        fucciOptions.isolationLevel = mysqlOptions.fucciIsolationLevel;
        fucciOptions.scheduleCount = mysqlOptions.fucciScheduleCount;

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
    public sqlancer.Reproducer<MySQLGlobalState> getLastReproducer() {
        return null;
    }
}