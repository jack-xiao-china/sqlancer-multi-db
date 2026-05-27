# Fucci项目深度分析报告

## 1. 项目概述

Fucci是一个专注于**数据库事务隔离级别正确性检测**的模糊测试工具，主要检测数据库系统在并发事务场景下是否存在隔离级别实现缺陷。

### 1.1 基本信息

| 项目属性 | 描述 |
|---------|------|
| 源码地址 | D:\Jack.Xiao\dbtools\Fucci-main |
| 核心功能 | 事务隔离级别Bug检测 |
| 支持DBMS | MySQL, MariaDB, TiDB |
| 开发语言 | Java |
| 核心思想 | 多版本并发控制(MVCC)模拟 + 差异测试/约束求解 |

---

## 2. 主要功能列表

### 2.1 核心功能模块

| 功能模块 | 功能描述 | 实现类 |
|---------|----------|--------|
| **事务对生成** | 随机生成包含冲突操作的并发事务对 | `Table.genTransaction()` |
| **冲突构造** | 确保事务对存在数据竞争 | `TableTool.makeConflict()` |
| **调度执行** | 模拟并发事务的交错执行顺序 | `TxnPairExecutor` |
| **Oracle检测** | 三种隔离级别正确性检测机制 | `FucciChecker` |
| **测试用例简化** | 四层递进式简化策略 | `Reducer` |
| **多版本链管理** | 模拟MVCC版本数据结构 | `Version`, `View` |

### 2.2 支持的隔离级别

- READ UNCOMMITTED
- READ COMMITTED
- REPEATABLE READ
- SERIALIZABLE

### 2.3 支持的语句类型

- SELECT (快照读)
- SELECT FOR UPDATE (当前读+排他锁)
- SELECT FOR SHARE (当前读+共享锁)
- INSERT
- UPDATE
- DELETE
- BEGIN / COMMIT / ROLLBACK

---

## 3. 解决的核心问题

### 3.1 问题描述

数据库系统在实现事务隔离级别时可能存在缺陷，导致：

1. **读写不一致**: 同一事务内多次读取同一数据返回不同结果
2. **写丢失**: 多个事务的更新操作互相覆盖
3. **幻读**: 同一事务内查询返回的行数发生变化
4. **锁机制缺陷**: 锁获取/释放时机不正确
5. **死锁处理缺陷**: 死锁检测或回滚机制异常

### 3.2 问题本质

传统数据库测试工具（如SQLancer的TLP/NoREC等）主要关注**单语句**的正确性，而**并发事务**的正确性涉及：

- 多语句的交错执行顺序
- 锁机制的语义正确性
- MVCC版本可见性规则
- 隔离级别语义一致性

这些问题需要**事务级别的测试框架**来系统性检测。

---

## 4. 核心思路和算法

### 4.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      Fuzzi Testing Loop                          │
├─────────────────────────────────────────────────────────────────┤
│  1. Table Generation → 2. Transaction Generation → 3. Conflict  │
│          ↓                    ↓                      ↓          │
│  4. Schedule Sampling → 5. Execute → 6. Oracle Check → 7. Reduce │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 三种Oracle检测机制

Fucci实现了三种独立的隔离级别检测Oracle：

#### 4.2.1 DT Oracle (Differential Testing)

**原理**: 使用不同数据库作为参照，对比执行结果

```
执行流程:
┌──────────────┐     ┌──────────────┐
│  Target DB   │     │  Reference DB│
│   (MySQL)    │     │  (MariaDB)   │
├──────────────┤     ├──────────────┤
│ Execute Tx   │ ←→  │ Execute Tx   │
│   Schedule   │     │   Schedule   │
├──────────────┤     ├──────────────┤
│   Result1    │ ←→  │   Result2    │
└──────────────┘     └──────────────┘
        ↓                    ↓
        └────── Compare ──────┘
                  ↓
           Bug Detection
```

**实现代码** (`FucciChecker.java:246-313`):
```java
// 1. 正常执行的结果
TxnPairExecutor executor1 = new TxnPairExecutor(scheduleClone(schedule), tx1, tx2, false);
TxnPairResult execResult1 = executor1.getResult();

// 2. 通过参照数据库获取的结果
TxnPairExecutor executor2 = new TxnPairExecutor(scheduleClone(schedule), tx1, tx2, true);
TxnPairResult execResult2 = executor2.getResult();

// 3. 对比结果检测Bug
return compareOracles(execResult1, execResult2);
```

**优势**: 不依赖理论模型，直接对比实际行为
**劣势**: 需要部署多个数据库实例，参照数据库本身可能有Bug

#### 4.2.2 MT Oracle (Metamorphic Testing)

**原理**: 通过变形测试，在相同数据上构造等价执行序列

**核心思想**:
- 对于检测到阻塞/死锁的执行，构造一个"无阻塞"的等价序列
- 比较两个序列的最终状态

**实现代码** (`FucciChecker.java:696-743`):
```java
void updateVersionByExecOnTable(StatementCell stmt, Transaction curTx, Transaction otherTx) {
    // 使用数据库实际执行来获取更新后的状态
    View curView = newestView();
    TableTool.viewToTable(curView);
    boolean success = TableTool.executeOnTable(stmt.statement);
    // 将执行结果更新到多版本链
    View newView = TableTool.tableToView();
    // ...
}
```

