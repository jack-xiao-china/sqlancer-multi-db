# EET原生工具 vs SQLancer EET Oracle — 当前实现状态审视

> 审视日期：2026-05-26
> 基于文档：`docs/eet_native_vs_sqlancer_deep_analysis.md`
> 对比对象：EET原生工具 (`D:\Jack.Xiao\dbtools\EET-main`) vs SQLancer EET Oracle当前实现

---

## 一、严重偏差修复状态（Phase 1）

### 1.1 语义重写型变换规则 ✅ 已实现

| 规则 | 原形式 | 等价形式 | SQLancer实现状态 |
|------|--------|----------|-----------------|
| De Morgan定律 | `(A) AND (B)` | `NOT( NOT(B) OR NOT(A) )` | ✅ 已实现并注册到所有方言 |
| BETWEEN→比较 | `x BETWEEN a AND b` | `(x >= a) AND (x <= b)` | ✅ 已实现并注册到所有方言 |
| EXISTS→IN | `EXISTS(SELECT...WHERE pred)` | `TRUE IN (SELECT CASE WHEN pred IS NULL THEN FALSE ELSE pred END FROM t WHERE TRUE)` | ✅ 已实现并注册到所有方言 |
| IN→EXISTS | `lhs IN (SELECT...WHERE pred)` | `CASE WHEN (lhs IN subq) IS NOT NULL THEN EXISTS(...) ELSE NULL END` | ✅ 已实现并注册到所有方言 |
| INTERSECT→EXISTS | `Q1 INTERSECT Q2` | `Q1 WHERE EXISTS(Q2 WHERE col_eq_or_null AND Q2_pred)` | ✅ 仅PostgreSQL（含NULL-safe列等值） |
| EXCEPT→NOT EXISTS | `Q1 EXCEPT Q2` | `Q1 WHERE NOT EXISTS(Q2 WHERE col_eq_or_null AND Q2_pred)` | ✅ 仅PostgreSQL（含NULL-safe列等值） |

**各方言注册情况：**
- **MySQL**: 4个规则（DeMorgan, BetweenToComp, ExistsToIn, InToExists）— INTERSECT/EXCEPT因MySQL语法不支持而跳过
- **PostgreSQL**: 6个规则（全部）— 含完整的NULL-safe列等值实现
- **GaussDB-M**: 4个规则（同MySQL）
- **GaussDB-A**: 4个规则（同MySQL）

**偏差修复程度：100%**

---

### 1.2 DML测试 ✅ 已实现

| DML类型 | EET原生 | SQLancer EET | 实现状态 |
|---------|---------|-------------|---------|
| UPDATE | ✅ | ✅ | 已实现（MySQL, PostgreSQL, GaussDB-M, GaussDB-A） |
| DELETE | ✅ | ✅ | 已实现（MySQL, PostgreSQL, GaussDB-M, GaussDB-A） |
| INSERT-SELECT | ✅ | ✅ | 已实现（MySQL, PostgreSQL, GaussDB-M, GaussDB-A） |

**DML Oracle实现细节：**
- 继承`EETDMLOracleBase`，实现snapshot→execute→restore→compare流程
- 比较变更行多重集（UPDATE）和删除行多重集（DELETE）
- 双次执行确认机制已集成
- 阻抗跟踪已集成

**偏差修复程度：100%**

---

### 1.3 INTERSECT/EXCEPT查询形状和变换 ⚠️ 部分实现

| 方面 | EET原生 | SQLancer EET | 偏差 |
|------|---------|-------------|------|
| INTERSECT AST节点 | ✅ | ✅ 仅PostgreSQL | 其他方言不支持INTERSECT语法 |
| EXCEPT AST节点 | ✅ | ✅ 仅PostgreSQL | 其他方言不支持EXCEPT语法 |
| INTERSECT→EXISTS变换 | ✅ | ✅ 仅PostgreSQL | 含NULL-safe列等值，完整对齐原生 |
| EXCEPT→NOT EXISTS变换 | ✅ | ✅ 仅PostgreSQL | 含NULL-safe列等值，完整对齐原生 |

