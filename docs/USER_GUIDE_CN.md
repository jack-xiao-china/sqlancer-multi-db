# SQLancer 中文用户指南

**版本**: v0.1.84 (2026-04-30)  
**支持的数据库**: MySQL, PostgreSQL, GaussDB-A, GaussDB-PG, GaussDB-M 等 20+ 种数据库管理系统

## 简介

SQLancer 是一款通过生成随机 SQL 查询来测试数据库管理系统并发现 Bug 的工具。本扩展版本包含：

- **16 种测试 Oracle**：支持 MySQL、PostgreSQL 和 GaussDB（包括 EDC、Sonar、EET、DQE、DQP、CODDTEST）
- **GaussDB 全面支持**：A 兼容模式（Oracle 风格）、PG 兼容模式、M 兼容模式（MySQL 风格）
- **扩展数据类型**：JSON、BLOB、时间类型、数组、枚举、空间类型
- **国际化支持**：处理非英文服务器错误消息
- **单 Jar 包封装**：所有依赖打包，直接运行 `java -jar`

---

## 快速开始

### 构建项目

```bash
# 克隆项目
git clone https://github.com/jack-xiao-china/sqlancer-multi-db.git
cd sqlancer-multi-db

# 构建（lib/ 目录包含 openGauss JDBC 驱动）
mvn clean package -DskipTests

# 输出：约 387MB 的单 Jar 包
ls -lh target/sqlancer-2.0.0.jar
```

### 运行测试

**推荐方式**（所有依赖已包含在 Jar 包中）：
```bash
java -jar target/sqlancer-2.0.0.jar mysql --oracle QUERY_PARTITIONING
java -jar target/sqlancer-2.0.0.jar gaussdb-m --host xxx --port xxx --username xxx --password xxx
```

**备选方式**（使用外部 classpath）：
```bash
# Linux/macOS
java -cp "target/sqlancer-2.0.0.jar:target/lib/*" sqlancer.Main mysql --oracle NOREC

# Windows
java -cp "target/sqlancer-2.0.0.jar;target/lib/*" sqlancer.Main mysql --oracle NOREC
```

---

## 支持的数据库和测试 Oracle

### Oracle 快速参考表

| 数据库 | 可用的测试 Oracle |
|--------|-------------------|
| **MySQL** | TLP_WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, EET, CODDTEST, EDC, SONAR, FUZZER |
| **PostgreSQL** | NOREC, PQS, TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, QUERY_PARTITIONING, CERT, DQP, DQE, EET, CODDTEST, EDC, SONAR, FUZZER |
| **GaussDB-A** | TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, EET, FUZZER |
| **GaussDB-PG** | TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, EET, FUZZER |
| **GaussDB-M** | TLP_WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, EET, CODDTEST, EDC, SONAR, FUZZER |
| SQLite3 | NoREC, WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, QUERY_PARTITIONING, PQS, CODDTEST, FUZZER |
| TiDB | WHERE, HAVING, QUERY_PARTITIONING, CERT, DQP |
| CockroachDB | NOREC, WHERE, HAVING, AGGREGATE, GROUP_BY, DISTINCT, QUERY_PARTITIONING, CERT |
| DuckDB | NOREC, WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, QUERY_PARTITIONING |
| MariaDB | NOREC, DQP |

---

## 系统要求

### 环境要求
- Java 17 或更高版本
- 数据库服务器（MySQL 8.0+, PostgreSQL 12-18, GaussDB）
- 数据库服务器的网络访问权限

### 数据库准备
1. 确保数据库服务器正在运行且可访问
2. 创建具有足够权限的用户（CREATE/DROP DATABASE/SCHEMA）
3. 测试数据库/模式将自动创建和删除

---

# MySQL 测试

## 基本用法

```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 3306 \
    --username root \
    --password your_password \
    mysql --oracle QUERY_PARTITIONING
```

## MySQL Oracle 说明

