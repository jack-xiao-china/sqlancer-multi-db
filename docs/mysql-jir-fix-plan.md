# MySQL JIR Oracle 修复方案 — 严格对齐原生实现

**日期**: 2026-06-17  
**版本**: v2.7.4
**参照**: 原生 GeneralJIROracle.java (sqlancer-scale) + SIGMOD 2026 论文
**原则**: 严格参照原生工具的功能实现，禁止做功能或范围裁剪

---

## 0. 总体原则

1. **严格对齐原生实现**：每个修复点必须参照原生 `GeneralJIROracle.java` 和 `GeneralJoin.java` 的代码逻辑，不做语义裁剪或简化
2. **保留原有规则覆盖**：当前 5 条规则 (Rule 1-3, 5-6) 全部保留，不删除任何规则
3. **MySQL 语法约束**：MySQL 无 FULL JOIN，Rule 4 继续跳过；MySQL 支持 NATURAL LEFT/RIGHT JOIN 但不支持 NATURAL FULL JOIN
4. **逐步递增实施**：按 P0 → P1 → P2 优先级顺序实施，每步独立验证

---

## 1. P0 修复：Rule 3 (SEMI_ANTI_COMPLEMENT) 算法纠正

### 1.1 问题

当前 Rule 3 的 source 查询为 `SELECT {leftCols} FROM L {JOIN} JOIN R ON cond`（带 JOIN），然后在带 JOIN 的查询上加 WHERE EXISTS/NOT EXISTS。这导致语义偏差：

- INNER JOIN + WHERE EXISTS ≈ INNER JOIN（EXISTS 对 INNER JOIN 不改变结果）
- INNER JOIN + WHERE NOT EXISTS ≈ 空集（INNER JOIN 行都满足 EXISTS）
- INNER JOIN ∪ 空 ≠ 左表全部行

### 1.2 论文正确语义

$$L = (L \bowtie_P^{\text{SEMI}}) \uplus (L \bowtie_P^{\text{ANTI}})$$

左表的每行要么有匹配（SEMI），要么无匹配（ANTI），二者合起来等于左表全部行。

### 1.3 原生实现参考

原生 `GeneralJIROracle` 仅实现 Rule 1，不包含 Rule 3。但原生 `GeneralDQPOracle`（DQP oracle）有 SEMI/ANTI 模式，其模式为：
- 对 LEFT JOIN 查询施加 WHERE EXISTS / WHERE NOT EXISTS 过滤
- 但 DQP 的 source 是 LEFT JOIN（保留全部左表行），不是纯左表

对于 JIR Rule 3，论文定义的 source 应为**纯左表行**（不含任何 JOIN），这与 DQP 的用法不同。

### 1.4 修复方案

**修改文件**: `src/sqlancer/mysql/oracle/MySQLJIRTransformer.java`

**修改方法**: `generateSemiAntiComplement()`

**修改后的逻辑**:

```java
private JIRQuerySet generateSemiAntiComplement() {
    MySQLExpression onCondition = gen.generateExpression(0);
    List<MySQLExpression> fetchColumns = generateLeftTableFetchColumns();  // 仅左表列

    // Source: SELECT {leftCols} FROM L  (纯左表，无 JOIN)
    MySQLSelect sourceSelect = new MySQLSelect();
    sourceSelect.setFetchColumns(fetchColumns);
    sourceSelect.setFromList(Collections.singletonList(new MySQLTableReference(leftTable)));
    // 不设置 joinClauses — 纯左表查询
    String sourceSQL = MySQLVisitor.asString(sourceSelect);

    // Build EXISTS subquery: SELECT 1 FROM R WHERE cond
    MySQLSelect existsSubquery = new MySQLSelect();
    existsSubquery.setFetchColumns(Collections.singletonList(MySQLConstant.createIntConstant(1)));
    existsSubquery.setFromList(Collections.singletonList(new MySQLTableReference(rightTable)));
    existsSubquery.setWhereClause(onCondition);

    // Target 1 (SEMI): SELECT {leftCols} FROM L WHERE EXISTS (SELECT 1 FROM R WHERE cond)
    MySQLSelect semiSelect = new MySQLSelect();
    semiSelect.setFetchColumns(fetchColumns);
    semiSelect.setFromList(Collections.singletonList(new MySQLTableReference(leftTable)));
    MySQLExists existsExpr = new MySQLExists(existsSubquery, MySQLConstant.createTrue());
    semiSelect.setWhereClause(existsExpr);
    String semiSQL = MySQLVisitor.asString(semiSelect);

    // Target 2 (ANTI): SELECT {leftCols} FROM L WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond)
    MySQLSelect antiSelect = new MySQLSelect();
    antiSelect.setFetchColumns(fetchColumns);
    antiSelect.setFromList(Collections.singletonList(new MySQLTableReference(leftTable)));
    MySQLExists notExistsSubquery = new MySQLExists(existsSubquery, MySQLConstant.createTrue());
    MySQLUnaryPrefixOperation notExistsExpr = new MySQLUnaryPrefixOperation(notExistsSubquery,
            MySQLUnaryPrefixOperator.NOT);
    antiSelect.setWhereClause(notExistsExpr);
    String antiSQL = MySQLVisitor.asString(antiSelect);

    return new JIRQuerySet(sourceSQL, Arrays.asList(semiSQL, antiSQL), JIRResultType.UNION_ALL,
            JIRRule.SEMI_ANTI_COMPLEMENT);
}
```

