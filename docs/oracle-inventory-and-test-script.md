# SQLancer Test Oracle 全量分析与一键测试脚本

## 1. Oracle 清单

| DBMS | 命令名 | Oracle 数 | 查询类 | 事务类 | 特殊参数 |
|------|--------|----------|--------|--------|----------|
| **MySQL** | `mysql` | 25 | 20 + 1(QP) | 4 | `--coddtest-model`, `--engines`, FUCCI 3 参数, 50+ hidden `--test-*` |
| **PostgreSQL** | `postgres` | 25 | 20 + 1(QP) | 4 | `--pg-table-columns`, `--pg-tables`, `--coverage-policy`, FUCCI 3 参数, 20+ hidden |
| **GaussDB-M** | `gaussdb-m` | 25 | 20 + 1(QP) | 4 | `--target-database` (**必填**), FUCCI 3 参数 |
| **GaussDB-A** | `gaussdb-a` | 25 | 20 + 1(QP) | 4 | `--target-database` (**必填**), `--enable-clob-blob`, `--coddtest-model`, FUCCI 3 参数 |
| **GaussDB-PG** | `gaussdb-pg` | 12 | 11 + 1(QP) | 0 | `--target-database` (可选, 默认 postgres), `--enable-time-types` |

### 详细 Oracle 列表

#### MySQL (25)

| # | Oracle | 类型 | 实现类 | 参数组合 |
|---|--------|------|--------|----------|
| 1 | AGGREGATE | TLP | MySQLTLPAggregateOracle | — |
| 2 | CERT | 查询计划 | CERTOracle | — |
| 3 | CODDTEST | CODD | MySQLCODDTestOracle | `--coddtest-model` RANDOM/EXPRESSION/SUBQUERY |
| 4 | DISTINCT | TLP | MySQLTLPDistinctOracle | — |
| 5 | DQE | 查询等价 | MySQLDQEOracle | — |
| 6 | DQP | 查询分区 | MySQLDQPOracle | — |
| 7 | EDC_DATA | 数据操作 | MySQLEDCDataOracle | — |
| 8 | EDC_RADAR | 约束优化 | MySQLEDCRadarOracle | — |
| 9 | EET | 表达式变换 | MySQLEETOracle | — |
| 10 | EET_DELETE | EET 变体 | MySQLEETDeleteOracle | — |
| 11 | EET_INSERT_SELECT | EET 变体 | MySQLEETInsertSelectOracle | — |
| 12 | EET_UPDATE | EET 变体 | MySQLEETUpdateOracle | — |
| 13 | FUZZER | 随机 | MySQLFuzzer | — |
| 14 | GROUP_BY | TLP | MySQLTLPGroupByOracle | — |
| 15 | HAVING | TLP | MySQLTLPHavingOracle | — |
| 16 | JIR | JOIN 推理 | JIROracle | 5 条规则 (MySQL 无 FULL OUTER JOIN) |
| 17 | NOREC | 查询等价 | NoRECOracle | — |
| 18 | PQS | pivoted | MySQLPivotedQuerySynthesisOracle | — |
| 19 | QUERY_PARTITIONING | 组合 | CompositeTestOracle (TLP_WHERE+HAVING+GROUP_BY+AGGREGATE+DISTINCT+NOREC) | — |
| 20 | SONAR | 优化分析 | MySQLSonarOracle | — |
| 21 | TLP_WHERE | TLP | TLPWhereOracle | — |
| 22 | **FUCCI** | 事务 | MySQLFucciOracle | `--fucci-oracle-type` DT/MT/CS/ALL × `--fucci-isolation-level` READ_UNCOMMITTED/READ_COMMITTED/REPEATABLE_READ/SERIALIZABLE/RANDOM × `--fucci-schedule-count` |
| 23 | **TX_INFER** | 事务 | MySQLTxInferOracle | — |
| 24 | **WRITE_CHECK** | 事务 | MySQLWriteCheckOracle | — |
| 25 | **WRITE_CHECK_REPRODUCE** | 事务 | MySQLWriteCheckReproduceOracle | — |