| Oracle | 测试内容 | 性能 |
|--------|----------|------|
| NOREC | 查询优化器正确性 | ~3000 查询/秒 |
| TLP_WHERE | WHERE 子句分区测试 | ~2700 查询/秒 |
| HAVING | HAVING 子句分区测试 | 中等 |
| AGGREGATE | 聚合函数正确性 | 中等 |
| DISTINCT | DISTINCT 处理正确性 | 中等 |
| GROUP_BY | GROUP BY 表达式正确性 | 中等 |
| QUERY_PARTITIONING | 组合测试（WHERE+HAVING+AGGREGATE+DISTINCT+NOREC） | 默认 |
| PQS | Pivot 查询合成 | 中等 |
| CERT | 基数估计准确性 | 中等 |
| DQP | 差异化查询计划 | ~2000 查询/秒 |
| DQE | SELECT/UPDATE/DELETE 等价性 | ~23 查询/秒 |
| EET | 等价表达式转换 | ~340 查询/秒 |
| CODDTEST | 常量驱动的优化测试 | 中等 |
| EDC | 等价数据库构建（约束相关测试） | 中等 |
| SONAR | 优化与非优化查询对比 | 中等 |
| FUZZER | 随机查询生成 | ~3000 查询/秒 |

## MySQL 支持的数据类型

| 类型分类 | 支持的数据类型 |
|----------|----------------|
| 数值类型 | INT, BIGINT, SMALLINT, TINYINT, FLOAT, DOUBLE, DECIMAL, BIT |
| 字符串类型 | VARCHAR, TEXT, TINYTEXT, MEDIUMTEXT, LONGTEXT, CHAR |
| 二进制类型 | BINARY, VARBINARY, BLOB, TINYBLOB, MEDIUMBLOB, LONGBLOB |
| 时间类型 | DATE, TIME, DATETIME, TIMESTAMP, YEAR |
| JSON 类型 | JSON（含 JSON_TYPE, JSON_VALID 函数） |
| 枚举/集合 | ENUM, SET |
| 空间类型 | GEOMETRY, POINT, LINESTRING, POLYGON |

## MySQL 功能开关

### 约束测试
| 选项 | 默认值 | 说明 |
|------|--------|------|
| `--test-foreign-keys` | true | 测试外键约束 |
| `--test-check-constraints` | true | 允许 CHECK 约束 |
| `--test-generated-columns` | true | 测试生成列 |
| `--test-primary-keys` | true | 允许主键 |
| `--test-unique-constraints` | true | 允许唯一约束 |

### 查询功能
| 选项 | 默认值 | 说明 |
|------|--------|------|
| `--test-joins` | true | 允许 JOIN 子句 |
| `--test-in-operator` | true | 允许 IN 操作符 |
| `--test-window-functions` | true | 允许窗口函数 |
| `--test-common-table-expressions` | true | 允许 WITH 子句（CTE） |
| `--test-union` | true | 允许 UNION 语句 |
| `--test-group-sets` | true | 允许 GROUPING SETS, ROLLUP, CUBE |

### 存储引擎
```bash
mysql --engines InnoDB,MyISAM,MEMORY
```

---

# PostgreSQL 测试

## 基本用法

```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 5432 \
    --username postgres \
    --password your_password \
    postgres --oracle QUERY_PARTITIONING
```

## PostgreSQL Oracle 说明

| Oracle | 测试内容 | 性能 |
|--------|----------|------|
| NOREC | 查询优化器正确性 | ~3000 查询/秒 |
| TLP_WHERE | WHERE 子句分区测试 | ~800 查询/秒 |
| HAVING | HAVING 子句分区测试 | 中等 |
| AGGREGATE | 聚合函数正确性 | 中等 |
| DISTINCT | DISTINCT 处理正确性 | 中等 |
| GROUP_BY | GROUP BY 表达式正确性 | 中等 |
| QUERY_PARTITIONING | 组合测试（TLP_WHERE+HAVING+AGGREGATE） | 默认 |
| PQS | Pivot 查询合成 | 中等 |
| CERT | 基数估计准确性 | 中等 |
| DQP | 差异化查询计划 | 中等 |
| DQE | SELECT/UPDATE/DELETE 等价性 | 中等 |
| EET | 等价表达式转换 | 中等 |
| CODDTEST | 常量驱动的优化测试 | 中等 |
| EDC | 等价数据库构建（约束相关测试） | 中等 |
| SONAR | 优化与非优化查询对比 | 中等 |
| FUZZER | 随机查询生成 | ~3000 查询/秒 |

### PQS 和 CERT 推荐参数

PQS 和 CERT Oracle 需要表中包含数据才能正确测试。推荐使用以下参数：

