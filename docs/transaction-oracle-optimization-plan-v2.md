# 事务检测 Oracle 优化整改方案

> 基于 Troc vs SQLancer 深度对比分析 v2
> 版本：v1.0 | 日期：2026-06-03
> 覆盖范围：MySQL、PostgreSQL、GaussDB-A、GaussDB-M

---

## 一、背景与动机

SQLancer v2.0.63 已完成 Troc 的 Undecided 分类、穷举 Schedule、快照时机修正等基础借鉴。

但深度对比发现 **3 个高价值检测能力缺失**，导致事务 Oracle 的 Bug 发现率远低于 Troc：

1. **无冲突构造**：随机生成的事务操作目标不重叠，锁冲突概率极低
2. **无 Range Conflict 检测**：MySQL/GaussDB-M RR 下的幻读 Bug 是盲区
3. **FucciMTOracle 精度不足**：不区分 RC/RR 可见性，非 SER 级别几乎无检测能力

本方案按优先级分 4 个阶段实施，预估总工作量 12-18 天。

---

## 二、实施阶段总览

| 阶段 | 名称 | 优先级 | 预估工时 | 依赖 |
|:----:|------|:------:|:--------:|:----:|
| 1 | 冲突构造 (ConflictConstructor) | P0 | 3 天 | 无 |
| 2 | Range Conflict 检测 | P1 | 4 天 | 阶段 1 |
| 3 | PG/GaussDB-A TX_INFER 补齐 | P3 | 5 天 | 无 |
| 4 | FucciMT 精度提升 | P2 | 5 天 | 阶段 1 |

---

## 三、阶段 1：冲突构造（P0，预估 3 天）

### 3.1 问题分析

**现状**：SQLancer 的 `TransactionProvider.generateTransaction()` 为每个事务独立随机生成 SQL 语句。两个事务操作同一行或同一 WHERE 条件的概率极低。

**后果**：
- WRITE_CHECK Oracle 的两个 schedule 执行结果几乎完全相同（无锁冲突 → 无差异）
- TX_INFER 的 MVCC 推理永远走 "无冲突" 路径
- 检测能力接近零

**Troc 的做法**：`TableTool.makeConflict()` 在生成事务后，显式修改两个事务的 WHERE 子句使其指向相同行。

### 3.2 设计方案

#### 3.2.1 新增 `TxConflictConstructor.java`

```
文件：src/sqlancer/common/transaction/TxConflictConstructor.java
职责：在事务生成后，注入共享操作目标以提高锁冲突概率
```

**核心算法**（借鉴 Troc `makeConflict`）：

```
makeConflict(transactions):
  1. 从 transactions 中选取 2 个事务（若 >=2 个）
  2. 从每个事务中筛选 "含 WHERE 子句" 的语句（UPDATE/DELETE/SELECT）
  3. 从两个事务各选 1 条语句 (stmt1, stmt2)
  4. 随机选择冲突策略：
     - Strategy A (50%): 共享 WHERE — 将 stmt2 的 WHERE 子句复制到 stmt1
     - Strategy B (50%): 共享目标行 — 在两条语句的 WHERE 中追加 AND <pk_col> = <value>
  5. 重建 TxSQLQueryAdapter 并通过 setTxSQLQueryAdapter() 替换
```

**SQL 字符串操作**：
```java
// Strategy A: 共享 WHERE
// 原 SQL: "UPDATE t0 SET c0 = 1 WHERE <expr1>"
//        "DELETE FROM t0 WHERE <expr2>"
// 修改: 将 stmt1 的 WHERE 替换为 stmt2 的 WHERE
// 结果: "UPDATE t0 SET c0 = 1 WHERE <expr2>"

// Strategy B: 追加共享行条件
// 原 SQL: "UPDATE t0 SET c0 = 1 WHERE <expr1>"
//        "DELETE FROM t0 WHERE <expr2>"
// 修改: 在两个 WHERE 后追加 " AND <pk_col> = <random_value>"
// 结果: "UPDATE t0 SET c0 = 1 WHERE (<expr1>) AND <pk_col> = 3"
//        "DELETE FROM t0 WHERE (<expr2>) AND <pk_col> = 3"

// 无 WHERE 子句时:
// 原 SQL: "UPDATE t0 SET c0 = 1"
// 修改: 追加 " WHERE <pk_col> = <random_value>"
// 结果: "UPDATE t0 SET c0 = 1 WHERE <pk_col> = 3"
```

