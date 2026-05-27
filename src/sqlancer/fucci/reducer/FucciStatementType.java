package sqlancer.fucci.reducer;

/**
 * 语句类型枚举。
 * 定义语句删除层可操作的语句类型。
 */
public enum FucciStatementType {

    /** SELECT查询 */
    SELECT,

    /** SELECT FOR UPDATE */
    SELECT_FOR_UPDATE,

    /** SELECT FOR SHARE */
    SELECT_FOR_SHARE,

    /** UPDATE更新 */
    UPDATE,

    /** INSERT插入 */
    INSERT,

    /** DELETE删除 */
    DELETE,

    /** CREATE INDEX */
    CREATE_INDEX,

    /** DROP INDEX */
    DROP_INDEX,

    /** ALTER TABLE */
    ALTER_TABLE,

    /** BEGIN */
    BEGIN,

    /** COMMIT */
    COMMIT,

    /** ROLLBACK */
    ROLLBACK
}