#### 4.2.3 CS Oracle (Constraint Solving)

**原理**: 通过约束求解模拟MVCC行为，不依赖数据库执行

**核心算法**:

1. **多版本链初始化** (`FucciChecker.java:425-502`)
```java
private TxnPairResult inferOracleMVCC(ArrayList<StatementCell> schedule) {
    vData = TableTool.initVersionData();  // 初始化版本链
    
    for (StatementCell stmt : schedule) {
        boolean blocked = analyzeStmt(stmt, curTx, otherTx);
        if (blocked) {
            curTx.blockedStatements.add(stmt);
        } else {
            // 在多版本链上执行语句
            updateVersion(stmt, curTx, otherTx);
        }
    }
    return result;
}
```

2. **版本可见性规则** (`FucciChecker.java:566-591`)
```java
// SELECT语句在不同隔离级别下的视图构建
if (curTx.isolationlevel == IsolationLevel.REPEATABLE_READ) {
    view = snapshotView(curTx);  // 读快照
} else if (curTx.isolationlevel == IsolationLevel.READ_UNCOMMITTED) {
    view = newestView();         // 读最新数据
} else {
    view = buildTxView(curTx, otherTx, false); // 读已提交数据
}
```

3. **约束求解求值** (`FucciChecker.java:892-917`)
```java
ArrayList<Object> queryOnViewByCostraintSolver(StatementCell stmt, View view) {
    for (int rowId : view.data.keySet()) {
        // 构造元组映射
        HashMap<String, Object> tupleMap = new HashMap<>();
        // 使用表达式求值判断WHERE条件
        MySQLConstant constant = stmt.predicate.getExpectedValue(tupleMap);
        if (!constant.isNull() && constant.asBooleanNotNull()) {
            res.add(...); // 加入结果集
        }
    }
    return res;
}
```

### 4.3 锁冲突分析算法

**实现位置**: `Lock.java:23-44`

```java
boolean isConflict(Transaction otherTx) {
    for (Lock otherLock : otherTx.locks) {
        // 排他锁与任何锁冲突
        if (this.type == LockType.EXCLUSIVE || otherLock.type == LockType.EXCLUSIVE) {
            // 检查行ID或索引交集
            if (setIntersect(this.lockObject.rowIds, otherLock.lockObject.rowIds)
                    || setIntersect(this.lockObject.indexes, otherLock.lockObject.indexes)) {
                return true;
            }
        }
        // 间隙锁检测 (RR/SERIALIZABLE隔离级别)
        if (useRangeLock(this.stmt) && isRangeConflict(otherLock.stmt, this.stmt)) {
            return true;
        }
    }
    return false;
}
```

### 4.4 测试用例简化算法 (Reducer)

**四层递进简化策略** (`Reducer.java:200-319`):

```
Level 1: 语句删除层 - 删除不必要的语句
Level 2: 语句简化层 - 简化WHERE条件、删除列
Level 3: 表达式简化层 - 简化复杂表达式
Level 4: 常量简化层 - 替换复杂常量
```

**三种Order Selector**:
- RandomOrderSelector: 随机选择简化顺序
- ProbabilityTableBasedOrderSelector: 基于成功概率表选择
- EpsilonGreedyOrderSelector: ε-贪婪策略平衡探索与利用

### 4.5 冲突构造策略

**实现位置**: `TableTool.java:371-423`

四种冲突构造方法:
1. **fully-shared-filters**: 两个事务使用完全相同的WHERE条件
2. **partially-shared-filters**: WHERE条件部分重叠 (OR连接)
3. **conflict-tuple-containment**: 强制修改同一行数据
4. **random**: 随机选择上述策略之一

---

## 5. 与SQLancer WriteCheck对比分析

### 5.1 共通性

| 共通点 | Fucci | SQLancer WriteCheck |
|--------|-------|---------------------|
| **测试目标** | 事务隔离级别Bug | 事务隔离级别Bug |
| **测试对象** | MySQL/MariaDB/TiDB | MySQL/Postgres/GaussDB/CockroachDB |
| **核心机制** | 多事务并发执行 | 多事务并发执行 |
| **调度方式** | 随机交错执行顺序 | 随机交错执行顺序 |
| **检测方式** | 结果对比 | 结果对比 |
| **Oracle类型** | DT/MT/CS三种 | 自身执行对比 |

### 5.2 关键差异对比

| 维度 | Fucci | SQLancer WriteCheck |
|------|-------|---------------------|
| **Oracle机制** | 三种独立Oracle，可交叉验证 | 单一Oracle：执行结果与自身推导对比 |
| **参照方式** | DT使用外部数据库参照 | 无外部参照，仅内部序列对比 |
| **MVCC模拟** | CS Oracle完整模拟MVCC行为 | 无MVCC模拟 |
| **约束求解** | 使用表达式求值判断WHERE条件 | 无约束求解 |
| **锁分析** | 精细的锁冲突检测(行锁+间隙锁) | 基于超时的阻塞检测 |
| **冲突构造** | 显式构造数据冲突 | 随机生成，无显式冲突构造 |
| **用例简化** | 四层递进简化框架 | 无简化机制 |
| **事务数量** | 固定2个事务 | 支持1-5个事务 |
| **快照管理** | 完整的快照点和版本链管理 | 无快照概念 |

### 5.3 技术架构对比

#### Fucci架构特点

