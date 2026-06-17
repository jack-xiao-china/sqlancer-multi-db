# Oracle 审计报告：代码实现 vs 文档一致性检查

**日期**: 2026-06-11  
**审计范围**: MySQL, PostgreSQL, GaussDB-M, GaussDB-A, GaussDB-PG  
**数据来源**: OracleFactory 代码 vs USER_GUIDE.md / user_guide_cn.md

---

## 一、Oracle 总数统计（代码实现）

| DBMS | Oracle 总数 | 完整列表 |
|------|:-----------:|----------|
| **MySQL** | 25 | AGGREGATE, HAVING, GROUP_BY, DISTINCT, NOREC, TLP_WHERE, PQS, CERT, FUZZER, DQP, DQE, EET, EET_UPDATE, EET_DELETE, EET_INSERT_SELECT, CODDTEST, EDC_RADAR, EDC_DATA, SONAR, WRITE_CHECK, WRITE_CHECK_REPRODUCE, FUCCI, TX_INFER, JIR, QUERY_PARTITIONING |
| **PostgreSQL** | 25 | NOREC, PQS, TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, QUERY_PARTITIONING, CERT, FUZZER, DQP, DQE, EET, EET_UPDATE, EET_DELETE, EET_INSERT_SELECT, CODDTEST, SONAR, EDC_RADAR, EDC_DATA, WRITE_CHECK, WRITE_CHECK_REPRODUCE, TX_INFER, FUCCI, JIR |
| **GaussDB-M** | 25 | AGGREGATE, HAVING, GROUP_BY, DISTINCT, NOREC, TLP_WHERE, PQS, CERT, FUZZER, DQP, DQE, EET, EET_INSERT_SELECT, EET_UPDATE, EET_DELETE, CODDTEST, SONAR, EDC_RADAR, EDC_DATA, WRITE_CHECK, WRITE_CHECK_REPRODUCE, FUCCI, TX_INFER, JIR, QUERY_PARTITIONING |
| **GaussDB-A** | 25 | TLP_WHERE, NOREC, QUERY_PARTITIONING, HAVING, AGGREGATE, DISTINCT, GROUP_BY, PQS, CERT, DQP, DQE, EET, EET_INSERT_SELECT, EET_UPDATE, EET_DELETE, CODDTEST, SONAR, EDC_RADAR, EDC_DATA, WRITE_CHECK, WRITE_CHECK_REPRODUCE, TX_INFER, FUCCI, JIR, FUZZER |
| **GaussDB-PG** | 11 | TLP_WHERE, NOREC, QUERY_PARTITIONING, HAVING, AGGREGATE, DISTINCT, GROUP_BY, PQS, CERT*, DQP*, DQE*, FUZZER |

> *CERT/DQP/DQE 在 GaussDB-PG 中为 placeholder（实际返回 TLP_WHERE）

---

## 二、Oracle 专属参数汇总

