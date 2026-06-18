# PostgreSQL JIR Oracle 规则审视与差距分析

> 审视时间：2026-06-17 | 审核修订：2026-06-17（Agent 审核 7/10 + Codex MCP 审核 8/10）
> 参照：原生论文 (SIGMOD 2026) + 原生实现 (sqlancer-scale GeneralJIROracle) + MySQL v2.7.5 已修复版本
> 审视对象：`PostgresJIRTransformer.java` (6 条规则实现)
> 审核补充：4 个遗漏差距 (P0-4, P1-5, P1-6, **P1-7**) + 2 个事实纠错 + 2 个修复代码 bug + PG 表继承假阳性风险
> **修复状态更新 (v2.7.5-v2.7.6)**：P0（SELECT * / Fetch 列 / Anti-join NULL / NATURAL OUTER / Rule 3 source）已在 v2.7.4-v2.7.5 修复；P1-1（多表 JOIN）v2.7.6 修复；P1-3（CROSS 4 变体）v2.7.6 修复；P1-4（Rule 4 full-anti）v2.7.6 修复

---

## 1. 总览对比表

| 维度 | 原生 GeneralJIROracle | MySQL v2.7.5 (已修复) | PostgreSQL 当前 | 差距等级 |
|------|----------------------|----------------------|----------------|---------|
| 规则覆盖 | 仅 Rule 1 | 5 条 (无 Rule 4) | 6 条 (含 Rule 4) | ✅ 覆盖最全 |
| Fetch 列数 | 50/50 SELECT * vs 多列子集 | 50/50 SELECT * vs 多列子集 | **仅单列** | **P0** |
| SELECT * AST | `GeneralColumn("*")` | `MySQLWildcard` | **无** | **P0** |
| Anti-join NULL 替换 | SELECT * 分支 + 显式列分支 | SELECT * 分支 + 显式列分支 | **仅显式列分支** | **P0** |
| NATURAL OUTER JOIN | `OuterType` (FULL, LEFT, RIGHT) | `OuterType` (LEFT, RIGHT) | **无 OuterType** | **P0** |
| Rule 3 source 查询 | 纯左表 (论文定义) | 纯左表 (v2.7.4 修复) | **带 JOIN** | **P0** |
| 多表 JOIN 链 | 2+ 表, 多 JOIN | 2-4 表, 1-2 JOIN 链 | **仅 2 表, 单 JOIN** | **P1** |
| removeLastJoin() | `GeneralSelect.removeLastJoin()` | `MySQLSelect.removeLastJoin()` | **无** | **P1** |
| CROSS JOIN 多变体 | — | INNER/LEFT/RIGHT ON TRUE | 仅 INNER JOIN ON TRUE | **P1** |
| 结果比较 | 列级 (仅第 1 列) ⚠️ | 行级 (getResultSetAllColumnsAsString) | 行级 (共享 JIROracle) | ✅ (PG > 原生) |
| Rule 6 SELECT * 列数不匹配 | — | ⚠️ 潜在 bug | **缺失** | **P0-4 (新增)** |
| removeLastJoin() 类型 bug | — | ✅ | **代码 bug** | **P1-5 (新增)** |
| PostgresJoin 双构造器不兼容 | — | ✅ 单构造器 | **leftTable=null** | **P1-6 (新增)** |
| **PostgresFromTable 随机 * 后缀** | — | ✅ 无此问题 | **50% 假阳性风险** | **P1-7 (Codex 新增)** |
| Subquery as JOIN | `GeneralSubquery` (当前禁用) | P2-1 (未实施) | 无 | Info |

---

## 2. P0 级差距（算法偏差 / 功能缺失）

### P0-1: Rule 3 算法偏差 — Source 查询不应包含 JOIN

**当前实现** (`PostgresJIRTransformer.generateSemiAntiComplement()` lines 153-179):
```java
// Source: SELECT {fetchColumns} FROM L {type} JOIN R ON cond
PostgresSelect sourceSelect = createBaseSelectWithCols(joinType, onCondition, fetchColumns);
```

**论文正确定义**：
$$L = (L \bowtie_P^{SEMI}) \uplus (L \bowtie_P^{ANTI})$$

即 Source 应为纯左表 `SELECT cols FROM L`（不含 JOIN），SEMI = `L WHERE EXISTS(...)`，ANTI = `L WHERE NOT EXISTS(...)`。

**偏差影响**：
- Source 包含 JOIN → SEMI + ANTI 的 UNION_ALL 等于带 JOIN 的 Source（而非纯左表）
- 对于 INNER JOIN：SEMI + ANTI = Source 在语义上仍成立（因为 INNER JOIN 只返回匹配行，EXISTS 对所有匹配行为 true），但测试的不是论文定义的 Rule 3
- 论文定义测试的是 **EXISTS/NOT EXISTS 优化器路径**（纯左表 + 子查询），当前实现测试的是 **JOIN + EXISTS 组合路径**，覆盖面较弱

