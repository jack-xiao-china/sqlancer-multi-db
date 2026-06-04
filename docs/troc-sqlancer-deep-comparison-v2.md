# Troc vs SQLancer 事务检测 Oracle 深度对比分析

> 分析日期：2026-06-03
> 分析范围：MySQL、PostgreSQL、GaussDB-A、GaussDB-M
> 源码版本：Troc-main、SQLancer v2.0.63

---

## 一、总体架构对比

| 维度 | Troc | SQLancer |
|------|------|----------|
| **项目定位** | 专注事务正确性测试 | 通用 DBMS 逻辑 Bug 检测 |
| **事务数量** | 固定 2 个事务对 | 1-5 个事务（加权偏向 2-3） |
| **Oracle 数量** | 1 个（MVCC 推理） | 4 种：WRITE_CHECK、TX_INFER、Fucci(DT/MT/CS)、WRITE_CHECK_REPRODUCE |
| **支持 DBMS** | MySQL、MariaDB、TiDB | MySQL、PostgreSQL、GaussDB-A、GaussDB-M 等 20+ |
| **执行模型** | 线程并发（Producer-Consumer） | 线程池 + Future.get(timeout) |
| **Schedule 生成** | 穷举 + Reservoir Sampling | 随机 + 穷举混合（v2.0.63 借鉴后） |

### 1.1 核心架构图

```
Troc 架构:
Main → Table → genTransactionPair() → TrocChecker → TxnPairExecutor (线程并发)
                                    → inferOracleMVCC() (内存推理)
                                    → compareOracles() (差异分类)

SQLancer 架构:
Main → OracleFactory → WriteCheckOracle / TxInferOracle / FucciOracle
                     → TxTestGenerator → TxTestExecutor (线程池)
                     → TxBase.compare*Results() (差异分类)
```

---

## 二、功能实现对比

### 2.1 Oracle 类型与检测目标

| Oracle 类型 | Troc | SQLancer | 检测目标 |
|-------------|:----:|:--------:|----------|
| **MVCC 推理** (内存模拟) | ✅ `inferOracleMVCC()` | ✅ `FucciMTOracle` (简化版) | 基于 MVCC 可见性规则推理每步应看到的结果 |
| **辅助版本表** (SQL 执行) | ❌ | ✅ `MySQLTxInfer` / `GaussDBMTxInfer` | 用 SQL 创建版本表跟踪行版本变化 |
| **黑盒 Schedule 对比** | ❌ | ✅ `WriteCheckOracle` | 同一事务在不同 schedule 下结果应一致 |
| **差分测试** (对比参考 DB) | ❌ | ✅ `FucciDTOracle` | 同 SQL 在不同 DBMS 实例上执行结果对比 |
| **约束求解** | ❌ | ✅ `FucciCSOracle` (占位实现) | SELECT 谓词约束验证 |
| **Range Conflict 检测** | ✅ `Lock.isRangeConflict()` | ❌ | 幻读检测：INSERT 对范围锁的影响 |

**关键差异**：
- **Troc 的核心优势**：`inferOracleMVCC()` 是完整的内存 MVCC 模拟器，精确到每行每版本的可见性判断，包含锁冲突预测和范围冲突检测
- **SQLancer 的核心优势**：`TX_INFER` 通过实际 SQL 创建辅助版本表，不依赖内存模拟的准确性，更鲁棒但仅支持 MySQL/GaussDB-M
- **SQLancer 的 WRITE_CHECK** 是黑盒方法，不需要建模 MVCC 语义，但检测能力弱于 Troc 的白盒推理

### 2.2 SQL 语句支持范围

| 语句类型 | Troc | SQLancer WRITE_CHECK | SQLancer TX_INFER | SQLancer Fucci |
|----------|:----:|:--------------------:|:-----------------:|:--------------:|
| SELECT | ✅ | ✅ | ✅ | ✅ |
| SELECT FOR UPDATE | ✅ | ✅ | ✅ | ✅ |
| SELECT FOR SHARE | ✅ | ✅ | ✅ | ✅ |
| INSERT | ✅ | ✅ | ✅ | ✅ |
| UPDATE | ✅ | ✅ | ✅ | ✅ |
| DELETE | ✅ | ✅ | ✅ | ✅ |
| REPLACE | ❌ | ✅ (MySQL) | ✅ | ✅ |
| DDL (CREATE/ALTER) | ❌ | ✅ | ❌ | ❌ |
| SAVEPOINT | ❌ | ❌ | ❌ | ❌ |
| SET | ✅ | ✅ | ❌ | ✅ |