```bash
java -jar target/sqlancer-2.0.0.jar \
    --username postgres \
    --password your_password \
    --num-threads 20 \
    --timeout-seconds 30 \
    --num-tries 20 \
    postgres --pg-tables 1 \
    --pg-generate-sql-num 10 \
    --pg-generate-rows-per-insert 5 \
    --oracle PQS
```

**PQS/CERT 推荐设置：**

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| `--pg-tables` | 1 | 单表确保数据被插入 |
| `--pg-generate-sql-num` | 10 | 每表更多 INSERT 语句 |
| `--pg-generate-rows-per-insert` | 5 | 每个 INSERT 多行 |
| `--num-threads` | 20 | 并行测试 |
| `--timeout-seconds` | 30 | 每线程超时时间 |

**注意**：使用 `--pg-tables 1` 很重要，因为 PQS/CERT 需要非空表。多表可能导致临时表被 DELETE/TRUNCATE 清空。

## PostgreSQL 支持的数据类型

| 类型分类 | 支持的数据类型 |
|----------|----------------|
| 数值类型 | INT, BIGINT, SMALLINT, SERIAL, BIGSERIAL, FLOAT, DOUBLE, DECIMAL |
| 字符串类型 | VARCHAR, CHAR, TEXT |
| 时间类型 | DATE, TIME, TIMETZ, TIMESTAMP, TIMESTAMPTZ |
| JSON 类型 | JSON, JSONB |
| 二进制类型 | BYTEA |
| UUID 类型 | UUID |
| 数组类型 | ARRAY 类型（INT[], VARCHAR[] 等） |
| 枚举类型 | ENUM 类型 |
| 空间类型 | GEOMETRY, POINT（需 PostGIS 扩展） |

## PostgreSQL 特定选项

```bash
--coverage-policy BALANCED          # CONSERVATIVE|BALANCED|AGGRESSIVE
--test-foreign-keys true            # 在变异前准备外键组
--pg-table-columns 10               # CREATE TABLE 列数
--pg-generate-sql-num 3             # INSERT ... VALUES 行数
--pg-index-model 0                  # 0-6 对应不同索引模式
--extensions "pg_trgm,postgis"      # 预创建扩展
```

---

# GaussDB-A 测试（A 兼容模式 / Oracle 风格）

## 简介

GaussDB-A 支持 Oracle 风格的 SQL 语法和数据类型。使用内置 openGauss JDBC 驱动，支持 SM3 认证。

**⚠️ 重要：`--target-database` 是必需参数**

必须指定一个 A 兼容数据库。工具不会默认连接到 `postgres`。

## 前置准备

**步骤 1：创建 A 兼容数据库（必需）**
```sql
-- 以管理员身份连接 GaussDB
CREATE DATABASE gaussdb_a_test WITH dbcompatibility 'A';

-- 验证兼容模式
SELECT datcompatibility FROM pg_database WHERE datname = 'gaussdb_a_test';
-- 应返回：A
```

**步骤 2：授权**
```sql
-- 授予 CREATE/DROP SCHEMA 权限给测试用户
GRANT ALL PRIVILEGES ON DATABASE gaussdb_a_test TO root;
```

## 基本用法

```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 8000 \
    --username root --password password \
    gaussdb-a --target-database gaussdb_a_test --oracle QUERY_PARTITIONING
```

## 错误处理

**未指定 `--target-database` 时：**
```
Exception: The following option is required: [--target-database]

Usage: gaussdb-a [options]
  * --target-database    要连接的 A 兼容数据库（必需）
```

**数据库不是 A 兼容时：**
```
[WARN] Database 'xxx' compatibility mode is not 'A'
[WARN] 创建 A 兼容数据库: CREATE DATABASE xxx WITH dbcompatibility 'A'
```

## 测试隔离策略

GaussDB-A 使用 **模式隔离**（而非数据库隔离）：
- 连接到指定的 A 兼容数据库
- 创建模式：`database0`, `database1`, `database2`...
- 设置 `search_path` 到测试模式
- 表在模式内创建以实现隔离

这遵循 Oracle 的模式隔离模式。

## GaussDB-A Oracle 说明