**⚠️ 审核补充**：当前 Rule 3 还随机选择 INNER/LEFT/RIGHT/FULL 作为 joinType。对于 INNER joinType，SEMI 查询总是等于 Source（EXISTS 对所有 INNER JOIN 匹配行为 true），ANTI 总是空集，测试平凡通过。对于 RIGHT/FULL joinType，语义更加混乱（右反连接行中左表列可能为 NULL）。修复后应移除 joinType 选择，Rule 3 仅适用于纯左表，SEMI/ANTI 不涉及 JOIN 类型。

**修复方案**：
```java
// Source: SELECT {leftCols} FROM L (纯左表，不含 JOIN)
PostgresSelect sourceSelect = new PostgresSelect();
sourceSelect.setFetchColumns(fetchColumns);
sourceSelect.setFromList(Collections.singletonList(new PostgresFromTable(leftTable, false)));
sourceSelect.setAllowForClause(false);
String sourceSQL = PostgresVisitor.asString(sourceSelect);

// SEMI: SELECT {leftCols} FROM L WHERE EXISTS (SELECT 1 FROM R WHERE cond)
PostgresSelect semiSelect = new PostgresSelect();
semiSelect.setFetchColumns(fetchColumns);
semiSelect.setFromList(Collections.singletonList(new PostgresFromTable(leftTable, false)));
semiSelect.setAllowForClause(false);
semiSelect.setWhereClause(new PostgresExists(buildExistsSubquery(onCondition)));

// ANTI: SELECT {leftCols} FROM L WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond)
PostgresSelect antiSelect = new PostgresSelect();
antiSelect.setFetchColumns(fetchColumns);
antiSelect.setFromList(Collections.singletonList(new PostgresFromTable(leftTable, false)));
antiSelect.setAllowForClause(false);
PostgresPrefixOperation notExists = new PostgresPrefixOperation(
    new PostgresExists(buildExistsSubquery(onCondition)), PrefixOperator.NOT);
antiSelect.setWhereClause(notExists);
```

**对齐参照**：MySQL v2.7.4 修复（commit 74414e6 同系列），`MySQLJIRTransformer.generateSemiAntiComplement()` lines 171-203。

---

### P0-2: 仅单列 Fetch — 缺少 SELECT * 和多列模式

**当前实现**：
- `generateRandomFetchColumns()` (Rule 1): `Collections.singletonList(PostgresColumnValue.create(Randomly.fromList(allCols), null))` — 仅 1 列
- `getLeftTableFetchColumns()` (Rules 2-6): `Collections.singletonList(PostgresColumnValue.create(Randomly.fromList(cols), null))` — 仅 1 列

**原生实现** (`GeneralJIROracle.generateFetchColumns()` lines 67-78):
```java
if (Randomly.getBoolean()) {
    columns.add(new ColumnReferenceNode<>(new GeneralColumn("*", null, false, false)));  // SELECT *
} else {
    columns = Randomly.nonEmptySubset(targetTables.getColumns()).stream()
            .map(c -> new ColumnReferenceNode<>(c)).collect(Collectors.toList());  // 多列子集
}
```

**偏差影响**：
- 仅单列 fetch → 仅检测单列的 JOIN 优化器 bug，遗漏多列组合下的 bug
- 无 SELECT * → 遗漏 `*` 列展开路径的优化器 bug（原生 50% 概率使用 SELECT *）
- Anti-join 缺少 SELECT * 分支：原生有两条路径（`*` + 右表列数 NULLs / 显式列右表列 → NULL 替换），PostgreSQL 仅显式列分支

**修复方案**：
1. 新增 `PostgresWildcard` AST 节点（类似 `MySQLWildcard`）
2. 新增 `generateFetchColumns()` — 50/50 SELECT * vs 多列随机子集（两表列）
3. 新增 `generateLeftTableFetchColumns()` — 50/50 SELECT * vs 多列随机子集（仅左表列）
4. 更新 `buildAntiJoinFetchColumns()` — 增加 SELECT * 分支（`*` + 右表列数个 NULL）
5. 注册 `PostgresWildcard` 到 `PostgresVisitor` + `PostgresToStringVisitor` + `PostgresExpectedValueVisitor`

**关键注意**：`PostgresToStringVisitor.visit(PostgresSelect)` 当 `getFetchColumns() == null` 时输出 `*`（line 263-264），但这是 NULL（未设置 fetch columns）的 fallback，不是显式 Wildcard AST 节点。Wildcard 节点放在 fetch columns 列表中，visitor 需要识别并渲染为 `*`，**且不追加 AS ref<N> 别名**（MySQL 的 BUG-1 教训）。

**⚠️ 审核补充 (P0-4)**：Rule 6 (NATURAL_JOIN_EXPLICATION) **必须禁止使用 SELECT * 作为 fetch columns**。

