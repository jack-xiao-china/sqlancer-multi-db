# SQLancer vs EET 工具对比分析报告

**报告日期**: 2026-05-09
**对比范围**: MySQL、PostgreSQL 数据库
**分析维度**: 功能、语法覆盖范围、适用场景

---

## 1. 概述

### 1.1 SQLancer 简介

- **开发语言**: Java
- **代码规模**: 1370 个 Java 文件，数十万行代码
- **项目来源**: 学术项目，持续扩展
- **核心特点**: 多 Oracle 测试框架，支持 20+ 种测试方法
- **支持数据库**: MySQL, PostgreSQL, GaussDB-A/M/PG, SQLite3, TiDB, CockroachDB, DuckDB, MariaDB 等 10+ 种
- **测试方法**: NoREC, TLP (WHERE/HAVING/AGGREGATE/DISTINCT/GROUP_BY), PQS, CERT, DQP, DQE, EET, EDC, SONAR, WRITE_CHECK, FUZZER 等

### 1.2 EET 简介

- **开发语言**: C++
- **代码规模**: ~4000 行核心代码 (grammar.cc + mysql.cc + postgres.cc + value_expr/)
- **项目来源**: OSDI 2024 论文工具
- **核心特点**: 专注于单一测试方法 - 等价表达式转换 (EET)
- **支持数据库**: MySQL, PostgreSQL, SQLite, ClickHouse, TiDB, OceanBase, YugabyteDB, CockroachDB
- **测试方法**: 仅 EET (Equivalent Expression Transformation)
- **已发现 Bug**: 66 个 (MySQL 16, PostgreSQL 9, SQLite 10, ClickHouse 21, TiDB 10)

---

## 2. 核心方法论对比

### 2.1 EET (Equivalent Expression Transformation) 原理

**核心算法**:
```
1. 生成随机 SQL 查询 Q_original
2. 对查询中的表达式进行等价变换 E' = Transform(E)
3. 生成变换后的查询 Q_transformed
4. 执行 Q_original 和 Q_transformed
5. 对比结果集 - 若不同则发现逻辑 Bug
```

**变换规则**:
- CASE WHEN 条件变换
- LIKE/NOT LIKE 等价替换
- NULL 感知表达式变换
- 子查询条件重构
- 窗口函数表达式变换

**示例变换** (来自 EET README):
```sql
-- Original: select 1 row
SELECT * FROM t5 WHERE (t5.pkey >= t5.vkey) <> (t5.c30 = (...))

-- EET transformed: select 0 row (Bug!)
SELECT * FROM t5 WHERE (t5.pkey >= t5.vkey) <> (t5.c30 = (
    ... WHERE ((CASE WHEN (((ref_0.c10 LIKE 'z~%')
              AND (NOT (ref_0.c10 LIKE 'z~%')))
              AND ((ref_0.c10 LIKE 'z~%') IS NOT NULL))
          THEN t5.c28 ELSE t5.c28 END) = ...)))
```

### 2.2 SQLancer 多 Oracle 方法论

| Oracle | 原理 | 检测目标 |
|--------|------|----------|
| **NoREC** | 优化与非优化查询对比 | 优化器 Bug |
| **TLP_WHERE** | WHERE 三元逻辑分区 | WHERE 逻辑 Bug |
| **TLP_HAVING** | HAVING 三元逻辑分区 | HAVING 逻辑 Bug |
| **TLP_AGGREGATE** | 聚合函数分区 | 聚合逻辑 Bug |
| **PQS** | Pivot 行验证 | 过滤条件 Bug |
| **CERT** | 基数估计对比 | 性能规划 Bug |
| **DQP** | 不同计划对比 | 计划选择 Bug |
| **DQE** | SELECT/UPDATE/DELETE 等价 | DML 逻辑 Bug |
| **EET** | 等价表达式变换 | 表达式处理 Bug |
| **EDC** | 等价数据库构建 | 约束处理 Bug |
| **SONAR** | 优化标志对比 | 优化器语义 Bug |
| **WRITE_CHECK** | 事务调度对比 | 隔离级别 Bug |
| **FUZZER** | 随机查询执行 | 崩溃/内部错误 |

---

## 3. MySQL 支持对比

### 3.1 功能覆盖

