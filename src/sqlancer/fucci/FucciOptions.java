package sqlancer.fucci;

import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.Parameter;

import sqlancer.DBMSSpecificOptions;

/**
 * Fucci Oracle配置选项类。
 * 支持三种Oracle：DT(差异测试)、MT(变形测试)、CS(约束求解)
 */
public class FucciOptions implements DBMSSpecificOptions<FucciOracleFactory> {

    public enum FucciOracleType {
        DT, MT, CS, ALL
    }

    @Parameter(names = "--fucci-oracle", description = "Fucci Oracle类型: DT/MT/CS/ALL")
    public FucciOracleType oracleType = FucciOracleType.ALL;

    @Parameter(names = "--fucci-ref-host", description = "DT Oracle参照DB主机地址")
    public String refHost;

    @Parameter(names = "--fucci-ref-port", description = "DT Oracle参照DB端口")
    public int refPort;

    @Parameter(names = "--fucci-ref-dbms", description = "DT Oracle参照DB类型")
    public String refDbms;

    @Parameter(names = "--fucci-ref-user", description = "DT Oracle参照DB用户名")
    public String refUser = "root";

    @Parameter(names = "--fucci-ref-password", description = "DT Oracle参照DB密码")
    public String refPassword = "";

    @Parameter(names = "--fucci-ref-database", description = "DT Oracle参照DB数据库名")
    public String refDatabase = "test";

    @Parameter(names = "--fucci-reducer", description = "启用Reducer简化器", arity = 1)
    public boolean useReducer = false;

    @Parameter(names = "--fucci-reducer-type", description = "简化策略: random/prob-table/epsilon-greedy")
    public String reducerType = "random";

    @Parameter(names = "--fucci-reducer-count", description = "每层最大简化次数")
    public int maxReduceCount = 5;

    @Parameter(names = "--fucci-conflict-type", description = "冲突构造策略")
    public String conflictType = "random";

    @Parameter(names = "--fucci-isolation", description = "测试隔离级别")
    public String isolationLevel = "RANDOM";

    @Parameter(names = "--fucci-tx-count", description = "事务数量(当前固定为2)")
    public int txCount = 2;

    @Parameter(names = "--fucci-schedule-count", description = "调度序列采样数量")
    public int scheduleCount = 10;

    @Parameter(names = "--fucci-insert-conflict", description = "是否考虑INSERT冲突", arity = 1)
    public boolean insertConflict = false;

    @Parameter(names = "--fucci-filter-duplicate", description = "过滤重复Bug", arity = 1)
    public boolean filterDuplicateBug = false;

    @Override
    public List<FucciOracleFactory> getTestOracleFactory() {
        List<FucciOracleFactory> factories = new ArrayList<>();
        switch (oracleType) {
            case DT:
                factories.add(FucciOracleFactory.FUCCI_DT);
                break;
            case MT:
                factories.add(FucciOracleFactory.FUCCI_MT);
                break;
            case CS:
                factories.add(FucciOracleFactory.FUCCI_CS);
                break;
            case ALL:
                factories.add(FucciOracleFactory.FUCCI_ALL);
                break;
        }
        return factories;
    }

    public FucciOracleType getOracleType() { return oracleType; }
    public boolean isDTOracle() { return oracleType == FucciOracleType.DT || oracleType == FucciOracleType.ALL; }
    public boolean isMTOracle() { return oracleType == FucciOracleType.MT || oracleType == FucciOracleType.ALL; }
    public boolean isCSOracle() { return oracleType == FucciOracleType.CS || oracleType == FucciOracleType.ALL; }
    public String getRefHost() { return refHost; }
    public int getRefPort() { return refPort; }
    public String getRefDbms() { return refDbms; }
    public String getRefUser() { return refUser; }
    public String getRefPassword() { return refPassword; }
    public String getRefDatabase() { return refDatabase; }
    public boolean useReducer() { return useReducer; }
    public String getReducerType() { return reducerType; }
    public int getMaxReduceCount() { return maxReduceCount; }
    public String getConflictType() { return conflictType; }
    public String getIsolationLevel() { return isolationLevel; }
    public int getTxCount() { return txCount; }
    public int getScheduleCount() { return scheduleCount; }
    public boolean isInsertConflict() { return insertConflict; }
    public boolean isFilterDuplicateBug() { return filterDuplicateBug; }
    public boolean hasRefDbConfig() { return refHost != null && !refHost.isEmpty() && refPort > 0; }
}