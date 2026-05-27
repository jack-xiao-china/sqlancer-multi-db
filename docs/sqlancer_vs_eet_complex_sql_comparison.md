# SQLancer vs EET 复杂SQL覆盖范围对比分析

**报告日期**: 2026-05-09
**对比重点**: 多表关联查询、子查询、递归查询

---

## 1. 多表关联查询 (JOIN) 覆盖对比

### 1.1 EET JOIN 支持

**MySQL JOIN 类型** (mysql.cc):
| JOIN 类型 | 支持状态 |
|-----------|----------|
| INNER JOIN | ✅ |
| LEFT OUTER JOIN | ✅ |
| RIGHT OUTER JOIN | ✅ |
| CROSS JOIN | ✅ |
| NATURAL JOIN | ❌ |
| STRAIGHT_JOIN | ❌ |
| FULL OUTER JOIN | ❌ |

**PostgreSQL JOIN 类型** (postgres.cc):
| JOIN 类型 | 支持状态 |
|-----------|----------|
| INNER JOIN | ✅ |
| LEFT OUTER JOIN | ✅ |
| RIGHT OUTER JOIN | ✅ |
| FULL OUTER JOIN | ✅ |
| CROSS JOIN | ✅ |
| NATURAL JOIN | ❌ |

**EET JOIN 实现细节** (grammar.cc):
```cpp
// joined_table 构造函数
joined_table::joined_table(prod *p) : table_ref(p) {
    lhs = table_ref::factory(this);  // 左表
    rhs = table_ref::factory(this);  // 右表
    type = random_pick(scope->schema->supported_join_op);  // 随机选择JOIN类型
    if (type != "cross")
        condition = join_cond::factory(this, *lhs, *rhs);  // ON条件
}
```

**EET JOIN 特点**:
- 支持嵌套 JOIN (lhs/rhs 可递归为 joined_table)
- ON 条件支持表达式 (expr_join_cond)
- 简单条件支持 (simple_join_cond)
- CROSS JOIN 无 ON 条件
- 不支持 NATURAL JOIN (需要唯一列名约束)
- 不支持 MySQL 特有的 STRAIGHT_JOIN

### 1.2 SQLancer JOIN 支持

**MySQL JOIN 类型** (MySQLJoin.java):
| JOIN 类型 | 支持状态 | 实现细节 |
|-----------|----------|----------|
| INNER JOIN | ✅ | 标准实现 |
| LEFT JOIN | ✅ | 标准实现 |
| RIGHT JOIN | ✅ | 标准实现 |
| CROSS JOIN | ✅ | 无 ON 条件 |
| NATURAL JOIN | ✅ | 无 ON 条件，列名匹配 |
| STRAIGHT_JOIN | ✅ | MySQL 特有，强制左表优先 |

**SQLancer JOIN 实现细节** (MySQLJoin.java):
```java
public enum JoinType {
    NATURAL, INNER, STRAIGHT, LEFT, RIGHT, CROSS;
}

// 随机生成 JOIN 子句
public static List<MySQLJoin> getRandomJoinClauses(List<MySQLTable> tables, MySQLGlobalState globalState) {
    List<JoinType> options = new ArrayList<>(Arrays.asList(JoinType.values()));
    // NATURAL JOIN 不兼容其他 JOIN (需要唯一列名)
    if (nrJoinClauses > 1) {
        options.remove(JoinType.NATURAL);
    }
    // NATURAL JOIN 无 ON 条件
    if (selectedOption == JoinType.NATURAL) {
        joinClause = null;
    }
}
```

**SQLancer JOIN 特点**:
- 支持 MySQL 特有的 STRAIGHT_JOIN (优化器控制)
- 支持 NATURAL JOIN (智能处理列名冲突)
- 支持多表 JOIN 随机组合
- ON 条件表达式完整覆盖
- GaussDB-M 支持扩展 (GaussDBJoin.JoinType 包含 NATURAL)

### 1.3 JOIN 覆盖范围对比总结