```
┌───────────────────────────────────────────────────────────────┐
│                     Fucci Architecture                         │
├───────────────────────────────────────────────────────────────┤
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐        │
│  │ DT Oracle   │    │ MT Oracle   │    │ CS Oracle   │        │
│  │ (Reference) │    │ (Metamorph) │    │ (MVCC Sim)  │        │
│  └──────┬──────┘    └──────┬──────┘    └──────┬──────┘        │
│         │                  │                  │                │
│         └──────────────────┼──────────────────┘                │
│                            ↓                                   │
│                  ┌─────────────────┐                          │
│                  │  Result Compare │                          │
│                  └─────────────────┘                          │
│                            ↓                                   │
│                  ┌─────────────────┐                          │
│                  │    Reducer      │                          │
│                  │  (4-Level Simp) │                          │
│                  └─────────────────┘                          │
└───────────────────────────────────────────────────────────────┘
```

#### SQLancer WriteCheck架构特点

```
┌───────────────────────────────────────────────────────────────┐
│                SQLancer WriteCheck Architecture                │
├───────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐  │
│  │              Transaction Generator                       │  │
│  │          (Random 1-5 transactions)                       │  │
│  └─────────────────────────────────────────────────────────┘  │
│                            ↓                                   │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │              Schedule Generator                          │  │
│  │          (Random interleaving order)                     │  │
│  └─────────────────────────────────────────────────────────┘  │
│                            ↓                                   │
│  ┌────────────────────────┐   ┌────────────────────────────┐  │
│  │   Execute Schedule     │   │   Generate Oracle Schedule │  │
│  │   (Actual DB Execution)│   │   (Based on exec result)   │  │
│  └────────────────────────┘   └────────────────────────────┘  │
│              ↓                              ↓                   │
│              └────────── Compare ───────────┘                   │
│                            ↓                                   │
│                  ┌─────────────────┐                          │
│                  │   Bug Detection │                          │
│                  └─────────────────┘                          │
└───────────────────────────────────────────────────────────────┘
```

### 5.4 Oracle对比详解

#### SQLancer WriteCheck Oracle机制

**核心代码** (`MySQLWriteCheckOracle.java:113-151`):

```java
// Oracle1: 根据执行结果生成等价序列
public List<TxStatement> genOracleSchedule(TxTestExecutionResult testResult) {
    for (TxStatementExecutionResult stmtResult : testResult.getStatementExecutionResults()) {
        if (stmtResult.reportDeadlock()) {
            // 死锁时：将该事务所有语句加入，然后ROLLBACK
            for (TxStatement stmt : stmtTx.getStatements()) {
                oracleSchedule.add(stmt);
            }
            oracleSchedule.add(rollbackStmt);
        } else if (stmtResult.getStatement().isCommitOrRollback()) {
            // 提交/回滚时：加入该事务所有语句
            oracleSchedule.addAll(stmtTx.getStatements());
        }
    }
    return oracleSchedule;
}

// Oracle2: 无Commit/Rollback的序列
public List<TxStatement> genOracleWithoutCommitAndRollbackSchedule(...) {
    // 移除BEGIN和COMMIT，只保留中间操作
}
```

**对比**:
| 特性 | Fucci CS Oracle | SQLancer WriteCheck |
|------|------------------|---------------------|
| MVCC版本管理 | 外部版本链vData模拟 | 无 |
| 快照点检测 | 根据DBMS类型检测 | 无 |
| 锁分析 | 精确的锁对象计算 | 超时检测阻塞 |
| WHERE条件求值 | 表达式约束求解 | 直接数据库执行 |

### 5.5 优劣分析

#### Fucci优势

1. **多种Oracle交叉验证**: DT/MT/CS三种机制可独立或组合使用，提高检测准确性
2. **MVCC精确模拟**: CS Oracle完整模拟MVCC版本链和可见性规则，不依赖数据库执行
3. **精细锁分析**: 支持行锁、间隙锁(Gap Lock)等复杂锁机制
4. **测试用例简化**: 四层简化机制有效减少Bug报告复杂度
5. **冲突定向构造**: 显式构造数据竞争，提高Bug触发概率
6. **表达式约束求解**: WHERE条件求值不依赖数据库，可发现逻辑缺陷

#### Fucci劣势

1. **事务数量固定**: 仅支持2个事务，无法检测更多事务参与的复杂场景
2. **DBMS支持有限**: 仅MySQL/MariaDB/TiDB，扩展性受限
3. **参照数据库依赖**: DT Oracle需要部署多个数据库实例
4. **约束求解限制**: 仅支持特定表达式类型，复杂SQL可能无法求解
5. **间隙锁实现不完整**: 代码注释表明间隙锁检测存在缺陷

#### SQLancer WriteCheck优势

1. **多DBMS支持**: MySQL/Postgres/GaussDB/CockroachDB等，架构设计更通用
2. **事务数量灵活**: 支持1-5个事务，测试场景更丰富
3. **架构简洁**: 无外部依赖，部署简单
4. **框架通用性强**: 继承TxBase抽象类，易于扩展新DBMS

#### SQLancer WriteCheck劣势

1. **单一Oracle**: 缺乏交叉验证机制
2. **无MVCC模拟**: 完全依赖数据库执行，无法发现MVCC实现缺陷
3. **无冲突构造**: 随机生成导致冲突概率低
4. **无用例简化**: Bug报告可能过于复杂
5. **锁检测粗粒度**: 仅通过超时判断阻塞，无法精确分析锁语义