**关键变更**:
- Source 从 `SELECT cols FROM L {JOIN} JOIN R ON cond` → `SELECT cols FROM L`（去掉 JOIN）
- Target 从 `Source + WHERE EXISTS/NOT EXISTS` → `SELECT cols FROM L WHERE EXISTS/NOT EXISTS (SELECT 1 FROM R WHERE onCondition)`
- Fetch columns 仅选左表列（source 是纯左表，不含右表列）
- 不再随机选择 JOIN 类型（INNER/LEFT/RIGHT），因为 source 不包含 JOIN

**验证**: source 的 UNION ALL (SEMI + ANTI) 应等于 source 本身（纯左表全部行）

---

## 2. P1-1 修复：多列 Fetch + SELECT * 支持

### 2.1 问题

当前仅支持单列 fetch (`getLeftTableFetchColumns` 返回 1 列，`generateRandomFetchColumns` 返回 1 列)，覆盖面窄，降低了 Bug 检测概率。

### 2.2 原生实现参考

原生 `GeneralJIROracle.generateFetchColumns()` (lines 67-78):
```java
List<Node<GeneralExpression>> generateFetchColumns() {
    List<Node<GeneralExpression>> columns = new ArrayList<>();
    if (Randomly.getBoolean()) {
        // SELECT * — 50% 概率
        columns.add(new ColumnReferenceNode<>(new GeneralSchema.GeneralColumn("*", null, false, false)));
    } else {
        // 多列子集 — 50% 概率
        columns = Randomly.nonEmptySubset(targetTables.getColumns()).stream()
                .map(c -> new ColumnReferenceNode<GeneralExpression, GeneralSchema.GeneralColumn>(c))
                .collect(Collectors.toList());
    }
    return columns;
}
```

### 2.3 修复方案

#### 2.3.1 新增 MySQLWildcard AST 节点

**新建文件**: `src/sqlancer/mysql/ast/MySQLWildcard.java`

```java
package sqlancer.mysql.ast;

/**
 * Represents the wildcard (*) in a SELECT fetch column list.
 * Renders as literal "*" in SQL output.
 */
public class MySQLWildcard implements MySQLExpression {
    @Override
    public MySQLConstant getExpectedValue() {
        return null;
    }
}
```

#### 2.3.2 更新 MySQLToStringVisitor

**修改文件**: `src/sqlancer/mysql/MySQLToStringVisitor.java`

添加 `visit(MySQLWildcard)` 方法:
```java
@Override
public void visit(MySQLWildcard wildcard) {
    sb.append("*");
}
```

#### 2.3.3 新增 generateFetchColumns() 方法

**修改文件**: `src/sqlancer/mysql/oracle/MySQLJIRTransformer.java`

替换现有的 `generateRandomFetchColumns()` 和 `getLeftTableFetchColumns()`，新增两个方法：

```java
/**
 * Generate fetch columns following original GeneralJIROracle pattern:
 * 50% SELECT * vs 50% multi-column random subset from both tables.
 * Used by Rule 1 (LEFT JOIN Decomposition).
 */
private List<MySQLExpression> generateFetchColumns() {
    List<MySQLExpression> columns = new ArrayList<>();
    if (Randomly.getBoolean()) {
        // SELECT * — 50% probability
        columns.add(new MySQLWildcard());
    } else {
        // Multi-column subset from both tables — 50% probability
        List<MySQLColumn> allCols = new ArrayList<>();
        allCols.addAll(leftTable.getColumns());
        allCols.addAll(rightTable.getColumns());
        List<MySQLColumn> subset = Randomly.nonEmptySubset(allCols);
        for (MySQLColumn col : subset) {
            columns.add(MySQLColumnReference.create(col, null));
        }
    }
    return columns;
}

/**
 * Generate fetch columns from left table only:
 * 50% SELECT * vs 50% multi-column random subset from left table.
 * Used by Rules 2, 3, 5, 6 where comparison must be on left-table columns only.
 */
private List<MySQLExpression> generateLeftTableFetchColumns() {
    List<MySQLExpression> columns = new ArrayList<>();
    List<MySQLColumn> leftCols = leftTable.getColumns();
    if (leftCols.isEmpty()) {
        throw new IgnoreMeException();
    }
    if (Randomly.getBoolean()) {
        // SELECT * — 50% probability
        columns.add(new MySQLWildcard());
    } else {
        // Multi-column subset from left table — 50% probability
        List<MySQLColumn> subset = Randomly.nonEmptySubset(leftCols);
        for (MySQLColumn col : subset) {
            columns.add(MySQLColumnReference.create(col, null));
        }
    }
    return columns;
}
```

