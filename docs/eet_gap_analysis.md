# EET Oracle Gap Analysis Report

## Executive Summary

本文档对比EET原生工具（C++, D:\Jack.Xiao\dbtools\EET-main）与SQLancer重构的EET Oracle（Java），详细记录功能、算法、语法覆盖差距，并给出补齐方案。

**已完成的差距补齐**：
- G1: PostgreSQL INTERSECT→EXISTS / EXCEPT→NOT EXISTS 转换规则实现
- G2: 阻抗跟踪器(ImpedanceTracker)集成生效
- G3: MySQL ANY/ALL比较子查询基本支持

**待完成的差距**：
- G8: INSERT-SELECT Oracle
- G15/G18: GaussDB-M/A DML Oracle (UPDATE/DELETE)
- G19/G20: GaussDB-A表达式覆盖扩展

---

## 1. 变换规则差距对照表

| 规则 | 原生EET位置 | SQLancer位置 | 状态 | 说明 |
|------|-------------|--------------|------|------|
| **CASE WHEN Tautology (Rule 1-2)** | value_expr.cc:79-93 | EETBoolTransformRule.java | ✅完整 | `(p OR NOT p OR p IS NULL) AND expr` |
| **CASE WHEN Value (Rule 3-6)** | value_expr.cc:94-107 | EETValueTransformRule.java | ✅完整 | CASE WHEN wrapping for all expressions |
| **ConstBoolTransform** | const_bool.cc:42-83 | EETConstBoolTransformRule.java | ✅完整 | `1 → 1 OR rand_bool`, `0 → 0 AND rand_bool` |
| **De Morgan's Law** | bool_term.cc:36-51 | EETDeMorganRule.java | ✅完整 | `(A AND B) → NOT(NOT A OR NOT B)` |
| **BETWEEN→Comparison** | between_op.cc:12-44 | EETBetweenToComparisonRule.java | ✅完整 | `x BETWEEN a AND b → (x >= a) AND (x <= b)` |
| **IN→EXISTS** | in_query.cc:63-140 | EETInToExistsRule.java + 各adapter | ✅完整 | NULL-safe CASE IS NOT NULL |
| **EXISTS→IN** | exists_predicate.cc:60-139 | EETExistsToInRule.java + 各adapter | ✅完整 | TRUE IN (SELECT CASE WHEN...) |
| **INTERSECT→EXISTS** | grammar.cc:1980-2034 | PostgresEETTransformAdapter.java:324-392 | ✅已实现 | NULL-safe列等值 `(q1=q2) OR (q1 IS NULL AND q2 IS NULL)` |
| **EXCEPT→NOT EXISTS** | grammar.cc:1980-2034 | PostgresEETTransformAdapter.java:395-463 | ✅已实现 | NOT EXISTS包装 |
| **Impedance Blacklisting** | impedance.cc:43-52 | EETTransformerBase.java + EETOracleBase.java | ✅已集成 | >99%失败率自动黑名单表达式类型 |

---

## 2. 表达式类型差距对照表

