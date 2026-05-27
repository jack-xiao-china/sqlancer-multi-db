# EET原生工具 vs SQLancer EET Oracle — 深度对比分析

> 分析日期：2026-05-22
> 对比对象：EET原生工具 (`d:\Jack.Xiao\dbtools\EET-main`) vs SQLancer EET Oracle实现 (`src/sqlancer/*/oracle/eet/`)

---

## 一、实现逻辑对比

### 1.1 核心算法

| 维度 | EET原生 | SQLancer EET | 偏差程度 |
|------|---------|-------------|---------|
| 测试范式 | 等价表达式变换 (metamorphic testing) | 同范式，基于OSDI 2024论文Eq.1 | **一致** |
| 变换策略 | **双重策略**: (1)包装型规则(wrapping) + (2)语义重写型规则(semantic rewrite) | **仅包装型规则** (CASE/OR/AND wrapping) | **严重偏差** |
| 查询生成 | 独立随机生成器，覆盖SELECT/UPDATE/DELETE/CTE/UNION等 | 4种SELECT形状(plain/UNION/CTE/derived) | **偏差大** |
| 结果比较 | 多重集比较(multiset)，三种场景(SELECT/UPDATE/DELETE各有不同策略) | 仅SELECT多重集比较 | **偏差大** |
| 验证机制 | 双次执行确认 + 进程隔离(fork) + 崩溃检测 | 双次执行确认 | **部分偏差** |
| 最小化 | delta debugging(表达式+数据库双重最小化) | 两阶段组件缩减(仅表达式级) | **部分偏差** |
| 阻抗反馈 | 有impedance_feedback，自动黑名单99%失败的生产规则 | 无 | **遗漏** |

### 1.2 最核心偏差：缺少语义重写型变换

EET原生的变换分为两大类：

**A. 包装型规则（wrapping）** — 在原表达式外包裹等价结构：
- Rule 1: `expr → false_expr OR expr`
- Rule 2: `expr → true_expr AND expr`
- Rule 3: `expr → CASE WHEN false_expr THEN rand ELSE expr END`
- Rule 4: `expr → CASE WHEN true_expr THEN expr ELSE rand END`
- Rule 5/6: `expr → CASE WHEN rand THEN copy(expr) ELSE expr END`
- Rule 7: 无变换(叶节点)
- const_bool: `0 → 0 AND rand`, `1 → 1 OR rand`

**B. 语义重写型规则（semantic rewrite）** — 将一种SQL构造替换为语义等价的不同构造：

| 规则 | 原形式 | 等价形式 | SQLancer是否实现 |
|------|--------|----------|-----------------|
| De Morgan定律 | `(A) AND (B)` | `NOT( NOT(B) OR NOT(A) )` | **❌ 完全缺失** |
| BETWEEN→比较 | `x BETWEEN a AND b` | `(x >= a) AND (x <= b)` | **❌ 完全缺失** |
| EXISTS→IN | `EXISTS(SELECT...WHERE pred)` | `TRUE IN (SELECT CASE WHEN pred IS NULL THEN FALSE ELSE pred END FROM t WHERE TRUE)` | **❌ 完全缺失** |
| IN→EXISTS | `lhs IN (SELECT...WHERE pred)` | `CASE WHEN (lhs IN subq) IS NOT NULL THEN EXISTS(SELECT...WHERE lhs=col AND pred) ELSE NULL END` | **❌ 完全缺失** |
| INTERSECT→EXISTS | `Q1 INTERSECT Q2` | `Q1 WHERE EXISTS(Q2 WHERE col_eq_or_null AND Q2_pred)` | **❌ 完全缺失** |
| EXCEPT→NOT EXISTS | `Q1 EXCEPT Q2` | `Q1 WHERE NOT EXISTS(Q2 WHERE col_eq_or_null AND Q2_pred)` | **❌ 完全缺失** |