NATURAL JOIN 会去重公共列名（结果列数 = `|L| + |R| - |common|`），而 INNER JOIN ON (equalities) 不去重（结果列数 = `|L| + |R|`）。如果 Rule 6 的 fetch columns 使用 `SELECT *`，source 和 target 的结果列数不同，导致 `ComparatorHelper.assumeResultSetsAreEqualMultiset` 产生假阳性断言失败。

此问题同样存在于 MySQL JIR 的 Rule 6（`generateLeftTableFetchColumns()` 可能生成 MySQLWildcard），但尚未被触发（MySQL 不支持 NATURAL LEFT/RIGHT JOIN 与 SELECT * 的组合测试）。

**修复方案修正**：
- Rule 6 使用专门的 `getLeftTableFetchColumnsNoWildcard()` 方法（仅显式列引用，不含 SELECT *）
- 其他规则 (1-5) 使用 50/50 SELECT * vs 多列的 `generateLeftTableFetchColumns()`

---

### P0-3: NATURAL JOIN 无 OuterType — 缺少 NATURAL LEFT/RIGHT/FULL OUTER JOIN

**当前实现**：
- `PostgresJoinType.NATURAL` 是扁平枚举值，无 OuterType
- `PostgresToStringVisitor` 渲染：`NATURAL JOIN`（line 292）
- Rule 6 source：仅 `SELECT cols FROM L NATURAL JOIN R`

**原生实现** (`GeneralJoin.OuterType`):
```java
public enum OuterType { FULL, LEFT, RIGHT; }
// NATURAL JOIN 可带 OuterType → NATURAL LEFT JOIN, NATURAL RIGHT JOIN, NATURAL FULL JOIN
```

**PostgreSQL 语法支持**：
PostgreSQL 完全支持 `NATURAL LEFT OUTER JOIN`、`NATURAL RIGHT OUTER JOIN`、`NATURAL FULL OUTER JOIN` 语法。

**偏差影响**：
- 仅测试 plain NATURAL JOIN ≡ INNER JOIN ON (equalities)
- 遗漏 NATURAL LEFT JOIN ≡ LEFT JOIN ON (equalities) 的等价性 bug
- 遗漏 NATURAL RIGHT JOIN ≡ RIGHT JOIN ON (equalities) 的等价性 bug
- 遗漏 NATURAL FULL JOIN ≡ FULL JOIN ON (equalities) 的等价性 bug（PG 特有，MySQL 无此语法）

**修复方案**：
1. 新增 `PostgresOuterType` enum（或直接在 `PostgresJoin` 上加 `outerType` 字段），值为 `LEFT, RIGHT, FULL`
2. `PostgresToStringVisitor` 渲染 NATURAL JOIN 时追加 OuterType：**必须使用 PostgreSQL 语法 `NATURAL {LEFT|RIGHT|FULL} OUTER JOIN`**（带 OUTER 关键字，与 PG 已有的 LEFT OUTER JOIN / RIGHT OUTER JOIN 渲染风格一致）
3. Rule 6 随机选 OuterType（null/LEFT/RIGHT/FULL），target 对应 INNER/LEFT/RIGHT/FULL JOIN ON (equalities)

**⚠️ 审核补充**：渲染格式需用 `OUTER` 关键字。原生的 `GeneralToStringVisitor` 渲染为 `NATURAL LEFT JOIN`（不带 OUTER），但当前 PostgreSQL visitor 已对 LEFT/RIGHT/FULL 使用 `OUTER`（lines 281-287: `LEFT OUTER JOIN`、`RIGHT OUTER JOIN`、`FULL OUTER JOIN`），因此 NATURAL OUTER JOIN 也应保持一致风格。具体渲染逻辑：
```java
case NATURAL:
    sb.append("NATURAL ");
    if (j.getOuterType() != null) {
        switch (j.getOuterType()) {
        case LEFT:  sb.append("LEFT OUTER "); break;
        case RIGHT: sb.append("RIGHT OUTER "); break;
        case FULL:  sb.append("FULL OUTER "); break;
        }
    }
    sb.append("JOIN");
    break;
```

**⚠️ 审核补充 (P0-4)**：NATURAL JOIN 去重公共列名，INNER JOIN ON (equalities) 不去重。Rule 6 的 fetch columns 必须避免 SELECT *，且应仅引用左表列或非公共列。详见 P0-2 中的审核补充。

---

## 3. P1 级差距（功能增强 / 覆盖面扩展）

### P1-1: 仅 2 表单 JOIN — 缺少多表 JOIN 链

**当前实现**：
- `initialize()` 固定取 `tables.get(0)` 和 `tables.get(1)` — 仅 2 表
- 所有 SELECT 都是 `FROM L JOIN R ON cond` — 仅 1 个 JOIN

**原生实现** (`GeneralJIROracle.check()` lines 93-103):
```java
List<GeneralSchema.GeneralTable> tables = targetTables.getTables();
// 可有 2+ 表
joins = GeneralJoin.getJoins(tableList, state);  // 构建 1+ JOIN 链
select.setJoinList(joins);  // 多个 JOIN
```

