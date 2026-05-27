# TX_INFER Oracle 深度分析报告

## 一、TX_INFER Oracle 核心原理

### 1.1 设计理念

TX_INFER Oracle的核心思想是**通过数据库自身实现MVCC推理**，而非在Java层面模拟MVCC行为。

**关键创新**：
- 利用数据库的版本链表能力推断事务执行结果
- 通过辅助表(version table)跟踪每行的版本历史
- 基于事务ID(txid)和版本ID(vid)实现可见性判断

### 1.2 与WriteCheck Oracle的本质差异

| 对比维度 | WriteCheck Oracle | TX_INFER Oracle |
|---------|------------------|-----------------|
| **验证方式** | 调度重排序对比 | MVCC版本链推断 |
| **Oracle生成** | genOracleSchedule | TiDBTxInfer.inferOracle |
| **验证对象** | 执行结果一致性 | 版本可见性一致性 |
| **理论依据** | 可序列化定义 | MVCC可见性规则 |
| **适用范围** | 死锁/回滚检测 | 隔离级别语义验证 |

### 1.3 核心架构组件

```
TiDBTxInfer (推理引擎)
├── 版本链表 (_infer_t_vt)
│   ├── vid: 版本ID (语句序号)
│   ├── rid: 行ID (唯一标识)
│   ├── deleted: 删除标记 (0=存在, 1=已删)
│   └── txid: 事务ID
├── 可见性判断
│   ├── readLatestVersion() - 最终状态
│   ├── readCommittedVersion() - RC隔离
│   └── readSnapshotVersion() - RR/SI隔离
└── 快照管理
    └── snapshotTxs - 记录事务开始时的已提交事务列表
```

## 二、TiDBTxInfer 核心实现分析

### 2.1 版本表结构设计

```sql
-- 辅助版本表结构
CREATE TABLE _infer_t_vt (
    -- 原表所有列
    col1, col2, ...,
    -- 版本控制列
    rid INT,           -- 行唯一标识
    vid INT,           -- 版本ID(语句序号)
    deleted INT,       -- 删除标记
    txid INT           -- 事务ID
);

-- 版本记录示例
-- 事务1: INSERT (vid=1, txid=1, deleted=0)
-- 事务2: UPDATE (vid=2, txid=2, deleted=0)
-- 事务3: DELETE (vid=3, txid=3, deleted=1)
```

### 2.2 可见性判断算法

#### READ COMMITTED隔离级别
```java
// readCommittedVersion(): 每次语句读取最新提交版本
selectTxId = {0, curTx.getId(), committedTxs}

// 可见性条件:
// WHERE rid NOT IN (
//     SELECT rid FROM _vt WHERE deleted=1 AND txid IN (selectTxId)
// )
```

#### REPEATABLE READ/SNAPSHOT隔离级别
```java
// readSnapshotVersion(): 基于事务开始时的快照
selectTxId = {0, curTx.getId()}
// 只包含事务开始前已提交的事务
if snapshotTxs.get(curTx).contains(committedTxId):
    selectTxId.add(committedTxId)

// 可见性条件:
// WHERE rid NOT IN (
//     SELECT rid FROM _vt WHERE deleted=1 AND txid IN (selectTxId)
// )
// ORDER BY vid DESC LIMIT 1  -- 取最新可见版本
```

### 2.3 关键方法流程

#### inferOracle()主流程
```
1. 为每个表添加ROWID列 (唯一行标识)
2. 创建版本辅助表 (_infer_t_vt)
3. 初始化版本表: INSERT所有现有行, vid=0
4. 按调度顺序执行语句分析:
   - BEGIN: createSnapshot() (RR/SI)
   - SELECT: readCommittedVersion()/readSnapshotVersion()
   - INSERT/UPDATE/DELETE: affectRowOnView() + updateVersionTable()
   - COMMIT: committedTxs.add()
   - ROLLBACK: DELETE WHERE txid=curTx.getId()
5. 检查死锁: checkDeadlock()
6. 获取最终状态: getDBFinalState()
7. 返回推理结果: TxTestExecutionResult
```

#### affectRowOnView()写操作处理
```
INSERT:
  1. 执行INSERT到辅助视图
  2. fillEmptyRowIds()获取新行ID
  3. updateVersionTable()添加版本记录

UPDATE:
  1. 添加updated标记列
  2. 执行UPDATE
  3. 获取受影响行ID
  4. updateVersionTable()更新版本

DELETE:
  1. 获取删除前行ID
  2. 执行DELETE
  3. updateVersionTable()标记deleted=1

REPLACE:
  1. inferReplaceStmt()处理
  2. 分解为DELETE+INSERT操作
```

