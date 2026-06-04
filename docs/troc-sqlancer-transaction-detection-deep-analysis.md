# Troc 与 SQLancer 事务检测能力深度对比分析

> **分析日期**：2026-06-03
> **源码版本**：Troc（D:\Jack.Xiao\dbtools\Troc-main\Troc-main）、SQLancer（D:\Jack.Xiao\dbtools\sqlancer-main\sqlancer-main）
> **覆盖范围**：MySQL、PostgreSQL、GaussDB-A、GaussDB-M

---

## 一、Troc 架构全景

### 1.1 整体架构

```
┌─────────────────────────────────────────────────┐
│                    Main.java                     │
│          (入口，解析 Options, 循环执行)            │
├─────────────────────────────────────────────────┤
│               TableTool.java (25KB)              │
│  静态工具类：表管理、版本数据初始化、锁推断、       │
│  SQL执行、快照保存/恢复、行ID管理                  │
├───────────┬──────────────┬──────────────────────┤
│ Table.java│ StatementCell│  Transaction.java     │
│ (表生成)   │ (语句抽象)    │  (事务状态管理)        │
├───────────┴──────────────┴──────────────────────┤
│              ShuffleTool.java                    │
│     (穷举/采样 Schedule 交错序列)                  │
├──────────────────┬──────────────────────────────┤
│ TxnPairExecutor  │      TrocChecker.java         │
│ (真实DB执行)      │   (MVCC推断Oracle + Bug检测)   │
│ 双线程+阻塞队列    │   inferOracleMVCC()           │
│ 2秒超时检测阻塞    │   analyzeStmt()               │
│                  │   compareOracles()             │
├──────────────────┴──────────────────────────────┤
│  Version.java  │  View.java  │  Lock.java         │
│  (版本链数据)    │ (视图快照)   │ (锁建模+Range检测) │
└─────────────────────────────────────────────────┘
```

### 1.2 核心执行流程

```
Main.main()
  for 每轮:
    1. TableTool.createTable()        → 建表 + 初始化数据
    2. Table.genTransaction() × 2     → 生成 tx1, tx2（各3-6条语句）
    3. ShuffleTool.genAll/sampleTrace → 生成 Schedule 列表
    4. for 每个 Schedule:
       a. TxnPairExecutor.execute()   → 在真实DB上交错执行 → execResult
       b. TrocChecker.inferOracleMVCC() → 内存模拟MVCC → oracleResult
       c. TrocChecker.compareOracles() → 对比 execResult vs oracleResult
          ├── 差异 → Bug Report（含完整复现信息）
          └── Undecided → 跳过（无法判定）
```

### 1.3 关键算法详解

#### 1.3.1 MVCC 版本链（vData）

```java
// TrocChecker.java:124
vData = TableTool.initVersionData();
// 类型: HashMap<Integer, ArrayList<Version>>
// key = rowId, value = 该行的版本链（按创建顺序排列）

// Version 结构:
class Version {
    Object[] data;     // 行数据
    Transaction tx;    // 创建此版本的事务（txInit=初始, tx1, tx2）
    boolean deleted;   // 是否为删除标记（tombstone）
}
```

#### 1.3.2 视图构建（三种 View）

| View 方法 | 用途 | 隔离级别 |
|-----------|------|---------|
| `newView()` | 取每行最新版本（含未提交） | READ_UNCOMMITTED |
| `buildTxView(curTx, otherTx, useDel)` | 取已提交+自身版本 | READ_COMMITTED / SELECT_SHARE / SELECT_UPDATE |
| `snapshotView(curTx)` | 取快照点时刻的版本 | REPEATABLE_READ / SERIALIZABLE |

**buildTxView 的幻读追踪逻辑（423-448行）**：
```
readTxs = [txInit, curTx]  // 可见事务列表
if otherTx.committed → readTxs.add(otherTx)

for 每行 rowId:
  从版本链末尾向前找第一个 readTxs 中的版本:
    if !version.deleted → view.data[rowId] = version.data
    if version.deleted && snapView有此行 && useDel → 
      view.data[rowId] = snapView.data[rowId]  // 保留快照原始数据
      view.deleted[rowId] = true                // 标记幻读删除
```

#### 1.3.3 锁冲突检测