**偏差影响**：
- 仅 2 表 → 无法测试多表 JOIN 优化器路径（3-4 表的 JOIN 优化器可能产生不同执行计划）
- 仅单 JOIN → 无法测试 JOIN 链中仅变换最后一个 JOIN 的场景（原生行为）

**修复方案**：
1. `initialize()` 存储 `List<PostgresTable>`（而非固定 leftTable/rightTable）
2. 新增 `buildJoinChain()` 方法（类似 MySQL 的 `buildJoinChain()`）
3. 新增 `decideJoinCount()` — 1 JOIN（高概率）或 2 JOIN（低概率）
4. 需要 `PostgresSelect.removeLastJoin()` 方法支持

---

### P1-2: PostgresSelect 缺少 removeLastJoin()

**原生实现** (`GeneralSelect.removeLastJoin()` lines 58-67):
```java
public void removeLastJoin() {
    List<Node<GeneralExpression>> joinClauses = this.getJoinList();
    GeneralJoin lastJoin = (GeneralJoin) joinClauses.getLast();
    joinClauses.removeLast();
    this.setJoinList(joinClauses);
    List<Node<GeneralExpression>> leftTable = this.getFromList();
    leftTable.add(lastJoin.getLeftTable());
    this.setFromList(leftTable.stream().collect(Collectors.toList()));
}
```

**当前 PostgreSQL**：`PostgresSelect` 无此方法。

**修复方案**（⚠️ 审核修正）：
在 `PostgresSelect` 中新增 `removeLastJoin()`，**注意类型与原生不同**：
```java
public PostgresJoin removeLastJoin() {
    List<PostgresJoin> joins = new ArrayList<>(getJoinClauses());
    if (joins.isEmpty()) { throw new AssertionError("No joins to remove"); }
    PostgresJoin removed = joins.remove(joins.size() - 1);
    setJoinClauses(joins);
    // 不需要修改 fromList — PostgreSQL 的 FROM 总是包含主表，JOIN 仅引用右表 (tableReference)
    return removed;
}
```

**⚠️ P1-5 (审核新增)**：原文档提出的代码有类型错误 — `getJoinClauses()` 返回 `List<PostgresJoin>`，不是 `List<PostgresExpression>`。上述修正代码使用正确类型。

**⚠️ P1-6 (审核新增)**：原生的 `GeneralSelect.removeLastJoin()` 将 `lastJoin.getLeftTable()` 加入 fromList，但 PostgreSQL 的 `PostgresJIRTransformer` 使用第一个构造器 `PostgresJoin(tableReference, onClause, type)`，此构造器 `leftTable=null`。调用 `getLeftTable()` 返回 null，加入 fromList 会导致 SQL 渲染崩溃。因此 PostgreSQL 版本的 `removeLastJoin()` 不需要修改 fromList — FROM 总是包含主表 (primaryTable)。

---

### P1-3: CROSS JOIN 单变体 — 仅 INNER JOIN ON TRUE

**当前实现** (`generateCrossJoinEquivalence()` lines 208-222):
- Target: 仅 `INNER JOIN ON TRUE`

**MySQL v2.7.5**：
- Target: 随机选 `INNER/LEFT/RIGHT JOIN ON TRUE`

**分析**：
- `CROSS JOIN ≡ INNER JOIN ON TRUE`（两表非空时） — 正确
- `CROSS JOIN ≡ LEFT JOIN ON TRUE` — 当右表非空时，LEFT JOIN ON TRUE 的所有左行都有匹配，结果同 CROSS JOIN
- `CROSS JOIN ≡ RIGHT JOIN ON TRUE` — 同理
- 但 `LEFT JOIN ON TRUE` 和 `RIGHT JOIN ON TRUE` 在右表/左表空时行为不同

**修复方案**：
随机选 `INNER/LEFT/RIGHT/FULL JOIN ON TRUE` 作为 target（PostgreSQL 支持 FULL JOIN，可额外覆盖 FULL JOIN ON TRUE 变体）。

---

### P1-4: Rule 4 (FULL JOIN Decomposition) 右反连接不完整

**当前实现** (`buildReverseAntiJoinSQL()` lines 377-395):
```java
reverseAntiSelect.setFetchColumns(Collections.singletonList(PostgresConstant.createNullConstant()));
// 仅 SELECT NULL — 1 列
```

**论文定义**：
FULL JOIN = INNER UNION ALL left-anti UNION ALL right-anti

- right-anti 应包含：左表列全为 NULL + 右表列原始值
- 当前仅 `SELECT NULL`（1 列），无法检测多列组合下的 FULL JOIN 优化器 bug

**⚠️ Codex 审核修正**：对于当前单列 fetch（仅取左表 1 列），right-anti 返回 `NULL` 实际是**正确的**——因为 FULL JOIN source 中 unmatched R rows 的左表列值就是 NULL。P1-4 仅在 P0-2（多列 fetch）实施后才变得关键。当前是**潜在差距**（latent），不产生错误结果。

