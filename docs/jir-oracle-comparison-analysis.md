# JIR Oracle 原始实现 vs 当前集成版本 深度对比分析报告

> **分析日期**：2026-06-03  
> **原始实现路径**：`D:\Jack.Xiao\dbtools\sqlancer-scale+DQP+JIR\sqlancer-scale`  
> **当前集成版本路径**：`D:\Jack.Xiao\dbtools\sqlancer-main\sqlancer-main`  
> **参考论文**：*Detecting Join Bugs in Database Engines via Join Implication Reasoning* (SIGMOD 2026)

---

## 一、结论摘要

| 维度 | 原始实现（GeneralJIROracle） | 当前集成版本 | 差异严重度 |
|------|------------------------------|--------------|-----------|
| 规则覆盖率 | 仅 Rule 1（部分） | 全部 6 条规则 | ✅ 当前更完整 |
| 结果集比较语义 | HashSet（SET 语义） | HashSet（SET 语义） | ⚠️ **两者都有 BUG**（论文要求 MULTISET） |
| Fetch 列策略 | 随机多列 / `*` | 固定左表第一列 | ⚠️ 覆盖率损失 |
| Anti-Join NULL 替换 | 完整实现 | 不需要（只选左表列） | ⚠️ 语义简化 |
| 多表 JOIN 树 | 支持（只变换最后一个 JOIN） | 不支持（固定 2 表单 JOIN） | ⚠️ 复杂度损失 |
| ON 条件类型 | 通用表达式 | DBMS 特定（GaussDB-A 有 bug） | 🔴 GaussDB-A 有实际 BUG |

---

## 二、规则覆盖对比

### 原始实现只实现了 Rule 1（部分）

```java
// GeneralJIROracle.java:140
if (lastJoin.getJoinType() == LEFT) {  // 只处理最后一个 LEFT JOIN
    lastJoin.setJoinType(INNER);
    String innerJoinQuery = GeneralToStringVisitor.asString(select);
    // ... build anti-join
    String combination2 = innerJoinQuery + " UNION ALL " + antiJoinTransformedQuery;
}
```

**原始实现缺失**：
- Rule 2（LEFT/RIGHT 对称）
- Rule 3（Semi/Anti 补集）
- Rule 4（FULL 分解）
- Rule 5（CROSS 等价）
- Rule 6（NATURAL 显式化）

**当前实现**：6 条规则全部实现，结构清晰。这是当前版本的明显优势。

### 规则覆盖矩阵

| 规则 | 论文定义 | 原始实现 | MySQL | PostgreSQL | GaussDB-M | GaussDB-A |
|------|----------|----------|-------|------------|-----------|-----------|
| Rule 1: LEFT_JOIN_DECOMPOSITION | $L \bowtie^{\text{LEFT}} = \text{INNER} \uplus \text{ANTI}$ | 部分（最后 JOIN） | ✅ | ✅ | ✅ | ✅ |
| Rule 2: LEFT_RIGHT_SYMMETRY | $L \bowtie^{\text{LEFT}} R = R \bowtie^{\text{RIGHT}} L$ | ❌ 未实现 | ✅ | ✅ | ✅ | ✅ |
| Rule 3: SEMI_ANTI_COMPLEMENT | $Q = Q_{\exists} \uplus Q_{\neg\exists}$ | ❌ 未实现 | ✅ | ✅ | ✅ | ✅ |
| Rule 4: FULL_JOIN_DECOMPOSITION | $L \bowtie^{\text{FULL}} = \text{INNER} \uplus \text{LEFT\_ANTI} \uplus \text{RIGHT\_ANTI}$ | ❌ 未实现 | ❌ | ✅ | ❌ | ✅ |
| Rule 5: CROSS_JOIN_EQUIVALENCE | $L \times R = L \bowtie_{\text{TRUE}}$ | ❌ 未实现 | ✅ | ✅ | ✅ | ✅ |
| Rule 6: NATURAL_JOIN_EXPLICATION | $L \bowtie^{\text{NATURAL}} \equiv L \bowtie_{\wedge L.a_i=R.a_i}$ | ❌ 未实现 | ✅ | ✅ | ✅ | ✅ |

---

## 三、Rule 1（LEFT_JOIN_DECOMPOSITION）核心算法差异

这是两者唯一重叠实现的规则，差异最关键。

### 3.1 论文公式

$$L \bowtie_P^{\text{LEFT}} R = (L \bowtie_P^{\text{INNER}} R) \;\uplus\; \mathcal{N}_R(L \bar{\bowtie}_P R)$$

