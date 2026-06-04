# SQLancer 事务检测能力优化整改方案

> **版本**：v1.0
> **创建日期**：2026-06-03
> **适用范围**：MySQL、PostgreSQL、GaussDB-A、GaussDB-M
> **依据**：docs/troc-sqlancer-transaction-detection-deep-analysis.md

---

## 一、整改总览

### 1.1 背景

通过对 Troc 项目源码的逐文件精读，发现 SQLancer 事务检测体系存在以下核心差距：

1. **误报率高**：`TxBase` 对比逻辑无 Undecided 分类，所有差异一律报 Bug（Troc 有 3 类 Undecided 过滤）
2. **快照时机建模错误**：MySQL/GaussDB-M 的 TX_INFER 在 BEGIN 时创建快照，但 InnoDB 实际在第一条 SELECT 时创建（潜在误报/漏报源）
3. **Schedule 覆盖率不足**：仅随机采样 + O(n²) 去重，无法保证短事务场景下的确定性覆盖
4. **Bug 分类粗糙**：仅 2 种 Bug 类型（Inconsistent result / final state），Troc 有 6 种

### 1.2 整改目标

| 指标 | 当前 | 目标 |
|------|------|------|
| Bug 分类数 | 2 种 | 6 种 + 3 种 Undecided |
| MySQL TX_INFER 快照准确性 | BEGIN 创建（错误） | 第一条 SELECT 创建（正确） |
| Schedule 穷举能力 | 无 | 支持 HYBRID 模式（小空间穷举 + 大空间采样） |
| 误报过滤 | 无 | 3 类 Undecided 场景显式跳过 |

### 1.3 影响范围

| 模块 | 修改文件 | 影响 |
|------|---------|------|
| TxBase（公共基类） | 1 文件 | 所有 Oracle（WRITE_CHECK/TX_INFER/Fucci）受益 |
| TxTestGenerator（公共基类） | 1 文件 | 所有 Schedule 生成受益 |
| MySQL TX_INFER | 1 文件 | MySQL 隔离级别 RR/SER 准确性 |
| GaussDB-M TX_INFER | 1 文件 | GaussDB-M 隔离级别 RR/SER 准确性 |
| 新增工具类 | 2 文件 | Bug 类型枚举 + Schedule 穷举算法 |

---

## 二、阶段1（P0）：Undecided 过滤机制

### 2.1 问题描述

**现状**（`TxBase.java` 第 41-86 行 `compareAllResults()`）：
```java
// 只跳过 blocked 的语句（45行）
if (stmtExecResult.isBlocked()) continue;
// 其他所有差异一律报 Bug → 无 Undecided 分类
```

**Troc 的做法**（`TrocChecker.compareOracles()` 第 538-606 行）：
- 3 类 Undecided 显式跳过，避免无法判定的差异被误报为 Bug
- 6 种 Bug 类型细分，便于定位问题根因

### 2.2 修改方案

#### 2.2.1 新增 Bug 类型枚举

**新建文件**：`src/sqlancer/common/oracle/TxBugType.java`

```java
package sqlancer.common.oracle;

/**
 * 事务检测 Bug 类型分类。
 * 参考 Troc 的 compareOracles() 方法中的 6 种 Bug 类型 + 3 种 Undecided。
 */
public enum TxBugType {
    // === 确定性 Bug（6 种）===
    MISSING_ABORT("Error: Missing abort - execution should have aborted but didn't"),
    UNNECESSARY_ABORT("Error: Unnecessary abort - execution aborted without cause"),
    INCONSISTENT_QUERY_RESULT("Error: Inconsistent query result"),
    MISSING_LOCK("Error: Missing lock - execution should have blocked but didn't"),
    UNNECESSARY_LOCK("Error: Unnecessary lock - execution blocked without cause"),
    INCONSISTENT_FINAL_STATE("Error: Inconsistent final database state"),

    // === Undecided（3 种）===
    UNDECIDED_DEADLOCK("Ignore: Undecided - execution deadlock, oracle no deadlock"),
    UNDECIDED_ABORT("Ignore: Undecided - execution aborted, oracle didn't expect abort"),
    UNDECIDED_BLOCK("Ignore: Undecided - execution blocked, oracle didn't expect block");

    private final String message;

    TxBugType(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public boolean isUndecided() {
        return this == UNDECIDED_DEADLOCK || this == UNDECIDED_ABORT || this == UNDECIDED_BLOCK;
    }

    public boolean isBug() {
        return !isUndecided();
    }
}
```