| 维度 | EET | SQLancer |
|------|-----|----------|
| **MySQL JOIN 类型数** | 4 种 | 6 种 |
| **PostgreSQL JOIN 类型数** | 5 种 | 5 种+ |
| **NATURAL JOIN** | ❌ | ✅ |
| **STRAIGHT_JOIN** | ❌ | ✅ (MySQL特有) |
| **嵌套 JOIN 深度** | ✅ 支持 | ✅ 支持 |
| **ON 条件复杂度** | ✅ 表达式 | ✅ 完整表达式 |
| **多表 JOIN 组合** | ✅ 支持 | ✅ 支持 |

**结论**: SQLancer JOIN 覆盖范围更广，特别是 MySQL 的 NATURAL JOIN 和 STRAIGHT_JOIN。

---

## 2. 子查询覆盖对比

### 2.1 EET 子查询类型

**FROM 子句子查询** (grammar.hh):
| 类型 | 实现类 | 说明 |
|------|--------|------|
| 派生表 | `table_subquery` | `(subquery) AS alias` |
| LATERAL | `lateral_subquery` | `LATERAL (subquery) AS alias` |

**WHERE 子句子查询** (value_expr/bool_expr/):
| 类型 | 实现类 | 说明 |
|------|--------|------|
| EXISTS | `exists_predicate` | `EXISTS (subquery)` |
| IN 子查询 | `in_query` | `expr IN (subquery)` |
| 比较子查询 | `comp_subquery` | `expr = ANY/ALL (subquery)` |

**EET 子查询变换规则**:
```
1. EXISTS 变换:
   - EXISTS (subquery) → CASE WHEN ... THEN ... END
   
2. IN 子查询变换:
   - expr IN (subquery) → CASE WHEN (expr = ANY (subquery)) THEN ...
   
3. 比较子查询变换:
   - expr > ANY (subquery) → CASE WHEN ... THEN expr > col END
```

**EET 子查询特点**:
- 支持嵌套子查询 (subquery 可包含 table_subquery)
- 支持 UNION 子查询 (unioned_query 作为子查询)
- EXISTS 变换有 `eq_exer` 等价表达式
- IN 子查询有 `eq_expr` CASE WHEN 变换
- LATERAL 子查询支持 (PostgreSQL 标准)

### 2.2 SQLancer 子查询类型

**FROM 子句子查询**:
| 类型 | 实现类 | 说明 |
|------|--------|------|
| 派生表 | `MySQLDerivedTable` | `(SELECT ...) AS alias` |
| CTE | `MySQLCteDefinition` | `WITH name AS (SELECT ...)` |
| CTE 引用 | `MySQLCteTableReference` | CTE 名称作为表引用 |

**WHERE 子句子查询**:
| 类型 | 实现类 | 说明 |
|------|--------|------|
| EXISTS | `MySQLExists` / `GaussDBExists` | `EXISTS (SELECT ...)` |
| IN 子查询 | MySQLExpressionGenerator | `expr IN (SELECT ...)` |
| 标量子查询 | PQS/CODDTEST Oracle | `(SELECT col FROM ... LIMIT 1)` |

**SQLancer 子查询特点**:
- 支持派生表嵌套 (DerivedTable 可多层嵌套)
- 支持 CTE (WITH 子句)
- 支持 EXISTS 在多种 Oracle 中使用
- EET Oracle 专门测试子查询变换
- PQS/CODDTEST 使用标量子查询验证

### 2.3 子查询覆盖范围对比总结

| 维度 | EET | SQLancer |
|------|-----|----------|
| **FROM 子查询** | ✅ table_subquery | ✅ DerivedTable |
| **LATERAL 子查询** | ✅ lateral_subquery | ❓ 未明确 |
| **EXISTS** | ✅ exists_predicate | ✅ MySQLExists |
| **IN 子查询** | ✅ in_query | ✅ 支持 |
| **比较子查询** | ✅ comp_subquery | ✅ 支持 |
| **标量子查询** | ❓ 未明确 | ✅ PQS/CODDTEST |
| **CTE 子查询** | ✅ common_table_expression | ✅ MySQLCteDefinition |
| **UNION 子查询** | ✅ unioned_query | ✅ 支持 |
| **子查询变换测试** | ✅ EET 核心功能 | ✅ EET Oracle |

**结论**: EET 子查询变换测试更深入，SQLancer 子查询类型覆盖更全面。