**修复方案**：
- 当 fetch columns 包含左表和右表列时：
  - right-anti = 左表列 → NULL, 右表列保留
  - 这样 INNER + left-anti + right-anti 的列数与 FULL JOIN 的列数一致
- 若采用 P0-2 修复（多列 fetch），此问题自动解决

---

### P1-7: PostgresFromTable 随机 * 后缀 — 假阳性风险 **(Codex 审核新增)**

**当前实现** (`PostgresToStringVisitor.java` lines 104-109):
```java
@Override
public void visit(PostgresFromTable from) {
    if (from.isOnly()) {
        sb.append("ONLY ");
    }
    sb.append(from.getTable().getName());
    if (!from.isOnly() && Randomly.getBoolean()) {
        sb.append("*");  // ← 50% 概率追加 *，表示"包含继承子表"
    }
}
```

**问题机制**：
- `PostgresJIRTransformer` 在所有 FROM 子句中使用 `new PostgresFromTable(table, false)`
- `isOnly()=false` → 每次渲染时 `Randomly.getBoolean()` 独立决定是否追加 `*`
- 每个 source/target 查询是独立的 `PostgresSelect` 对象，由各自的 `PostgresVisitor.asString()` 渲染
- Source 可能得到 `FROM t1*`（包含继承子表），Target 可能得到 `FROM t1`（不含子表）
- PostgreSQL 表继承语义：`FROM t1*` = `t1` + 所有继承子表的行，`FROM t1` = 仅 `t1` 的行
- 结果行数不同 → 假阳性断言失败

**影响范围**（Codex 逐行确认）：
| 行号 | 规则 | 用途 |
|------|------|------|
| 102 | Rule 1 source | `new PostgresFromTable(leftTable, false)` |
| 111 | Rule 1 inner target | `new PostgresFromTable(leftTable, false)` |
| 138 | Rule 2 target | `new PostgresFromTable(rightTable, false)` |
| 310 | Rule 1 anti-join | `new PostgresFromTable(leftTable, false)` |
| 329 | Rules 2-6 base select | `new PostgresFromTable(leftTable, false)` |
| 354 | EXISTS subquery | `new PostgresFromTable(rightTable, false)` |
| 363 | buildAntiJoinSQL | `new PostgresFromTable(leftTable, false)` |
| 380 | reverse anti-join | `new PostgresFromTable(rightTable, false)` |
| 386 | reverse EXISTS | `new PostgresFromTable(leftTable, false)` |

**具体示例**：
```sql
-- Source (随机得到 t1*): SELECT c0 FROM t1* LEFT JOIN t2 ON cond  → 10 行 (5 from t1 + 5 from child)
-- Target (随机得到 t1):  SELECT c0 FROM t1 INNER JOIN t2 ON cond   → 5 行 (仅 t1)
-- Anti-join (随机得到 t1): SELECT NULL FROM t1 WHERE NOT EXISTS ... → 0 行
-- UNION ALL: 5 + 0 = 5 ≠ 10 → FALSE POSITIVE
```

**修复方案**（Codex 推荐）：
将所有 `new PostgresFromTable(table, false)` 替换为 `new PostgresTableReference(table)`：
```java
// Before:
sourceSelect.setFromList(Collections.singletonList(new PostgresFromTable(leftTable, false)));
// After:
sourceSelect.setFromList(Collections.singletonList(new PostgresTableReference(leftTable)));
```

`PostgresToStringVisitor.visit(PostgresTableReference)` (lines 121-123) 仅渲染 `ref.getTable().getName()`，无随机 `*` 后缀，完全确定性。

---

## 4. Info 级信息（非差距，但值得记录）

### Info-1: PostgreSQL 支持 FULL JOIN（Rule 4 特有）

PostgreSQL 是 4 款目标数据库中唯一支持 `FULL OUTER JOIN` 的，因此 Rule 4 (FULL_JOIN_DECOMPOSITION) 仅在 PostgreSQL 上启用。MySQL 和 GaussDB-M 无此规则。当前 Rule 4 实现结构正确（source = FULL JOIN, target = INNER + left-anti + right-anti），但受 P0-2（单列 fetch）和 P1-4（right-anti 仅 1 列 NULL）影响。

### Info-2: EXISTS 子查询关联性

`buildExistsSubquery()` 将 `onCondition`（引用 L + R 列）放入 `SELECT 1 FROM R WHERE onCondition`。PostgreSQL 自动将 L 列引用解析为外层查询（correlated subquery），语义正确。无需修改。

### Info-3: PostgresToStringVisitor 中 JOIN 渲染

`PostgresToStringVisitor.visit(PostgresSelect)` lines 271-304 内联渲染 JOIN（无独立 `visit(PostgresJoin)` 方法）。修改 OuterType 时需在此处追加逻辑，不影响已有渲染。

