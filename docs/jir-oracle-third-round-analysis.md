# JIR Oracle 第三轮深度对比分析报告（v2.4.3 后）

> **分析日期**：2026-06-08  
> **原始实现路径**：`D:\Jack.Xiao\dbtools\sqlancer-scale+DQP+JIR\sqlancer-scale`  
> **当前集成版本路径**：`D:\Jack.Xiao\dbtools\sqlancer-main\sqlancer-main`（v2.4.3）  
> **参考论文**：*Detecting Join Bugs in Database Engines via Join Implication Reasoning* (SIGMOD 2026)

---

## 一、结论摘要

经过两轮优化（v2.4.2/v2.4.3），核心算法已基本对齐。第三轮分析发现 **1 个关键逻辑 BUG** + **5 个设计差异** + **3 个覆盖率损失**。

| # | 差异 | 严重度 | 类型 |
|---|------|--------|------|
| 1 | Rules 2-6 独立随机选列 — source/target 可能选中不同列 | 🔴 **关键 BUG** | 逻辑错误 |
| 2 | PostgreSQL Rule 6 原始 SQL 拼接（未使用 AST） | 🟡 中 | 语法安全 |
| 3 | 缺少 `*` fetch column 模式 | 🟡 中 | 覆盖率 |
| 4 | ORDER BY 表达式多样性不足 | 🟢 低 | 覆盖率 |
| 5 | 缺少 NATURAL OUTER JOIN 变体 | 🟢 低 | 覆盖率 |
| 6 | Reproducer 缺少 errorMessage/orderBy 存储 | 🟢 低 | 诊断信息 |
| 7 | 缺少 ErrorHandler/Scoring 自适应机制 | 🟢 低 | 架构差异 |
| 8 | 多表多 JOIN 树支持缺失 | 🟢 低 | 架构差异 |
| 9 | MySQL createBaseSelect 条件逻辑冗余 | 🟢 低 | 代码质量 |

---

## 二、🔴 关键 BUG：Rules 2-6 独立随机选列

### 问题描述

当前所有 4 个 Transformer 的 Rules 2-6 通过 `getLeftTableFetchColumns()` 为 source 和 target 查询**独立**选择随机列。由于每次调用返回不同的随机列，source 和 target 可能选中**不同的列**，导致比较无效。

### 影响范围

**所有 4 个 Transformer × Rules 2-5（MySQL/GaussDB-M 无 Rule 4）= 共 18 处调用**

#### Rule 2（LEFT_RIGHT_SYMMETRY）

```java
// MySQLJIRTransformer.java:124-143
MySQLSelect sourceSelect = createBaseSelect(JoinType.LEFT, onCondition);   // 随机选 L.c3
String sourceSQL = MySQLVisitor.asString(sourceSelect);

MySQLSelect targetSelect = new MySQLSelect();
targetSelect.setFetchColumns(getLeftTableFetchColumns());                  // 随机选 L.c7 ← 不同列！
```

**后果**：source 返回 `L.c3` 的值，target 返回 `L.c7` 的值。即使两个 JOIN 结果完全正确，不同列的值可能不同 → **误报**（false positive）。反之，如果 `L.c3` 有 Bug 但 `L.c7` 没有 → **漏报**（false negative）。

#### Rule 3（SEMI_ANTI_COMPLEMENT）

```java
MySQLSelect sourceSelect = createBaseSelect(joinType, onCondition);  // 随机选 L.c2
MySQLSelect semiSelect = createBaseSelect(joinType, onCondition);    // 随机选 L.c5 ← 不同列！
MySQLSelect antiSelect = createBaseSelect(joinType, onCondition);    // 随机选 L.c1 ← 又不同列！
```

**后果**：3 个查询选了 3 个不同的列，source + semi UNION ALL anti 的比较完全无效。

#### Rule 5（CROSS_JOIN_EQUIVALENCE）

```java
MySQLSelect sourceSelect = createBaseSelect(JoinType.CROSS, null);     // 随机选 L.c4
MySQLSelect targetSelect = createBaseSelect(JoinType.INNER, onTrue);   // 随机选 L.c0 ← 不同列！
```

#### Rule 6（NATURAL_JOIN_EXPLICATION）

```java
MySQLSelect sourceSelect = createBaseSelect(JoinType.NATURAL, null);   // 随机选 L.c3
MySQLSelect targetSelect = createBaseSelect(JoinType.INNER, explicitOn); // 随机选 L.c8 ← 不同列！
```

### 原始实现对比

