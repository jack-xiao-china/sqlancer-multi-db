# JIR Oracle 集成方案：Join Implication Reasoning for SQLancer

> 基于 SIGMOD 2026 JIR 论文，集成 JOIN 语义蕴含推理 Oracle 到 SQLancer

## Context

JIR（Join Implication Reasoning）是 SIGMOD 2026 提出的 JOIN Bug 检测方法，利用不同 JOIN 类型之间的语义蕴含关系检测 JOIN 优化器 Bug。在 11 个 DBMS 上发现 100 个唯一 Bug（69 个逻辑 Bug），核心实现仅 ~210 行代码。

当前 SQLancer 缺少 JOIN 语义测试能力——NoREC/TLP/EET 测试 WHERE/HAVING 表达式，DQP 测试查询计划差异（需 hint），但无人测试 JOIN ON 的跨类型语义一致性。JIR 填补了这一空白。

**目标**：为 MySQL + PostgreSQL 集成 JIR Oracle，实现全部 6 条推理规则。

## 架构设计

```
CLI: --oracle JIR
       │
       ├─ MySQLOracleFactory.JIR ──── JIROracle<MySQLGlobalState>
       │                                      │
       │                              MySQLJIRTransformer
       │                              5 rules (MySQL 无 FULL JOIN)
       │
       └─ PostgresOracleFactory.JIR ── JIROracle<PostgresGlobalState>
                                              │
                                      PostgresJIRTransformer
                                      6 rules (全部规则)

通用流程:
  1. 随机选表 → 2. 随机选规则 → 3. 生成 source + target 查询
  → 4. 执行 → 5. UNION ALL 组合 → 6. 结果比较 → 不一致 = Bug
```

## 6 条推理规则

| # | 规则 | Source | Target(s) | 组合方式 |
|:-:|------|--------|-----------|:--------:|
| 1 | Left Join 分解 | `LEFT JOIN` | INNER JOIN + ANTI(NOT EXISTS) | UNION ALL |
| 2 | Left/Right 对称 | `LEFT JOIN` | `RIGHT JOIN`（交换表序） | EQUAL |
| 3 | Semi/Anti 互补 | `任意 JOIN` | EXISTS + NOT EXISTS 变体 | UNION ALL |
| 4 | Full Join 分解 | `FULL JOIN` | INNER + LEFT_ANTI + RIGHT_ANTI | UNION ALL |
| 5 | Cross Join 等价 | `CROSS JOIN` | `INNER JOIN ON TRUE` | EQUAL |
| 6 | Natural Join 显式化 | `NATURAL JOIN` | `INNER JOIN ON 等值条件` | EQUAL |

---

## 文件清单

### 新建文件（7 个）

| 文件 | 行数 | 说明 |
|------|:----:|------|
| `src/sqlancer/common/oracle/jir/JIRRule.java` | ~40 | 规则枚举，含 forMySQL()/forPostgres() 过滤 |
| `src/sqlancer/common/oracle/jir/JIRResultType.java` | ~10 | 结果组合方式枚举：UNION_ALL / EQUAL |
| `src/sqlancer/common/oracle/jir/JIRQuerySet.java` | ~30 | 数据类：source + target 查询列表 + 组合方式 |
| `src/sqlancer/common/oracle/jir/JIRTransformer.java` | ~25 | DBMS 特定变换器接口 |
| `src/sqlancer/common/oracle/jir/JIROracle.java` | ~120 | 通用 Oracle：规则选择 + 执行 + 比较 |
| `src/sqlancer/mysql/oracle/MySQLJIRTransformer.java` | ~280 | MySQL 变换器（5 条规则） |
| `src/sqlancer/postgres/oracle/ext/PostgresJIRTransformer.java` | ~320 | PostgreSQL 变换器（6 条规则） |

### 修改文件（4 个）

| 文件 | 变更 |
|------|------|
| `src/sqlancer/mysql/MySQLOracleFactory.java` | 新增 `JIR` 枚举项 |
| `src/sqlancer/postgres/PostgresOracleFactory.java` | 新增 `JIR` 枚举项 |
| `docs/release_notes.md` | 新增 v2.4.0 条目 |
| `pom.xml` | 版本号 2.3.0 → 2.4.0 |

---

## 核心类设计

### 1. JIRRule 枚举

```java
public enum JIRRule {
    LEFT_JOIN_DECOMPOSITION(true, true),
    LEFT_RIGHT_SYMMETRY(true, true),
    SEMI_ANTI_COMPLEMENT(true, true),
    FULL_JOIN_DECOMPOSITION(false, true),   // MySQL 无 FULL JOIN
    CROSS_JOIN_EQUIVALENCE(true, true),
    NATURAL_JOIN_EXPLICATION(true, true);

    // forMySQL() 返回 5 条（排除 FULL）
    // forPostgres() 返回 6 条（全部）
}
```

