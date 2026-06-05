# SQLancer Fucci Oracle 深度审计报告

> 基于 Fucci 论文 (ISSTA 2024) 与原生工具 (Fucci-main) 的对照审计  
> 审计范围：架构设计、核心算法、校验方法  
> 审计日期：2026-06-04

---

## 一、结论概述

**SQLancer 的 Fucci Oracle 存在 6 项架构级缺陷和 4 项算法级偏差，其中 3 项为严重问题，直接影响 Bug 检测能力。**

| 严重度 | 数量 | 关键项 |
|:------:|:----:|--------|
| 🔴 严重 | 3 | CS Oracle 完全未实现、DT Oracle 完全未实现、MVCC 模拟与执行器脱节 |
| 🟡 重要 | 4 | 调度生成缺少穷举+过滤、冲突构造策略退化为随机、锁分析器空实现、Reducer 仅 Level 1 生效 |
| 🟢 建议 | 3 | 缺少 blocked 语句重处理、缺少异常历史过滤、缺少 INSERT 冲突构造 |

---

## 二、原生 Fucci 架构全景

```
┌─────────────────────────────────────────────────────────┐
│                    Fucci 主循环                          │
│                                                         │
│  ┌─────────┐   ┌────────────┐   ┌──────────────────┐   │
│  │ Table   │──▶│ Transaction│──▶│ makeConflict()   │   │
│  │ 生成    │   │ 生成 (×2)  │   │ 4 种冲突策略      │   │
│  └─────────┘   └────────────┘   └────────┬─────────┘   │
│                                           │             │
│  ┌────────────────────────────────────────▼──────────┐  │
│  │           ShuffleTool.genAllSubmittedTrace()      │  │
│  │  穷举所有交错调度 + 异常历史过滤 + 去重            │  │
│  └────────────────────────┬──────────────────────────┘  │
│                           │                             │
│  ┌────────────────────────▼──────────────────────────┐  │
│  │           TxnPairExecutor.execute()               │  │
│  │  在目标 DB 上执行交错调度                         │  │
│  └────────────────────────┬──────────────────────────┘  │
│                           │                             │
│  ┌────────────────────────▼──────────────────────────┐  │
│  │            Oracle 校验 (三选一)                    │  │
│  │  ┌────────┐  ┌────────┐  ┌────────────────────┐   │  │
│  │  │   DT   │  │   MT   │  │        CS          │   │  │
│  │  │差异测试│  │变形测试│  │ 约束求解(MVCC推理) │   │  │
│  │  │参考DBMS│  │MVCC模拟│  │ WHERE AST 求值      │   │  │
│  │  └────┬───┘  └────┬───┘  └────────┬───────────┘   │  │
│  │       └───────────┼───────────────┘                │  │
│  │                   ▼                                │  │
│  │         compareOracles() 逐语句比对                │  │
│  │  6种Bug类型: MISSING_ABORT / UNNECESSARY_ABORT /   │  │
│  │  INCONSISTENT_RESULT / MISSING_LOCK /              │  │
│  │  UNNECESSARY_LOCK / INCONSISTENT_FINAL_STATE       │  │
│  └────────────────────────┬──────────────────────────┘  │
│                           │                             │
│  ┌────────────────────────▼──────────────────────────┐  │
│  │            Reducer 4 级渐进缩减                    │  │
│  │  L1: 语句删除 → L2: 语句简化 → L3: 表达式简化     │  │
│  │  → L4: 常量化简 (3种选择策略)                      │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### Fucci 核心算法详解

#### 2.1 MVCC 推理引擎 (`FucciChecker.inferOracleMVCC`)

这是 Fucci 的核心——一个**完整的 MVCC 模拟器**，可以预测任意调度下的预期执行结果：

```
对于 schedule 中的每条语句 stmt:
  1. 若 curTx 被阻塞 → 加入 blockedStatements 队列
  2. 否则执行 analyzeStmt():
     a. 锁冲突检测: Lock.isConflict(otherTx) → 阻塞
     b. 快照点判定: MySQL=首次SELECT, TiDB=BEGIN
     c. 可见性视图构建:
        - RU → newestView() (含未提交)
        - RC → buildTxView() (仅已提交)
        - RR → snapshotView() (首次读快照)
        - SER → buildTxView() + SELECT加共享锁
     d. SELECT → queryOnView(stmt, view) 在视图上执行查询
     e. UPDATE/INSERT/DELETE → updateVersion() 更新版本链
  3. COMMIT/ROLLBACK → 释放被阻塞事务，重处理其 blockedStatements
  4. 双阻塞检测 → deadlock = true