**锁类型（3种）**：SHARE / EXCLUSIVE / NONE

**锁分配规则（TableTool.getLock 252-271行）**：
| 语句类型 | 锁类型 |
|---------|--------|
| SELECT（普通） | NONE（SERIALIZABLE 时为 SHARE） |
| SELECT_SHARE | SHARE |
| SELECT_UPDATE / UPDATE / DELETE / INSERT | EXCLUSIVE |

**冲突检测（Lock.isConflict 23-41行）**：
```
for otherTx 持有的每个锁 otherLock:
  if (this是EXCLUSIVE 或 otherLock是EXCLUSIVE)
    && (rowIds有交集 || indexes有交集) → 冲突
  if useRangeLock(this) && isRangeConflict(otherLock的语句, this语句) → 冲突
  if useRangeLock(otherLock) && isRangeConflict(this语句, otherLock的语句) → 冲突
```

#### 1.3.4 Range Conflict 检测（快照前后对比法）

**触发条件（useRangeLock 43-48行）**：
- 仅 MySQL/MariaDB 的 REPEATABLE_READ 和 SERIALIZABLE

**算法（isRangeConflict 50-66行）**：
```
1. takeSnapshotForTable("range_conflict")   → 保存表状态
2. viewToTable(affectedStmt.view)           → 将被影响语句的View物化到表
3. affectedBefore = getLockObject(affectedStmt)  → 记录修改前的rowIds/indexes
4. executeOnTable(stmt.statement)           → 执行修改语句（INSERT/UPDATE/DELETE）
5. affectedAfter = getLockObject(affectedStmt)   → 记录修改后的rowIds/indexes
6. recoverTableFromSnapshot("range_conflict") → 恢复表状态
7. return affectedBefore ≠ affectedAfter     → 差异即Range Conflict（幻读）
```

**本质**：通过在真实DB上临时执行修改语句，对比修改前后另一条语句能看到的行集合是否变化。变化意味着出现了幻行。

#### 1.3.5 快照点建模

| DBMS | 快照建立时机 | 代码 |
|------|------------|------|
| MySQL/MariaDB | 第一条 SELECT | `isSnapshotPoint()` 361-371行 |
| TiDB | BEGIN | 同上 |
| PostgreSQL | ❌ 不支持 | Troc 不覆盖 PG |
| GaussDB | ❌ 不支持 | Troc 不覆盖 GaussDB |

#### 1.3.6 Undecided 分类（compareOracles 538-606行）

| 场景 | 分类 | 行为 |
|------|------|------|
| 执行死锁 + Oracle未预期死锁 | **Undecided** | 跳过，skipCase++ |
| 执行abort + Oracle未预期abort | 若 shouldNotAbort() → Bug；否则 → **Undecided** | 视判断结果 |
| 执行block + Oracle未预期block | 若 shouldNotBlock() → Bug；否则 → **Undecided** | 视判断结果 |
| Oracle说abort + 执行未abort | **Bug: Missing abort** | 报告 |
| Oracle说block + 执行未block | **Bug: Missing lock** | 报告 |
| 查询结果不一致 | **Bug: Inconsistent result** | 报告 |
| 最终状态不一致 | **Bug: Inconsistent final state** | 报告 |

#### 1.3.7 Schedule 穷举

```java
// ShuffleTool.genAllSubmittedTrace()
// 回溯法枚举 C(n1+n2, n1) 种合法交错
// 例: tx1=4条, tx2=4条 → C(8,4)=70 种
//     tx1=6条, tx2=6条 → C(12,6)=924 种

// 采样模式: sampleSubmittedTrace(count=10)
// 当全量 ≤ count → 返回全量
// 当全量 > count → Reservoir Sampling 均匀抽取 count 个
```

---

## 二、SQLancer 事务检测能力全景

### 2.1 现有 Oracle 体系

