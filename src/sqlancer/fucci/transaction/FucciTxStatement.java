package sqlancer.fucci.transaction;

import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.transaction.Transaction;
import sqlancer.common.transaction.TxSQLQueryAdapter;
import sqlancer.common.transaction.TxStatement;
import sqlancer.common.transaction.TxStatementType;
import sqlancer.fucci.mvcc.View;

/**
 * Fucci扩展语句类。
 * 在SQLancer TxStatement基础上添加MVCC和约束求解所需的状态。
 */
public class FucciTxStatement extends TxStatement {

    /**
     * Fucci语句类型枚举
     */
    public enum FucciStatementType implements TxStatementType {
        BEGIN, COMMIT, ROLLBACK,
        SELECT, SELECT_FOR_SHARE, SELECT_FOR_UPDATE,
        UPDATE, DELETE, INSERT, REPLACE,
        SET,
        UNKNOWN
    }

    // ============ Fucci扩展状态 ============

    /** WHERE条件谓词表达式字符串 */
    private String predicate;

    /** 语句执行前的视图快照 */
    private View beforeView;

    /** 语句执行后的视图快照 */
    private View afterView;

    /** 语句执行结果数据 */
    private Object[] resultData;

    /** 是否阻塞执行 */
    private boolean blockedExecution;

    /** 语句涉及的主键值集合 */
    private int[] involvedRowIds;

    /**
     * 构造函数
     *
     * @param transaction 所属事务
     * @param queryAdapter SQL查询适配器
     */
    public FucciTxStatement(Transaction transaction, TxSQLQueryAdapter queryAdapter) {
        super(transaction, queryAdapter);
    }

    public FucciTxStatement(Transaction transaction, SQLQueryAdapter queryAdapter) {
        this(transaction, new TxSQLQueryAdapter(queryAdapter));
    }

    @Override
    public boolean isSelectType() {
        return this.type == FucciStatementType.SELECT
                || this.type == FucciStatementType.SELECT_FOR_SHARE
                || this.type == FucciStatementType.SELECT_FOR_UPDATE;
    }

    @Override
    public boolean isNoErrorType() {
        return this.type == FucciStatementType.BEGIN
                || this.type == FucciStatementType.COMMIT
                || this.type == FucciStatementType.ROLLBACK;
    }

    @Override
    public boolean isEndTxType() {
        return this.type == FucciStatementType.COMMIT || this.type == FucciStatementType.ROLLBACK;
    }

    @Override
    protected void setStatementType() {
        String stmt = txQueryAdapter.getQueryString().replace(";", "").toUpperCase();
        FucciStatementType realType;
        try {
            realType = FucciStatementType.valueOf(stmt.split(" ")[0]);
        } catch (IllegalArgumentException e) {
            realType = FucciStatementType.UNKNOWN;
        }
        // 处理SELECT FOR UPDATE/SHARE
        if (realType == FucciStatementType.SELECT) {
            int forIdx = stmt.indexOf("FOR ");
            if (forIdx == -1) {
                forIdx = stmt.indexOf("LOCK IN SHARE MODE");
            }
            if (forIdx != -1) {
                String postfix = stmt.substring(forIdx);
                if (postfix.equals("FOR SHARE") || postfix.equals("LOCK IN SHARE MODE")) {
                    realType = FucciStatementType.SELECT_FOR_SHARE;
                } else if (postfix.equals("FOR UPDATE")) {
                    realType = FucciStatementType.SELECT_FOR_UPDATE;
                }
            }
        }
        this.type = realType;
    }

    @Override
    protected boolean reportDeadlock(String errorInfo) {
        if (errorInfo == null) {
            return false;
        }
        // MySQL: Deadlock found when trying to get lock
        // PostgreSQL: deadlock detected
        // GaussDB: Deadlock detected
        return errorInfo.contains("Deadlock") || errorInfo.contains("deadlock")
                || errorInfo.contains("Lock wait timeout exceeded");
    }

    @Override
    protected boolean reportRollback(String errorInfo) {
        if (errorInfo == null) {
            return false;
        }
        return errorInfo.contains("rollback") || errorInfo.contains("ROLLBACK");
    }

    // ============ Predicate解析相关方法 ============

