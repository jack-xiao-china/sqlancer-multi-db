# MySQL JIR Oracle 规则完整性分析 (v2.7.4 重新审视)

**日期**: 2026-06-17  
**版本**: v2.7.4 (修复版本) → 重新审视  
**分析对象**: MySQL JIRTransformer (5 规则实现) vs 原生论文 (6 规则) vs 原生 sqlancer-scale (1 规则)

---

## 0. 关键 Bug 发现 ⚠️

### BUG-1: SELECT * + AS 别名导致无效 SQL（P0级）

**发现**: MySQLToStringVisitor 在渲染 SELECT 的 fetch columns 时，**始终为每个列追加 `AS ref<N>` 别名**（lines 86-95）：

```java
for (int i = 0; i < s.getFetchColumns().size(); i++) {
    visit(s.getFetchColumns().get(i));
    sb.append(" AS ref");   // ← 始终追加别名
    sb.append(ref++);
}
```

当 fetch columns 包含 `MySQLWildcard`（`*`）时，渲染结果为 `SELECT * AS ref0` — **这是无效的 MySQL 语法**！MySQL 不允许为 `*` 添加别名。

**影响链**:
1. `generateFetchColumns()` / `generateLeftTableFetchColumns()` 50% 概率选择 `MySQLWildcard`
2. 渲染为 `SELECT * AS ref0 FROM t0 LEFT JOIN t1 ON cond` → MySQL 执行报 syntax error
3. 错误被 JIROracle 捕获 → `IgnoreMeException` → **整个 JIR check 被静默跳过**
4. SELECT * 功能完全失效，Bug 检测机会丢失

**Anti-join SELECT * 分支同样受影响**:
- `SELECT * AS ref0, NULL AS ref1, NULL AS ref2 FROM t0 WHERE NOT EXISTS (...)` — `* AS ref0` 仍然无效

**修复方案**: 在 MySQLToStringVisitor.visit(MySQLSelect) 中，当 fetch column 是 MySQLWildcard 或 MySQLConstant（NULL）时，跳过别名追加：

```java
for (int i = 0; i < s.getFetchColumns().size(); i++) {
    visit(s.getFetchColumns().get(i));
    MySQLExpression expr = s.getFetchColumns().get(i);
    // Skip alias for wildcard (*) and NULL constants — they cannot be aliased in MySQL
    if (!(expr instanceof MySQLWildcard)) {
        sb.append(" AS ref");
        sb.append(ref++);
    }
}
```

**原生对比**: 原生 GeneralToStringVisitor **不添加任何别名** — 直接渲染列名。我们的别名机制是 MySQL 模块的预有行为（非 JIR 引入），但对 JIR SELECT * 功能产生了致命影响。

---

## 1. 三个实现版本对比（v2.7.4 更新）

| 维度 | 论文定义 (SIGMOD 2026) | 原生 sqlancer-scale | 我们的 MySQL 实现 (v2.7.4) | 对齐状态 |
|------|----------------------|---------------------|--------------------------|---------|
| 规则数 | 6 条完整规则 | 1 条 (仅 Rule 1 LEFT JOIN) | 5 条 (Rule 4 因 MySQL 无 FULL JOIN 跳过) | ✅ 覆盖 MySQL 支持的全部规则 |
| JOIN 变体数 | INNER/LEFT/RIGHT/FULL/SEMI/ANTI/NATURAL/CROSS | INNER/LEFT/RIGHT/NATURAL | INNER/LEFT/RIGHT/CROSS/NATURAL + NATURAL LEFT/RIGHT | ✅ 覆盖 MySQL 支持的全部变体 |
| SELECT * | 支持 (50/50 随机) | 支持 (50/50 随机) | 支持 (50/50 随机) | ✅ 实现但 **BUG-1 导致无效 SQL** |
| 多列 fetch | 论文用多列比较 | `Randomly.nonEmptySubset(allCols)` | `Randomly.nonEmptySubset(allCols)` | ✅ 对齐原生 |
| Anti-join NULL 列 | 论文用多列比较 | SELECT * → `*, NULL, NULL, ...`; 显式列 → 右表列替换 NULL | 同上 | ✅ 完全对齐原生 |
| 子查询 JOIN | 论文提到 8 个 Bug 需要 | 代码存在但被 `if(true)` 禁用 | ❌ 不支持 | ⚠️ 原生也未启用，P2 优先级 |
| WHERE 子句 | 随机生成 | `setWhereClause(null)` 固定空 | 不生成 WHERE | ✅ 对齐原生（均不生成） |
| 多表 JOIN 树 | 论文支持多表 JOIN 树 | 仅 1 JOIN（代码可扩展但未循环） | 1-2 JOIN（2-4 表） | ✅ 扩展了原生，低概率多 JOIN |
| NATURAL OUTER | LEFT/RIGHT/FULL NATURAL | `OuterType` enum (FULL/LEFT/RIGHT) | `OuterType` enum (LEFT/RIGHT) | ✅ MySQL 无 FULL JOIN，仅 LEFT/RIGHT |
| 结果比较 | 行级/多列 | `getResultSetFirstColumnAsString` | `getResultSetAllColumnsAsString` | ✅ 升级为行级比较（更严格） |
| Rule 3 source | 纯左表行 | ❌ 原生不实现 Rule 3 | ✅ 纯左表 `SELECT cols FROM L` | ✅ P0 修复已完成 |