---

## 6. 原生WriteCheck项目分析

### 6.1 项目概述

原生WriteCheck是一个专注于检测**写特定可串行化违规**（write-specific serializability violations）的事务测试工具，基于SQLancer框架扩展开发。

| 项目属性 | 描述 |
|---------|------|
| 源码地址 | D:\Jack.Xiao\dbtools\WriteCheck-main |
| 核心功能 | 写操作可串行化违规检测 |
| 支持DBMS | CockroachDB (主), MySQL, MariaDB, TiDB, PostgreSQL, SQLite |
| 开发语言 | Java |
| 核心思想 | 序列化执行对比验证 |
| 已发现Bug | 13个（MySQL 1, MariaDB 3, TiDB 9） |

### 6.2 原生WriteCheck核心功能

#### 6.2.1 支持的Oracle类型

原生WriteCheck提供两种Oracle：

| Oracle类型 | 功能描述 | 实现类 |
|-----------|----------|--------|
| **WRITE_CHECK** | 随机生成事务并检测 | `CockroachDBWriteCheckOracle` |
| **WRITE_CHECK_REPRODUCE** | 从文件复现Bug | `CockroachDBWriteCheckReproduceOracle` |

#### 6.2.2 支持的隔离级别

**CockroachDB**: 仅支持 `SERIALIZABLE` 隔离级别

```java
public enum CockroachDBIsolationLevel implements IsolationLevel {
    SERIALIZABLE("SERIALIZABLE");
}
```

**MySQL/MariaDB/TiDB**: 支持多种隔离级别（从detected-bugs推测）

#### 6.2.3 支持的语句类型

- SELECT
- INSERT
- UPDATE
- DELETE
- UPSERT (CockroachDB特有)
- BEGIN / COMMIT / ROLLBACK

### 6.3 原生WriteCheck Oracle机制详解

#### 6.3.1 WRITE_CHECK Oracle流程

```java
// CockroachDBWriteCheckOracle.java
public void check() throws SQLException {
    // 1. 生成事务列表 (1-5个事务)
    List<Transaction> transactions = txTestGenerator.generateTransactions();
    
    // 2. 生成调度序列 (多个随机交错顺序)
    List<List<TxStatement>> schedules = txTestGenerator.genSchedules(transactions);
    
    for (List<TxStatement> schedule : schedules) {
        // 3. 执行调度
        TxTestExecutionResult testResult = testExecutor.execute();
        
        // 4. 生成Oracle调度序列
        List<TxStatement> oracleSchedule = genOracleSchedule(testResult);
        TxTestExecutionResult oracleResult = oracleExecutor.execute();
        
        // 5. 生成无Commit/Rollback的Oracle序列
        List<TxStatement> oracleWithoutCR = genOracleWithoutCommitAndRollbackSchedule(testResult);
        TxTestExecutionResult oracleWithoutCRResult = executor.execute();
        
        // 6. 对比结果
        compareAllResults(testResult, oracleResult);
        compareAllResults(testResult, oracleWithoutCRResult);
    }
}
```

#### 6.3.2 Oracle调度生成逻辑

**Oracle1: 有Commit/Rollback的序列**

```java
public List<TxStatement> genOracleSchedule(TxTestExecutionResult testResult) {
    for (TxStatementExecutionResult stmtResult : testResult.getStatementExecutionResults()) {
        if (!stmtResult.isBlocked()) {
            if (stmtResult.reportDeadlock() || stmtResult.reportRollback()) {
                // 死锁/回滚：加入该事务所有已执行语句 + ROLLBACK
                for (TxStatement stmt : stmtTx.getStatements()) {
                    if (!stmt.isEndTxType()) {
                        oracleSchedule.add(stmt);
                    }
                    if (stmtResult.getStatement().equals(stmt)) break;
                }
                oracleSchedule.add(new CockroachDBTxStatement(stmtTx, "ROLLBACK"));
            } else if (stmtResult.getStatement().isCommitOrRollback()) {
                // 正常提交：加入该事务全部语句
                oracleSchedule.addAll(stmtTx.getStatements());
            }
        }
    }
}
```

**Oracle2: 无Commit/Rollback的序列**

```java
public List<TxStatement> genOracleWithoutCommitAndRollbackSchedule(TxTestExecutionResult testResult) {
    for (TxStatementExecutionResult stmtResult : testResult.getStatementExecutionResults()) {
        if (!stmtResult.isBlocked() && !stmtResult.reportDeadlock() 
            && stmtResult.getStatement().isCommit()) {
            // 移除BEGIN和COMMIT，保留中间操作
            List<TxStatement> txStatements = new ArrayList<>(stmtTx.getStatements());
            txStatements.remove(0);   // 移除BEGIN
            txStatements.remove(txStatements.size() - 1); // 移除COMMIT
            oracleSchedule.addAll(txStatements);
        }
    }
}
```

#### 6.3.3 WRITE_CHECK_REPRODUCE Oracle

用于从文件复现已发现的Bug：