### 2.4 快照机制实现

```java
// createSnapshot(): 事务开始时记录已提交事务
private void createSnapshot(Transaction curTx) {
    List<Integer> snapTx = snapshotTxs.get(curTx);
    snapTx.add(curTx.getId());  // 包含自身
    for (Transaction transaction : committedTxs) {
        snapTx.add(transaction.getId());  // 包含已提交事务
    }
    snapshotTxs.put(curTx, snapTx);
}
```

## 三、三种TiDB事务Oracle对比

### 3.1 Oracle类型矩阵

| Oracle类型 | 验证方法 | 适用场景 | 复杂度 |
|-----------|---------|---------|--------|
| **TiDBTxInferOracle** | MVCC版本推断 | 隔离级别语义验证 | ⭐⭐⭐ |
| **TiDBTxReproduceOracle** | 文件复现+推断 | Bug复现验证 | ⭐⭐ |
| **TiDBTxSerializableReproduceOracle** | 文件复现+调度重排 | 可序列化验证 | ⭐⭐ |
| **TiDBTxWriteSerializableOracle** | 调度重排序 | 可序列化验证 | ⭐ |

### 3.2 验证逻辑差异

```
TiDBTxInferOracle:
  Execution → inferOracle() → compareAllResults()

TiDBTxReproduceOracle:
  File → Execution → inferOracle() → compareAllResults()

TiDBTxSerializableReproduceOracle:
  File → Execution → genOracleSchedule() → compareAllResults()

TiDBTxWriteSerializableOracle:
  Execution → genOracleSchedule() → compareFinalDBState()
```

## 四、各DBMS集成适用性评估

### 4.1 MySQL适用性分析

| 评估项 | 结论 |
|--------|------|
| **MVCC支持** | ✅ InnoDB支持版本链 |
| **版本表创建** | ✅ CREATE TABLE ... LIKE 支持 |
| **ROWID机制** | ⚠️ 需要手动添加列(无隐式ROWID) |
| **SELECT ... ORDER BY LIMIT** | ✅ 支持 |
| **隔离级别** | ✅ RC/RR/SERIALIZABLE语义相同 |

**适配要点**：
1. `CREATE TABLE ... SELECT`语法需改用`CREATE TABLE ... LIKE + INSERT`
2. 版本表主键约束需移除(避免版本冲突)
3. InnoDB间隙锁行为不影响TX_INFER逻辑

**集成方案**：
```java
// MySQLTxInfer可直接复用TiDBTxInfer大部分逻辑
public class MySQLTxInfer extends TiDBTxInfer {
    // 仅需调整CREATE TABLE语法适配
    @Override
    protected void createVersionTable(String tableName) {
        // MySQL支持CREATE TABLE ... AS SELECT
        // 可简化实现
    }
}
```

### 4.2 PostgreSQL适用性分析

| 评估项 | 结论 |
|--------|------|
| **MVCC支持** | ✅ 完整MVCC实现 |
| **版本表创建** | ✅ CREATE TABLE ... LIKE 支持 |
| **ROWID机制** | ✅ 可用CTID或手动列 |
| **隔离级别** | ⚠️ RR实际是Snapshot Isolation |

**关键差异**：
- PostgreSQL的REPEATABLE READ实际是Snapshot Isolation
- SERIALIZABLE使用SSI(Serializable Snapshot Isolation)
- 快照语义不同：事务开始时获取快照，而非每条语句

**适配方案**：
```java
// PostgreSQLTxInfer需调整快照语义
public class PostgreSQLTxInfer extends TiDBTxInfer {
    @Override
    protected void analyzeStmt(TxStatementExecutionResult stmtResult, ...) {
        // PostgreSQL: RR/SI在BEGIN时获取快照
        if (stmt.getType() == StatementType.BEGIN) {
            if (isolationLevel == PostgresIsolationLevel.REPEATABLE_READ
                || isolationLevel == PostgresIsolationLevel.SERIALIZABLE) {
                // 整个事务使用同一快照
                createSnapshotOnce(curTx);
            }
        }
        // SELECT不需要每次刷新快照
    }
}
```

### 4.3 GaussDB-M适用性分析

| 评估项 | 结论 |
|--------|------|
| **MySQL兼容模式** | ✅ 事务语义与MySQL相同 |
| **MVCC支持** | ✅ 继承MySQL InnoDB特性 |
| **版本表创建** | ✅ MySQL语法兼容 |

