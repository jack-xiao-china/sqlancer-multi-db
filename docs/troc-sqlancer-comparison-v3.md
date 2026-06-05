# Troc vs SQLancer 事务检测 Oracle 深度对比报告 v3

> 基于 Troc 源码 (Troc-main) 与 SQLancer v2.2.0 最新代码的全方位对比
> 审计日期：2026-06-05

---

## 一、结论概述

SQLancer v2.2.0 的 Fucci Oracle 经过两轮优化后，在**调度质量**和**冲突构造**方面已超越 Troc，但在**核心检测算法精度**上仍存在 3 项关键差距。同时，TX_INFER 的辅助版本表方案在**跨 DBMS 适应性**上优于 Troc 的纯内存模拟。

| 维度 | Troc | SQLancer v2.2.0 | 优势方 |
|------|------|-----------------|:------:|
| MVCC 语句级模拟 | ✅ 完整 | 🟡 Fucci 可用，TX_INFER 独立 | Troc |
| 范围锁精确检测 | ✅ 快照-执行-比较 | 🟡 保守启发式 | Troc |
| 约束求解/SELECT 求值 | ✅ WHERE AST 求值 | ❌ 未实现 | Troc |
| 调度穷举+过滤 | 🟡 穷举+采样（无过滤） | ✅ 穷举+非交错过滤+异常历史过滤 | SQLancer |
| 冲突构造策略 | 🟡 1 种（共享 WHERE 或同行） | ✅ 2 种（WHERE 复制 + PK 注入） | SQLancer |
| DBMS 覆盖 | MySQL/MariaDB/TiDB | MySQL/PG/GaussDB-A/GaussDB-M | SQLancer |
| 测试用例缩减 | ❌ 无 | 🟡 Reducer L1+L2 部分 | SQLancer |
| Bug 分类精度 | 5 种 Bug + 3 种 Undecided | 6 种 Bug + 3 种 Undecided | SQLancer |
| 多事务支持 | 固定 2 事务 | WriteCheck 1-5 事务, Fucci 固定 2 | SQLancer |

---

## 二、功能实现对比

### 2.1 MVCC 模拟引擎

#### Troc: 统一语句级模拟

Troc 的核心优势是 `TrocChecker.inferOracleMVCC()` — 一个**完整的语句级 MVCC 模拟器**：

```
对 schedule 中每条语句:
  1. 若 curTx 被阻塞 → 加入 blockedStatements
  2. 否则 analyzeStmt():
     a. 构建事务视图 (buildTxView/snapshotView/newView)
     b. Lock.isConflict() → 锁冲突检测
     c. SELECT → queryOnView(stmt, view) 在内存视图上执行查询
     d. UPDATE/INSERT/DELETE → updateVersion() 更新版本链
  3. COMMIT/ROLLBACK → 重处理被阻塞语句
  4. 双阻塞 → 死锁
```

**关键能力**：Troc 能**预测每条 SELECT 的返回结果**。它通过 `queryOnView()` 在内存视图上执行 SQL 查询来实现：

```java
ArrayList<Object> queryOnView(StatementCell stmt, View view) {
    // 遍历视图中的每一行
    for (int rowId : view.data.keySet()) {
        // 构建 tupleMap: columnName → value
        HashMap<String, Object> tupleMap = new HashMap<>();
        // ... 填充列值
        
        // 使用表达式求值器计算 WHERE 条件
        MySQLConstant constant = stmt.predicate.getExpectedValue(tupleMap);
        if (!constant.isNull() && constant.asBooleanNotNull()) {
            res.add(...); // 该行满足条件
        }
    }
    return res;
}
```

这需要**完整的 MySQL 表达式 AST**（ANTLR 生成的 `MySQLExpression.g4`），包括：
- 二元比较操作 (`=`, `<`, `>`, `<=`, `>=`, `<>`)
- 逻辑操作 (`AND`, `OR`, `NOT`)
- `BETWEEN`, `IN`, `IS NULL`, `IS NOT NULL`
- `CAST`, 函数调用
- 字符串和数值常量