#### 2.2.2 新增 Undecided 分类器

**新建文件**：`src/sqlancer/common/oracle/TxDiscrepancyClassifier.java`

```java
package sqlancer.common.oracle;

import sqlancer.common.transaction.TxStatementExecutionResult;

/**
 * 差异分类器：判断执行结果与 Oracle 结果之间的差异属于 Bug 还是 Undecided。
 * 参考 Troc TrocChecker.compareOracles() 的 shouldNotAbort() / shouldNotBlock() 逻辑。
 */
public class TxDiscrepancyClassifier {

    /**
     * 判断执行死锁但 Oracle 未预期死锁的情况。
     * 死锁检测时机因 DBMS 实现而异，无法判定是否为 Bug。
     */
    public static TxBugType classifyDeadlock(boolean execDeadlocked, boolean oracleDeadlocked) {
        if (execDeadlocked && !oracleDeadlocked) {
            return TxBugType.UNDECIDED_DEADLOCK;
        }
        return null; // 非此场景
    }

    /**
     * 判断执行 abort 但 Oracle 未预期 abort 的情况。
     *
     * shouldNotAbort 判断标准（按 DBMS 分）：
     * - MySQL/GaussDB-M: 死锁自动回滚代价小的事务是标准行为 → canBeLegitimateAbort
     * - PG/GaussDB-A: SSI 序列化异常导致 abort 是标准行为 → canBeLegitimateAbort
     * - Lock wait timeout → canBeLegitimateAbort
     *
     * 如果 abort 原因可以解释（DBMS 合法行为），则为 Undecided；否则为 Bug。
     */
    public static TxBugType classifyAbort(TxStatementExecutionResult execResult,
                                          TxStatementExecutionResult oracleResult) {
        boolean execAborted = execResult.reportError() && !execResult.reportDeadlock();
        boolean oracleAborted = oracleResult.reportError() && !oracleResult.reportDeadlock();

        if (!execAborted && !oracleAborted) {
            return null; // 双方都未 abort
        }
        if (execAborted && oracleAborted) {
            return null; // 双方都 abort，一致
        }
        if (!execAborted && oracleAborted) {
            return TxBugType.MISSING_ABORT; // Oracle 说应 abort 但执行未 abort → Bug
        }

        // execAborted && !oracleAborted：执行 abort 但 Oracle 未预期
        if (canBeLegitimateAbort(execResult)) {
            return TxBugType.UNDECIDED_ABORT; // 可能是 DBMS 合法行为
        }
        return TxBugType.UNNECESSARY_ABORT; // 无法解释的 abort → Bug
    }

    /**
     * 判断执行 block 但 Oracle 未预期 block 的情况。
     */
    public static TxBugType classifyBlock(TxStatementExecutionResult execResult,
                                          TxStatementExecutionResult oracleResult) {
        boolean execBlocked = execResult.isBlocked();
        boolean oracleBlocked = oracleResult.isBlocked();

        if (!execBlocked && !oracleBlocked) {
            return null;
        }
        if (execBlocked && oracleBlocked) {
            return null;
        }
        if (!execBlocked && oracleBlocked) {
            return TxBugType.MISSING_LOCK; // Oracle 说应 block 但执行未 block → Bug
        }

        // execBlocked && !oracleBlocked：执行 block 但 Oracle 未预期
        if (canBeLegitimateBlock(execResult)) {
            return TxBugType.UNDECIDED_BLOCK; // 可能是 DBMS 合法行为
        }
        return TxBugType.UNNECESSARY_LOCK; // 无法解释的 block → Bug
    }

    /**
     * 判断 abort 是否可能是 DBMS 的合法行为。
     * 参考 Troc shouldNotAbort()。
     */
    private static boolean canBeLegitimateAbort(TxStatementExecutionResult result) {
        if (!result.reportError()) {
            return false;
        }
        String error = result.getErrorInfo();
        if (error == null) {
            return false;
        }
        // MySQL/GaussDB-M: 死锁回滚
        if (error.contains("Deadlock") || error.contains("deadlock")) {
            return true;
        }
        // MySQL/GaussDB-M: Lock wait timeout
        if (error.contains("Lock wait timeout")) {
            return true;
        }
        // PG/GaussDB-A: SSI serialization failure
        if (error.contains("could not serialize") || error.contains("serialization")) {
            return true;
        }
        // PG/GaussDB-A: statement timeout
        if (error.contains("statement timeout") || error.contains("lock timeout")) {
            return true;
        }
        // GaussDB-A: Oracle 兼容错误码
        if (error.contains("ORA-00060") || error.contains("ORA-04020")) {
            return true;
        }
        return false;
    }

    /**
     * 判断 block 是否可能是 DBMS 的合法行为。
     * 参考 Troc shouldNotBlock()。
     */
    private static boolean canBeLegitimateBlock(TxStatementExecutionResult result) {
        // 超时阻塞通常是 DBMS 的合法锁等待行为
        if (result.isBlocked()) {
            return true; // 默认将未预期的 block 视为 Undecided
        }
        return false;
    }
}
```