#### 2.3.4 修复 buildAntiJoinFetchColumns() 支持 SELECT *

**修改文件**: `src/sqlancer/mysql/oracle/MySQLJIRTransformer.java`

参照原生 `GeneralJIROracle` lines 145-167 的两路分支：

```java
/**
 * Build anti-join fetch columns: replace right table columns with NULL constants.
 * Strictly follows original GeneralJIROracle anti-join NULL substitution logic:
 * - SELECT * branch: append *, then rightTable.getColumns().size() NULL columns
 * - Explicit columns branch: replace right-table column references with NULL
 */
private List<MySQLExpression> buildAntiJoinFetchColumns(List<MySQLExpression> fetchColumns) {
    List<MySQLExpression> result = new ArrayList<>();

    // SELECT * branch: *, NULL, NULL, ... (one NULL per right-table column)
    if (fetchColumns.size() == 1 && fetchColumns.get(0) instanceof MySQLWildcard) {
        result.add(new MySQLWildcard());  // * expands to left-table columns (FROM is only L)
        for (int i = 0; i < rightTable.getColumns().size(); i++) {
            result.add(MySQLConstant.createNullConstant());
        }
        return result;
    }

    // Explicit columns branch: replace right-table column references with NULL
    for (MySQLExpression expr : fetchColumns) {
        if (expr instanceof MySQLColumnReference) {
            MySQLColumn col = ((MySQLColumnReference) expr).getColumn();
            if (col.getTable().getName().equals(rightTable.getName())) {
                result.add(MySQLConstant.createNullConstant());
            } else {
                result.add(expr);
            }
        } else {
            result.add(expr);
        }
    }
    return result;
}
```

**关键差异**:
- `SELECT *` 分支：在 `*` 后追加 `rightTable.getColumns().size()` 个 NULL 列（原生 lines 145-153）
- 显式列分支：右表列 → NULL，左表列保持原样（原生 lines 154-167）

#### 2.3.5 更新所有规则使用新的 fetch 方法

| 规则 | 旧方法 | 新方法 | 说明 |
|------|--------|--------|------|
| Rule 1 | `generateRandomFetchColumns()` | `generateFetchColumns()` | 两表列 + SELECT * |
| Rule 2 | `getLeftTableFetchColumns()` | `generateLeftTableFetchColumns()` | 左表列 + SELECT * |
| Rule 3 | `getLeftTableFetchColumns()` | `generateLeftTableFetchColumns()` | 左表列 + SELECT * |
| Rule 5 | `getLeftTableFetchColumns()` | `generateLeftTableFetchColumns()` | 左表列 + SELECT * |
| Rule 6 | `getLeftTableFetchColumns()` | `generateLeftTableFetchColumns()` | 左表列 + SELECT * |

---

## 3. P1-2 修复：多表 JOIN 树支持

### 3.1 问题

当前仅支持 2 表 1 JOIN，无法检测多表 JOIN 组合的 Bug。论文 18/20 Bug 需要多种 JOIN 类型组合。

### 3.2 原生实现参考

原生 `GeneralJoin.getJoins()` (lines 124-139)：
- 从 `tableList` 取前 2 个表构建 1 个 JOIN
- 当前活跃代码路径仅生成 1 个 JOIN（无循环追加更多 JOIN）
- 但框架设计上 `tableList` 可包含更多表

原生 `GeneralSelect.removeLastJoin()` (lines 58-67)：
- 从 joinList 中移除最后一个 JOIN
- 运用于 anti-join 变换：移除最后一个 LEFT JOIN，将其转为 NOT EXISTS

### 3.3 修复方案

#### 3.3.1 扩展 JIROracle 表选择

**修改文件**: `src/sqlancer/common/oracle/jir/JIROracle.java`

当前代码选择至少 2 个表：
```java
List<? extends AbstractTable<?, ?, ?>> selectedTables = Randomly.nonEmptySubsetLeast(allTables, 2);
```

修改为支持 2-4 个表（与原生 `tableList` 对齐，允许更多表以构建多 JOIN 树）：
```java
// Pick 2-4 tables for multi-JOIN tree support
int maxTables = Math.min(allTables.size(), 4);
List<? extends AbstractTable<?, ?, ?>> selectedTables = Randomly.nonEmptySubsetLeast(allTables, 2, maxTables);
```

注：`Randomly.nonEmptySubsetLeast` 已有 `min/max` 参数版本。若无，需添加。

#### 3.3.2 扩展 MySQLJIRTransformer.initialize() 支持多变表

**修改文件**: `src/sqlancer/mysql/oracle/MySQLJIRTransformer.java`

当前仅存储 2 个表：
```java
private MySQLTable leftTable;
private MySQLTable rightTable;
```

扩展为多表列表：
```java
private List<MySQLTable> tables;       // 所有选中的表 (2-4)
private MySQLTable primaryTable;       // 第一个表 (FROM 表)
private MySQLExpressionGenerator gen;
private final ExpectedErrors errors;
```