#### SQLancer TX_INFER: 辅助版本表方案

TX_INFER 采用完全不同的策略——在真实 DB 上创建辅助版本表 (`_infer_<table>_vt`)：

```sql
CREATE TABLE _infer_t0_vt AS SELECT *, 0 AS rid, 0 AS vid, FALSE AS deleted, 0 AS txid FROM t0
```

**优势**：
- 利用 DB 自身的查询引擎执行 SELECT，无需实现表达式求值器
- 天然支持复杂 SQL（JOIN、子查询、聚合函数等）
- 跨 DBMS 适应性好（只需调整 DDL 语法）

**劣势**：
- 辅助表创建/清理有额外开销
- 版本链更新通过 INSERT/UPDATE 语句，比内存操作慢
- 需要为每个 DBMS 定制 DDL 语法

#### SQLancer Fucci: 内存 MVCC + 锁分析

Fucci v2.2.0 的 `FucciMVCCAnalyzer` + `FucciLockAnalyzer` 已实现语句级处理：

```java
// FucciTxTestExecutor.postStatementComplete()
mvccAnalyzer.processStatement(fucciStmt);  // MVCC 版本链更新
lockAnalyzer.analyzeAndAcquire(tx, fucciStmt);  // 锁冲突检测
```

**但缺失关键能力**：`FucciMVCCAnalyzer.processSelect()` 构建 beforeView 后**没有 queryOnView() 等价物**——它只是将视图整体保存，无法像 Troc 那样在视图上执行 WHERE 谓词求值来预测 SELECT 的精确返回行。

### 2.2 范围锁检测

| 方案 | Troc `isRangeConflict()` | SQLancer `InnoDBRangeConflictDetector` |
|------|--------------------------|---------------------------------------|
| **方法** | 快照-执行-比较 | 保守启发式 |
| **精度** | 高（精确检测行集变化） | 低（同表+范围锁=冲突） |
| **误报** | 少 | 多（可能产生 false positive） |
| **实现** | 需要表快照+执行+恢复 | 仅需标记+检查 |

Troc 的精确方法：
```java
// 1. 快照表
TableTool.takeSnapshotForTable("range_conflict");
// 2. 构建受影响语句的可见视图
TableTool.viewToTable(affectedStmt.view);
// 3. 获取执行前的锁定对象集合
LockObject before = TableTool.getLockObject(affectedStmt);
// 4. 执行当前语句
TableTool.executeOnTable(stmt.statement);
// 5. 获取执行后的锁定对象集合
LockObject after = TableTool.getLockObject(affectedStmt);
// 6. 恢复表
TableTool.recoverTableFromSnapshot("range_conflict");
// 7. 比较前后差异
return setDiffer(before.rowIds, after.rowIds) || setDiffer(before.indexes, after.indexes);
```

SQLancer 的启发式方法：
```java
// 只要目标表有活跃范围锁 → 认为冲突
return rangeLockType.containsKey(otherTx) 
    && lockedTable.equals(tableName) 
    && InnoDBRangeConflictDetector.hasRangeConflict(stmtType, ...);
```

### 2.3 约束求解 (CS Oracle)

| | Troc | SQLancer Fucci CS |
|--|------|-------------------|
| **实现** | ANTLR MySQL 表达式解析 + AST 求值 | 空壳 (parseExpression 返回输入) |
| **支持的表达式** | 比较/逻辑/BETWEEN/IN/CAST/函数 | 无 |
| **求值方式** | `MySQLExpression.getExpectedValue(tupleMap)` | 无 |

Troc 的 CS Oracle 通过 ANTLR 语法文件 `MySQLExpression.g4` 生成完整的表达式解析器，能在内存视图上对每一行计算 WHERE 谓词的布尔值。这是**检测 SELECT 结果不一致的核心能力**。