原始实现只有 Rule 1，其中 `generateFetchColumns()` 生成一次列列表并**共享**给所有查询变体：

```java
// GeneralJIROracle.java:67-78
select.setFetchColumns(generateFetchColumns());  // 生成一次，后续所有变体使用同一列表
```

Rule 1 当前实现也正确（`generateRandomFetchColumns()` 生成一次，共享给 source/inner/anti-join）。

### 修复方案

为 Rules 2-6 在方法入口生成 fetch columns 一次，传递给所有相关查询：

```java
// Rule 2 修复示例：
private JIRQuerySet generateLeftRightSymmetry() {
    MySQLExpression onCondition = gen.generateExpression(0);
    List<MySQLExpression> fetchColumns = getLeftTableFetchColumns();  // 生成一次

    // Source: 使用同一 fetchColumns
    MySQLSelect sourceSelect = createBaseSelectWithCols(JoinType.LEFT, onCondition, fetchColumns);

    // Target: 使用同一 fetchColumns
    MySQLSelect targetSelect = new MySQLSelect();
    targetSelect.setFetchColumns(fetchColumns);  // ← 共享，不是重新随机选择
    targetSelect.setFromList(Collections.singletonList(new MySQLTableReference(rightTable)));
    targetSelect.setJoinClauses(Collections.singletonList(new MySQLJoin(leftTable, onCondition, JoinType.RIGHT)));
    ...
}
```

所有 4 个 Transformer 均需做同样修复。可以新增 `createBaseSelectWithCols(joinType, onCondition, fetchColumns)` 辅助方法，避免重复构造 SELECT。

---

## 三、🟡 PostgreSQL Rule 6 原始 SQL 拼接

### 问题描述

PostgreSQL Rule 6 的 source NATURAL JOIN 查询使用原始 SQL 字符串拼接，而非 AST 渲染：

```java
// PostgresJIRTransformer.java:250-253
String leftColName = leftCols.get(0).getFullQualifiedName();
String sourceSQL = String.format("SELECT %s FROM %s NATURAL JOIN %s",
    leftColName, leftTable.getName(), rightTable.getName());
```

### 原始实现对比

原始实现通过 AST 渲染（`GeneralToStringVisitor.asString()`），identifier quoting 由 Visitor 统一处理。

GaussDB-M 和 GaussDB-A 已在 v2.4.2 中修复为 AST 渲染（`JoinType.NATURAL`），但 PostgreSQL 因 `PostgresJoinType` 缺少 NATURAL 枚举而仍用原始拼接。

### 风险

1. 表名/列名含特殊字符（大写、空格、保留字）时，`String.format` 不加引号 → syntax error
2. `leftCols.get(0)` 是固定第一列而非随机列（与 Rule 1 的 `getLeftTableFetchColumns()` 行为不一致）
3. **与 BUG #1 组合**：source 用 `leftCols.get(0)` 固定列，target 用 `getLeftTableFetchColumns()` 随机列 → 列不匹配

### 修复方案

1. 为 `PostgresJoinType` 添加 NATURAL 枚举（需检查 `PostgresToStringVisitor` 是否已有对应渲染逻辑）
2. 或在 `PostgresJIRTransformer` 中用子查询 + `GeneralJoin` 模式构建 NATURAL JOIN AST
3. 同时修复 source/target 列一致性（关联 BUG #1）

---

## 四、🟡 缺少 `*` Fetch Column 模式

### 问题描述

原始 `generateFetchColumns()` 有两种模式：

```java
// GeneralJIROracle.java:67-78
List<Node<GeneralExpression>> generateFetchColumns() {
    if (Randomly.getBoolean()) {
        columns.add(new ColumnReferenceNode<>(new GeneralColumn("*", null, false, false)));
    } else {
        columns = Randomly.nonEmptySubset(targetTables.getColumns()).stream()
                .map(c -> new ColumnReferenceNode<>(c)).collect(Collectors.toList());
    }
}
```

当前实现只有单列模式：

```java
// 所有 Transformer 的 generateRandomFetchColumns()
return Collections.singletonList(MySQLColumnReference.create(Randomly.fromList(allCols), null));
```

### 影响分析

`*` 模式虽然受 `getString(1)` 限制（只比较第一列），但它：
1. 生成不同的 SQL 结构，可能触发不同的优化器路径
2. Anti-Join 中 `SELECT *, NULL, NULL, ...` 是一个独特的 SQL 模式
3. 部分数据库对 `SELECT *` 的列展开有 Bug（如列顺序、NULL 处理）