**偏差原因分析：**
- MySQL/GaussDB-M/GaussDB-A均不支持INTERSECT/EXCEPT语法，这是合理的方言差异
- PostgreSQL已完整实现，含`(q1=q2) OR (q1 IS NULL AND q2 IS NULL)`的NULL-safe处理

**偏差修复程度：PostgreSQL 100%，其他方言因语法限制无需实现**

---

## 二、中等偏差修复状态（Phase 2）

### 2.1 窗口函数 ⚠️ 部分实现

| 方面 | EET原生 | SQLancer EET | 偏差 |
|------|---------|-------------|------|
| MySQL WindowFunction AST | ✅ | ✅ 已有节点 | ExpressionTree已处理 |
| PostgreSQL WindowFunction AST | ✅ | ✅ 已有节点 | ExpressionTree已处理 |
| GaussDB-M WindowFunction AST | ✅ | ✅ 已有节点 | ExpressionTree已处理 |
| GaussDB-A WindowFunction AST | ✅ | ❌ 缺失 | ExpressionTree未覆盖 |
| 窗口函数变换规则 | ✅ mapChildren遍历 | ⚠️ 仅MySQL/PG/GaussDB-M有遍历 | GaussDB-A缺失 |

**偏差修复程度：75%**

---

### 2.2 标量子查询 ❌ 未实现

| 方面 | EET原生 | SQLancer EET | 偏差 |
|------|---------|-------------|------|
| 标量子查询AST节点 | ✅ atomic_subselect | ❌ 无专门节点 | 缺失 |
| mapChildren遍历 | ✅ | ❌ | 缺失 |
| 等价变换规则 | ✅ | ❌ | 缺失 |

**偏差修复程度：0%**

---

### 2.3 COALESCE/NULLIF变换 ❌ 未实现

| 规则 | 原形式 | 等价形式 | SQLancer实现状态 |
|------|--------|----------|-----------------|
| COALESCE→CASE | `COALESCE(a,b)` | `CASE WHEN a IS NOT NULL THEN a ELSE b END` | ❌ 缺失 |
| NULLIF→CASE | `NULLIF(a,b)` | `CASE WHEN a=b THEN NULL ELSE a END` | ❌ 缺失 |

**偏差修复程度：0%**

---

### 2.4 LATERAL子查询 ❌ 未实现

| 方面 | EET原生 | SQLancer EET | 偏差 |
|------|---------|-------------|------|
| LATERAL AST节点 | ✅ | ❌ | 缺失 |
| FROM子句生成 | ✅ | ❌ | 缺失 |

**偏差修复程度：0%**

---

### 2.5 GaussDB-A实现质量 ⚠️ 已部分修复

| 原偏差 | 修复状态 |
|--------|---------|
| `copyExpr()`返回自身而非PrintedExpression | ✅ 已修复：`GaussDBAPrintedExpression`已实现 |
| 类型感知随机值生成恒为0 | ✅ 已修复：`sameTypeFallback`已实现 |
| UNION/CTE/Derived查询形状全部stub | ❌ 仍缺失 |
| 缺少Text节点的Rule7守卫 | ⚠️ GaussDB-A无Text节点（合理差异） |
| ExpressionTree节点覆盖少 | ⚠️ 已扩展到13种，但仍缺少Aggregate遍历、算术运算等 |

**偏差修复程度：60%**

---

### 2.6 PostgreSQL JOIN ON变换和Aggregate遍历 ⚠️ 部分修复

| 原偏差 | 修复状态 |
|--------|---------|
| 缺少JOIN ON变换 | ❌ 仍缺失 |
| 缺少Aggregate遍历 | ❌ ExpressionTree仍无Aggregate处理 |
| EXISTS/IN识别为bool | ✅ 已修复：`PostgresExists`已实现 |