**差异点**：
- Troc 不支持 REPLACE（仅 MySQL 特有）
- SQLancer 的 WRITE_CHECK 可包含 DDL，但 TX_INFER 和 Fucci 不支持
- 两者都不支持 SAVEPOINT（嵌套事务测试盲区）

### 2.3 事务生成策略

| 维度 | Troc | SQLancer |
|------|------|----------|
| **事务数量** | 固定 2 | 1-5（加权：`{1,2,2,2,2,3,3,3,4,5}`） |
| **事务体大小** | 3-6 条语句 | 1-5 条语句 |
| **冲突构造** | ✅ `makeConflict()` 显式构造 | ❌ 随机生成，依赖概率碰撞 |
| **WHERE 共享** | ✅ 两事务共享 WHERE 或指向同行 | ❌ 各事务独立生成 |

**Troc 的 `makeConflict()` 算法**（TableTool.java L196-210）：
1. 从两个事务中各随机选取一条含 WHERE 子句的语句
2. 随机决定：共享 WHERE 条件 或 指向同一行 ID
3. 确保两个事务操作有数据重叠，提高锁冲突概率

**SQLancer 的差距**：随机生成的事务操作目标不重叠的概率很高，导致锁冲突少、可检测的 Bug 路径少。这是 **Troc 的显著优势**。

---

## 三、检测逻辑对比

### 3.1 MVCC 可见性建模

#### Troc `inferOracleMVCC()` — 完整内存模拟

```
数据结构:
  vData: HashMap<rowId, ArrayList<Version>>  // 每行的版本链
  Version: {data, tx, deleted}               // 版本数据+创建者+删除标记
  View: {data: HashMap<rowId, Object[]>, deleted: HashMap<rowId, Boolean>}

可见性规则（按隔离级别）:
  READ_UNCOMMITTED: newView() → 每个行取最新版本（含未提交）
  READ_COMMITTED:   buildTxView() → 可见 txInit + curTx + 已提交的 otherTx
  REPEATABLE_READ:  snapshotView() → 首个 SELECT 时建立快照，后续复用
  SERIALIZABLE:     同 RC + SELECT 加 SHARE 锁

快照时机:
  MySQL/MariaDB: 第一条 SELECT 语句触发
  TiDB: BEGIN 语句触发

版本更新:
  INSERT → 在版本链末尾添加新版本
  UPDATE → 在受影响行的版本链末尾添加新版本
  DELETE → 在受影响行的版本链末尾添加 deleted=true 版本
  ROLLBACK → 删除该事务创建的所有版本
```

#### SQLancer `MySQLTxInfer` — 辅助版本表（SQL 执行）

```
数据结构:
  _infer_<table>_vt (版本表):
    rid (行 ID), vid (版本 ID), deleted (删除标记), txid (事务 ID), [原始列...]

可见性规则:
  READ_COMMITTED:  readCommittedVersion() → 查 committedTxs 列表
  REPEATABLE_READ: readSnapshotVersion() → 查 snapshotTxs 映射
  SERIALIZABLE:    同 RR

快照时机:
  MySQL/GaussDB-M: 第一条 SELECT 触发（v2.0.63 修复）

版本更新:
  INSERT → INSERT INTO _vt (rid, vid, txid, ...)
  UPDATE → INSERT INTO _vt (新版本)
  DELETE → UPDATE _vt SET deleted=1
  COMMIT → committedTxs.add(curTx)
  ROLLBACK → DELETE FROM _vt WHERE txid = curTx
```

#### SQLancer `FucciMTOracle` — 简化内存模拟