其中 $\uplus$ 是 **bag union（多重集合并）**，$\mathcal{N}_R$ 表示将右表列替换为 NULL。

### 3.2 两者实现对比

**原始实现（正确遵循论文）**：

```java
// 原始 GeneralJIROracle.java:144-167
// 关键：Anti-Join 需要 SELECT 右表列 → 替换为 NULL
if (fetchColumns == "*") {
    // SELECT *, NULL, NULL, ... (为右表每列添加 NULL)
    antiJoinColumns.add("*");
    for (int i = 0; i < rightTable.getColumns().size(); i++) {
        antiJoinColumns.add("NULL");
    }
} else {
    // 具体列：右表列 → NULL，左表列保留
    for (fetchCol : select.getFetchColumns()) {
        if (tableName.equals(rightTable.getName())) {
            antiJoinColumns.add("NULL");  // 右表列替换为 NULL
        } else {
            antiJoinColumns.add(fetchCol);  // 左表列保留
        }
    }
}
```

**当前实现（简化）**：

```java
// 所有 Transformer 的 getLeftTableFetchColumns()
// 固定只选左表第一列，Anti-Join 不需要 NULL 替换
return Collections.singletonList(MySQLColumnReference.create(cols.get(0), null));
```

### 3.3 差异影响分析

**当前实现的简化是语义正确的**，因为：
- 只 SELECT 左表列，LEFT JOIN 中左表列永远不会是 NULL
- Anti-Join 结果中左表列保留原值，无需 NULL 替换
- INNER JOIN 与 Anti-Join 在左表列上 UNION ALL 语义一致

**但覆盖率降低**：永远不会测试到右表列的 NULL 处理逻辑，这正是 JOIN Bug 的高发区。

---

## 四、结果集比较语义 — 关键 BUG（两者共有）

### 4.1 问题描述

`ComparatorHelper.assumeResultSetsAreEqual()` 使用 `HashSet` 比较，**忽略重复行**：

```java
// ComparatorHelper.java:108-109（两个代码库相同）
Set<String> firstHashSet = new HashSet<>(resultSet);
Set<String> secondHashSet = new HashSet<>(secondResultSet);
if (!firstHashSet.equals(secondHashSet)) { throw new AssertionError(...); }
```

### 4.2 为什么这是 BUG

论文 Rule 1 的 UNION ALL 是 **bag union（多重集）**，要求保留重复行计数：

| Source（LEFT JOIN） | Target（INNER UNION ALL ANTI） | HashSet 比较 | 正确结果 |
|---|---|---|---|
| `[A, A, B]` | `[A, A] + [B] = [A, A, B]` | ✅ 正确通过 | ✅ |
| `[A, A, B]` | `[A] + [B] = [A, B]`（DB Bug：少一个A） | ❌ **HashSet 误判为相等**（`{A,B}` == `{A,B}`） | ❌ 应报错 |

**size 检查能捕获这个 Bug 吗？**

```java
if (resultSet.size() != secondResultSet.size()) { throw ... }  // size 3 vs 2 → 捕获！
```

size 检查在**单一规则**场景下能捕获，但在 `UNION ALL` 两个 target 都执行后合并的场景，size 是合并后的总数，如果两个 target 各自有错但行数恰好抵消，size 检查也会漏过。

### 4.3 修复建议

使用排序后的 `List.equals()` 代替 `HashSet.equals()`，保留重复行：

```java
// 修复方案：MULTISET 比较
List<String> sortedFirst = new ArrayList<>(resultSet);
List<String> sortedSecond = new ArrayList<>(secondResultSet);
Collections.sort(sortedFirst);
Collections.sort(sortedSecond);
if (!sortedFirst.equals(sortedSecond)) {
    throw new AssertionError("Multiset content mismatch!");
}
```

---

## 五、Fetch 列策略差异

| 方面 | 原始实现 | 当前实现 |
|------|----------|----------|
| 列选择 | `Randomly.getBoolean()` → `*` 或随机子集 | 固定左表第一列 |
| 列来源 | 所有参与表（左+右） | 仅左表 |
| 测试覆盖 | 可触发右表列 NULL 处理 Bug | 永远不会触发 |

**建议**：在 `getLeftTableFetchColumns()` 中增加随机性，允许选择多列（包括右表列），并在 Anti-Join 中实现 NULL 替换。

---

## 六、ON 条件生成差异