| Oracle | 方法论 | MySQL | PG | GaussDB-M | GaussDB-A | 代码状态 |
|--------|-------|:-----:|:--:|:---------:|:---------:|:--------:|
| **WRITE_CHECK** | 同一事务组不同schedule对比（黑箱） | ✅ | ✅ | ✅ | ✅ | 生产可用 |
| **TX_INFER** | 辅助版本表 `_vt` 用真实SQL模拟MVCC | ✅ | ❌ | ✅ | ❌ | 生产可用 |
| **Fucci-MT** | 内存MVCC模拟 | ⚠️ | ⚠️ | ⚠️ | ⚠️ | 空壳 |
| **Fucci-DT** | 差分测试（参考DB对比） | ⚠️ | ⚠️ | ⚠️ | ⚠️ | 空壳 |
| **Fucci-CS** | 约束求解验证 | ⚠️ | ⚠️ | ⚠️ | ⚠️ | 空壳 |
| **WRITE_CHECK_REPRODUCE** | 从文件加载用例复现Bug | ✅ | ✅ | ✅ | ✅ | 生产可用 |

### 2.2 TX_INFER 核心算法（MySQLTxInfer.java 666行）

```
inferOracle()
  1. 为每张表:
     ├── addRowIdColumn(tableName)     → ALTER TABLE 添加 rid 列
     ├── fillEmptyRowIds(tableName)    → UPDATE ... SET rid=N WHERE rid IS NULL LIMIT 1
     └── initVersionTable(tableName)   → CREATE TABLE _infer_<name>_vt AS SELECT * FROM <name>
                                         ALTER TABLE _vt ADD vid INT, deleted INT DEFAULT 0, txid INT DEFAULT 0
  2. 遍历 schedule 中每条语句:
     analyzeStmt(stmtResult, curTx, stmtId)
       BEGIN → createSnapshot(curTx): 记录当前committedTxs到snapshotTxs
       COMMIT → committedTxs.add(curTx)
       ROLLBACK → DELETE FROM _vt WHERE txid = curTx.id
       SELECT/SELECT_SHARE/SELECT_UPDATE:
         if RU/RC 或 locking read → readCommittedVersion(): vid包含[txId=0, curTx, 所有committed]
         if RR/SER → readSnapshotVersion(): vid包含[txId=0, curTx, snapshot中committed]
         → CREATE TABLE _infer_<txId>_<stmtId> AS SELECT FROM _vt WHERE vid IN (...)
         → 在此临时表上执行SELECT → 获取预期结果
       UPDATE/DELETE/INSERT/REPLACE:
         → affectRowOnView(): 通过前后对比确定影响行ID
         → updateVersionTable(): INSERT新版本到_vt, 设置vid/deleted/txid
  3. 计算最终状态: 从_vt中读取deleted=0的最新版本
  4. 清理辅助表
  5. 返回推断结果用于对比
```

**TX_INFER vs Troc 核心差异**：

| 维度 | TX_INFER | Troc |
|------|----------|------|
| MVCC推断 | 在真实DB上执行SQL操作辅助表 | 纯Java内存推演 |
| WHERE处理 | DBMS自身执行WHERE，不需要解析 | 手工从SQL中提取WHERE子句，在内存中计算受影响行 |
| 版本追踪 | `_vt` 表的 rid/vid/deleted/txid 列 | HashMap<Integer, ArrayList<Version>> |
| 幻读检测 | 通过 deleted 标记 + 版本可见性推断 | 快照前后对比法（在真实DB上执行修改） |
| 准确性 | 利用DBMS原生SQL执行能力 | 受限于WHERE子句解析的准确性 |

### 2.3 WRITE_CHECK 核心算法（MySQLWriteCheckOracle.java 152行）

```
check()
  1. 生成事务列表（1-5个事务，加权偏向2-3个）
  2. 生成 schedule 列表（随机交错）
  3. 对每个 schedule:
     a. 在真实DB上执行 schedule → testResult
     b. 重建DB
     c. 生成 oracle schedule（序列化顺序） → oracleResult
     d. 重建DB
     e. 生成 no-commit-rollback schedule → noCommitResult
     f. 对比:
        SERIALIZABLE → compareAllResults(testResult, oracleResult)
        其他级别 → compareWriteTxResults(testResult, oracleResult)
     g. 对比:
        SERIALIZABLE → compareAllResults(testResult, noCommitResult)
        其他级别 → compareWriteTxResults(testResult, noCommitResult)
```

### 2.4 TxBase 对比逻辑（所有Oracle共用基类）

