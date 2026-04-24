# SQLancer 用户使用指导

**文档版本**: v0.1.81 (2026-04-24)  
**参考源码**: [SQLancer GitHub](https://github.com/sqlancer/sqlancer)  

本版本扩展了 SQLancer 对 MySQL、PostgreSQL、GaussDB-A、GaussDB-PG、GaussDB-M 等数据库的全面支持，包括：

- **14种 Test Oracle**：TLP系列、NoREC、PQS、CERT、DQP、DQE、EET、CODDTEST、FUZZER
- **GaussDB 三种兼容模式**：A兼容（Oracle风格）、PG兼容、M兼容（MySQL风格）
- **扩展数据类型**：JSON、BLOB、Temporal、Array、Enum、Spatial等
- **国际化支持**：支持中文、德文、日文等非英文服务器错误消息处理

---

## 一、支持数据库总览

| 数据库 | 支持Oracle数 | 数据类型覆盖 | 状态 |
|--------|-------------|-------------|------|
| **MySQL** | 14种 | 完整（含JSON/BLOB/Spatial） | ✅ 已验证 |
| **PostgreSQL** | 14种 | 完整（含Temporal/JSONB/Array） | ✅ 已验证 |
| **GaussDB-A** | 13种 | 完整（含CLOB/BLOB） | ✅ 已验证 |
| **GaussDB-PG** | 13种 | 完整（含Temporal） | ✅ 已验证 |
| **GaussDB-M** | 14种 | MySQL风格 | ⚠️ 需M兼容库 |
| SQLite3 | 10种 | 基础 | ✅ |
| TiDB | 5种 | 基础 | ✅ |
| CockroachDB | 8种 | 基础 | ✅ |
| DuckDB | 7种 | 基础 | ✅ |
| MariaDB | 2种 | 基础 | ✅ |

---

## 二、各数据库支持的 Test Oracle

### 2.1 MySQL（完整支持）

| Oracle | 说明 | 性能 |
|--------|------|------|
| `TLP_WHERE` | TLP WHERE 子句分区 | ~2700 queries/s |
| `HAVING` | TLP HAVING 子句分区 | 中等 |
| `GROUP_BY` | TLP GROUP BY 分区 | 中等 |
| `AGGREGATE` | TLP 聚合函数分区 | 中等 |
| `DISTINCT` | TLP DISTINCT 分区 | 中等 |
| `NOREC` | NoREC 优化器检测 | ~3000 queries/s |
| `QUERY_PARTITIONING` | 组合Oracle（默认） | ~2700 queries/s, 成功率95% |
| `PQS` | 轴心行存在性检查 | 中等 |
| `CERT` | 基数估计性能检测 | 中等 |
| `DQP` | 不同执行计划结果一致性 | ~2000 queries/s |
| `DQE` | SELECT/UPDATE/DELETE 一致性 | ~23 queries/s |
| `EET` | 等价表达式变换 | ~340 queries/s |
| `CODDTEST` | 常量驱动等价变换检测 | 中等 |
| `FUZZER` | 随机 Fuzzer | ~3000 queries/s |

**示例**：
```bash
java -jar sqlancer-2.0.0.jar mysql --oracle QUERY_PARTITIONING
java -jar sqlancer-2.0.0.jar mysql --oracle DQP --oracle DQE
java -jar sqlancer-2.0.0.jar mysql --oracle EET --engines=InnoDB,MyISAM
java -jar sqlancer-2.0.0.jar --use-reducer mysql --oracle EET
```

**MySQL 数据类型支持**：
- 数值：INT, BIGINT, FLOAT, DOUBLE, DECIMAL, BIT
- 字符：VARCHAR, TEXT, CHAR
- 二进制：BINARY, VARBINARY, BLOB
- 时间：DATE, TIME, DATETIME, TIMESTAMP, YEAR
- JSON：JSON（含 JSON_TYPE/JSON_VALID 函数）
- 枚举：ENUM, SET


### 2.2 PostgreSQL（完整支持）

| Oracle | 说明 | 性能 |
|--------|------|------|
| `NOREC` | NoREC 优化器检测 | ~3000 queries/s |
| `PQS` | 轴心行存在性检查 | 中等 |
| `TLP_WHERE` | TLP WHERE 子句分区 | ~800 queries/s |
| `HAVING` | TLP HAVING 子句分区 | 中等 |
| `AGGREGATE` | TLP 聚合分区 | 中等 |
| `DISTINCT` | TLP DISTINCT 分区 | 中等 |
| `GROUP_BY` | TLP GROUP BY 分区 | 中等 |
| `QUERY_PARTITIONING` | 组合Oracle（默认） | ~700 queries/s |
| `CERT` | 基数估计性能检测 | 中等 |
| `DQP` | 不同执行计划结果一致性 | 中等 |
| `DQE` | SELECT/UPDATE/DELETE 一致性 | 中等 |
| `EET` | 等价表达式变换 | 中等 |
| `CODDTEST` | 常量驱动等价变换检测 | 中等 |
| `FUZZER` | 随机 Fuzzer | ~3000 queries/s |

**PostgreSQL 数据类型支持**：
- 数值：INT, BIGINT, SERIAL, FLOAT, DOUBLE, DECIMAL
- 字符：VARCHAR, CHAR, TEXT
- 时间：DATE, TIME, TIMETZ, TIMESTAMP, TIMESTAMPTZ
- JSON：JSON, JSONB
- 二进制：BYTEA
- UUID：UUID
- 数组：ARRAY (INT[], VARCHAR[]等)
- 枚举：ENUM

**PostgreSQL DDL/DML 覆盖增强**：
- DDL：`DROP TABLE`、`DROP VIEW`、`DROP SEQUENCE`、`ALTER SEQUENCE`、`ALTER INDEX`
- 对象：composite `CREATE TYPE`、简单 SQL `CREATE FUNCTION`、`CREATE RULE`
- 索引：`BTREE/HASH/GIST/GIN/SPGIST/BRIN`
- DML：`MERGE`、低频 `COPY ... TO STDOUT`
- 暂未启用：触发器与权限管理语句（如 `GRANT/REVOKE`）

**PostgreSQL 专属参数**：
```bash
--coverage-policy BALANCED        # CONSERVATIVE|BALANCED|AGGRESSIVE
--pg-table-columns 10             # CREATE TABLE 列数
--pg-generate-sql-num 3           # INSERT 行数
--pg-index-model 0                # 索引生成模式(0-6)
--extensions "pg_trgm,postgis"    # 预创建扩展
```

### 2.3 GaussDB-A（A兼容模式）

GaussDB-A 支持 Oracle 风格的 SQL 语法和数据类型。

| Oracle | 说明 |
|--------|------|
| `TLP_WHERE` | TLP WHERE 子句分区 |
| `HAVING` | TLP HAVING 子句分区 |
| `AGGREGATE` | TLP 聚合分区 |
| `DISTINCT` | TLP DISTINCT 分区 |
| `GROUP_BY` | TLP GROUP BY 分区 |
| `NOREC` | NoREC 优化器检测 |
| `QUERY_PARTITIONING` | 组合Oracle（默认） |
| `PQS` | 轴心行存在性检查 |
| `CERT` | 基数估计检测 |
| `DQP` | 不同执行计划一致性 |
| `DQE` | SELECT/UPDATE/DELETE一致性 |
| `EET` | 等价表达式变换 |
| `FUZZER` | 随机 Fuzzer |

**连接示例**：
```bash
java -jar sqlancer-2.0.0.jar \
    --host localhost --port 5432 \
    --username tpcc --password Taurus@123 \
    gaussdb-a --oracle QUERY_PARTITIONING
```

**创建A兼容数据库**：
```sql
CREATE DATABASE gaussdb_a_test WITH dbcompatibility 'A';
```

**数据类型支持**：
- 数值：NUMBER, INTEGER, BIGINT, FLOAT, DOUBLE
- 字符：VARCHAR2, CHAR, CLOB（需 `--enable-clob-blob`）
- 时间：DATE, TIMESTAMP
- 二进制：BLOB（需 `--enable-clob-blob`）

### 2.4 GaussDB-PG（PG兼容模式）

GaussDB-PG 支持 PostgreSQL 风格的 SQL 语法。

| Oracle | 说明 |
|--------|------|
| `TLP_WHERE` | TLP WHERE 子句分区 |
| `HAVING` | TLP HAVING 子句分区 |
| `AGGREGATE` | TLP 聚合分区 |
| `DISTINCT` | TLP DISTINCT 分区 |
| `GROUP_BY` | TLP GROUP BY 分区 |
| `NOREC` | NoREC 优化器检测 |
| `QUERY_PARTITIONING` | 组合Oracle（默认） |
| `PQS` | 轴心行存在性检查 |
| `CERT` | 基数估计检测 |
| `DQP` | 不同执行计划一致性 |
| `DQE` | SELECT/UPDATE/DELETE一致性 |
| `EET` | 等价表达式变换 |
| `FUZZER` | 随机 Fuzzer |

**连接示例**：
```bash
java -jar sqlancer-2.0.0.jar \
    --host localhost --port 5432 \
    --username tpcc --password Taurus@123 \
    gaussdb-pg --oracle QUERY_PARTITIONING
```

**创建PG兼容数据库**：
```sql
CREATE DATABASE gaussdb_pg_test WITH dbcompatibility 'pg';
```

### 2.5 GaussDB-M（M兼容模式）

GaussDB-M 支持 MySQL 风格的 SQL 语法。

| Oracle | 说明 |
|--------|------|
| `TLP_WHERE` | TLP WHERE 子句分区 |
| `HAVING` | TLP HAVING 子句分区 |
| `GROUP_BY` | TLP GROUP BY 分区 |
| `AGGREGATE` | TLP 聚合分区 |
| `DISTINCT` | TLP DISTINCT 分区 |
| `NOREC` | NoREC 优化器检测 |
| `QUERY_PARTITIONING` | 组合Oracle（默认） |
| `PQS` | 轴心行存在性检查 |
| `CERT` | 基数估计检测 |
| `DQP` | 不同执行计划一致性 |
| `DQE` | SELECT/UPDATE/DELETE一致性 |
| `EET` | 等价表达式变换 |
| `CODDTEST` | 常量驱动等价变换检测 |
| `FUZZER` | 随机 Fuzzer |

**连接示例**：
```bash
java -jar sqlancer-2.0.0.jar \
    --connection-url "jdbc:gaussdb://127.0.0.1:8000/test" \
    --username sqlancer --password sqlancer \
    gaussdb-m --oracle QUERY_PARTITIONING
```

**创建M兼容数据库**：
```sql
CREATE DATABASE gaussdb_m_test WITH dbcompatibility 'B';
```

---

## 三、Oracle 快速参考表

| DBMS | 可用 Oracle 列表 |
|------|------------------|
| **MySQL** | TLP_WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, CODDTEST, EET, FUZZER |
| **PostgreSQL** | NOREC, PQS, TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, QUERY_PARTITIONING, CERT, DQP, DQE, EET, CODDTEST, FUZZER |
| **GaussDB-A** | TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, EET, FUZZER |
| **GaussDB-PG** | TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, EET, FUZZER |
| **GaussDB-M** | TLP_WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, EET, CODDTEST, FUZZER |
| SQLite3 | NOREC, WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, QUERY_PARTITIONING, PQS, CODDTEST, FUZZER |
| TiDB | WHERE, HAVING, QUERY_PARTITIONING, CERT, DQP |
| CockroachDB | NOREC, WHERE, HAVING, AGGREGATE, GROUP_BY, DISTINCT, QUERY_PARTITIONING, CERT |
| DuckDB | NOREC, WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, QUERY_PARTITIONING |
| MariaDB | NOREC, DQP |

---

## 四、常用选项

| 选项 | 说明 | 默认值 |
|------|------|--------|
| `--num-threads N` | 线程数 | 1 |
| `--oracle <名称>` | 指定 Test Oracle | QUERY_PARTITIONING |
| `--num-tries N` | 测试轮数 | 1 |
| `--timeout-seconds N` | 最大运行时间（秒） | 120 |
| `--log-dir <目录>` | 日志基目录 | logs/<dbms>/ |
| `--log-each-select` | 记录每条SQL | true |
| `--use-reducer` | 启用用例缩减 | false |
| `--seed N` | 随机种子（复现用） | 时间戳 |

---

## 五、验证性能数据（v0.1.73）

| 数据库 | Oracle | 查询数/秒 | 成功率 | 状态 |
|--------|--------|----------|--------|------|
| MySQL | QUERY_PARTITIONING | ~2700 | 95% | ✅ 稳定运行 |
| MySQL | DQP | ~2000 | 99% | ✅ 稳定运行 |
| MySQL | DQE | ~23 | 96% | ✅ 稳定运行 |
| MySQL | EET | ~340 | 62% | ✅ 稳定运行 |
| PostgreSQL | QUERY_PARTITIONING | ~700 | 53% | ✅ 稳定运行 |
| GaussDB-PG | QUERY_PARTITIONING | ~3500 | 53% | ✅ 稳定运行 |
| GaussDB-A | TLP_WHERE | ~4000 | 62% | ✅ 稳定运行 |

---

## 六、日志与复现

- **默认日志目录**：`logs/<dbms>/<oracle>_YYYY_MMDD_HHMM/`
- **主要文件**：
  - `<database>.log`：异常栈与可复现状态
  - `<database>-cur.log`：每条执行 SQL
  - `reduce/<database>-reduce.log`：缩减日志（需 `--use-reducer`）
  - `reproduce/<database>.ser`：序列化复现状态

---

## 七、快速开始示例

### MySQL 测试
```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost --port 3306 \
    --username root --password Taurus@123 \
    --num-tries 10 --timeout-seconds 100 \
    mysql --oracle QUERY_PARTITIONING
```

### PostgreSQL 测试
```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost --port 5432 \
    --username tpcc --password Taurus@123 \
    --num-tries 10 --timeout-seconds 100 \
    postgres --oracle QUERY_PARTITIONING
```

### GaussDB-PG 测试
```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost --port 5432 \
    --username tpcc --password Taurus@123 \
    --num-tries 10 --timeout-seconds 100 \
    gaussdb-pg --oracle QUERY_PARTITIONING
```

### GaussDB-A 测试
```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost --port 5432 \
    --username tpcc --password Taurus@123 \
    --num-tries 10 --timeout-seconds 100 \
    gaussdb-a --oracle QUERY_PARTITIONING
```

---

## 八、版本历史

### v0.1.81 (2026-04-24)
- 扩展 PostgreSQL DDL/DML 生成：DROP/ALTER 对象、composite CREATE TYPE、CREATE FUNCTION、CREATE RULE、MERGE、低频 COPY
- PostgreSQL 索引 access method 增加 SPGIST/BRIN
- 触发器与权限语句暂不启用，避免引入额外状态与权限噪声

### v0.1.73 (2026-04-21)
- 集成验证：MySQL、PostgreSQL、GaussDB-PG、GaussDB-A 全面通过
- 修复 MySQL ALTER TABLE 对视图执行问题
- 修复 MySQL DQE oracle rowId 列名问题
- 修复 MySQL JSON 列索引预期错误处理
- 验证：所有数据库稳定运行，可持续发现逻辑错误

### v0.1.72 (2026-04-20)
- 优化 PostgreSQL 查询加锁策略
- 修复日志目录按分钟切分问题

### v0.1.70-71 (2026-04-18-20)
- PostgreSQL 代码线收敛
- 新增 PostgreSQL temporal/JSON/UUID/Array/Enum 支持
- GaussDB-A 约束和 DML 生成
- MySQL JSON/BLOB/BINARY 类型支持

---

sqlancer指定版本号的编译方法：
  总结：                                                                                                                             
                                                                                                                                     
  ┌─────────────────────────────────────────────────────────────────────┬──────────────────────┐                                     
  │                              构建方式                               │       输出文件       │                                     
  ├─────────────────────────────────────────────────────────────────────┼──────────────────────┤                                     
  │ mvn package -DskipTests                                             │ sqlancer-2.0.0.jar   │                                     
  ├─────────────────────────────────────────────────────────────────────┼──────────────────────┤                                     
  │ mvn package -Drevision=2.0.100 -DskipTests                          │ sqlancer-2.0.100.jar │                                     
  ├─────────────────────────────────────────────────────────────────────┼──────────────────────┤                                     
  │ mvn package -Drevision=2.0.$(git rev-list --count HEAD) -DskipTests │ sqlancer-2.0.15.jar  │                                     
  └─────────────────────────────────────────────────────────────────────┴──────────────────────┘ 

## 九、参考链接

- [SQLancer GitHub](https://github.com/sqlancer/sqlancer)
- [详细用户指导 docs/USER_GUIDE.md](docs/USER_GUIDE.md)
- [发现缺陷汇总](https://www.manuelrigger.at/dbms-bugs/)
- [Papers and References](docs/PAPERS.md)