| DBMS | 当前代码 | 问题 |
|------|----------|------|
| MySQL | `gen.generateExpression(0)` | 可生成任意类型表达式，MySQL 容忍数字/字符串作为布尔值，正常工作 |
| PostgreSQL | `gen.generateExpression(0, PostgresDataType.BOOLEAN)` | 显式指定 BOOLEAN 类型，正确 |
| GaussDB-M | `gen.generateExpression()`（无参） | 等价于 depth=0 的任意类型，MySQL 兼容模式容忍，正常工作 |
| GaussDB-A | `gen.generateBooleanExpression()` | **已修复**，之前用 `generateExpression(0)` 导致 Oracle 兼容模式大量 syntax error |

**GaussDB-A 的修复是正确的**，与 PostgreSQL 的方式对齐。

---

## 七、GaussDB-A / GaussDB-M 适配器特有问题

### 7.1 GaussDB-A Rule 6（NATURAL JOIN）：原始 SQL 拼接 vs AST

```java
// GaussDBAJIRTransformer.java:238-240
// 因为 GaussDBAJoinType 没有 NATURAL 枚举，使用原始 SQL
String sourceSQL = String.format(
    "SELECT %s FROM %s NATURAL JOIN %s",
    leftColName, leftTable.getName(), rightTable.getName());
```

**风险**：如果表名或列名包含特殊字符（大写、空格），原始 SQL 拼接会绕过 identifier quoting 逻辑，可能产生语法错误。

**GaussDB-M 对应实现（正确）**：

```java
// GaussDBMJIRTransformer.java:210
// JoinType.NATURAL 在 GaussDB-M 枚举中存在，使用 AST
GaussDBSelect sourceSelect = createBaseSelect(JoinType.NATURAL, null);
```

**建议**：为 GaussDBAJoinType 添加 `NATURAL` 枚举值，与 GaussDB-M 对齐。

### 7.2 GaussDB-A Rule 5（CROSS JOIN = INNER JOIN ON TRUE）

```java
// GaussDBAJIRTransformer.java:198
GaussDBExpression onTrue = GaussDBAConstant.createNumberConstant(1);
// 渲染为 "ON 1"，Oracle 兼容模式中 1 不等于 TRUE
```

**问题**：Oracle 兼容模式下 `ON 1` 是否被接受为布尔 TRUE？需要验证。PostgreSQL 使用 `PostgresConstant.createTrue()` 渲染为 `ON TRUE`。

**建议**：验证 GaussDB-A Oracle 兼容模式是否接受 `ON 1`，若不接受则改用 `ON (1=1)`。

### 7.3 GaussDB-M 的 `ON TRUE` 处理（正确）

```java
// GaussDBMJIRTransformer.java:170
GaussDBExpression onTrue = GaussDBConstant.createIntConstant(1);
// MySQL 兼容模式：1 = TRUE，正确
```

---

## 八、架构设计差异

### 8.1 原始架构（单体）

```
GeneralJIROracle extends GeneralQueryPartitioningBase
  - 直接继承 QueryPartitioning 基类（共享 state, errors, select, targetTables 等字段）
  - 规则逻辑硬编码在 check() 方法中
  - 无 Transformer 接口
  - 无 JIRRule / JIRQuerySet 抽象
  - 与 DQP（Query Plan Guidance）深度耦合（appendScoreToTable）
```

### 8.2 当前架构（模块化）

```
JIROracle<G> implements TestOracle<G>          (通用框架，DBMS 无关)
  └── JIRTransformer<G> interface              (DBMS 特定接口)
        ├── MySQLJIRTransformer
        ├── PostgresJIRTransformer
        ├── GaussDBMJIRTransformer
        └── GaussDBAJIRTransformer
  └── JIRRule enum                             (规则定义 + DBMS 支持矩阵)
  └── JIRQuerySet                              (Source + Targets 数据包)
  └── JIRResultType enum                       (EQUAL / UNION_ALL)
```

**评价**：当前架构显著优于原始架构，可维护性和扩展性更好。

---

## 九、完整文件清单对比

### 原始实现文件

| 文件 | 行数 | 职责 |
|------|------|------|
| `src/sqlancer/general/oracle/GeneralJIROracle.java` | ~218 | 全部 JIR 逻辑（规则生成+执行+比较） |

### 当前集成版本文件