### 2.1 通用全局参数（MainOptions.java）

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--num-threads` | 16 | 并发测试线程数 |
| `--num-tries` | 100 | 发现多少个错误后停止 |
| `--num-queries` | 100000 | 每个数据库发出的查询数 |
| `--max-num-inserts` | 30 | INSERT 语句数量上限 |
| `--max-expression-depth` | 3 | 随机表达式最大深度 |
| `--random-seed` | -1 | 随机种子（-1=时间基准） |
| `--timeout-seconds` | -1 | 超时秒数（-1=无超时） |
| `--username` | "sqlancer" | DBMS 登录用户名 |
| `--password` | "sqlancer" | DBMS 登录密码 |
| `--host` | null | DBMS 主机地址 |
| `--port` | -1 | DBMS 端口 |
| `--connection-url` | null | 可选 JDBC 连接 URL |
| `--log-each-select` | true | 记录每条 SQL |
| `--log-execution-time` | true | 记录执行时间 |
| `--log-dir` | null | 日志目录（默认 logs/\<dbms\>/\<oracle\>_timestamp/） |
| `--use-reducer` | false | 启用 delta-debugging 缩减 |
| `--reduce-ast` | false | AST 缩减（实验性） |
| `--qpg-enable` | false | 启用查询计划引导 |
| `--print-progress-information` | true | 打印进度信息 |
| `--validate-result-size-only` | false | 只验证结果集大小 |
| `--pqs-test-aggregates` | false | PQS 部分测试聚合函数 |
| `--num-transaction` | 2 | 事务数量 |
| `--num-schedule` | 10 | 调度数量 |
| `--conflict-construction` | true | Troc 风格冲突构造 |
| `--set-case` | false | 使用指定测试用例 |
| `--case-file` | "" | 测试用例文件路径 |

### 2.2 DBMS 专属参数

#### MySQL (MySQLOptions.java)
| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--oracle` | QUERY_PARTITIONING | Oracle 列表 |
| `--engines` | null | 存储引擎列表 |
| `--coddtest-model` | RANDOM | CODDTEST 模式 (RANDOM/EXPRESSION/SUBQUERY) |
| `--fucci-oracle-type` | ALL | FUCCI 类型 (DT/MT/CS/ALL) |
| `--fucci-isolation-level` | RANDOM | FUCCI 隔离级别 |
| `--fucci-schedule-count` | 10 | FUCCI 调度数 |
| 60+ `--test-*` 功能开关 | true | 控制各类 SQL 特性（foreign-keys, joins, window-functions 等） |

#### PostgreSQL (PostgresOptions.java)
| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--oracle` | QUERY_PARTITIONING | Oracle 列表 |
| `--fucci-oracle-type` | ALL | FUCCI 类型 |
| `--fucci-isolation-level` | RANDOM | FUCCI 隔离级别 |
| `--fucci-schedule-count` | 10 | FUCCI 调度数 |
| `--coverage-policy` | BALANCED | 覆盖策略 (BALANCED/CONSERVATIVE/AGGRESSIVE) |
| `--pg-tables` | 3 | 表数量 |
| `--pg-table-columns` | 10 | 每表列数 |
| `--pg-generate-sql-num` | 3 | INSERT 阶段 SQL 预算 |
| `--pg-generate-rows-per-insert` | 3 | 单 INSERT 最大行数 |
| `--pg-index-model` | 0 | 索引模式 (0-6) |
| `--extensions` | "" | 预创建扩展列表 |
| `--test-foreign-keys` | true | 外键组准备 |
| `--bombard` | false | 压力测试模式 |
| `--bombard-workers` | 4 | 压力测试线程数 |

#### GaussDB-M (GaussDBMOptions.java)
| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--oracle` | QUERY_PARTITIONING | Oracle 列表 |
| `--target-database` | null | M 兼容数据库名（必需） |
| `--fucci-oracle-type` | ALL | FUCCI 类型 |
| `--fucci-isolation-level` | RANDOM | FUCCI 隔离级别 |
| `--fucci-schedule-count` | 10 | FUCCI 调度数 |

#### GaussDB-A (GaussDBAOptions.java)
| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--oracle` | QUERY_PARTITIONING | Oracle 列表 |
| `--target-database` | null (required) | A 兼容数据库名（必需） |
| `--enable-clob-blob` | false | 启用 CLOB/BLOB 类型 |
| `--fucci-oracle-type` | ALL | FUCCI 类型 |
| `--fucci-isolation-level` | RANDOM | FUCCI 隔离级别 |
| `--fucci-schedule-count` | 10 | FUCCI 调度数 |

### 2.3 独立 FUCCI 模式 (FucciOptions.java)

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--fucci-oracle` | ALL | FUCCI 类型 (DT/MT/CS/ALL) |
| `--fucci-ref-host` | null | DT Oracle 参照 DB 主机 |
| `--fucci-ref-port` | 0 | DT Oracle 参照 DB 端口 |
| `--fucci-ref-dbms` | null | DT Oracle 参照 DB 类型 |
| `--fucci-ref-user` | "root" | DT Oracle 参照 DB 用户名 |
| `--fucci-ref-password` | "" | DT Oracle 参照 DB 密码 |
| `--fucci-ref-database` | "test" | DT Oracle 参照 DB 数据库名 |
| `--fucci-reducer` | false | 启用 Reducer |
| `--fucci-reducer-type` | "random" | 简化策略 |
| `--fucci-reducer-count` | 5 | 每层最大简化次数 |
| `--fucci-conflict-type` | "random" | 冲突构造策略 |
| `--fucci-isolation` | "RANDOM" | 隔离级别 |
| `--fucci-tx-count` | 2 | 事务数量 |
| `--fucci-schedule-count` | 10 | 调度数量 |
| `--fucci-insert-conflict` | false | INSERT 冲突 |
| `--fucci-filter-duplicate` | false | 过滤重复 Bug |