```

**关键特征**：这是一个**语句级的模拟器**，不仅预测最终状态，还预测每条 SELECT 的返回结果、每条语句是否被阻塞/中止。

#### 2.2 锁冲突检测 (`Lock.isConflict`)

```
对 otherTx 持有的每个锁 otherLock:
  - 排他锁冲突: (this.type == EXCLUSIVE || other.type == EXCLUSIVE)
    && (rowIds 交集非空 || indexes 交集非空)
  - 范围锁冲突 (RR/SER):
    isRangeConflict():
      1. takeSnapshotForTable() — 快照当前表
      2. viewToTable(affectedStmt.view) — 构建 affectedStmt 的可见视图
      3. getLockObject(affectedStmt) — 获取 affectedStmt 的锁定对象 (before)
      4. executeOnTable(stmt.statement) — 执行当前语句
      5. getLockObject(affectedStmt) — 获取 affectedStmt 的锁定对象 (after)
      6. recoverTableFromSnapshot() — 恢复表
      7. 比较 before/after 的 rowIds 和 indexes → 变化则冲突
```

**关键特征**：这是一种**基于快照-执行-比较的精确范围锁冲突检测**，而非保守启发式。

#### 2.3 调度穷举 + 过滤 (`ShuffleTool`)

```
1. 递归穷举: shuffle() 生成 C(n1+n2, n1) 种交错排列
2. 过滤非交错: 排除 tx1 全在 tx2 前/后的退化情况
3. 异常历史过滤: containAnomalousHistory()
   - 要求冲突语句出现在两个 COMMIT 之前
   - max(tx1ConflictIdx, tx2ConflictIdx) < min(tx1CommitIdx, tx2CommitIdx)
4. BEGIN 归一化: 确保 tx1.BEGIN 在 tx2.BEGIN 之前 (去重)
5. 去重: HashSet<String> 消除等价调度
6. 蓄水池采样: 若总数 > sampleCount，使用 reservoir sampling
```

#### 2.4 冲突构造 (`TableTool.makeConflict`)

4 种策略：

| 策略 | 描述 | 效果 |
|------|------|------|
| `fully-shared-filters` | s2.where = s1.where | 两个事务操作完全相同的行 |
| `partially-shared-filters` | s2.where = s1.where OR s2.where | 操作行部分重叠 |
| `conflict-tuple-containment` | s1.chooseRow(r), s2.chooseRow(r) | 强制操作同一行 |
| `random` | 随机选择上述策略 | — |

另有**INSERT 冲突构造**：使两个 INSERT 触发唯一索引冲突。

#### 2.5 约束求解 (`queryOnViewByCostraintSolver`)

Fucci 的 CS Oracle 不是使用 SMT solver，而是在**内存视图上直接求值 WHERE 表达式**：

```
对于 view 中的每一行:
  1. 构建 tupleMap: columnName → columnValue
  2. 调用 stmt.predicate.getExpectedValue(tupleMap) — AST 求值
  3. 若结果为 true → 该行应出现在 SELECT 结果中
  4. 比较预测结果与实际 SELECT 结果
