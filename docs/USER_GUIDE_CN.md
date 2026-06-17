# SQLancer 中文用户指南

**版本**: v2.7.2 (2026-06-17)  
**支持的数据库**: MySQL, PostgreSQL, GaussDB-A, GaussDB-M

## 简介

SQLancer 是一款通过生成随机 SQL 查询来测试数据库管理系统并发现 Bug 的工具。本扩展版本包含：

- **25+ 种测试 Oracle**：支持 MySQL、PostgreSQL 和 GaussDB（包括 JIR、EDC_RADAR、Sonar、EET、DQE、DQP、CODDTEST、QPG、FUCCI、TX_INFER）
- **JIR Oracle**：Join Implication Reasoning — 基于 6 条语义规则检测 JOIN 优化器 Bug（SIGMOD 2026 论文）
- **GaussDB 全面支持**：A 兼容模式（Oracle 风格）、PG 兼容模式、M 兼容模式（MySQL 风格）
- **QPG Oracle**：查询计划引导测试，支持 MySQL、PostgreSQL、GaussDB-M、GaussDB-A
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

# 输出：约 4MB 的轻量 Jar + target/lib/ 依赖目录
ls -lh target/sqlancer-2.7.2.jar
```

### 运行测试

CLI 格式：**全局选项在 DBMS 命令之前，DBMS 选项在命令之后**。

**推荐方式**（所有依赖通过 manifest Class-Path 加载）：
```bash
# MySQL
java -jar target/sqlancer-2.7.2.jar \
    --host localhost --port 3306 --username root --password your_password \
    mysql --oracle QUERY_PARTITIONING

# PostgreSQL
java -jar target/sqlancer-2.7.2.jar \
    --host localhost --port 5432 --username postgres --password your_password \
    postgres --oracle TLP_WHERE

# GaussDB-M（必须指定 --target-database）
java -jar target/sqlancer-2.7.2.jar \
    --host your_host --port 19995 --username your_user --password your_password \
    gaussdb-m --target-database testm --oracle QUERY_PARTITIONING

# GaussDB-A（必须指定 --target-database）
java -jar target/sqlancer-2.7.2.jar \
    --host your_host --port 8000 --username your_user --password your_password \
    gaussdb-a --target-database testa --oracle QUERY_PARTITIONING
```

**备选方式**（使用外部 classpath）：
```bash
# Linux/macOS
java -cp "target/sqlancer-2.5.6.jar:target/lib/*" sqlancer.Main mysql --oracle NOREC

# Windows
java -cp "target/sqlancer-2.5.6.jar;target/lib/*" sqlancer.Main mysql --oracle NOREC
```

---

## 支持的数据库和测试 Oracle

### Oracle 快速参考表

| 数据库 | 可用的测试 Oracle |
|--------|-------------------|
| **MySQL** | TLP_WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, EET, EET_UPDATE, EET_DELETE, EET_INSERT_SELECT, CODDTEST, EDC_RADAR, EDC_DATA, SONAR, WRITE_CHECK, WRITE_CHECK_REPRODUCE, FUCCI, TX_INFER, JIR, FUZZER |
| **PostgreSQL** | NOREC, PQS, TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, QUERY_PARTITIONING, CERT, DQP, DQE, EET, EET_UPDATE, EET_DELETE, EET_INSERT_SELECT, CODDTEST, EDC_RADAR, EDC_DATA, SONAR, WRITE_CHECK, WRITE_CHECK_REPRODUCE, FUCCI, TX_INFER, JIR, FUZZER |
| **GaussDB-A** | TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, EET, EET_UPDATE, EET_DELETE, EET_INSERT_SELECT, CODDTEST, EDC_RADAR, EDC_DATA, SONAR, WRITE_CHECK, WRITE_CHECK_REPRODUCE, TX_INFER, FUCCI, JIR, FUZZER |
| **GaussDB-PG** | TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, NOREC, QUERY_PARTITIONING, PQS, CERT*, DQP*, DQE*, FUZZER |
| **GaussDB-M** | TLP_WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, EET, EET_UPDATE, EET_DELETE, EET_INSERT_SELECT, CODDTEST, EDC_RADAR, EDC_DATA, SONAR, WRITE_CHECK, WRITE_CHECK_REPRODUCE, FUCCI, TX_INFER, JIR, FUZZER |
| SQLite3 | NoREC, WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, QUERY_PARTITIONING, PQS, CODDTEST, FUZZER |
| TiDB | WHERE, HAVING, QUERY_PARTITIONING, CERT, DQP |
| CockroachDB | NOREC, WHERE, HAVING, AGGREGATE, GROUP_BY, DISTINCT, QUERY_PARTITIONING, CERT, WRITE_CHECK |
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
3. GaussDB-A 和 GaussDB-M 需要**预先创建兼容数据库**（详见各数据库章节）

---

# MySQL 测试

## 基本用法

```bash
java -jar target/sqlancer-2.7.2.jar \
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
| EET_UPDATE | 等价表达式 UPDATE 变体 | 中等 |
| EET_DELETE | 等价表达式 DELETE 变体 | 中等 |
| EET_INSERT_SELECT | 等价表达式 INSERT...SELECT 变体 | 中等 |
| CODDTEST | 常量驱动的优化测试 | 中等 |
| EDC_RADAR | 等价数据库构建（约束相关测试） | 中等 |
| SONAR | 优化与非优化查询对比 | 中等 |
| WRITE_CHECK | 事务隔离级别正确性 | ~10 调度/秒 |
| WRITE_CHECK_REPRODUCE | 事务隔离 Bug 重现 | 中等 |
| FUCCI | MVCC 测试（DT/MT/CS） | 中等 |
| TX_INFER | MVCC 版本推断 | 中等 |
| JIR | JOIN 语义等价推理（5 规则） | 中等 |
| FUZZER | 随机查询生成 | ~3000 查询/秒 |
| EDC_DATA | 数据操作等价（函数、聚合、谓词） | ~3000 查询/秒 |

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
java -jar target/sqlancer-2.7.2.jar \
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
| EET_UPDATE | 等价表达式 UPDATE 变体 | 中等 |
| EET_DELETE | 等价表达式 DELETE 变体 | 中等 |
| EET_INSERT_SELECT | 等价表达式 INSERT...SELECT 变体 | 中等 |
| CODDTEST | 常量驱动的优化测试 | 中等 |
| EDC_RADAR | 等价数据库构建（约束相关测试） | 中等 |
| SONAR | 优化与非优化查询对比 | 中等 |
| WRITE_CHECK | 事务隔离级别正确性 | ~10 调度/秒 |
| WRITE_CHECK_REPRODUCE | 事务隔离 Bug 重现 | 中等 |
| FUCCI | MVCC 测试（DT/MT/CS） | 中等 |
| TX_INFER | MVCC 版本推断 | 中等 |
| JIR | JOIN 语义等价推理（6 规则） | 中等 |
| FUZZER | 随机查询生成 | ~3000 查询/秒 |
| EDC_DATA | 数据操作等价（函数、聚合、谓词） | 中等 |

