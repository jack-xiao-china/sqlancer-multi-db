package sqlancer.fucci.reducer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sqlancer.Randomly;
import sqlancer.common.transaction.TxStatement;

/**
 * Fucci简化器主类。
 * 实现四层简化框架:
 * 1. 语句删除层 - 删除不影响Bug触发的语句
 * 2. 语句简化层 - 简化语句结构(删除列、简化表等)
 * 3. 表达式简化层 - 简化WHERE表达式
 * 4. 常量简化层 - 替换复杂常量为简单值
 */
public class FucciReducer {

    /** 第一层: 语句删除层选择器 */
    private FucciOrderSelector<FucciStatementType> stmtDeleteSelector;

    /** 第二层: 语句简化层选择器 */
    private FucciOrderSelector<FucciSimplifyType> stmtSimplifySelector;

    /** 每层最大简化次数 */
    private int maxReduceCount;

    /** 语句类型映射(按类型分组语句) */
    private Map<FucciStatementType, List<TxStatement>> stmtTypeMap;

    /** 简化失败的语句映射 */
    private Map<FucciStatementType, List<TxStatement>> stmtFailMap;

    /** Oracle检查器 */
    private FucciOracleChecker oracleChecker;

    /** 简化统计 */
    private int totalReduceCount;
    private int validReduceCount;
    private int[] reduceCountByLevel;
    private int[] validReduceCountByLevel;

    /** 简化前后长度统计 */
    private int[] lengthBeforeReduce;
    private int[] lengthAfterReduce;

    /**
     * 构造函数
     *
     * @param selectorType 选择器类型(0=random, 1=probability, 2=epsilon-greedy)
     * @param maxReduceCount 每层最大简化次数
     */
    public FucciReducer(int selectorType, int maxReduceCount) {
        this.maxReduceCount = maxReduceCount;
        this.stmtTypeMap = new HashMap<>();
        this.stmtFailMap = new HashMap<>();

        // 初始化统计数组
        this.reduceCountByLevel = new int[4];
        this.validReduceCountByLevel = new int[4];
        this.lengthBeforeReduce = new int[4];
        this.lengthAfterReduce = new int[4];

        // 初始化候选列表
        List<FucciStatementType> stmtDeleteCandidates = new ArrayList<>();
        stmtDeleteCandidates.add(FucciStatementType.SELECT);
        stmtDeleteCandidates.add(FucciStatementType.UPDATE);
        stmtDeleteCandidates.add(FucciStatementType.INSERT);
        stmtDeleteCandidates.add(FucciStatementType.DELETE);
        stmtDeleteCandidates.add(FucciStatementType.CREATE_INDEX);

        List<FucciSimplifyType> stmtSimplifyCandidates = new ArrayList<>();
        stmtSimplifyCandidates.add(FucciSimplifyType.DEL_WHERE_EXPR);
        stmtSimplifyCandidates.add(FucciSimplifyType.DEL_INSERT_COL);
        stmtSimplifyCandidates.add(FucciSimplifyType.DEL_UPDATE_COL);
        stmtSimplifyCandidates.add(FucciSimplifyType.DEL_SELECT_COL);

        // 初始化选择器
        switch (selectorType) {
            case 0:
                stmtDeleteSelector = new FucciRandomOrderSelector<>(stmtDeleteCandidates);
                stmtSimplifySelector = new FucciRandomOrderSelector<>(stmtSimplifyCandidates);
                break;
            case 1:
                stmtDeleteSelector = new FucciProbabilityTableOrderSelector<>(stmtDeleteCandidates);
                stmtSimplifySelector = new FucciProbabilityTableOrderSelector<>(stmtSimplifyCandidates);
                break;
            case 2:
                stmtDeleteSelector = new FucciEpsilonGreedyOrderSelector<>(stmtDeleteCandidates, 0.1);
                stmtSimplifySelector = new FucciEpsilonGreedyOrderSelector<>(stmtSimplifyCandidates, 0.1);
                break;
            default:
                stmtDeleteSelector = new FucciRandomOrderSelector<>(stmtDeleteCandidates);
                stmtSimplifySelector = new FucciRandomOrderSelector<>(stmtSimplifyCandidates);
        }

        // 初始化语句类型映射
        for (FucciStatementType type : FucciStatementType.values()) {
            stmtTypeMap.put(type, new ArrayList<>());
            stmtFailMap.put(type, new ArrayList<>());
        }
    }

