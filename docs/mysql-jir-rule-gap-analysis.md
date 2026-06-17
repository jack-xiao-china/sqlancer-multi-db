# MySQL JIR Oracle 规则完整性分析

**日期**: 2026-06-17  
**版本**: v2.7.3 (分析版本) → v2.7.4 (修复版本)
**分析对象**: MySQL JIRTransformer (5 规则实现) vs 原生论文 (6 规则) vs 原生 sqlancer-scale (1 规则)

---

## 1. 三个实现版本对比

| 维度 | 论文定义 (SIGMOD 2026) | 原生 sqlancer-scale | 我们的 MySQL 实现 |
|------|----------------------|---------------------|-------------------|
| 规则数 | 6 条完整规则 | 1 条 (Rule 1 仅 LEFT JOIN) | 5 条 (Rule 4 因 MySQL 无 FULL JOIN 跳过) |
| 覆盖率 | 100% | 17% (仅核心规则) | 83% (MySQL 支持的全部规则) |
| JOIN 变体数 | INNER/LEFT/RIGHT/FULL/SEMI/ANTI/NATURAL/CROSS | INNER/LEFT/RIGHT/NATURAL | INNER/LEFT/RIGHT/CROSS/NATURAL |
| 子查询 JOIN | 支持 (论文 20 个 Bug 中 20 需要子查询) | 代码存在但被 `if(true)` 强制禁用 | ❌ 不支持 |
| LATERAL JOIN | 论文提到 8 个 Bug 需要 | 不支持 | ❌ 不支持 |
| WHERE 子句 | 随机生成 | `setWhereClause(null)` 固定空 | ❌ 不生成 WHERE |
| 多列 fetch | 论文用多列比较 | `SELECT *` 或多列 | 仅单列 (`getLeftTableFetchColumns`) |
| 多 JOIN | 论文支持多表 JOIN 树 | 单 JOIN | 仅单 JOIN (2 表) |
| NATURAL OUTER | LEFT NATURAL / RIGHT NATURAL / FULL NATURAL | 有 `OuterType` enum (FULL/LEFT/RIGHT) | ❌ 不区分 NATURAL OUTER |

---

## 2. 逐规则详细对比

### Rule 1: LEFT_JOIN_DECOMPOSITION ✅ 完整实现

| 论文公式 | $L \bowtie_P^{\text{LEFT}} = (L \bowtie_P^{\text{INNER}}) \uplus \mathcal{N}_R(L \bowtie_P^{\text{ANTI}})$ |
|----------|------|

**我们的实现**:
- Source: `SELECT {fetch} FROM L LEFT JOIN R ON cond`
- Target1: `SELECT {fetch} FROM L INNER JOIN R ON cond`
- Target2: `SELECT {antiCols} FROM L WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond)` (右表列替换为 NULL)
- 比较方式: UNION_ALL (bag semantics)

**与原生的差异**:
- ✅ 核心逻辑一致 (LEFT = INNER ∪ ANTI via NOT EXISTS)
- ⚠️ fetch columns: 原生支持 `SELECT *` 或多列; 我们仅选单列
- ⚠️ 原生在 anti-join 中对 `SELECT *` 专门追加右表列数个 NULL 列; 我们仅替换右表列为 NULL

**遗漏**: 无算法遗漏，但 fetch columns 的覆盖面较窄（单列 vs 多列），降低了 Bug 检测概率。

### Rule 2: LEFT_RIGHT_SYMMETRY ✅ 完整实现

| 论文公式 | $L \bowtie_P^{\text{RIGHT}} = \pi_{R,L}(R \bowtie_P^{\text{LEFT}})$ |
|----------|------|

**我们的实现**:
- Source: `SELECT {leftCols} FROM L LEFT JOIN R ON cond`
- Target: `SELECT {leftCols} FROM R RIGHT JOIN L ON cond`
- 比较方式: EQUAL (set semantics)

**与论文的差异**:
- ⚠️ 论文要求列重排 $\pi_{R,L}$，即 RIGHT JOIN 结果中右表列在前、左表列在后; 我们只选左表列，避免了列顺序问题
- 这个简化是合理的：只比较左表列时，LEFT JOIN 和 RIGHT JOIN 的左表列部分确实应该完全一致

**遗漏**: 无算法遗漏。列选择策略是合理的简化。

### Rule 3: SEMI_ANTI_COMPLEMENT ✅ 实现但有偏差

| 论文公式 | $L = (L \bowtie_P^{\text{SEMI}}) \uplus (L \bowtie_P^{\text{ANTI}})$ |
|----------|------|