#### 2.2.3 修改 TxBase 对比逻辑

**修改文件**：`src/sqlancer/common/oracle/TxBase.java`

**修改点 1**：在类头新增 import 和统计字段

```java
// 新增 import
import sqlancer.common.oracle.TxBugType;
import sqlancer.common.oracle.TxDiscrepancyClassifier;

// 新增字段（在 logger 之后）
protected int undecidedCount = 0;
protected int bugCount = 0;
```

**修改点 2**：重写 `compareAllResults()` 方法（替换第 41-86 行）

```java
public String compareAllResults(TxTestExecutionResult testResult, TxTestExecutionResult oracleResult) {
    List<TxStatementExecutionResult> stmtExecResults = testResult.getStatementExecutionResults();
    List<TxStatementExecutionResult> stmtOracleResults = oracleResult.getStatementExecutionResults();

    for (TxStatementExecutionResult stmtExecResult : stmtExecResults) {
        if (stmtExecResult.isBlocked()) {
            continue;
        }
        TxStatementExecutionResult stmtOracleResult = findMatchingResult(stmtExecResult, stmtOracleResults);
        if (stmtOracleResult == null) {
            continue;
        }
        TxStatement stmt = stmtExecResult.getStatement();
        if (stmt.isNoErrorType()) {
            continue;
        }

        // --- 新增: Undecided 分类 ---

        // 1. 死锁差异分类
        TxBugType deadlockType = TxDiscrepancyClassifier.classifyDeadlock(
                stmtExecResult.reportDeadlock(), stmtOracleResult.reportDeadlock());
        if (deadlockType != null) {
            if (deadlockType.isUndecided()) {
                logUndecided(deadlockType, stmt);
                continue; // 跳过 Undecided
            }
            return deadlockType.getMessage() + "\n" + stmt;
        }

        // 2. Abort 差异分类
        TxBugType abortType = TxDiscrepancyClassifier.classifyAbort(stmtExecResult, stmtOracleResult);
        if (abortType != null) {
            if (abortType.isUndecided()) {
                logUndecided(abortType, stmt);
                continue;
            }
            return abortType.getMessage() + "\n" + stmt + "\n"
                    + "Exec: " + stmtExecResult.getErrorInfo() + "\n"
                    + "Oracle: " + stmtOracleResult.getErrorInfo();
        }

        // 3. Block 差异分类
        TxBugType blockType = TxDiscrepancyClassifier.classifyBlock(stmtExecResult, stmtOracleResult);
        if (blockType != null) {
            if (blockType.isUndecided()) {
                logUndecided(blockType, stmt);
                continue;
            }
            return blockType.getMessage() + "\n" + stmt;
        }

        // --- 原有逻辑 ---

        String compareErrorResult = compareErrors(stmtExecResult, stmtOracleResult);
        if (!compareErrorResult.equals("")) {
            return compareErrorResult;
        }

        if (stmt.isSelectType()) {
            if (!stmtExecResult.reportDeadlock()) {
                String selectCompareResult = compareQueryResult(stmtExecResult.getResult(),
                        stmtOracleResult.getResult());
                if (!selectCompareResult.isEmpty()) {
                    StringBuilder compareResult = new StringBuilder();
                    compareResult.append(TxBugType.INCONSISTENT_QUERY_RESULT.getMessage()).append("\n");
                    compareResult.append(stmt).append("\n");
                    compareResult.append(selectCompareResult);
                    bugCount++;
                    return compareResult.toString();
                }
            }
        }
    }

    return compareFinalDBState(testResult, oracleResult);
}
```