```java
compareAllResults(testResult, oracleResult)
  for 每条语句执行结果（跳过blocked）:
    1. compareErrors() → 检查error/warning一致性
    2. 如果是SELECT且非死锁:
       compareQueryResult() → 排序后逐行对比
  3. compareFinalDBState() → 对比最终DB状态

// 注意: 没有 Undecided 分类
// 所有不一致一律报告为Bug
```

### 2.5 Schedule 生成（TxTestGenerator.java 100行）

```java
generateTransactions()
  // 从事务数候选 {1,2,2,2,2,3,3,3,4,5} 随机选取
  // 可配置固定数量

genOneSchedule()
  // 随机交错: 随机选一个事务，取其下一条未执行语句
  // 直到所有事务所有语句执行完毕

genSchedules()
  // 循环生成 num 个 schedule
  // 去重: List.contains() → O(n²)
```

### 2.6 Fucci 框架现状

**已完成**：
- 数据结构：Version.java（83行）、View.java（133行）、Lock.java（155行）、LockType.java（9种类型）、LockObject.java（164行）
- Adapter接口 + 4个DBMS实现（MySQL/PG/GaussDB-M/GaussDB-A）
- FucciReducer（500行，Layer 1-2 可用，Layer 3-4 空壳）
- FucciTxTestGenerator（258行，固定2事务，冲突策略）

**空壳/未实现**：
- `FucciMTOracle.simulateMVCCExecution()` → 只创建空结果+计算finalStates，无逐语句MVCC模拟
- `FucciDTOracle.executeOnRefDatabase()` → `return new TxTestExecutionResult()`（空结果）
- `FucciCSOracle.verifySelectResult()` → `return ""`（无约束求解）
- `FucciLockAnalyzer.analyzeLockConflicts()` → 只打印日志
- `FucciMVCCSimulator.verifyMVCC()` → 只打印日志
- `DBMSFucciAdapter.evaluateConstraint()` → `return true`
- `DBMSFucciAdapter.generateStatements()` → 硬编码 `SELECT * FROM table LIMIT 10` + `UPDATE table SET c0 = c0 + 1 WHERE id = 1`

---

## 三、逐项差异对比

### 3.1 方法论差异

| 维度 | Troc | SQLancer |
|------|------|----------|
| **Oracle 方法数** | 1（内存MVCC建模） | 5（WriteCheck/TxInfer/FucciMT/FucciDT/FucciCS） |
| **核心方法论** | 推断预期结果 → 对比实际 | WriteCheck: schedule变分对比; TxInfer: 辅助表推断 |
| **MVCC推断方式** | 纯Java内存推演 | TxInfer: 真实DB上执行辅助SQL（更健壮） |
| **WHERE处理** | 手工解析WHERE提取行ID（脆弱） | DBMS自身执行WHERE（健壮） |

### 3.2 事务生成差异

| 维度 | Troc | SQLancer |
|------|------|----------|
| 事务数量 | **固定2个** | **1-5个可配置**（加权偏向2-3） |
| 语句数量 | 3-6条/事务 | 1-5条/事务 |
| 语句类型 | SELECT/SELECT_SHARE/SELECT_UPDATE/UPDATE/DELETE/INSERT | INSERT/SELECT/SELECT_FOR_UPDATE/SELECT_FOR_SHARE/DELETE/UPDATE |
| SQL生成 | 随机WHERE + 随机表达式 | 依赖TransactionProvider的随机SQL生成器 |
| 冲突构造 | 随机生成（makeConflict确保行重叠） | Fucci有冲突策略（fully-shared/part-shared/tuple） |

### 3.3 Schedule 策略差异

| 维度 | Troc | SQLancer |
|------|------|----------|
| 穷举模式 | ✅ `genAllSubmittedTrace()` 回溯法 | ❌ 无 |
| 随机采样 | ✅ Reservoir Sampling + HashSet去重 | ✅ 简单随机 + List.contains()去重 O(n²) |
| 冲突感知 | ✅ `makeConflict()` 确保行重叠 | ⚠️ Fucci有框架但`generateConflictSchedule()`是简化实现 |

### 3.4 锁建模差异