```java
public void check() throws SQLException {
    // 从文件读取
    Scanner scanner = new Scanner(new File(options.getCaseFile()));
    
    // 解析建表语句和初始化数据
    List<Query<?>> dbInitQueries = prepareTableFromScanner(scanner);
    
    // 解析事务定义
    for (int i = 0; i < nrTransactions; i++) {
        transactions.add(readTransactionFromScanner(scanner));
    }
    
    // 解析调度顺序
    String scheduleStr = readOrderFromScanner(scanner);
    List<TxStatement> schedule = checkOrder(scheduleStr, transactions);
    
    // 对所有隔离级别执行检测
    for (CockroachDBIsolationLevel level : CockroachDBIsolationLevel.values()) {
        // 执行并对比...
    }
}
```

### 6.4 原生WriteCheck事务生成

```java
public static Transaction generateTransaction(CockroachDBGlobalState state) {
    Transaction tx = new Transaction(state.createConnection());
    
    int stmtNum = Randomly.getNotCachedInteger(1, 6); // 1-5条语句
    
    // BEGIN
    tx.addStatement(new CockroachDBTxStatement(tx, BEGIN.getQuery(state)));
    
    // 事务体
    CockroachDBTransactionAction[] actions = {INSERT, SELECT, DELETE, UPDATE};
    for (int i = 1; i <= stmtNum; i++) {
        CockroachDBTransactionAction action = actions[random];
        SQLQueryAdapter query = action.getQuery(state);
        // 添加预期错误
        errors.add("TransactionAbortedError");
        errors.add("WriteTooOldError");
        errors.add("RETRY_SERIALIZABLE");
        tx.addStatement(new CockroachDBTxStatement(tx, query));
    }
    
    // COMMIT 或 ROLLBACK
    SQLQueryAdapter endQuery = Randomly.getBoolean() ? COMMIT : ROLLBACK;
    tx.addStatement(new CockroachDBTxStatement(tx, endQuery));
    
    return tx;
}
```

### 6.5 原生WriteCheck发现的Bug列表

| ID | DBMS | Bug链接 | 状态 |
|----|------|---------|------|
| 1 | MySQL | http://bugs.mysql.com/104833 | New |
| 2 | MariaDB | https://jira.mariadb.org/browse/MDEV-30793 | New |
| 3 | MariaDB | https://jira.mariadb.org/browse/MDEV-30835 | New |
| 4 | MariaDB | https://jira.mariadb.org/browse/MDEV-26643 | Fixed |
| 5 | TiDB | https://github.com/pingcap/tidb/issues/54497 | New |
| 6 | TiDB | https://github.com/pingcap/tidb/issues/54411 | New |
| 7 | TiDB | https://github.com/pingcap/tidb/issues/39972 | New |
| 8 | TiDB | https://github.com/pingcap/tidb/issues/39976 | New |
| 9 | TiDB | https://github.com/pingcap/tidb/issues/42121 | Fixed |
| 10 | TiDB | https://github.com/pingcap/tidb/issues/42486 | New |
| 11 | TiDB | https://github.com/pingcap/tidb/issues/42889 | Duplicate |
| 12 | TiDB | https://github.com/pingcap/tidb/issues/28095 | Duplicate |
| 13 | TiDB | https://github.com/pingcap/tidb/issues/28092 | New |

---

## 7. 三方工具对比分析 (Fucci vs 原生WriteCheck vs SQLancer WriteCheck)

### 7.1 总体对比概览

| 维度 | Fucci | 原生WriteCheck | SQLancer WriteCheck |
|------|-------|----------------|---------------------|
| **项目性质** | 独立项目 | SQLancer分支扩展 | SQLancer内置Oracle |
| **测试目标** | 隔离级别Bug | 写可串行化违规 | 事务隔离级别Bug |
| **核心DBMS** | MySQL/MariaDB/TiDB | CockroachDB(主) | MySQL/Postgres/GaussDB等 |
| **Oracle数量** | 3种(DT/MT/CS) | 2种(WC/WCR) | 1种 |
| **隔离级别** | 4种全部 | SERIALIZABLE(主) | 多种 |
| **MVCC模拟** | ✅ 完整 | ❌ 无 | ❌ 无 |
| **冲突构造** | ✅ 显式定向 | ❌ 随机 | ❌ 随机 |
| **用例简化** | ✅ 四层简化 | ❌ 无 | ❌ 无 |
| **Bug复现** | ✅ 支持 | ✅ REPRODUCE Oracle | ❌ 无专门机制 |
| **事务数量** | 固定2个 | 1-5个 | 1-5个 |
| **发现Bug数** | 未统计 | 13个 | 未统计 |

### 7.2 架构对比

#### Fucci架构 (独立项目)

```
┌───────────────────────────────────────────────────────────────┐
│                     Fucci Architecture                         │
│                   (Standalone Project)                         │
├───────────────────────────────────────────────────────────────┤
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐        │
│  │ DT Oracle   │    │ MT Oracle   │    │ CS Oracle   │        │
│  │ (Reference) │    │ (Metamorph) │    │ (MVCC Sim)  │        │
│  └──────┬──────┘    └──────┬──────┘    └──────┬──────┘        │
│         └──────────────────┼──────────────────┘                │
│                            ↓                                   │
│                  ┌─────────────────┐                          │
│                  │  Result Compare │                          │
│                  └─────────────────┘                          │
│                            ↓                                   │
│                  ┌─────────────────┐                          │
│                  │    Reducer      │                          │
│                  │  (4-Level Simp) │                          │
│                  └─────────────────┘                          │
└───────────────────────────────────────────────────────────────┘
```