**这是最严重的偏差。** 包装型规则主要测试优化器对"冗余但等价"结构的处理能力，而语义重写型规则测试优化器对"完全不同语法形式但语义相同"结构的处理能力。后者更容易暴露深层逻辑错误，因为不同的语法路径会触发不同的查询优化路径。

---

## 二、功能覆盖对比

### 2.1 查询类型覆盖

| 查询类型 | EET原生 | SQLancer EET | 偏差 |
|----------|---------|-------------|------|
| SELECT | ✅ | ✅ | 一致 |
| UNION/UNION ALL | ✅ | ✅ | 一致 |
| INTERSECT/INTERSECT ALL | ✅ | **❌** | 遗漏 |
| EXCEPT/EXCEPT ALL | ✅ | **❌** | 遗漏 |
| CTE/WITH | ✅ | ✅ | 一致 |
| Derived table (FROM子查询) | ✅ | ✅ | 一致 |
| UPDATE | ✅ | **❌** | 遗漏 |
| DELETE | ✅ | **❌** | 遗漏 |
| INSERT | ✅ | **❌** | 遗漏 |
| INSERT-SELECT | ✅ | **❌** | 遗漏 |
| MERGE | ✅ | **❌** | 遗漏 |
| UPSERT/ON CONFLICT | ✅ | **❌** | 遗漏 |
| SELECT FOR UPDATE | ✅ | **❌** | 遗漏 |
| PREPARE | ✅ | **❌** | 遗漏 |
| UPDATE/DELETE RETURNING | ✅ | **❌** | 遗漏 |

**DML测试是重要遗漏。** EET原生的UPDATE/DELETE测试逻辑是：执行原DML，对比"受影响行的变化"（变更行多重集 vs 删除行多重集），再执行变换后的DML对比变化行。这种测试能发现DML执行中的逻辑错误，例如WHERE条件优化导致更新了不该更新的行。

### 2.2 FROM子句特征覆盖

| 特征 | EET原生 | SQLancer EET | 偏差 |
|------|---------|-------------|------|
| 基表引用 | ✅ | ✅ | 一致 |
| JOIN (INNER/LEFT/RIGHT/FULL/CROSS) | ✅ 全部 | ✅ 部分(MySQL无FULL) | 小偏差 |
| 子查询(FROM中) | ✅ | ✅ | 一致 |
| **LATERAL子查询** | ✅ | **❌** | 遗漏 |
| **TABLESAMPLE** | ✅ | **❌** | 遗漏 |

### 2.3 表达式类型覆盖

| 表达式类型 | EET原生 | SQLancer EET | 偏差 |
|------------|---------|-------------|------|
| 算术运算 | ✅ 丰富(15+运算符) | ✅ 基础 | 小偏差 |
| 比较运算 | ✅ | ✅ | 一致 |
| 布尔运算(AND/OR) | ✅ + De Morgan变换 | ✅ 仅包装 | **偏差** |
| **窗口函数** | ✅ (ROW_NUMBER/RANK等) | **❌** | 遗漏 |
| **标量子查询** | ✅ (atomic_subselect) | **❌** | 遗漏 |
| COALESCE/NULLIF | ✅ | **❌** | 遗漏 |
| EXISTS子查询 | ✅ + 等价变换 | ✅ 仅MySQL识别为bool | **偏差** |
| IN子查询 | ✅ + 等价变换 | ✅ 仅MySQL识别为bool | **偏差** |
| BETWEEN | ✅ + 等价变换 | ✅ 仅遍历子节点 | **偏差** |
| LIKE/NOT LIKE | ✅ | ✅ 仅Postgres遍历 | 小偏差 |
| DISTINCT predicate | ✅ | **❌** | 遗漏 |
| CASE WHEN | ✅ | ✅ | 一致 |
| NOT表达式 | ✅ | ✅ | 一致 |
| IS NULL/IS NOT NULL | ✅ | ✅ | 一致 |
| 聚合函数 | ✅ | ✅ (MySQL/GaussDBM) | 一致 |
| 类型感知随机值生成 | ✅ (从schema获取) | ✅ (部分方言有sameTypeFallback) | 部分偏差 |