### PQS 和 CERT 推荐参数

PQS 和 CERT Oracle 需要表中包含数据才能正确测试。推荐使用以下参数：

```bash
java -jar target/sqlancer-2.7.2.jar \
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
| 时间类型 | DATE, TIME, TIMETZ, TIMESTAMP, TIMESTAMPTZ, INTERVAL |
| JSON 类型 | JSON, JSONB |
| 二进制类型 | BYTEA |
| UUID 类型 | UUID |
| 数组类型 | ARRAY 类型（INT[], VARCHAR[] 等） |
| 枚举类型 | ENUM 类型 |
| 空间类型 | GEOMETRY, POINT（需 PostGIS 扩展） |

## PostgreSQL DDL/DML 覆盖增强

- DDL：`DROP TABLE`、`DROP VIEW`、`DROP SEQUENCE`、`ALTER SEQUENCE`、`ALTER INDEX`
- 对象：composite `CREATE TYPE`、简单 SQL `CREATE FUNCTION`、`CREATE RULE`
- 索引：`BTREE/HASH/GIST/GIN/SPGIST/BRIN`（6 种 access method）
- DML：PostgreSQL 15+ `MERGE`
- COPY：低频 `COPY ... TO STDOUT` 覆盖探针
- 暂未启用：触发器与权限管理语句（如 `GRANT/REVOKE`）

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

GaussDB-A 支持 Oracle 风格的 SQL 语法和数据类型。使用内置 openGauss JDBC 驆动，支持 SM3 认证。

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
GRANT ALL PRIVILEGES ON DATABASE gaussdb_a_test TO your_user;
```

## 基本用法

```bash
java -jar target/sqlancer-2.7.2.jar \
    --host localhost \
    --port 8000 \
    --username root --password your_password \
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
| EET_UPDATE | 等价表达式 UPDATE 变体 |
| EET_DELETE | 等价表达式 DELETE 变体 |
| EET_INSERT_SELECT | 等价表达式 INSERT...SELECT 变体 |
| WRITE_CHECK | 事务隔离级别正确性测试 |
| WRITE_CHECK_REPRODUCE | 事务隔离 Bug 重现 |
| TX_INFER | MVCC 版本推断 |
| FUCCI | MVCC 测试（DT/MT/CS） |
| JIR | JOIN 语义等价推理（6 规则） |
| CODDTEST | 常量驱动的优化测试（3 模式：EXPRESSION/SUBQUERY/RANDOM） |
| FUZZER | 随机查询生成 |
| EDC_DATA | 数据操作等价（函数、聚合、谓词） |

## GaussDB-A 支持的数据类型

| 类型分类 | 支持的数据类型 |
|----------|----------------|
| 数值类型 | NUMBER, NUMERIC, INTEGER, BIGINT, SMALLINT, TINYINT, FLOAT, REAL, DOUBLE PRECISION, DECIMAL |
| 字符串类型 | VARCHAR2, CHAR, TEXT, CLOB |
| 时间类型 | DATE, TIME, TIMESTAMP |
| 二进制类型 | RAW, BLOB |
| 布尔类型 | BOOLEAN |
| 其他类型 | JSON |

## GaussDB-A 选项

| 选项 | 是否必需 | 说明 |
|------|----------|------|
| `--target-database` | **是** | A 兼容数据库名称（必须已存在） |
| `--enable-clob-blob` | 否 | 启用 CLOB/BLOB 类型生成 |
| `--coddtest-model` | 否 | CODDTEST 模式：EXPRESSION、SUBQUERY 或 RANDOM（默认：RANDOM） |

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

**步骤 2：授权**
```sql
GRANT ALL PRIVILEGES ON DATABASE gaussdb_pg_test TO your_user;
```

## 基本用法

```bash
java -jar target/sqlancer-2.7.2.jar \
    --host localhost \
    --port 8000 \
    --username root --password your_password \
    gaussdb-pg --target-database gaussdb_pg_test --oracle QUERY_PARTITIONING
```

## 测试隔离策略

与 GaussDB-A 相同：在指定数据库内使用 **模式隔离**。
- 创建模式：`database0`, `database1`...
- 设置 `search_path` 实现隔离

## GaussDB-PG Oracle 说明

GaussDB-PG 有较小的 Oracle 集（11 个）。CERT、DQP、DQE 目前为占位符，实际回退到 TLP_WHERE。

| Oracle | 说明 |
|--------|------|
| TLP_WHERE | TLP WHERE 子句分区测试 |
| HAVING | TLP HAVING 子句分区测试 |
| AGGREGATE | TLP 聚合函数分区测试 |
| DISTINCT | TLP DISTINCT 分区测试 |
| GROUP_BY | TLP GROUP BY 分区测试 |
| NOREC | NoREC 优化检测 |
| QUERY_PARTITIONING | 组合测试 TLP_WHERE+HAVING+AGGREGATE（默认） |
| PQS | Pivot 查询合成 |
| CERT | 基数估计测试（⚠ 占位符 → TLP_WHERE） |
| DQP | 差异化查询计划（⚠ 占位符 → TLP_WHERE） |
| DQE | SELECT/UPDATE/DELETE 等价性（⚠ 占位符 → TLP_WHERE） |
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

GaussDB-M 支持 MySQL 风格的 SQL 语法。使用内置 openGauss JDBC 驱动。

**⚠️ 重要：`--target-database` 是必需参数**

必须指定一个 M 兼容数据库。工具不会自动创建数据库。

## 前置准备

**步骤 1：创建 M 兼容数据库（必需）**
```sql
-- 以管理员身份连接 GaussDB
CREATE DATABASE gaussdb_m_test WITH DBCOMPATIBILITY 'M';

-- 验证兼容模式
SELECT datcompatibility FROM pg_database WHERE datname = 'gaussdb_m_test';
-- 应返回：M
```

**步骤 2：授权**
```sql
-- 授予 CREATE/DROP SCHEMA 权限给测试用户
GRANT ALL PRIVILEGES ON DATABASE gaussdb_m_test TO your_user;
```

## 基本用法

```bash
java -jar target/sqlancer-2.7.2.jar \
    --host localhost \
    --port 19995 \
    --username root --password your_password \
    gaussdb-m --target-database gaussdb_m_test --oracle QUERY_PARTITIONING