#### 原生WriteCheck架构 (SQLancer分支)

```
┌───────────────────────────────────────────────────────────────┐
│               Original WriteCheck Architecture                 │
│                  (SQLancer Fork Extension)                     │
├───────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐  │
│  │              Transaction Generator                       │  │
│  │          (Random 1-5 transactions)                       │  │
│  └─────────────────────────────────────────────────────────┘  │
│                            ↓                                   │
│  ┌────────────────────────┐   ┌────────────────────────────┐  │
│  │ WRITE_CHECK Oracle     │   │ WRITE_CHECK_REPRODUCE      │  │
│  │ (Random Generation)    │   │ (FromFile Reproduction)    │  │
│  └────────────────────────┘   └────────────────────────────┘  │
│              ↓                              ↓                   │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │          Oracle Schedule Generation                      │  │
│  │  - With Commit/Rollback                                  │  │
│  │  - Without Commit/Rollback                               │  │
│  └─────────────────────────────────────────────────────────┘  │
│                            ↓                                   │
│                  ┌─────────────────┐                          │
│                  │   Result Compare│                          │
│                  └─────────────────┘                          │
└───────────────────────────────────────────────────────────────┘
```

#### SQLancer WriteCheck架构 (内置Oracle)

```
┌───────────────────────────────────────────────────────────────┐
│             SQLancer WriteCheck Architecture                   │
│              (Built-in Oracle in SQLancer)                     │
├───────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐  │
│  │              Transaction Generator                       │  │
│  │          (Random 1-5 transactions)                       │  │
│  └─────────────────────────────────────────────────────────┘  │
│                            ↓                                   │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │              Schedule Generator                          │  │
│  │          (Random interleaving order)                     │  │
│  └─────────────────────────────────────────────────────────┘  │
│                            ↓                                   │
│  ┌────────────────────────┐   ┌────────────────────────────┐  │
│  │   Execute Schedule     │   │   Generate Oracle Schedule │  │
│  └────────────────────────┘   └────────────────────────────┘  │
│              └────────── Compare ───────────┘                  │
│                            ↓                                   │
│                  ┌─────────────────┐                          │
│                  │   Bug Detection │                          │
│                  └─────────────────┘                          │
└───────────────────────────────────────────────────────────────┘
```

### 7.3 Oracle机制深度对比

| Oracle特性 | Fucci DT | Fucci MT | Fucci CS | 原生WriteCheck | SQLancer WC |
|-----------|----------|----------|----------|----------------|-------------|
| **参照方式** | 外部DB | 内部变形 | 约束求解 | 内部序列对比 | 内部序列对比 |
| **依赖数据库** | 需2个DB | 单DB | 无需DB | 单DB | 单DB |
| **MVCC模拟** | ❌ | 部分 | ✅完整 | ❌ | ❌ |
| **锁分析** | 实际锁 | 实际锁 | 模拟锁 | 超时检测 | 超时检测 |
| **WHERE求值** | DB执行 | DB执行 | 约束求解 | DB执行 | DB执行 |
| **可串行化检测** | ✅ | ✅ | ✅ | ✅专精 | ✅ |
| **其他隔离级别** | ✅ | ✅ | ✅ | 支持(次要) | ✅ |

### 7.4 检测能力对比

#### Fucci检测能力

- ✅ 读写不一致
- ✅ 写丢失
- ✅ 幻读
- ✅ 锁机制缺陷（行锁+间隙锁）
- ✅ MVCC版本可见性错误
- ✅ 死锁处理缺陷
- ✅ 快照点语义错误

#### 原生WriteCheck检测能力

- ✅ 写可串行化违规（专精）
- ✅ 事务中止语义错误
- ✅ Commit/Rollback语义
- ✅ 死锁检测缺陷
- ⚠️ 其他隔离级别（支持但非核心）

#### SQLancer WriteCheck检测能力

- ✅ 事务隔离级别错误
- ✅ 写语义不一致
- ✅ 最终状态不一致
- ✅ 死锁处理缺陷
- ⚠️ 锁分析粗粒度

### 7.5 优劣势综合评估

#### Fucci

| 优势 | 劣势 |
|------|------|
| 3种Oracle交叉验证 | 仅支持2个事务 |
| 完整MVCC模拟 | DBMS支持有限 |
| 精细锁分析(行锁+间隙锁) | DT需部署多DB |
| 四层用例简化 | 约束求解有局限 |
| 显式冲突构造 | 间隙锁实现不完整 |
| WHERE约束求解 | 无已发现Bug统计 |

#### 原生WriteCheck

| 优势 | 劣势 |
|------|------|
| 专精写可串行化检测 | 仅SERIALIZABLE为主 |
| REPRODUCE Oracle复现 | 无MVCC模拟 |
| 1-5事务灵活 | 无用例简化 |
| 已发现13个Bug | 无冲突定向构造 |
| 基于SQLancer扩展 | CockroachDB为主 |
| 无外部依赖 | 锁分析粗粒度 |

#### SQLancer WriteCheck

| 优势 | 劣势 |
|------|------|
| 多DBMS通用架构 | 单一Oracle |
| 1-5事务灵活 | 无MVCC模拟 |
| 内置无外部依赖 | 无冲突构造 |
| 易扩展新DBMS | 无用例简化 |
| 支持多种隔离级别 | 锁分析粗粒度 |

### 7.6 代码复杂度对比