SQLancer 虽然依赖 jsqlparser (`com.github.jsqlparser:4.6`)，但尚未集成到 CS Oracle 中。

---

## 三、检测逻辑对比

### 3.1 Bug 类型覆盖

| Bug 类型 | Troc | SQLancer WriteCheck | SQLancer TX_INFER | SQLancer Fucci |
|----------|:----:|:-------------------:|:------------------:|:--------------:|
| SELECT 结果不一致 | ✅ 语句级 | ✅ 比较 schedule 变体 | ✅ 语句级 | 🟡 仅最终状态 |
| 缺失中止 | ✅ | ⚠️ 部分 | ✅ | 🟡 仅最终状态 |
| 不必要中止 | ✅ | ⚠️ 部分 | ✅ | 🟡 TxDiscrepancyClassifier |
| 缺失锁 | ✅ | ❌ | ⚠️ 保守启发式 | 🟡 FucciLockAnalyzer |
| 不必要锁 | ✅ | ❌ | ❌ | 🟡 FucciLockAnalyzer |
| 最终状态不一致 | ✅ | ✅ | ✅ | ✅ |
| 死锁缺陷 | ⚠️ Undecided | ⚠️ Undecided | ⚠️ Undecided | ⚠️ Undecided |

### 3.2 比较策略差异

**Troc `compareOracles()` — 逐语句精确比较**：

```
for 每条语句:
  1. 双方均 aborted → 跳过
  2. oracle aborted, 实际未 aborted → "Missing abort" BUG
  3. 实际 aborted, oracle 未 aborted → "Unnecessary abort" 或 Undecided
  4. 双方均未 blocked 且是 SELECT → 比较查询结果 → "Inconsistent query result"
  5. oracle blocked, 实际未 blocked → "Missing lock" BUG
  6. 实际 blocked, oracle 未 blocked → "Unnecessary lock" 或 Undecided
最后: 比较最终数据库状态 → "Inconsistent final state"
```

**SQLancer Fucci MT — 当前仅最终状态比较**：

```
MT Oracle:
  1. 执行器执行 schedule → 获取 testResult
  2. FucciMVCCAnalyzer 模拟 schedule → 获取 simulatedResult
  3. 比较 testResult 和 simulatedResult:
     - SER 级别: compareAllResults() — 逐语句比较
     - 其他级别: compareWriteTxResults() — 仅比较写操作
```

**关键差距**：Fucci MT Oracle 在 SER 级别使用 `compareAllResults()` 逐语句比较，但由于 `FucciMVCCAnalyzer.processSelect()` 没有实现 `queryOnView()`，SELECT 的预测结果 (`stmtResult.setResult(predictedData)`) 只是将 beforeView 的所有行数据扁平化为 List，而非真正执行 WHERE 谓词过滤后的结果。这意味着**即使 SELECT 有 WHERE 条件，预测结果也会包含视图中所有行**，与实际 SELECT 结果无法正确比较。

### 3.3 Undecided 处理

两者在 Undecided 分类上基本一致：

| 场景 | Troc | SQLancer |
|------|------|----------|
| 执行死锁但 oracle 未预测 | Undecided (跳过) | UNDECIDED_DEADLOCK |
| 执行中止但 oracle 未预测 | Undecided (可能合法中止) | UNDECIDED_ABORT + 正则模式匹配 |
| 执行阻塞但 oracle 未预测 | Undecided (可能合法锁) | UNDECIDED_BLOCK + 正则模式匹配 |

**SQLancer 优势**：`TxDiscrepancyClassifier` 使用正则匹配识别 DBMS 特定的合法中止/阻塞模式（如 MySQL 的 "Deadlock found"、PostgreSQL 的 "could not serialize"），比 Troc 的简单判断更精确。

---

## 四、多事务交互组合方式

### 4.1 调度生成策略对比