但 v2.4.3 的单列策略是为了**精确验证 NULL 替换**（避免 `getString(1)` 只比较第一列的缺陷）。`*` 模式和多列模式都受此限制。

### 建议

保留当前单列模式（精确验证），同时以**低概率**添加 `*` 模式作为额外覆盖层：

```java
private List<MySQLExpression> generateRandomFetchColumns() {
    if (Randomly.getBooleanWithRatherLowProbability()) {
        // * 模式：生成 SELECT *（不同优化器路径）
        return Collections.singletonList(new MySQLColumnReference(new MySQLColumn("*", ...)));
    }
    // 单列模式：精确验证 NULL 替换
    List<MySQLColumn> allCols = new ArrayList<>();
    allCols.addAll(leftTable.getColumns());
    allCols.addAll(rightTable.getColumns());
    return Collections.singletonList(MySQLColumnReference.create(Randomly.fromList(allCols), null));
}
```

**注意**：`*` 模式需要各 DBMS AST 支持 `*` 列引用。如果 AST 不支持，可以暂时跳过此模式。

---

## 五、🟢 ORDER BY 表达式多样性不足

### 问题描述

- **原始**：`gen.generateOrderBys()` 生成多样的 ORDER BY 表达式（列引用、函数等）
- **当前**：固定 `ORDER BY 1`（位置引用）

```java
// JIROracle.java:98-99
if (Randomly.getBooleanWithRatherLowProbability()) {
    sourceQuery = sourceQuery + " ORDER BY 1";
}
```

### 影响

`ORDER BY 1` 只测试一种排序模式。不同的 ORDER BY 表达式可能触发不同的排序算法和优化路径。

### 建议

当前 `ORDER BY 1` 是最安全的跨 DBMS 方式。可以以更低概率添加 `ORDER BY 2`、`ORDER BY colName` 等变体，但需确保 ExpectedErrors 覆盖可能的失败情况。**优先级低**，当前方案已满足基本需求。

---

## 六、🟢 缺少 NATURAL OUTER JOIN 变体

### 问题描述

原始 `GeneralJoin` 支持 `OuterType`（FULL/LEFT/RIGHT），可与 NATURAL 组合：

```java
// GeneralJoin.java:60-78
public enum OuterType { FULL, LEFT, RIGHT; }

// GeneralToStringVisitor.java:46-48
if (join.getOuterType() != null) {
    sb.append(join.getOuterType());  // 渲染为 "NATURAL LEFT JOIN" 等
}
```

当前 Rule 6 只测试 `NATURAL JOIN`（= `NATURAL INNER JOIN`），缺少：
- `NATURAL LEFT JOIN` ≡ `LEFT JOIN ON (matching columns)` + right columns NULL
- `NATURAL RIGHT JOIN` ≡ `RIGHT JOIN ON (matching columns)` + left columns NULL
- `NATURAL FULL JOIN` ≡ `FULL JOIN ON (matching columns)` + either side NULL

### 建议

可在 Rule 6 中随机选择 NATURAL JOIN 的 OUTER 变体（仅 PostgreSQL/GaussDB-A 支持 FULL，MySQL/GaussDB-M 支持 LEFT/RIGHT）。但需要仔细定义等价规则，因为 NATURAL LEFT JOIN 的等价关系不同于 NATURAL INNER JOIN。**优先级低**，当前 NATURAL INNER JOIN 已覆盖核心语义。

---

## 七、🟢 Reproducer 缺少 errorMessage/orderBy 存储

### 原始 Reproducer

```java
private class GeneralJIROracleReproducer implements Reproducer<GeneralGlobalState> {
    final String combinedQuery;
    final String originalQueryString;
    final boolean orderBy;
    private String errorMessage;

    GeneralJIROracleReproducer(String combinedQuery, String originalQueryString,
            boolean orderBy, String errorMessage) {
        ...
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() { return errorMessage; }

    @Override
    public boolean bugStillTriggers(GeneralGlobalState globalState) {
        try { ... } catch (AssertionError triggeredError) {
            this.errorMessage = triggeredError.getMessage();  // 更新 errorMessage
            return true;
        }
    }
}
```

### 当前 Reproducer

```java
private class JIRReproducer implements Reproducer<G> {
    private final String sourceQuery;
    private final String combinedTargetQuery;
    private final ExpectedErrors errors;
    // 缺少: orderBy, errorMessage
}
```

### 影响

1. `errorMessage` 在 bug 报告中提供关键诊断信息（具体哪行数据不匹配）
2. `orderBy` 标志帮助理解测试条件（是否使用了 ORDER BY）
3. 原始在 `bugStillTriggers()` 中更新 `errorMessage`，当前没有