**偏差修复程度：33%**

---

## 三、基础设施修复状态（Phase 3）

### 3.1 崩溃检测 ✅ 已实现

| 方面 | EET原生 | SQLancer EET | 实现状态 |
|------|---------|-------------|---------|
| 崩溃类错误识别 | ✅ fork+信号 | ✅ SQLState+ErrorCode判断 | 已实现`EETCrashTracker` |
| 崩溃日志记录 | ✅ | ✅ 写入`eet_crash_log.txt` | 已实现 |
| 区分逻辑错误vs崩溃 | ✅ | ✅ | 已集成到`EETOracleBase` |

**偏差修复程度：100%**

---

### 3.2 阻抗反馈 ✅ 已实现

| 方面 | EET原生 | SQLancer EET | 实现状态 |
|------|---------|-------------|---------|
| 表达式类型成功率跟踪 | ✅ | ✅ `EETImpedanceTracker` | 已实现 |
| 99%失败率自动黑名单 | ✅ | ✅ `isBlacklisted()` | 阈值0.99，最小样本100 |
| 集成到变换流程 | ✅ | ✅ | `EETTransformerBase`已调用 |

**偏差修复程度：100%**

---

### 3.3 双次执行确认 ✅ 已实现

| 方面 | EET原生 | SQLancer EET | 实现状态 |
|------|---------|-------------|---------|
| SELECT双重集比较 | ✅ | ✅ | `EETOracleBase`已实现 |
| 减少flaky测试 | ✅ | ✅ | 两次执行结果一致才报错 |

**偏差修复程度：100%**

---

## 四、其他缺失功能（EET原生支持但SQLancer未实现）

| 功能 | EET原生 | SQLancer EET | 重要性 |
|------|---------|-------------|--------|
| MERGE Oracle | ✅ 仅PostgreSQL原生语法 | ❌ 缺失 | 中 |
| UPSERT/ON CONFLICT | ✅ | ❌ 缺失 | 中 |
| SELECT FOR UPDATE | ✅ | ❌ 缺失 | 低 |
| PREPARE语句测试 | ✅ | ❌ 缺失 | 低 |
| UPDATE/DELETE RETURNING | ✅ | ❌ 缺失 | 低 |
| TABLESAMPLE | ✅ | ❌ 缺失 | 低 |
| const_bool变换(TRUE→expr OR TRUE) | ✅ | ⚠️ 仅ConstBoolTransform处理常量 | 低 |

---

## 五、各方言ExpressionTree节点覆盖对比

| 节点类型 | MySQL | PostgreSQL | GaussDB-M | GaussDB-A |
|----------|-------|------------|-----------|-----------|
| TableReference | ✅ | ✅ | ✅ | ✅ |
| ColumnReference | ✅ | ✅ | ✅ | ✅ |
| Constant | ✅ | ✅ | ✅ | ✅ |
| Text | ✅ | ✅ | ✅ | ❌(无节点) |
| BinaryLogicalOperation | ✅ | ✅ | ✅ | ✅ |
| BinaryComparisonOperation | ✅ | ✅ | ✅ | ✅ |
| UnaryPrefixOperation | ✅ | ✅ | ✅ | ✅ |
| UnaryPostfixOperation | ✅ | ✅ | ✅ | ✅ |
| CaseWhen | ✅ | ✅ | ✅ | ✅ |
| BetweenOperation | ✅ | ✅ | ✅ | ✅ |
| InOperation | ✅ | ✅ | ✅ | ✅ |
| Exists | ✅ | ✅ | ✅ | ✅ |
| PrintedExpression | ✅ | ✅ | ✅ | ✅ |
| Aggregate | ✅ | ❌ | ✅ | ❌(无遍历) |
| WindowFunction | ✅ | ✅ | ✅ | ❌ |
| CteTableReference | ✅ | ✅ | ✅ | ❌ |
| 算术运算 | ✅ | ✅ | ✅ | ⚠️(有AST无遍历) |
| 标量子查询 | ❌ | ❌ | ❌ | ❌ |