---

## 三、文档一致性问题

### P0 — 严重错误（影响用户使用）

#### 3.1 `--oracle` 选项描述严重过时（4 个 DBMS 全部受影响）

所有 4 个 DBMS 的 `--oracle` 选项 description 与实际实现严重不匹配：

| DBMS | `--oracle` 描述中的 Oracle | 代码实际实现 | 缺失 |
|------|---------------------------|:----------:|------|
| **MySQL** | 22 个 | 25 个 | EDC_RADAR, EDC_DATA, EET_INSERT_SELECT, EET_UPDATE, EET_DELETE, JIR, SONAR, TX_INFER, WRITE_CHECK_REPRODUCE |
| **PostgreSQL** | 21 个 | 25 个 | EDC_RADAR, EDC_DATA, EET_INSERT_SELECT, EET_UPDATE, EET_DELETE, JIR, SONAR, TX_INFER, WRITE_CHECK_REPRODUCE |
| **GaussDB-M** | 21 个 | 25 个 | EDC_RADAR, EDC_DATA, EET_INSERT_SELECT, EET_UPDATE, EET_DELETE, SONAR, TX_INFER, WRITE_CHECK_REPRODUCE |
| **GaussDB-A** | 18 个 | 25 个 | EDC_DATA, EET_INSERT_SELECT, EET_UPDATE, EET_DELETE, TX_INFER, WRITE_CHECK_REPRODUCE |

**影响**: 用户运行 `--help` 时看不到大部分可用 Oracle，导致功能不可发现。

**修复**: 更新 4 个 Options 类的 `@Parameter(names = "--oracle", description = "...")` 字符串。

---

#### 3.2 ~~GaussDB-A 文档列出 2 个不存在的 Oracle~~ [已修复 v2.7.2]

| Oracle | 文档列出 | 代码实现 | 状态 |
|--------|:--------:|:--------:|:----:|
| EDC_RADAR | ✓ | ✓ | ✅ 已实现并集成测试通过 |
| SONAR | ✓ | ✓ | ✅ 已实现并集成测试通过 |

**修复记录**: v2.7.2 中新增了 GaussDBA SONAR 和 EDC_RADAR 的实现，包括 AST Visitor 修复、Schema 隔离修复等，经过集成测试验证。

---

#### 3.3 GaussDB-PG 文档列出 2 个不存在的 Oracle

GaussDB-PG 文档声称 "Same oracle set as GaussDB-A"，但实际只有 11 个 Oracle。

| Oracle | 文档列出 | 代码实现 |
|--------|:--------:|:--------:|
| EET_UPDATE | ✓ | ✗ **不存在** |
| EET_DELETE | ✓ | ✗ **不存在** |
| EET_INSERT_SELECT | ✓ | ✗ **不存在** |
| WRITE_CHECK | ✓ | ✗ **不存在** |
| WRITE_CHECK_REPRODUCE | ✓ | ✗ **不存在** |
| TX_INFER | ✓ | ✗ **不存在** |
| FUCCI | ✓ | ✗ **不存在** |
| JIR | ✓ | ✗ **不存在** |
| EDC_DATA | ✓ | ✗ **不存在** |
| CODDTEST | ✓ | ✗ **不存在** |
| SONAR | ✓ | ✗ **不存在** |
| EDC_RADAR | ✓ | ✗ **不存在** |