```
数据结构:
  versionChains: Map<tableName, Map<rowId, List<Version>>>
  Version: {data, transaction, deleted, timestamp}

可见性规则:
  仅区分 SERIALIZABLE vs 其他:
    SERIALIZABLE → compareAllResults()
    其他 → compareWriteTxResults()
  版本可见性: isVersionCommitted() → 仅检查 tx.isCommitted()

限制:
  不区分 RC/RR/RU 的可见性差异
  不建模快照时机
  不建模锁冲突
```

**关键对比**：

| 建模精度 | Troc | SQLancer TX_INFER | SQLancer Fucci-MT |
|----------|:----:|:-----------------:|:-----------------:|
| RU 可见性 | ✅ newView | ❌ (不支持) | ❌ 不区分 |
| RC 可见性 | ✅ buildTxView | ✅ readCommitted | ❌ 不区分 |
| RR 可见性 | ✅ snapshotView | ✅ readSnapshot | ❌ 不区分 |
| SER 可见性 | ✅ + SHARE锁 | ✅ readSnapshot | ✅ compareAll |
| 快照时机 | ✅ 按 DBMS 区分 | ✅ 按 DBMS 区分 | ❌ 不建模 |
| 锁冲突预测 | ✅ isConflict | ❌ | ❌ |
| 范围锁/幻读 | ✅ isRangeConflict | ❌ | ❌ |
| 死锁预测 | ✅ 双阻塞=死锁 | ❌ | ❌ |

### 3.2 锁冲突建模

**Troc 的锁模型**（Lock.java）：

```java
锁类型: SHARE, EXCLUSIVE, NONE
锁对象: {rowIds: HashSet<Integer>, indexes: HashSet<String>}

获取锁规则 (getLock):
  SELECT + SERIALIZABLE     → SHARE
  SELECT_FOR_SHARE          → SHARE
  SELECT_FOR_UPDATE/UPDATE/DELETE/INSERT → EXCLUSIVE

冲突检测 (isConflict):
  1. 类型检查: 至少一方是 EXCLUSIVE
  2. 行 ID 交集检查
  3. 索引键交集检查
  4. 范围冲突检查 (MySQL RR/SER 专用)

范围冲突 (isRangeConflict):
  1. 保存当前表快照
  2. 物化受影响语句的视图
  3. 执行修改语句
  4. 比较执行前后的锁对象集合
  5. 集合不同 → 幻读检测 → 冲突
```

**SQLancer WRITE_CHECK**：不建模锁。通过比较不同 schedule 的执行结果间接检测锁问题。

**SQLancer TX_INFER**：不建模锁。只建模 MVCC 可见性。

**SQLancer Fucci-MT**：不建模锁。只建模版本可见性。

**结论**：Troc 的锁建模是其核心优势之一。能预测 "应阻塞但未阻塞"（Missing Lock）和 "不应阻塞但阻塞了"（Unnecessary Lock），而 SQLancer 只能通过 schedule 差异间接检测。

### 3.3 差异分类与 Undecided 处理

| 差异类型 | Troc 处理 | SQLancer 处理 (v2.0.63) |
|----------|-----------|------------------------|
| **执行死锁 + Oracle 不死锁** | → Undecided (跳过) | → UNDECIDED_DEADLOCK (跳过) |
| **Oracle 中止 + 执行未中止** | → Missing Abort (Bug) | → MISSING_ABORT (Bug) |
| **执行中止 + Oracle 未中止** | → shouldNotAbort() ? Bug : Undecided | → canBeLegitimateAbort() ? Undecided : Bug |
| **Oracle 阻塞 + 执行未阻塞** | → Missing Lock (Bug) | → MISSING_LOCK (Bug) |
| **执行阻塞 + Oracle 未阻塞** | → shouldNotBlock() ? Bug : Undecided | → canBeLegitimateBlock() ? Undecided : Bug |
| **查询结果不一致** | → Inconsistent Query Result (Bug) | → INCONSISTENT_QUERY_RESULT (Bug) |
| **最终状态不一致** | → Inconsistent Final State (Bug) | → INCONSISTENT_FINAL_STATE (Bug) |

**Troc 的 `shouldNotAbort()`/`shouldNotBlock()`**：默认返回 `false`，即所有未建模的 abort/block 差异都归为 Undecided。

