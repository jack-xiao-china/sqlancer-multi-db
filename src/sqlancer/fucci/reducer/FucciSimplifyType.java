package sqlancer.fucci.reducer;

/**
 * 简化类型枚举。
 * 定义语句简化层可执行的操作类型。
 */
public enum FucciSimplifyType {

    /** 删除WHERE表达式 */
    DEL_WHERE_EXPR,

    /** 删除INSERT列 */
    DEL_INSERT_COL,

    /** 删除UPDATE列 */
    DEL_UPDATE_COL,

    /** 简化表定义 */
    SIMPLIFY_TABLE,

    /** 删除SELECT列 */
    DEL_SELECT_COL,

    /** 删除ORDER BY */
    DEL_ORDER_BY,

    /** 删除LIMIT */
    DEL_LIMIT,

    /** 简化JOIN条件 */
    SIMPLIFY_JOIN
}