### 建议

为 `JIRReproducer` 添加 `errorMessage` 字段和 `getErrorMessage()` 方法，在创建和重放时更新。`orderBy` 标志可作为可选增强。

---

## 八、🟢 其他架构差异

### 8.1 ErrorHandler/Scoring 自适应机制

原始使用 `GeneralErrorHandler` 的评分系统追踪 JOIN 类型覆盖率，自适应调整选择概率。当前使用均匀随机选择。

**评价**：当前架构更简洁，自适应机制是原始工具的整体设计，不适合直接移植到 sqlancer-main。保持当前方式。

### 8.2 多表多 JOIN 树

原始可处理多 JOIN 树中的最后一个 LEFT JOIN（3+ 表场景）。当前固定 2 表 1 JOIN。

**评价**：多表场景的 Bug 检测价值高但实现复杂度高。当前 2 表方案覆盖核心语义，可作为未来扩展方向。

### 8.3 MySQL createBaseSelect 条件逻辑

```java
// MySQLJIRTransformer.java:307
if (joinType != null && joinType != JoinType.CROSS || (joinType == JoinType.CROSS)) {
```

简化为 `joinType != null`（因为 `joinType == CROSS` 时第二部分为 true）。虽然功能正确，但逻辑冗余且可读性差。

**建议**：改为 `if (joinType != null)`，与其他 Transformer 一致。

### 8.4 EXISTS 构造模式差异

| DBMS | 当前构造 | 原始构造 |
|------|----------|----------|
| 原始 | `GeneralExist(subquery, false)` → 渲染 "NOT EXISTS (...)" | 单 AST 节点 |
| MySQL | `MySQLExists(subquery, true)` + `MySQLUnaryPrefixOperation.NOT` | 2 节点 |
| PostgreSQL | `PostgresExists(subquery)` + `PostgresPrefixOperation.NOT` | 2 节点 |
| GaussDB-M | `GaussDBExists(subquery, 1)` + `GaussDBUnaryPrefixOperation.NOT` | 2 节点 |
| GaussDB-A | `GaussDBAExists(subquery)` + `GaussDBAUnaryPrefixOperation.NOT` | 2 节点 |

**评价**：2 节点方式功能正确，渲染结果相同（`NOT EXISTS (...)`）。原始的单节点方式更简洁，但需要修改各 DBMS 的 Exists AST 类。**不影响功能，不改**。

---

## 九、已对齐项确认（v2.4.2/v2.4.3 修复验证）

| # | 修复项 | v2.4.2/v2.4.3 状态 | 第三轮验证 |
|---|--------|---------------------|------------|
| 1 | Rule 1 NULL 替换逻辑 | ✅ 完成 | ✅ 正确（单列 + NULL 替换） |
| 2 | MULTISET 比较语义 | ✅ 完成 | ✅ 正确（`assumeResultSetsAreEqualMultiset` + `canonicalizeResultValue`） |
| 3 | GaussDB-A NATURAL JOIN AST | ✅ 完成 | ✅ 正确（`GaussDBAJoinType.NATURAL` + Visitor 渲染） |
| 4 | GaussDB-A Rule 5 ON 1=1 | ✅ 完成 | ✅ 正确（`GaussDBABinaryComparisonOperation(1,1,EQUALS)`） |
| 5 | Rule 1 单列精确验证 | ✅ 完成 | ✅ 正确（`generateRandomFetchColumns()` → 单列） |
| 6 | ORDER BY 支持 | ✅ 完成 | ✅ 正确（低概率 ORDER BY 1） |
| 7 | Reproducer 验证模式 | ✅ 完成 | ✅ 正确（JIRReproducer + bugStillTriggers） |
| 8 | Rule 2-6 随机左表列 | ✅ 完成 | ⚠️ 有 BUG（独立随机选列，详见 #二） |

---

## 十、修复优先级排序

| 优先级 | # | 差异 | 修复方案 | 影响范围 |
|--------|---|------|----------|----------|
| **P0** | 1 | Rules 2-6 独立随机选列 BUG | 生成 fetch columns 一次，共享给 source/target | 4 Transformer × 4-5 Rules |
| **P1** | 2 | PostgreSQL Rule 6 原始 SQL | 为 PostgresJoinType 添加 NATURAL + AST 渲染 | PostgresJIRTransformer |
| **P2** | 3 | 缺少 `*` fetch column 模式 | 以低概率添加 `*` 模式（需 AST 支持） | 4 Transformer |
| **P3** | 6 | Reproducer 缺 errorMessage | 添加 errorMessage 字段 + getErrorMessage() | JIROracle |
| **P4** | 9 | MySQL createBaseSelect 条件冗余 | 简化为 `joinType != null` | MySQLJIRTransformer |
| — | 4,5,7,8 | 低优先级覆盖率/架构差异 | 未来扩展方向 | — |