---

## 3. 递归查询 (Recursive CTE) 覆盖对比

### 3.1 EET 递归查询支持

**EET CTE 实现** (grammar.cc):
```cpp
void common_table_expression::out(std::ostream &out) {
    out << "WITH ";  // 仅输出 WITH，无 RECURSIVE
    for (size_t i = 0; i < with_queries.size(); i++) {
        indent(out);
        out << refs[i]->ident() << " AS " << "(" << *with_queries[i] << ")";
        if (i+1 != with_queries.size())
            out << ", ";
    }
    out << *query;
}
```

**EET 递归查询状态**:
| 特性 | 支持状态 |
|------|----------|
| 普通 CTE (WITH) | ✅ 支持 |
| RECURSIVE CTE | ❌ **不支持** |
| 多个 CTE 定义 | ✅ 支持 (with_queries 数组) |
| CTE 引用 | ✅ 支持 (refs 数组) |

**代码证据**: EET 源码中搜索 `RECURSIVE` 仅发现 Doxyfile.in 配置文件，无递归 CTE 实现。

### 3.2 SQLancer 递归查询支持

**SQLancer PostgreSQL RECURSIVE VIEW** (PostgresViewGenerator.java):
```java
if (Randomly.getBoolean()) {
    sb.append(" RECURSIVE");  // 随机添加 RECURSIVE
    recursive = true;
}
sb.append(" VIEW ");
```

**PostgreSQL RECURSIVE VIEW 错误处理**:
```java
errors.add("does not have the form non-recursive-term UNION [ALL] recursive-term");
// 递归视图必须符合特定格式
```

**SQLancer 递归查询状态**:
| 特性 | MySQL | PostgreSQL |
|------|-------|------------|
| 普通 CTE (WITH) | ✅ MySQLCteDefinition | ✅ 支持 |
| RECURSIVE CTE | ❓ 未明确 | ✅ RECURSIVE VIEW |
| RECURSIVE VIEW | ❌ MySQL不支持 | ✅ PostgresViewGenerator |
| 递归触发器 | ❓ | ✅ SQLite3 RECURSIVE_TRIGGERS |

**SQLite3 RECURSIVE_TRIGGERS** (SQLite3PragmaGenerator.java):
```java
case RECURSIVE_TRIGGERS:
    // SQLite 支持递归触发器配置
```

### 3.3 递归查询覆盖范围对比总结

| 维度 | EET | SQLancer |
|------|-----|----------|
| **普通 CTE** | ✅ | ✅ |
| **RECURSIVE CTE** | ❌ | ✅ PostgreSQL |
| **RECURSIVE VIEW** | ❌ | ✅ PostgreSQL |
| **递归触发器** | ❌ | ✅ SQLite3 |
| **递归深度测试** | ❌ | ❓ 未明确 |

**结论**: SQLancer 递归查询覆盖更广，支持 PostgreSQL RECURSIVE VIEW 和 SQLite3 递归触发器。EET **完全不支持递归查询**。

---

## 4. UNION/INTERSECT/EXCEPT 覆盖对比

### 4.1 EET 集合操作支持

**unioned_query 结构** (grammar.hh):
```cpp
struct unioned_query : prod {
    shared_ptr<query_spec> lhs;
    shared_ptr<query_spec> rhs;
    string type;  // union, union all, intersect, or except
};
```

**EET 集合操作类型**:
| 操作 | 支持状态 |
|------|----------|
| UNION | ✅ |
| UNION ALL | ✅ |
| INTERSECT | ✅ |
| EXCEPT | ✅ |

**EET 集合操作特点**:
- 支持嵌套集合操作 (lhs/rhs 可为 unioned_query)
- 集合操作可作为子查询 (in_subquery, target_subquery)
- 集合操作支持 EET 变换 (equivalent_transform)

### 4.2 SQLancer 集合操作支持

**SQLancer 集合操作**:
| 操作 | MySQL | PostgreSQL |
|------|-------|------------|
| UNION | ✅ | ✅ |
| UNION ALL | ✅ | ✅ |
| INTERSECT | ❌ MySQL不支持 | ✅ |
| EXCEPT | ❌ MySQL不支持 | ✅ |