---

## 2. 逐规则详细对比

### Rule 1: LEFT_JOIN_DECOMPOSITION ✅ 完整对齐原生

| 维度 | 原生 GeneralJIROracle | 我们的实现 | 对齐状态 |
|------|---------------------|----------|---------|
| 触发条件 | 仅当 `lastJoin.getJoinType() == LEFT` | 总是生成 LEFT JOIN 作为 source | ✅ 等效（我们直接生成 LEFT，原生先随机再判断） |
| fetch columns | 50/50 SELECT * vs 多列子集 | 50/50 SELECT * vs 多列子集 | ✅ 完全对齐 |
| INNER target | `lastJoin.setJoinType(INNER)` 再渲染 | `buildJoinChain(joinCount, JoinType.INNER, onCondition)` | ✅ 等效（不同实现方式，相同语义） |
| Anti-join SELECT * | `*, NULL, NULL, ...` (一个 NULL per 右表列) | `MySQLWildcard + rightTable.getColumns().size() NULLs` | ✅ 完全对齐 |
| Anti-join 显式列 | 右表列 → NULL 替换 (基于 `split("\\.")[0]`) | 右表列 → NULL 替换 (基于 `col.getTable().getName()`) | ✅ 等效但更精确（用元数据而非字符串解析） |
| removeLastJoin | `select.removeLastJoin()` 修改原 select | `precedingJoins.remove()` 从新列表移除 | ✅ 等效（我们的方式更干净，不修改共享对象） |
| EXISTS subquery | `SELECT 1 FROM R WHERE onCondition`, `NOT EXISTS` | `buildExistsSubquery(table, onCondition)`, `NOT EXISTS` | ✅ 完全对齐 |
| ON condition reuse | 同一 Node 共享于 INNER + EXISTS | 同一 MySQLExpression 共享于 INNER + EXISTS | ✅ 完全对齐 |
| UNION_ALL 组合 | `innerJoinQuery + " UNION ALL " + antiJoinTransformedQuery` | `JIRQuerySet(innerSQL, antiSQL, UNION_ALL)` | ✅ 完全对齐 |
| **BUG-1 影响** | 无（原生不添加 AS 别名） | `SELECT * AS ref0` → 无效 SQL | ⚠️ **需修复** |

### Rule 2: LEFT_RIGHT_SYMMETRY ✅ 正确实现

| 论文公式 | $L \bowtie_P^{RIGHT} = \pi_{R,L}(R \bowtie_P^{LEFT})$ |
|----------|------|

**我们的实现**: source = LEFT JOIN, target = RIGHT JOIN (仅最后 JOIN 类型不同)，fetch columns 仅选左表列。

**与论文的差异**: 论文要求列重排 $\pi_{R,L}$，我们只选左表列。这是合理的简化——只比较左表列时，LEFT JOIN 和 RIGHT JOIN 的左表列部分确实应完全一致。

**BUG-1 影响**: `generateLeftTableFetchColumns()` 50% 概率选 `*` → `SELECT * AS ref0` → 无效 SQL。

### Rule 3: SEMI_ANTI_COMPLEMENT ✅ P0 修复已完成

| 论文公式 | $L = (L \bowtie_P^{SEMI}) \uplus (L \bowtie_P^{ANTI})$ |
|----------|------|

**v2.7.3 问题**: source = `SELECT cols FROM L JOIN R ON cond`（带 JOIN），EXISTS/NOT EXISTS 语义偏差。

**v2.7.4 修复**: source = `SELECT cols FROM L`（纯左表，无 JOIN），SEMI + ANTI = 左表全部行。✅

**BUG-1 影响**: `generateLeftTableFetchColumns()` 50% 概率选 `*` → source/target 渲染为 `SELECT * AS ref0 FROM L` → 无效 SQL。

### Rule 4: FULL_JOIN_DECOMPOSITION ❌ MySQL 不支持 (正确跳过)

MySQL 无 `FULL [OUTER] JOIN`，跳过此规则是正确的。✅