**主键列获取**：

```java
// 从 SQL 字符串中提取表名，再查 schema 获取主键列
// MySQL/GaussDB-M: "SHOW KEYS FROM <table> WHERE Key_name = 'PRIMARY'"
// PG/GaussDB-A: 查 pg_index + pg_attribute 系统表
// 简化方案：使用 schema 中第一个表的第一个列（大多数测试表只有一个表）
```

#### 3.2.2 集成到 `TxTestGenerator`

```java
// TxTestGenerator.generateTransactions() 修改：
public List<Transaction> generateTransactions() throws SQLException {
    // ... 现有生成逻辑 ...
    List<Transaction> transactions = ...;

    // 新增：冲突构造（仅当 >=2 个事务时）
    if (transactions.size() >= 2 && globalState.getOptions().useConflictConstruction()) {
        TxConflictConstructor.makeConflict(transactions, globalState);
    }

    return transactions;
}
```

#### 3.2.3 命令行开关

```java
// MainOptions 新增:
@Parameter(names = {"--conflict-construction"}, description = "Enable conflict construction between transactions (Troc-style)")
private boolean conflictConstruction = true;  // 默认开启
```

### 3.3 实施步骤

| 步骤 | 文件 | 修改内容 |
|:----:|------|----------|
| 1 | `common/transaction/TxConflictConstructor.java` | **新建**。冲突构造工具类，含 `makeConflict()`、`extractTableName()`、`injectWhereCondition()` |
| 2 | `common/transaction/TxTestGenerator.java` | 修改 `generateTransactions()` 增加冲突构造调用 |
| 3 | `MainOptions.java` | 新增 `--conflict-construction` 开关 |
| 4 | 单元测试 | 新建 `TestTxConflictConstructor.java` 验证 SQL 修改逻辑 |

### 3.4 验证标准

1. 生成 1000 对事务，统计含 WHERE 语句的事务对中 >=1 对共享操作目标的比例 → 目标 >95%
2. `mvn compile` BUILD SUCCESS
3. 已有单元测试全部通过
4. 对比开启/关闭冲突构造时 WRITE_CHECK 的 Bug 发现率

### 3.5 风险评估

| 风险 | 概率 | 缓解措施 |
|------|:----:|----------|
| SQL 字符串解析失败（复杂 SQL 无标准 WHERE） | 中 | 解析失败时静默跳过，不影响原始语句 |
| 注入的条件使 SELECT 返回空集 | 低 | 随机选取行值，确保表中有匹配行 |
| 冲突构造导致事务过于相似 | 低 | 仅修改 1 对语句，其余保持不变 |

---

## 四、阶段 2：Range Conflict 检测（P1，预估 4 天）

### 4.1 问题分析

**现状**：SQLancer 不具备范围锁 / 幻读检测能力。

**MySQL/GaussDB-M 的 InnoDB 锁行为**：
- RR 级别下，SELECT ... FOR UPDATE 会获取 Next-Key Lock（记录锁 + 间隙锁）
- INSERT 需要获取 Insert Intention Lock，与现有间隙锁冲突
- 幻读场景：Tx1 执行 `SELECT * FROM t WHERE c0 > 5 FOR UPDATE`，Tx2 执行 `INSERT INTO t (c0) VALUES (10)` → 应被阻塞