**修改点 3**：重写 `compareWriteTxResults()` 方法（替换第 88-122 行）

```java
public String compareWriteTxResults(TxTestExecutionResult testResult, TxTestExecutionResult oracleResult) {
    List<TxStatementExecutionResult> stmtExecResults = testResult.getStatementExecutionResults();
    List<TxStatementExecutionResult> stmtOracleResults = oracleResult.getStatementExecutionResults();

    for (TxStatementExecutionResult stmtExecResult : stmtExecResults) {
        if (stmtExecResult.isBlocked()) {
            continue;
        }
        TxStatementExecutionResult stmtOracleResult = findMatchingResult(stmtExecResult, stmtOracleResults);
        if (stmtOracleResult == null) {
            continue;
        }
        TxStatement stmt = stmtExecResult.getStatement();
        if (stmt.isSelectType()) {
            continue;
        }
        if (stmt.isNoErrorType()) {
            continue;
        }

        // --- 新增: Undecided 分类（同 compareAllResults）---
        TxBugType deadlockType = TxDiscrepancyClassifier.classifyDeadlock(
                stmtExecResult.reportDeadlock(), stmtOracleResult.reportDeadlock());
        if (deadlockType != null && deadlockType.isUndecided()) {
            logUndecided(deadlockType, stmt);
            continue;
        }

        TxBugType abortType = TxDiscrepancyClassifier.classifyAbort(stmtExecResult, stmtOracleResult);
        if (abortType != null) {
            if (abortType.isUndecided()) {
                logUndecided(abortType, stmt);
                continue;
            }
            return abortType.getMessage() + "\n" + stmt;
        }

        TxBugType blockType = TxDiscrepancyClassifier.classifyBlock(stmtExecResult, stmtOracleResult);
        if (blockType != null) {
            if (blockType.isUndecided()) {
                logUndecided(blockType, stmt);
                continue;
            }
            return blockType.getMessage() + "\n" + stmt;
        }

        String compareResult = compareErrors(stmtExecResult, stmtOracleResult);
        if (!compareResult.equals("")) {
            return compareResult;
        }
    }

    return compareFinalDBState(testResult, oracleResult);
}
```

**修改点 4**：`compareFinalDBState()` 增加 Bug 类型标注（替换第 124-134 行）

```java
public String compareFinalDBState(TxTestExecutionResult testResult, TxTestExecutionResult oracleResult) {
    for (Map.Entry<String, List<Object>> finalState : testResult.getDbFinalStates().entrySet()) {
        List<Object> execFinalState = finalState.getValue();
        List<Object> oracleFinalState = oracleResult.getDbFinalStates().get(finalState.getKey());
        String compareResultInfo = compareQueryResult(execFinalState, oracleFinalState);
        if (!compareResultInfo.isEmpty()) {
            bugCount++;
            return TxBugType.INCONSISTENT_FINAL_STATE.getMessage() + "\n" + compareResultInfo;
        }
    }
    return "";
}
```

**修改点 5**：新增辅助方法

```java
private TxStatementExecutionResult findMatchingResult(
        TxStatementExecutionResult target,
        List<TxStatementExecutionResult> candidates) {
    for (TxStatementExecutionResult candidate : candidates) {
        if (candidate.getStatement().equals(target.getStatement())
                && candidate.getStatement().getTransaction().getId()
                   == target.getStatement().getTransaction().getId()) {
            return candidate;
        }
    }
    return null;
}

protected void logUndecided(TxBugType type, TxStatement stmt) {
    undecidedCount++;
    logger.writeCurrent("[Undecided] " + type.getMessage() + " - " + stmt);
}
```

### 2.3 验证方案

1. 在 MySQL 上运行 WRITE_CHECK Oracle 100 轮，统计 `undecidedCount` 和 `bugCount`
2. 对比修改前后的 Bug 报告数量——预期误报减少 30-50%
3. 用 Troc 的 `cases/` 目录中已知 Bug 用例验证：真正的 Bug 不会被过滤掉