```

## 错误处理

**未指定 `--target-database` 时：**
```
ERROR: --target-database is REQUIRED for GaussDB-M testing.

Please create an M-compatible database first:
  CREATE DATABASE <db_name> WITH DBCOMPATIBILITY 'M';

Then specify it with:
  java -jar sqlancer.jar gaussdb-m --target-database <db_name> ...
```

## 测试隔离策略

GaussDB-M 使用 **模式隔离**（与 GaussDB-A 一致）：
- 连接到指定的 M 兼容数据库
- 创建模式：`database0`, `database1`, `database2`...
- 设置 `search_path` 到测试模式
- 表在模式内创建以实现隔离

这种方式比自动创建数据库更可靠，可避免 CREATE DATABASE 权限问题和 DBCOMPATIBILITY 语法差异。

**注意**：M 兼容模式不支持 `DROP SCHEMA ... CASCADE`，工具使用不带 CASCADE 的 `DROP SCHEMA`。

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
| EET_UPDATE | 等价表达式 UPDATE 变体 |
| EET_DELETE | 等价表达式 DELETE 变体 |
| EET_INSERT_SELECT | 等价表达式 INSERT...SELECT 变体 |
| CODDTEST | 常量驱动的优化测试 |
| EDC_RADAR | 等价数据库构建（约束相关测试） |
| SONAR | 优化与非优化查询对比 |
| WRITE_CHECK | 事务隔离级别正确性测试 |
| WRITE_CHECK_REPRODUCE | 事务隔离 Bug 重现 |
| FUCCI | MVCC 测试（DT/MT/CS） |
| TX_INFER | MVCC 版本推断 |
| JIR | JOIN 语义等价推理（6 规则） |
| FUZZER | 随机查询生成 |
| EDC_DATA | 数据操作等价（函数、聚合、谓词） |

## GaussDB-M 完整示例

```bash
java -jar target/sqlancer-2.7.2.jar \
    --username your_user \
    --password your_password \
    --connection-url "jdbc:opengauss://your_host:19995/testm" \
    --num-tries 10 \
    --timeout-seconds 300 \
    gaussdb-m \
    --target-database testm \
    --oracle QUERY_PARTITIONING,AGGREGATE,DQP,JIR
```

## GaussDB 兼容模式对比

| 模式 | 数据库语法 | 隔离方法 | `--target-database` |
|------|------------|----------|---------------------|
| **GaussDB-A** | Oracle 风格 | 模式隔离 | **必需** |
| **GaussDB-PG** | PostgreSQL 风格 | 模式隔离 | **必需** |
| **GaussDB-M** | MySQL 风格 | 模式隔离 | **必需** |

---

# Oracle 深度解析与选择指南

## Oracle 算法对比表

| Oracle | 核心算法 | 解决的问题 | 适用场景 | 参考论文 |
|--------|----------|------------|----------|----------|
| **JIR** | Join 语义等价推理 — 比较 6 条语义规则下不同 JOIN 类型的查询结果（LEFT→INNER+NOT EXISTS、LEFT/RIGHT 对称、SEMI/ANTI 互补、FULL→INNER+anti、CROSS→INNER ON TRUE、NATURAL→INNER ON 等值） | JOIN 优化器 Bug（LEFT/RIGHT/FULL/NATURAL JOIN 结果错误、EXISTS 子查询 Bug） | JOIN 正确性测试；2 表随机选择 | [SIGMOD 2026](https://sigmod2026.org/) |
| **PQS** | Pivot 查询合成 — 随机选择一行作为 Pivot，生成应该包含该行的查询 | 查询执行中的逻辑 Bug（缺失行、错误过滤） | 通用正确性测试；对 WHERE 子句 Bug 特别有效 | [OSDI 2020](https://arxiv.org/pdf/2001.04174.pdf) |
| **NoREC** | 非优化参考引擎 — 比较优化查询结果与非优化（布尔表达式）结果 | 优化器 Bug，即优化改变了查询语义 | 测试 WHERE 子句优化；谓词下推 Bug | [FSE 2020](https://arxiv.org/abs/2007.08292) |
| **TLP** (WHERE/HAVING/AGGREGATE/DISTINCT/GROUP_BY) | 三元逻辑分区 — 将查询分为 3 个分区（TRUE/FALSE/NULL）并验证总和等于原结果 | 查询各组件中的逻辑 Bug | WHERE 子句、HAVING 子句、聚合函数、DISTINCT、GROUP BY | [OOPSLA 2020](https://www.manuelrigger.at/preprints/TLP.pdf) |
| **CERT** | 基数估计测试 — 检测性能问题，比较估计基数与实际基数 | 基数估计不准确导致的性能 Bug | 查询规划；索引使用决策；连接顺序选择 | [ICSE 2024](https://bajinsheng.github.io/assets/pdf/cert_icse24.pdf) |
| **DQP** | 差异化查询计划 — 比较不同 DBMS 或不同配置下的查询计划 | 通过计划差异揭示的逻辑 Bug | 跨 DBMS 对比；优化器计划选择 Bug | [SIGMOD 2024](https://bajinsheng.github.io/assets/pdf/dqp_sigmod24.pdf) |
| **DQE** | 差异化查询等价性 — 比较相同数据集上的 SELECT、UPDATE、DELETE 结果 | DML 正确性 Bug，修改操作与查询不匹配 | UPDATE/DELETE 正确性；事务隔离 Bug | 内部扩展 |
| **EET** | 等价表达式转换 — 测试语义等价的表达式 | 表达式求值 Bug（如 `a AND b` vs `NOT(NOT a OR NOT b)`） | 布尔表达式处理；NULL 逻辑；表达式简化 Bug | 内部扩展 |
| **CODDTEST** | 常量驱动的优化测试（3 模式：EXPRESSION/SUBQUERY/RANDOM） — 使用字面常量或子查询触发特定优化，比较折叠与非折叠查询结果 | 常量表达式的优化器行为；常量折叠 Bug | 常量折叠；子查询优化；参数化查询优化 | 内部扩展 |
| **EDC_RADAR** | 等价数据库构建 — 创建无约束的原始数据库，比较查询结果 | 约束相关的优化器 Bug（约束被忽略或误处理） | 外键、CHECK 约束、UNIQUE 约束测试；约束感知优化 | [ISSTA 2024] (RADAR) |
| **SONAR** | 优化与非优化对比 — 使用标志位过滤比较执行路径 | 优化改变了语义的优化器 Bug | 索引使用 Bug；谓词求值顺序 Bug；优化正确性 | 内部扩展（Sonar 论文待发布） |
| **FUZZER** | 随机查询生成 — 生成语法有效但语义随机的查询 | 崩溃 Bug、语法边界情况、意外错误 | 压力测试；崩溃检测；语法边界测试 | 基本模糊测试方法 |

---

## JIR Oracle (Join Implication Reasoning)

### 概念

JIR 通过比较不同 JOIN 类型下的查询结果，利用语义等价规则检测 JOIN 优化器 Bug。基于 SIGMOD 2026 论文 "Detecting Join Bugs in Database Engines via Join Implication Reasoning"。

### 6 条语义规则

| 规则 | 描述 | 等价关系 |
|------|------|----------|
| **1. LEFT_JOIN_DECOMPOSITION** | LEFT JOIN 可分解为 INNER + ANTI | `L LEFT JOIN R ON c` = `(L INNER JOIN R ON c) ∪ (L WHERE NOT EXISTS(...))`，右列 → NULL |
| **2. LEFT_RIGHT_SYMMETRY** | LEFT JOIN 与 RIGHT JOIN 对称 | `L LEFT JOIN R ON c` ≡ `R RIGHT JOIN L ON c` |
| **3. SEMI_ANTI_COMPLEMENT** | SEMI + ANTI 覆盖所有行 | `L ⋈ R ON c` = `(L ⋈ R WHERE EXISTS(...)) ∪ (L ⋈ R WHERE NOT EXISTS(...))` |
| **4. FULL_JOIN_DECOMPOSITION** | FULL JOIN 可分解为 INNER + 双侧 ANTI | 仅 PostgreSQL 和 GaussDB-A 支持 |
| **5. CROSS_JOIN_EQUIVALENCE** | CROSS JOIN ≡ INNER JOIN ON TRUE | `L CROSS JOIN R` ≡ `L INNER JOIN R ON TRUE` |
| **6. NATURAL_JOIN_EXPLICATION** | NATURAL JOIN ≡ INNER JOIN ON（公共列等值） | `NATURAL JOIN` ≡ `INNER JOIN ON (matching columns)` |

### 关键设计特性

- **共享 Fetch Columns**：source 和 target 查询使用同一随机选取的列（防止比较无效）
- **单列验证**：Rule 1 随机选取一列，确保 `getString(1)` + NULL 替换能精确验证
- **多重集比较**：使用 `assumeResultSetsAreEqualMultiset()` + `canonicalizeResultValue` 实现 bag 语义
- **ORDER BY 支持**：低概率为 source 添加 `ORDER BY 1` 测试不同优化器路径
- **Reproducer**：`JIRReproducer` 重放查询确认 Bug 可重现

### 支持 DBMS 和规则

| 规则 | MySQL | PostgreSQL | GaussDB-M | GaussDB-A |
|------|:-----:|:----------:|:---------:|:---------:|
| Rule 1: LEFT_JOIN_DECOMPOSITION | ✓ | ✓ | ✓ | ✓ |
| Rule 2: LEFT_RIGHT_SYMMETRY | ✓ | ✓ | ✓ | ✓ |
| Rule 3: SEMI_ANTI_COMPLEMENT | ✓ | ✓ | ✓ | ✓ |
| Rule 4: FULL_JOIN_DECOMPOSITION | ✗ | ✓ | ✗ | ✓ |
| Rule 5: CROSS_JOIN_EQUIVALENCE | ✓ | ✓ | ✓ | ✓ |
| Rule 6: NATURAL_JOIN_EXPLICATION | ✓ | ✓ | ✓ | ✓ |

### 使用方法

```bash
# MySQL JIR（5 规则）
java -jar sqlancer.jar mysql --oracle JIR --num-queries 500