> 注意：CERT、DQP、DQE 在 GaussDB-PG 代码中存在但是 placeholder（返回 TLP_WHERE）。

**修复**: 重写 GaussDB-PG Oracle 表，列出实际的 11 个 Oracle。

---

### P1 — 中等问题（内容不准确但不阻塞使用）

#### 3.4 EDC_DATA 在 MySQL/PostgreSQL Oracle 表中重复列出

**英文 USER_GUIDE.md** (第 121-122 行, 第 198 行):
```
| EDC_DATA | Data operation equivalence (functions, aggregates, predicates) | ~3000 queries/s |
| EDC_DATA | Data operation equivalence (functions, aggregates, predicates) | Moderate |
```

**中文 user_guide_cn.md** (第 145-146 行, 第 226-227 行): 同样重复。

**修复**: 删除重复行，保留一条即可。

---

#### 3.5 TX_INFER 支持数据库列表错误（英文文档）

**英文 USER_GUIDE.md** (第 1039 行):
> Supported DBMS: MySQL, GaussDB-M

**实际代码**: MySQL, PostgreSQL, GaussDB-M, GaussDB-A（4 个 DBMS 全部实现）

**中文 user_guide_cn.md** (第 971 行):
> 支持数据库：MySQL、PostgreSQL、GaussDB-A、GaussDB-M ✓ 正确

**修复**: 英文文档 TX_INFER 部分更新为 4 个 DBMS，并添加 PostgreSQL 和 GaussDB-A 的用法示例。

---

#### 3.6 命名不一致：代码 "EDC_RADAR" vs 文档 "EDC"

| 位置 | 使用名称 |
|------|----------|
| MySQLOracleFactory.java | `EDC_RADAR` |
| PostgresOracleFactory.java | `EDC_RADAR` |
| GaussDBMOracleFactory.java | `EDC_RADAR` |
| `--oracle` 选项描述 | `EDC` |
| 英文文档 Deep Dive 章节 | `EDC_RADAR` |
| 英文文档 Oracle 对比表 | `EDC_RADAR` |
| 英文文档 "Comprehensive Testing" 示例 | `EDC_RADAR` |
| 中文文档 Deep Dive 章节 | `EDC` |
| 中文文档 Oracle 对比表 | `EDC` |
| 中文文档测试场景示例 | `EDC` |

**修复**: 统一为 `EDC_RADAR`。代码枚举名是 `EDC_RADAR`，CLI 选项也应匹配。

---

#### 3.7 全局参数默认值错误（两份文档）

| 参数 | 文档值 | 代码实际默认值 | 影响文档 |
|------|--------|:-------------:|----------|
| `--num-threads` | 1 | **16** | EN + CN |
| `--num-tries` | 1 | **100** | EN + CN |
| `--timeout-seconds` | 120 | **-1** (无超时) | EN + CN |
| `--seed` | — | 参数名应为 `--random-seed` | EN + CN |

---

#### 3.8 全局参数缺失（两份文档）

以下重要全局参数在文档 Global Options 部分完全缺失：

| 缺失参数 | 默认值 | 说明 |
|----------|--------|------|
| `--max-expression-depth` | 3 | 表达式最大深度（影响 EDC_DATA 等） |
| `--max-num-inserts` | 30 | INSERT 语句上限 |
| `--num-queries` | 100000 | 查询数上限 |
| `--random-seed` | -1 | 随机种子 |
| `--max-generated-databases` | -1 | 最大生成数据库数 |
| `--print-failed` | true | 打印失败语句 |
| `--canonicalize-sql-strings` | true | 规范化 SQL 字符串 |
| `--connection-url` | null | JDBC 连接 URL |
| `--jdbc-driver-class` | null | JDBC 驱动类名 |
| `--use-create-database` | false | 使用 CREATE DATABASE 隔离 |

---

### P2 — 轻微问题

#### 3.9 EDC_DATA Oracle 无 Deep Dive 章节