### 2.4 涉及文件清单

| 文件 | 操作 | 行数 |
|------|------|------|
| `src/sqlancer/common/oracle/TxBugType.java` | 新建 | ~40行 |
| `src/sqlancer/common/oracle/TxDiscrepancyClassifier.java` | 新建 | ~120行 |
| `src/sqlancer/common/oracle/TxBase.java` | 修改 | 改写 ~100行 |

---

## 三、阶段2（P0）：MySQL/GaussDB-M TX_INFER 快照点修正

### 3.1 问题描述

**现状**（`MySQLTxInfer.java` 第 171-172 行）：
```java
if (stmtType == MySQLStatementType.BEGIN) {
    createSnapshot(curTx);  // ← 在 BEGIN 时创建快照
}
```

**InnoDB 实际行为**：RR 隔离级别的快照在**第一条一致性读（SELECT）**时建立，而非 BEGIN 时。

**影响**：如果事务 T2 在 T1 的 BEGIN 和 T1 的第一条 SELECT 之间 COMMIT，当前 TX_INFER 会错误地将 T2 的数据包含在 T1 的快照中。

**Troc 的正确建模**（`TrocChecker.isSnapshotPoint()` 第 361-371 行）：
```java
case MYSQL: case MARIADB:
    return stmt.type == StatementType.SELECT;  // 第一条 SELECT 才创建快照
```

### 3.2 修改方案

**修改文件 1**：`src/sqlancer/mysql/oracle/transaction/MySQLTxInfer.java`

**修改点**：第 171-172 行 — 移除 BEGIN 时的快照创建

```java
// 修改前:
if (stmtType == MySQLStatementType.BEGIN) {
    createSnapshot(curTx);
}

// 修改后:
if (stmtType == MySQLStatementType.BEGIN) {
    // 不在 BEGIN 时创建快照
    // InnoDB RR/SER 的快照在第一条一致性读（SELECT）时建立
    // 快照创建延迟到 readSnapshotVersion() 中按需创建
}
```

> **注意**：第 196-200 行已有正确的 fallback 逻辑——在 RR/SER 下执行 `readSnapshotVersion()` 时，如果 `snapshotTxs.get(curTx).isEmpty()`，会调用 `createSnapshot(curTx)`。这意味着移除 BEGIN 时的 eager 创建后，快照将在第一条 SELECT 时延迟创建，符合 InnoDB 实际行为。

**修改文件 2**：`src/sqlancer/gaussdbm/oracle/transaction/GaussDBMTxInfer.java`

**修改点**：第 167-168 行 — 同样的修改

```java
// 修改前:
if (stmtType == GaussDBMStatementType.BEGIN) {
    createSnapshot(curTx);
}

// 修改后:
if (stmtType == GaussDBMStatementType.BEGIN) {
    // 延迟到第一条 SELECT 创建快照
}
```

### 3.3 验证方案

构造测试场景：
```
T1: BEGIN → (T2 在此处 COMMIT) → SELECT * FROM t WHERE ...
T2: BEGIN → INSERT INTO t VALUES(...) → COMMIT
```
- 修改前：T1 的快照包含 T2 的数据（错误）
- 修改后：T1 的快照不包含 T2 的数据（正确，InnoDB 实际行为）

### 3.4 涉及文件清单

| 文件 | 操作 | 行数 |
|------|------|------|
| `src/sqlancer/mysql/oracle/transaction/MySQLTxInfer.java` | 修改 | 改 2 行 |
| `src/sqlancer/gaussdbm/oracle/transaction/GaussDBMTxInfer.java` | 修改 | 改 2 行 |

---

## 四、阶段3（P1）：穷举 + 采样混合 Schedule 策略

### 4.1 问题描述

**现状**（`TxTestGenerator.java` 第 55-70 行）：
```java
public List<List<TxStatement>> genSchedules(List<Transaction> transactions) {
    // 仅随机采样
    while (count < num) {
        List<TxStatement> schedule = genOneSchedule(transactions);
        if (!schedules.contains(schedule)) {  // O(n²) 去重
            schedules.add(schedule);
            count++;
        }
    }
}
```