| 维度 | Troc `ShuffleTool` | SQLancer Fucci v2.2.0 | SQLancer TX_INFER |
|------|--------------------|----------------------|-------------------|
| **穷举** | ✅ 递归回溯 C(n1+n2, n1) | ✅ `ScheduleExhaustiveEnumerator` | 🟡 随机 |
| **采样** | ✅ 蓄水池采样 | ✅ 蓄水池采样 | 🟡 随机去重循环 |
| **非交错过滤** | ❌ 无 | ✅ `filterNonInterleaved()` | ❌ |
| **异常历史过滤** | ❌ 无 | ✅ `filterAnomalousHistories()` | ❌ |
| **去重** | ✅ HashSet\<String\> | ✅ List.contains() | ✅ List.contains() |
| **随机生成** | ✅ `genRandomSubmittedTrace()` | ✅ `genOneScheduleInternal()` | ✅ `genOneScheduleInternal()` |

**SQLancer Fucci 的调度过滤是独特优势**。Troc 的 `ShuffleTool` 没有任何过滤机制，生成的所有 C(n1+n2, n1) 种交错排列都会被执行或采样，包括大量无效调度（如 Tx1 全在 Tx2 前的串行调度、冲突在 COMMIT 之后的调度）。

### 4.2 冲突构造策略对比

| 策略 | Troc `makeConflict()` | SQLancer `TxConflictConstructor` |
|------|----------------------|----------------------------------|
| WHERE 子句共享 | ✅ `stmt1.whereClause = stmt2.whereClause` | ✅ Strategy A (60%): 复制 WHERE |
| 同行目标 | ✅ `stmt1.makeChooseRow(id), stmt2.makeChooseRow(id)` | ✅ Strategy B (40%): 注入 PK 条件 |
| 部分共享 | ❌ | ❌ (Troc 论文提到但未在代码中实现) |
| INSERT 冲突 | ❌ | ❌ |
| 后置保留 | N/A | ✅ 保留 FOR UPDATE/LIMIT/ORDER BY |

**SQLancer 优势**：
- Strategy B 使用反射获取 PK 列名，注入 `AND pk_col = value`，比 Troc 的 `makeChooseRow()` 更通用
- `extractWhereCondition()` 正确处理后缀（FOR UPDATE、LOCK IN SHARE MODE、LIMIT、ORDER BY）
- 有 12 个单元测试验证

### 4.3 事务数量

| 项目 | 事务数量 | 说明 |
|------|:--------:|------|
| Troc | 固定 2 | 论文设计限制 |
| SQLancer WriteCheck | 1-5 (可配) | `--nr-transactions` 参数 |
| SQLancer TX_INFER | 1-5 (可配) | 同 WriteCheck |
| SQLancer Fucci | 固定 2 | 论文设计限制 |

**SQLancer 优势**：WriteCheck 和 TX_INFER 支持多事务（最多 5 个），可以检测 3+ 事务交互场景下的隔离违规（如 Write Skew、Lost Update 等需要 3 事务触发的 Bug）。

---

## 五、隔离级别覆盖范围

### 5.1 隔离级别支持矩阵

| 隔离级别 | Troc | SQLancer MySQL | SQLancer PG | SQLancer GaussDB-A | SQLancer GaussDB-M |
|----------|:----:|:--------------:|:-----------:|:------------------:|:------------------:|
| READ UNCOMMITTED | ✅ | ✅ | ❌ (=RC) | ❌ (=RC) | ✅ |
| READ COMMITTED | ✅ | ✅ | ✅ | ✅ | ✅ |
| REPEATABLE READ | ✅ | ✅ | ✅ | ✅ | ✅ |
| SERIALIZABLE | ✅ | ✅ | ✅ | ✅ | ✅ |

### 5.2 快照时机差异