```java
@Override
public void initialize(MySQLGlobalState state, List<? extends AbstractTable<?, ?, ?>> tables) {
    this.tables = tables.stream().map(t -> (MySQLTable) t).collect(Collectors.toList());
    this.primaryTable = this.tables.get(0);

    List<MySQLColumn> allColumns = new ArrayList<>();
    for (MySQLTable table : this.tables) {
        allColumns.addAll(table.getColumns());
    }
    this.gen = new MySQLExpressionGenerator(state).setColumns(allColumns);
}
```

#### 3.3.3 新增 buildJoinChain() 方法

**修改文件**: `src/sqlancer/mysql/oracle/MySQLJIRTransformer.java`

参照原生 `GeneralJoin.getJoins()` 的模式构建多 JOIN 链：

```java
/**
 * Build a chain of JOINs from the table list.
 * The last JOIN is the transformation target (LEFT/RIGHT/NATURAL/CROSS etc.).
 * Earlier JOINs form the context (random types: INNER/LEFT/RIGHT).
 * Strictly follows original GeneralJoin.getJoins() pattern:
 * - First table goes to FROM list
 * - Each subsequent table gets a JOIN clause with random ON condition
 * - Only 1-2 JOINs per query (matching original's active code path)
 */
private List<MySQLJoin> buildJoinChain(int joinCount, JoinType lastJoinType, MySQLExpression lastOnCondition) {
    List<MySQLJoin> joins = new ArrayList<>();

    // Build preceding JOINs (random types, random ON conditions)
    for (int i = 1; i < joinCount; i++) {  // i=1 is first JOIN, up to joinCount-1
        MySQLTable joinTable = tables.get(i);
        MySQLExpression onCond = gen.generateExpression(0);
        JoinType type = Randomly.fromOptions(JoinType.INNER, JoinType.LEFT, JoinType.RIGHT);
        joins.add(new MySQLJoin(joinTable, onCond, type));
    }

    // Build the last JOIN (transformation target with specified type and ON condition)
    MySQLTable lastTable = tables.get(joinCount);
    joins.add(new MySQLJoin(lastTable, lastOnCondition, lastJoinType));

    return joins;
}
```

**说明**：
- `joinCount` = 1 时仅 1 个 JOIN（2 表），与当前行为一致
- `joinCount` = 2 时 2 个 JOIN（3 表），第一个 JOIN 是上下文，第二个是变换目标
- 最多支持 3 个 JOIN（4 表），与 `maxTables=4` 对齐
- 随机选择 1-2 个 JOIN（低概率选 2），与原生的实际行为一致

#### 3.3.4 新增 MySQLSelect.removeLastJoin() 方法

**修改文件**: `src/sqlancer/mysql/ast/MySQLSelect.java`

参照原生 `GeneralSelect.removeLastJoin()`：

```java
/**
 * Remove the last JOIN clause from this select.
 * Used by JIR anti-join transformation: remove the LEFT JOIN and convert it to NOT EXISTS.
 * Returns the removed join for extracting its table and ON condition.
 */
public MySQLJoin removeLastJoin() {
    List<MySQLExpression> joinList = getJoinList();
    if (joinList.isEmpty()) {
        throw new AssertionError("No joins to remove");
    }
    MySQLExpression last = joinList.remove(joinList.size() - 1);
    return (MySQLJoin) last;
}
```

注：`getJoinList()` 返回 `SelectBase` 的内部可变列表，直接 `remove()` 即可修改。

#### 3.3.5 更新各规则支持多 JOIN 链

**Rule 1 (LEFT_JOIN_DECOMPOSITION)**:

```java
private JIRQuerySet generateLeftJoinDecomposition() {
    MySQLExpression onCondition = gen.generateExpression(0);
    List<MySQLExpression> fetchColumns = generateFetchColumns();
    int joinCount = Randomly.getBooleanWithRatherLowProbability() ? 2 : 1;

    // Source: SELECT {fetchColumns} FROM primaryTable {preceding joins} LEFT JOIN lastTable ON cond
    MySQLSelect sourceSelect = new MySQLSelect();
    sourceSelect.setFetchColumns(fetchColumns);
    sourceSelect.setFromList(Collections.singletonList(new MySQLTableReference(primaryTable)));
    sourceSelect.setJoinClauses(buildJoinChain(joinCount, JoinType.LEFT, onCondition));
    String sourceSQL = MySQLVisitor.asString(sourceSelect);

    // Target 1: Same structure but LEFT → INNER for the last join
    MySQLSelect innerSelect = new MySQLSelect();
    innerSelect.setFetchColumns(fetchColumns);
    innerSelect.setFromList(Collections.singletonList(new MySQLTableReference(primaryTable)));
    List<MySQLJoin> innerJoins = buildJoinChain(joinCount, JoinType.INNER, onCondition);
    innerSelect.setJoinClauses(innerJoins);
    String innerSQL = MySQLVisitor.asString(innerSelect);

    // Target 2: Anti-join — remove last LEFT JOIN, add NOT EXISTS
    MySQLSelect antiSelect = new MySQLSelect();
    antiSelect.setFetchColumns(buildAntiJoinFetchColumns(fetchColumns));
    antiSelect.setFromList(Collections.singletonList(new MySQLTableReference(primaryTable)));
    // Copy preceding joins (without the last LEFT JOIN)
    List<MySQLJoin> precedingJoins = buildJoinChain(joinCount, JoinType.LEFT, onCondition);
    MySQLJoin removedJoin = precedingJoins.remove(precedingJoins.size() - 1);
    antiSelect.setJoinClauses(precedingJoins.isEmpty() ? null : precedingJoins);

    // Build NOT EXISTS subquery using the removed join's table and ON condition
    MySQLSelect existsSubquery = new MySQLSelect();
    existsSubquery.setFetchColumns(Collections.singletonList(MySQLConstant.createIntConstant(1)));
    existsSubquery.setFromList(Collections.singletonList(new MySQLTableReference(removedJoin.getTable())));
    existsSubquery.setWhereClause(removedJoin.getOnClause());

    MySQLExists existsExpr = new MySQLExists(existsSubquery, MySQLConstant.createTrue());
    MySQLUnaryPrefixOperation notExists = new MySQLUnaryPrefixOperation(existsExpr, MySQLUnaryPrefixOperator.NOT);
    antiSelect.setWhereClause(notExists);
    String antiSQL = MySQLVisitor.asString(antiSelect);

    return new JIRQuerySet(sourceSQL, Arrays.asList(innerSQL, antiSQL), JIRResultType.UNION_ALL,
            JIRRule.LEFT_JOIN_DECOMPOSITION);
}
```

**Rule 2 (LEFT_RIGHT_SYMMETRY)**: 类似更新，最后一个 JOIN 是 LEFT → RIGHT 互换

**Rule 3 (SEMI_ANTI_COMPLEMENT)**: source 是纯左表（primaryTable），不需要多 JOIN。但 EXISTS 的 ON condition 可以基于多表列生成。

**Rule 5/6**: 保持 2 表（CROSS/NATURAL JOIN 不适合多表链），但使用 `tables.get(0)` 和 `tables.get(1)` 替代 `leftTable/rightTable`。

---

## 4. P1-3 修复：NATURAL OUTER JOIN 变体

### 4.1 问题

当前 NATURAL JOIN 仅验证 plain NATURAL JOIN (= INNER with equalities)。MySQL 支持 `NATURAL LEFT JOIN` 和 `NATURAL RIGHT JOIN`，但未验证。

### 4.2 原生实现参考

原生 `GeneralJoin.OuterType` enum (lines 27-78):
```java
public enum OuterType {
    FULL, LEFT, RIGHT;
}
```

原生 `GeneralJoin.createNaturalJoin()` (lines 217-222):
```java
static GeneralJoin createNaturalJoin(TableReferenceNode<GeneralExpression, GeneralSchema.GeneralTable> leftTable,
        TableReferenceNode<GeneralExpression, GeneralSchema.GeneralTable> rightTable,
        OuterType naturalJoinType, ...) {
    GeneralJoin naturalJoin = new GeneralJoin(leftTable, rightTable, null, JoinType.NATURAL);
    naturalJoin.setOuterType(naturalJoinType);  // LEFT, RIGHT, FULL, or null for plain NATURAL
    ...
}
```

原生渲染 `NATURAL LEFT JOIN` / `NATURAL RIGHT JOIN` / `NATURAL FULL JOIN` (GeneralToStringVisitor lines 41-55).

### 4.3 MySQL 语法约束

MySQL 支持:
- `NATURAL JOIN` (plain = INNER with equalities)
- `NATURAL LEFT [OUTER] JOIN`
- `NATURAL RIGHT [OUTER] JOIN`

MySQL **不支持** `NATURAL FULL JOIN`（因无 FULL JOIN）。

### 4.4 修复方案

#### 4.4.1 新增 OuterType enum 到 MySQLJoin

**修改文件**: `src/sqlancer/mysql/ast/MySQLJoin.java`

```java
public enum OuterType {
    LEFT, RIGHT;  // No FULL — MySQL lacks FULL JOIN
}
```

新增字段:
```java
private OuterType outerType;  // Nullable; only used when JoinType == NATURAL
```

新增方法:
```java
public OuterType getOuterType() {
    return outerType;
}

public void setOuterType(OuterType outerType) {
    this.outerType = outerType;
}
```

#### 4.4.2 更新 MySQLToStringVisitor 渲染 NATURAL OUTER

**修改文件**: `src/sqlancer/mysql/MySQLToStringVisitor.java`

修改 `visit(MySQLJoin)` 中 NATURAL case:

```java
case NATURAL:
    sb.append("NATURAL ");
    if (join.getOuterType() != null) {
        sb.append(join.getOuterType().name()).append(" ");
    }
    break;
```