**Troc 的做法**（`ShuffleTool.java` 第 8-51 行）：
- 回溯法枚举所有 C(n1+n2, n1) 种合法交错
- 2×4 条语句 → C(8,4)=70 种
- 2×6 条语句 → C(12,6)=924 种
- Reservoir Sampling 均匀抽样

### 4.2 修改方案

**新建文件**：`src/sqlancer/common/transaction/ScheduleExhaustiveEnumerator.java`

```java
package sqlancer.common.transaction;

import java.util.*;

/**
 * Schedule 穷举 + Reservoir Sampling 工具。
 * 参考 Troc ShuffleTool.java 的回溯穷举和 Reservoir Sampling 算法。
 */
public class ScheduleExhaustiveEnumerator {

    /** 穷举阈值：当总交错数 <= 此值时穷举，否则采样 */
    private static final int EXHAUSTIVE_THRESHOLD = 1000;

    /**
     * 穷举两个事务的所有合法交错序列。
     * 参考 Troc ShuffleTool.shuffle() 回溯算法。
     *
     * @param tx1Stmts 事务1的语句列表
     * @param tx2Stmts 事务2的语句列表
     * @return 所有合法交错序列
     */
    public static List<List<TxStatement>> enumerateAll(
            List<TxStatement> tx1Stmts, List<TxStatement> tx2Stmts) {
        List<List<TxStatement>> results = new ArrayList<>();
        backtrack(results, new ArrayList<>(),
                tx1Stmts, tx1Stmts.size(), 0,
                tx2Stmts, tx2Stmts.size(), 0);
        return results;
    }

    private static void backtrack(
            List<List<TxStatement>> results, List<TxStatement> current,
            List<TxStatement> tx1, int tx1Len, int tx1Idx,
            List<TxStatement> tx2, int tx2Len, int tx2Idx) {
        if (tx1Idx == tx1Len && tx2Idx == tx2Len) {
            results.add(new ArrayList<>(current));
            return;
        }
        if (tx1Idx < tx1Len) {
            current.add(tx1.get(tx1Idx));
            backtrack(results, current, tx1, tx1Len, tx1Idx + 1, tx2, tx2Len, tx2Idx);
            current.remove(current.size() - 1);
        }
        if (tx2Idx < tx2Len) {
            current.add(tx2.get(tx2Idx));
            backtrack(results, current, tx1, tx1Len, tx1Idx, tx2, tx2Len, tx2Idx + 1);
            current.remove(current.size() - 1);
        }
    }

    /**
     * Reservoir Sampling 从全量空间中均匀抽取。
     * 参考 Troc ShuffleTool.sampleSubmittedTrace()。
     */
    public static List<List<TxStatement>> reservoirSample(
            List<List<TxStatement>> allSchedules, int sampleSize) {
        if (allSchedules.size() <= sampleSize) {
            return allSchedules;
        }
        Random random = new Random();
        List<List<TxStatement>> reservoir = new ArrayList<>(sampleSize);
        for (int i = 0; i < sampleSize; i++) {
            reservoir.add(allSchedules.get(i));
        }
        for (int i = sampleSize; i < allSchedules.size(); i++) {
            int j = random.nextInt(i + 1);
            if (j < sampleSize) {
                reservoir.set(j, allSchedules.get(i));
            }
        }
        return reservoir;
    }

    /**
     * HYBRID 策略：小空间穷举，大空间采样。
     */
    public static List<List<TxStatement>> hybridGenerate(
            List<TxStatement> tx1Stmts, List<TxStatement> tx2Stmts, int sampleSize) {
        long totalSchedules = countSchedules(tx1Stmts.size(), tx2Stmts.size());
        if (totalSchedules <= EXHAUSTIVE_THRESHOLD) {
            return enumerateAll(tx1Stmts, tx2Stmts);
        }
        List<List<TxStatement>> allSchedules = enumerateAll(tx1Stmts, tx2Stmts);
        return reservoirSample(allSchedules, sampleSize);
    }

    /**
     * 计算 C(n1+n2, n1) = (n1+n2)! / (n1! * n2!)
     */
    public static long countSchedules(int n1, int n2) {
        int total = n1 + n2;
        if (total > 20) {
            return Long.MAX_VALUE; // 防止溢出
        }
        long result = 1;
        int min = Math.min(n1, n2);
        for (int i = 0; i < min; i++) {
            result = result * (total - i) / (i + 1);
        }
        return result;
    }
}
```