| DBMS | Troc | SQLancer TX_INFER | SQLancer Fucci |
|------|------|-------------------|----------------|
| MySQL | 首次 SELECT | 首次 SELECT | ✅ 隔离级别规则映射 |
| MariaDB | 首次 SELECT | N/A | ✅ |
| TiDB | BEGIN | N/A | ✅ |
| PostgreSQL | N/A | BEGIN | ✅ |
| GaussDB-A | N/A | BEGIN | ✅ |
| GaussDB-M | N/A | 首次 SELECT | ✅ |

**SQLancer 优势**：覆盖了 Troc 不支持的 PostgreSQL 和 GaussDB 系列，且正确处理了不同 DBMS 的快照时机差异。

### 5.3 PostgreSQL SSI (Serializable Snapshot Isolation)

PostgreSQL 的 SERIALIZABLE 使用 SSI 而非传统的 2PL+MVCC。SSI 的特点：
- 基于依赖图检测 serialization anomaly
- 可能导致事务被 "serialization failure" 中止
- 不依赖锁来保证隔离

**两者均未精确建模 SSI**。Troc 仅支持 MySQL/MariaDB/TiDB。SQLancer 的 TX_INFER 对 PG SERIALIZABLE 使用与 RR 相同的快照逻辑 + 范围锁，这在语义上不完全正确（SSI 不使用传统的锁机制）。`TxDiscrepancyClassifier` 通过识别 "could not serialize" 错误将其归为 UNDECIDED 来处理。

---

## 六、Troc 可借鉴的优势功能

基于上述分析，以下是 Troc 中**SQLancer 尚未充分实现**且有实际价值的功能：

### 🔴 优先级 1: SELECT 视图上谓词求值 (queryOnView)

**Troc 能力**：在内存视图上执行 WHERE 谓词求值，精确预测每条 SELECT 的返回行。

**当前差距**：
- Fucci MT Oracle 的 `simulateMVCCExecution()` 将 beforeView 的所有行扁平化为 List，未做 WHERE 过滤
- Fucci CS Oracle 的 `FucciConstraintSolver.parseExpression()` 是空壳

**建议方案**：利用已有的 `jsqlparser` 依赖实现简化的表达式求值器：
1. 用 JSqlParser 解析 WHERE 子句为 AST
2. 实现 `ExpressionEvaluator.evaluate(Expression, Map<String, Object>)` 递归求值
3. 支持基础操作：比较 (`=`, `<`, `>`, `<=`, `>=`, `<>`)、逻辑 (`AND`, `OR`, `NOT`)、`IS NULL`、`IN`、`BETWEEN`
4. 对 beforeView 中的每行调用求值器，预测 SELECT 结果
5. 将预测结果与实际 SELECT 结果比较

**预期收益**：使 Fucci MT Oracle 从"最终状态比较"升级为"逐语句 SELECT 结果比较"，检测能力覆盖不可重复读、幻读等场景。

### 🔴 优先级 2: 精确范围锁检测 (isRangeConflict)

**Troc 能力**：快照-执行-比较方法精确检测范围锁冲突。

**当前差距**：`InnoDBRangeConflictDetector` 使用保守启发式（同表+范围锁=冲突），误报率高。

**建议方案**：在 FucciLockAnalyzer 中实现 Troc 的快照-执行-比较方法：
1. 对每个 range-lock-holding 语句，记录其 `beforeView`
2. 当其他事务的写语句到来时：
   - 在临时表上构建 beforeView 的数据
   - 执行写语句
   - 重新查询 beforeView 对应的行集
   - 比较行集变化 → 变化则冲突
3. 恢复临时表

**注意**：这需要在真实 DB 上创建临时表，实现复杂度较高。可以考虑在 Fucci 的 MT Oracle 中使用（Fucci 已有 `reproduceDatabase()` 机制），而非 TX_INFER。

### 🟡 优先级 3: Blocked 语句重处理优化