### 2. JIRTransformer 接口

```java
public interface JIRTransformer<G extends SQLGlobalState<?, ?>> {
    /** 初始化：设置表、生成器、错误列表 */
    void initialize(G state, List<? extends AbstractTable<?, ?, ?>> tables);

    /** 对指定规则生成 source + target 查询集。无法生成时返回 null。*/
    JIRQuerySet generateQuerySet(JIRRule rule);

    /** 获取预期错误列表（用于 IgnoreMeException）*/
    ExpectedErrors getExpectedErrors();
}
```

### 3. JIROracle 通用基类

```java
public class JIROracle<G extends SQLGlobalState<?, ?>> implements TestOracle<G> {
    private final G state;
    private final JIRTransformer<G> transformer;
    private final JIRRule[] availableRules;

    @Override
    public void check() throws Exception {
        // 1. 选表（非空）
        // 2. transformer.initialize(state, tables)
        // 3. 随机选规则
        // 4. transformer.generateQuerySet(rule) → null 则 throw IgnoreMeException
        // 5. 执行 source → sourceResult
        // 6. 执行 targets → targetResult（UNION ALL 或 分别执行）
        // 7. ComparatorHelper.assumeResultSetsAreEqual()
        //    → AssertionError = Bug; IgnoreMeException = 跳过
    }
}
```

---

## 各规则变换逻辑

### 通用约定

- **左表** = FROM 列表第一张表（`tables.get(0)`）
- **右表** = JOIN 目标表（`MySQLJoin.getTable()` / `PostgresJoin.getTableReference()`）
- **SELECT 列** = 仅左表列（保证 ANTI JOIN 变体无右表时仍可用）
- **ON 条件** = 通过 ExpressionGenerator 生成布尔表达式，涵盖两表列
- **无 ORDER BY** — UNION ALL 子查询中不允许 ORDER BY

### Rule 1: Left Join Decomposition

```
Source:  SELECT L.c0 FROM L LEFT JOIN R ON cond
Target1: SELECT L.c0 FROM L INNER JOIN R ON cond
Target2: SELECT L.c0 FROM L WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond)
比较:    Source == Target1 UNION ALL Target2
```

**NOT EXISTS 构造**：
- 移除最后的 JOIN
- 构建 EXISTS 子查询：`SELECT 1 FROM R WHERE <onCondition>`
- 包装为 NOT EXISTS：使用 `MySQLUnaryPrefixOperation(NOT)` + `MySQLExists()`
- ON 条件中的列引用通过全限定名（`L.c0`, `R.c1`）工作，关联子查询自然解析

### Rule 2: Left/Right Symmetry

```
Source:  SELECT L.c0 FROM L LEFT JOIN R ON cond
Target:  SELECT L.c0 FROM R RIGHT JOIN L ON cond
比较:    Source == Target（单查询，非 UNION ALL）
```

**AST 变换**：
- FROM: `[L]` → `[R]`
- JOIN: `Join(R, cond, LEFT)` → `Join(L, cond, RIGHT)`
- SELECT 列 `L.c0` 不变（全限定名在两种查询中都有效）

### Rule 3: Semi/Anti Complement

```
Source:  SELECT L.c0 FROM L {type} JOIN R ON cond
Target1: 同上 + WHERE EXISTS (SELECT 1 FROM R WHERE cond)
Target2: 同上 + WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond)
比较:    Source == Target1 UNION ALL Target2
```

- JOIN 类型随机从 {INNER, LEFT} 选择（MySQL）；{INNER, LEFT, RIGHT, FULL}（PG）
- EXISTS/NOT EXISTS 将左表行分为"有匹配"和"无匹配"两部分

### Rule 4: Full Join Decomposition（仅 PostgreSQL）

```
Source:  SELECT L.c0 FROM L FULL OUTER JOIN R ON cond
Target1: SELECT L.c0 FROM L INNER JOIN R ON cond
Target2: SELECT L.c0 FROM L WHERE NOT EXISTS (SELECT 1 FROM R WHERE cond)
Target3: SELECT NULL FROM R WHERE NOT EXISTS (SELECT 1 FROM L WHERE cond)
比较:    Source == Target1 UNION ALL Target2 UNION ALL Target3
```

- Target3 的 NOT EXISTS **反向**：检查 R 中在 L 无匹配的行
- Target3 `SELECT NULL` — FULL JOIN 中右独行的左列值为 NULL

### Rule 5: Cross Join Equivalence