**修改文件**：`src/sqlancer/common/transaction/TxTestGenerator.java`

**修改点**：新增 HYBRID Schedule 生成方法（在第 70 行之后新增）

```java
/**
 * HYBRID Schedule 生成：小空间穷举，大空间采样。
 * 参考 Troc ShuffleTool 的回溯穷举算法。
 */
public List<List<TxStatement>> genSchedulesHybrid(List<Transaction> transactions) {
    if (transactions.size() != 2) {
        // 非2事务场景退回随机采样
        return genSchedules(transactions);
    }

    List<TxStatement> tx1Stmts = transactions.get(0).getStatements();
    List<TxStatement> tx2Stmts = transactions.get(1).getStatements();
    int num = globalState.getOptions().getNrSchedules();

    long totalSchedules = ScheduleExhaustiveEnumerator.countSchedules(
            tx1Stmts.size(), tx2Stmts.size());

    if (totalSchedules <= num) {
        // 穷举所有交错
        return ScheduleExhaustiveEnumerator.enumerateAll(tx1Stmts, tx2Stmts);
    } else {
        // Reservoir Sampling
        List<List<TxStatement>> allSchedules =
                ScheduleExhaustiveEnumerator.enumerateAll(tx1Stmts, tx2Stmts);
        return ScheduleExhaustiveEnumerator.reservoirSample(allSchedules, num);
    }
}
```

### 4.3 验证方案

1. 构造 2×3 条语句的事务对，验证穷举生成 C(6,3)=20 种 schedule
2. 构造 2×6 条语句的事务对，验证穷举生成 C(12,6)=924 种 schedule
3. 构造 2×10 条语句的事务对，验证降级为采样模式

### 4.4 涉及文件清单

| 文件 | 操作 | 行数 |
|------|------|------|
| `src/sqlancer/common/transaction/ScheduleExhaustiveEnumerator.java` | 新建 | ~90行 |
| `src/sqlancer/common/transaction/TxTestGenerator.java` | 修改 | 新增 ~25行 |

---

## 五、阶段4（P2）：Bug 报告类型细化

### 5.1 问题描述

当前 Bug 报告仅使用 2 种类型描述：
- "Error: Inconsistent query result"
- "Error: Inconsistent final database state"

Troc 有 6 种细分，便于统计分析和定位根因。

### 5.2 修改方案

阶段1（Undecided 过滤）已引入 `TxBugType` 枚举和分类器。本阶段确保所有 Oracle 的 Bug 报告使用统一类型标注。

**修改文件**：各 `WriteCheckOracle` 和 `TxInferOracle`

在每个 Oracle 的 Bug 报告位置，将硬编码字符串替换为 `TxBugType.getMessage()`。

示例（`MySQLWriteCheckOracle.java` 第 78 行附近）：
```java
// 修改前:
throw new AssertionError("Transaction execution mismatches its oracles");

// 修改后:
throw new AssertionError(compareResultInfo);  // 已包含 TxBugType 信息
```

### 5.3 验证方案

运行一轮测试，检查 Bug 报告中是否包含正确的 Bug 类型标识。

### 5.4 涉及文件清单

| 文件 | 操作 | 行数 |
|------|------|------|
| `MySQLWriteCheckOracle.java` | 修改 | ~5行 |
| `PostgresWriteCheckOracle.java` | 修改 | ~5行 |
| `GaussDBMWriteCheckOracle.java` | 修改 | ~5行 |
| `GaussDBAWriteCheckOracle.java` | 修改 | ~5行 |
| `MySQLTxInferOracle.java` | 修改 | ~5行 |
| `GaussDBMTxInferOracle.java` | 修改 | ~5行 |

---

## 六、阶段5（可选）：Range Conflict 检测

### 6.1 问题描述

TX_INFER 通过辅助版本表的 `deleted` 标记间接推断幻读。Troc 使用快照前后对比法直接在真实 DB 上验证幻读。两种方式互补。

### 6.2 适用范围