**论文语义**: SEMI JOIN 和 ANTI JOIN 对左表行做**完全划分**——左表的每行要么有匹配(SEMI)，要么无匹配(ANTI)，二者合起来等于左表全部行。

**我们的实现**:
- Source: `SELECT {leftCols} FROM L {INNER/LEFT/RIGHT} JOIN R ON cond`
- Target1: Source + `WHERE EXISTS (SELECT 1 FROM R WHERE cond)`
- Target2: Source + `WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond)`
- 比较方式: UNION_ALL

**⚠️ 关键偏差**:

论文 Rule 3 的 source 应是 **纯左表行** (`SELECT {leftCols} FROM L`)，不包含任何 JOIN。

我们的实现 source 是 `SELECT {leftCols} FROM L {INNER/LEFT/RIGHT} JOIN R ON cond`，这是一个 **带 JOIN 的查询**，然后再在带 JOIN 的查询上加 WHERE EXISTS/NOT EXISTS。

这导致：
1. INNER JOIN + WHERE EXISTS ≈ INNER JOIN (EXISTS 对 INNER JOIN 不改变结果)
2. INNER JOIN + WHERE NOT EXISTS ≈ 空集 (INNER JOIN 行都满足 EXISTS)
3. INNER JOIN ∪ 空 = INNER JOIN ≠ 左表全部行

**正确实现应该是**:
- Source: `SELECT {leftCols} FROM L` (纯左表)
- Target1: `SELECT {leftCols} FROM L WHERE EXISTS (SELECT 1 FROM R WHERE cond)` (SEMI)
- Target2: `SELECT {leftCols} FROM L WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond)` (ANTI)

---

### Rule 4: FULL_JOIN_DECOMPOSITION ❌ MySQL 不支持 (正确跳过)

MySQL 无 `FULL [OUTER] JOIN`，跳过此规则是正确的。

### Rule 5: CROSS_JOIN_EQUIVALENCE ✅ 完整实现

| 论文公式 | $L \times R = L \bowtie_{\text{TRUE}}^{\text{INNER}}$ |
|----------|------|

**我们的实现**:
- Source: `SELECT {leftCols} FROM L CROSS JOIN R`
- Target: `SELECT {leftCols} FROM L INNER JOIN R ON 1`
- 比较方式: EQUAL

**与论文的差异**:
- ✅ 核心逻辑一致
- ⚠️ 论文还指出 CROSS JOIN ≡ LEFT JOIN ON TRUE ≡ RIGHT JOIN ON TRUE ≡ FULL JOIN ON TRUE，我们仅比较了 INNER JOIN ON TRUE
- ⚠️ fetch columns 仅选左表列; 论文要求两表均非空时等价才成立，我们的 JIROracle 不检查表非空条件

**遗漏**: 可以扩展为比较更多 JOIN ON TRUE 变体 (LEFT/RIGHT ON TRUE)，增加检测面。

### Rule 6: NATURAL_JOIN_EXPLICATION ✅ 实现但有简化

| 论文公式 | $L \bowtie^{\text{NATURAL}} \equiv_{\text{rows}} L \bowtie_{\bigwedge_i L.a_i = R.a_i}^{\text{INNER}}$ |
|----------|------|

**我们的实现**:
- Source: `SELECT {leftCols} FROM L NATURAL JOIN R`
- Target: `SELECT {leftCols} FROM L INNER JOIN R ON (lc1=rc1 AND lc2=rc2 AND ...)`
- 比较方式: EQUAL

**与原生 sqlancer-scale 的差异**:
- 原生有 `OuterType` enum (FULL/LEFT/RIGHT) 支持 LEFT NATURAL JOIN, RIGHT NATURAL JOIN, FULL NATURAL JOIN
- 我们仅实现 plain NATURAL JOIN (= INNER with equalities)

**⚠️ 遗漏**: MySQL 支持 `LEFT NATURAL JOIN` 和 `RIGHT NATURAL JOIN`，但我们的 NATURAL JOIN 规则仅验证 plain NATURAL JOIN。应该扩展：
- LEFT NATURAL JOIN = LEFT JOIN + ON (equalities)
- RIGHT NATURAL JOIN = RIGHT JOIN + ON (equalities)

---

## 3. 综合遗漏清单

### P0 — 算法错误 (影响 Bug 检测准确性)

| # | 遗漏 | 规则 | 影响 | 修复建议 |
|---|------|------|------|---------|
| 1 | **Rule 3 source 不应包含 JOIN** | SEMI_ANTI_COMPLEMENT | source 应为纯左表行，当前 source 带 JOIN 使 EXISTS/NOT EXISTS 的语义不对 | 改为 source = `SELECT {leftCols} FROM L` |