---

## 六、总结

### 已完整修复的严重偏差（原分析文档中的"必须修复"）

| 原偏差编号 | 原描述 | 修复状态 |
|------------|--------|---------|
| 1 | 缺少6种语义重写型变换规则 | ✅ 已修复（MySQL 4/6，PostgreSQL 6/6，GaussDB 4/6） |
| 2 | 缺少DML测试 | ✅ 已修复（UPDATE/DELETE/INSERT-SELECT全部实现） |
| 3 | 缺少INTERSECT/EXCEPT查询形状和变换 | ✅ PostgreSQL已完整实现 |

### 仍存在的偏差

| 原偏差编号 | 原描述 | 当前状态 | 优先级 |
|------------|--------|---------|--------|
| 4 | 缺少窗口函数和标量子查询 | ⚠️ 窗口函数部分实现，标量子查询缺失 | 中 |
| 5 | 缺少COALESCE/NULLIF变换 | ❌ 仍缺失 | 中 |
| 6 | 缺少LATERAL子查询 | ❌ 仍缺失 | 低 |
| 7 | GaussDB-A实现严重不完整 | ⚠️ 60%修复 | 中 |
| 8 | Postgres缺少JOIN ON变换和Aggregate遍历 | ❌ 仍缺失 | 低 |
| 9 | 缺少崩溃检测 | ✅ 已修复 | — |
| 10 | 缺少阻抗反馈 | ✅ 已修复 | — |

### 综合评估

SQLancer EET Oracle已**基本完整继承**了EET原生工具的核心能力：

**100%修复的功能：**
- 语义重写型变换规则（De Morgan、BETWEEN→比较、EXISTS→IN、IN→EXISTS、INTERSECT→EXISTS、EXCEPT→NOT EXISTS）
- DML测试（UPDATE、DELETE、INSERT-SELECT）
- 阻抗反馈机制
- 崩溃检测
- 双次执行确认
- 多重集比较

**仍需补齐的功能：**
- 标量子查询变换（重要性：中）
- COALESCE/NULLIF变换规则（重要性：中）
- GaussDB-A UNION/CTE/Derived查询形状（重要性：中）
- PostgreSQL JOIN ON变换和Aggregate遍历（重要性：低）
- LATERAL子查询生成（重要性：低）
- MERGE/UPSERT/SELECT FOR UPDATE等高级语句（重要性：低）

---

## 七、建议的后续补齐路线

### 高优先级（建议实现）

| 序号 | 功能 | 实现要点 |
|------|------|----------|
| 1 | 标量子查询变换 | 创建`ScalarSubquery` AST节点，添加到ExpressionTree的mapChildren/forEachChild |
| 2 | COALESCE→CASE规则 | 创建`EETCoalesceToCaseRule`，注册到各方言Transformer |
| 3 | GaussDB-A UNION/CTE/Derived | 补齐`GaussDBAEETQueryGenerator`的UNION/CTE/Derived形状生成 |
| 4 | GaussDB-A算术运算遍历 | 添加`GaussDBABinaryArithmeticOperation`到ExpressionTree |

### 低优先级（可选实现）

| 序号 | 功能 | 实现要点 |
|------|------|----------|
| 5 | PostgreSQL JOIN ON变换 | 在`PostgresEETTransformAdapter`添加`transformJoinOn`方法 |
| 6 | PostgreSQL Aggregate遍历 | 在`PostgresEETExpressionTree`添加Aggregate的mapChildren |
| 7 | LATERAL子查询 | 在FROM子句生成中支持LATERAL |
| 8 | MERGE Oracle | 仅PostgreSQL方言实现MERGE语句测试 |