| 维度 | Troc | SQLancer Fucci（数据结构已定义，分析逻辑为空） |
|------|------|----------|
| 锁类型 | 3种：SHARE/EXCLUSIVE/NONE | 9种：SHARED/EXCLUSIVE/IS/IE/GAP/RECORD/INSERT_INTENTION/NEXT_KEY/AUTO_INC |
| 锁对象 | rowIds + indexes | TABLE/ROW/GAP/NEXT_KEY/INSERT_INTENTION |
| 兼容矩阵 | 简单：EXCLUSIVE与任何锁冲突 | 完整矩阵（GAP vs INSERT_INTENTION冲突等） |
| 分析逻辑 | ✅ 完整运行时锁追踪 | ❌ FucciLockAnalyzer为空壳 |
| Range Lock | ✅ 快照前后对比法动态检测 | ❌ 数据结构有但无检测逻辑 |

### 3.5 Bug 检测逻辑差异

| 维度 | Troc | SQLancer |
|------|------|----------|
| **Undecided分类** | ✅ 3种场景显式跳过 | ❌ 所有差异一律报Bug |
| **Bug类型** | 6种：Missing abort / Unnecessary abort / Inconsistent result / Missing lock / Unnecessary lock / Inconsistent final state | 2种：Inconsistent result / Inconsistent final state |
| **误报控制** | ✅ Undecided 跳过 + skipCase 计数 | ❌ 无过滤 |
| **Bug复现** | cases/目录预定义用例 + 完整复现信息 | WRITE_CHECK_REPRODUCE + FucciReducer简化 |
| **用例简化** | ❌ 无 | ✅ FucciReducer（Layer 1-2 可用） |

### 3.6 DBMS 覆盖差异

| DBMS | Troc | SQLancer WriteCheck | SQLancer TxInfer | SQLancer Fucci |
|------|:----:|:-------------------:|:----------------:|:--------------:|
| MySQL | ✅ | ✅ | ✅ | ⚠️ 空壳 |
| PostgreSQL | ❌ | ✅ | ❌ | ⚠️ 空壳 |
| GaussDB-M | ❌ | ✅ | ✅ | ⚠️ 空壳 |
| GaussDB-A | ❌ | ✅ | ❌ | ⚠️ 空壳 |
| 快照点 | MySQL=SELECT, TiDB=BEGIN | 未建模 | TxInfer: BEGIN创建快照 | FucciIsolation有isSnapshotPoint()但未使用 |

---

## 四、Troc 可借鉴给 SQLancer 的优势功能

### 4.1 Undecided Case 过滤 ⭐⭐⭐ — 投入产出比最高

**Troc 原始逻辑（compareOracles 538-606行）**：

三类 Undecided 场景：
1. 执行死锁但Oracle未预期死锁 → 死锁检测时机因DB实现而异，无法判定
2. 执行abort但Oracle未预期abort + `!shouldNotAbort()` → DBMS合法abort（如死锁回滚）
3. 执行block但Oracle未预期block + `!shouldNotBlock()` → DBMS合法加锁

**SQLancer 现状（TxBase.compareAllResults 41-86行）**：
```java
// 只跳过 blocked 的语句（45行）
if (stmtExecResult.isBlocked()) continue;
// 其他所有差异一律报 Bug → 无 Undecided 分类
```

**借鉴价值**：
- 直接修改 `TxBase.compareAllResults()` 和 `compareWriteTxResults()` 即可
- WRITE_CHECK + TX_INFER + Fucci 全部Oracle共用 TxBase，一处改动全局受益
- 预估减少 30-50% 误报（死锁/abort 差异是最常见误报来源）

**四款DBMS的差异化判断标准**：
| 场景 | MySQL/GaussDB-M | PG/GaussDB-A |
|------|----------------|-------------|
| 死锁 | InnoDB自动回滚代价小的事务 → 标准行为 | deadlock detected + 自动回滚 → 标准行为 |
| SERIALIZABLE abort | N/A（MySQL SER用锁） | SSI序列化异常 → 标准行为 |
| Lock wait timeout | `innodb_lock_wait_timeout` 超时 → 可视为Undecided | `statement_timeout` 超时 → 可视为Undecided |

**实现复杂度**：~80-120行，低风险

---

### 4.2 穷举 Schedule ⭐⭐ — 确定性覆盖保障

**Troc 原始逻辑（ShuffleTool 8-51行）**：
```java
// 回溯法枚举所有 C(n1+n2, n1) 种合法交错
// 2×4条语句 → C(8,4)=70 种（< CheckSize=10 时走采样）
// 2×6条语句 → C(12,6)=924 种
```