**Troc 的做法**：`Lock.isRangeConflict()` — 快照前后对比法
1. 保存语句执行前的表状态快照
2. 物化受影响语句的视图到临时表
3. 执行修改语句
4. 比较执行前后的锁对象集合（rowIds + indexes）
5. 集合变化 → 幻读检测 → 冲突

### 4.2 设计方案

#### 4.2.1 新增 `InnoDBRangeConflictDetector.java`

```
文件：src/sqlancer/common/transaction/InnoDBRangeConflictDetector.java
职责：检测 INSERT/UPDATE/DELETE 对范围锁的影响（幻读检测）
适用：MySQL、GaussDB-M（InnoDB 兼容引擎）
```

**核心算法**：

```
detectRangeConflict(modifyStmt, rangeStmt, tableState):
  // modifyStmt: INSERT/UPDATE/DELETE（可能触发幻读的语句）
  // rangeStmt: SELECT FOR UPDATE / SELECT FOR SHARE（持有范围锁的语句）

  1. 获取 rangeStmt 当前可见的行集: beforeSet = executeQuery(rangeStmt)
  2. 在临时环境中执行 modifyStmt
  3. 获取 rangeStmt 执行后的行集: afterSet = executeQuery(rangeStmt)
  4. 比较:
     - beforeSet == afterSet → 无幻读，不冲突
     - beforeSet != afterSet → 幻读发生，冲突
  5. 恢复表状态
```

**简化实现**（避免临时表开销）：

```
detectRangeConflict(modifyStmt, rangeStmt, state):
  1. SAVEPOINT range_check
  2. 执行 modifyStmt
  3. 执行 rangeStmt → afterSet
  4. ROLLBACK TO SAVEPOINT range_check
  5. 执行 rangeStmt → beforeSet
  6. 比较 beforeSet vs afterSet
```

#### 4.2.2 集成到 TX_INFER

```java
// MySQLTxInfer.analyzeStmt() 中增加范围冲突检查：
if (stmt.isWriteType() && isRangeLockActive(otherTx)) {
    boolean conflict = InnoDBRangeConflictDetector.detectRangeConflict(
        stmt, otherTx.getLastRangeSelect(), state);
    if (conflict) {
        // 标记为阻塞（与 Troc isConflict 行为一致）
        markAsBlocked(stmtResult);
    }
}

// isRangeLockActive(): 检查另一事务是否有活跃的 SELECT FOR UPDATE/SHARE
//   - MySQL RR/SER 下 SELECT FOR UPDATE/SHARE 持有 Next-Key Lock
//   - 如果 otherTx 的隔离级别是 RR 或 SER 且最后一条 SELECT 带 FOR UPDATE/SHARE
```

#### 4.2.3 锁类型枚举扩展

```java
// 新增 src/sqlancer/common/transaction/LockType.java
public enum LockType {
    NONE,
    ROW_SHARE,        // SELECT FOR SHARE
    ROW_EXCLUSIVE,    // UPDATE/DELETE/INSERT
    RANGE_SHARE,      // SELECT FOR SHARE (RR/SER 下升级为 Next-Key Lock)
    RANGE_EXCLUSIVE,  // SELECT FOR UPDATE (RR/SER 下升级为 Next-Key Lock)
    INSERT_INTENTION  // INSERT 隐式获取的插入意向锁
}
```

### 4.3 实施步骤

| 步骤 | 文件 | 修改内容 |
|:----:|------|----------|
| 1 | `common/transaction/LockType.java` | **新建**。锁类型枚举 |
| 2 | `common/transaction/InnoDBRangeConflictDetector.java` | **新建**。范围冲突检测器 |
| 3 | `mysql/oracle/transaction/MySQLTxInfer.java` | 修改 `analyzeStmt()` 增加范围冲突检查 |
| 4 | `gaussdbm/oracle/transaction/GaussDBMTxInfer.java` | 同步修改 |
| 5 | 单元测试 | 新建 `TestInnoDBRangeConflictDetector.java` |