    /**
     * 设置Oracle检查器
     *
     * @param oracleChecker Oracle检查器
     */
    public void setOracleChecker(FucciOracleChecker oracleChecker) {
        this.oracleChecker = oracleChecker;
    }

    /**
     * 简化测试用例
     *
     * @param testCase 原始测试用例
     * @return 简化后的测试用例
     */
    public FucciTestCase reduce(FucciTestCase testCase) {
        // 清空统计
        clearStatistics();

        // 记录原始长度
        lengthBeforeReduce[0] = testCase.getLength();

        // 按语句类型分组
        categorizeStatements(testCase);

        // 第一层: 语句删除
        testCase = reduceByStatementDeletion(testCase);

        // 第二层: 语句简化
        testCase = reduceByStatementSimplification(testCase);

        // 第三层: 表达式简化(简化实现)
        testCase = reduceByExpressionSimplification(testCase);

        // 第四层: 常量简化(简化实现)
        testCase = reduceByConstantSimplification(testCase);

        return testCase;
    }

    /**
     * 清空统计数据
     */
    private void clearStatistics() {
        totalReduceCount = 0;
        validReduceCount = 0;
        for (int i = 0; i < 4; i++) {
            reduceCountByLevel[i] = 0;
            validReduceCountByLevel[i] = 0;
        }
        for (FucciStatementType type : FucciStatementType.values()) {
            stmtTypeMap.get(type).clear();
            stmtFailMap.get(type).clear();
        }
    }

    /**
     * 按语句类型分组
     *
     * @param testCase 测试用例
     */
    private void categorizeStatements(FucciTestCase testCase) {
        // 分组准备语句(用于统计，但不影响简化)
        // 准备语句是String类型，不直接加入TxStatement列表
        for (String stmt : testCase.getPrepareStatements()) {
            parseStatementType(stmt); // 仅解析用于日志分析
        }

        // 分组事务语句
        if (testCase.getTransaction1() != null) {
            for (TxStatement stmt : testCase.getTransaction1().getStatements()) {
                FucciStatementType type = parseTxStatementType(stmt);
                if (type != FucciStatementType.BEGIN && type != FucciStatementType.COMMIT
                        && type != FucciStatementType.ROLLBACK) {
                    stmtTypeMap.get(type).add(stmt);
                }
            }
        }

        if (testCase.getTransaction2() != null) {
            for (TxStatement stmt : testCase.getTransaction2().getStatements()) {
                FucciStatementType type = parseTxStatementType(stmt);
                if (type != FucciStatementType.BEGIN && type != FucciStatementType.COMMIT
                        && type != FucciStatementType.ROLLBACK) {
                    stmtTypeMap.get(type).add(stmt);
                }
            }
        }
    }

    /**
     * 第一层: 语句删除层简化
     *
     * @param testCase 测试用例
     * @return 简化后的测试用例
     */
    private FucciTestCase reduceByStatementDeletion(FucciTestCase testCase) {
        for (int i = 0; i < maxReduceCount; i++) {
            // 复制测试用例
            FucciTestCase clonedTestCase = testCase.copy();

            // 选择要删除的语句类型
            FucciStatementType typeToDelete = stmtDeleteSelector.selectNext();
            if (typeToDelete == null) {
                break;
            }

            // 从该类型中随机选择一个语句删除
            List<TxStatement> stmtsOfType = stmtTypeMap.get(typeToDelete);
            if (stmtsOfType.isEmpty()) {
                continue;
            }

            TxStatement stmtToDelete = Randomly.fromList(stmtsOfType);

            // 尝试删除
            boolean deleted = tryDeleteStatement(clonedTestCase, stmtToDelete);

            reduceCountByLevel[0]++;
            totalReduceCount++;

            if (deleted && oracleChecker != null && oracleChecker.hasBug(clonedTestCase)) {
                // 删除成功且仍能触发Bug
                testCase = clonedTestCase;
                stmtDeleteSelector.updateWeight(typeToDelete, true);
                validReduceCount++;
                validReduceCountByLevel[0]++;
            } else {
                // 删除失败或不再触发Bug
                stmtFailMap.get(typeToDelete).add(stmtToDelete);
                stmtDeleteSelector.updateWeight(typeToDelete, false);
            }
        }

        lengthAfterReduce[0] = testCase.getLength();
        return testCase;
    }