渲染结果:
- `outerType=null` → `NATURAL JOIN table` (plain)
- `outerType=LEFT` → `NATURAL LEFT JOIN table`
- `outerType=RIGHT` → `NATURAL RIGHT JOIN table`

#### 4.4.3 更新 Rule 6 支持 NATURAL OUTER 变体

**修改文件**: `src/sqlancer/mysql/oracle/MySQLJIRTransformer.java`

```java
private JIRQuerySet generateNaturalJoinExplication() {
    // Randomly pick NATURAL variant: plain, LEFT, RIGHT
    OuterType outerType = Randomly.fromOptions(
            null,           // plain NATURAL JOIN (= INNER with equalities)
            OuterType.LEFT,  // NATURAL LEFT JOIN (= LEFT with equalities)
            OuterType.RIGHT  // NATURAL RIGHT JOIN (= RIGHT with equalities)
    );

    // Find common column names
    List<MySQLColumn> leftCols = primaryTable.getColumns();
    List<MySQLColumn> rightCols = tables.get(1).getColumns();
    // ... (same equality-building logic as current)

    if (equalities.isEmpty()) {
        return null;  // No common columns → NATURAL JOIN ≡ CROSS JOIN, skip
    }

    // Build AND chain of equalities
    MySQLExpression explicitOn = ...;  // (same as current)

    List<MySQLExpression> fetchColumns = generateLeftTableFetchColumns();

    // Determine target JoinType based on OuterType:
    // null → INNER, LEFT → LEFT, RIGHT → RIGHT
    JoinType targetJoinType;
    if (outerType == null) {
        targetJoinType = JoinType.INNER;
    } else if (outerType == OuterType.LEFT) {
        targetJoinType = JoinType.LEFT;
    } else {
        targetJoinType = JoinType.RIGHT;
    }

    // Source: SELECT {fetchColumns} FROM L NATURAL [{outerType}] JOIN R
    MySQLSelect sourceSelect = new MySQLSelect();
    sourceSelect.setFetchColumns(fetchColumns);
    sourceSelect.setFromList(Collections.singletonList(new MySQLTableReference(primaryTable)));
    MySQLJoin naturalJoin = new MySQLJoin(tables.get(1), null, JoinType.NATURAL);
    naturalJoin.setOuterType(outerType);
    sourceSelect.setJoinClauses(Collections.singletonList(naturalJoin));
    String sourceSQL = MySQLVisitor.asString(sourceSelect);

    // Target: SELECT {fetchColumns} FROM L {targetJoinType} JOIN R ON (equalities)
    MySQLSelect targetSelect = new MySQLSelect();
    targetSelect.setFetchColumns(fetchColumns);
    targetSelect.setFromList(Collections.singletonList(new MySQLTableReference(primaryTable)));
    targetSelect.setJoinClauses(
            Collections.singletonList(new MySQLJoin(tables.get(1), explicitOn, targetJoinType)));
    String targetSQL = MySQLVisitor.asString(targetSelect);

    return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
            JIRRule.NATURAL_JOIN_EXPLICATION);
}
```

**验证等价关系**:
- `NATURAL JOIN` ≡ `INNER JOIN ON (equalities)` — 验证 plain NATURAL
- `NATURAL LEFT JOIN` ≡ `LEFT JOIN ON (equalities)` — 验证 NATURAL LEFT
- `NATURAL RIGHT JOIN` ≡ `RIGHT JOIN ON (equalities)` — 验证 NATURAL RIGHT

---

## 5. P1-4 修复：CROSS JOIN 多变体比较

### 5.1 问题

当前 Rule 5 仅比较 `CROSS JOIN` vs `INNER JOIN ON TRUE`。论文指出 `CROSS JOIN ≡ LEFT JOIN ON TRUE ≡ RIGHT JOIN ON TRUE`，多变体可增加检测面。

### 5.2 论文语义

$$L \times R = L \bowtie_{\text{TRUE}}^{\text{INNER}} = L \bowtie_{\text{TRUE}}^{\text{LEFT}} = L \bowtie_{\text{TRUE}}^{\text{RIGHT}}$$

当 ON 条件为 TRUE 时，所有 JOIN 类型产生相同结果（每行都匹配，无未匹配行）。

### 5.3 修复方案

**修改文件**: `src/sqlancer/mysql/oracle/MySQLJIRTransformer.java`

随机选择一种变体作为 target（每次 check() 测试一种，多次迭代覆盖所有变体）：