### 4.4 验证标准

1. 手动构造幻读测试用例：Tx1 `SELECT * FROM t WHERE c0 > 5 FOR UPDATE` + Tx2 `INSERT INTO t (c0) VALUES (10)` → 应检测为冲突
2. `mvn compile` BUILD SUCCESS
3. 已有 TX_INFER 测试不受影响
4. 在 MySQL/GaussDB-M RR 下运行 TX_INFER，统计新增的 Range Conflict 检测数量

### 4.5 风险评估

| 风险 | 概率 | 缓解措施 |
|------|:----:|----------|
| SAVEPOINT 开销过大 | 中 | 改为内存比较（不实际执行 SQL）|
| InnoDB 锁行为过于复杂（GAP_LOCK vs INSERT_INTENTION） | 高 | 保守策略：仅在能确定冲突时报告，否则归为 Undecided |
| PG/GaussDB-A 的 SSI 不支持范围锁概念 | 确定 | 仅对 MySQL/GaussDB-M 启用 |

---

## 五、阶段 3：PG/GaussDB-A TX_INFER 补齐（P3，预估 5 天）

### 5.1 问题分析

**现状**：TX_INFER 仅支持 MySQL 和 GaussDB-M。PG 和 GaussDB-A 缺少精确的 MVCC 白盒 Oracle。

**PG 与 MySQL 的 MVCC 差异**：

| 维度 | MySQL (InnoDB) | PostgreSQL |
|------|---------------|------------|
| 行标识 | 需要手动添加 `rid` 列 | 内置 `ctid`（物理行 ID） |
| 版本追踪 | 辅助版本表 `_vt` | 可用 `xmin`/`xmax` 系统列 |
| 快照时机 | 第一条 SELECT (RR) | BEGIN 语句 |
| 隔离级别 | RU, RC, RR, SER | RC, RR, SER（无 RU） |
| 多版本 | InnoDB undo log | 原生 MVCC（行级版本） |

### 5.2 设计方案

#### 5.2.1 新增 `PostgresTxInfer.java`

```
文件：src/sqlancer/postgres/oracle/transaction/PostgresTxInfer.java
职责：PG 版 TX_INFER，使用辅助版本表（与 MySQL 版同构）
```

**设计选择**：使用辅助版本表（同 MySQL 版）而非 xmin/xmax 系统列。
理由：xmin/xmax 是事务 ID，需要维护 "事务 ID → 是否已提交" 的映射，且在 RR 下快照语义更复杂。辅助版本表更直观、更易移植。

**与 MySQL 版的差异**：

```java
// 1. 行标识：使用 ctid 或手动添加 rid 列
//    方案 A：添加 rid 列（同 MySQL，统一逻辑）  ← 推荐
//    方案 B：使用 ctid（PG 原生，但 VACUUM 后会变）

// 2. 快照时机：BEGIN 时创建快照（而非首 SELECT）
if (stmtType == PostgresStatementType.BEGIN) {
    createSnapshot(curTx);  // PG 快照在 BEGIN 时建立
}

// 3. 隔离级别：不支持 RU，仅 RC/RR/SER
//    RC: readCommittedVersion() — 同 MySQL
//    RR/SER: readSnapshotVersion() — 同 MySQL

// 4. 错误模式：PG 风格
//    "deadlock detected", "could not serialize", "statement timeout"
```

#### 5.2.2 新增 `PostgresTxInferOracle.java`

```java
// 注册到 PostgresOracleFactory
TX_INFER((state) -> new PostgresTxInferOracle(state))
```

#### 5.2.3 GaussDB-A 同步实现

GaussDB-A 使用 Oracle 兼容模式，MVCC 行为与 PG 类似：
- 快照时机：BEGIN
- 隔离级别：RC, RR, SER（无 RU）
- 错误码：ORA-00060, ORA-04020