**SQLancer 的 `canBeLegitimateAbort()`**：基于错误消息模式匹配（死锁、超时、序列化失败等），能识别更多合法 abort 场景，降低误报率。

**SQLancer 的优势**：更精细的错误消息匹配，覆盖 4 种 DBMS 的特有错误模式（MySQL 死锁、PG SSI、GaussDB-A ORA 码等）。

### 3.4 结果对比方式

| 维度 | Troc | SQLancer |
|------|------|----------|
| **结果预处理** | null → "[NULL]"，排序后比较 | null → "[NULL]"，排序后比较 |
| **对比语义** | 集合比较（无序） | 集合比较（无序） |
| **最终状态对比** | 单表（Troc 只操作一张表） | 多表（遍历所有表） |
| **错误对比** | 不比较错误/警告 | ✅ 比较 error 和 warning |
| **SELECT 对比时机** | 每条 SELECT 立即对比 | 同上（SER）/ 跳过 SELECT（非 SER） |

**差异点**：
- SQLancer 额外对比 error/warning 信息，Troc 不对比
- SQLancer 的 `compareWriteTxResults()` 在非 SER 级别跳过 SELECT，因为 RC/RR 下 SELECT 结果的差异是合法的
- Troc 对所有隔离级别都比较 SELECT 结果，因为其 MVCC 模型能精确预测可见数据

---

## 四、多事务交互组合方式对比

### 4.1 事务数量支持

| 维度 | Troc | SQLancer WRITE_CHECK | SQLancer TX_INFER | SQLancer Fucci |
|------|:----:|:--------------------:|:-----------------:|:--------------:|
| **事务数量** | 固定 2 | 1-5 | 1-5 | 固定 2 |
| **交互维度** | 两两配对 | 多维交叉 | 多维交叉 | 两两配对 |
| **MVCC 可见性** | 仅需考虑 1 个 otherTx | 需考虑 N-1 个 otherTx | 同上 | 仅需考虑 1 个 |
| **死锁检测** | 双方都阻塞 = 死锁 | 更复杂（可能 3 方循环等待） | 同上 | 双方 |

### 4.2 Schedule 生成策略

| 策略 | Troc | SQLancer (v2.0.63) |
|------|------|---------------------|
| **穷举枚举** | ✅ `genAllSubmittedTrace()` 回溯 | ✅ `ScheduleExhaustiveEnumerator.enumerateAll()` |
| **Reservoir Sampling** | ✅ `sampleSubmittedTrace()` | ✅ `reservoirSample()` |
| **混合策略** | ✅ 先穷举再采样 | ✅ `hybridGenerate()` (仅 2 事务) |
| **随机去重** | ✅ `genRandomSubmittedTrace()` HashSet | ✅ `genSchedules()` contains 检查 |
| **N>2 事务穷举** | ❌ 不支持 | ✅ `countSchedules()` 多项式系数 |
| **N>2 事务采样** | ❌ 不支持 | ✅ 随机采样（非均匀） |

**关键差异**：
- Troc 的 Reservoir Sampling 保证了均匀分布，每条 schedule 被选中的概率严格相等
- SQLancer 对 N>2 事务的随机采样不是均匀分布（`Randomly.fromList` 每次选事务的概率相等，但不是均匀选择所有可能的交错）
- SQLancer v2.0.63 已借鉴 Troc 的穷举+采样策略用于 2 事务场景

### 4.3 Schedule 执行引擎

**Troc — Producer-Consumer 线程模型**：
```
Producer Thread:
  → 遍历 schedule，将语句分发到对应的 Consumer Queue
  → 等待 2 秒，超时 → 标记为阻塞
  → COMMIT/ROLLBACK 后解除另一事务的阻塞，执行排队语句
  → 死锁检测：双方都阻塞

Consumer1 Thread: 执行 tx1 语句，通过 Queue 返回结果
Consumer2 Thread: 执行 tx2 语句，通过 Queue 返回结果
```

**SQLancer — 线程池 + Future.get(timeout)**：
```
execute():
  → 创建 N 线程的 ExecutorService
  → 遍历 schedule，submit 语句
  → Future.get(2s) → TimeoutException → 标记阻塞
  → 检测死锁/回滚 → handleAbortedTxn()
  → 检查已阻塞事务是否可恢复
```