| 功能类别 | SQLancer | EET |
|----------|----------|-----|
| **测试方法数** | 17 种 Oracle | 1 种 (EET) |
| **查询类型** | SELECT, INSERT, UPDATE, DELETE, DDL | SELECT 为主 |
| **事务测试** | WRITE_CHECK (并发调度) | 不支持 |
| **约束测试** | EDC (等价数据库) | 不支持 |
| **性能测试** | CERT (基数估计) | 不支持 |
| **崩溃检测** | FUZZER + 任意 Oracle | 支持 (--ignore-crash 可关闭) |

### 3.2 MySQL 语法覆盖范围

#### EET 覆盖的语法元素

| 类别 | 支持程度 |
|------|----------|
| SELECT 基本语法 | ✅ 完整 |
| JOIN (INNER/LEFT/RIGHT) | ✅ 支持 |
| 子查询 (FROM/WHERE) | ✅ 重点 |
| CASE WHEN | ✅ 核心变换 |
| LIKE/NOT LIKE | ✅ 变换规则 |
| 窗口函数 | ✅ 部分支持 |
| GROUP BY/HAVING | ✅ 支持 |
| 聚合函数 | ✅ 基本支持 |
| 表达式嵌套 | ✅ 深度支持 |
| INSERT/UPDATE/DELETE | ❌ 不支持 |
| DDL 操作 | ❌ 不支持 |
| 事务语句 | ❌ 不支持 |
| 存储过程 | ❌ 不支持 |

#### SQLancer 覆盖的语法元素

| 类别 | 支持程度 |
|------|----------|
| SELECT 基本语法 | ✅ 完整 |
| INSERT | ✅ 完整 |
| UPDATE | ✅ 完整 |
| DELETE | ✅ 完整 |
| DDL (CREATE/ALTER/DROP) | ✅ 完整 |
| JOIN (所有类型) | ✅ 完整 (包括 NATURAL) |
| 子查询 (FROM/WHERE/EXISTS) | ✅ 完整 |
| CASE WHEN | ✅ 完整 |
| 窗口函数 | ✅ 完整 (SONAR 支持) |
| GROUP BY/HAVING | ✅ 完整 |
| 聚合函数 (23 种) | ✅ 完整 |
| JSON 函数 (10+ 种) | ✅ GaussDB-M 支持 |
| LIKE/NOT LIKE | ✅ 完整 |
| IN/EXISTS | ✅ 完整 |
| BIT 运算 | ✅ 完整 |
| 正则表达式 | ✅ 支持 |
| 空间类型 | ✅ 支持 |
| 事务 (BEGIN/COMMIT/ROLLBACK) | ✅ WRITE_CHECK |
| CTE (WITH 子句) | ✅ 支持 |
| UNION/UNION ALL | ✅ 支持 |

### 3.3 MySQL 数据类型对比

| 数据类型 | SQLancer | EET |
|----------|----------|-----|
| INT/BIGINT/SMALLINT | ✅ | ✅ |
| FLOAT/DOUBLE/DECIMAL | ✅ | ✅ |
| VARCHAR/TEXT/CHAR | ✅ | ✅ |
| DATE/TIME/DATETIME/TIMESTAMP | ✅ | ✅ |
| YEAR | ✅ | ✅ |
| BIT | ✅ | ❓ |
| BINARY/BLOB | ✅ | ❓ |
| JSON | ✅ (GaussDB-M) | ❓ |
| ENUM/SET | ✅ | ❓ |
| GEOMETRY 空间类型 | ✅ | ❌ |

### 3.4 MySQL 已发现 Bug 对比

**EET 发现的 MySQL Bug** (16 个):
| 类型 | 数量 | 示例 |
|------|------|------|
| 逻辑 Bug | 11 | 子查询 JOIN 结果不一致, FIELD() 结果不一致 |
| 崩溃 (CVE) | 5 | CVE-2023-22112, CVE-2024-21008, CVE-2024-21009 等 |

**SQLancer 发现的 MySQL Bug** (公开记录):
- NoREC/TLP 系列发现大量优化器 Bug
- PQS 发现 NULL 处理 Bug
- CERT 发现基数估计 Bug

---

## 4. PostgreSQL 支持对比

### 4.1 功能覆盖

| 功能类别 | SQLancer | EET |
|----------|----------|-----|
| **测试方法数** | 17 种 Oracle | 1 种 (EET) |
| **查询类型** | SELECT, INSERT, UPDATE, DELETE, DDL | SELECT 为主 |
| **事务测试** | WRITE_CHECK (并发调度) | 不支持 |
| **约束测试** | EDC | 不支持 |
| **崩溃检测** | FUZZER + 任意 Oracle | 支持 |