| 代码维度 | Fucci | 原生WriteCheck | SQLancer WriteCheck |
|----------|-------|----------------|---------------------|
| **核心Oracle类** | ~1066行 | ~142行 | ~112行 |
| **执行器** | ~412行 | ~47行 | ~30行 |
| **事务生成** | 分散多处 | ~94行 | ~25行 |
| **MVCC模拟** | ~500行 | 无 | 无 |
| **锁分析** | ~95行 | 无 | 无 |
| **用例简化** | ~500行 | 无 | 无 |
| **总复杂度** | 高 | 中 | 低 |

---

## 8. 技术演进关系分析

### 8.1 项目演进脉络

```
┌─────────────────────────────────────────────────────────────────┐
│                    技术演进关系图                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│    SQLancer (Original)                                          │
│         │                                                        │
│         ├──→ WriteCheck (Fork) ──→ 原生WriteCheck               │
│         │        (专注写可串行化)    (CockroachDB专精)            │
│         │                                                        │
│         └──→ SQLancer Main                                       │
│              │                                                    │
│              ├──→ WriteCheck Oracle (内置)                       │
│              │    (多DBMS通用)                                    │
│              │                                                    │
│              └──→ 其他Oracle (TLP/NoREC等)                       │
│                                                                  │
│    Fucci (独立项目)                                              │
│    (MVCC模拟 + 多Oracle)                                         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 8.2 技术传承与创新

| 技术来源 | Fucci继承 | Fucci创新 |
|---------|-----------|-----------|
| SQLancer架构 | 表达式生成器、AST结构 | MVCC模拟、多Oracle、Reducer |
| WriteCheck思路 | 事务调度执行 | CS Oracle约束求解、锁分析 |

| 技术来源 | 原生WriteCheck继承 | 原生WriteCheck创新 |
|---------|-------------------|-------------------|
| SQLancer架构 | 事务框架、调度生成 | REPRODUCE Oracle、写可串行化专精 |

| 技术来源 | SQLancer WC继承 | SQLancer WC创新 |
|---------|-----------------|-----------------|
| 原生WriteCheck | Oracle机制 | 多DBMS适配、隔离级别扩展 |

---

## 9. 融合建议与选型指南

### 9.1 三方工具融合建议

| 融合方向 | 来源 | 目标工具 | 具体建议 |
|---------|------|----------|----------|
| MVCC模拟 | Fucci CS | 原生WriteCheck/SQLancer WC | 引入版本链和约束求解 |
| 多Oracle机制 | Fucci | SQLancer WC | 添加DT/MT Oracle选项 |
| 冲突定向构造 | Fucci | 原生WriteCheck/SQLancer WC | 提高Bug触发效率 |
| 用例简化 | Fucci Reducer | 原生WriteCheck | 四层简化改善Bug报告 |
| REPRODUCE Oracle | 原生WriteCheck | Fucci | 增强Bug复现能力 |
| 多DBMS适配 | SQLancer WC | Fucci | 扩展PostgreSQL等支持 |
| 多事务支持 | 原生WriteCheck | Fucci | 2→1-5事务灵活配置 |

### 9.2 选型决策树

```
需求：事务隔离级别Bug检测
        │
        ├── 需要深度MVCC分析？
        │       ├── YES → Fucci (CS Oracle)
        │       └── NO  → 继续
        │
        ├── 需要跨DBMS对比？
        │       ├── YES → Fucci (DT Oracle)
        │       └── NO  → 继续
        │
        ├── 需要CockroachDB专精？
        │       ├── YES → 原生WriteCheck
        │       └── NO  → 继续
        │
        ├── 需要多DBMS快速测试？
        │       ├── YES → SQLancer WriteCheck
        │       └── NO  → 继续
        │
        ├── 需要Bug复现能力？
        │       ├── YES → 原生WriteCheck (REPRODUCE)
        │       └── NO  → 继续
        │
        ├── 需要用例简化？
        │       ├── YES → Fucci
        │       └── NO  → SQLancer WriteCheck (简单场景)