**SQLancer 现状（TxTestGenerator.genSchedules 55-70行）**：
```java
// 仅随机采样 + List.contains() 去重（O(n²)）
// 无法保证覆盖所有交错
```

**借鉴价值**：
- SQLancer Fucci 固定2事务（与Troc对齐），穷举空间可控
- 2×5条语句 → C(10,5)=252 种，穷举耗时可控
- 配合 Reservoir Sampling 做 HYBRID 策略：小空间穷举，大空间采样

**对4款DBMS均适用**：穷举是通用的Schedule策略改进，与DBMS无关

**实现复杂度**：~60-80行核心算法 + Reservoir Sampling ~20行

---

### 4.3 快照前后对比法检测 Range Conflict ⭐⭐ — 幻读精确检测

**Troc 原始逻辑（Lock.isRangeConflict 50-66行）**：
```
1. 保存表快照
2. 物化被影响语句的View
3. 记录修改前该语句能看到的行集合（before）
4. 在真实DB上执行修改语句
5. 记录修改后该语句能看到的行集合（after）
6. 恢复表快照
7. before ≠ after → Range Conflict（幻读）
```

**SQLancer 现状**：
- TX_INFER：通过 `_vt` 辅助表的 `deleted` 标记 + 版本可见性来推断幻读（间接方法）
- Fucci：LockType 定义了 GAP_LOCK / INSERT_INTENTION 但无检测逻辑

**借鉴价值分析**：

| 方面 | 分析 |
|------|------|
| **与TX_INFER的关系** | TX_INFER 已能推断幻读（通过辅助表版本追踪），但方式是间接的。Troc的方式是直接验证——在真实DB上执行修改观察行集合变化。两种方式互补 |
| **对MySQL/GaussDB-M** | ✅ 适用。InnoDB RR/SER 使用间隙锁防幻读，Troc方法精确模拟此机制 |
| **对PG/GaussDB-A** | ⚠️ 需谨慎。PG RR 使用 MVCC 快照（不会出现幻读是正确行为）；PG SER 使用 SSI。Troc方法不直接适用 |
| **与Fucci Lock框架结合** | 可在 FucciLockAnalyzer 中集成此检测逻辑，利用已有的 9种LockType 做更精细判断 |

**实现复杂度**：~100-150行（MySQL/GaussDB-M only），需要 SAVEPOINT 或辅助事务做快照保存/恢复

---

### 4.4 幻读删除追踪 ⭐⭐ — RR 隔离级别精度提升

**Troc 原始逻辑（buildTxView 423-448行，useDel=true 时）**：
```java
// 当行被其他事务删除，但当前事务快照中该行仍可见时:
if (version.deleted && curTx.snapView.data.containsKey(rowId)
        && version.tx != curTx && useDel) {
    view.data.put(rowId, curTx.snapView.data.get(rowId));  // 保留快照原始数据
    view.deleted.put(rowId, true);                          // 标记为幻读删除
}
```

**SQLancer 现状**：
- Fucci View.java 已有 `deleted` HashMap 和 `putRow(rowId, rowData, isDeleted)` API（数据结构就绪）
- FucciMTOracle 未使用此机制（空壳）
- TX_INFER 通过 `_vt` 表的 `deleted` 列追踪删除（不同机制）

**借鉴价值分析**：

| 方面 | 分析 |
|------|------|
| **对TX_INFER** | TX_INFER 已有自己的删除追踪机制（`_vt.deleted` 列），不需要Troc方式 |
| **对Fucci-MT** | 如果未来补全Fucci-MT，此机制可直接使用（View数据结构已就绪） |
| **对PG/GaussDB-A** | PG RR 下，被删行在快照中仍可见是**正确行为**。需要区分"快照中仍可见"（PG正确）vs"快照中消失"（Bug） |

**实现复杂度**：~50行（仅在Fucci-MT场景下有价值）

---

### 4.5 DBMS 特定快照点建模 ⭐⭐ — MVCC 准确性基础

**Troc 原始逻辑（isSnapshotPoint 361-371行）**：
```java
case MYSQL: case MARIADB: return stmt.type == SELECT;   // 第一条SELECT
case TIDB:                return stmt.type == BEGIN;     // BEGIN
```