# PostgreSQL JIR（6 规则，含 FULL JOIN 和 NATURAL JOIN AST）
java -jar sqlancer.jar postgres --oracle JIR --num-queries 500

# GaussDB-M JIR（5 规则，需 --target-database）
java -jar sqlancer.jar --connection-url "jdbc:opengauss://host:port/testm" \
    gaussdb-m --target-database testm --oracle JIR --num-queries 500

# GaussDB-A JIR（6 规则，需 --target-database）
java -jar sqlancer.jar --connection-url "jdbc:opengauss://host:port/testa" \
    gaussdb-a --target-database testa --oracle JIR --num-queries 500
```

### 最适用于

JOIN 优化器 Bug、LEFT/RIGHT/FULL/NATURAL JOIN 结果错误、EXISTS 子查询 Bug。

---

## EDC_RADAR (等价数据库构建) Oracle

### 概念

EDC_RADAR 通过比较两种数据库状态下的查询结果来检测优化器 Bug：

1. **原始数据库**：包含带约束的表（外键、CHECK、UNIQUE、NOT NULL）
2. **无约束数据库**：相同数据但**无约束**

### 算法流程

- 生成引用约束列的随机查询
- 在两种数据库状态下执行查询
- 结果应该相同，因为约束不改变查询语义
- 如果不同 → 优化器 Bug（约束错误影响了执行）

### 可发现的 Bug 示例

优化器使用约束信息跳过某些检查，但这种优化改变了查询结果。

### 使用方法

```bash
java -jar sqlancer.jar mysql --oracle EDC_RADAR --num-tries 100
java -jar sqlancer.jar postgres --oracle EDC_RADAR --num-tries 100
java -jar sqlancer.jar gaussdb-m --target-database testm --oracle EDC_RADAR --num-tries 100
```

---

## EDC_DATA (数据操作等价) Oracle

### 概念

EDC_DATA 通过比较函数/聚合/谓词表达式与预计算值来检测数据操作实现 Bug。基于 SIGMOD 2026 EDC 论文方法论。

### 算法流程

- 创建包含多类型列的测试表（由 `type.txt` 种子文件驱动）
- 插入种子数据行
- 生成测试表达式：`FUNC(c0, c1, ...)` 使用所有测试列（由 `func.txt`、`agg.txt`、`pred.txt` 驱动）
- 通过在种子数据上预计算得到预期结果
- 执行 SELECT 查询
- 比较实际结果与预期 — 差异表示数据操作 Bug

### 种子文件（按 DBMS 独立，位于 `src/main/resources/edc-data-seeds/{dbms}/`）

| 文件 | 内容 |
|------|------|
| `func.txt` | 标量函数（ABS, CONCAT, SUBSTR, ...） |
| `agg.txt` | 聚合函数（AVG, SUM, COUNT, ...） |
| `pred.txt` | 谓词（=, <, >, LIKE, BETWEEN, ...） |
| `type.txt` | 列数据类型（INT, VARCHAR, DATE, ...） |

### 与 EDC_RADAR 的区别

EDC_RADAR 检测约束相关的优化器 Bug；EDC_DATA 检测数据操作（函数/聚合/谓词）实现 Bug。

### 支持数据库

MySQL、PostgreSQL、GaussDB-M、GaussDB-A

### 使用方法

```bash
java -jar sqlancer.jar mysql --oracle EDC_DATA --num-tries 200
java -jar sqlancer.jar postgres --oracle EDC_DATA --num-tries 200
java -jar sqlancer.jar gaussdb-m --target-database testm --oracle EDC_DATA --num-tries 200
java -jar sqlancer.jar gaussdb-a --target-database testa --oracle EDC_DATA --num-tries 200
```

### 最适用于

函数实现 Bug、聚合计算错误、谓词求值 Bug、类型转换错误。

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
java -jar sqlancer.jar gaussdb-m --target-database testm --oracle SONAR --num-tries 100
```