- ✅ MySQL RR/SER（InnoDB 间隙锁防幻读）
- ✅ GaussDB-M RR/SER（与 InnoDB 对齐）
- ❌ PG RR（MVCC 快照，幻读是正确行为）
- ❌ GaussDB-A RR（同 PG）

### 6.3 修改方案

如需实施，在 `MySQLTxInfer` 的 `analyzeStmt()` 中，对 RR/SER 隔离级别下的 SELECT 语句，增加额外的幻读验证步骤：

```java
// 在 RR/SER 下，SELECT 执行后验证幻读
if ((isolationLevel == MySQLIsolationLevel.REPEATABLE_READ
     || isolationLevel == MySQLIsolationLevel.SERIALIZABLE)
    && stmtType == MySQLStatementType.SELECT) {
    // 1. 保存当前 _vt 快照
    // 2. 执行其他事务的写语句
    // 3. 重新计算 SELECT 可见行
    // 4. 对比前后差异 → 差异即幻读
    // 5. 恢复 _vt 快照
}
```

### 6.4 评估

此阶段为可选增强。当前 TX_INFER 的辅助表方法已能覆盖大部分幻读场景。建议在阶段1-4完成并运行一段时间后，根据实际检测结果决定是否实施。

---

## 七、实施排期

| 阶段 | 优先级 | 工作量 | 预估工时 | 依赖 |
|------|:------:|:------:|:--------:|:----:|
| 阶段1：Undecided 过滤 | P0 | ~260行（2新建+1修改） | 2-3天 | 无 |
| 阶段2：快照点修正 | P0 | ~4行（2文件各改2行） | 0.5天 | 无 |
| 阶段3：穷举 Schedule | P1 | ~115行（1新建+1修改） | 1-2天 | 无 |
| 阶段4：Bug 类型细化 | P2 | ~30行（6文件各改5行） | 0.5天 | 阶段1 |
| 阶段5：Range Conflict | 可选 | ~150行 | 2天 | 阶段2 |

**总计**（不含阶段5）：~410行新增/修改，4-6天工时

### 实施顺序建议

```
阶段2（快照点修正）  ← 改动最小，立即可做
    ↓
阶段1（Undecided过滤）← 核心价值最高
    ↓
阶段3（穷举Schedule）← 独立模块，无依赖
    ↓
阶段4（Bug类型细化）  ← 依赖阶段1的 TxBugType
```

---

## 八、验收标准

### 8.1 功能验收

| 验收项 | 验证方法 |
|--------|---------|
| Undecided 过滤生效 | 运行 MySQL WRITE_CHECK 100轮，检查日志中出现 `[Undecided]` 标记 |
| 真实 Bug 不被过滤 | 用 Troc `cases/mysql104833.txt` 用例验证 Bug 仍被报告 |
| 快照点修正生效 | 构造 BEGIN→T2 COMMIT→SELECT 场景，验证 T1 快照不包含 T2 数据 |
| 穷举生成正确 | 2×3 语句生成 20 种 schedule，2×4 生成 70 种 |
| Bug 类型标注正确 | 检查 Bug 报告包含 TxBugType 枚举的消息文本 |

### 8.2 回归验收

- 修改前后在相同数据集上运行 WRITE_CHECK/TX_INFER，确认可检测的 Bug 不会因 Undecided 过滤被漏报
- PG/GaussDB-A 的 WRITE_CHECK 结果不受影响（快照点修正仅影响 MySQL/GaussDB-M 的 TX_INFER）

### 8.3 性能验收

- 穷举模式：2×6 语句（924 种 schedule）穷举耗时 < 5 秒
- Undecided 过滤引入的额外开销 < 1%（仅为 if 判断 + 日志）

---

## 九、风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|:----:|---------|
| Undecided 过滤过松漏报真实 Bug | 高 | 低 | 所有 Undecided 记录日志；定期人工审查；Troc 已验证此逻辑有效 |
| 快照点修正引入新问题 | 中 | 低 | 构造特定测试场景验证；对比修正前后在相同 schedule 上的结果 |
| 穷举内存溢出（大事务对） | 低 | 低 | HYBRID 模式自动降级；阈值 1000 种交错约 50MB 内存 |
| `canBeLegitimateAbort()` 覆盖不全 | 中 | 中 | 先保守（只识别已知错误模式）；后续根据实际运行补充 |