### Info-4: 共享 JIROracle 行级比较

`JIROracle` 已使用 `ComparatorHelper.getResultSetAllColumnsAsString()`（行级比较，所有列拼接为 `|` 分隔字符串），PostgreSQL 自动继承此能力。无需修改。

### Info-5: 原生 GeneralSubquery（子查询作为 JOIN 右表）

原生 `GeneralJoin.getJoinsWithSubquery()` 支持子查询作为 JOIN 右表，但当前被 `if (true)` 禁用。MySQL P2-1 也暂未实施。PostgreSQL 同样暂不实施，列为 P2 级。

### Info-6: NATURAL JOIN NULL 匹配语义（审核确认）

PostgreSQL NATURAL JOIN 不匹配 NULL=NULL（SQL 中 NULL 比较永远不为 true）。显式 INNER JOIN ON (L.col = R.col) 也不匹配 NULL（因为 `L.col = R.col` 对 NULL 值求值为 NULL，在 ON 中视为 false）。因此 NATURAL JOIN ≡ INNER JOIN ON (equalities) 在 NULL 值存在时语义等价仍然成立。无需额外适配。

### Info-7: 结果比较规范化（审核确认）

`ComparatorHelper.canonicalizeResultValue()` 仅规范化 `-0.0 → 0.0` 和 `-0 → 0`。PostgreSQL 特有数据类型（BOOLEAN 渲染为 `t`/`f`、TIMESTAMP 时间戳文本、INTERVAL `1 day` vs `24 hours`）的规范化依赖 JDBC `getString()` 的文本一致性。由于 JIR 比较中 source 和 target 查询产生相同列类型，JDBC 返回一致的文本表示。目前不是问题，但若未来实现导致类型不匹配（如 Rule 2 LEFT JOIN vs RIGHT JOIN 的列顺序差异），可能需要额外规范化。目前 Info 级别。

### Info-8: Anti-join 非列值表达式处理（审核补充）

`buildAntiJoinFetchColumns()` (lines 286-301) 仅处理 `PostgresColumnValue` 实例。非列表达式（如 PostgresAggregate、PostgresFunction）如果内部引用右表列，在 anti-join 查询（FROM L only, 无 R）中会导致 SQL 错误（"column R.col not found"），被 expected errors 捕获为 IgnoreMeException。这会静默跳过有效测试案例。当前单列 fetch 总是生成 PostgresColumnValue，因此不影响，但 P0-2 实施后（多列 fetch 可能包含函数/聚合表达式），此问题将变得相关。

### Info-9: 原生结果比较方法（审核纠错）

总览对比表中"原生 GeneralJIROracle → 行级 (所有列)"有误。审核确认：原生 `GeneralJIROracle` 使用 `ComparatorHelper.getResultSetFirstColumnAsString`（仅第 1 列），不是所有列。共享 `JIROracle` 使用 `getResultSetAllColumnsAsString`（行级比较）是对原生的**改进**，而非对齐。修正后总览表已更新。

### Info-10: Rule 1 generateRandomFetchColumns() 包含右表列（Codex 纠错）

文档 P0-2 部分暗示 Rule 1 的 fetch columns 仅来自左表。**实际 `generateRandomFetchColumns()` (lines 275-280) 从两表列池 (`allCols`) 中选取**，可选取右表列。`buildAntiJoinFetchColumns()` 正确处理此情况：右表列 → NULL 替换。因此 Rule 1 的 anti-join NULL 替换路径**已被行使**，只是单列范围。文档此前暗示此路径未行使，有误。

### Info-11: NATURAL JOIN NULL 匹配语义（Codex 确认）

Codex 独立验证：PostgreSQL NATURAL JOIN 使用 `=` 运算符（不是 `IS NOT DISTINCT FROM`）。显式 `INNER JOIN ON (L.col = R.col)` 也使用 `=`。`NULL = NULL` 在两者中均求值为 NULL（视为 false）。因此 NATURAL JOIN ≡ INNER JOIN ON (equalities) 在 NULL 值存在时语义等价仍成立。无需额外适配。这与 Agent 审核结论一致。

### Info-12: EXISTS 子查询隐式关联（Codex 确认）

`buildExistsSubquery()` 将 `onCondition`（引用 L + R 列）放入 `SELECT 1 FROM R WHERE onCondition`。L 列引用自动解析为外层 correlated reference。关联是隐式的（无显式 `L.col` 限定）。如果左右表有相同列名，PostgreSQL 可能报 "ambiguous column" 错误，但被 `ExpectedErrors` 捕获。当前实现正确，无假阳性风险。

### Info-13: UNION ALL NULL 类型推断（Codex 确认）

Rule 1/4 的 anti-join 用 `NULL`（无类型）替换右表列。UNION ALL 中 PostgreSQL 从非 NULL 分支推断类型。对于 `jsonb`、`xml`、数组等特殊类型，可能失败。当前 `ExpectedErrors` 已覆盖类型不匹配错误。无假阳性风险，但可能在特殊类型场景下静默跳过有效测试。