---

## WRITE_CHECK Oracle

### 概念

WRITE_CHECK 是事务级别的测试 Oracle，用于检测数据库事务隔离级别实现中的 Bug。

### 算法流程

- 生成多个并发事务，包含 INSERT/UPDATE/DELETE 操作
- 生成随机事务调度（语句交错执行顺序）
- 使用指定隔离级别执行调度（SERIALIZABLE、REPEATABLE_READ、READ_COMMITTED）
- 重放相同调度并比较最终数据库状态
- 如果状态不同 → 隔离级别实现 Bug

### 关键特性

- 测试并发事务正确性
- 检测幻读、不可重复读、脏读
- 验证 COMMIT/ROLLBACK 语义
- 随机调度生成测试不同交错场景

### 可发现的 Bug 示例

GaussDB-M SERIALIZABLE 隔离级别 Bug - 并发事务导致数据重复（预期 8 行，实际执行后 16 行）。

### 使用方法

```bash
# MySQL WRITE_CHECK
java -jar sqlancer.jar mysql --oracle WRITE_CHECK --num-tries 50

# PostgreSQL WRITE_CHECK
java -jar sqlancer.jar postgres --oracle WRITE_CHECK --num-tries 50

# GaussDB-M WRITE_CHECK（需 --target-database）
java -jar sqlancer.jar --username your_user --password your_password --host your_host --port 19995 \
    gaussdb-m --target-database testm --oracle WRITE_CHECK --num-threads=1 --num-tries=10

# GaussDB-A WRITE_CHECK（需 --target-database）
java -jar sqlancer.jar --username your_user --password your_password --host your_host --port 8000 \
    gaussdb-a --target-database testa --oracle WRITE_CHECK --num-tries=10
```

### 支持的隔离级别

| 数据库 | 支持的隔离级别 |
|--------|----------------|
| MySQL | READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE |
| PostgreSQL | READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE |
| GaussDB-M | READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE |
| GaussDB-A | READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE |
| CockroachDB | SERIALIZABLE |

### 事务选项（仅 CockroachDB）

| 选项 | 默认值 | 说明 |
|------|--------|------|
| `--use-fixed-num-transaction` | false | 使用固定事务数量 |
| `--num-transaction` | 2 | 生成的事务数量 |
| `--num-schedule` | 10 | 测试的调度数量 |

### WRITE_CHECK_REPRODUCE（Bug 重现模式）

用于重现已发现的 WRITE_CHECK Bug。使用 `--oracle WRITE_CHECK_REPRODUCE` 重放特定调度。

### 最佳应用场景

隔离级别 Bug、并发 Bug、事务回滚 Bug、幻读检测。

---

## QPG Oracle (Query Plan Guidance)

### 概念

QPG (Query Plan Guidance) 是一种基于查询计划的测试引导方法，通过探索未见过的查询计划类型来提高测试覆盖率。

### 算法流程

- 生成随机数据库状态（表、索引、数据）
- 使用 EXPLAIN 获取查询计划
- 解析查询计划提取操作类型（Seq Scan、Index Scan、Hash Join 等）
- 使用强化学习奖励机制引导生成探索新的查询计划
- 遇到新的查询计划类型 → 增加测试权重

### 支持的数据库

| 数据库 | EXPLAIN 格式 | 解析方式 |
|--------|--------------|----------|
| MySQL | `EXPLAIN FORMAT=JSON` | 解析 query_block.access_type |
| PostgreSQL | `EXPLAIN (FORMAT JSON)` | BFS 遍历 Node Type |
| GaussDB-M | `EXPLAIN (FORMAT JSON)` | PostgreSQL 风格 JSON 解析 |
| GaussDB-A | `EXPLAIN (FORMAT JSON)` | PostgreSQL 风格 JSON 解析 |

### 使用方法

```bash
# MySQL QPG 测试
java -jar sqlancer.jar mysql --qpg-enable true --oracle NOREC --num-queries 100

# PostgreSQL QPG 测试
java -jar sqlancer.jar postgres --qpg-enable true --oracle NOREC --num-queries 100

# GaussDB-M QPG 测试（需 --target-database）
java -jar sqlancer.jar --username your_user --password your_password --host your_host --port your_port \
    gaussdb-m --target-database testm --qpg-enable true --oracle NOREC --num-queries 100

# GaussDB-A QPG 测试（需 --target-database）
java -jar sqlancer.jar --username your_user --password your_password --host your_host --port your_port \
    gaussdb-a --target-database testa \
    --qpg-enable true --oracle NOREC --num-queries 100
```

### QPG Actions

QPG 通过执行以下 Actions 来修改数据库状态，引导生成不同的查询计划：

| Action | 说明 |
|--------|------|
| INSERT | 插入数据行 |
| UPDATE | 更新数据行 |
| DELETE | 删除数据行 |
| CREATE_INDEX | 创建索引（引导使用 Index Scan） |
| DROP_INDEX | 删除索引（引导使用 Seq Scan） |
| ALTER_TABLE | 修改表结构 |
| ANALYZE_TABLE | 分析表统计信息 |
| TRUNCATE | 清空表 |

### 最佳应用场景

- 优化器测试：引导生成不同查询计划
- 索引测试：通过创建/删除索引测试不同访问路径
- 综合测试：提高查询计划覆盖率

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
- 优化查询：`SELECT SUM(CASE WHEN complex_expr THEN 1 ELSE 0 END) FROM t` → count2
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

## EET 变体（DML 操作）

EET 有三个变体，将等价表达式变换应用于 DML 语句而非 SELECT 查询：