**差异**：
- Troc 的 Producer-Consumer 模型更精确地模拟了并发交错
- SQLancer 的线程池模型更灵活，支持 N>2 事务
- 两者都使用 2 秒超时检测阻塞

---

## 五、隔离级别覆盖范围对比

### 5.1 隔离级别支持矩阵

| 隔离级别 | Troc MySQL | Troc MariaDB | Troc TiDB | SQLancer MySQL | SQLancer PG | SQLancer GaussDB-A | SQLancer GaussDB-M |
|----------|:----------:|:------------:|:---------:|:--------------:|:-----------:|:------------------:|:------------------:|
| READ_UNCOMMITTED | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ✅ |
| READ_COMMITTED | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| REPEATABLE_READ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| SERIALIZABLE | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |

### 5.2 各隔离级别下的 Oracle 行为差异

| 隔离级别 | Troc Oracle 行为 | SQLancer WRITE_CHECK 行为 | SQLancer TX_INFER 行为 |
|----------|------------------|--------------------------|------------------------|
| **RU** | `newView()` — 看最新版本 | `compareWriteTxResults()` | ❌ 不支持 |
| **RC** | `buildTxView()` — 看已提交 | `compareWriteTxResults()` | `readCommittedVersion()` |
| **RR** | `snapshotView()` — 首次 SELECT 快照 | `compareWriteTxResults()` | `readSnapshotVersion()` |
| **SER** | RC 视图 + SELECT 加 SHARE 锁 | `compareAllResults()` | `readSnapshotVersion()` |

**关键差异**：
- Troc 在 SER 下对 SELECT 加 SHARE 锁，能检测幻读冲突
- SQLancer WRITE_CHECK 在 SER 下比较所有结果（含 SELECT），在非 SER 下只比较写操作
- SQLancer TX_INFER 对 RR 和 SER 使用相同的快照机制（依赖 DBMS 自身的隔离语义）

### 5.3 快照时机建模

| DBMS | 快照点（理论正确） | Troc 实现 | SQLancer TX_INFER (v2.0.63) | SQLancer Fucci |
|------|-------------------|-----------|----------------------------|----------------|
| MySQL (InnoDB) | 第一条 SELECT | ✅ 第一条 SELECT | ✅ 第一条 SELECT（v2.0.63 修复） | ❌ 不建模 |
| PostgreSQL | BEGIN | N/A (不支持) | N/A (不支持 TX_INFER) | ❌ 不建模 |
| GaussDB-A | BEGIN | N/A (不支持) | N/A (不支持 TX_INFER) | ❌ 不建模 |
| GaussDB-M | 第一条 SELECT | N/A (不支持) | ✅ 第一条 SELECT（v2.0.63 修复） | ❌ 不建模 |

---

## 六、Troc 可借鉴给 SQLancer 的优势功能评估

### 6.1 已借鉴项（v2.0.63 已完成）

| 借鉴项 | Troc 原始实现 | SQLancer 移植实现 | 状态 |
|--------|--------------|-------------------|:----:|
| **Undecided 分类** | `shouldNotAbort()` / `shouldNotBlock()` | `TxDiscrepancyClassifier` + `TxBugType` | ✅ |
| **穷举 Schedule** | `ShuffleTool.genAllSubmittedTrace()` | `ScheduleExhaustiveEnumerator` | ✅ |
| **Reservoir Sampling** | `sampleSubmittedTrace()` | `reservoirSample()` | ✅ |
| **快照时机修正** | `isSnapshotPoint()` (MySQL=首 SELECT) | MySQLTxInfer/GaussDBMTxInfer 修复 | ✅ |
| **Bug 类型细化** | 6 种 Bug 类型 + 3 种 Undecided | `TxBugType` 枚举完全对齐 | ✅ |

### 6.2 可借鉴项评估

#### 优先级 P0：冲突构造（`makeConflict`）