    /**
     * 第二层: 语句简化层
     *
     * @param testCase 测试用例
     * @return 简化后的测试用例
     */
    private FucciTestCase reduceByStatementSimplification(FucciTestCase testCase) {
        lengthBeforeReduce[1] = testCase.getLength();

        for (int i = 0; i < maxReduceCount; i++) {
            FucciSimplifyType simplifyType = stmtSimplifySelector.selectNext();
            if (simplifyType == null) {
                break;
            }

            FucciTestCase clonedTestCase = testCase.copy();

            boolean simplified = false;
            switch (simplifyType) {
                case DEL_WHERE_EXPR:
                    simplified = tryDeleteWhereClause(clonedTestCase);
                    break;
                case DEL_INSERT_COL:
                    simplified = tryDeleteInsertColumn(clonedTestCase);
                    break;
                case DEL_UPDATE_COL:
                    simplified = tryDeleteUpdateColumn(clonedTestCase);
                    break;
                case DEL_SELECT_COL:
                    simplified = tryDeleteSelectColumn(clonedTestCase);
                    break;
                case SIMPLIFY_TABLE:
                    // 简化表定义(简化实现)
                    simplified = false;
                    break;
                case DEL_ORDER_BY:
                    simplified = tryDeleteOrderBy(clonedTestCase);
                    break;
                case DEL_LIMIT:
                    simplified = tryDeleteLimit(clonedTestCase);
                    break;
                case SIMPLIFY_JOIN:
                    // 简化JOIN条件(简化实现)
                    simplified = false;
                    break;
            }

            reduceCountByLevel[1]++;
            totalReduceCount++;

            if (simplified && oracleChecker != null && oracleChecker.hasBug(clonedTestCase)) {
                testCase = clonedTestCase;
                stmtSimplifySelector.updateWeight(simplifyType, true);
                validReduceCount++;
                validReduceCountByLevel[1]++;
            } else {
                stmtSimplifySelector.updateWeight(simplifyType, false);
            }
        }

        lengthAfterReduce[1] = testCase.getLength();
        return testCase;
    }

    /**
     * 第三层: 表达式简化层(简化实现)
     *
     * @param testCase 测试用例
     * @return 简化后的测试用例
     */
    private FucciTestCase reduceByExpressionSimplification(FucciTestCase testCase) {
        lengthBeforeReduce[2] = testCase.getLength();
        // 简化实现: 尝试简化WHERE表达式
        // 完整实现需要表达式解析器
        lengthAfterReduce[2] = testCase.getLength();
        return testCase;
    }

    /**
     * 第四层: 常量简化层(简化实现)
     *
     * @param testCase 测试用例
     * @return 简化后的测试用例
     */
    private FucciTestCase reduceByConstantSimplification(FucciTestCase testCase) {
        lengthBeforeReduce[3] = testCase.getLength();
        // 简化实现: 尝试替换复杂常量
        // 完整实现需要常量识别和替换
        lengthAfterReduce[3] = testCase.getLength();
        return testCase;
    }

    // ============ 辅助方法 ============

    /**
     * 解析语句类型
     */
    private FucciStatementType parseStatementType(String stmt) {
        String upperStmt = stmt.toUpperCase().trim();
        if (upperStmt.startsWith("SELECT")) {
            return FucciStatementType.SELECT;
        } else if (upperStmt.startsWith("UPDATE")) {
            return FucciStatementType.UPDATE;
        } else if (upperStmt.startsWith("INSERT")) {
            return FucciStatementType.INSERT;
        } else if (upperStmt.startsWith("DELETE")) {
            return FucciStatementType.DELETE;
        } else if (upperStmt.startsWith("CREATE INDEX")) {
            return FucciStatementType.CREATE_INDEX;
        }
        return FucciStatementType.SELECT; // 默认
    }