```
Source:  SELECT L.c0 FROM L CROSS JOIN R
Target:  SELECT L.c0 FROM L INNER JOIN R ON TRUE
比较:    Source == Target
```

- MySQL: `ON 1`（MySQL 中 1 = TRUE）
- PostgreSQL: `ON TRUE`

### Rule 6: Natural Join Explication

```
Source:  SELECT L.c0 FROM L NATURAL JOIN R
Target:  SELECT L.c0 FROM L INNER JOIN R ON L.x = R.x AND L.y = R.y ...
比较:    Source == Target
```

- 遍历两表列，找到同名列构建等值条件 AND 链
- 无共同列时 NATURAL ≡ CROSS，跳过该规则
- **PostgreSQL**：`PostgresJoinType` 无 NATURAL，用原始 SQL 字符串注入 `NATURAL JOIN`

---

## 关键风险与缓解

| 风险 | 等级 | 缓解 |
|------|:----:|------|
| 关联子查询列解析失败 | 高 | 列引用用全限定名 `table.col`；加入 ExpectedErrors |
| UNION ALL 类型不兼容 | 中 | 所有查询统一 SELECT 左表首列；Rule 4 Target3 用 NULL |
| PG PostgresJoin 两构造函数混淆 | 中 | 始终用第一构造函数 `(tableReference, onClause, type)` |
| PG FOR UPDATE 与 UNION ALL 不兼容 | 中 | 所有 PG SELECT 调用 `setAllowForClause(false)` |
| PG 无 NATURAL JOIN 枚举 | 中 | 用原始 SQL 字符串 `"NATURAL JOIN"` |
| 空表导致 Rule 5 失败 | 低 | `requiresAllTablesToContainRows() = true` |
| PG Visitor 随机渲染 | 低 | 不影响语义等价性，是 SQLancer 特性 |

---

## 复用现有组件

| 组件 | 文件 | 用途 |
|------|------|------|
| `ComparatorHelper.getResultSetFirstColumnAsString()` | `ComparatorHelper.java:39` | 执行查询取首列 |
| `ComparatorHelper.assumeResultSetsAreEqual()` | `ComparatorHelper.java:89` | 结果集比较（size + HashSet） |
| `MySQLJoin` / `PostgresJoin` | 各自 ast 包 | JOIN AST 节点 |
| `MySQLExpressionGenerator` / `PostgresExpressionGenerator` | 各自 gen 包 | ON 条件生成 |
| `MySQLExists` / `PostgresExists` | 各自 ast 包 | EXISTS 子查询 |
| `MySQLUnaryPrefixOperation` / `PostgresPrefixOperation` | 各自 ast 包 | NOT 运算符 |
| `MySQLToStringVisitor` / `PostgresToStringVisitor` | 各自根包 | AST→SQL |
| `ExpectedErrors` + `IgnoreMeException` | common 包 | 错误处理 |
| `TestOracleUtils.getRandomTableNonEmptyTables()` | common/oracle 包 | 选非空表 |

---

## 实施步骤

```
Phase 1: 通用 JIR 框架（~30 min）
  ├─ JIRRule.java
  ├─ JIRResultType.java
  ├─ JIRQuerySet.java
  ├─ JIRTransformer.java
  └─ JIROracle.java
       ↓ 验证: mvn compile -q
Phase 2: MySQL JIR 变换器（~3h）
  ├─ MySQLJIRTransformer.java（5 条规则）
  └─ MySQLOracleFactory.java 注册
       ↓ 验证: mvn compile -q + --oracle JIR 运行测试
Phase 3: PostgreSQL JIR 变换器（~3.5h）
  ├─ PostgresJIRTransformer.java（6 条规则）
  └─ PostgresOracleFactory.java 注册
       ↓ 验证: mvn compile -q + --oracle JIR 运行测试
Phase 4: 集成验证 + 版本更新（~30 min）
  ├─ mvn test 全量测试
  ├─ release_notes.md 更新
  └─ pom.xml 版本号 2.4.0
```

**总计**：7 新建 + 4 修改，~825 行新增

---

## 验证方法

1. **编译验证**：`mvn compile -q` 零错误零警告
2. **MySQL 运行**：
   ```bash
   java -jar sqlancer.jar --dbms mysql --oracle JIR \
     --host localhost --port 3306 \
     --username your_username --password your_password
   ```
   预期：正常执行，无异常崩溃
3. **PostgreSQL 运行**：
   ```bash
   java -jar sqlancer.jar --dbms postgres --oracle JIR \
     --host localhost --port 5432 \
     --username your_username --password your_password
   ```
   预期：正常执行，无异常崩溃
4. **全量测试**：`mvn test` 现有测试全部通过