**EET_UPDATE**: 测试 UPDATE 使用等价 WHERE predicates 时是否产生相同的行修改。
**EET_DELETE**: 测试 DELETE 使用等价 WHERE predicates 时是否删除相同的行。
**EET_INSERT_SELECT**: 测试 INSERT...SELECT 使用等价表达式时是否插入相同的数据。

**算法**（所有变体共享）：
- 在 predicate/选择列表中生成包含表达式 E 的 DML 语句
- 应用 EET 变换生成等价表达式 E'
- 在数据库的相同副本上执行两个 DML 语句
- 比较结果表状态 — 差异表示表达式求值 Bug

**支持数据库**：MySQL、PostgreSQL、GaussDB-A、GaussDB-M

**使用示例**：
```bash
java -jar sqlancer.jar mysql --oracle EET_UPDATE --num-queries 50
java -jar sqlancer.jar postgres --oracle EET_DELETE --num-queries 50
java -jar sqlancer.jar gaussdb-a --target-database testa --oracle EET_INSERT_SELECT --num-queries 50
java -jar sqlancer.jar gaussdb-m --target-database testm --oracle EET_UPDATE --num-queries 50
```

**最适用于**：DML 特定表达式 Bug、UPDATE/DELETE predicate 中的优化器 Bug、INSERT...SELECT 求值 Bug。

---

## TX_INFER（MVCC 版本推断）

**概念**：TX_INFER 使用辅助版本跟踪表来推断特定隔离级别下的预期事务结果，检测 MVCC（多版本并发控制）实现 Bug。

**算法**：
- 创建版本跟踪表，记录行修改
- 在特定隔离级别下执行并发事务
- 使用版本数据计算每个事务的预期结果
- 将实际 DBMS 结果与推断预期进行比较
- 差异表示隔离级别或 MVCC 实现 Bug

**支持数据库**：MySQL、PostgreSQL、GaussDB-A、GaussDB-M

**使用示例**：
```bash
java -jar sqlancer.jar mysql --oracle TX_INFER --num-queries 100
java -jar sqlancer.jar postgres --oracle TX_INFER --num-queries 100
java -jar sqlancer.jar gaussdb-m --target-database testm --oracle TX_INFER --num-queries 100
java -jar sqlancer.jar gaussdb-a --target-database testa --oracle TX_INFER --num-queries 100
```

**最适用于**：MVCC Bug、隔离级别违反、并发事务异常。

---

## FUCCI（MVCC 测试）

**概念**：FUCCI 结合三种 MVCC 测试方法 — 差分测试（DT）、蜕变测试（MT）和约束求解（CS） — 检测并发和隔离 Bug。

**算法**（取决于变体）：
- **DT**：比较相同调度在不同隔离级别下的结果
- **MT**：比较等价但重排序的调度结果
- **CS**：使用约束求解验证调度属性
- **ALL**：运行所有三种变体

**支持数据库**：MySQL、PostgreSQL、GaussDB-A、GaussDB-M

**选项**：
| 选项 | 值 | 说明 |
|------|-----|------|
| `--fucci-oracle-type` | DT, MT, CS, ALL | 使用哪种 FUCCI 变体 |
| `--fucci-isolation-level` | RANDOM, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE | 调度的隔离级别 |
| `--fucci-schedule-count` | 任意整数 | 每次测试的调度数量（默认: 10） |

**使用示例**：
```bash
java -jar sqlancer.jar mysql --oracle FUCCI --fucci-oracle-type DT --fucci-isolation-level SERIALIZABLE
java -jar sqlancer.jar postgres --oracle FUCCI --fucci-oracle-type ALL --fucci-schedule-count 20
java -jar sqlancer.jar gaussdb-m --target-database testm --oracle FUCCI --fucci-oracle-type DT
java -jar sqlancer.jar gaussdb-a --target-database testa --oracle FUCCI --fucci-oracle-type ALL
```

**最适用于**：隔离级别 Bug、并发异常、调度依赖 Bug。

---

## Oracle 选择决策表

根据测试目标选择合适的 Oracle：

| 测试目标 | 主要 Oracle | 辅助 Oracle |
|----------|-------------|-------------|
| **新 DBMS 集成验证** | QUERY_PARTITIONING | PQS, NoREC |
| **优化器回归测试** | EDC_RADAR, SONAR | NoREC, DQP |
| **性能回归测试** | CERT | NoREC |
| **约束正确性测试** | EDC_RADAR | DQE |
| **表达式处理测试** | EET | TLP_WHERE |
| **DML 表达式处理** | EET_UPDATE, EET_DELETE | EET, DQE |
| **DML 操作测试** | DQE | QUERY_PARTITIONING |
| **跨版本测试** | DQP | EDC_RADAR |
| **压力测试** | FUZZER | NoREC |
| **快速 Bug 发现** | TLP_WHERE + NoREC | PQS |
| **事务隔离测试** | WRITE_CHECK | DQE |
| **并发 Bug 检测** | WRITE_CHECK, FUCCI | CERT |
| **MVCC 版本追踪** | TX_INFER | WRITE_CHECK |
| **JOIN 优化器 Bug** | JIR | EDC_RADAR, DQP |
| **优化器覆盖率** | QPG | CERT, EET |
| **全面综合测试** | QUERY_PARTITIONING + JIR + EDC_RADAR + EDC_DATA + EET + DQE + WRITE_CHECK | 所有其他 Oracle |

---

# 全局选项

## 通用参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--num-threads N` | 并发测试线程数 | 16 |
| `--num-tries N` | 发现多少个错误后停止 | 100 |
| `--num-queries N` | 每个数据库发出的查询数 | 100000 |
| `--timeout-seconds N` | 每线程最大运行时间（-1 = 无超时） | -1 |
| `--random-seed N` | 随机种子（-1 = 时间基准） | -1 |
| `--max-expression-depth N` | 随机表达式最大深度 | 3 |
| `--max-num-inserts N` | INSERT 语句数量上限 | 30 |
| `--max-generated-databases N` | 每线程最大数据库数（-1 = 无限） | -1 |
| `--log-each-select` | 记录每条 SQL 语句 | true |
| `--log-execution-time` | 记录语句执行时间 | true |
| `--log-dir <dir>` | 日志目录基准 | logs/\<dbms\>/\<oracle\>_timestamp/ |
| `--print-failed` | 打印失败语句 | true |
| `--use-reducer` | 启用 delta 调试缩减 | false |
| `--reduce-ast` | 启用 AST 缩减（实验性） | false |
| `--qpg-enable` | 启用查询计划引导 | false |
| `--username` | DBMS 登录用户名 | "sqlancer" |
| `--password` | DBMS 登录密码 | "sqlancer" |
| `--host` | DBMS 主机地址 | null |
| `--port` | DBMS 端口 | -1 |
| `--connection-url` | 可选 JDBC 连接 URL | null |
| `--jdbc-driver-class` | 可选 JDBC 驱动类名 | null |
| `--validate-result-size-only` | 只验证结果集大小，跳过内容比较 | false |