| 文件 | 行数 | 职责 |
|------|------|------|
| `src/sqlancer/common/oracle/jir/JIROracle.java` | ~130 | 通用 JIR 框架（执行+比较） |
| `src/sqlancer/common/oracle/jir/JIRTransformer.java` | ~44 | DBMS 特定变换器接口 |
| `src/sqlancer/common/oracle/jir/JIRRule.java` | ~85 | 规则枚举 + DBMS 支持矩阵 |
| `src/sqlancer/common/oracle/jir/JIRQuerySet.java` | ~39 | Source + Targets 数据包 |
| `src/sqlancer/common/oracle/jir/JIRResultType.java` | ~10 | EQUAL / UNION_ALL 枚举 |
| `src/sqlancer/mysql/oracle/MySQLJIRTransformer.java` | ~281 | MySQL 5 规则实现 |
| `src/sqlancer/postgres/oracle/ext/PostgresJIRTransformer.java` | ~323 | PostgreSQL 6 规则实现 |
| `src/sqlancer/gaussdbm/oracle/GaussDBMJIRTransformer.java` | ~271 | GaussDB-M 5 规则实现 |
| `src/sqlancer/gaussdba/oracle/GaussDBAJIRTransformer.java` | ~318 | GaussDB-A 6 规则实现 |

---

## 十、差异汇总与建议优先级

| # | 差异点 | 影响 | 优先级 | 建议 |
|---|--------|------|--------|------|
| 1 | HashSet SET 语义（两者共有 BUG） | 漏报 duplicate 差异 Bug | 🔴 高 | 改用排序 List 比较（MULTISET 语义） |
| 2 | GaussDB-A Rule 6 原始 SQL 拼接 | 特殊字符表名触发 syntax error | 🟡 中 | 为 GaussDBAJoinType 添加 NATURAL 枚举 |
| 3 | GaussDB-A Rule 5 ON 1 vs ON TRUE | 可能触发语法错误 | 🟡 中 | 验证或改用 `ON (1=1)` |
| 4 | 固定选左表第一列 | 覆盖率低，无法测试右表 NULL 处理 | 🟡 中 | 随机选择多列 + 实现 NULL 替换 |
| 5 | 仅 2 表单 JOIN | 无法检测多 JOIN 树的复合 Bug | 🟢 低 | 扩展为多表多 JOIN（复杂度高） |
| 6 | GaussDB-A 之前用 generateExpression(0) | **已修复** → generateBooleanExpression() | ✅ 已完成 | — |
| 7 | GaussDB-M GaussDBExists 单参构造 AssertionError | **已修复** → 容忍 null | ✅ 已完成 | — |

---

## 十一、最优先修复方案

### 修复 1：ComparatorHelper 的 SET 语义 BUG（影响所有 DBMS）

在 `ComparatorHelper.java` 中新增 `assumeResultSetsAreEqualMultiset()` 方法，供 JIROracle 专用：

```java
public static void assumeResultSetsAreEqualMultiset(List<String> resultSet, List<String> secondResultSet,
        String originalQueryString, List<String> combinedString, SQLGlobalState<?, ?> state) {
    if (resultSet.size() != secondResultSet.size()) {
        throw new AssertionError("Size mismatch: " + resultSet.size() + " vs " + secondResultSet.size());
    }
    List<String> sortedFirst = new ArrayList<>(resultSet);
    List<String> sortedSecond = new ArrayList<>(secondResultSet);
    Collections.sort(sortedFirst);
    Collections.sort(sortedSecond);
    if (!sortedFirst.equals(sortedSecond)) {
        throw new AssertionError("Multiset content mismatch!");
    }
}
```

### 修复 2：GaussDBAJoinType 添加 NATURAL 枚举

```java
// GaussDBAJoin.java
public enum GaussDBAJoinType {
    INNER, LEFT, RIGHT, CROSS, FULL, NATURAL  // 添加 NATURAL
}

// GaussDBAToStringVisitor.java visit(GaussDBAJoin)
case NATURAL: sb.append("NATURAL JOIN "); break;
```

### 修复 3：GaussDB-A Rule 5 改用 `ON (1=1)`

```java
// GaussDBAJIRTransformer.java generateCrossJoinEquivalence()
// 修改前
GaussDBExpression onTrue = GaussDBAConstant.createNumberConstant(1);
// 修改后（使用布尔比较表达式，语义明确）
GaussDBExpression onTrue = new GaussDBABinaryComparisonOperation(
    GaussDBAConstant.createNumberConstant(1),
    GaussDBAConstant.createNumberConstant(1),
    GaussDBABinaryComparisonOperator.EQUALS);  // 渲染为 "1 = 1"
```
