# Fucci Oracle 缺陷修复方案

> 基于 `docs/fucci-oracle-deep-audit.md` 审计结果，按优先级实施 5 个阶段的修复
> 实施状态：✅ 全部完成（v2.2.0）

## 背景与动机

SQLancer 的 Fucci Oracle 存在 3 项严重缺陷和 4 项重要缺陷：
- 🔴 CS Oracle 完全未实现（parseExpression 返回原始字符串）
- 🔴 DT Oracle 完全未实现（executeOnRefDatabase 返回空对象）
- 🔴 MVCC 模拟与执行器脱节（只能比较最终状态，无法做语句级预测）
- 🟡 锁分析器空实现（仅 log 输出）
- 🟡 调度生成缺少穷举与过滤
- 🟡 冲突构造策略退化为 adapter stub
- 🟡 Reducer L2-4 均为 stub

核心问题：所有基础设施（Lock/LockObject/MVCCSimulator/ScheduleExhaustiveEnumerator/TxConflictConstructor）已完整实现，但未被正确连接。

## 方案概述

### Phase 4（最先执行）：冲突构造集成 — 修复 P2

**问题**：`FucciTxTestGenerator` 的冲突构造委托给 adapter stub，未使用已有的 `TxConflictConstructor`。

**修改文件**：
- `FucciTxTestGenerator.java`：`generateTransactions()` 末尾调用 `TxConflictConstructor.makeConflict()`；删除 5 个 adapter 委托方法
- `FucciTxStatement.java`：`setStatementType()` 末尾调用 `extractInvolvedRowIds()` 从 WHERE 子句提取行 ID

### Phase 1：语句级锁分析 — 修复 P0

**问题**：`FucciTxTestExecutor.FucciLockAnalyzer` 内部类是空壳。

**修改文件**：
- `TxTestExecutor.java`：新增 `postStatementComplete(TxStatement)` hook（默认 no-op），在语句执行完毕后调用
- 新建 `fucci/lock/FucciLockAnalyzer.java`（顶层类）：
  - `inferLockType()` — SELECT→NONE, SELECT_FOR_SHARE→SHARED, 写操作→EXCLUSIVE
  - `inferLockObject()` — 从 SQL 提取表名 + involvedRowIds
  - `checkConflict()` — 遍历其他事务锁列表，调用 `Lock.isCompatibleWith()`
  - `analyzeAndAcquire()` — 综合分析+获取/阻塞
  - `releaseLocks()` / `processBlockedStatements()` — COMMIT/ROLLBACK 处理
- `FucciTxTestExecutor.java`：删除 stub 内部类，重写 `postStatementComplete()` 驱动锁分析

### Phase 2：语句级 MVCC 视图构建 — 修复 P0

**问题**：MVCCSimulator 仅在 FucciMTOracle 中计算最终状态，无法做语句级预测。

**修改文件**：
- 新建 `fucci/mvcc/FucciMVCCAnalyzer.java`：
  - `processStatement()` 根据类型分发：SELECT→buildView, WRITE→addVersion, COMMIT/ROLLBACK→状态更新
  - RR 隔离首次 SELECT 创建快照
- `FucciMTOracle.java`：重写 `simulateMVCCExecution()` 使用 `FucciMVCCAnalyzer` 逐语句模拟
- `FucciTxTestExecutor.java`：`postStatementComplete()` 同时调用 `mvccAnalyzer.processStatement()`

### Phase 3：调度质量提升 — 修复 P1

**问题**：`genSchedules()` 使用随机循环+去重，产生大量无效调度。

**修改文件**：
- `FucciTxTestGenerator.java`：
  - `genSchedules()` 改用 `ScheduleExhaustiveEnumerator.hybridGenerate()`
  - 新增 `filterNonInterleaved()` — 排除串行调度
  - 新增 `filterAnomalousHistories()` — 数据操作必须出现在两个 COMMIT 之前

### Phase 5：Reducer L2 语句简化 — 修复 P4

**问题**：`tryDeleteWhereClause()`、`tryDeleteOrderBy()`、`tryDeleteLimit()` 均为 stub。

**修改文件**：
- `FucciReducer.java`：
  - `removeWhereClause()` — 定位 WHERE 关键字，保留 ORDER BY/LIMIT/FOR 后缀
  - `removeOrderByClause()` — 定位 ORDER BY 关键字，保留 LIMIT/FOR 后缀
  - `removeLimitClause()` — 定位 LIMIT 关键字

## 风险评估

| 风险 | 等级 | 缓解措施 |
|------|:----:|----------|
| `postStatementComplete()` hook 影响其他 TxTestExecutor 子类 | 低 | 默认 no-op，仅 Fucci 子类重写 |
| 锁分析误判导致误报 | 中 | 保守策略：仅标记明确冲突，不确定时放行 |
| MVCC 模拟版本链与实际 DB 状态不一致 | 中 | 每次 schedule 执行后 resetVersionChains |
| 调度过滤过度导致遗漏 Bug | 低 | 过滤后为空时回退到原始调度集 |
| Reducer SQL 修改导致语法错误 | 低 | Reducer 验证 bug 是否仍可复现 |

## 验收标准

1. ✅ `mvn compile` 零错误零警告
2. ✅ 现有 12 个单元测试全部通过
3. ✅ 新增文件：FucciLockAnalyzer.java, FucciMVCCAnalyzer.java
4. ✅ 修改文件：TxTestExecutor.java, FucciTxTestExecutor.java, FucciMTOracle.java, FucciTxTestGenerator.java, FucciTxStatement.java, FucciReducer.java
5. ✅ pom.xml 版本更新为 2.2.0
6. ✅ release_notes.md 更新 v2.2.0 条目