---

# 测试场景示例

## 快速 Bug 发现

```bash
# MySQL
java -jar sqlancer.jar mysql --oracle NOREC,TLP_WHERE --num-tries 50 --timeout-seconds 60

# PostgreSQL
java -jar sqlancer.jar postgres --oracle NOREC,TLP_WHERE --num-tries 50 --timeout-seconds 60

# GaussDB-PG（需 target-database）
java -jar sqlancer.jar gaussdb-pg --target-database gaussdb_pg_test \
    --oracle TLP_WHERE --num-tries 50 --timeout-seconds 60

# GaussDB-A（需 target-database）
java -jar sqlancer.jar gaussdb-a --target-database testa \
    --oracle TLP_WHERE --num-tries 50 --timeout-seconds 60

# GaussDB-M（需 target-database）
java -jar sqlancer.jar gaussdb-m --target-database testm \
    --oracle TLP_WHERE --num-tries 50 --timeout-seconds 60
```

## JOIN 优化器 Bug 检测

```bash
# MySQL JIR
java -jar sqlancer.jar mysql --oracle JIR --num-tries 50 --timeout-seconds 120

# PostgreSQL JIR（6 规则，含 FULL JOIN）
java -jar sqlancer.jar postgres --oracle JIR --num-tries 50 --timeout-seconds 120

# GaussDB-M JIR（需 target-database）
java -jar sqlancer.jar gaussdb-m --target-database testm --oracle JIR --num-tries 50

# GaussDB-A JIR（6 规则，需 target-database）
java -jar sqlancer.jar gaussdb-a --target-database testa --oracle JIR --num-tries 50
```

## 全面综合测试

```bash
# MySQL 全 Oracle
java -jar sqlancer.jar mysql \
    --oracle QUERY_PARTITIONING,DQE,DQP,EET,EDC_RADAR,SONAR,JIR \
    --num-tries 100 --timeout-seconds 300

# PostgreSQL 高覆盖率
java -jar sqlancer.jar postgres \
    --oracle QUERY_PARTITIONING,DQE,DQP,EET,EDC_RADAR,SONAR,JIR \
    --coverage-policy AGGRESSIVE \
    --pg-table-columns 12 \
    --pg-generate-sql-num 5

# GaussDB-A 全面测试
java -jar sqlancer.jar gaussdb-a \
    --target-database testa \
    --oracle QUERY_PARTITIONING,DQP,DQE,EET,JIR \
    --num-tries 100 --timeout-seconds 300

# GaussDB-PG 全面测试
java -jar sqlancer.jar gaussdb-pg \
    --target-database gaussdb_pg_test \
    --enable-time-types \
    --oracle QUERY_PARTITIONING,DQP,DQE,EET \
    --num-tries 100 --timeout-seconds 300

# GaussDB-M 全面测试
java -jar sqlancer.jar gaussdb-m \
    --target-database testm \
    --oracle QUERY_PARTITIONING,DQP,DQE,EET,EDC,SONAR,JIR \
    --num-tries 100 --timeout-seconds 300
```

## Bug 重现

```bash
# MySQL
java -jar sqlancer.jar mysql --seed 1776222510722 --oracle EET --num-tries 1

# GaussDB-A（需 target-database）
java -jar sqlancer.jar gaussdb-a --target-database testa \
    --seed 1776222510722 --oracle EET --num-tries 1

# GaussDB-M（需 target-database）
java -jar sqlancer.jar gaussdb-m --target-database testm \
    --seed 1776222510722 --oracle JIR --num-tries 1
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
| `--target-database is required` | 创建 A/M/PG 兼容数据库并使用 `--target-database` 指定 |
| "datcompatibility" field not found | 连接的是 PostgreSQL 数据库，需要 GaussDB 实例 |
| Compatibility mode is not 'A'/'M'/'pg' | 重新创建数据库：`CREATE DATABASE xxx WITH dbcompatibility 'A'` 或 `'M'` 或 `'pg'` |
| Session initialization failed | 检查网络连接和凭据 |
| Cannot create schema | 授予用户 CREATE SCHEMA 权限 |
| `DROP SCHEMA ... CASCADE` 失败 | M 兼容模式不支持 CASCADE；工具已自动去掉 |

## 创建兼容数据库

```sql
-- A 兼容（Oracle 风格）
CREATE DATABASE gaussdb_a_test WITH dbcompatibility 'A';

-- M 兼容（MySQL 风格） — 必须手动创建
CREATE DATABASE gaussdb_m_test WITH DBCOMPATIBILITY 'M';