新增 `GaussDBATxInfer.java` + `GaussDBATxInferOracle.java`，从 PostgresTxInfer 移植，替换 PG 特有语法为 GaussDB-A 语法。

### 5.3 实施步骤

| 步骤 | 文件 | 修改内容 |
|:----:|------|----------|
| 1 | `postgres/oracle/transaction/PostgresTxInfer.java` | **新建**。PG 版 MVCC 推理 |
| 2 | `postgres/oracle/transaction/PostgresTxInferOracle.java` | **新建**。PG TX_INFER Oracle 包装器 |
| 3 | `PostgresOracleFactory.java` | 注册 TX_INFER |
| 4 | `postgres/transaction/PostgresTxStatement.java` | 确认 isSelectType() 覆盖 SELECT FOR UPDATE/SHARE |
| 5 | `gaussdba/oracle/transaction/GaussDBATxInfer.java` | **新建**。从 PostgresTxInfer 移植 |
| 6 | `gaussdba/oracle/transaction/GaussDBATxInferOracle.java` | **新建**。GaussDB-A TX_INFER Oracle |
| 7 | `GaussDBAOracleFactory.java` | 注册 TX_INFER |
| 8 | 单元测试 | 新建 `PostgresTxInferTest.java`、`GaussDBATxInferTest.java` |

### 5.4 验证标准

1. PG TX_INFER 在 RC/RR/SER 下能正确推理 SELECT 可见性
2. GaussDB-A TX_INFER 同上
3. `mvn compile` BUILD SUCCESS
4. 已有 Oracle 测试不受影响
5. 连接 PG 实例运行 TX_INFER，无异常

### 5.5 风险评估

| 风险 | 概率 | 缓解措施 |
|------|:----:|----------|
| PG ctid 不稳定（VACUUM 后变化） | — | 使用 rid 列方案，不依赖 ctid |
| GaussDB-A 的 MVCC 行为与 PG 有差异 | 中 | 在 GaussDB-A 实例上验证每个隔离级别 |
| 辅助版本表的 SQL 语法在 PG 上不完全兼容 | 低 | 逐条验证辅助 SQL |

---

## 六、阶段 4：FucciMT 精度提升（P2，预估 5 天）

### 6.1 问题分析

**现状**：`FucciMTOracle` 的 MVCC 模拟仅区分 SER vs 其他，不建模 RC/RR 的可见性差异。

**Troc 的做法**：
- RU: `newView()` — 看最新版本
- RC: `buildTxView()` — 看已提交版本
- RR: `snapshotView()` — 首次 SELECT 快照
- SER: 同 RC + SELECT 加 SHARE 锁

### 6.2 设计方案

#### 6.2.1 增强 FucciMTOracle 的 MVCC 建模

```java
// 修改 FucciMTOracle.simulateMVCCExecution()：

// 当前实现（简化）：
if (isolation == SERIALIZABLE) {
    compareAllResults(execResult, simResult);
} else {
    compareWriteTxResults(execResult, simResult);
}

// 目标实现（精确）：
switch (isolation) {
    case READ_UNCOMMITTED:
        // 每步看最新版本（含未提交）
        simulateWithVisibilityRule(VisibilityRule.NEWEST);
        break;
    case READ_COMMITTED:
        // 每步看已提交版本
        simulateWithVisibilityRule(VisibilityRule.COMMITTED);
        break;
    case REPEATABLE_READ:
        // 首次 SELECT 建立快照，后续复用
        simulateWithVisibilityRule(VisibilityRule.SNAPSHOT);
        break;
    case SERIALIZABLE:
        // 同 RC + SELECT 加 SHARE 锁
        simulateWithVisibilityRule(VisibilityRule.COMMITTED_WITH_LOCK);
        break;
}
```

#### 6.2.2 引入 Troc 的 View/Version 模型

将 Troc 的 `View` 和 `snapshotView()`/`buildTxView()` 逻辑移植到 Fucci-MT：