```java
private JIRQuerySet generateCrossJoinEquivalence() {
    List<MySQLExpression> fetchColumns = generateLeftTableFetchColumns();

    // Source: SELECT {fetchColumns} FROM L CROSS JOIN R
    MySQLSelect sourceSelect = new MySQLSelect();
    sourceSelect.setFetchColumns(fetchColumns);
    sourceSelect.setFromList(Collections.singletonList(new MySQLTableReference(primaryTable)));
    sourceSelect.setJoinClauses(Collections.singletonList(new MySQLJoin(tables.get(1), null, JoinType.CROSS)));
    String sourceSQL = MySQLVisitor.asString(sourceSelect);

    // Randomly pick target variant: INNER/LEFT/RIGHT ON TRUE
    JoinType targetJoinType = Randomly.fromOptions(JoinType.INNER, JoinType.LEFT, JoinType.RIGHT);
    MySQLExpression onTrue = MySQLConstant.createIntConstant(1);  // ON 1 (TRUE in MySQL)

    MySQLSelect targetSelect = new MySQLSelect();
    targetSelect.setFetchColumns(fetchColumns);
    targetSelect.setFromList(Collections.singletonList(new MySQLTableReference(primaryTable)));
    targetSelect.setJoinClauses(
            Collections.singletonList(new MySQLJoin(tables.get(1), onTrue, targetJoinType)));
    String targetSQL = MySQLVisitor.asString(targetSelect);

    return new JIRQuerySet(sourceSQL, Collections.singletonList(targetSQL), JIRResultType.EQUAL,
            JIRRule.CROSS_JOIN_EQUIVALENCE);
}
```

**说明**: 每次 check() 随机选一种变体（INNER/LEFT/RIGHT ON TRUE），多次迭代自然覆盖所有变体。与 EQUAL 比较模式一致（单 target）。

---

## 6. P2 修复：优化增强

### 6.1 P2-1: WHERE 子句生成

**原生状态**: 原生 `GeneralJIROracle` 调用 `setWhereClause(null)`（固定空 WHERE），不生成 WHERE 条件。

**论文建议**: 随机 WHERE 条件可增加谓词复杂度和 Bug 检测概率。

**方案**: 与原生保持一致（不生成 WHERE），但预留扩展接口。后续可作为独立增强添加。

**不实施原因**: 严格参照原生实现，原生不生成 WHERE。如需添加，应作为独立特性而非本次修复内容。

### 6.2 P2-2: 子查询作为 JOIN 右表

**原生状态**: 原生 `GeneralJoin.getJoinsWithSubquery()` 存在但被 `if(true)` 强制禁用（GeneralJIROracle line 99）。代码结构完整但未激活。

**方案**: 实现子查询 JOIN 支持的代码框架，但同样保持禁用状态（与原生对齐）。具体实现：
- 新增 `MySQLSubquery` AST 节点（包装 MySQLSelect 作为 derived table）
- 更新 `MySQLToStringVisitor` 渲染 `(SELECT ...) AS alias`
- 在 `MySQLJIRTransformer` 中添加 subquery JOIN 生成逻辑（禁用状态）

**优先级**: 低 — 原生也未激活此功能，本次修复以对齐原生活跃功能为主。

### 6.3 P2-3: 多列 ORDER BY

**当前状态**: JIROracle 有低概率 `ORDER BY 1`（单列）。

**方案**: 保持现状（与原生行为一致）。原生也仅使用 `ORDER BY` 单列/低概率。

---

## 7. 结果比较基础设施升级

### 7.1 问题

当前 `ComparatorHelper.getResultSetFirstColumnAsString` 仅读取第 1 列 (`getString(1)`)。对于 `SELECT *` 和多列 fetch，单列比较不足以发现列级别的 Bug（如 NULL 处理错误、列顺序错误）。

### 7.2 方案

新增行级比较方法，读取所有列并拼接为行字符串：

**修改文件**: `src/sqlancer/ComparatorHelper.java`

```java
/**
 * Read all columns from each row and concatenate them into a single string per row.
 * Delimiter: "|" (unlikely to appear in normal data).
 * Applies canonicalization to each column value.
 * Required for JIR Oracle SELECT * and multi-column fetch comparisons.
 */
public static List<String> getResultSetAllColumnsAsString(String queryString, ExpectedErrors errors,
        SQLGlobalState<?, ?> state) throws SQLException {
    if (state.getOptions().logEachSelect()) {
        state.getLogger().writeCurrent(queryString);
        try {
            state.getLogger().getCurrentFileWriter().flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    boolean canonicalizeString = state.getOptions().canonicalizeSqlString();
    SQLQueryAdapter q = new SQLQueryAdapter(queryString, errors, true, canonicalizeString);
    List<String> resultSet = new ArrayList<>();
    SQLancerResultSet result = null;
    try {
        result = q.executeAndGet(state);
        state.getManager().incrementSelectQueryCount();
        if (result == null) {
            throw new IgnoreMeException();
        }
        int columnCount = result.getMetaData().getColumnCount();
        while (result.next()) {
            StringBuilder rowBuilder = new StringBuilder();
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) {
                    rowBuilder.append("|");
                }
                String value = result.getString(i);
                if (value != null) {
                    value = canonicalizeResultValue(value);
                    value = value.replaceAll("[\\.]0+$", "");  // Remove trailing zeros
                }
                rowBuilder.append(value == null ? "NULL" : value);
            }
            resultSet.add(rowBuilder.toString());
        }
    } catch (Exception e) {
        if (e instanceof IgnoreMeException) {
            throw e;
        }
        if (e.getMessage() == null) {
            throw new AssertionError(queryString, e);
        }
        if (errors.errorIsExpected(e.getMessage())) {
            throw new IgnoreMeException();
        }
        throw new AssertionError(queryString, e);
    } finally {
        if (result != null && !result.isClosed()) {
            result.close();
        }
    }
    return resultSet;
}
```