Oracle Reference Guide 中有 EDC_RADAR、SONAR、WRITE_CHECK、TX_INFER、JIR、FUCCI、QPG 的 Deep Dive 章节，但缺少 EDC_DATA 的 Deep Dive 章节（两份文档均缺失）。

**建议**: 添加 EDC_DATA Deep Dive，涵盖：
- 基于 SIGMOD 2026 EDC 论文的方法论
- Seed file 驱动机制（func.txt/agg.txt/pred.txt/type.txt）
- 表达式生成流程（FUNC(c0,c1,...) vs 预计算值）
- 与 EDC_RADAR 的区别（数据操作 vs 约束优化）

---

#### 3.10 FUCCI 独立模式参数未文档化

`FucciOptions.java` 有 16 个参数（包括 `--fucci-ref-host`, `--fucci-reducer`, `--fucci-conflict-type` 等），文档只列出了 3 个嵌入在各 DBMS Options 中的参数（`--fucci-oracle-type`, `--fucci-isolation-level`, `--fucci-schedule-count`）。

---

#### 3.11 Oracle 选择指南表缺失 EDC_DATA

**英文文档** (第 1144 行):
> Comprehensive: QUERY_PARTITIONING + JIR + EDC_RADAR + EET + DQE + WRITE_CHECK

**中文文档** (第 1036 行):
> 全面综合测试：QUERY_PARTITIONING + JIR + EDC + EET + DQE + WRITE_CHECK

两者均缺少 EDC_DATA。建议添加为综合测试的一部分。

---

#### 3.12 英文文档测试场景示例中命令名不一致

**英文文档** (第 1023 行):
```bash
java -jar sqlancer.jar gaussdbm --qpg-enable ...
```

正确命令名应为 `gaussdb-m`（含连字符，来自 `GaussDBMProvider.getDBMSName()` = `"gaussdb-m"`）。

同一文档在其他位置正确使用 `gaussdb-m`。

---

#### 3.13 中文文档 PostgreSQL 数据类型遗漏 INTERVAL

**中文文档** (第 264 行):
> 时间类型：DATE, TIME, TIMETZ, TIMESTAMP, TIMESTAMPTZ, **INTERVAL**

**英文文档** (第 236 行):
> Temporal: DATE, TIME, TIMETZ, TIMESTAMP, TIMESTAMPTZ

英文版本遗漏 INTERVAL。

---

#### 3.14 英文文档版本号历史区域混乱

Version History 区域版本号格式不统一：
- v2.5.6, v2.4.5, v2.4.4, v2.4.3, v2.4.2
- v2.0.60, v2.0.59
- v0.1.85, v0.1.84, v0.1.83, v0.1.82, v0.1.73, v0.1.72, v0.1.70-71

从 v0.1.x → v2.0.x → v2.4.x → v2.5.x 的演进过程未解释。

---

## 四、GaussDB-PG 实际 Oracle 列表

基于 `GaussDBPGOracleFactory.java` 代码，GaussDB-PG 实际有 **11 个有效 Oracle**（CERT/DQP/DQE 为 placeholder）：

| Oracle | 状态 | 说明 |
|--------|------|------|
| TLP_WHERE | ✓ 真实实现 | WHERE 子句分区 |
| NOREC | ✓ 真实实现 | 优化检测 |
| QUERY_PARTITIONING | ✓ 真实实现 | TLP_WHERE + HAVING + AGGREGATE |
| HAVING | ✓ 真实实现 | HAVING 子句分区 |
| AGGREGATE | ✓ 真实实现 | 聚合函数分区 |
| DISTINCT | ✓ 真实实现 | DISTINCT 分区 |
| GROUP_BY | ✓ 真实实现 | GROUP BY 分区 |
| PQS | ✓ 真实实现 | Pivot 查询合成 |
| CERT | ⚠ Placeholder | 返回 TLP_WHERE |
| DQP | ⚠ Placeholder | 返回 TLP_WHERE |
| DQE | ⚠ Placeholder | 返回 TLP_WHERE |
| FUZZER | ✓ 真实实现 | 随机查询生成 |

---

## 五、修复优先级总结