### 4.2 PostgreSQL 语法覆盖范围

#### EET 覆盖的语法元素

| 类别 | 支持程度 |
|------|----------|
| SELECT 基本语法 | ✅ 完整 |
| JOIN (INNER/LEFT/RIGHT) | ✅ 支持 |
| 子查询 (FROM/WHERE) | ✅ 重点 |
| CASE WHEN | ✅ 核心变换 |
| LIKE/NOT LIKE | ✅ 变换规则 |
| CTE (WITH) | ✅ 支持 |
| 窗口函数 | ✅ 部分支持 |
| GROUP BY/HAVING | ✅ 支持 |
| 聚合函数 | ✅ 基本支持 |

#### SQLancer 覆盖的语法元素

| 类别 | 支持程度 |
|------|----------|
| SELECT 基本语法 | ✅ 完整 |
| INSERT/UPDATE/DELETE | ✅ 完整 |
| DDL | ✅ 完整 |
| JOIN (所有类型) | ✅ 完整 |
| 子查询 | ✅ 完整 |
| CASE WHEN | ✅ 完整 |
| CTE (WITH) | ✅ 完整 |
| 窗口函数 | ✅ 完整 |
| GROUP BY/HAVING | ✅ 完整 |
| 聚合函数 | ✅ 完整 |
| JSON 类型/函数 | ✅ 完整 |
| 数组类型 | ✅ 完整 |
| Range 类型 | ✅ 支持 |
| POSIX 正则 | ✅ SONAR 支持 |
| ENUM 类型 | ✅ 支持 |
| 事务 | ✅ WRITE_CHECK |
| LATERAL JOIN | ✅ 支持 |

### 4.3 PostgreSQL 已发现 Bug 对比

**EET 发现的 PostgreSQL Bug** (9 个):
| 类型 | 数量 | 示例 |
|------|------|------|
| 逻辑 Bug | 5 | CASE WHEN 结果不一致, CTE 子查询比较 Bug |
| 崩溃/内存问题 | 3 | Segmentation fault, 内存泄漏 |
| 内部错误 | 1 | "variable not found in subplan target lists" |

**SQLancer PostgreSQL 测试覆盖**:
- 全部 16 种 Oracle 可运行
- PQS/CERT 需要表中有数据
- WRITE_CHECK 支持事务隔离测试

---

## 5. 适用场景对比

### 5.1 EET 适用场景

| 场景 | 适用性 | 说明 |
|------|--------|------|
| **表达式逻辑 Bug** | ⭐⭐⭐⭐⭐ | 核心优势，CASE WHEN、LIKE 等变换非常有效 |
| **子查询逻辑 Bug** | ⭐⭐⭐⭐⭐ | 子查询条件重构能力强 |
| **快速 Bug 发现** | ⭐⭐⭐⭐ | 单一方法，专注高效 |
| **崩溃检测** | ⭐⭐⭐⭐ | 支持，可关闭 (--ignore-crash) |
| **DML 操作测试** | ⭐ | 不支持 INSERT/UPDATE/DELETE |
| **事务隔离测试** | ❌ | 不支持 |
| **约束正确性** | ❌ | 不支持 |
| **性能回归测试** | ❌ | 不支持 |
| **优化器深度测试** | ⭐⭐ | 仅通过表达式变换间接测试 |

**最佳应用**:
- 发现表达式处理 Bug
- 发现子查询语义 Bug
- 快速验证查询逻辑正确性

### 5.2 SQLancer 适用场景

| 场景 | 适用性 | 说明 |
|------|--------|------|
| **表达式逻辑 Bug** | ⭐⭐⭐⭐ | EET Oracle 支持 |
| **优化器 Bug** | ⭐⭐⭐⭐⭐ | NoREC、SONAR、CERT 多角度测试 |
| **查询分区 Bug** | ⭐⭐⭐⭐⭐ | TLP 系列 WHERE/HAVING/AGGREGATE |
| **DML 操作测试** | ⭐⭐⭐⭐⭐ | DQE Oracle 专门测试 |
| **事务隔离测试** | ⭐⭐⭐⭐⭐ | WRITE_CHECK 新增支持 |
| **约束正确性** | ⭐⭐⭐⭐ | EDC Oracle 支持 |
| **性能回归测试** | ⭐⭐⭐⭐ | CERT 基数估计 |
| **崩溃检测** | ⭐⭐⭐⭐ | FUZZER + 所有 Oracle |
| **全面综合测试** | ⭐⭐⭐⭐⭐ | 多 Oracle 组合覆盖 |
| **新 DBMS 集成** | ⭐⭐⭐⭐⭐ | 模块化设计易于扩展 |