| Oracle | 说明 |
|--------|------|
| TLP_WHERE | TLP WHERE 子句分区测试 |
| HAVING | TLP HAVING 子句分区测试 |
| AGGREGATE | TLP 聚合函数分区测试 |
| DISTINCT | TLP DISTINCT 分区测试 |
| GROUP_BY | TLP GROUP BY 分区测试 |
| NOREC | NoREC 优化检测 |
| QUERY_PARTITIONING | 组合 Oracle（默认） |
| PQS | Pivot 查询合成 |
| CERT | 基数估计测试 |
| DQP | 差异化查询计划 |
| DQE | SELECT/UPDATE/DELETE 等价性 |
| EET | 等价表达式转换 |
| FUZZER | 随机查询生成 |

## GaussDB-A 支持的数据类型

| 类型分类 | 支持的数据类型 |
|----------|----------------|
| 数值类型 | NUMBER, INTEGER, BIGINT, FLOAT, DOUBLE |
| 字符串类型 | VARCHAR2, CHAR, CLOB（可选） |
| 时间类型 | DATE, TIMESTAMP |
| 二进制类型 | BLOB（可选，使用 `--enable-clob-blob` 启用） |
| 布尔类型 | BOOLEAN |

## GaussDB-A 选项

| 选项 | 是否必需 | 说明 |
|------|----------|------|
| `--target-database` | **是** | A 兼容数据库名称（必须已存在） |
| `--enable-clob-blob` | 否 | 启用 CLOB/BLOB 类型生成 |

---

# GaussDB-PG 测试（PG 兼容模式）

## 简介

GaussDB-PG 支持 PostgreSQL 风格的 SQL 语法。使用内置 openGauss JDBC 驱动。

## 前置准备

**步骤 1：创建 PG 兼容数据库（必需）**
```sql
CREATE DATABASE gaussdb_pg_test WITH dbcompatibility 'pg';

-- 验证
SELECT datcompatibility FROM pg_database WHERE datname = 'gaussdb_pg_test';
-- 应返回：pg
```

## 基本用法

```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 8000 \
    --username root --password password \
    gaussdb-pg --target-database gaussdb_pg_test --oracle QUERY_PARTITIONING
```

## 测试隔离策略

与 GaussDB-A 相同：在指定数据库内使用 **模式隔离**。
- 创建模式：`database0`, `database1`...
- 设置 `search_path` 实现隔离

## GaussDB-PG Oracle 说明

与 GaussDB-A 相同的 Oracle 集，使用 PostgreSQL 兼容语法。

| Oracle | 说明 |
|--------|------|
| TLP_WHERE | TLP WHERE 子句分区测试 |
| HAVING | TLP HAVING 子句分区测试 |
| AGGREGATE | TLP 聚合函数分区测试 |
| DISTINCT | TLP DISTINCT 分区测试 |
| GROUP_BY | TLP GROUP BY 分区测试 |
| NOREC | NoREC 优化检测 |
| QUERY_PARTITIONING | 组合 Oracle（默认） |
| PQS | Pivot 查询合成 |
| CERT | 基数估计测试 |
| DQP | 差异化查询计划 |
| DQE | SELECT/UPDATE/DELETE 等价性 |
| EET | 等价表达式转换 |
| FUZZER | 随机查询生成 |

## GaussDB-PG 支持的数据类型

| 类型分类 | 支持的数据类型 |
|----------|----------------|
| 数值类型 | INT, BIGINT, SMALLINT, FLOAT, DOUBLE, DECIMAL |
| 字符串类型 | VARCHAR, CHAR, TEXT |
| 时间类型 | DATE, TIME, TIMESTAMP（可选，使用 `--enable-time-types` 启用） |
| 布尔类型 | BOOLEAN |

---

# GaussDB-M 测试（M 兼容模式 / MySQL 风格）

## 简介

GaussDB-M 支持 MySQL 风格的 SQL 语法。使用内置 openGauss JDBC 驱动，自动创建 M 兼容测试数据库。

## 基本用法

```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 19995 \
    --username root \
    --password password \
    gaussdb-m --oracle QUERY_PARTITIONING
```

**注意**：GaussDB-M 自动创建 M 兼容测试数据库。无需 `--target-database` 参数。

## 测试隔离策略

GaussDB-M 使用 **数据库隔离**：
- 首先连接到 `postgres`
- 创建数据库：`database0`, `database1`... 并设置 `DBCOMPATIBILITY 'M'`
- 切换到测试数据库进行测试

这遵循 MySQL 的数据库隔离模式。