| 优先级 | 问题编号 | 问题摘要 | 影响范围 |
|--------|---------|---------|---------|
| **P0** | 3.1 | `--oracle` 选项描述过时 | 4 个 Options.java 文件 |
| **P0** | 3.2 | GaussDB-A 文档列出 EDC_RADAR/SONAR（不存在） | EN + CN Quick Reference |
| **P0** | 3.3 | GaussDB-PG 文档列出 12 个不存在的 Oracle | EN + CN 表格 |
| **P1** | 3.4 | MySQL/PostgreSQL 表中 EDC_DATA 重复 | EN + CN Oracle 详表 |
| **P1** | 3.5 | TX_INFER 支持 DBMS 列表错误（英文） | EN TX_INFER Deep Dive |
| **P1** | 3.6 | EDC_RADAR vs EDC 命名不一致 | 全部文档 + 选项描述 |
| **P1** | 3.7 | 全局参数默认值错误 | EN + CN Global Options |
| **P1** | 3.8 | 全局参数缺失 10 项 | EN + CN Global Options |
| **P2** | 3.9 | EDC_DATA 缺少 Deep Dive 章节 | EN + CN |
| **P2** | 3.10 | FUCCI 独立模式参数未文档化 | EN + CN |
| **P2** | 3.11 | 选择指南表缺失 EDC_DATA | EN + CN |
| **P2** | 3.12 | 英文文档 gaussdbm → gaussdb-m | EN |
| **P2** | 3.13 | 英文文档遗漏 INTERVAL 类型 | EN |
| **P2** | 3.14 | 版本号格式不统一 | EN + CN |

---

## 六、Oracle 支持矩阵（代码真实状态）

| Oracle | MySQL | PostgreSQL | GaussDB-M | GaussDB-A | GaussDB-PG |
|--------|:-----:|:----------:|:---------:|:---------:|:----------:|
| NOREC | ✓ | ✓ | ✓ | ✓ | ✓ |
| TLP_WHERE | ✓ | ✓ | ✓ | ✓ | ✓ |
| HAVING | ✓ | ✓ | ✓ | ✓ | ✓ |
| AGGREGATE | ✓ | ✓ | ✓ | ✓ | ✓ |
| DISTINCT | ✓ | ✓ | ✓ | ✓ | ✓ |
| GROUP_BY | ✓ | ✓ | ✓ | ✓ | ✓ |
| QUERY_PARTITIONING | ✓ | ✓ | ✓ | ✓ | ✓ |
| PQS | ✓ | ✓ | ✓ | ✓ | ✓ |
| CERT | ✓ | ✓ | ✓ | ✓ | ⚠ |
| DQP | ✓ | ✓ | ✓ | ✓ | ⚠ |
| DQE | ✓ | ✓ | ✓ | ✓ | ⚠ |
| EET | ✓ | ✓ | ✓ | ✓ | ✗ |
| EET_UPDATE | ✓ | ✓ | ✓ | ✓ | ✗ |
| EET_DELETE | ✓ | ✓ | ✓ | ✓ | ✗ |
| EET_INSERT_SELECT | ✓ | ✓ | ✓ | ✓ | ✗ |
| CODDTEST | ✓ | ✓ | ✓ | ✗ | ✗ |
| EDC_RADAR | ✓ | ✓ | ✓ | ✗ | ✗ |
| EDC_DATA | ✓ | ✓ | ✓ | ✓ | ✗ |
| SONAR | ✓ | ✓ | ✓ | ✗ | ✗ |
| WRITE_CHECK | ✓ | ✓ | ✓ | ✓ | ✗ |
| WRITE_CHECK_REPRODUCE | ✓ | ✓ | ✓ | ✓ | ✗ |
| FUCCI | ✓ | ✓ | ✓ | ✓ | ✗ |
| TX_INFER | ✓ | ✓ | ✓ | ✓ | ✗ |
| JIR | ✓ | ✓ | ✓ | ✓ | ✗ |
| FUZZER | ✓ | ✓ | ✓ | ✓ | ✓ |
| **合计** | **25** | **25** | **25** | **22** | **11** |