---

## 5. 修复优先级排序

| # | 差距 | 等级 | 修复工作量 | 影响面 | 审核状态 |
|---|------|------|-----------|--------|---------|
| P0-1 | Rule 3 算法偏差 | P0 | 小 (改 1 方法) | Rule 3 语义正确性 | ✅ 确认 + 补充 joinType 移除 |
| P0-2 | 单列 Fetch + 无 SELECT * | P0 | 中 (1 新文件 + 4 修改) | 所有规则检测覆盖面 | ✅ 确认 + Rule 6 禁止 SELECT * |
| P0-3 | NATURAL 无 OuterType | P0 | 中 (1 新 enum + 2 修改) | Rule 6 检测覆盖面 | ✅ 确认 + 渲染需 OUTER 关键字 |
| **P0-4** | **Rule 6 SELECT * 列数不匹配** | **P0 (新增)** | **小 (改 fetch 方法)** | **Rule 6 结果比较假阳性** | **审核新增** |
| P1-1 | 仅 2 表单 JOIN | P1 | 中 (改 transformer + Select) | 多表 JOIN 优化器路径 | ✅ 确认 |
| P1-2 | removeLastJoin() | P1 | 小 (1 方法) | P1-1 的前置依赖 | ✅ 确认 + **代码 bug 修正** |
| **P1-5** | **removeLastJoin() 类型 bug** | **P1 (新增)** | **小 (已在 P1-2 修正)** | **编译正确性** | **审核新增** |
| **P1-6** | **PostgresJoin 双构造器不兼容** | **P1 (新增)** | **小 (已在 P1-2 修正)** | **removeLastJoin 逻辑** | **审核新增** |
| P1-3 | CROSS JOIN 单变体 | P1 | 小 (改 1 方法) | Rule 5 检测覆盖面 | ✅ 确认 |
| P1-4 | Rule 4 right-anti 不完整 | P1 | 小 (改 1 方法) | Rule 4 检测覆盖面 (latent) | ✅ 确认 + **Codex 修正：当前单列下正确** |
| **P1-7** | **PostgresFromTable 随机 * 假阳性** | **P1 (Codex 新增)** | **小 (替换 9 处 FromTable → TableRef)** | **所有规则假阳性风险** | **Codex 新增** |

**建议实施顺序**：P1-7 → P0-1 → P0-2 → P0-4（与 P0-2 同步） → P0-3 → P1-2 → P1-1 → P1-3 → P1-4

（P1-5 和 P1-6 已在 P1-2 修复方案中合并修正，无需单独步骤）

---

## 6. 修复方案与文件清单

### Step 1: P1-7 — 修复 PostgresFromTable 随机 * 假阳性 **(Codex 新增，优先修复)**

**文件**：`src/sqlancer/postgres/oracle/ext/PostgresJIRTransformer.java`（修改）
- 将所有 9 处 `new PostgresFromTable(table, false)` 替换为 `new PostgresTableReference(table)`
- `PostgresToStringVisitor.visit(PostgresTableReference)` 已渲染为确定性 `table.getName()`（无随机 `*`）
- 消除 source/target 因表继承 `*` 后缀不一致导致的假阳性

### Step 2: P0-1 — 修复 Rule 3 算法偏差

**文件**：`src/sqlancer/postgres/oracle/ext/PostgresJIRTransformer.java`（修改）
- `generateSemiAntiComplement()` source 改为纯左表（不含 JOIN）
- SEMI = `SELECT leftCols FROM L WHERE EXISTS (...)`
- ANTI = `SELECT leftCols FROM L WHERE NOT EXISTS (...)`
- **⚠️ Codex 纠错：必须删除 lines 155-156 的 joinType 选择代码**（`Randomly.fromOptions(INNER, LEFT, RIGHT, FULL)`），否则产生未使用变量 → `-failOnWarning` 编译失败

### Step 3: P0-2 — 新增 PostgresWildcard + 多列 Fetch

**新文件**：
- `src/sqlancer/postgres/ast/PostgresWildcard.java` — AST 节点，渲染为 `*`

**修改文件**：
- `src/sqlancer/postgres/PostgresVisitor.java` — 添加 `void visit(PostgresWildcard wildcard)` 抽象方法声明 + dispatch 分支 (`} else if (expression instanceof PostgresWildcard) { visit((PostgresWildcard) expression); }`)
- `src/sqlancer/postgres/PostgresToStringVisitor.java` — 添加 `visit(PostgresWildcard)` 方法 (渲染 `*`)。⚠️ Codex 确认：当前 SELECT 列渲染 (line 266) 通过 `visit(s.getFetchColumns())` 调用，无别名追加逻辑，PostgresWildcard 不需要额外别名跳过逻辑（PG 无 MySQL 的 AS ref<N> 问题）
- `src/sqlancer/postgres/PostgresExpectedValueVisitor.java` — 添加 `visit(PostgresWildcard)` 方法 (`throw new IgnoreMeException()`，不是 no-op)
- `src/sqlancer/postgres/oracle/ext/PostgresJIRTransformer.java` — 替换 `generateRandomFetchColumns()` 和 `getLeftTableFetchColumns()` 为 50/50 SELECT * vs 多列模式；更新 `buildAntiJoinFetchColumns()` 增加 SELECT * 分支。⚠️ Codex 纠错：`generateRandomFetchColumns()` 实际从两表选取（不是仅左表），anti-join NULL 替换路径已被行使，但仅单列范围