```

这需要**完整的 SQL 表达式 AST**（MySQL 的二元比较、逻辑运算、BETWEEN、IN、CAST 等），Fucci 使用 ANTLR 生成的 `MySQLExpression.g4` 解析 WHERE 子句。

#### 2.6 测试用例缩减 (`Reducer`)

4 级渐进缩减，每级使用 3 种选择策略之一：

| 级别 | 操作 | 示例 |
|------|------|------|
| L1: 语句删除 | 删除非必要语句 | 删除不影响 Bug 复现的 SELECT |
| L2: 语句简化 | 简化语句结构 | 删除 WHERE、删除列、删除 ORDER BY |
| L3: 表达式简化 | 简化 WHERE 表达式 | `a > 5 AND b < 10` → `1 AND b < 10` |
| L4: 常量化简 | 将常量替换为小值 | `a > 12345` → `a > 3` |

---

## 三、SQLancer Fucci 实现状态审计

### 3.1 实现状态矩阵

| 组件 | 文件 | 行数 | 原生 Fucci | SQLancer | 状态 |
|------|------|:----:|:----------:|:--------:|:----:|
| **CS Oracle** | FucciCSOracle.java | 173 | 1066 行核心 | `parseExpression()` 返回输入 | 🔴 空壳 |
| **DT Oracle** | FucciDTOracle.java | 121 | TxnPairExecutor 412 行 | `executeOnRefDatabase()` 返回空对象 | 🔴 空壳 |
| **MT Oracle** | FucciMTOracle.java | 181 | 同上 | MVCCSimulator 已实现 | 🟢 可用 |
| **Composite Oracle** | FucciCompositeOracle.java | 137 | — | 编排框架正常 | 🟢 可用 |
| **MVCC 模拟器** | MVCCSimulator.java | 250 | FucciChecker 内置 | 独立模块，逻辑正确 | 🟢 可用 |
| **锁分析器** | FucciTxTestExecutor 内部类 | 15 行 | Lock.java 95 行 | 仅 log 输出 | 🔴 空壳 |
| **调度生成** | FucciTxTestGenerator.java | 258 | ShuffleTool 完整穷举 | 随机交错，无穷举/过滤 | 🟡 简化 |
| **冲突构造** | FucciTxTestGenerator.java | + adapter | 4 种策略 | 3 种策略全委托 adapter stub | 🟡 简化 |
| **Reducer L1** | FucciReducer.java | 500 | Reducer.java 500+ | 语句删除已实现 | 🟢 可用 |
| **Reducer L2-4** | FucciReducer.java | (同上) | 完整实现 | 全部返回 false / pass-through | 🟡 部分 |
| **可见性规则** | VisibilityRule.java | 48 | FucciChecker 内置 | 枚举映射正确 | 🟢 可用 |
| **Bug 分类** | TxBugType + TxDiscrepancyClassifier | ~180 | compareOracles | 6 种 Bug + 3 种 Undecided | 🟢 可用 |

### 3.2 各组件详细审计

#### 🔴 CS Oracle — 完全未实现

```java
// FucciCSOracle.java:168-172 — 约束求解器是空壳
private static class FucciConstraintSolver {
    public Object parseExpression(String expression) {
        return expression;  // 直接返回输入字符串，无任何解析
    }
}

// FucciCSOracle.java:130-155 — 验证方法永远返回空字符串
private String verifySelectResult(...) {
    constraintSolver.parseExpression(predicate);  // 无实际效果
    // ...
    return "";  // 永远不报告问题
}
```

**影响**：CS Oracle 是 Fucci 论文中**最核心的 Oracle**——通过 MVCC 模拟 + 约束求解预测每条 SELECT 的预期结果。当前实现完全丧失了这一能力。

**原生实现对照**：Fucci 的 `queryOnViewByCostraintSolver()` 使用完整的 MySQL 表达式 AST 遍历，对视图中的每一行逐一求值 WHERE 谓词。这需要 ANTLR 生成的语法解析器和完整的表达式求值器。

#### 🔴 DT Oracle — 完全未实现

```java
// FucciDTOracle.java:96-100 — 参考数据库执行是空壳
private TxTestExecutionResult executeOnRefDatabase(...) {
    logger.writeCurrent("Executing on reference database...");
    return new TxTestExecutionResult();  // 返回空结果
}