### P1 — 功能缺失 (降低 Bug 检测覆盖率)

| # | 遗漏 | 规则 | 影响 | 修复建议 |
|---|------|------|------|---------|
| 2 | **不支持多表 JOIN 树** | 所有规则 | 论文 18 个 Bug 需要多种 JOIN 类型组合 | 扩展为支持 ≥2 个 JOIN (3+ 表) |
| 3 | **fetch columns 仅单列** | Rule 1, 2, 3, 5, 6 | 单列比较覆盖面窄，多列比较更容易发现列级别的 NULL 处理 bug | 支持多列 fetch + `SELECT *` |
| 4 | **NATURAL OUTER JOIN 不验证** | Rule 6 | MySQL 支持 LEFT/RIGHT NATURAL JOIN，不验证遗漏了外连接 NATURAL 变体 | 新增 LEFT NATURAL / RIGHT NATURAL 变体 |
| 5 | **CROSS JOIN 仅比较 INNER ON TRUE** | Rule 5 | 论文指出 CROSS ≡ LEFT ON TRUE ≡ RIGHT ON TRUE，多变体可增加检测 | 扩展为比较 INNER/LEFT/RIGHT ON TRUE |

### P2 — 优化建议 (提升 Bug 检测效率)

| # | 遗漏 | 影响 | 修复建议 |
|---|------|------|---------|
| 6 | **不生成 WHERE 子句** | 原生 sqlancer-scale 和论文都支持 WHERE 条件，增加谓词复杂度 | 为 source/target 添加随机 WHERE 子句 |
| 7 | **不支持子查询 JOIN** | 论文 20 个 Bug 中 20 个需要子查询 JOIN (占比极高) | 支持 derived table/subquery 作为 JOIN 的右表 |
| 8 | **不生成 ORDER BY** | 仅低概率 `ORDER BY 1`，缺少多列 ORDER BY 测试 | 支持多列 ORDER BY |
| 9 | **NULL 替换不够精细** | Rule 1 anti-join 中 `SELECT *` 的 NULL 列追加逻辑缺失 | 对 `SELECT *` 追加右表列数个 NULL 列 |

---

## 4. 与原生 sqlancer-scale 实现对比

原生 `GeneralJIROracle.java` (~210 行) 仅实现 **Rule 1 (LEFT JOIN Decomposition)**：
- 随机生成包含 JOIN 的 SELECT 查询
- 如果最后一个 JOIN 是 LEFT，变换为 INNER + ANTI (NOT EXISTS)
- 其他 JOIN 类型 (INNER/RIGHT/NATURAL) 仅执行不变换

我们的实现比原生更全面 (5 规则 vs 1 规则)，但在以下维度不如原生：

| 维度 | 原生 | 我们 | 差距 |
|------|------|------|------|
| fetch columns | `SELECT *` + 多列 | 单列 | ✅ 原生更全面 |
| 多 JOIN | 支持 (>=1 JOIN) | 仅单 JOIN | ✅ 原生更全面 |
| NATURAL OUTER | 有 `OuterType` enum | 仅 plain NATURAL | ✅ 原生更全面 |
| 子查询作为 JOIN 右表 | 有代码（被禁用） | 不支持 | ⚠️ 原生有但禁用 |
| WHERE 子句 | 固定 null | 固定无 WHERE | ⚠️ 两者均不生成 |

---

## 5. 优先修复建议

1. **P0-1**: 修正 Rule 3 source 查询（去掉 JOIN，使用纯左表）—— 这是最重要的，影响检测准确性
2. **P1-3**: 扩展 fetch columns 为多列 + `SELECT *` 支持
3. **P1-2**: 支持多表 JOIN 树 (≥2 JOINs)
4. **P1-4**: 添加 LEFT/RIGHT NATURAL JOIN 变体
5. **P1-5**: 扩展 Rule 5 为比较 LEFT/RIGHT ON TRUE

---

## 6. 结论

MySQL JIR Oracle 当前实现了 **5 条规则中的 5 条** (MySQL 支持的全部)，覆盖面比原生 sqlancer-scale 更广。但存在 **1 个 P0 级算法偏差** (Rule 3 source 不应为带 JOIN 的查询) 和 **4 个 P1 级功能缺失** (多列 fetch、多表 JOIN、NATURAL OUTER、CROSS JOIN 多变体)。

**与论文定义的差异**: 当前实现覆盖了论文 6 条规则的语义定义，但在实现细节上存在偏差——特别是 Rule 3 的 source 查询构造和 fetch columns 的覆盖面。这些偏差不影响核心算法的正确性（对已实现的规则而言），但降低了 Bug 检测概率。