**适配方案**：
```java
// GaussDBMTxInfer可直接复用MySQLTxInfer
public class GaussDBMTxInfer extends MySQLTxInfer {
    // 无需特殊适配
}
```

### 4.4 GaussDB-A适用性分析

| 评估项 | 结论 |
|--------|------|
| **Oracle兼容模式** | ⚠️ 事务语义差异大 |
| **MVCC实现** | ⚠️ Oracle风格读一致性 |
| **版本表创建** | ✅ 支持CREATE TABLE ... AS |

**关键差异**：
- Oracle兼容模式的读一致性机制独特
- Statement-level读一致性(默认)
- Transaction-level读一致性(SET TRANSACTION READ ONLY)
- Flashback Query能力

**适配方案**：
```java
// GaussDBATxInfer需重新设计
public class GaussDBATxInfer {
    // Oracle兼容模式需调整可见性算法
    // 1. 默认Statement-level读一致性(类似RC)
    // 2. 每条SELECT语句看到语句开始时的已提交数据
    // 3. 不支持标准RR语义

    @Override
    protected void readSnapshotVersion(...) {
        // Oracle兼容模式: Statement-level一致性
        // 每条SELECT独立获取"快照"
    }
}
```

## 五、集成实施建议

### 5.1 优先级排序

| DBMS | 优先级 | 工作量 | 适配难度 |
|------|:------:|:------:|:--------:|
| MySQL/GaussDB-M | **P1** | ⭐低 | ⭐低 |
| PostgreSQL | **P2** | ⭐⭐中 | ⭐⭐中 |
| GaussDB-A | **P3** | ⭐⭐⭐高 | ⭐⭐⭐高 |

### 5.2 实施路线图

#### Phase 1: MySQL/GaussDB-M集成 (低难度)

1. 创建`MySQLTxInfer.java`复用`TiDBTxInfer`逻辑
2. 调整`CREATE TABLE ... SELECT`语法适配
3. 注册到`MySQLOracleFactory`: `TX_INFER`
4. GaussDB-M直接复用

#### Phase 2: PostgreSQL集成 (中等难度)

1. 创建`PostgreSQLTxInfer.java`
2. 调整快照语义适配SI/SSI
3. 处理CTID vs 手动ROWID差异
4. 注册到`PostgresOracleFactory`

#### Phase 3: GaussDB-A集成 (高难度)

1. 分析GaussDB-A Oracle兼容模式读一致性机制
2. 设计适配的可见性判断算法
3. 实现`GaussDBATxInfer.java`
4. 注册到`GaussDBAOracleFactory`

### 5.3 Oracle注册方案

```java
// MySQLOracleFactory.java
public enum MySQLOracleFactory implements OracleFactory<MySQLOracleFactory> {
    // ...existing oracles...
    TX_INFER,  // 新增
}

// PostgresOracleFactory.java
public enum PostgresOracleFactory implements OracleFactory<PostgresOracleFactory> {
    // ...existing oracles...
    TX_INFER,  // 新增
}
```

### 5.4 命令行参数设计

```bash
# 使用TX_INFER Oracle
--oracle TX_INFER

# 指定隔离级别(可选)
--tx-infer-isolation-level RANDOM|RC|RR|SERIALIZABLE
```

## 六、TX_INFER vs FUCCI对比

| 对比维度 | TX_INFER | FUCCI |
|---------|---------|-------|
| **实现方式** | 数据库辅助表 | Java模拟MVCC |
| **性能开销** | ⭐⭐中等(辅助表操作) | ⭐低(内存操作) |
| **准确性** | ⭐⭐⭐高(数据库原生) | ⭐⭐依赖模拟精度 |
| **可移植性** | ⭐⭐依赖SQL语法 | ⭐⭐⭐Java实现 |
| **适用范围** | 隔离级别语义 | MVCC实现细节 |
| **验证类型** | 单Oracle | DT/MT/CS三种 |

## 七、总结

**TX_INFER Oracle核心价值**：
1. 利用数据库自身MVCC能力，避免Java模拟偏差
2. 通过版本链表精确跟踪事务可见性
3. 验证隔离级别语义正确性

**推荐集成策略**：
- MySQL/GaussDB-M: 直接复用，工作量最小
- PostgreSQL: 适配SI/SSI语义，工作量中等
- GaussDB-A: 重新设计，工作量最大

**与现有WriteCheck Oracle关系**：
- TX_INFER专注隔离级别语义验证
- WriteCheck专注调度一致性验证
- 两者互补，可同时启用

---

*分析完成时间: 2026-05-14*