| 维度 | 评估 |
|------|------|
| **Troc 实现** | `TableTool.makeConflict()` — 显式让两个事务操作相同行或相同 WHERE 条件 |
| **SQLancer 现状** | 随机生成事务，两个事务操作目标不重叠的概率极高 |
| **影响** | 事务目标不重叠 → 无锁冲突 → WRITE_CHECK 的两个 schedule 结果完全相同 → 检测能力接近零 |
| **实现难度** | 中等。需要在 `TransactionProvider.generateTransaction()` 中增加共享目标行的逻辑 |
| **涉及文件** | MySQL/PG/GaussDB-A/GaussDB-M 各自的 `TransactionProvider.java` 或 `TxTestGenerator.java` |
| **建议实现** | 在 `generateTransactions()` 中增加 `makeConflict()` 步骤：(1) 从两个事务中各选一条写语句 (2) 共享 WHERE 条件或指向同行 |
| **预期收益** | 显著提高锁冲突率，使 WRITE_CHECK 检测能力从低概率碰撞提升到确定性触发 |

#### 优先级 P1：Range Conflict 检测

| 维度 | 评估 |
|------|------|
| **Troc 实现** | `Lock.isRangeConflict()` — 快照前后对比法检测幻读 |
| **SQLancer 现状** | 无任何范围锁/幻读检测能力 |
| **影响** | MySQL/GaussDB-M 在 RR 和 SER 下使用间隙锁 (Gap Lock) 和 Next-Key Lock，幻读是重要的检测目标 |
| **实现难度** | 高。需要 (1) 建模 InnoDB 的锁层次（记录锁、间隙锁、插入意向锁） (2) 实现快照前后对比 (3) 集成到 TX_INFER 或新建 Oracle |
| **涉及文件** | 新增 `RangeConflictDetector.java`，修改 `MySQLTxInfer.java` / `GaussDBMTxInfer.java` |
| **建议实现** | 在 TX_INFER 的 `inferOracle()` 中，对 INSERT/UPDATE/DELETE 语句增加范围冲突检查：保存语句执行前的快照视图 → 执行语句 → 比较受影响行集 → 集合变化 = 幻读 |
| **预期收益** | 检测 MySQL/GaussDB-M 在 RR 下的幻读 Bug，这是当前 SQLancer 的盲区 |
| **风险** | InnoDB 锁行为复杂（GAP_LOCK 与 INSERT_INTENTION 的交互），精确建模困难，Undecided 率可能较高 |

#### 优先级 P2：FucciMTOracle 精度提升

| 维度 | 评估 |
|------|------|
| **Troc 实现** | 完整的 MVCC 建模：区分 RU/RC/RR/SER 可见性、快照时机、锁冲突 |
| **SQLancer Fucci-MT 现状** | 仅区分 SER vs 其他，不建模 RC/RR 可见性差异，不建模锁 |
| **影响** | Fucci-MT 在非 SER 级别几乎无检测能力 |
| **实现难度** | 高。需要将 Troc 的 `buildTxView()`、`snapshotView()`、`updateVersion()` 移植到 FucciMTOracle |
| **涉及文件** | `FucciMTOracle.java`、`Version.java`、`View.java`、新增锁模型 |
| **建议** | 考虑将 TX_INFER 的 SQL 执行方式移植到 Fucci-MT，而非在内存中模拟。TX_INFER 的 SQL 方式更鲁棒 |
| **预期收益** | 提升 Fucci-MT 在 RC/RR 下的检测能力 |
| **风险** | Fucci 框架与 SQLancer 原生框架存在抽象层次差异，移植可能引入大量适配代码 |

#### 优先级 P3：PG/GaussDB-A 的 TX_INFER 支持

| 维度 | 评估 |
|------|------|
| **现状** | TX_INFER 仅支持 MySQL 和 GaussDB-M（InnoDB 兼容引擎） |
| **影响** | PG 和 GaussDB-A 缺少精确的 MVCC Oracle，只能依赖 WRITE_CHECK（黑盒）和 Fucci（简化） |
| **实现难度** | 中等。PG 的 MVCC 模型与 InnoDB 不同（每行有 xmin/xmax），需要适配版本表结构 |
| **涉及文件** | 新增 `PostgresTxInfer.java`、`PostgresTxInferOracle.java`、`GaussDBATxInfer.java`、`GaussDBATxInferOracle.java` |
| **建议实现** | 参考 MySQLTxInfer 的架构，适配 PG 的 MVCC 语义：(1) 版本表使用 ctid 代替 rid (2) 快照时机为 BEGIN (3) 可见性基于 xmin/xmax |
| **预期收益** | 补齐 PG/GaussDB-A 的 MVCC 白盒检测能力 |