## GaussDB-M Oracle 说明

| Oracle | 说明 |
|--------|------|
| TLP_WHERE | TLP WHERE 子句分区测试 |
| HAVING | TLP HAVING 子句分区测试 |
| GROUP_BY | TLP GROUP BY 分区测试 |
| AGGREGATE | TLP 聚合函数分区测试 |
| DISTINCT | TLP DISTINCT 分区测试 |
| NOREC | NoREC 优化检测 |
| QUERY_PARTITIONING | 组合 Oracle（默认） |
| PQS | Pivot 查询合成 |
| CERT | 基数估计测试 |
| DQP | 差异化查询计划 |
| DQE | SELECT/UPDATE/DELETE 等价性 |
| EET | 等价表达式转换 |
| CODDTEST | 常量驱动的优化测试 |
| EDC | 等价数据库构建（约束相关测试） |
| SONAR | 优化与非优化查询对比 |
| FUZZER | 随机查询生成 |

## GaussDB 兼容模式对比

| 模式 | 数据库语法 | 隔离方法 | `--target-database` |
|------|------------|----------|---------------------|
| **GaussDB-A** | Oracle 风格 | 模式隔离 | **必需** |
| **GaussDB-PG** | PostgreSQL 风格 | 模式隔离 | **必需** |
| **GaussDB-M** | MySQL 风格 | 数据库隔离 | 可选（自动创建） |

---

# Oracle 深度解析与选择指南

## Oracle 算法对比表