---

## 三、语法覆盖范围对比

### 3.1 EET原生生成的SQL语法模式

EET原生覆盖的语法远超SQLancer当前实现：

```sql
-- SELECT (4种形状 + INTERSECT/EXCEPT)
SELECT ... FROM tables WHERE bool_expr
SELECT ... UNION/UNION ALL SELECT ...
SELECT ... INTERSECT SELECT ...
SELECT ... EXCEPT SELECT ...
WITH cte AS (SELECT ...) SELECT ... FROM cte WHERE ...
SELECT ... FROM (SELECT ...) AS sub WHERE ...

-- DML (SQLancer全部缺失)
UPDATE t SET col=expr WHERE bool_expr
DELETE FROM t WHERE bool_expr
INSERT INTO t VALUES (...)
INSERT INTO t SELECT ... FROM ...
MERGE INTO t USING ... ON ... WHEN MATCHED THEN ...
INSERT INTO t ... ON CONFLICT ... DO UPDATE ...

-- 高级FROM特征 (SQLancer部分缺失)
SELECT ... FROM t LATERAL (SELECT ...)
SELECT ... FROM t TABLESAMPLE ...
SELECT ... FROM t FOR UPDATE

-- 高级表达式 (SQLancer部分缺失)
AGG(...) OVER (PARTITION BY ... ORDER BY ...)  -- 窗口函数
(SELECT MAX(x) FROM t)                          -- 标量子查询
COALESCE(a, b), NULLIF(a, b)
```

### 3.2 变换后的SQL输出模式差异

**EET原生变换输出（含语义重写）：**

```sql
-- 包装型: 与SQLancer一致
CASE WHEN (p OR NOT p OR p IS NULL) THEN expr ELSE rand END
((p AND NOT p AND p IS NOT NULL) OR expr)

-- 语义重写型: SQLancer完全缺失
NOT ((NOT B) OR (NOT A))           -- De Morgan: (A AND B)
(x >= a) AND (x <= b)              -- BETWEEN重写
TRUE IN (SELECT CASE WHEN pred IS NULL THEN FALSE ELSE pred END
         FROM t WHERE TRUE)        -- EXISTS重写
CASE WHEN (lhs IN subq) IS NOT NULL
    THEN EXISTS(SELECT ... WHERE lhs=col AND pred)
    ELSE NULL END                  -- IN重写
SELECT ... FROM t1 WHERE EXISTS(
    SELECT ... FROM t2
    WHERE (t1.col=t2.col OR (t1.col IS NULL AND t2.col IS NULL))
      AND t2_pred) AND t1_pred    -- INTERSECT重写
SELECT ... FROM t1 WHERE NOT EXISTS(...) AND t1_pred  -- EXCEPT重写
```

**SQLancer变换输出（仅包装型）：**

```sql
CASE WHEN (p OR NOT p OR p IS NULL) THEN expr ELSE rand END
((p AND NOT p AND p IS NOT NULL) OR expr)
CASE WHEN rand_bool THEN copy_expr ELSE expr END
1 OR random_bool    -- const 1
0 AND random_bool   -- const 0
```

---

## 四、逻辑错误检测方法对比

### 4.1 检测策略

| 策略 | EET原生 | SQLancer EET | 影响 |
|------|---------|-------------|------|
| SELECT多重集比较 | ✅ | ✅ | 一致 |
| UPDATE变更行多重集比较 | ✅ | **❌** | 无法检测UPDATE逻辑错误 |
| DELETE删除行多重集比较 | ✅ | **❌** | 无法检测DELETE逻辑错误 |
| 双次执行确认 | ✅ | ✅ | 一致 |
| 崩溃检测 | ✅ (独立于逻辑错误) | **❌** (SQLException→跳过) | 遗漏崩溃类错误 |
| 行数上限 | 10,000 | 10,000 | 一致 |

### 4.2 可检测的错误类型范围