    /**
     * 从SQL语句中提取WHERE条件谓词
     *
     * @return WHERE条件字符串，无WHERE则返回null
     */
    public String extractPredicate() {
        String query = txQueryAdapter.getQueryString();
        int whereIdx = query.toUpperCase().indexOf("WHERE");
        if (whereIdx == -1) {
            return null;
        }
        // 提取WHERE后的条件
        String afterWhere = query.substring(whereIdx + 5).trim();
        // 移除末尾的分号和其他子句(LIMIT, ORDER BY等)
        int limitIdx = afterWhere.toUpperCase().indexOf("LIMIT");
        int orderIdx = afterWhere.toUpperCase().indexOf("ORDER BY");
        int forIdx = afterWhere.toUpperCase().indexOf("FOR");
        int lockIdx = afterWhere.toUpperCase().indexOf("LOCK IN");

        int endIdx = afterWhere.length();
        if (limitIdx != -1 && limitIdx < endIdx) {
            endIdx = limitIdx;
        }
        if (orderIdx != -1 && orderIdx < endIdx) {
            endIdx = orderIdx;
        }
        if (forIdx != -1 && forIdx < endIdx) {
            endIdx = forIdx;
        }
        if (lockIdx != -1 && lockIdx < endIdx) {
            endIdx = lockIdx;
        }

        this.predicate = afterWhere.substring(0, endIdx).trim();
        return this.predicate;
    }

    /**
     * 获取谓词表达式
     *
     * @return 谓词字符串
     */
    public String getPredicate() {
        if (predicate == null) {
            extractPredicate();
        }
        return predicate;
    }

    /**
     * 设置谓词表达式
     *
     * @param predicate 谓词字符串
     */
    public void setPredicate(String predicate) {
        this.predicate = predicate;
    }

    // ============ 视图快照相关方法 ============

    /**
     * 获取执行前视图
     *
     * @return 执行前视图
     */
    public View getBeforeView() {
        return beforeView;
    }

    /**
     * 设置执行前视图
     *
     * @param beforeView 执行前视图
     */
    public void setBeforeView(View beforeView) {
        this.beforeView = beforeView;
    }

    /**
     * 获取执行后视图
     *
     * @return 执行后视图
     */
    public View getAfterView() {
        return afterView;
    }

    /**
     * 设置执行后视图
     *
     * @param afterView 执行后视图
     */
    public void setAfterView(View afterView) {
        this.afterView = afterView;
    }

    // ============ 结果数据相关方法 ============

    /**
     * 获取结果数据
     *
     * @return 结果数据数组
     */
    public Object[] getResultData() {
        return resultData;
    }

    /**
     * 设置结果数据
     *
     * @param resultData 结果数据数组
     */
    public void setResultData(Object[] resultData) {
        this.resultData = resultData;
    }

    /**
     * 获取是否阻塞执行
     *
     * @return 是否阻塞
     */
    public boolean isBlockedExecution() {
        return blockedExecution;
    }

    /**
     * 设置是否阻塞执行
     *
     * @param blockedExecution 是否阻塞
     */
    public void setBlockedExecution(boolean blockedExecution) {
        this.blockedExecution = blockedExecution;
    }

    // ============ 涉及行ID相关方法 ============

    /**
     * 获取涉及的主键值集合
     *
     * @return 行ID数组
     */
    public int[] getInvolvedRowIds() {
        return involvedRowIds;
    }

    /**
     * 设置涉及的主键值集合
     *
     * @param involvedRowIds 行ID数组
     */
    public void setInvolvedRowIds(int[] involvedRowIds) {
        this.involvedRowIds = involvedRowIds;
    }

    /**
     * 判断语句是否是写操作
     *
     * @return 是否是写操作
     */
    public boolean isWriteType() {
        return this.type == FucciStatementType.UPDATE
                || this.type == FucciStatementType.DELETE
                || this.type == FucciStatementType.INSERT
                || this.type == FucciStatementType.REPLACE;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("FucciTxStatement{id=%s, type=%s}\n", getStmtId(), type));
        sb.append("  SQL: ").append(txQueryAdapter.getQueryString()).append("\n");
        if (predicate != null) {
            sb.append("  Predicate: ").append(predicate).append("\n");
        }
        if (beforeView != null) {
            sb.append("  BeforeView: ").append(beforeView.toSimpleString()).append("\n");
        }
        if (afterView != null) {
            sb.append("  AfterView: ").append(afterView.toSimpleString()).append("\n");
        }
        return sb.toString();
    }
}