**最佳应用**:
- DBMS 全面功能测试
- 新版本回归测试
- 优化器深度验证
- 事务隔离级别验证
- 多角度组合测试

---

## 6. 技术实现对比

### 6.1 架构设计

| 维度 | SQLancer | EET |
|------|----------|-----|
| **语言** | Java | C++ |
| **模块化** | 高度模块化 (Oracle Factory 模式) | 单一功能模块 |
| **扩展性** | 易于添加新 Oracle/DBMS | 专注于 EET 方法 |
| **并发支持** | 多线程测试 (--num-threads) | 单进程 |
| **结果验证** | 多种验证方法 | Multiset 对比 |
| **错误处理** | ExpectedErrors 系统 | 基本错误捕获 |

### 6.2 查询生成策略

| 策略 | SQLancer | EET |
|------|----------|-----|
| **随机性** | Randomly 类 + 种子控制 | C++ random |
| **表达式深度** | 可配置 (--max-expression-depth) | 固定策略 |
| **表选择** | 随机 + 非空约束 | 随机 |
| **查询复杂度** | 多种形状 (SONAR/EET) | 固定模板 |
| **变换规则** | 多种 Oracle 规则 | 仅 EET 变换 |

### 6.3 Bug 报告格式

**EET Bug 报告**:
```
- db_setup.sql: 数据库初始化
- origin.sql: 原始查询
- eet.sql: 变换后查询
- origin.out: 原始结果
- eet.out: 变换后结果
```

**SQLancer Bug 报告**:
```
- Time/Database/Version/Seed
- Full SQL reproduction script
- DROP DATABASE + CREATE DATABASE
- CREATE TABLE + INSERT statements
- Bug-triggering query
- Oracle-specific result comparison
```

---

## 7. 性能对比

### 7.1 查询生成速度

| 工具 | 性能 | 说明 |
|------|------|------|
| **EET** | 较快 | C++ 实现，单一方法 |
| **SQLancer FUZZER** | ~3000 queries/s | 简单随机查询 |
| **SQLancer NoREC/TLP** | ~800-2700 queries/s | 中等复杂度 |
| **SQLancer EET** | ~340 queries/s | 复杂表达式变换 |
| **SQLancer WRITE_CHECK** | ~10 schedules/s | 事务调度开销大 |

### 7.2 Bug 发现效率

| 工具 | 效率评估 |
|------|----------|
| **EET** | 非常高效 - OSDI 2024 论文验证，66 个 Bug |
| **SQLancer EET Oracle** | 有效 - 继承 EET 方法 |
| **SQLancer 综合** | 高 - 多 Oracle 组合覆盖更多场景 |

---

## 8. 综合评估

### 8.1 SQLancer 优势

1. **测试方法多样**: 17 种 Oracle 覆盖不同 Bug 类型
2. **扩展性强**: 工厂模式易于添加新 Oracle
3. **DBMS 覆盖广**: 10+ 种数据库支持
4. **DML 测试完整**: INSERT/UPDATE/DELETE 全覆盖
5. **事务测试支持**: WRITE_CHECK 检测隔离级别 Bug
6. **约束测试**: EDC Oracle 测试约束正确性
7. **文档完善**: USER_GUIDE.md + USER_GUIDE_CN.md

### 8.2 EET 优势

1. **专注高效**: 单一方法深度优化
2. **学术验证**: OSDI 2024 论文，66 个真实 Bug
3. **表达式变换**: CASE WHEN/LIKE 变换规则精确
4. **C++ 性能**: 查询生成速度快
5. **Bug 报告清晰**: origin.sql + eet.sql + out 对比直观
6. **轻量级**: ~4000 行核心代码

### 8.3 互补性建议

| 建议 | 说明 |
|------|------|
| **组合使用** | EET 发现表达式 Bug，SQLancer 全面验证 |
| **SQLancer 吸收 EET** | 已实现 EET Oracle，功能融合 |
| **EET 扩展** | 可考虑添加 DML/事务测试 |
| **Bug 类型互补** | EET 专注逻辑 Bug，SQLancer 覆盖性能/约束/事务 |