#### PostgreSQL (25) — 同 MySQL + 部分参数不同

| 差异项 | 说明 |
|--------|------|
| FUCCI 隔离级别 | 无 READ_UNCOMMITTED，有 READ_COMMITTED/REPEATABLE_READ/SERIALIZABLE |
| CODDTEST | `--coddtest-model` 同 MySQL |
| JIR 规则 | 6 条 (含 FULL OUTER JOIN) |
| PostgreSQL 特有参数 | `--pg-table-columns`, `--pg-tables`, `--pg-generate-sql-num`, `--pg-index-model`, `--coverage-policy` |

#### GaussDB-M (25) — 同 MySQL

| 差异项 | 说明 |
|--------|------|
| **必填参数** | `--target-database` (DBCOMPATIBILITY 'M') |
| JIR 规则 | 5 条 (无 FULL OUTER JOIN) |
| FUCCI 隔离级别 | READ_COMMITTED/REPEATABLE_READ/SERIALIZABLE/RANDOM |
| 默认端口 | 19995 |

#### GaussDB-A (25) — 与 MySQL/PostgreSQL/GaussDB-M 完全对齐

| 特殊 | 说明 |
|------|------|
| **必填参数** | `--target-database` (DBCOMPATIBILITY 'A') |
| `--enable-clob-blob` | 启用 CLOB/BLOB 类型 |
| `--coddtest-model` | RANDOM/EXPRESSION/SUBQUERY |
| JIR 规则 | 6 条 (含 FULL OUTER JOIN + NATURAL JOIN) |
| FUCCI 隔离级别 | READ_COMMITTED/REPEATABLE_READ/SERIALIZABLE/RANDOM |
| 默认端口 | 19995 |
| Oracle 兼容特性 | NUMBER/VARCHAR2/DATE/TIMESTAMP 类型、空字符串=NULL、ORA 错误码 |

#### GaussDB-PG (12) — 最小集合

| # | Oracle | 备注 |
|---|--------|------|
| 1 | TLP_WHERE | 正常实现 |
| 2 | NOREC | 正常实现 |
| 3 | QUERY_PARTITIONING | 组合 (TLP_WHERE+HAVING+AGGREGATE) |
| 4 | HAVING | 正常实现 |
| 5 | AGGREGATE | 正常实现 |
| 6 | DISTINCT | 正常实现 |
| 7 | GROUP_BY | 正常实现 |
| 8 | PQS | 正常实现 |
| 9 | **CERT** | ⚠️ 占位符，实际返回 TLP_WHERE |
| 10 | **DQP** | ⚠️ 占位符，实际返回 TLP_WHERE |
| 11 | **DQE** | ⚠️ 占位符，实际返回 TLP_WHERE |
| 12 | FUZZER | 正常实现 |

无 EET、EDC_RADAR、EDC_DATA、SONAR、CODDTEST、事务类 Oracle

## 2. 参数组合矩阵

### FUCCI 参数组合

| `--fucci-oracle-type` | `--fucci-isolation-level` | 组合数 |
|-----------------------|---------------------------|--------|
| DT, MT, CS, ALL | MySQL: READ_UNCOMMITTED/READ_COMMITTED/REPEATABLE_READ/SERIALIZABLE/RANDOM | 4×5=20 |
| DT, MT, CS, ALL | PG/GaussDB: READ_COMMITTED/REPEATABLE_READ/SERIALIZABLE/RANDOM | 4×4=16 |

### CODDTEST 参数组合 (MySQL/PG/GaussDB-M 支持 --coddtest-model)