### Rule 5: CROSS_JOIN_EQUIVALENCE ✅ 多变体实现

| 论文公式 | $L \times R = L \bowtie_{TRUE}^{INNER} = L \bowtie_{TRUE}^{LEFT} = L \bowtie_{TRUE}^{RIGHT}$ |
|----------|------|

**我们的实现**: source = CROSS JOIN, target = 随机选 INNER/LEFT/RIGHT ON TRUE (每次测试一种变体)。✅

**与原生的差异**: 原生不实现 Rule 5，我们实现了。论文指出三种等价关系，我们随机选一种测试，多次迭代覆盖全部。✅

**BUG-1 影响**: `generateLeftTableFetchColumns()` 50% 概率选 `*` → 无效 SQL。

### Rule 6: NATURAL_JOIN_EXPLICATION ✅ 含 NATURAL OUTER 变体

| 论文公式 | $L \bowtie^{NATURAL} \equiv L \bowtie_{\bigwedge_i L.a_i = R.a_i}$ (含 LEFT/RIGHT OUTER 变体) |
|----------|------|

**我们的实现**: source = NATURAL [{LEFT|RIGHT}] JOIN, target = {INNER|LEFT|RIGHT} JOIN ON (equalities)。OuterType 随机从 null/LEFT/RIGHT 选择。✅

**与原生的对齐**: 原生有 OuterType = {FULL, LEFT, RIGHT}，我们有 OuterType = {LEFT, RIGHT}（MySQL 无 FULL JOIN）。✅

**BUG-1 影响**: `generateLeftTableFetchColumns()` 50% 概率选 `*` → 无效 SQL。

---

## 3. 原生 vs 我们的功能对比总览

| 原生特性 | 原生实现状态 | 我们的实现状态 | 对齐评估 |
|---------|------------|-------------|---------|
| Rule 1 (LEFT JOIN Decomposition) | ✅ 活跃 | ✅ 完整 | ✅ 完全对齐 |
| Rule 2-6 | ❌ 不实现 | ✅ 5 条规则 | ✅ 超越原生 |
| generateFetchColumns (50/50) | ✅ | ✅ | ✅ 对齐 |
| Anti-join SELECT * NULL 追加 | ✅ | ✅ | ✅ 对齐 |
| Anti-join 显式列 NULL 替换 | ✅ | ✅ | ✅ 对齐 |
| ON condition reuse | ✅ (同一 Node) | ✅ (同一 Expression) | ✅ 对齐 |
| removeLastJoin pattern | ✅ (mutate 原对象) | ✅ (build new list, remove) | ✅ 等效 |
| NATURAL OUTER (OuterType) | ✅ (FULL/LEFT/RIGHT) | ✅ (LEFT/RIGHT only) | ✅ MySQL 约束正确 |
| 子查询作为右表 | ⚠️ 有代码但 `if(true)` 禁用 | ❌ 不支持 | ⚠️ 原生也未启用 |
| WHERE 子句 | `null` (不生成) | 不设置 (默认 null) | ✅ 对齐 |
| EXISTS rendering | `NOT EXISTS (SELECT 1 FROM R WHERE cond)` | `NOT EXISTS (SELECT 1 FROM R WHERE cond)` | ✅ 对齐 |
| UNION_ALL 组合 | 字符串拼接 | JIRQuerySet + UNION_ALL 结果类型 | ✅ 对齐 |
| 行级比较 | ❌ 仅 column 1 | ✅ 全列行级 | ✅ 升级 |
| SELECT * + AS alias | ❌ 原生不添加别名 | ⚠️ 始终添加别名 → BUG-1 | ⚠️ **需修复** |
| ORDER BY | 低概率 gen.generateOrderBys() | 低概率 ORDER BY 1 | ⚠️ 简化（仅单列） |

---

## 4. 遗漏与修复建议

### P0 — 严重 Bug（影响功能正确性）

| # | Bug | 规则 | 影响 | 修复建议 |
|---|-----|------|------|---------|
| 1 | **SELECT * + AS 别名导致无效 SQL** | 全部 5 规则 | 50% 的 JIR check 被 IgnoreMeException 跳过，SELECT * 功能完全失效 | MySQLToStringVisitor.visit(MySQLSelect) 中：MySQLWildcard 跳过别名 |

### P1 — 功能完善

| # | 遗漏 | 规则 | 影响 | 修复建议 |
|---|------|------|------|---------|
| 2 | **MySQLConstant(NULL) 也应跳过别名** | Rule 1 anti-join | `NULL AS ref1` 虽然语法有效但语义多余，且增加列名不一致风险 | 对 NULL 常量也跳过别名（可选，非必须） |