### Step 4: P0-4 — Rule 6 禁止 SELECT *（与 P0-2 同步）

**文件**：`src/sqlancer/postgres/oracle/ext/PostgresJIRTransformer.java`（修改）
- 新增 `getLeftTableFetchColumnsNoWildcard()` 方法（仅显式列引用，50/50 多列子集 vs 单列，不含 SELECT *）
- Rule 6 使用此方法而非 `generateLeftTableFetchColumns()`

### Step 6: P0-3 — 新增 OuterType + NATURAL OUTER JOIN

**修改文件**：
- `src/sqlancer/postgres/ast/PostgresJoin.java` — 新增 `PostgresOuterType` enum (LEFT, RIGHT, FULL) + `outerType` 字段
- `src/sqlancer/postgres/PostgresToStringVisitor.java` — NATURAL JOIN 渲染追加 OuterType（`NATURAL LEFT OUTER JOIN` 等）
- `src/sqlancer/postgres/oracle/ext/PostgresJIRTransformer.java` — Rule 6 随机 OuterType，target JoinType 对应

### Step 7: P1-2 — 新增 removeLastJoin()

**修改文件**：
- `src/sqlancer/postgres/ast/PostgresSelect.java` — 新增 `removeLastJoin()` 方法（使用 `List<PostgresJoin>` 正确类型，不修改 fromList）

### Step 8: P1-1 — 多表 JOIN 链支持

**修改文件**：
- `src/sqlancer/postgres/oracle/ext/PostgresJIRTransformer.java` — 存储多表列表、`buildJoinChain()`、`decideJoinCount()`、更新所有规则方法使用多表

### Step 9: P1-3 — CROSS JOIN 多变体

**修改文件**：
- `src/sqlancer/postgres/oracle/ext/PostgresJIRTransformer.java` — Rule 5 随机选 INNER/LEFT/RIGHT/FULL JOIN ON TRUE

### Step 10: P1-4 — Rule 4 right-anti 完整化

**修改文件**：
- `src/sqlancer/postgres/oracle/ext/PostgresJIRTransformer.java` — right-anti 包含左表列 → NULL + 右表列保留

### Step 11: 更新 Release Notes + 版本号

**修改文件**：
- `docs/release_notes.md` — 新增版本条目
- `pom.xml` — 版本号递增

---

## 7. PostgreSQL vs MySQL 特有差异

| 差异点 | PostgreSQL | MySQL |
|--------|-----------|-------|
| FULL OUTER JOIN | ✅ 支持 → Rule 4 启用 | ❌ 不支持 → Rule 4 禁用 |
| NATURAL FULL OUTER JOIN | ✅ OuterType 应包含 FULL | ❌ OuterType 仅 LEFT/RIGHT |
| CROSS JOIN ≡ FULL JOIN ON TRUE | ✅ 可测此变体 | ❌ 不可测 |
| RIGHT JOIN ≡ LEFT JOIN (swap) | ✅ 原生支持 | ✅ 原生支持 |
| ON TRUE 语义 | `ON TRUE` → boolean | `ON 1` → truthy integer |
| JOIN 渲染风格 | `LEFT OUTER JOIN` | `LEFT JOIN` |
| Visitor 模式 | 内联渲染 (在 visit(PostgresSelect) 中) | 独立 visit(MySQLJoin) 方法 |

---

## 8. 验证方案

1. **编译验证**：`mvn clean compile -DskipTests` 确保 `-failOnWarning` 通过
2. **冒烟测试**：
   ```
   java -jar sqlancer.jar --num-queries 20 postgres --oracle JIR
   ```
3. **单规则测试**：修改 `JIRRule.forPostgres()` 临时只启用 1 条规则，逐条验证
4. **P0-1 验证**：Rule 3 source 应为 `SELECT cols FROM L`（不含 JOIN），SEMI + ANTI UNION_ALL 结果等于纯左表
5. **P0-2 验证**：约 50% 概率生成 `SELECT *`，约 50% 概率生成多列 fetch
6. **P0-3 验证**：约 25% 概率生成 `NATURAL JOIN`、`NATURAL LEFT OUTER JOIN`、`NATURAL RIGHT OUTER JOIN`、`NATURAL FULL OUTER JOIN`
7. **兼容性验证**：确认已有 oracle（TLP_WHERE、NOREC 等）正常运行不受影响
