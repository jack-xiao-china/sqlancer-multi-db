package sqlancer.fucci.reducer;

/**
 * Fucci Oracle检查器接口。
 * 用于判断简化后的测试用例是否仍能触发Bug。
 */
public interface FucciOracleChecker {

    /**
     * 判断测试用例是否会触发Bug
     *
     * @param testCase 测试用例
     * @return 是否触发Bug
     */
    boolean hasBug(FucciTestCase testCase);

    /**
     * 判断测试用例字符串是否会触发Bug
     *
     * @param testCaseString 测试用例字符串
     * @return 是否触发Bug
     */
    boolean hasBug(String testCaseString);

    /**
     * 获取Bug报告信息
     *
     * @return Bug报告
     */
    String getBugReport();

    /**
     * 清除Bug报告
     */
    void clearBugReport();
}