| 错误类型 | EET原生可检测 | SQLancer EET可检测 | 偏差原因 |
|----------|--------------|-------------------|---------|
| SELECT优化器对冗余结构的错误处理 | ✅ | ✅ | 包装型规则覆盖 |
| SELECT优化器对不同语法路径的错误处理 | ✅ | **❌** | 缺少语义重写规则 |
| AND/OR分发律计算错误 | ✅ | **❌** | 缺少De Morgan变换 |
| BETWEEN与比较对等的计算错误 | ✅ | **❌** | 缺少BETWEEN变换 |
| EXISTS与IN语义差异错误 | ✅ | **❌** | 缺少EXISTS/IN变换 |
| INTERSECT/EXCEPT优化错误 | ✅ | **❌** | 缺少INTERSECT/EXCEPT变换 |
| UPDATE WHERE条件优化错误 | ✅ | **❌** | 缺少DML测试 |
| DELETE WHERE条件优化错误 | ✅ | **❌** | 缺少DML测试 |
| NULL值三值逻辑处理错误 | ✅ (EXISTS/IN/INTERSECT含NULL安全处理) | ✅ (仅包装型含三值逻辑) | 偏差：包装型覆盖有限 |
| 崩溃类错误 | ✅ | **❌** | 缺少崩溃检测 |

---

## 五、各方言实现质量差异

| 维度 | MySQL | Postgres | GaussDB-M | GaussDB-A |
|------|-------|----------|-----------|-----------|
| 查询形状 | 4种全部 | 4种全部 | 4种全部 | **仅plain SELECT** |
| JOIN ON变换 | ✅ | **❌** | ✅ | ✅ |
| EXISTS/IN识别为bool | ✅ | **❌** | **❌** | **❌** |
| 类型感知随机值 | ✅(15种类型) | ✅(gen按类型) | ✅(8种类型) | **❌(恒为0)** |
| PrintedExpression | ✅ | ✅ | ✅ | **❌(返回自身)** |
| Rule7作用域守卫 | ✅(Text+TableRef) | ✅(Text+TableRef) | ✅(Text+TableRef) | **❌(仅TableRef)** |
| ExpressionTree节点 | 18种 | 16种 | 12种 | **10种** |
| Aggregate遍历 | ✅ | **❌** | ✅ | **❌** |
| 语义重写规则 | 全部缺失 | 全部缺失 | 全部缺失 | 全部缺失 |

**GaussDB-A的EET实现质量最低**，存在多个严重问题：
- `copyExpr()`返回自身而非PrintedExpression，Rule 5/6实际上变成了两个分支指向同一对象
- UNION/CTE/Derived查询形状全部stub掉
- 类型感知随机值生成恒为0
- 缺少Text节点的Rule7守卫

---

## 六、偏差与遗漏总结

### 6.1 严重偏差（必须修复）

1. **缺少6种语义重写型变换规则** — 这是最大的偏差。当前只实现了"包装型"规则，完全没有"语义重写型"规则。EET论文的Table 2只列出了7种包装型规则，但EET原生工具的`bool_term`、`between_op`、`exists_predicate`、`in_query`和`unioned_query`都额外实现了语义重写变换。这些变换覆盖了完全不同的优化器路径，是发现深层逻辑错误的关键。

2. **缺少DML测试** — UPDATE/DELETE的逻辑错误检测是EET原生的重要功能。SQLancer的EET完全没有DML测试能力。

3. **缺少INTERSECT/EXCEPT查询形状和变换** — EET原生可以将INTERSECT重写为EXISTS、EXCEPT重写为NOT EXISTS，这是检测集合操作优化错误的关键手段。

### 6.2 中等偏差（建议修复）

4. **缺少窗口函数和标量子查询** — 这些是EET原生生成的重要表达式类型，当前完全缺失。
5. **缺少COALESCE/NULLIF变换** — 这些有明确的等价变换（COALESCE→CASE WHEN IS NOT NULL），当前缺失。
6. **缺少LATERAL子查询** — EET原生支持LATERAL，当前缺失。
7. **GaussDB-A实现严重不完整** — 多个关键功能缺失或简化。
8. **Postgres缺少JOIN ON变换和Aggregate遍历** — 减少了变换覆盖率。