```

### 9.3 适用场景推荐矩阵

| 场景特征 | Fucci | 原生WriteCheck | SQLancer WC |
|----------|:-----:|:--------------:|:-----------:|
| MVCC深度检测 | ⭐⭐⭐ | ⭐ | ⭐ |
| 可串行化专精 | ⭐⭐ | ⭐⭐⭐ | ⭐⭐ |
| 多隔离级别 | ⭐⭐⭐ | ⭐ | ⭐⭐⭐ |
| CockroachDB | ⭐ | ⭐⭐⭐ | ⭐ |
| MySQL深度 | ⭐⭐⭐ | ⭐⭐ | ⭐⭐ |
| 多DBMS覆盖 | ⭐ | ⭐⭐ | ⭐⭐⭐ |
| 冲突定向构造 | ⭐⭐⭐ | ⭐ | ⭐ |
| Bug复现 | ⭐⭐ | ⭐⭐⭐ | ⭐ |
| 用例简化 | ⭐⭐⭐ | ⭐ | ⭐ |
| 部署复杂度 | ⭐(高) | ⭐⭐(中) | ⭐⭐⭐(低) |

---

## 10. 总结

### 10.1 三方工具定位

| 工具 | 核心定位 | 适用场景 |
|------|----------|----------|
| **Fucci** | MVCC精确模拟+多Oracle验证 | 深度隔离级别Bug挖掘 |
| **原生WriteCheck** | 写可串行化违规专精 | CockroachDB/已发现Bug复现 |
| **SQLancer WriteCheck** | 多DBMS通用测试 | 快速跨数据库事务测试 |

### 10.2 技术互补关系

三方工具在技术层面存在显著互补：
- Fucci提供**深度分析**能力（MVCC模拟、锁分析、约束求解）
- 原生WriteCheck提供**专精检测**能力（写可串行化、Bug复现）
- SQLancer WriteCheck提供**广度覆盖**能力（多DBMS、简单部署）

### 10.3 最佳实践建议

1. **研发阶段**: 使用SQLancer WriteCheck进行快速日常测试
2. **深度挖掘**: 使用Fucci CS Oracle进行MVCC缺陷分析
3. **Bug复现**: 使用原生WriteCheck REPRODUCE Oracle验证已知Bug
4. **跨DB对比**: 使用Fucci DT Oracle对比不同数据库行为
5. **可串行化检测**: 使用原生WriteCheck专精CockroachDB

---

## 附录: 代码统计

| 类名 | Fucci | 原生WriteCheck | SQLancer WC |
|------|-------|----------------|-------------|
| 核心Oracle | ~1066行 | ~142行 | ~112行 |
| 执行器 | ~412行 | ~47行 | ~30行 |
| 事务生成 | 分散 | ~94行 | ~25行 |
| MVCC模拟 | ~500行 | 无 | 无 |
| 锁分析 | ~95行 | 无 | 无 |
| 用例简化 | ~500行 | 无 | 无 |
| 基础框架 | ~1240行 | 共享SQLancer | 共享SQLancer |

---

*报告生成时间: 2026-05-14*
*分析覆盖: Fucci-main, WriteCheck-main, sqlancer-main WriteCheck Oracle*

### 6.1 主要执行路径

```
Main.java:main()
    ↓
Main.java:txnTesting()
    ↓
TableTool.initialize()           // 初始化连接和配置
    ↓
table.initialize()               // 生成表结构和数据
    ↓
table.genTransaction(1/2)        // 生成事务对
    ↓
TableTool.makeConflict()         // 构造冲突
    ↓
FucciChecker.checkRandom()       // 随机调度检测
    ↓
FucciChecker.oracleCheck()       // Oracle检测
    ↓
TxnPairExecutor.execute()        // 执行调度
    ↓
FucciChecker.inferOracleMVCC()   // CS Oracle推断
    ↓
FucciChecker.compareOracles()    // 结果对比
    ↓
Reducer.reduce()                 // 用例简化
```

### 6.2 关键数据结构

| 数据结构 | 用途 | 定义位置 |
|---------|------|----------|
| `Transaction` | 事务对象，包含语句列表和状态 | `Transaction.java` |
| `StatementCell` | 单条SQL语句封装 | `StatementCell.java` |
| `Version` | MVCC版本数据 | `Version.java` |
| `View` | 数据视图(行集合) | `View.java` |
| `Lock` | 锁对象(类型+锁范围) | `Lock.java` |
| `vData` | 外部版本链(行ID→版本列表) | `FucciChecker.java` |

---

## 7. 总结与建议

### 7.1 技术总结

Fucci是一个**深度专业**的事务隔离级别检测工具，其核心创新点在于:

1. **CS Oracle的MVCC模拟**: 通过外部版本链和约束求解，完整模拟数据库MVCC行为，可发现实现缺陷
2. **多Oracle交叉验证**: DT/MT/CS三种机制互补，提高检测可信度
3. **四层简化机制**: 有效降低Bug报告复杂度，便于开发者分析

### 7.2 与SQLancer WriteCheck融合建议

| 融合方向 | 具体建议 |
|---------|----------|
| **MVCC模拟引入** | 将Fucci的CS Oracle机制引入SQLancer，增强隔离级别检测精度 |
| **多Oracle机制** | SQLancer可引入DT Oracle，使用PostgreSQL作为参照检测MySQL |
| **冲突构造** | 引入Fucci的定向冲突构造策略，提高Bug触发效率 |
| **用例简化** | 将Reducer简化机制引入SQLancer，改善Bug报告质量 |
| **事务数量扩展** | 将SQLancer的多事务支持引入Fucci，增强测试场景覆盖 |

### 7.3 适用场景分析

| 场景 | 推荐工具 |
|------|----------|
| **深度隔离级别检测** | Fucci (CS Oracle) |
| **跨数据库差异检测** | Fucci (DT Oracle) |
| **快速多DBMS测试** | SQLancer WriteCheck |
| **复杂事务场景测试** | SQLancer WriteCheck (多事务支持) |
| **Bug复现与简化** | Fucci (Reducer机制) |

---

## 附录: 代码统计

| 类名 | 代码行数 | 核心职责 |
|------|----------|----------|
| `FucciChecker.java` | ~1066 | Oracle检测核心 |
| `TxnPairExecutor.java` | ~412 | 调度执行器 |
| `TableTool.java` | ~1239 | 表操作和冲突构造 |
| `Reducer.java` | ~500+ | 四层简化 |
| `Lock.java` | ~95 | 锁冲突分析 |
| `Transaction.java` | ~62 | 事务封装 |
| `StatementCell.java` | ~407 | 语句解析 |

---

*报告生成时间: 2026-05-14*