| 表达式类型 | 原生EET | MySQL | PostgreSQL | GaussDB-M | GaussDB-A | 状态 |
|------------|---------|--------|------------|-----------|-----------|------|
| **Column Reference** | column_reference.cc | ✅ | ✅ | ✅ | ✅ | 完整 |
| **Constant** | const_expr.cc | ✅ | ✅ | ✅ | ✅ | 完整 |
| **Binary Logical (AND/OR)** | bool_term.cc | ✅ | ✅ | ✅ | ✅ | 完整 |
| **Binary Comparison** | comparison_op.cc | ✅ | ✅ | ✅ | ✅ | 完整 |
| **Binary Arithmetic** | binop_expr.cc | ✅ | ✅ | ❌缺少mapChildren | ❌缺少mapChildren | 部分缺失 |
| **CASE WHEN** | case_expr.cc | ✅ | ✅ | ✅ | ✅ | 完整 |
| **BETWEEN** | between_op.cc | ✅ | ✅ | ✅ | ✅ | 完整 |
| **IN Subquery** | in_query.cc | ✅ | ✅ | ✅ | ✅ | 完整 |
| **EXISTS** | exists_predicate.cc | ✅ | ✅ | ✅ | ✅ | 完整 |
| **NOT** | not_expr.cc | ✅ | ✅ | ✅ | ✅ | 完整 |
| **IS NULL/IS NOT NULL** | null_predicate.cc | ✅ | ✅ | ✅ | ✅ | 完整 |
| **LIKE** | like_op.cc | ✅ | ✅ (有AST) | ✅ (有AST) | ❌无mapChildren | ❌无mapChildren | 部分缺失 |
| **ANY/ALL Subquery** | comp_subquery.cc | ✅基本支持 | ❌无 | ❌无 | ❌无 | **新增(MySQL)** |
| **COALESCE** | coalesce.cc | ✅ (有AST) | ✅ (有AST) | ✅ (有AST) | ❌无AST | ❌无AST | 部分缺失 |
| **NULLIF** | nullif.cc (引用但文件不存在) | ❌无AST | ❌无AST | ❌无AST | ❌无AST | ❌无AST | 缺失 |
| **Scalar Subquery** | atomic_subselect.cc | ❌需验证 | ❌需验证 | ❌需验证 | ❌需验证 | ❌需验证 | 需验证mapChildren |
| **Function Call** | funcall.cc | ✅ | ✅ | ❌无mapChildren | ❌无mapChildren | ❌无AST | 部分缺失 |
| **Aggregate** | schema.hh AGG宏 | ✅ | ✅ | ✅ | ✅ | ❌无generator | 部分缺失 |
| **Window Function** | window_function.cc + win_funcall.cc | ✅ | ✅ | ❌无mapChildren | ❌无mapChildren | ❌无AST | 部分缺失 |
| **Cast** | (via funcall) | ✅ | ✅ | ❌无mapChildren | ❌无mapChildren | ❌无generator | 部分缺失 |

---

## 3. 语句类型差距对照表

| 语句类型 | 原生EET位置 | SQLancer支持 | 状态 |
|----------|-------------|--------------|------|
| **SELECT** | qcn_select_tester.cc | MySQL/PostgreSQL/GaussDB-M/A | ✅完整 |
| **SELECT UNION** | grammar.cc unioned_query | MySQL/PostgreSQL/GaussDB-M/A | ✅完整 |
| **SELECT INTERSECT** | grammar.cc | PostgreSQL (已实现转换) | ✅已实现 |
| **SELECT EXCEPT** | grammar.cc | PostgreSQL (已实现转换) | ✅已实现 |
| **SELECT CTE (WITH)** | qcn_cte_tester.cc | MySQL/PostgreSQL/GaussDB-M (随机shape) | ⚠️缺少独立变换 |
| **SELECT Derived Table** | grammar.cc | MySQL/PostgreSQL/GaussDB-M/A | ✅完整 |
| **UPDATE** | qcn_update_tester.cc | MySQL/PostgreSQL (有) / GaussDB-M/A (无) | ⚠️GaussDB缺失 |
| **DELETE** | qcn_delete_tester.cc | MySQL/PostgreSQL (有) / GaussDB-M/A (无) | ⚠️GaussDB缺失 |
| **INSERT-SELECT** | qcn_insert_select_tester.cc | 无 | ❌缺失 |
| **MERGE** | merge_stmt.cc | 无 | ❌缺失(PostgreSQL) |
| **SELECT FOR UPDATE** | select_for_update.hh | 无 | ❌缺失 |

---

## 4. DBMS特定差距

### MySQL
- ✅ 完整的EET支持
- ✅ 已添加ANY/ALL子查询基本支持
- ❌ 不支持INTERSECT/EXCEPT语法（合理缺失）

### PostgreSQL
- ✅ 完整的EET支持
- ✅ INTERSECT→EXISTS/EXCEPT→NOT EXISTS已实现
- ✅ 完整的set operation AST节点

### GaussDB-M (MySQL兼容模式)
- ✅ SELECT EET支持
- ❌ 无EET_UPDATE/EET_DELETE Oracle
- ❌ ExpressionTree缺少算术/函数/窗口/Cast节点处理
- ❌ 不支持INTERSECT/EXCEPT语法（合理缺失，MySQL兼容模式限制）

