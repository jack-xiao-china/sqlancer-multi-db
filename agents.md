# SQLancer 项目分析与代理执行指南

## 概述

- 本项目是一个用于自动化测试数据库管理系统（DBMS）的工具，核心目标是通过自动生成 SQL、执行并比对结果来发现数据库实现中的逻辑 Bug。
- 主要特性包括 **25+ 种测试 Oracle**（NoREC、PQS、TLP、DQE、DQP、CERT、EET、CODDTEST、EDC_RADAR、EDC_DATA、SONAR、WRITE_CHECK、FUCCI、TX_INFER、JIR 等），对比/回归测试的测试用例和测试流控制，以及对 25 种数据库的适配层。

## 架构要点

- **Core（核心引擎）**：控制测试流程（参数解析、测试循环、测试用例管理、结果收集与日志输出）
- **DBMS 适配层（Adapters）**：对各 DBMS 的连接、语法特性、结果校验等做适配
- **测试 Oracle（Oracles）**：25 种 Oracle 检测不同类别的逻辑 Bug
- **实验日志与调试**：主要日志默认在 `target/logs/`

### 当前版本：v2.7.7

### 25 种 Oracle 完整列表

| # | Oracle | 类型 | 描述 |
|---|--------|------|------|
| 1 | NOREC | 标准 | 索引优化检测 |
| 2 | PQS | 标准 | Pivoted Query Synthesis |
| 3 | TLP_WHERE | 标准 | TLP WHERE 变体 |
| 4 | HAVING | 标准 | TLP HAVING 变体 |
| 5 | AGGREGATE | 标准 | TLP Aggregate 变体 |
| 6 | DISTINCT | 标准 | TLP Distinct 变体 |
| 7 | GROUP_BY | 标准 | TLP Group By 变体 |
| 8 | CERT | 标准 | 查询计划对比 |
| 9 | FUZZER | 标准 | 随机查询生成 |
| 10 | QUERY_PARTITIONING | 组合 | WHERE+HAVING+AGGREGATE+DISTINCT+NOREC |
| 11 | DQE | 变更等价 | DELETE/UPDATE 等价性检测 |
| 12 | DQP | 变更等价 | 确定性查询分区检测 |
| 13 | EET | 表达式变换 | 等价表达式变换检测 |
| 14 | EET_UPDATE | EET 变体 | UPDATE 路径等价检测 |
| 15 | EET_DELETE | EET 变体 | DELETE 路径等价检测 |
| 16 | EET_INSERT_SELECT | EET 变体 | INSERT SELECT 路径等价检测 |
| 17 | CODDTEST | 常量优化 | 常量折叠检测（3 种模式） |
| 18 | EDC_RADAR | 约束优化 | 等价数据库构建-约束优化 |
| 19 | EDC_DATA | 数据操作 | 等价数据构建检测 |
| 20 | SONAR | 性能 | 优化/非优化对比检测 |
| 21 | WRITE_CHECK | 事务 | 事务隔离检测 |
| 22 | WRITE_CHECK_REPRODUCE | 事务 | WRITE_CHECK Bug 重现 |
| 23 | FUCCI | MVCC | 多版本并发控制检测 |
| 24 | TX_INFER | MVCC | MVCC 版本推断检测 |
| 25 | JIR (SIGMOD 2026) | JOIN 优化 | Join Implication Reasoning — 6 条语义规则 |

### 25 种 DBMS Provider 完整列表

citus, clickhouse, cnosdb, cockroachdb, databend, datafusion, doris, duckdb, **gaussdb-a**, **gaussdb-m**, gaussdb-pg, h2, hive, hsqldb, mariadb, materialize, **mysql**, oceanbase, **postgres**, presto, questdb, sqlite3, tidb, yugabyte.ycql, yugabyte.ysql

**重点适配的 4 种数据库**（CLAUDE.md 强制规则）：MySQL, PostgreSQL, GaussDB-M, GaussDB-A

## 快速入门

### 构建/编译

```bash
mvn clean package -DskipTests
```

### 运行

```bash
cd target
java -jar sqlancer-2.7.7.jar --host localhost --port 3306 --username root --password password --num-tries 1 --timeout-seconds 100 --log-each-select true --num-threads 2 mysql --oracle QUERY_PARTITIONING
```

> ⚠️ GaussDB-A 和 GaussDB-M 需要使用 `java -cp` 方式运行（system scope 依赖），详见 `lib/README.md`

## 近期重要变更 (v2.7.3–v2.7.7)

| 版本 | 变更 |
|------|------|
| v2.7.7 | PostgresIndex `backsConstraint` 检查 — 防止 `ADD CONSTRAINT USING INDEX` 误选已关联约束的索引 |
| v2.7.6 | JIR P1：多表 JOIN 链（2-3 表），CROSS 4 种变体，Rule 4 full-anti 全列覆盖 |
| v2.7.5 | MySQLWildcard 修复：`SELECT * AS ref0` 不再生成 |
| v2.7.4 | JIR Rule3 算法修复，多列 fetch，NATURAL OUTER JOIN 变体 |
| v2.7.3 | JIR NPE 修复，GaussDB-A CODDTEST robustness |
| v2.7.1 | GaussDB-A SONAR + EDC_RADAR → 25 oracle parity |

## 输出与日志指南

- 主要日志默认在 `target/logs/`
- 当出现断言错误（AssertionError）且日志中有对应的对比输出，通常代表检测到潜在逻辑错误
- 若遇到崩溃，日志中会有崩溃现场信息，需配合重现脚本进一步定位

## Oracle Smoke 测试（工具健康检查）

目标：

- 允许发现逻辑错误（AssertionError mismatch）
- 不允许 SQLancer 自身生成非法 SQL/崩溃（如 `SQLSyntaxErrorException`、NPE、内部 `AssertionError: DATE`）

执行：

```bash
mvn package -DskipTests
```

```powershell
.\scripts\run-mysql-oracles-smoke.ps1 -DbHost localhost -Port 3306 -Username root -Password password
```

在 Linux 环境（bash）运行：

```bash
export DB_HOST=localhost DB_PORT=3306 DB_USER=root DB_PASS='password'
export TIMEOUT_SECONDS=25 NUM_QUERIES=5000 NUM_THREADS=1
./scripts/run-mysql-oracles-smoke.sh
```

输出：

- 汇总：`target/logs/mysql-oracles-smoke-results.csv`
- 详细失败（仅非 OK）：`target/logs/mysql-oracles-smoke-summary.txt`

## 参考与链接

- 项目主页/代码库：`https://github.com/jack-xiao-china/sqlancer-multi-db`
- 文档与论文：`docs/` 目录及 `https://github.com/sqlancer/sqlancer`