### 6.3 小偏差（可后续优化）

9. **缺少崩溃检测** — EET原生区分逻辑错误和崩溃错误，SQLancer将SQL异常直接跳过。
10. **缺少阻抗反馈** — EET原生有impedance_feedback机制自动黑名单99%失败的生产规则，提高测试效率。
11. **缺少进程隔离** — EET原生使用fork隔离，SQLancer单进程运行。

---

## 七、建议的优先级修复路线

### Phase 1（高优先级）— 补齐语义重写型变换

| 序号 | 变换规则 | 实现要点 |
|------|----------|----------|
| 1 | De Morgan定律 | 在`transformBoolExpr()`中对AND/OR类型的`BinaryLogicalOperation`进行分发律变换：`(A AND B) → NOT(NOT B OR NOT A)`，`(A OR B) → NOT(NOT B AND NOT A)` |
| 2 | BETWEEN→比较 | 在`transformBoolExpr()`中对`BetweenOperation`重写：`x BETWEEN a AND b → (x >= a) AND (x <= b)` |
| 3 | EXISTS→IN | 在`transformBoolExpr()`中对`Exists`构造IN子查询：`EXISTS(SELECT ... WHERE pred) → TRUE IN (SELECT CASE WHEN pred IS NULL THEN FALSE ELSE pred END FROM t WHERE TRUE)` |
| 4 | IN→EXISTS | 在`transformBoolExpr()`中对`InOperation`构造CASE+EXISTS：`lhs IN (SELECT ... WHERE pred) → CASE WHEN (lhs IN subq) IS NOT NULL THEN EXISTS(SELECT ... WHERE lhs=col AND pred) ELSE NULL END` |
| 5 | INTERSECT→EXISTS | 新增INTERSECT查询形状生成 + 变换：`Q1 INTERSECT Q2 → Q1 WHERE EXISTS(Q2 WHERE col_eq_or_null AND Q2_pred)` |
| 6 | EXCEPT→NOT EXISTS | 新增EXCEPT查询形状生成 + 变换：`Q1 EXCEPT Q2 → Q1 WHERE NOT EXISTS(Q2 WHERE col_eq_or_null AND Q2_pred)` |

### Phase 2（中优先级）— 扩展查询类型和表达式

| 序号 | 功能 | 实现要点 |
|------|------|----------|
| 7 | INTERSECT/EXCEPT查询形状 | 在`EETQueryGenerator`中新增mode 5/6，生成INTERSECT/EXCEPT SELECT |
| 8 | DML测试 | 新增`EETUpdateOracle`和`EETDeleteOracle`，对比变更行/删除行多重集 |
| 9 | 标量子查询 | 在表达式生成中支持`(SELECT ...)`标量子查询，纳入变换遍历 |
| 10 | COALESCE/NULLIF变换 | `COALESCE(a,b) → CASE WHEN a IS NOT NULL THEN a ELSE b END`；`NULLIF(a,b) → CASE WHEN a=b THEN NULL ELSE a END` |
| 11 | 窗口函数 | 在SELECT列表中生成`AGG() OVER (PARTITION BY ... ORDER BY ...)`，纳入变换遍历 |

### Phase 3（低优先级）— 完善基础设施

| 序号 | 功能 | 实现要点 |
|------|------|----------|
| 12 | 修复GaussDB-A缺陷 | 实现`GaussDBAPrintedExpression`、类型感知fallback、UNION/CTE/Derived形状、Text Rule7守卫 |
| 13 | 修复Postgres缺陷 | 补充JOIN ON变换、Aggregate遍历、EXISTS/IN识别为bool |
| 14 | LATERAL子查询 | 在FROM子句中支持LATERAL生成 |
| 15 | 阻抗反馈 | 记录变换成功率，自动跳过高失败率的表达式类型 |
| 16 | 崩溃检测 | 区分SQLException类型，记录崩溃而非直接跳过 |