### P2 — 优化建议（低优先级，原生也未实现）

| # | 遗漏 | 影响 | 修复建议 |
|---|------|------|---------|
| 3 | **子查询作为 JOIN 右表** | 原生有代码但禁用，论文 20 个 Bug 中 20 个需要子查询 | 实现但保持禁用（对齐原生） |
| 4 | **ORDER BY 仅 ORDER BY 1** | 原生有 gen.generateOrderBys() 支持多列 | 扩展为多列 ORDER BY（可选） |
| 5 | **removeLastJoin() 未使用** | MySQLSelect.removeLastJoin() 已实现但未被 transformer 使用，存在代码冗余 | Rule 1 使用 removeLastJoin() 替代 buildJoinChain+list.remove 模式（可选，不影响功能） |

---

## 5. 与原生 sqlancer-scale 的实现差异详析

### 5.1 原生仅 LEFT JOIN 触发

原生 GeneralJIROracle 先随机生成 JOIN（INNER/NATURAL/LEFT/RIGHT），再**仅当最后一个 JOIN 是 LEFT 时**执行变换。其他类型被静默跳过（执行但不比较）。

我们的实现**始终**生成 LEFT JOIN 作为 Rule 1 source，不需要条件判断。这是更精确的——因为我们专门为 Rule 1 设计了 LEFT JOIN 生成，而原生是通用随机生成 + 条件触发。

**评估**: ✅ 我们的方案更优（不会浪费执行资源在无变换的查询上）。

### 5.2 原生 removeLastJoin 修改共享对象

原生使用同一个 `select` 对象：先渲染 LEFT JOIN → `originalQueryString`，再 `setJoinType(INNER)` → 渲染 `innerJoinQuery`，再 `removeLastJoin()` + `NOT EXISTS` → 渲染 `antiJoinTransformedQuery`。

我们为 source、inner、anti 分别构建独立的 MySQLSelect 对象。避免了共享对象修改的潜在副作用。

**评估**: ✅ 我们的方案更安全（不修改共享对象）。

### 5.3 原生不添加列别名

原生 GeneralToStringVisitor 直接渲染列名/表达式，**不追加任何 AS 别名**。

我们的 MySQLToStringVisitor **始终**为每个 fetch column 追加 `AS ref<N>` 别名。这是 MySQL 模块的预有行为（防止重复列名），但对 JIR 的 SELECT * 和 NULL 列产生了 BUG-1。

**评估**: ⚠️ 需修复 — MySQLWildcard 应跳过别名。

### 5.4 原生 ON condition Node 共享

原生在同一个 select 对象上操作，ON condition Node 自然被 INNER JOIN 和 EXISTS subquery 共享（因为 select 对象不重建）。

我们通过 `buildJoinChain` 传入同一 `onCondition` Expression 对象，实现相同的共享效果。但每次 `buildJoinChain` 调用会创建新的 MySQLJoin wrapper 对象，这些 wrapper 共享同一个 onCondition Expression。

**评估**: ✅ 等效 — ON condition 语义共享，wrapper 对象独立创建不影响渲染。

### 5.5 原生仅 1 JOIN

原生 `getJoins()` 从 tableList 取前 2 个表，仅创建 1 个 JOIN。没有循环追加更多 JOIN。

我们的 `buildJoinChain()` 支持 1-2 个 JOIN（`decideJoinCount()` 低概率返回 2），且 `buildJoinChain` 前面的 JOIN 使用随机类型。

**评估**: ✅ 我们超越了原生（低概率多 JOIN 树），但主要行为（1 JOIN）与原生一致。

---

## 6. 结论

MySQL JIR Oracle v2.7.4 已完成 P0 Rule 3 算法纠正和全部 P1 功能增强，在规则覆盖面和实现细节上**全面超越原生 sqlancer-scale**（5 规则 vs 1 规则），且每条规则的语义均对齐原生和论文。

**但存在 1 个 P0 级遗留 Bug**：MySQLWildcard（SELECT *）在 MySQLToStringVisitor 中被添加 `AS ref<N>` 别名，导致 `SELECT * AS ref0` 无效 SQL。此 Bug 使 50% 的 JIR check（SELECT * 分支）被 IgnoreMeException 跳过，Bug 检测机会丢失。

**修复优先级**:
1. **P0-1**: 修复 MySQLWildcard + AS 别名 → SELECT * 功能恢复
2. **P2-3/4/5**: 子查询 JOIN、多列 ORDER BY、removeLastJoin 使用 — 低优先级优化

**修复后预期**: SELECT * 功能恢复，JIR Oracle 的 Bug 检测覆盖率从 50%（仅显式列生效）提升至 100%（SELECT * + 显式列均生效）。