### GaussDB-A (Oracle兼容模式) - 严重差距
- ✅ SELECT EET基础支持
- ❌ 无EET_UPDATE/EET_DELETE Oracle
- ❌ ExpressionGenerator仅7种Action (需扩展到20+)
- ❌ QueryGenerator UNION/CTE/Derived全部stub返回baseSelect
- ❌ ExpressionTree缺少Text/Cte/Aggregate/算术/函数/窗口节点
- ❌ 缺少INTERSECT/MINUS AST节点和转换（GaussDB-A支持此语法）
- ❌ 连接方式需改为与GaussDB-M共用环境，自动创建A兼容数据库

---

## 5. 基础设施差距

### 阻抗跟踪器
- ✅ 已集成到EETTransformerBase
- ✅ 变换前检查isBlacklisted()
- ✅ 变换成功/失败时记录表达式类型
- ⚠️ ExpressionGenerator尚未集成黑名单过滤（需更大改动）

### 结果对比
- ✅ EETMultisetComparator已实现
- ✅ 顺序不敏感、保留重复行
- ✅ Double-execution确认机制

### Bug最小化
- ✅ EETComponentReducer两阶段缩减
- ✅ EETReproducer带AST上下文

---

## 6. 补齐建议

### Phase 1 (已完成)
- G1: PostgreSQL INTERSECT→EXISTS / EXCEPT→NOT EXISTS ✅
- G2: 阻抗跟踪器生效 ✅
- G3: MySQL ANY/ALL子查询基本支持 ✅

### Phase 2 (MySQL/PostgreSQL优先)
- G8: INSERT-SELECT Oracle实现
  - 创建 EETInsertSelectOracleBase
  - 实现MySQL/PostgreSQL/GaussDB-M/A版本
  - 注册到OracleFactories

### Phase 3 (GaussDB-A/GaussDB-M)
- G15/G18: GaussDB DML Oracle
  - 创建 GaussDBMEETUpdateOracle/DeleteOracle
  - 创建 GaussDBAEETUpdateOracle/DeleteOracle
  - 注册到GaussDBMOracleFactory/GaussDBAOracleFactory

- G19/G20: GaussDB-A表达式覆盖扩展
  - Action枚举扩展：CAST, AGGREGATE, CASE, ARITHMETIC, FUNCTION, EXISTS, LIKE, COALESCE
  - QueryGenerator实现UNION/CTE/Derived
  - ExpressionTree添加缺失节点处理
  - INTERSECT/MINUS AST节点和转换实现

---

## 7. 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v2.0.47 | 2026-05-26 | G1: PostgreSQL INTERSECT→EXISTS/EXCEPT→NOT EXISTS实现 |
| v2.0.47 | 2026-05-26 | G2: 阻抗跟踪器集成生效 |
| v2.0.47 | 2026-05-26 | G3: MySQL ANY/ALL子查询基本支持 |

---

## 附录：关键文件清单

### 原生EET关键文件
- `value_expr/value_expr.cc` - CASE WHEN tautology变换
- `value_expr/bool_expr/bool_binop/bool_term.cc` - De Morgan定律
- `value_expr/bool_expr/in_query.cc` - IN→EXISTS变换
- `value_expr/bool_expr/exists_predicate.cc` - EXISTS→IN变换
- `grammar.cc:1980-2034` - INTERSECT/EXCEPT变换
- `impedance.cc` - 阻抗跟踪

### SQLancer EET关键文件
- `src/sqlancer/common/oracle/eet/EETTransformerBase.java` - 核心变换算法
- `src/sqlancer/common/oracle/eet/EETOracleBase.java` - SELECT Oracle基类
- `src/sqlancer/common/oracle/eet/EETTransformAdapter.java` - 方言适配器接口
- `src/sqlancer/postgres/oracle/ext/eet/PostgresEETTransformAdapter.java` - PostgreSQL适配器
- `src/sqlancer/mysql/oracle/eet/MySQLEETTransformAdapter.java` - MySQL适配器
- `src/sqlancer/mysql/ast/MySQLAnyAllSubquery.java` - ANY/ALL子查询AST节点(新增)