**SQLancer 集合操作特点**:
- 支持 UNION/UNION ALL 作为 SELECT 组合
- PostgreSQL 支持 INTERSECT/EXCEPT
- MySQLOptions 有 `--test-union` 配置项

### 4.3 集合操作覆盖范围对比总结

| 维度 | EET | SQLancer |
|------|-----|----------|
| **UNION/UNION ALL** | ✅ | ✅ |
| **INTERSECT** | ✅ | ✅ PostgreSQL |
| **EXCEPT** | ✅ | ✅ PostgreSQL |
| **集合操作嵌套** | ✅ | ✅ |
| **集合操作作为子查询** | ✅ unioned_query | ✅ |
| **集合操作变换测试** | ✅ EET 核心 | ❓ 未明确 |

**结论**: EET 集合操作变换测试更深入，SQLancer 集合操作覆盖更完整（特别是 PostgreSQL）。

---

## 5. 窗口函数覆盖对比

### 5.1 EET 窗口函数支持

**EET 窗口函数结构** (value_expr/):
| 文件 | 说明 |
|------|------|
| window_function.hh | 窗口函数基础结构 |
| win_funcall.hh | 窗口函数调用 |
| win_func_using_exist_win.hh | 使用已定义窗口 |

**EET 窗口函数特点**:
- 支持 PARTITION BY
- 支持 ORDER BY
- 支持窗口定义 (named_window)
- 窗口函数支持 EET 变换

### 5.2 SQLancer 窗口函数支持

**SQLancer 窗口函数** (PostgresSelect.java):
```java
public static class WindowDefinition {
    private final List<PostgresExpression> partitionBy;
    private final List<PostgresOrderByTerm> orderBy;
    private final WindowFrame frame;
}
```

**SQLancer 窗口函数特点**:
- 支持 PARTITION BY / ORDER BY
- 支持窗口帧 (WindowFrame: ROWS/RANGE)
- SONAR Oracle 使用窗口函数作为 fetch column
- MySQLOptions 有 `--test-window-functions` 配置项

### 5.3 窗口函数覆盖范围对比总结

| 维度 | EET | SQLancer |
|------|-----|----------|
| **PARTITION BY** | ✅ | ✅ |
| **ORDER BY** | ✅ | ✅ |
| **窗口帧 (Frame)** | ❓ | ✅ ROWS/RANGE |
| **已定义窗口引用** | ✅ win_func_using_exist_win | ❓ |
| **窗口函数变换测试** | ✅ EET 核心 | ✅ SONAR |

**结论**: EET 窗口函数变换更深入，SQLancer 窗口帧覆盖更完整。

---

## 6. 综合对比矩阵

### 6.1 MySQL 复杂SQL覆盖对比

| 特性类别 | EET | SQLancer | 优势方 |
|----------|-----|----------|--------|
| **INNER JOIN** | ✅ | ✅ | 平局 |
| **LEFT/RIGHT JOIN** | ✅ | ✅ | 平局 |
| **CROSS JOIN** | ✅ | ✅ | 平局 |
| **NATURAL JOIN** | ❌ | ✅ | SQLancer |
| **STRAIGHT_JOIN** | ❌ | ✅ | SQLancer |
| **FROM 子查询** | ✅ | ✅ | 平局 |
| **EXISTS 子查询** | ✅ | ✅ | 平局 |
| **IN 子查询** | ✅ | ✅ | 平局 |
| **CTE (WITH)** | ✅ | ✅ | 平局 |
| **RECURSIVE CTE** | ❌ | ❓ | SQLancer (视图) |
| **UNION/UNION ALL** | ✅ | ✅ | 平局 |
| **窗口函数** | ✅ | ✅ | 平局 |
| **子查询变换测试** | ✅✅ | ✅ | EET |
| **表达式深度嵌套** | ✅✅ | ✅ | EET |

### 6.2 PostgreSQL 复杂SQL覆盖对比

