-- GaussDB-M SERIALIZABLE 隔离级别问题最小复现用例
-- Bug: 在 SERIALIZABLE 隔离级别下，并发事务执行后数据库状态不一致

-- ===== 测试环境 =====
-- 数据库: GaussDB-M (DBCOMPATIBILITY 'M')
-- 隔离级别: SERIALIZABLE
-- 表结构: t0(c0 FLOAT)

-- ===== 初始数据准备 =====
CREATE TABLE t0(c0 FLOAT);
INSERT INTO t0 VALUES (99.77), (57.3), (-13.34), (-2.66), (93.8), (97.26), (11.95);
-- 初始状态: 7 rows

-- ===== 事务定义 =====
-- Transaction 1 (Tx1): DELETE + UPDATE + SELECT + ROLLBACK
-- Transaction 2 (Tx2): INSERT + ROLLBACK
-- Transaction 3 (Tx3): INSERT + COMMIT

-- ===== 执行调度 =====
-- Schedule: [Tx2-BEGIN, Tx2-INSERT, Tx2-ROLLBACK,
--            Tx3-BEGIN, Tx3-INSERT, Tx3-COMMIT,
--            Tx1-BEGIN, Tx1-DELETE, Tx1-UPDATE, Tx1-SELECT, Tx1-ROLLBACK]

-- ===== 预期结果 =====
-- 正确行为: 最终数据库应只有 8 rows (原始 7 rows + Tx3 插入 1 row)
-- Tx1 的 DELETE 和 UPDATE 应被 ROLLBACK 撤销
-- Tx2 的 INSERT 应被 ROLLBACK 撤销
-- Tx3 的 INSERT 应保留 (COMMIT)

-- ===== 实际观察到的 Bug =====
-- Test Execution: 8 rows (正确)
-- Oracle Re-execution: 16 rows (数据重复!)

-- ===== 复现步骤 =====
-- Step 1: 启动三个并发事务会话

-- Session 1 (Tx1):
SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;
BEGIN;
DELETE FROM t0;                    -- 删除所有行
UPDATE t0 SET c0 = -91.80;         -- 更新 (此时表已空)
SELECT DISTINCT STD(c0) FROM t0 LOCK IN SHARE MODE;  -- 查询
ROLLBACK;                          -- 回滚所有操作

-- Session 2 (Tx2):
SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;
BEGIN;
INSERT INTO t0(c0) VALUES (89.78);
ROLLBACK;

-- Session 3 (Tx3):
SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;
BEGIN;
INSERT INTO t0(c0) VALUES (-76.78);
COMMIT;

-- Step 2: 按调度顺序执行 (串行模拟):
-- 2.1: Tx2 BEGIN -> INSERT -> ROLLBACK (无效果)
-- 2.2: Tx3 BEGIN -> INSERT -> COMMIT (插入 -76.78)
-- 2.3: Tx1 BEGIN -> DELETE (删除全部 8 rows) -> UPDATE -> SELECT -> ROLLBACK

-- Step 3: 检查最终状态
SELECT * FROM t0 ORDER BY c0;
-- 预期: 7 original rows + 1 new row = 8 rows
-- Bug: 可能观察到 16 rows (数据重复) 或其他不一致状态

-- ===== 问题分析 =====
-- 1. 在 SERIALIZABLE 隔离级别下，Tx1 的 DELETE 在执行时看到表中有 8 rows
--    (包括 Tx3 插入的行)，DELETE 成功删除全部
-- 2. Tx1 ROLLBACK 后，理论上应恢复到 DELETE 前的状态 (8 rows)
-- 3. 但在 Oracle 重执行时，事务调度执行顺序可能不同
-- 4. 当 Tx1 再次执行时，DELETE 看到的表状态可能不同
-- 5. 导致最终数据库状态不一致

-- ===== 根因 =====
-- WriteCheck Oracle 的设计问题:
-- 1. reproduceDatabase() 在每次事务执行后重建数据库
-- 2. Oracle 调度执行 Tx1 时，数据库处于初始状态 (只有 7 rows)
-- 3. Tx1 DELETE 删除了 7 rows，ROLLBACK 后恢复为 7 rows
-- 4. 然后 Tx3 INSERT 添加 1 row，变成 8 rows
-- 5. 但在某些情况下，事务重新执行导致数据重复

-- ===== SQLancer 命令 =====
-- java -jar sqlancer.jar --username=xxx --password=xxx --host=xxx --port=19995
--      gaussdb-m --oracle=WRITE_CHECK --num-threads=1 --log-each-select=true

-- ===== 验证方法 =====
-- 1. 运行上述 SQLancer 命令
-- 2. 观察日志中的 "Bug Report" 部分
-- 3. 检查 "DB Final State" 是否一致
-- 4. 对比 Execution Result 和 Oracle Result 的行数