### 7.3 更新 JIROracle 使用行级比较

**修改文件**: `src/sqlancer/common/oracle/jir/JIROracle.java`

将所有 `ComparatorHelper.getResultSetFirstColumnAsString` 替换为 `ComparatorHelper.getResultSetAllColumnsAsString`：

```java
// Source query execution
sourceResult = ComparatorHelper.getResultSetAllColumnsAsString(sourceQuery, errors, state);

// Target query execution (EQUAL)
targetResult = ComparatorHelper.getResultSetAllColumnsAsString(targetQuery, errors, state);

// Target query execution (UNION_ALL)
targetResult = ComparatorHelper.getResultSetAllColumnsAsString(combinedTargetQuery, errors, state);
```

同时更新 JIRReproducer 使用相同方法：

```java
List<String> sourceResult = ComparatorHelper.getResultSetAllColumnsAsString(
        sourceQuery, errors, globalState);
List<String> targetResult = ComparatorHelper.getResultSetAllColumnsAsString(
        combinedTargetQuery, errors, globalState);
```

**影响范围**: 仅影响 JIROracle 及其子类。其他 Oracle（TLP、NOREC、CERT 等）不受影响，仍使用 `getResultSetFirstColumnAsString`。

---

## 8. 完整文件修改清单

| 操作 | 文件路径 | 修改内容 |
|------|----------|----------|
| **新建** | `src/sqlancer/mysql/ast/MySQLWildcard.java` | `*` 通配符 AST 节点 |
| **修改** | `src/sqlancer/mysql/ast/MySQLJoin.java` | 新增 `OuterType` enum + `outerType` 字段 |
| **修改** | `src/sqlancer/mysql/ast/MySQLSelect.java` | 新增 `removeLastJoin()` 方法 |
| **修改** | `src/sqlancer/mysql/MySQLToStringVisitor.java` | 新增 `visit(MySQLWildcard)` + NATURAL OUTER 渲染 |
| **修改** | `src/sqlancer/mysql/oracle/MySQLJIRTransformer.java` | 全面重构：P0 Rule 3 + P1 全部修复 |
| **修改** | `src/sqlancer/common/oracle/jir/JIROracle.java` | 多表选择 + 行级比较 |
| **修改** | `src/sqlancer/ComparatorHelper.java` | 新增 `getResultSetAllColumnsAsString()` |
| **修改** | `docs/release_notes.md` | 版本更新 |
| **修改** | `pom.xml` | 版本号更新 |

---

## 9. 实施顺序

```
Step 1: P0 — Rule 3 算法纠正 (最关键)
  → verify: source=纯左表, SEMI+ANTI=source

Step 2: P1-1 — 多列 Fetch + SELECT * + MySQLWildcard
  → verify: SELECT * 渲染正确, anti-join NULL 列追加正确

Step 3: P1-3 — NATURAL OUTER JOIN (OuterType + visitor)
  → verify: NATURAL LEFT/RIGHT JOIN 渲染正确, 等价验证通过

Step 4: P1-4 — CROSS JOIN 多变体
  → verify: CROSS ≡ INNER/LEFT/RIGHT ON TRUE

Step 5: P1-2 — 多表 JOIN 树 + removeLastJoin + JIROracle 多表选择
  → verify: 3 表 JOIN 链正确, anti-join 移除最后一个 JOIN 正确

Step 6: 结果比较基础设施升级 (getResultSetAllColumnsAsString)
  → verify: 行级比较正确, NULL 值处理正确

Step 7: Release Notes + 版本号更新
  → verify: 编译通过, 冒烟测试通过
```

---

## 10. 验证方案

1. **编译验证**: `mvn clean compile -DskipTests` 确保 `-failOnWarning` 通过
2. **冒烟测试**: 在 MySQL 上运行 JIR Oracle 60 秒
   ```
   java -jar sqlancer.jar --num-queries 100 mysql --oracle JIR
   ```
3. **规则覆盖验证**: 确保 5 条规则均有非零概率触发
4. **SELECT * 验证**: 检查日志中出现 `SELECT *` 和 `*, NULL, NULL` 模式
5. **NATURAL OUTER 验证**: 检查日志中出现 `NATURAL LEFT JOIN` 和 `NATURAL RIGHT JOIN`
6. **CROSS JOIN 多变体验证**: 检查日志中出现 `LEFT JOIN ON 1` 和 `RIGHT JOIN ON 1`
7. **多表 JOIN 验证**: 检查日志中出现 3 表 JOIN 查询
8. **Rule 3 验证**: source 查询不含 JOIN，仅 `FROM L WHERE EXISTS/NOT EXISTS`