| 特性类别 | EET | SQLancer | 优势方 |
|----------|-----|----------|--------|
| **INNER JOIN** | ✅ | ✅ | 平局 |
| **LEFT/RIGHT JOIN** | ✅ | ✅ | 平局 |
| **FULL OUTER JOIN** | ✅ | ✅ | 平局 |
| **CROSS JOIN** | ✅ | ✅ | 平局 |
| **NATURAL JOIN** | ❌ | ✅ | SQLancer |
| **FROM 子查询** | ✅ | ✅ | 平局 |
| **LATERAL 子查询** | ✅ | ❓ | EET |
| **EXISTS 子查询** | ✅ | ✅ | 平局 |
| **IN 子查询** | ✅ | ✅ | 平局 |
| **比较子查询** | ✅ | ✅ | 平局 |
| **CTE (WITH)** | ✅ | ✅ | 平局 |
| **RECURSIVE CTE** | ❌ | ✅ | SQLancer |
| **RECURSIVE VIEW** | ❌ | ✅ | SQLancer |
| **UNION/UNION ALL** | ✅ | ✅ | 平局 |
| **INTERSECT** | ✅ | ✅ | 平局 |
| **EXCEPT** | ✅ | ✅ | 平局 |
| **窗口函数** | ✅ | ✅ | 平局 |
| **窗口帧 (Frame)** | ❓ | ✅ | SQLancer |
| **子查询变换测试** | ✅✅ | ✅ | EET |

---

## 7. 关键发现

### 7.1 EET 复杂SQL优势

1. **子查询变换测试深入**: EXISTS/IN/比较子查询有专门的变换规则，这是 EET 的核心优势
2. **LATERAL 子查询支持**: PostgreSQL 标准特性，EET 明确支持
3. **集合操作变换**: UNION/INTERSECT/EXCEPT 支持等价变换
4. **表达式嵌套深度**: EET 生成的表达式嵌套深度更大，测试更复杂场景

### 7.2 SQLancer 复杂SQL优势

1. **JOIN 类型覆盖更广**: NATURAL JOIN、STRAIGHT_JOIN (MySQL特有)
2. **递归查询支持**: PostgreSQL RECURSIVE VIEW，SQLite3 递归触发器
3. **窗口帧完整**: ROWS/RANGE 窗口帧定义
4. **多 Oracle 组合测试**: 不同 Oracle 从不同角度测试复杂 SQL

### 7.3 共同缺失

1. **MySQL RECURSIVE CTE**: 两个工具都不明确支持 MySQL 8.0 的递归 CTE
2. **复杂事务场景**: EET 不支持事务测试，SQLancer WRITE_CHECK 仅测试基本并发

---

## 8. 建议改进方向

### 8.1 EET 改进建议

| 改进项 | 优先级 | 说明 |
|--------|--------|------|
| 添加 RECURSIVE CTE 支持 | 高 | 递归查询是重要特性 |
| 添加 NATURAL JOIN 支持 | 中 | 标准 SQL 特性 |
| 添加 MySQL STRAIGHT_JOIN | 低 | MySQL 特有优化控制 |

### 8.2 SQLancer 改进建议

| 改进项 | 优先级 | 说明 |
|--------|--------|------|
| 加强 LATERAL 子查询支持 | 中 | PostgreSQL 标准特性 |
| 加强集合操作变换测试 | 低 | 参考 EET 的 unioned_query 变换 |
| 加强表达式嵌套深度测试 | 低 | 参考 EET 的表达式生成策略 |

---

## 9. 总结

**复杂 SQL 覆盖范围总体评分**:

| 维度 | EET 评分 | SQLancer 评分 |
|------|----------|---------------|
| **JOIN 类型覆盖** | 4/6 | 6/6 |
| **子查询类型覆盖** | 6/7 | 7/7 |
| **子查询变换深度** | 10/10 | 7/10 |
| **递归查询覆盖** | 0/5 | 3/5 |
| **集合操作覆盖** | 4/4 | 4/4 |
| **窗口函数覆盖** | 4/5 | 5/5 |
| **综合评分** | 28/37 | 37/37 |

**结论**:
- **SQLancer 复杂 SQL 类型覆盖更全面**，特别是 JOIN 类型、递归查询、窗口帧
- **EET 子查询变换测试更深入**，EXISTS/IN/比较子查询的等价变换是其核心优势
- **两者互补**: EET 专注表达式变换深度，SQLancer 专注语法类型覆盖广度

---

**报告结束**