// FucciDTOracle.java:26 — 引用连接从未初始化
private SQLConnection refConnection;  // 永远为 null
```

**影响**：DT Oracle 通过在不同 DBMS（如 MySQL vs MariaDB）上执行相同调度来发现行为差异。当前实现完全无法工作。

**原生实现对照**：Fucci 的 DT Oracle 在 `TxnPairExecutor` 中使用 `refConn`（到参考 DBMS 的连接）串行执行相同调度，然后比较两个 DBMS 的执行结果。

#### 🔴 执行器锁分析 — 空实现

```java
// FucciTxTestExecutor.java:184-188 — 锁分析器仅 log
public void analyzeLockConflicts(TxTestExecutionResult result) {
    globalState.getState().getLocalState().log(
        "Lock analysis for " + transactions.size() + " transactions");
}

// FucciTxTestExecutor.java:203-208 — MVCC 验证器仅设置隔离级别
public void verifyMVCC(TxTestExecutionResult result) {
    result.setIsolationLevel(isoLevel);
    globalState.getState().getLocalState().log(
        "MVCC verification for isolation level: " + isoLevel);
}
```

**影响**：Fucci 论文的核心创新之一是 `analyzeStmt()` 中的**语句级锁分析**——对每条语句判断是否应该被阻塞。当前实现完全缺失这一逻辑。

**原生实现对照**：Fucci 的 `analyzeStmt()` 使用 `Lock.isConflict()` 检查当前语句的锁是否与另一事务已持有的锁冲突，包含行锁和范围锁的精确检测。

#### 🟡 调度生成 — 缺少穷举与过滤

**SQLancer 实现**：
```java
// 纯随机交错，无穷举，无过滤
private List<TxStatement> genOneScheduleInternal(List<Transaction> transactions) {
    while (!txs.isEmpty()) {
        Transaction tx = Randomly.fromList(txs);  // 随机选一个事务
        schedule.add(tx.getStatements().get(stmtIndex));  // 取下一条语句
    }
}
```

**原生 Fucci 实现**：
```java
// 穷举所有交错 + 异常历史过滤 + 去重 + 蓄水池采样
ArrayList<ArrayList<StatementCell>> genAllSubmittedTrace(tx1, tx2) {
    shuffle(res, ...);  // 递归穷举 C(n1+n2, n1) 种排列
    res = filterSubmittedOrder(res, tx1, tx2);  // 过滤无效调度
    // 过滤条件:
    //   1. 排除非交错退化情况
    //   2. containAnomalousHistory(): 冲突在 COMMIT 之前
    //   3. BEGIN 归一化去重
}
```

**影响**：
- 缺少**异常历史过滤**：大量调度中冲突语句出现在 COMMIT 之后，不可能触发并发 Bug
- 缺少**穷举保证**：随机采样可能反复生成相同调度，遗漏能触发 Bug 的调度
- 缺少**去重**：`schedules.contains(schedule)` 使用 List.equals，效率低且语义不一定正确

#### 🟡 冲突构造 — 策略退化为随机

```java
// FucciTxTestGenerator.java:116-131 — 三种策略全委托 adapter
private void generateStructuredConflictStatements(FucciTransaction tx) {
    switch (conflictType) {
        case "fully-shared":
            adapter.generateConflictStatements(globalState, tx, "fully-shared");
            break;  // adapter 内部实现状态未知
    }
}
```

**原生 Fucci 的冲突构造**直接在 `TableTool.makeConflict()` 中操作 `StatementCell` 的 `whereClause` 和 `predicate` 字段，确保两个事务的语句共享操作目标。SQLancer 虽然有通用的 `TxConflictConstructor`（v2.1.0 新增），但 Fucci 模块内部的冲突构造路径并未使用它。

---

## 四、核心算法偏差分析

### 4.1 MVCC 模拟与执行器脱节

**Fucci 原生**：MVCC 模拟 (`inferOracleMVCC`) 和执行 (`TxnPairExecutor`) 是**紧密耦合**的：
- `analyzeStmt()` 同时做锁分析 + 版本链更新 + 视图构建 + SELECT 求值
- 每条语句处理后版本链立即更新
- 阻塞/提交事件触发 blockedStatements 的重处理

**SQLancer Fucci**：MVCC 模拟和执行是**分离**的：
- `FucciTxTestExecutor.execute()` 调用 `super.execute()` 在真实 DB 上执行
- `MVCCSimulator` 是独立模块，仅在 `FucciMTOracle` 中被调用
- `FucciMVCCSimulator` (执行器内部类) 是空壳，不参与语句级分析
- 版本链仅在 `initializeVersionChains()` 时从数据库读取初始状态

**后果**：SQLancer 的 MT Oracle 只能在**最终状态层面**比较（执行结果 vs 模拟结果），无法像原生 Fucci 那样在**语句级**预测每条 SELECT 的返回结果和每条语句的阻塞行为。

### 4.2 缺少 Blocked 语句重处理

**Fucci 原生**：
```java
if (stmt.type == COMMIT || stmt.type == ROLLBACK) {
    otherTx.blocked = false;
    for (StatementCell blockedStmt : otherTx.blockedStatements) {
        analyzeStmt(blockedStmt, otherTx, curTx);  // 重处理被阻塞语句
        oracleOrder.add(blockedStmt);
    }
}
```

**SQLancer Fucci**：无此机制。被阻塞的语句被简单标记为 blocked，不会在锁释放后重新分析。

### 4.3 缺少范围锁精确检测

**Fucci 原生**的 `isRangeConflict()` 使用**快照-执行-比较**方法精确检测范围锁冲突：
1. 快照当前表状态
2. 在视图上执行写语句
3. 比较受影响行/索引的变化
4. 恢复快照

**SQLancer** 的 `InnoDBRangeConflictDetector` 使用**保守启发式**：同表 + 有范围锁 = 冲突。这会产生大量误报（false positive），导致 Undecided 判定增多。

### 4.4 缺少 INSERT 冲突构造

**Fucci 原生**专门处理 INSERT 冲突：使两个 INSERT 触发唯一索引冲突，这是检测死锁和异常中止的重要场景。

**SQLancer** 的 `TxConflictConstructor` 仅处理 UPDATE/DELETE/SELECT 的 WHERE 条件共享，不支持 INSERT 冲突。

---

## 五、校验方法对比

### 5.1 比较策略差异

| 维度 | Fucci 原生 `compareOracles()` | SQLancer Fucci |
|------|-------------------------------|----------------|
| **粒度** | 逐语句比较 (statement-by-statement) | 最终状态比较 (final state) |
| **SELECT 结果** | 比较每条 SELECT 的返回行 | 仅 SER 级别比较所有结果 |
| **阻塞行为** | 比较每条语句是否被阻塞 | 不比较阻塞行为 |
| **中止行为** | 比较每条语句是否被中止 | 不比较中止行为 |
| **最终状态** | 比较数据库最终内容 | 比较数据库最终内容 |
| **死锁** | 比较是否发生死锁 | TxDiscrepancyClassifier 处理 |
| **Undecided 处理** | 3 种 undecided 分类 | 3 种 undecided 分类 (一致) |

**关键差距**：逐语句比较是 Fucci 的核心优势。它能发现：
- 某条 SELECT 在 RR 隔离级别下第二次读取结果不同（不可重复读）
- 某条 UPDATE 应该被阻塞但实际未被阻塞（缺失锁）
- 某条语句应该被中止但实际未被中止（缺失中止）

SQLancer 的最终状态比较只能发现：
- 数据库最终内容不一致（但无法定位是哪条语句导致）

### 5.2 Bug 检测能力矩阵

| Bug 类型 | Fucci 原生 | SQLancer Fucci | SQLancer TX_INFER |
|----------|:----------:|:--------------:|:-----------------:|
| 不可重复读 | ✅ 语句级 | ❌ | ✅ 辅助版本表 |
| 丢失更新 | ✅ 最终状态 | ✅ 最终状态 | ✅ 辅助版本表 |
| 幻读 | ✅ 范围锁 + 语句级 | ⚠️ 仅最终状态 | ✅ 范围冲突检测 |
| 缺失锁 | ✅ 阻塞比较 | ❌ | ⚠️ 保守启发式 |
| 不必要锁 | ✅ 阻塞比较 | ❌ | ❌ |
| 缺失中止 | ✅ 中止比较 | ❌ | ❌ |
| 不必要中止 | ✅ 中止比较 | ⚠️ TxDiscrepancyClassifier | ⚠️ 同 |
| 死锁缺陷 | ✅ 死锁比较 | ⚠️ TxDiscrepancyClassifier | ⚠️ 同 |
| 最终状态不一致 | ✅ | ✅ | ✅ |

---

## 六、业内经验与建议

### 6.1 优先级排序

基于 Fucci 论文的实验数据和业内实践经验，建议按以下优先级修复：

| 优先级 | 改进项 | 预期收益 | 工作量 |
|:------:|--------|----------|:------:|
| **P0** | 修复 MVCC 模拟与执行器的集成 | 实现语句级校验 | 大 |
| **P1** | 实现调度穷举 + 异常历史过滤 | 提升调度质量 3-5x | 中 |
| **P2** | 实现 CS Oracle 的表达式求值 | 新增核心检测能力 | 大 |
| **P3** | 实现锁分析器 | 检测缺失锁/不必要锁 | 中 |
| **P4** | 补齐 Reducer L2-4 | 提升 Bug 报告可用性 | 中 |
| **P5** | 实现 DT Oracle | 跨 DBMS 对比检测 | 中 |

### 6.2 具体建议

#### P0: 语句级 MVCC 模拟集成

**问题**：MVCCSimulator 是独立模块，无法在语句执行过程中实时更新版本链和构建视图。

**建议**：参照 Fucci 原生 `inferOracleMVCC()` 的设计，将 MVCC 模拟嵌入执行流程：

```
原架构:
  Executor.execute() → 真实DB执行 → MT Oracle.compare(finalState)
  