| DBMS | `--coddtest-model` 支持 | 组合数 |
|------|-------------------------|--------|
| MySQL | ✅ 支持 RANDOM/EXPRESSION/SUBQUERY | 3 |
| PostgreSQL | ✅ 支持 RANDOM/EXPRESSION/SUBQUERY | 3 |
| GaussDB-M | ✅ 支持 RANDOM/EXPRESSION/SUBQUERY | 3 |

> 注：PostgreSQL/GaussDB-M 的 SUBQUERY 模式目前 fallback 到 EXPRESSION（subquery 折叠逻辑尚未实现），但参数解析已生效。

### 全量参数组合统计

| DBMS | 查询 Oracle | CODDTEST 组合 | FUCCI 组合 | TX 类 | 总运行次数 |
|------|------------|---------------|------------|-------|-----------|
| MySQL | 19 | 3 | 20 | 3 | **45** |
| PostgreSQL | 19 | 3 | 16 | 3 | **42** |
| GaussDB-M | 19 | 3 | 16 | 3 | **42** |
| GaussDB-A | 16 | 0 | 16 | 3 | **36** |
| GaussDB-PG | 11 | 0 | 0 | 0 | **12** |

## 3. 一键测试脚本

文件：`run_all_tests.sh`

### 用法示例

```bash
# 1. 单 DBMS 全量测试 (默认所有 Oracle + 参数组合)
./run_all_tests.sh --dbms mysql --host localhost --port 3306 --username tpcc --password Taurus@123 --num-tries 50 --duration 300

# 2. 单 Oracle 测试
./run_all_tests.sh --dbms mysql --host localhost --port 3306 --username tpcc --password Taurus@123 --oracle EDC_DATA

# 3. FUCCI 测试 (自动遍历所有参数组合)
./run_all_tests.sh --dbms postgres --host localhost --port 5432 --username tpcc --password Taurus@123 --oracle FUCCI

# 4. GaussDB-M 测试
./run_all_tests.sh --dbms gaussdb-m --host 121.37.186.131 --port 19995 --username sqlbuilder1 --password huawei@123 --target-database testm --num-tries 100

# 5. 全 DBMS 全量测试 (需要配置所有连接)
./run_all_tests.sh --all --host ... --password ...

# 6. 跳过构建 (使用已有 JAR)
./run_all_tests.sh --dbms mysql --skip-build ...
```

### 脚本特性

1. **自动构建**：默认运行 `mvn package` 构建 JAR
2. **FUCCI 参数遍历**：自动运行 4 种 OracleType × 4-5 种 IsolationLevel
3. **CODDTEST 参数遍历**：自动运行 3 种 Model
4. **结果汇总**：每个 DBMS 测试后输出通过/失败/Bug 发现统计
5. **GaussDB target-database**：GaussDB-M/A 必填，GaussDB-PG 可选
6. **超时控制**：每个 Oracle 可配置运行时间上限
7. **退出码语义**：0=通过, 1=发现Bug, 其他=异常

## 4. 连接配置速查

| DBMS | 默认端口 | 默认主机 | 必填参数 | JDBC 协议 |
|------|---------|---------|---------|-----------|
| MySQL | 3306 | localhost | — | jdbc:mysql:// |
| PostgreSQL | 5432 | localhost | — | jdbc:postgresql:// |
| GaussDB-M | 19995 | 无(必填host) | `--target-database` | jdbc:opengauss:// |
| GaussDB-A | 19995 | 无(必填host) | `--target-database` | jdbc:opengauss:// |
| GaussDB-PG | 19995 | 无(必填host) | `--target-database`(可选) | jdbc:opengauss:// |

### GaussDB 兼容数据库创建

```sql
-- GaussDB-M (MySQL兼容)
CREATE DATABASE testm WITH DBCOMPATIBILITY 'M';

-- GaussDB-A (Oracle兼容)
CREATE DATABASE testa WITH DBCOMPATIBILITY 'A';

-- GaussDB-PG (PG兼容)
CREATE DATABASE testpg WITH DBCOMPATIBILITY 'pg';
```