```java
// 新增 src/sqlancer/fucci/mvcc/MVCCSimulator.java
public class MVCCSimulator {
    private Map<String, Map<Integer, List<Version>>> versionChains;

    // 按隔离级别构建视图
    public View buildView(Transaction curTx, Transaction otherTx, IsolationLevel isolation) {
        switch (isolation) {
            case READ_UNCOMMITTED: return newView();
            case READ_COMMITTED:   return buildTxView(curTx, otherTx);
            case REPEATABLE_READ:  return snapshotView(curTx);
            case SERIALIZABLE:     return buildTxView(curTx, otherTx); // + lock check
        }
    }

    // 快照视图（RR）：从 snapTxs 中找到可见版本
    private View snapshotView(Transaction curTx) { /* 同 Troc */ }

    // 已提交视图（RC）：txInit + curTx + committed otherTx
    private View buildTxView(Transaction curTx, Transaction otherTx) { /* 同 Troc */ }

    // 最新视图（RU）：每行取最新版本
    private View newView() { /* 同 Troc */ }
}
```

#### 6.2.3 快照时机集成

```java
// 借鉴 FucciIsolation.getSnapshotPoint()
// MySQL/GaussDB-M: 首 SELECT
// PG/GaussDB-A: BEGIN
```

### 6.3 实施步骤

| 步骤 | 文件 | 修改内容 |
|:----:|------|----------|
| 1 | `fucci/mvcc/MVCCSimulator.java` | **新建**。MVCC 模拟器，移植 Troc 的 View/Version 逻辑 |
| 2 | `fucci/mvcc/VisibilityRule.java` | **新建**。可见性规则枚举 |
| 3 | `fucci/oracle/FucciMTOracle.java` | 修改 `simulateMVCCExecution()` 使用 MVCCSimulator |
| 4 | `fucci/mvcc/Version.java` | 增强版本数据结构（如需） |
| 5 | `fucci/mvcc/View.java` | 增强视图数据结构（如需） |
| 6 | 单元测试 | 新建 `TestMVCCSimulator.java` 验证各隔离级别可见性 |

### 6.4 验证标准

1. RC 下：Tx1 INSERT → Tx2 SELECT 应看到已提交的新行
2. RR 下：Tx1 INSERT → Tx2 SELECT 不应看到 Tx1 的未提交行（快照隔离）
3. SER 下：Tx1 SELECT FOR UPDATE 应阻塞 Tx2 的冲突 UPDATE
4. `mvn compile` BUILD SUCCESS
5. 已有 Fucci 测试不受影响

### 6.5 风险评估

| 风险 | 概率 | 缓解措施 |
|------|:----:|----------|
| Fucci 框架与原生 SQLancer 抽象层次差异大 | 中 | 限制 MVCCSimulator 为独立模块，不修改 Fucci 框架 |
| N>2 事务的可见性建模复杂 | 中 | 仅支持 2 事务（Fucci 本身固定 2 事务） |
| 锁冲突预测精度不足导致高 Undecided 率 | 中 | 锁冲突差异统一归为 Undecided |

---

## 七、验收标准

### 7.1 功能验收

| 验收项 | 验证方式 | 通过标准 |
|--------|----------|----------|
| 冲突构造生效 | 统计 1000 对事务的目标重叠率 | ≥95% |
| Range Conflict 检测 | 手动构造幻读用例 | 正确检测 |
| PG TX_INFER 可用 | 连接 PG 实例运行 | 无异常 + 正确推理 |
| GaussDB-A TX_INFER 可用 | 连接 GaussDB-A 实例运行 | 无异常 + 正确推理 |
| FucciMT RC/RR 精度 | 单元测试各隔离级别 | 可见性正确 |

### 7.2 兼容性验收

