package sqlancer.fucci;

import java.util.Arrays;
import java.util.List;

import sqlancer.Randomly;
import sqlancer.common.transaction.IsolationLevel;

/**
 * Fucci隔离级别定义。
 * 支持MySQL、PostgreSQL、GaussDB-M、GaussDB-A四个数据库的隔离级别。
 */
public class FucciIsolation {

    /**
     * Fucci隔离级别枚举，实现SQLancer的IsolationLevel接口
     */
    public enum FucciIsolationLevel implements IsolationLevel {
        READ_UNCOMMITTED("READ UNCOMMITTED"),
        READ_COMMITTED("READ COMMITTED"),
        REPEATABLE_READ("REPEATABLE READ"),
        SERIALIZABLE("SERIALIZABLE");

        private final String name;

        FucciIsolationLevel(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        /**
         * 根据字符串获取隔离级别
         */
        public static FucciIsolationLevel fromString(String str) {
            for (FucciIsolationLevel level : values()) {
                if (level.name.equalsIgnoreCase(str) || level.name().equalsIgnoreCase(str)) {
                    return level;
                }
            }
            throw new IllegalArgumentException("Unknown isolation level: " + str);
        }
    }

    /**
     * 根据DBMS类型返回支持的隔离级别列表
     *
     * @param dbmsType DBMS类型: mysql/postgres/gaussdbm/gaussdba
     * @return 支持的隔离级别列表
     */
    public static List<FucciIsolationLevel> getSupportedIsolationLevels(String dbmsType) {
        switch (dbmsType.toLowerCase()) {
            case "mysql":
                // MySQL支持全部4种隔离级别
                return Arrays.asList(FucciIsolationLevel.values());
            case "postgres":
                // PostgreSQL不支持READ UNCOMMITTED(实际等同于READ COMMITTED)
                return Arrays.asList(
                        FucciIsolationLevel.READ_COMMITTED,
                        FucciIsolationLevel.REPEATABLE_READ,
                        FucciIsolationLevel.SERIALIZABLE);
            case "gaussdbm":
                // GaussDB-M(MySQL兼容)支持RC/RR/SERIALIZABLE
                return Arrays.asList(
                        FucciIsolationLevel.READ_COMMITTED,
                        FucciIsolationLevel.REPEATABLE_READ,
                        FucciIsolationLevel.SERIALIZABLE);
            case "gaussdba":
                // GaussDB-A(Oracle兼容)支持RC/RR/SERIALIZABLE
                return Arrays.asList(
                        FucciIsolationLevel.READ_COMMITTED,
                        FucciIsolationLevel.REPEATABLE_READ,
                        FucciIsolationLevel.SERIALIZABLE);
            default:
                // 默认返回三种常见隔离级别
                return Arrays.asList(
                        FucciIsolationLevel.READ_COMMITTED,
                        FucciIsolationLevel.REPEATABLE_READ,
                        FucciIsolationLevel.SERIALIZABLE);
        }
    }

    /**
     * 随机选择一个隔离级别
     *
     * @param dbmsType DBMS类型
     * @return 随机选择的隔离级别
     */
    public static FucciIsolationLevel getRandomIsolationLevel(String dbmsType) {
        List<FucciIsolationLevel> levels = getSupportedIsolationLevels(dbmsType);
        return Randomly.fromList(levels);
    }

    /**
     * 根据配置字符串获取隔离级别
     * - "RANDOM": 随机选择
     * - 其他: 指定隔离级别
     *
     * @param isolationStr 配置字符串
     * @param dbmsType DBMS类型
     * @return 隔离级别
     */
    public static FucciIsolationLevel getIsolationLevel(String isolationStr, String dbmsType) {
        if (isolationStr.equalsIgnoreCase("RANDOM")) {
            return getRandomIsolationLevel(dbmsType);
        }
        return FucciIsolationLevel.fromString(isolationStr);
    }

    /**
     * 快照点判断辅助方法
     * MySQL/MariaDB: SELECT语句是快照点
     * TiDB: BEGIN语句是快照点
     * PostgreSQL: BEGIN语句是快照点(取决于隔离级别)
     *
     * @param dbmsType DBMS类型
     * @param stmtType 语句类型
     * @param isolationLevel 隔离级别
     * @return 是否是快照点
     */
    public static boolean isSnapshotPoint(String dbmsType, String stmtType, FucciIsolationLevel isolationLevel) {
        switch (dbmsType.toLowerCase()) {
            case "mysql":
            case "gaussdbm":
                // MySQL/GaussDB-M: SELECT语句触发快照
                return stmtType.equalsIgnoreCase("SELECT");
            case "postgres":
            case "gaussdba":
                // PostgreSQL/GaussDB-A: BEGIN语句触发快照
                return stmtType.equalsIgnoreCase("BEGIN");
            default:
                return stmtType.equalsIgnoreCase("BEGIN");
        }
    }
}