目标架构:
  Executor.execute() → 真实DB执行
     ↓ (每条语句)
  FucciAnalyzer.analyzeStmt() → 锁检测 + 版本链更新 + 视图构建 + SELECT求值
     ↓
  compareOracles() → 逐语句比较(blocked/aborted/result)
```

核心实现点：
1. `FucciAnalyzer.analyzeStmt(stmt, curTx, otherTx)` — 对每条语句执行锁冲突检测
2. 版本链在每条写语句后实时更新 (`addVersion()`)
3. SELECT 语句在构建的视图上求值，保存预测结果
4. COMMIT/ROLLBACK 触发 blockedStatements 重处理

#### P1: 调度穷举 + 异常历史过滤

**建议**：将 `ScheduleExhaustiveEnumerator`（v2.1.0 已有）集成到 Fucci 调度生成中，并增加 Fucci 原生的过滤逻辑：

```java
// 1. 穷举所有交错
List<List<TxStatement>> allSchedules = ScheduleExhaustiveEnumerator.enumerateAll(tx1Stmts, tx2Stmts);

// 2. 异常历史过滤 (Fucci 论文核心贡献)
allSchedules = allSchedules.stream()
    .filter(s -> containAnomalousHistory(s, tx1, tx2))  // 冲突在 COMMIT 前
    .filter(s -> !isNonInterleaved(s, tx1, tx2))        // 排除退化调度
    .collect(toList());