| Oracle | 核心算法 | 解决的问题 | 适用场景 | 参考论文 |
|--------|----------|------------|----------|----------|
| **PQS** | Pivot 查询合成 - 随机选择一行作为 Pivot，生成应该包含该行的查询 | 查询执行中的逻辑 Bug（缺失行、错误过滤） | 通用正确性测试；对 WHERE 子句 Bug 特别有效 | [OSDI 2020](https://arxiv.org/pdf/2001.04174.pdf) |
| **NoREC** | 非优化参考引擎 - 比较优化查询结果与非优化（布尔表达式）结果 | 优化器 Bug，即优化改变了查询语义 | 测试 WHERE 子句优化；谓词下推 Bug | [FSE 2020](https://arxiv.org/abs/2007.08292) |
| **TLP** (WHERE/HAVING/AGGREGATE/DISTINCT/GROUP_BY) | 三元逻辑分区 - 将查询分为 3 个分区（TRUE/FALSE/NULL）并验证总和等于原结果 | 查询各组件中的逻辑 Bug | WHERE 子句、HAVING 子句、聚合函数、DISTINCT、GROUP BY | [OOPSLA 2020](https://www.manuelrigger.at/preprints/TLP.pdf) |
| **CERT** | 基数估计测试 - 检测性能问题，比较估计基数与实际基数 | 基数估计不准确导致的性能 Bug | 查询规划；索引使用决策；连接顺序选择 | [ICSE 2024](https://bajinsheng.github.io/assets/pdf/cert_icse24.pdf) |
| **DQP** | 差异化查询计划 - 比较不同 DBMS 或不同配置下的查询计划 | 通过计划差异揭示的逻辑 Bug | 跨 DBMS 对比；优化器计划选择 Bug | [SIGMOD 2024](https://bajinsheng.github.io/assets/pdf/dqp_sigmod24.pdf) |
| **DQE** | 差异化查询等价性 - 比较相同数据集上的 SELECT、UPDATE、DELETE 结果 | DML 正确性 Bug，修改操作与查询不匹配 | UPDATE/DELETE 正确性；事务隔离 Bug | 内部扩展 |
| **EET** | 等价表达式转换 - 测试语义等价的表达式 | 表达式求值 Bug（如 `a AND b` vs `NOT(NOT a OR NOT b)`） | 布尔表达式处理；NULL 逻辑；表达式简化 Bug | 内部扩展 |
| **CODDTEST** | 常量驱动的优化测试 - 使用字面常量触发特定优化 | 常量表达式的优化器行为 | 常量折叠；参数化查询优化 | 内部扩展 |
| **EDC** | 等价数据库构建 - 创建无约束的原始数据库，比较查询结果 | 约束相关的优化器 Bug（约束被忽略或误处理） | 外键、CHECK 约束、UNIQUE 约束测试；约束感知优化 | [OSDI 2020] (PQS 扩展) |
| **SONAR** | 优化与非优化对比 - 使用标志位过滤比较执行路径 | 优化改变了语义的优化器 Bug | 索引使用 Bug；谓词求值顺序 Bug；优化正确性 | 内部扩展（Sonar 论文待发布） |
| **FUZZER** | 随机查询生成 - 生成语法有效但语义随机的查询 | 崩溃 Bug、语法边界情况、意外错误 | 压力测试；崩溃检测；语法边界测试 | 基本模糊测试方法 |

---

## EDC (等价数据库构建) Oracle

### 概念

EDC 通过比较两种数据库状态下的查询结果来检测优化器 Bug：

1. **原始数据库**：包含带约束的表（外键、CHECK、UNIQUE、NOT NULL）
2. **原始数据库**：相同数据但**无约束**

### 算法流程

- 生成引用约束列的随机查询
- 在两种数据库状态下执行查询
- 结果应该相同，因为约束不改变查询语义
- 如果不同 → 优化器 Bug（约束错误影响了执行）

### 可发现的 Bug 示例

优化器使用约束信息跳过某些检查，但这种优化改变了查询结果。

### 使用方法

```bash
java -jar sqlancer.jar mysql --oracle EDC --num-tries 100
java -jar sqlancer.jar postgres --oracle EDC --num-tries 100
java -jar sqlancer.jar gaussdb-m --oracle EDC --num-tries 100
```

---

## SONAR Oracle

### 概念

SONAR 通过使用标志位过滤比较优化与非优化查询执行来检测优化器 Bug。

### 算法流程

- 生成查询并在启用优化器的情况下执行（正常模式）
- 在禁用优化器的情况下执行相同查询（强制非优化路径）
- 比较结果 - 应该相同
- 如果不同 → 优化器 Bug，优化改变了语义

### 可发现的 Bug 示例

基于索引的优化错误过滤了行，或谓词下推改变了 WHERE 子句语义。

### 使用方法

```bash
java -jar sqlancer.jar mysql --oracle SONAR --num-tries 100
java -jar sqlancer.jar postgres --oracle SONAR --num-tries 100
java -jar sqlancer.jar gaussdb-m --oracle SONAR --num-tries 100
```

---

## TLP (三元逻辑分区)

### 概念

TLP 使用蜕变测试将查询结果分为三个逻辑类别。

### 算法流程

- 原始查询：`SELECT * FROM t WHERE cond` → 结果集 R
- 分区查询：
  - `SELECT * FROM t WHERE cond IS TRUE` → R_true
  - `SELECT * FROM t WHERE cond IS FALSE` → R_false
  - `SELECT * FROM t WHERE cond IS NULL` → R_null
- 验证：R = R_true ∪ R_false ∪ R_null（无重叠）

### 变体说明

| 变体 | 应用场景 |
|------|----------|
| TLP_WHERE | WHERE 子句正确性 |
| TLP_HAVING | HAVING 子句正确性 |
| TLP_AGGREGATE | 聚合函数正确性 |
| TLP_DISTINCT | DISTINCT 处理 |
| TLP_GROUP_BY | GROUP BY 表达式正确性 |

---

## NoREC (非优化参考引擎)

### 概念

NoREC 通过比较优化与非优化执行路径来检测优化 Bug。

### 算法流程

- 优化查询：`SELECT COUNT(*) FROM t WHERE complex_expr` → count1
- 非优化查询：`SELECT SUM(CASE WHEN complex_expr THEN 1 ELSE 0 END) FROM t` → count2
- count1 应等于 count2
- 差异表示优化器错误处理了 complex_expr

### 最适用于

谓词下推 Bug、索引使用 Bug、表达式简化 Bug。

---

## PQS (Pivot 查询合成)

### 概念

PQS 选择一个 "pivot" 行并生成应该明确返回该行的查询。

### 算法流程

- 随机选择一行作为 pivot 行
- 生成明确匹配 pivot 行的 WHERE 条件
- 执行查询，验证 pivot 行在结果集中
- 如果缺失 → 逻辑 Bug（行应被包含但被过滤掉）

### 最适用于

WHERE 子句逻辑 Bug、NULL 处理 Bug、类型转换 Bug。

---

## Oracle 选择决策表

根据测试目标选择合适的 Oracle：

| 测试目标 | 主要 Oracle | 辅助 Oracle |
|----------|-------------|-------------|
| **新 DBMS 集成验证** | QUERY_PARTITIONING | PQS, NoREC |
| **优化器回归测试** | EDC, SONAR | NoREC, DQP |
| **性能回归测试** | CERT | NoREC |
| **约束正确性测试** | EDC | DQE |
| **表达式处理测试** | EET | TLP_WHERE |
| **DML 操作测试** | DQE | QUERY_PARTITIONING |
| **跨版本测试** | DQP | EDC |
| **压力测试** | FUZZER | NoREC |
| **快速 Bug 发现** | TLP_WHERE + NoREC | PQS |
| **全面综合测试** | QUERY_PARTITIONING + EDC + EET + DQE | 所有其他 Oracle |

---

# 全局选项

## 通用参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--num-threads N` | 并发测试线程数 | 1 |
| `--num-tries N` | 测试迭代次数 | 1 |
| `--timeout-seconds N` | 每次迭代最大运行时间 | 120 |
| `--seed N` | 随机种子（用于重现） | 时间基准 |
| `--log-dir <dir>` | 日志目录基准 | logs/<dbms>/ |
| `--log-each-select` | 记录每条 SQL 语句 | true |
| `--use-reducer` | 启用 delta 调试缩减 | false |
| `--qpg-enable` | 启用查询计划引导 | false |

---

# 测试场景示例

## 快速 Bug 发现

```bash
# MySQL
java -jar sqlancer.jar mysql --oracle NOREC,TLP_WHERE --num-tries 50 --timeout-seconds 60

# PostgreSQL
java -jar sqlancer.jar postgres --oracle NOREC,TLP_WHERE --num-tries 50 --timeout-seconds 60

# GaussDB-PG（需要 target-database）
java -jar sqlancer.jar gaussdb-pg --target-database gaussdb_pg_test \
    --oracle TLP_WHERE --num-tries 50 --timeout-seconds 60

# GaussDB-A（需要 target-database）
java -jar sqlancer.jar gaussdb-a --target-database gaussdb_a_test \
    --oracle TLP_WHERE --num-tries 50 --timeout-seconds 60

# GaussDB-M（自动创建数据库）
java -jar sqlancer.jar gaussdb-m --oracle TLP_WHERE --num-tries 50 --timeout-seconds 60
```

## 全面综合测试

```bash
# MySQL 全 Oracle
java -jar sqlancer.jar mysql \
    --oracle QUERY_PARTITIONING,DQE,DQP,EET,EDC,SONAR \
    --num-tries 100 --timeout-seconds 300

# PostgreSQL 高覆盖率
java -jar sqlancer.jar postgres \
    --oracle QUERY_PARTITIONING,DQE,DQP,EET,EDC,SONAR \
    --coverage-policy AGGRESSIVE \
    --pg-table-columns 12 \
    --pg-generate-sql-num 5

# GaussDB-A 全面测试
java -jar sqlancer.jar gaussdb-a \
    --target-database gaussdb_a_test \
    --oracle QUERY_PARTITIONING,DQP,DQE,EET \
    --num-tries 100 --timeout-seconds 300

# GaussDB-PG 全面测试
java -jar sqlancer.jar gaussdb-pg \
    --target-database gaussdb_pg_test \
    --enable-time-types \
    --oracle QUERY_PARTITIONING,DQP,DQE,EET \
    --num-tries 100 --timeout-seconds 300

# GaussDB-M 全面测试
java -jar sqlancer.jar gaussdb-m \
    --oracle QUERY_PARTITIONING,DQP,DQE,EET,EDC,SONAR \
    --num-tries 100 --timeout-seconds 300
```

## Bug 重现

```bash
java -jar sqlancer.jar mysql \
    --seed 1776222510722 \
    --oracle EET \
    --num-tries 1

# GaussDB-A 重现
java -jar sqlancer.jar gaussdb-a \
    --target-database gaussdb_a_test \
    --seed 1776222510722 \
    --oracle EET \
    --num-tries 1
```

---

# 性能与稳定性

## 已验证性能（v0.1.73）

| 数据库 | Oracle | 查询/秒 | 成功率 |
|--------|--------|---------|--------|
| MySQL | QUERY_PARTITIONING | ~2700 | 95% |
| MySQL | DQP | ~2000 | 99% |
| MySQL | DQE | ~23 | 96% |
| MySQL | EET | ~340 | 62% |
| PostgreSQL | QUERY_PARTITIONING | ~700 | 53% |
| GaussDB-PG | QUERY_PARTITIONING | ~3500 | 53% |
| GaussDB-A | TLP_WHERE | ~4000 | 62% |

## 多线程测试

```bash
--num-threads 4 --num-tries 10
```

---

# 故障排查

## 连接问题

| 错误 | 解决方案 |
|------|----------|
| Connection refused | 验证主机/端口，检查 DBMS 是否运行 |
| Access denied | 创建具有适当权限的用户 |
| SSL/TLS error | 在连接 URL 添加 `sslmode=disable` |

## GaussDB 特定问题

| 错误 | 解决方案 |
|------|----------|
| `--target-database is required` | 创建 A/PG 兼容数据库并使用 `--target-database` 指定 |
| "datcompatibility" field not found | 连接的是 PostgreSQL 数据库，需要 GaussDB 实例 |
| Compatibility mode is not 'A' or 'pg' | 重新创建数据库：`CREATE DATABASE xxx WITH dbcompatibility 'A'` |
| Session initialization failed | 检查网络连接和凭据 |
| Cannot create schema | 授予用户 CREATE SCHEMA 权限 |

## 创建兼容数据库

```sql
-- A 兼容（Oracle 风格）
CREATE DATABASE gaussdb_a_test WITH dbcompatibility 'A';

-- PG 兼容（PostgreSQL 风格）
CREATE DATABASE gaussdb_pg_test WITH dbcompatibility 'pg';

-- M 兼容（MySQL 风格） - SQLancer 自动创建
-- CREATE DATABASE gaussdb_m_test WITH dbcompatibility 'B';
```

## 内存问题

```bash
java -Xmx4g -jar sqlancer.jar ...
```

---

# 最佳实践

1. **从默认设置开始** - 经过充分测试且稳定
2. **使用 QUERY_PARTITIONING** - 获得全面覆盖
3. **保存种子** - 用于重现有趣行为和 Bug
4. **监控日志** - 在 `logs/<dbms>/<oracle>_YYYY_MMDD_HHMM/` 目录
5. **重现测试** - 在报告 Bug 前验证
6. **使用适当的超时** - EET/DQE 需要更多时间

---

# 获取帮助

1. 检查 `logs/<dbms>/` 目录中的日志文件
2. 使用 `--seed` 重现问题
3. 参考 README.md 快速入门
4. 查看 docs/ 目录详细文档

---

# 版本历史

## v0.1.84 (2026-04-30)
- **新增文档**：全面的 Oracle 参考指南
  - Oracle 算法对比表：核心算法、解决的问题、适用场景、参考论文
  - EDC Oracle 深度解析：等价数据库构建概念和用法
  - SONAR Oracle 深度解析：优化与非优化查询对比
  - Oracle 选择指南：针对特定测试目标如何选择 Oracle
- **Oracle 支持更新**：EDC 和 SONAR 添加到所有支持的数据库表
  - MySQL：16 个 Oracle（新增 EDC、SONAR）
  - PostgreSQL：16 个 Oracle（新增 EDC、SONAR）
  - GaussDB-M：16 个 Oracle（新增 EDC、SONAR）

## v0.1.83 (2026-04-25)
- **GaussDB-A/PG**: `--target-database` 现为必需参数
- **错误消息**: 参数缺失时提供清晰指导
- **文档**: 更新前置准备步骤
- **模式隔离**: 明确 A/PG 使用模式隔离，M 使用数据库隔离

## v0.1.82 (2026-04-25)
- **打包**: 所有依赖打包到单个 Jar（约 387MB）
- **驱动**: 统一 openGauss JDBC 驱动（支持 SM3 认证）
- **lib 目录**: 本地驱动 Jar，无需 Maven 下载
- **简化**: 直接使用 `java -jar` 运行

## v0.1.73 (2026-04-21)
- 集成验证：MySQL、PostgreSQL、GaussDB-PG、GaussDB-A 全部稳定
- 修复 MySQL ALTER TABLE 在视图上的问题
- 修复 MySQL DQE Oracle rowId 列名问题
- 修复 MySQL JSON 列索引预期错误
- 验证所有数据库可持续运行并发现逻辑 Bug