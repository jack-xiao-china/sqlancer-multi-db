# SQLancer 用户使用指导

**文档版本**: 2026-04-15  
**参考源码**: [SQLancer GitHub](https://github.com/sqlancer/sqlancer)  
**近期更新（2026-04）**：已对齐 `--help` 输出（见 `sqlancer_help_0413.md`），补充 `--log-dir`/`--validate-result-size-only` 等全局选项说明与日志目录结构；PostgreSQL 新增/补齐 Oracle（`DQP/DQE/EET/CODDTEST/DISTINCT/GROUP_BY/TLP_WHERE` 等）并补充 Postgres 新类型开关（`--enable-time-types/--enable-json/...`）与 `--coverage-policy`；修正快速参考表中 MySQL/PostgreSQL Oracle 列表漏项（如 `EET/CODDTEST`）。

本版本扩展了sqlancer对MySQL、Postgresql、GaussDB-M兼容模式的支持，包括但不限于扩展了多个test Oracle，以及默认支持的数据类型，具体用法和支持范围可参考用户指导docs/USER_GUIDE.md

---
###一、 常用选项

| 选项 | 说明 |
|------|------|
| `--num-threads N` | 线程数 |
| `--oracle <名称>` | 指定 Test Oracle（可多次使用组合多个） |
| `--num-tries N` | 发现 N 个缺陷后退出 |
| `--timeout-seconds N` | 最大运行时间（秒） |
| `--log-dir <目录>` | 日志基目录；未指定时默认写入 `logs/<dbms>/<oracle>_YYYY_MMDD_HHMM/` |
| `--log-each-select=true/false` | 是否记录每条执行语句（默认 true） |
| `--log-execution-time=true/false` | 记录语句执行耗时（需 `--log-each-select=true`，默认 true） |
| `--validate-result-size-only=true/false` | 只校验结果行数、跳过内容比较（默认 false；用于降噪/排障） |
| `--qpg-enable` | 启用 Query Plan Guidance（支持 SQLite、CockroachDB、TiDB、Materialize） |
| `--use-reducer` | 启用实验性 delta-debugging 用例缩减 |
| `mysql --engines=E1,E2,...` | MySQL 专属：仅在建表时使用指定引擎名生成 `ENGINE=...`（支持自定义引擎名） |

---


## 二、各数据库支持的 Test Oracle

以下为按 DBMS 整理的 Oracle 列表与简要说明。命令行使用 `--oracle <名称>` 指定。  
说明：表中 Oracle 名称与大小写按源码枚举保持一致；“默认”以对应 `*Options` 中默认值为准。

### 2.1 MySQL

| Oracle | 说明 |
|--------|------|
| `TLP_WHERE` | TLP WHERE 子句分区（默认） |
| `HAVING` | TLP HAVING 子句分区 |
| `GROUP_BY` | TLP GROUP BY 分区 |
| `AGGREGATE` | TLP 聚合函数分区（COUNT/SUM/MIN/MAX） |
| `DISTINCT` | TLP DISTINCT 分区 |
| `NOREC` | NoREC 优化器检测（可优化与不可优化等价查询结果一致性） |
| `QUERY_PARTITIONING` | TLP_WHERE + HAVING + GROUP_BY + AGGREGATE + DISTINCT + NOREC 组合 |
| `PQS` | 轴心行存在性检查 |
| `CERT` | 基数估计性能检测 |
| `DQP` | 不同执行计划结果一致性 |
| `DQE` | SELECT/UPDATE/DELETE 一致性 |
| `EET` | 等价表达式变换（原查询与变换查询 multiset 比较；**本仓库扩展**） |
| `FUZZER` | 随机 Fuzzer |

**示例**：
```bash
java -jar sqlancer-*.jar mysql --oracle QUERY_PARTITIONING
java -jar sqlancer-*.jar mysql --oracle TLP_WHERE
java -jar sqlancer-*.jar mysql --oracle NOREC
java -jar sqlancer-*.jar mysql --oracle EET
java -jar sqlancer-*.jar mysql --oracle EET --engines=InnoDB
java -jar sqlancer-*.jar mysql --oracle EET --engines=MyCustomEngine
java -jar sqlancer-*.jar mysql --oracle EET --engines=InnoDB,MyISAM,MyCustomEngine
# EET + 实验性缩减：StatementReducer（语句级）与 MySQLEETComponentReducer（AST component 两阶段，需 --use-reducer）
java -jar sqlancer-*.jar --use-reducer mysql --oracle EET
java -jar sqlancer-*.jar mysql --oracle GROUP_BY --oracle DISTINCT
java -jar sqlancer-*.jar mysql --oracle DQP --oracle DQE
```

**MySQL 注意事项**：
- 全面测试建议使用 `--oracle QUERY_PARTITIONING`，可一次覆盖 TLP WHERE/HAVING/GROUP_BY/AGGREGATE/DISTINCT 及 NoREC。
- 推荐 MySQL 8.0+。MySQL 8.4 下已处理 `show_old_temporals` 等不兼容变量的兼容性问题。
- `--engines=...` 是 **MySQL 子命令参数**，需写在 `mysql` 之后；指定后仅生成对应 `ENGINE=xxx` 的建表语句，可用于官方或自定义引擎测试。
- 修改源码后需执行 `mvn clean package -DskipTests` 重新构建后再运行。


### 2.2 PostgreSQL

| Oracle | 说明 |
|--------|------|
| `NOREC` | NoREC 优化器检测 |
| `PQS` | 轴心行存在性检查（更严格，通常要求表非空） |
| `WHERE` / `TLP_WHERE` | TLP WHERE 子句分区（`TLP_WHERE` 为 `WHERE` 的别名入口） |
| `HAVING` | TLP HAVING 子句分区 |
| `AGGREGATE` | TLP 聚合分区 |
| `DISTINCT` | TLP DISTINCT 分区 |
| `GROUP_BY` | TLP GROUP BY 分区 |
| `QUERY_PARTITIONING` | `TLP_WHERE` + `HAVING` + `AGGREGATE` 组合（默认） |
| `CERT` | 基数估计性能检测 |
| `DQP` | 不同执行计划结果一致性 |
| `DQE` | SELECT/UPDATE/DELETE 一致性 |
| `EET` | 等价表达式变换（结果 multiset 比较） |
| `CODDTEST` | 常量驱动等价变换检测 |
| `FUZZER` | 随机 Fuzzer |

**Postgres 新类型开关（2026-04 起）**：用于控制生成器引入更多数据类型（默认关闭，便于灰度降噪）。
- `--enable-time-types=true`：时间类型组（TIME/DATE/TIMESTAMP/INTERVAL）
- `--enable-json=true`：JSON/JSONB
- `--enable-uuid=true`：UUID
- `--enable-bytea=true`：BYTEA
- `--enable-arrays=true`：数组（受限子集）
- `--enable-enum=true`：Enum（会在建表前预创建 enum 对象）
- `--coverage-policy=BALANCED|CONSERVATIVE|AGGRESSIVE`：覆盖策略（默认 BALANCED）
- `--enable-newtypes-in-dqe-dqp-eet=true`：允许新类型进入 DQE/DQP/EET 相关路径（best-effort；PQS strict 路径仍会忽略）

### 2.3 GaussDB-M（M-Compatibility）

| Oracle | 说明 |
|--------|------|
| `TLP_WHERE` | TLP WHERE 子句分区 |
| `HAVING` | TLP HAVING 子句分区 |
| `GROUP_BY` | TLP GROUP BY 分区 |
| `AGGREGATE` | TLP 聚合函数分区 |
| `DISTINCT` | TLP DISTINCT 分区 |
| `NOREC` | NoREC 优化器检测 |
| `QUERY_PARTITIONING` | TLP_WHERE + HAVING + GROUP_BY + AGGREGATE + DISTINCT + NOREC 组合（默认） |
| `PQS` | 轴心行存在性检查 |
| `CERT` | 基数估计性能检测 |
| `DQP` | 不同执行计划结果一致性 |
| `DQE` | SELECT/UPDATE/DELETE 一致性 |
| `EET` | 等价表达式变换（结果 multiset 比较） |
| `CODDTEST` | 常量驱动等价变换检测 |
| `FUZZER` | 随机 Fuzzer |

**示例**：

```bash
java -jar sqlancer-*.jar gaussdb-m --oracle QUERY_PARTITIONING
java -jar sqlancer-*.jar gaussdb-m --oracle NOREC
java -jar sqlancer-*.jar gaussdb-m --oracle TLP_WHERE
java -jar sqlancer-*.jar gaussdb-m --oracle EET
java -jar sqlancer-*.jar gaussdb-m --oracle DQP --oracle DQE
```

---

## 三、Oracle 快速参考表

| DBMS | 可用 Oracle 列表 |
|------|------------------|
| MySQL | TLP_WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, CODDTEST, EET, FUZZER |
| TiDB | WHERE, HAVING, QUERY_PARTITIONING, CERT, DQP |
| PostgreSQL | NOREC, PQS, WHERE, TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, QUERY_PARTITIONING, CERT, DQP, DQE, EET, CODDTEST, FUZZER |
| SQLite3 | NoREC, WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, QUERY_PARTITIONING, PQS, CODDTest, FUZZER |
| MariaDB | NOREC, DQP |
| CockroachDB | NOREC, WHERE, HAVING, AGGREGATE, GROUP_BY, DISTINCT, EXTENDED_WHERE, QUERY_PARTITIONING, CERT |
| DuckDB | NOREC, WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, QUERY_PARTITIONING |
| Databend | NOREC, WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, QUERY_PARTITIONING, PQS |
| Doris | NOREC, WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, QUERY_PARTITIONING, PQS, ALL |
| OceanBase | TLP_WHERE, NoREC, PQS |
| Materialize | NOREC, WHERE, HAVING, QUERY_PARTITIONING, PQS |
| H2 | TLP_WHERE |
| Hive | TLPWhere |
| HSQLDB | WHERE, NOREC |
| ClickHouse | TLPWhere, TLPDistinct, TLPGroupBy, TLPAggregate, TLPHaving, NoREC |
| CnosDB | NOREC, HAVING, QUERY_PARTITIONING |
| Citus | NOREC, WHERE, HAVING, QUERY_PARTITIONING, PQS |
| DataFusion | NOREC, QUERY_PARTITIONING_WHERE |
| Presto | NOREC, WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, QUERY_PARTITIONING |
| QuestDB | WHERE |
| YugabyteDB YSQL | NOREC, HAVING, QUERY_PARTITIONING, PQS, FUZZER, CATALOG |
| YugabyteDB YCQL | FUZZER |

---


## 四、各 Test Oracle 作用与引入日期

以下按时间顺序列出 SQLancer 中各类 Test Oracle 的作用、引入年份与论文来源。

| Oracle 名称 | 全称 | 引入 | 作用 | 论文/链接 |
|-------------|------|------|------|-----------|
| **PQS** | Pivoted Query Synthesis | 2020 | 随机选取“轴心行”，生成保证能选中该行的查询；若结果不包含该行则判定逻辑错误。当前未维护。 | [OSDI 2020](https://www.usenix.org/system/files/osdi20-rigger.pdf) |
| **NoREC** | Non-optimizing Reference Engine Construction | 2020 | 将可被优化的查询改写为几乎不被优化的等价查询，比较两者结果；不一致则表明优化器逻辑错误。主要针对带过滤谓词的简单查询。 | [ESEC/FSE 2020](https://arxiv.org/abs/2007.08292) |
| **TLP (WHERE/HAVING/AGGREGATE/DISTINCT/GROUP_BY)** | Ternary Logic Partitioning | 2020 | 将查询按三值逻辑拆成三个分区查询，组合结果与原查询比较；不一致则判定逻辑错误。可覆盖聚合、DISTINCT、GROUP BY、HAVING 等高级特性。 | [OOPSLA 2020](https://dl.acm.org/doi/pdf/10.1145/3428279) |
| **DQE** | Differential Query Execution | 2023 | 对同一谓词 φ 分别执行 SELECT、UPDATE、DELETE，检查结果是否一致（如被 UPDATE 的行应出现在同谓词 SELECT 中）。仅支持 MySQL。 | [ICSE 2023](https://ieeexplore.ieee.org/document/10172736)、[PR #1251](https://github.com/sqlancer/sqlancer/pull/1251) |
| **QPG** | Query Plan Guidance | 2023 | 非独立 Oracle，而是测试**输入生成策略**。以查询计划覆盖为反馈，长时间无新计划时变异数据库状态。需配合 TLP/NoREC 使用。启用：`--qpg-enable`。 | [ICSE 2023](https://arxiv.org/pdf/2312.17510)、[Issue #641](https://github.com/sqlancer/sqlancer/issues/641) |
| **CERT** | Cardinality Estimation Restriction Testing | 2024 | 通过基数估计不一致发现**性能问题**。从给定查询派生更严格查询，其估计基数应 ≤ 原查询；违反则视为潜在性能缺陷。 | [ICSE 2024](https://arxiv.org/pdf/2306.00355)、[Issue #822](https://github.com/sqlancer/sqlancer/issues/822) |
| **DQP** | Differential Query Plans | 2024 | 控制同一查询的**不同执行计划**，比较各计划下的结果是否一致；不一致则判定逻辑错误。 | [SIGMOD 2024](https://dl.acm.org/doi/pdf/10.1145/3654991)、[Issue #918](https://github.com/sqlancer/sqlancer/issues/918) |
| **CODDTest** | Constant Optimization Driven Database System Testing | 2025 | 利用常量折叠/常量传播等价替换部分查询，比较替换前后结果；可覆盖子查询等高级特性中的逻辑错误。 | [SIGMOD 2025](https://github.com/sqlancer/sqlancer/pull/1054) |
| **EET** | Equivalent Expression Transformation | 2024 | 对表达式做语义保持变换后比较原查询与变换查询结果（multiset）；**本仓库 MySQL 扩展**，上游主线未必包含。 | [OSDI 2024](https://www.usenix.org/conference/osdi24/presentation/jiang) |
| **FUZZER** | Random Fuzzer | — | 随机生成 SQL 并执行，不进行等价性校验；用于发现崩溃、内部错误等。 | 内置 |
| **CATALOG** | Catalog Oracle | — | YugabyteDB YSQL 专用，用于验证系统目录一致性。 | 内置 |

---

#### 4.1.1 EET Oracle（本仓库扩展）— 功能、用法与七条规则

**论文**：Jiang et al., OSDI 2024 — *Detecting Logic Bugs in Database Engines via Equivalent Expression Transformation*。EET 通过 **语义等价** 的表达式改写生成查询 \(Q'\)，与原始查询 \(Q\) 在同一数据库状态下执行，若 **结果 multiset** 不一致则怀疑引擎逻辑错误（形式化见设计文档 §1.4）。

##### （1）功能与特性清单

| 类别 | 能力说明 |
|------|----------|
| **核心预言** | 随机生成 `SELECT`（含 **plain / UNION / WITH(CTE) / 派生表** 等形态），对 AST 做等价变换得到 \(Q'\)，比较两次查询结果的 **multiset 相等性**；二次执行仍不一致则上报潜在逻辑缺陷。 |
| **递归变换（§3.2）** | 对 `CASE`、布尔/算术子表达式、`IN`、`EXISTS`、函数参数等 **先递归子树再变换根节点**，贴近 EET-main 的复合表达式处理顺序。 |
| **JOIN / 子查询** | `JOIN ON`、FROM 中 **派生表**、**CTE** 内外层 `SELECT` 均参与遍历与变换（外层 CTE/派生列通过 `MySQLText` 限定列名，避免引用基表）。 |
| **执行容错** | 执行失败（语法/语义错误等）视为 **IgnoreMe** 跳过当次尝试，不将未列入白名单的错误当作 AssertionError 中断（适配随机 SQL）。 |
| **缩减（§六）** | 启用 `--use-reducer` 时，`MySQLEETReproducer` 可在 `bugStillTriggers` 中执行 **两阶段 component 缩减**（原 AST 节点 NULL 替换 + 变换后 CASE 剥离尝试），与全局 **StatementReducer**（语句级 delta-debugging）叠加。 |
| **隔离** | EET **默认不**加入 `QUERY_PARTITIONING`；需显式 `--oracle EET`。不修改既有 TLP/NoREC 等 Oracle 的默认组合。 |

##### （2）命令行用法速查

| 场景 | 示例命令 |
|------|----------|
| 仅启用 EET | `java -jar sqlancer-*.jar mysql --oracle EET` |
| 指定连接与时长 | `java -jar sqlancer-*.jar --host <h> --port 3306 --username <u> --password <p> --num-tries 9 --timeout-seconds 600 --num-threads 2 mysql --oracle EET` |
| EET + 实验性缩减 | `java -jar sqlancer-*.jar --use-reducer mysql --oracle EET` |
| 记录每条 SELECT | 在 **cmd.exe** 或 `--log-each-select=true`（PowerShell 需正确引用布尔参数，避免吞掉后续参数） |

**注意**：全局参数须写在 **`mysql` 之前**；`--oracle EET` 写在 **`mysql` 之后**。

##### （3）七条等价变换规则：原理与适用场景

实现集中于 `sqlancer.mysql.oracle.eet.MySQLEETTransformer`（`transformBoolExpr` / `transformValueExpr` / `tryConstBoolTransform` 等）。下列 **Rule 1–7** 与论文 Table 2、EET-main 中 `bool_expr` / `value_expr` 对应。

| 规则 | 名称（论文） | 核心形式（直觉） | 原理要点 | 典型适用场景 |
|------|----------------|------------------|----------|----------------|
| **Rule 1** | Determined Boolean | `B → false_expr OR B` | `false_expr(p) = (p AND NOT p AND p IS NOT NULL)` 在 SQL 三值逻辑下为 **恒假**，故 `false_expr OR B` 与 `B` 同真值。 | **布尔型** WHERE/HAVING/JOIN ON、子表达式为 **布尔** 时，放大「假支」不改变整体真假。 |
| **Rule 2** | Determined Boolean | `B → true_expr AND B` | `true_expr(p) = (p OR NOT p OR p IS NULL)` 为 **恒真**，故 `true_expr AND B` 与 `B` 等价。 | 同上，通过「真支」与 `B` 做 **AND** 连接。 |
| **Rule 3** | Redundant Branch | `E → CASE WHEN false_expr THEN R ELSE E END` | `WHEN` 永假，从不走 THEN，整体等价于 **ELSE 分支 `E`**。 | **非布尔** 标量表达式（列、算术、函数等）：用冗余 `CASE` 包裹，检验优化器/CASE 语义。 |
| **Rule 4** | Redundant Branch | `E → CASE WHEN true_expr THEN E ELSE R END` | `WHEN` 永真，始终取 **THEN 的 `E`**，与 `R` 无关。 | 同上；`R` 需与 `E` **类型一致**（实现中通过 `generateSameTypeAs` 等约束）。 |
| **Rule 5** | Redundant Branch | `E → CASE WHEN rb THEN copy(E) ELSE E` | `rb` 为随机布尔；`copy(E)` 为 **打印再嵌入**（`MySQLPrintedExpression`），与 `E` 同语义。 | 混合 **随机分支** 与 **拷贝等价**，覆盖 `CASE` 与字符串化子表达式交互。 |
| **Rule 6** | Redundant Branch | `E → CASE WHEN rb THEN E ELSE copy(E)` | 与 Rule 5 对称，THEN/ELSE 上 **E** 与 **copy(E)** 位置互换。 | 同上，增加执行路径差异（不同分支求值顺序）。 |
| **Rule 7** | 保守（不变换） | `E → E` | **表引用**、CTE/派生表外层脚手架 **`MySQLText`** 等不参与等价套壳，以免破坏作用域或列绑定。 | **FROM 纯表名**、**仅字符串嵌入的列引用** 等；与论文「不适用 CASE 的节点」一致。 |

**补充：const_bool 特例（0/1 布尔常量）**  
对 **整型 0/1** 布尔常量，实现中等价于在 Rule 1/2 思路上扩展：`1 → 1 OR extend`、`0 → 0 AND extend`（`extend` 为随机布尔表达式），避免对 **NULL** 常量强行套 CASE。

**规则选择（实现策略）**  
- 若当前节点 **像布尔**（`AND/OR/NOT`、比较、`IN`、`EXISTS` 等）：随机选 **Rule 1 或 2**（`transformBoolExpr`），或以一定概率走 **Rule 3–6**（`transformValueExpr`）。  
- 否则走 **Rule 3–6**（`transformValueExpr`）。  
- **Rule 7** 由遍历层跳过或 `isRule7NoChange` 判定，不套新 CASE。

##### （4）团队使用建议

1. **何时选用 EET**  
   - 需要针对 **表达式求值、CASE、布尔化简、NULL/三值逻辑** 与 **优化器对等价式** 的差异做持续fuzz时，在 MySQL 上 **单独或额外** 启用 `--oracle EET`。  
   - **Regression**：发版前应用 **默认 Oracle 组合**（不含 EET）跑一轮；EET 作为 **增量/专项** 任务单独配置 CI 或定时任务即可。

2. **与 TLP / NoREC 的关系**  
   - EET 验证的是 **同一查询的等价改写**，TLP 验证的是 **三分区组合与原查询**；二者 **正交**，可轮换使用，不建议在未评估负载前把 EET 并入 `QUERY_PARTITIONING`。

3. **结果解读**  
   - **Multiset 不一致** 且二次验证仍不一致：按 **潜在逻辑缺陷** 处理，可用 `--use-reducer` 缩小语句与（在实现可用时）AST 组件。  
   - **大量 IgnoreMe**：常见于随机 SQL 不可执行，属正常；若比例异常，检查连接、权限、SQL 模式与 MySQL 版本。

4. **参数与稳定性**  
   - 长时任务建议设 `--timeout-seconds` 与合理 `--num-threads`；结果行数过大时 Oracle 会 **IgnoreMe**（见设计文档 `MAX_PROCESS_ROW_NUM`）。  
   - **浮点/字符串** 格式化可能导致 multiset 字符串比较边界问题；若怀疑假阳性，对照设计文档 §4.5 与论文讨论。

5. **开发与扩展**  
   - 新增变换规则须保持 **\(E \equiv E'\)**（方言语义下），并在 `sqlancer.mysql.oracle.eet` 内扩展；避免修改共享 Oracle 的默认行为（见设计文档 §1.3）。

##### （5）延伸阅读

- 设计实现对照与模块清单：[sqlancer_eet_design_0319.md](sqlancer_eet_design_0319.md)  
- 论文与 EET-main 分析：[eet_analyze.md](eet_analyze.md)（若仓库中存在）

## 五、QPG 支持范围

启用 `--qpg-enable` 时，以下 DBMS 可与 TLP/NoREC 配合使用反馈引导式输入生成：

- SQLite3  
- CockroachDB  
- TiDB  
- Materialize  

---

## 六、日志与复现

- **默认日志目录**：`logs/<dbms>/<oracle>_YYYY_MMDD_HHMM/`
  - 例：`logs/mysql/eet_2026_0409_1453/`
  - 可通过 `--log-dir <目录>` 改为自定义基目录（目录结构仍保持 `<base>/<dbms>/<run>/`）
- **主要文件**：
  - `<database>.log`：异常栈与可复现状态（发生错误/断言时写入）
  - `<database>-cur.log`：每条执行 SQL（需 `--log-each-select=true`，默认 true）
  - `<database>-plan.log`：QPG 计划日志（需 `--qpg-enable` 且 `--qpg-log-query-plan=true`）
  - `reduce/<database>-reduce.log`：缩减日志（需 `--use-reducer`）
  - `reproduce/<database>.ser`：序列化复现状态（需 `--serialize-reproduce-state=true`）

---

## 七、参考链接

- [SQLancer GitHub](https://github.com/sqlancer/sqlancer)
- [SQLancer README](https://github.com/sqlancer/sqlancer/blob/main/README.md)
- [CONTRIBUTING](https://github.com/sqlancer/sqlancer/blob/main/CONTRIBUTING.md)
- [Papers and .bib](https://github.com/sqlancer/sqlancer/blob/main/docs/PAPERS.md)
- [发现缺陷汇总](https://www.manuelrigger.at/dbms-bugs/)