-- PG 兼容（PostgreSQL 风格）
CREATE DATABASE gaussdb_pg_test WITH dbcompatibility 'pg';
```

> ⚠️ 所有三种兼容模式都需要手动创建数据库，然后通过 `--target-database` 指定。GaussDB-M 不再自动创建数据库。

## 内存问题

```bash
java -Xmx4g -jar sqlancer.jar ...
```

---

# 最佳实践

1. **从默认设置开始** — 经过充分测试且稳定
2. **使用 QUERY_PARTITIONING** — 获得全面覆盖
3. **保存种子** — 用于重现有趣行为和 Bug
4. **监控日志** — 在 `logs/<dbms>/<oracle>_YYYY_MMDD_HHMM/` 目录
5. **重现测试** — 在报告 Bug 前验证
6. **使用适当的超时** — EET/DQE 需要更多时间
7. **GaussDB 必须指定 --target-database** — 预先创建兼容数据库

---

# 版本历史

## v2.7.2 (2026-06-17)
- **QPS 显示精度修复**：查询吞吐量格式从 `%d`（整数截断）改为 `%.1f`（一位小数）
  - 修复前：4 queries/5s = 0.8/s → `(int)0.8` = **0 queries/s**（误导）
  - 修复后：4 queries/5s = 0.8/s → **0.8 queries/s**（准确）

## v2.7.1 (2026-06-17)
- **GaussDB-A SONAR Oracle**：注册已有的 `GaussDBASonarOracle` 到 `GaussDBAOracleFactory`
  - 对比优化 vs 未优化查询执行结果，检测优化器 bug
- **GaussDB-A EDC_RADAR Oracle**：Equivalent Database Construction - RADAR
  - 新建 `GaussDBAEDCRadar.java`：在 GaussDB-A 内创建无约束 raw schema（schema 隔离，非数据库隔离）
  - 新建 `GaussDBAEDCRadarOracle.java`：对比原始 schema 与 raw schema 的查询结果
  - 使用 `information_schema.columns` 获取列元数据，`pg_catalog.pg_constraint` 获取约束元数据
  - 适配 GaussDB-A Oracle 兼容模式：双引号 `""`、NUMBER/VARCHAR2/DATE 类型映射
- **GaussDB-A Schema 查询修复**：`GaussDBASchema.fromConnection()` 移除 `public` schema 表查询
  - 原查询包含 `OR table_schema='public'` 导致 `testa` 数据库的 `public` 表被计入，使 `requiresAllTablesToContainRows=true` 的 Oracle（SONAR/PQS 等）因空表抛出 `IgnoreMeException` 而无法执行查询
- **GaussDB-A UPDATE/DELETE 生成器修复**：使用 `getRandomTableOrBailout()` 替代 `getRandomTable()` + null 检查
  - `getRandomTable(predicate)` 在过滤后列表为空时抛出 `IllegalArgumentException`（而非返回 null）
  - 导致 `generateDatabase()` 阶段 UPDATE/DELETE 生成器崩溃
- **GaussDB-A ToStringVisitor 修复**：添加 `GaussDBAPostfixText` 和 `GaussDBAManuelPredicate` 的 visitor 方法
  - SONAR Oracle 使用这两个 AST 节点生成查询，缺少 visitor 导致 `AssertionError`
- **GaussDB-A Oracle 数量达到 25 个，与 PostgreSQL 完全对齐**
- 集成测试验证：SONAR 120 queries ✅、EDC_RADAR 54 queries ✅

## v2.7.0 (2026-06-16)
- **GaussDB-A CODDTEST Oracle**：完整 3 模式实现（EXPRESSION/SUBQUERY/RANDOM）
  - 算法与 GaussDB-M/MySQL CODDTEST 一致
  - Oracle 语义适配：NUMBER(1) 模拟布尔、空字符串 = NULL、ORA-xxxxx 错误码
  - 新增 AST 类型：GaussDBAOracleAlias、GaussDBAOracleExpressionBag
  - 新增 CLI 参数：`--coddtest-model`（GaussDB-A/GaussDB-M，默认 RANDOM）
- **Bug 修复**：
  - DQP NullPointerException：GaussDBAColumnReference 空列防御性检查
  - EET_DELETE DateTimeException：DATE/TIMESTAMP 常量生成范围限制为 ±365 天
  - 编译警告：移除 MySQL/PG/GaussDB-M CODDTEST 未使用的 import/field
- **Oracle 统计**：MySQL 25, PostgreSQL 25, GaussDB-M 25, GaussDB-A 25
- **跨数据库矩阵**：CODDTEST 现支持全部 4 款目标数据库

## v2.4.6 (2026-06-09)
- 重构 [README.md] 从中文用户手册改为英文项目门户文档
- 更新 [中文用户指南] 同步所有变更（JIR、GaussDB-M --target-database、版本号）

## v2.4.5 (2026-06-09)
- 修复 [GaussDB-M Provider] 连接方式从 CREATE DATABASE 改为 schema 隔离（对齐 GaussDB-A 模式）
  - **关键修复**：`CREATE DATABASE ... DBCOMPATIBILITY 'M'` 在远程服务器不被支持 → 改用 `--target-database` + schema 隔离
  - 新增 `--target-database` 参数（与 GaussDB-A 保持一致）
  - `DROP SCHEMA ... CASCADE` 在 M 兼容模式不支持 → 去掉 CASCADE

## v2.4.4 (2026-06-09)
- 优化 [JIR Oracle] 第三轮深度对齐 — 共享 Fetch Columns + PostgreSQL NATURAL AST + 条件简化
  - **P0: Rules 2-6 共享 Fetch Columns**：source/target 查询使用同一 fetch columns
  - **P1: PostgreSQL NATURAL JOIN AST 支持**：消除 Rule 6 原始 SQL 拼接
  - **P3: MySQL createBaseSelect 条件简化**

## v2.4.3 (2026-06-08)
- 优化 [JIR Oracle] 二次深度对齐 — 列选择修复 + ORDER BY + Reproducer
  - **P0: Rule 1 随机单列修复**：`generateRandomFetchColumns()` 从多列改为随机选取一列
  - **P1: ORDER BY 支持**：JIROracle 以低概率为 source query 添加 `ORDER BY 1`
  - **P2: Reproducer 验证**：`JIRReproducer` 确认 Bug 可重现性
  - **P3: Rule 2-6 随机左表列**：提升列覆盖多样性

## v2.4.2 (2026-06-05)
- 新增 [JIR Oracle] 第一轮集成
  - Rule 1 核心算法：多列 fetch + NULL 替换
  - MULTISET 比较：`assumeResultSetsAreEqualMultiset()` + `canonicalizeResultValue`
  - GaussDB-A NATURAL JOIN AST + ON TRUE
  - PostgreSQL/GaussDB-A FULL JOIN 支持（Rule 4）

## v2.0.60 (2026-05-27)
- **GaussDB-M EET 对齐**：ExpressionTree 从16种扩展到32种节点类型
- **文档更新**：添加 EET 变体、TX_INFER、FUCCI 深度解析章节

## v2.0.59 (2026-05-27)
- **EET Oracle 对齐原生工具**：IN→EXISTS 变换支持 UNION 子查询
- **isQueryLevelNode 机制**：查询级节点递归变换内部表达式但不做 CASE WHEN wrapping
- **GaussDB-A EET 扩展**：ExpressionTree 12→24+，MINUS→NOT EXISTS，IN→EXISTS UNION

## v0.1.85 (2026-05-08)
- **新增 Oracle**: WRITE_CHECK 事务级别测试 Oracle
- **Bug 修复**: BigDecimal 处理以兼容 GaussDB-M JDBC 驱动

## v0.1.84 (2026-04-30)
- **新增文档**：全面的 Oracle 参考指南
- **Oracle 支持更新**：EDC_RADAR 和 SONAR 添加到所有支持的数据库

## v0.1.83 (2026-04-25)
- **GaussDB-A/PG**: `--target-database` 现为必需参数
- **模式隔离**: 明确 A/PG 使用模式隔离

## v0.1.82 (2026-04-25)
- **打包**: 轻量 Jar (~4MB) + manifest Class-Path 加载
- **驱动**: 本地 lib 目录包含 openGauss JDBC 驱动