#### 优先级 P4：多事务冲突构造扩展

| 维度 | 评估 |
|------|------|
| **Troc 限制** | 仅支持 2 事务，`makeConflict()` 也只处理两两配对 |
| **SQLancer 优势** | 支持 3-5 事务，但缺少冲突构造 |
| **建议实现** | 扩展 `makeConflict()` 到 N 事务：(1) 随机选取 K 个事务对 (2) 每对共享操作目标 (3) 确保冲突图连通 |
| **实现难度** | 中等 |
| **预期收益** | 检测多事务循环等待死锁、级联回滚等复杂交互 Bug |

### 6.3 不建议借鉴的项

| 项目 | 理由 |
|------|------|
| **固定 2 事务** | SQLancer 的 N 事务支持是优势，不应回退 |
| **单表操作** | Troc 只操作一张表，SQLancer 支持多表，不应限制 |
| **Producer-Consumer 执行模型** | SQLancer 的线程池模型更灵活，更适合 N>2 场景 |
| **内存 MVCC 模拟替代 SQL 版本表** | TX_INFER 的 SQL 执行方式比内存模拟更鲁棒（不依赖建模精度） |

---

## 七、功能覆盖矩阵总结

| 检测能力 | Troc | SQLancer WRITE_CHECK | SQLancer TX_INFER | SQLancer Fucci | **差距** |
|----------|:----:|:--------------------:|:-----------------:|:--------------:|:--------:|
| 写结果一致性 | ✅ | ✅ | ✅ | ✅ | 无 |
| SELECT 可见性 (RR/SER) | ✅ | ✅ (SER only) | ✅ | ❌ (简化) | 中 |
| SELECT 可见性 (RC) | ✅ | ❌ (跳过) | ✅ | ❌ | 中 |
| SELECT 可见性 (RU) | ✅ | ❌ (跳过) | ❌ | ❌ | 低 |
| 锁冲突预测 | ✅ | ❌ | ❌ | ❌ | **高** |
| 范围锁/幻读检测 | ✅ | ❌ | ❌ | ❌ | **高** |
| 死锁预测 | ✅ | ❌ | ❌ | ❌ | 中 |
| 最终状态一致性 | ✅ | ✅ | ✅ | ✅ | 无 |
| 错误/警告对比 | ❌ | ✅ | ✅ | ✅ | SQLancer 优 |
| 多事务(>2) | ❌ | ✅ | ✅ | ❌ | SQLancer 优 |
| 差分测试(跨 DB) | ❌ | ❌ | ❌ | ✅ | SQLancer 独有 |
| 冲突构造 | ✅ | ❌ | ❌ | ✅ (部分) | **高** |
| 穷举 Schedule | ✅ | ✅ (2事务) | ✅ | ❌ | 无 |

---

## 八、结论与建议实施路线

### 短期（可直接实施）
1. **冲突构造 `makeConflict`**（P0）— 显著提升 WRITE_CHECK 检测能力
   - 预估工作量：2-3 天
   - 修改 4 个 DBMS 的 TransactionProvider/TxTestGenerator

### 中期（需要设计）
2. **Range Conflict 检测**（P1）— 补齐幻读检测盲区
   - 预估工作量：5-7 天
   - 新增 RangeConflictDetector，集成到 TX_INFER
3. **PG/GaussDB-A TX_INFER**（P3）— 补齐白盒 Oracle
   - 预估工作量：5-7 天
   - 新增 PostgresTxInfer + GaussDBATxInfer

### 长期（需要评估 ROI）
4. **FucciMTOracle 精度提升**（P2）— 取决于 Fucci 框架的定位
   - 预估工作量：7-10 天
5. **多事务冲突构造**（P4）— 扩展 makeConflict 到 N 事务
   - 预估工作量：3-5 天

---

> 分析人：Claude (基于源码深度阅读)
> 上次更新：2026-06-03