    /**
     * 解析TxStatement类型
     */
    private FucciStatementType parseTxStatementType(TxStatement stmt) {
        String typeStr = stmt.getType().toString();
        try {
            return FucciStatementType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return FucciStatementType.SELECT;
        }
    }

    /**
     * 尝试删除语句
     */
    private boolean tryDeleteStatement(FucciTestCase testCase, TxStatement stmtToDelete) {
        // 从提交顺序中删除
        testCase.getSubmittedOrder().remove(stmtToDelete);

        // 从事务中删除
        if (testCase.getTransaction1() != null) {
            testCase.getTransaction1().getStatements().remove(stmtToDelete);
        }
        if (testCase.getTransaction2() != null) {
            testCase.getTransaction2().getStatements().remove(stmtToDelete);
        }

        return true;
    }

    /**
     * 尝试删除WHERE条件
     */
    private boolean tryDeleteWhereClause(FucciTestCase testCase) {
        // 简化实现: 从语句中移除WHERE子句
        // 完整实现需要SQL解析
        return false;
    }

    /**
     * 尝试删除INSERT列
     */
    private boolean tryDeleteInsertColumn(FucciTestCase testCase) {
        // 简化实现
        return false;
    }

    /**
     * 尝试删除UPDATE列
     */
    private boolean tryDeleteUpdateColumn(FucciTestCase testCase) {
        // 简化实现
        return false;
    }

    /**
     * 尝试删除SELECT列
     */
    private boolean tryDeleteSelectColumn(FucciTestCase testCase) {
        // 简化实现
        return false;
    }

    /**
     * 尝试删除ORDER BY
     */
    private boolean tryDeleteOrderBy(FucciTestCase testCase) {
        // 简化实现
        return false;
    }

    /**
     * 尝试删除LIMIT
     */
    private boolean tryDeleteLimit(FucciTestCase testCase) {
        // 简化实现
        return false;
    }

    // ============ 统计方法 ============

    /**
     * 获取总简化次数
     */
    public int getTotalReduceCount() {
        return totalReduceCount;
    }

    /**
     * 获取有效简化次数
     */
    public int getValidReduceCount() {
        return validReduceCount;
    }

    /**
     * 获取有效简化率
     */
    public double getValidReduceRate() {
        if (totalReduceCount == 0) {
            return 0.0;
        }
        return validReduceCount / (double) totalReduceCount;
    }

    /**
     * 获取简化率(字符串长度减少比例)
     */
    public double getSimplificationRate() {
        if (lengthBeforeReduce[0] == 0) {
            return 0.0;
        }
        int finalLength = lengthAfterReduce[3] > 0 ? lengthAfterReduce[3] : lengthAfterReduce[0];
        return 1.0 - (finalLength / (double) lengthBeforeReduce[0]);
    }

    /**
     * 获取简化统计报告
     */
    public String getStatisticsReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Fucci Reducer Statistics ===\n");
        sb.append("Total reduce attempts: ").append(totalReduceCount).append("\n");
        sb.append("Valid reduces: ").append(validReduceCount).append("\n");
        sb.append("Valid reduce rate: ").append(String.format("%.2f%%", getValidReduceRate() * 100)).append("\n");
        sb.append("Simplification rate: ").append(String.format("%.2f%%", getSimplificationRate() * 100)).append("\n");

        sb.append("\nPer-level statistics:\n");
        for (int i = 0; i < 4; i++) {
            sb.append("  Level ").append(i + 1).append(": ");
            sb.append(reduceCountByLevel[i]).append(" attempts, ");
            sb.append(validReduceCountByLevel[i]).append(" valid, ");
            if (reduceCountByLevel[i] > 0) {
                double rate = validReduceCountByLevel[i] / (double) reduceCountByLevel[i];
                sb.append(String.format("%.2f%%", rate * 100));
            } else {
                sb.append("N/A");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}