**SQLancer 现状**：
- `FucciIsolation.isSnapshotPoint()` 已定义但 FucciMTOracle 未使用
- TX_INFER 的 `createSnapshot()` 在 BEGIN 时调用（MySQLTxInfer 172行）——这对 MySQL 不完全准确
- 对 PG/GaussDB-A 无影响（PG 快照确实在 BEGIN 建立）

**借鉴价值**：

| DBMS | 正确快照点 | TX_INFER当前 | 差异 |
|------|-----------|------------|------|
| MySQL | 第一条 SELECT（RR/SER） | BEGIN 时创建 | ⚠️ **TX_INFER 在 BEGIN 创建快照，与 InnoDB 实际行为不同** |
| GaussDB-M | 第一条 SELECT（RR/SER） | BEGIN 时创建 | ⚠️ 同上 |
| PostgreSQL | BEGIN | BEGIN 时创建 | ✅ 正确 |
| GaussDB-A | BEGIN | BEGIN 时创建 | ✅ 正确 |

**关键发现**：MySQLTxInfer 的 `createSnapshot()` 在 BEGIN 时调用，但 InnoDB 的 RR 快照实际在**第一条一致性读（SELECT）**时建立。这意味着 TX_INFER 对 MySQL RR 的快照时机建模**不准确**——这是一个潜在误报/漏报源。

**实现复杂度**：~30-50行（修改 TX_INFER 的快照创建时机 + Adapter 扩展）

---

### 4.6 Bug 报告质量增强 ⭐ — 复现信息完善

**Troc 的 Bug Report**：
```
BugReport.java:
  - 建表SQL
  - 初始数据INSERT
  - tx1 隔离级别 + 全部语句
  - tx2 隔离级别 + 全部语句
  - Schedule（交错序列）
  - Bug类型（Missing lock / Inconsistent result / ...）
  - 实际执行结果
  - Oracle推断结果
```

**SQLancer 现有 Bug Report**：
- WRITE_CHECK：事务定义 + Schedule + 差异信息 + 执行结果 + Oracle结果 ✅
- FucciReducer：可简化Bug复现用例 ✅
- Bug类型分类：仅 "Inconsistent result" / "Inconsistent final state" ⚠️ 不够细

**借鉴价值**：
- 引入 Troc 的细粒度 Bug 类型分类（6种 vs 2种）
- 有助于后续统计分析和优先级排序

**实现复杂度**：~30行（扩展Bug报告模板）

---

## 五、不建议借鉴的 Troc 特性

| Troc 特性 | 不借鉴原因 |
|-----------|-----------|
| **内存MVCC建模作为主Oracle** | SQLancer 的 TX_INFER（辅助表SQL执行）方法论更健壮，不需要解析WHERE子句。Troc的内存建模是TX_INFER的低配版 |
| **固定2事务** | SQLancer 支持1-5事务是优势，3+事务交互才能发现某些并发Bug |
| **3种简单锁类型** | SQLancer Fucci 已定义9种更精细的锁类型（GAP/INSERT_INTENTION等），不需要降级 |
| **TiDB支持** | 不在4款DBMS覆盖范围 |

---

## 六、综合评估矩阵

| 借鉴项 | 价值 | 难度 | 对4款DBMS适用性 | 与现有Oracle关系 | 建议 |
|--------|:----:|:----:|:---------------:|:----------------:|:----:|
| **Undecided过滤** | ⭐⭐⭐ | 低 | 全部适用 | 增强TxBase，全Oracle受益 | **实施** |
| **穷举Schedule** | ⭐⭐ | 低 | 全部适用 | 增强TxTestGenerator | **实施** |
| **快照点修正** | ⭐⭐ | 低 | MySQL/GaussDB-M | 修正TX_INFER准确性 | **实施** |
| **Bug类型细化** | ⭐ | 低 | 全部适用 | 增强Bug报告 | **实施** |
| **Range Conflict检测** | ⭐⭐ | 中 | 仅MySQL/GaussDB-M | 补充TX_INFER间接推断的盲区 | 可选 |
| **幻读删除追踪** | ⭐⭐ | 低 | 仅Fucci-MT | 仅Fucci-MT补全时有价值 | 搁置 |
| **内存MVCC Oracle** | ⭐ | 高 | 全部（但重复） | 与TX_INFER功能重叠 | 搁置 |