---

## 9. 总结

### 9.1 核心差异

| 维度 | SQLancer | EET |
|------|----------|-----|
| **定位** | 全功能 DBMS 测试框架 | 专注表达式逻辑 Bug |
| **测试方法** | 17 种 Oracle | 1 种 (EET) |
| **覆盖范围** | SELECT/DML/DDL/事务 | SELECT 表达式 |
| **扩展性** | 高度可扩展 | 专注单一目标 |
| **适用场景** | 全面回归测试 | 快速表达式 Bug 发现 |
| **学术来源** | 多论文方法集成 | OSDI 2024 单篇 |

### 9.2 选择建议

**选择 EET**:
- 专注于表达式逻辑 Bug
- 需要快速发现 CASE WHEN/LIKE/子查询 Bug
- 希望单一高效工具

**选择 SQLancer**:
- 需要全面功能测试
- 需要事务隔离测试
- 需要多角度验证 (优化器、约束、DML)
- 需要扩展到新 DBMS

**组合使用**:
- EET 作为表达式 Bug 专项检测
- SQLancer 作为综合回归测试框架
- 发挥各自优势，互补覆盖

---

## 附录 A: EET MySQL Bug 列表 (16 个)

| ID | 类型 | 状态 | CVE |
|----|------|------|-----|
| 1 | 逻辑 Bug (子查询 JOIN) | Confirmed | - |
| 2 | 崩溃 (SEGV) | Fixed | CVE-2023-22112 |
| 3 | 崩溃 (SEGV) | Fixed | - |
| 4 | 崩溃 (SEGV) | Fixed | CVE-2024-21008 |
| 5 | 逻辑 Bug (SPACE()) | Confirmed | - |
| 6 | 崩溃 (SEGV) | Fixed | CVE-2024-21009 |
| 7 | 崩溃 (SEGV) | Fixed | CVE-2024-20982 |
| 8 | 崩溃 (SEGV) | Fixed | CVE-2024-21013 |
| 9 | 逻辑 Bug (FIELD()) | Confirmed | - |
| 10 | 逻辑 Bug (子查询) | Confirmed | - |
| 11 | 逻辑 Bug (窗口函数) | Fixed | - |
| 12 | 逻辑 Bug (UNIX_TIMESTAMP) | Confirmed | - |
| 13 | 逻辑 Bug (REPEAT) | Confirmed | - |
| 14 | 逻辑 Bug (EXISTS) | Confirmed | - |
| 15 | 逻辑 Bug (HEX/REVERSE/RPAD) | Confirmed | - |
| 16 | 逻辑 Bug (NULLIF) | Confirmed | - |

## 附录 B: EET PostgreSQL Bug 列表 (9 个)

| ID | 类型 | 状态 |
|----|------|------|
| 1 | 逻辑 Bug (CASE WHEN) | Fixed |
| 2 | 崩溃 (连接断开) | Fixed |
| 3 | 逻辑 Bug (CTE 子查询) | Fixed |
| 4 | 逻辑 Bug (CASE WHEN 对比) | Fixed |
| 5 | 内存泄漏 | Confirmed |
| 6 | Segmentation fault | Fixed |
| 7 | 内存消耗过大 | Fixed |
| 8 | 内部错误 (no relation entry) | Fixed |
| 9 | 内部错误 (subplan target lists) | Fixed |

## 附录 C: SQLancer MySQL Oracle 列表 (17 个)

1. TLP_WHERE
2. HAVING
3. GROUP_BY
4. AGGREGATE
5. DISTINCT
6. NOREC
7. QUERY_PARTITIONING
8. PQS
9. CERT
10. DQP
11. DQE
12. EET
13. CODDTEST
14. EDC
15. SONAR
16. WRITE_CHECK
17. FUZZER

## 附录 D: SQLancer PostgreSQL Oracle 列表 (17 个)

1. NOREC
2. PQS
3. TLP_WHERE
4. HAVING
5. AGGREGATE
6. DISTINCT
7. GROUP_BY
8. QUERY_PARTITIONING
9. CERT
10. DQP
11. DQE
12. EET
13. CODDTEST
14. EDC
15. SONAR
16. WRITE_CHECK
17. FUZZER

---

**报告结束**

ARG: Testing Query Rewriters via Abstract Rule Guided Fuzzing