---

## 十一、P0 修复详细方案

### 核心思路

为每个 Transformer 新增 `createBaseSelectWithCols()` 方法，接受外部指定的 fetch columns。Rules 2-6 在方法入口生成 fetch columns 一次，传递给所有查询变体。

### MySQL 修复示例

```java
// 新增辅助方法
private MySQLSelect createBaseSelectWithCols(JoinType joinType, MySQLExpression onCondition,
        List<MySQLExpression> fetchColumns) {
    MySQLSelect select = new MySQLSelect();
    select.setFetchColumns(fetchColumns);  // ← 使用外部传入的列
    select.setFromList(Collections.singletonList(new MySQLTableReference(leftTable)));
    if (joinType != null) {
        MySQLJoin join = new MySQLJoin(rightTable, onCondition, joinType);
        select.setJoinClauses(Collections.singletonList(join));
    }
    return select;
}

// Rule 2 修复
private JIRQuerySet generateLeftRightSymmetry() {
    MySQLExpression onCondition = gen.generateExpression(0);
    List<MySQLExpression> fetchColumns = getLeftTableFetchColumns();  // ← 生成一次

    MySQLSelect sourceSelect = createBaseSelectWithCols(JoinType.LEFT, onCondition, fetchColumns);
    String sourceSQL = MySQLVisitor.asString(sourceSelect);

    MySQLSelect targetSelect = new MySQLSelect();
    targetSelect.setFetchColumns(fetchColumns);  // ← 共享同一列列表
    targetSelect.setFromList(Collections.singletonList(new MySQLTableReference(rightTable)));
    targetSelect.setJoinClauses(Collections.singletonList(new MySQLJoin(leftTable, onCondition, JoinType.RIGHT)));
    String targetSQL = MySQLVisitor.asString(targetSelect);

    return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
            JIRRule.LEFT_RIGHT_SYMMETRY);
}

// Rule 3 修复（3 个查询共享同一 fetchColumns）
private JIRQuerySet generateSemiAntiComplement() {
    MySQLExpression onCondition = gen.generateExpression(0);
    JoinType joinType = Randomly.fromOptions(JoinType.INNER, JoinType.LEFT, JoinType.RIGHT);
    List<MySQLExpression> fetchColumns = getLeftTableFetchColumns();  // ← 生成一次

    MySQLSelect sourceSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);
    String sourceSQL = MySQLVisitor.asString(sourceSelect);

    MySQLSelect semiSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);  // ← 共享
    semiSelect.setWhereClause(new MySQLExists(existsSubquery, MySQLConstant.createTrue()));
    String semiSQL = MySQLVisitor.asString(semiSelect);

    MySQLSelect antiSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);  // ← 共享
    antiSelect.setWhereClause(notExists);
    String antiSQL = MySQLVisitor.asString(antiSelect);

    return new JIRQuerySet(sourceSQL, Arrays.asList(semiSQL, antiSQL), JIRResultType.UNION_ALL,
            JIRRule.SEMI_ANTI_COMPLEMENT);
}
```

### 4 Transformer 修改清单

| Transformer | Rules 需修复 | 新增方法 |
|-------------|--------------|----------|
| MySQLJIRTransformer | 2, 3, 5, 6 | `createBaseSelectWithCols()` |
| PostgresJIRTransformer | 2, 3, 4, 5, 6 | `createBaseSelectWithCols()` |
| GaussDBMJIRTransformer | 2, 3, 5, 6 | `createBaseSelectWithCols()` |
| GaussDBAJIRTransformer | 2, 3, 4, 5, 6 | `createBaseSelectWithCols()` |

**注意**：Rule 1 不受影响（已正确共享 `generateRandomFetchColumns()` 生成一次的列列表）。

---

## 十二、验收标准

1. `mvn compile -q` 零错误零警告
2. Rules 2-6 的 source/target 查询使用**同一** fetch columns（无独立随机选择）
3. PostgreSQL Rule 6 使用 AST 渲染（非原始 SQL 拼接）
4. 4 DBMS 各执行 `--num-queries 5000`，无误报