---

## 七、建议实施路线图

| 阶段 | 事项 | 预估工作量 | 收益 |
|------|------|-----------|------|
| **阶段1** | Undecided 过滤（修改TxBase） | ~80-120行 | 全Oracle误报率降低30-50% |
| **阶段2** | MySQL/GaussDB-M 快照点修正 | ~30-50行 | TX_INFER准确性提升 |
| **阶段3** | 穷举+采样混合Schedule | ~80-100行 | 短事务确定性覆盖保障 |
| **阶段4** | Bug类型细化 | ~30行 | 报告质量提升 |
| **阶段5** | PG/GaussDB-A TX_INFER 补齐 | ~500行×2 | 填补最大覆盖空白 |
| **阶段6** | Range Conflict检测（可选） | ~100-150行 | MySQL/GaussDB-M幻读精确检测 |

---

## 附录A：Troc 源码文件清单

```
src/main/java/troc/
├── TrocChecker.java       (27KB) ← 核心Oracle：MVCC推断+Bug检测
├── TableTool.java         (25KB) ← 表管理+版本数据+锁推断+SQL执行
├── TxnPairExecutor.java   (14KB) ← 真实DB执行器（双线程+阻塞队列）
├── StatementCell.java     (9KB)  ← 语句抽象（含WHERE解析+结果存储）
├── ShuffleTool.java       (4KB)  ← Schedule穷举/采样
├── Lock.java              (3KB)  ← 锁建模+Range Conflict检测
├── Transaction.java       (2KB)  ← 事务状态管理
├── Version.java           (0.5KB)← 版本链数据结构
├── View.java              (0.9KB)← 视图快照数据结构
├── BugReport.java         (1.4KB)← Bug报告生成
├── DBMS.java              (0.7KB)← DBMS枚举（MySQL/MariaDB/TiDB）
├── IsolationLevel.java    (0.9KB)← 隔离级别枚举
├── Options.java           (1.3KB)← CLI参数
├── Main.java              (4.4KB)← 入口
├── mysql/
│   ├── MySQLTable.java    (5.8KB)← MySQL表生成
│   ├── MySQLExprGen.java  (4.5KB)← MySQL表达式生成
│   └── ...
├── common/
│   └── Table.java         (8.2KB)← 表生成基类
└── cases/                         ← 预定义Bug复现用例
    ├── mysql104833.txt, mysql104986.txt, mysql105030.txt, mysql105670.txt
    ├── mariadb26642.txt, mariadb26643.txt, mariadb26671.txt, mariadb27992.txt
    └── tidb28092.txt, tidb28095.txt, tidb28212.txt, tidb30239.txt
```

## 附录B：SQLancer 事务相关文件清单

```
src/sqlancer/common/
├── transaction/
│   ├── Transaction.java, TxStatement.java, TxTestGenerator.java
│   ├── TxTestExecutor.java (222行) ← 通用执行器
│   └── TxTestExecutionResult.java, TxStatementExecutionResult.java
└── oracle/
    └── TxBase.java (202行) ← 通用对比基类

src/sqlancer/mysql/
├── oracle/transaction/
│   ├── MySQLTxInfer.java (666行) ← MVCC辅助表推断
│   ├── MySQLWriteCheckOracle.java (152行) ← 黑箱变分测试
│   └── MySQLFucciOracle.java (64行) ← Fucci包装器
├── transaction/
│   └── MySQLTransactionProvider.java (157行)
└── gen/transaction/
    └── MySQLTxTestGenerator.java (20行)

src/sqlancer/fucci/
├── mvcc/Version.java, View.java
├── lock/Lock.java, LockType.java (9种), LockObject.java
├── oracle/FucciMTOracle.java (209行, 空壳), FucciDTOracle.java (121行, 空壳), FucciCSOracle.java (173行, 空壳)
├── bridge/DBMSFucciAdapter.java + 4个实现
├── transaction/FucciTxTestExecutor.java (210行, LockAnalyzer+MVCCSimulator为空壳)
└── FucciReducer.java (500行, Layer 1-2可用)
```