| 验收项 | 验证方式 | 通过标准 |
|--------|----------|----------|
| WRITE_CHECK 不受影响 | 运行 4 种 DBMS 的 WriteCheckOracle 单元测试 | 全部通过 |
| TX_INFER (MySQL/GaussDB-M) 不受影响 | 运行已有 TX_INFER 测试 | 全部通过 |
| 非事务 Oracle 不受影响 | 运行 NoREC/PQS/CERT/TLP 等测试 | 全部通过 |
| `mvn compile` | 编译 | BUILD SUCCESS |
| `mvn test` | 单元测试 | ≥46/47（同 v2.0.63 基线） |

### 7.3 效果验收

| 指标 | v2.0.63 基线 | 优化后目标 |
|------|:------------:|:----------:|
| WRITE_CHECK Bug 发现率 | 低（随机碰撞） | 显著提高（确定性冲突） |
| TX_INFER 覆盖 DBMS 数 | 2 (MySQL, GaussDB-M) | 4 (全量) |
| Range Conflict 检测 | 0 | >0 |
| FucciMT 有效隔离级别 | 1 (SER) | 4 (RU/RC/RR/SER) |

---

## 八、版本规划

| 阶段完成后 | 版本号 | 变更内容 |
|:----------:|:------:|----------|
| 阶段 1 | v2.0.64 | 冲突构造 TxConflictConstructor |
| 阶段 2 | v2.0.65 | Range Conflict 检测 |
| 阶段 3 | v2.0.66 | PG/GaussDB-A TX_INFER |
| 阶段 4 | v2.1.0 | FucciMT 精度提升（minor version bump） |

---

## 九、附录：关键代码参考

### A. Troc `makeConflict()` 源码（TableTool.java L196-210）

```java
static void makeConflict(Transaction tx1, Transaction tx2) {
    StatementCell stmt1 = randomStmtWithCondition(tx1);
    StatementCell stmt2 = randomStmtWithCondition(tx2);
    int n = getNewRowId();
    if (Randomly.getBoolean() || n == 0) {
        stmt1.whereClause = stmt2.whereClause;
        stmt1.recomputeStatement();
    } else {
        int rowId = Randomly.getNextInt(1, n);
        try {
            stmt1.makeChooseRow(rowId);
            stmt2.makeChooseRow(rowId);
        } catch (Exception e) {}
    }
}
```

### B. Troc `makeChooseRow()` 源码（StatementCell.java L142-174）

```java
public void makeChooseRow(int rowId) {
    // Phase 1: 检查行是否已匹配 WHERE
    String query = "SELECT * FROM " + table + " WHERE (" + whereClause + ") AND " + rowIdCol + " = " + rowId;
    if (executeQuery(query).hasNext()) return;  // 已匹配

    // Phase 2: 修改 WHERE 使其匹配该行
    query = "SELECT (" + whereClause + ") FROM " + table + " WHERE " + rowIdCol + " = " + rowId;
    Object res = executeQuery(query).next();
    if (res == null) {
        whereClause = "(" + whereClause + ") IS NULL";
    } else {
        whereClause = "NOT (" + whereClause + ")";
    }
    recomputeStatement();
}
```

### C. Troc `isRangeConflict()` 源码（Lock.java L50-66）

```java
static boolean isRangeConflict(StatementCell stmt, StatementCell affectedStmt) {
    if (!useRangeLock(stmt)) return false;
    if (!isModifyType(stmt.type) || !isAffectedType(affectedStmt.type)) return false;

    // 快照对比法
    saveSnapshot("range_conflict");
    materializeView(affectedStmt);           // 物化受影响语句的视图
    LockObject before = getLockObject(stmt); // 修改前的锁对象
    executeStatement(stmt);                  // 执行修改
    LockObject after = getLockObject(stmt);  // 修改后的锁对象
    recoverSnapshot("range_conflict");       // 恢复快照

    return setDiffer(before.rowIds, after.rowIds)
        || setDiffer(before.indexes, after.indexes);
}
```