**Troc 能力**：COMMIT/ROLLBACK 时重处理**所有**被阻塞语句：
```java
if (stmt.type == COMMIT || stmt.type == ROLLBACK) {
    otherTx.blocked = false;
    for (StatementCell blockedStmt : otherTx.blockedStatements) {
        analyzeStmt(blockedStmt, otherTx, curTx);
        oracleOrder.add(blockedStmt);
    }
}
```

**当前差距**：SQLancer Fucci 的 `FucciLockAnalyzer.processBlockedStatements()` 已实现重处理逻辑，但 `TxTestExecutor` 的执行循环中**被阻塞的语句不会重新提交执行**——它只标记为 blocked 并跳过，依赖超时机制让 DB 自行解除阻塞。

**建议方案**：在 `postStatementComplete()` 中，当 COMMIT/ROLLBACK 释放锁后，检查是否有被阻塞的事务可以恢复，并通过 `blockedTxs` 机制让执行器重新尝试。

### 🟡 优先级 4: INSERT 冲突构造

**Troc 能力**：两个 INSERT 触发唯一索引冲突。

**当前差距**：`TxConflictConstructor` 只处理 UPDATE/DELETE/SELECT，INSERT 被 `filterDataStatements()` 排除。

**建议方案**：
1. 在 `TxConflictConstructor.makeConflict()` 中增加 INSERT 冲突策略
2. 从表 schema 获取唯一索引列
3. 为两个 INSERT 语句设置相同的唯一索引值

---

## 七、SQLancer 相对 Troc 的独特优势

以下是 SQLancer 已超越 Troc 的能力，应继续保持和强化：

### 7.1 调度质量（已超越）
- 非交错过滤 + 异常历史过滤 — Troc 没有
- 混合穷举+采样策略 — Troc 有穷举和采样但无混合策略

### 7.2 冲突构造（已超越）
- PK 注入策略 — Troc 仅共享 WHERE 或同行
- 后缀保留 — Troc 无此需求（直接操作 AST）

### 7.3 DBMS 覆盖（远超）
- PostgreSQL + GaussDB-A/GaussDB-M — Troc 不支持
- 快照时机差异处理 — 覆盖 5 种 DBMS 行为

### 7.4 Bug 分类（已超越）
- `TxDiscrepancyClassifier` 使用 DBMS 特定正则匹配 — Troc 仅简单字符串比较
- 6 种 Bug + 3 种 Undecided — Troc 5 种 Bug + 3 种 Undecided

### 7.5 测试用例缩减（远超）
- 4 级 Reducer 框架（L1+L2 部分实现） — Troc 无 Reducer
- 3 种选择策略（Random/Probability/EpsilonGreedy） — Troc 无

### 7.6 多事务支持（远超）
- WriteCheck/TX_INFER 支持 1-5 事务 — Troc 固定 2 事务
- 可检测 Write Skew 等需要 3+ 事务的 Bug

### 7.7 辅助版本表方案（独特）
- TX_INFER 利用 DB 自身查询引擎 — 天然支持复杂 SQL
- 无需实现表达式求值器

---

## 八、改进路线图建议

| 阶段 | 改进项 | 优先级 | 预期收益 | 工作量 |
|:----:|--------|:------:|----------|:------:|
| 1 | SELECT 视图谓词求值 (JSqlParser) | 🔴 P0 | MT Oracle 检测能力提升 3x | 大 |
| 2 | 精确范围锁检测 | 🔴 P0 | 减少 Undecided 判定 | 中 |
| 3 | Blocked 语句重处理优化 | 🟡 P1 | 提升锁分析精度 | 小 |
| 4 | INSERT 冲突构造 | 🟡 P1 | 增加死锁触发概率 | 小 |
| 5 | Fucci DT Oracle 参考 DB 执行 | 🟡 P2 | 跨 DBMS 对比检测 | 中 |
| 6 | Reducer L3-L4 表达式/常量化简 | 🟡 P2 | Bug 报告可用性 | 中 |
| 7 | PostgreSQL SSI 建模 | 🟢 P3 | PG SERIALIZABLE 精度 | 大 |