// 3. 蓄水池采样
return ScheduleExhaustiveEnumerator.reservoirSample(allSchedules, sampleSize);
```

#### P2: CS Oracle 表达式求值

**建议方案**（按成本递增）：

**方案 A（推荐）**：利用 SQLancer 已有的表达式生成器
- SQLancer 每个 DBMS 模块都有完整的表达式 AST（如 `MySQLExpression`, `PostgresExpression`）
- 在生成 SELECT 语句时保留 AST 引用（`FucciTxStatement.predicate`）
- 实现 `evaluateOnView(Expression, View)` 遍历视图行并求值

**方案 B**：复用 JSQLParser
- 项目已依赖 `com.github.jsqlparser:jsqlparser:4.6`
- 解析 WHERE 子句为 JSQLParser AST
- 实现简单的表达式求值器

#### P3: 锁分析器

**建议**：参照 Fucci 原生 `Lock.isConflict()` 实现行级锁冲突检测：

```java
public boolean isConflict(FucciTransaction otherTx) {
    for (Lock otherLock : otherTx.getLocks()) {
        if (this.type == EXCLUSIVE || otherLock.type == EXCLUSIVE) {
            if (rowIdsIntersect(this, otherLock) || indexesIntersect(this, otherLock)) {
                return true;
            }
        }
        if (useRangeLock() && isRangeConflict(otherLock)) {
            return true;
        }
    }
    return false;
}
```

范围锁冲突可继续使用 `InnoDBRangeConflictDetector` 的保守启发式，或实现 Fucci 的快照-执行-比较方法以获得更高精度。

#### P4: Reducer L2-4

**建议**：逐步实现各级简化：
- L2 语句简化：删除 WHERE → 删除 ORDER BY → 删除 LIMIT → 删除部分列
- L3 表达式简化：遍历 WHERE AST，将子表达式替换为 `TRUE`/`FALSE`
- L4 常量化简：将常量替换为 `[-10, 10]` 范围内的小整数

每级简化后重跑 Oracle 验证 Bug 是否仍可复现。

### 6.3 架构改进建议

#### 建议 A: 统一 Fucci 与 TX_INFER 的 MVCC 建模

当前 SQLancer 有两套独立的 MVCC 建模：
- **TX_INFER**：基于辅助版本表 (`_vt`)，在真实 DB 上执行推理
- **Fucci MT Oracle**：基于内存 `MVCCSimulator`，纯软件模拟

建议统一为一个 `MVCCModel` 接口：
```java
interface MVCCModel {
    void processStatement(TxStatement stmt, Transaction curTx);
    View buildView(Transaction tx, VisibilityRule rule);
    Map<String, List<Object>> computeFinalState();
}
```

TX_INFER 和 Fucci MT 分别实现此接口，共享可见性规则和比较逻辑。

#### 建议 B: 引入 Fuzzing 覆盖率指标

Fucci 论文的实验部分使用了以下指标：
- **冲突覆盖率**：构造的冲突类型占比
- **调度多样性**：不同交错模式的数量
- **隔离级别均衡性**：各隔离级别被测试的比例

建议在 FucciOptions 中增加覆盖率统计，帮助调优 fuzzing 参数。

---

## 七、总结

SQLancer 的 Fucci Oracle 当前**仅 MT Oracle 可用**，且只能做最终状态比较，丧失了原生 Fucci 最核心的**语句级校验**能力。CS Oracle 和 DT Oracle 均为空壳。调度生成使用纯随机交错，缺少异常历史过滤。锁分析器和 Reducer L2-4 未实现。

**最有价值的改进方向**是将 MVCC 模拟嵌入执行流程（P0），实现逐语句的结果预测和比较。这将使 Fucci MT Oracle 的检测能力从"最终状态不一致"扩展到"不可重复读"、"缺失锁"、"缺失中止"等更多 Bug 类型。
