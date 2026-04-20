# Release Notes

## v0.1.67 | 2026-04-20
- 优化 PostgreSQL 查询加锁策略：删除冗余的 `WHERE` oracle 别名，仅保留 `TLP_WHERE`；将原先基于 oracle 名称的 `FOR ...` 锁子句过滤改为统一的查询形状/执行上下文判定，避免在 `NOREC`、`CERT`、`EET` 等不适合的路径上错误注入行锁，同时保留直接查询路径上的锁覆盖能力
- 修复日志目录按分钟切分问题：单次 SQLancer 运行内复用同一个 run 目录，不再因跨分钟新建日志文件夹，确保一次运行的全部数据库日志落到同一目录；同步更新 `--log-dir` 说明与相关回归测试

## v0.1.66 | 2026-04-18
- 新增 SQLancer PostgreSQL 代码线收敛：将 `sqlancer-pg` 自 `e0d924b6...` 之后的 PostgreSQL 增强按 `sqlancer-multi-db` 架构手工回合，保留本仓库既有 oracle 体系（含 `DISTINCT/GROUP_BY/DQP/DQE/EET/CODDTEST`）不回退，并逐步替代独立的 `sqlancer-pg` 项目作为唯一 PostgreSQL 主线
- 优化 PostgreSQL 参数归属：将 pg 专属配置从全局层收口到 `PostgresOptions`，新增 `--pg-table-columns`、`--pg-generate-sql-num`、`--pg-index-model`（整型模式值）并接通 `PostgresTableGenerator` / `PostgresInsertGenerator` / `PostgresIndexGenerator`；移除旧的按类型开关式参数（如 `--enable-time-types`、`--enable-json`、`--enable-newtypes-in-dqe-dqp-eet`），改为由当前 PostgreSQL 生成器默认覆盖
- 新增 PostgreSQL 查询与类型能力：补齐 `FOR UPDATE/NO KEY UPDATE/SHARE/KEY SHARE` 与 `NOWAIT/SKIP LOCKED` 锁子句生成与序列化；引入 temporal AST（`PostgresTemporalFunction`、`PostgresTemporalBinaryArithmeticOperation`、`PostgresTemporalUtil`）并打通 `TIMETZ`、`DATE_TRUNC/DATE_PART/EXTRACT/MAKE_INTERVAL/TIMEZONE` 等时间类型链路；增强 schema 元数据（constraint/index/statistics/compound type）与索引模型生成能力
- 修复 PostgreSQL 长跑稳定性：修复 `MainOptions.logExecutionTime()` 与 `--log-each-select` 的断言冲突；修复 `PostgresCODDTestOracle` 的空 `ExpectedErrors`、错误 folding 语义与布尔常量上下文不一致问题；修复 `PostgresInOperation` 在 expected-value 比较中的 NPE；调整 PostgreSQL 建库入口统一连接 `postgres` 数据库、简化 `CREATE DATABASE` 语句并将 tablespace 测试默认值收敛为各 OS 默认关闭；补充 `.settings/org.eclipse.jdt.core.prefs` 以恢复标准 `mvn -DskipTests compile/package`
- 验证 PostgreSQL 真实连库运行：使用 `java -jar target/sqlancer-2.0.0.jar` 直连本地 PostgreSQL 16.4 验证，常规 smoke 下 pg oracle 全量可运行；并完成高并发长跑验证，其中 `QUERY_PARTITIONING`、`DQE`、`CODDTEST` 已在 `--num-threads 10 --num-tries 10 --num-queries 10000` 下通过

## v0.1.65 | 2026-04-14
- 修复 SQLancer PostgreSQL：`PostgresExpressionGenerator` 在通用表达式分支对 ENUM/数组类型生成 `CAST(... AS …)` 时调用 `getCompoundDataType` 未覆盖这些类型导致 `AssertionError`（AGGREGATE 等 oracle 可复现）；对上述类型在该分支改为常量生成，与首阶段保守策略一致

## v0.1.64 | 2026-04-13
- 优化 SQLancer PostgreSQL Milestone6：默认在 DQE/DQP/EET/CODDTEST 路径下关闭新增类型组（时间/JSON/UUID/bytea/数组/enum），仅在 `--enable-newtypes-in-dqe-dqp-eet=true` 时允许新类型进入，以降低误报与不稳定性；PQS strict 仍保持排除新类型

## v0.1.63 | 2026-04-13
- 新增 SQLancer PostgreSQL Milestone4+5：最小可编译闭环支持数组（1 维、受限元素类型：`integer[]/text[]/uuid[]/timestamptz[]`，`ARRAY[...]::type[]` 常量生成，`--enable-arrays=true` 控制，PQS strict 排除）与 enum（建表前创建 `e0/e1` 与 labels，DDL 类型输出可引用，常量生成 `'a'::e0`，schema 读取阶段保守降级/跳过以稳定运行，`--enable-enum=true` 控制）

## v0.1.62 | 2026-04-13
- 新增 SQLancer PostgreSQL Milestone3：支持 JSON/JSONB（扩展 `PostgresDataType`、DDL 类型输出 `json/jsonb`、information_schema 映射识别、稳定合法 JSON 常量生成（显式 `::jsonb`/`::json` cast）以及 json 相关 expected errors）；并确保仅在 `--enable-json=true` 时进入且 PQS strict（`generateOnlyKnown`）排除

## v0.1.61 | 2026-04-13
- 新增 SQLancer PostgreSQL Milestone1：打通时间类型 `date/time/timestamp/timestamptz/interval` 的全链路最小闭环（DDL 类型输出、information_schema 映射、safe 常量生成（显式 `::timestamptz` 等 cast）、最小表达式支持与 expected errors 增补）；并确保 balanced 默认进入而 PQS strict（`generateOnlyKnown`）不进入新类型

## v0.1.60 | 2026-04-13
- 新增 SQLancer PostgreSQL Milestone0 基础开关：`PostgresOptions` 增加类型组开关（`--enable-time-types/--enable-json/--enable-uuid/--enable-bytea/--enable-arrays/--enable-enum`）、覆盖率策略 `--coverage-policy=balanced|conservative|aggressive`（默认 balanced）、以及 `--enable-newtypes-in-dqe-dqp-eet`（默认 false）；并在 `PostgresProvider.createDatabase()` 为测试连接增加会话确定性设置（`TimeZone/DateStyle/IntervalStyle`），保留既有 `lc_messages` best-effort 逻辑

## v0.1.59 | 2026-04-13
- 修复 SQLancer PostgreSQL 随机生成的非法组合/崩溃：禁止 UNLOGGED/TEMP 表生成分区；TEMP 表不生成 WITH(storage parameters)；布尔 CAST 仅使用可解析字面量；EXCLUDE 约束不再随机拼接不匹配的 opclass；GlobalState 执行阶段对 null Query 做 IgnoreMe 防 NPE；SQLQueryAdapter 增加常见 SQLSTATE 兜底匹配以适配本地化错误信息，确保 PostgreSQL 全量 oracle（NOREC/PQS/TLP/HAVING/AGGREGATE/DISTINCT/GROUP_BY/QUERY_PARTITIONING/CERT/FUZZER/DQP/DQE/EET/CODDTEST）可稳定运行且工具不报错

## v0.1.58 | 2026-04-10
- 优化 文档：更新 `sqlancer_pg_oracle_supported_0410.md` 的 PostgreSQL oracle 现状盘点与实现定位，确保与当前 `PostgresOracleFactory` 支持项一致

## v0.1.57 | 2026-04-10
- 优化 PostgreSQL AST 类型信息：为 `PostgresColumnReference`/`PostgresOrderByTerm`/`PostgresOracleExpressionBag` 补齐 `getExpressionType()` 推断，提升 EET 等价变换的“同类型值表达式”覆盖率；回归 `TestPostgresEET`

## v0.1.55 | 2026-04-10
- 优化 SQLancer help：在各 DBMS 命令的 `--oracle` 帮助信息中补充可选 test oracle 列表，便于运行时快速查阅（覆盖 mysql/postgres/gaussdb-m 等）

## v0.1.56 | 2026-04-10
- 增量增强 PostgreSQL EET value-expression 覆盖率：当表达式类型未知时也允许用“同分支 CASE WHEN”做等价包裹，提升 Rule 1–7 变换触发率并保持 reducer/reproducer 兼容；回归 `TestPostgresEET`

## v0.1.55 | 2026-04-10
- 增量补齐 PostgreSQL EET Rule 1–7：新增 `PostgresCaseWhen`/`PostgresPrintedExpression`、实现 `PostgresEETExpressionTree` + `PostgresEETComponentReducer` + 可最小化的 `PostgresEETReproducer`，将 `PostgresEETTransformer` 扩展为递归 + CASE WHEN 等价变换，保持与 MySQL/GaussDBM 同形态框架

## v0.1.54 | 2026-04-10
- 升级 SQLancer PostgreSQL 的 EET/CODDTEST 为框架化实现：新增 Postgres EET AST 支撑（CTE/UNION/Derived/Text 节点与 visitor 输出）、`sqlancer.postgres.oracle.ext.eet` 的 QueryGenerator/Transformer/Executor/Reproducer，并将 CODDTEST 调整为基于 `CODDTestBase` 的框架形态（保留隔离包结构与单测覆盖）

## v0.1.53 | 2026-04-10
- 新增 SQLancer PostgreSQL oracle 扩展：`PostgresOracleFactory` 补齐 `TLP_WHERE`/`AGGREGATE`/`DISTINCT`/`GROUP_BY`/`DQP`/`DQE`/`EET`/`CODDTEST` 并将新增实现隔离在 `sqlancer.postgres.oracle.ext`；补充对应 Postgres 单元测试与修复 `TestPostgresQueryPlan` 环境开关；新增开发文档 `sqlancer_pg_oracle_supported_0410.md`

## v0.1.52 | 2026-04-10
- 优化 SQLancer GaussDB-M Phase5：`GaussDBMOracleFactory` 枚举顺序与 `MySQLOracleFactory` 对齐（`QUERY_PARTITIONING` 置末）；`CERT` 与 `NOREC`/`TLP_WHERE` 一致使用 `GaussDBMErrors.getExpressionErrors()`；扩展 `GaussDBMOracleIsolationTest`（14 个 oracle 名称、`requiresAllTablesToContainRows` 断言、实现类映射防「全委托 TLP_WHERE」回归）；更新 `docs/gaussdb-smoke.md`（`gaussdb-m`、完整 oracle 列表）与 `scripts/run-gaussdb-oracles-smoke.ps1`（`gaussdb-m` 与 14 oracle）

## v0.1.51 | 2026-04-10
- 新增 SQLancer GaussDB-M Phase4：`sqlancer.gaussdbm.oracle.eet`（EET：`GaussDBMEETOracle` 与 QueryGenerator/Transformer/ExpressionTree/ComponentReducer/Reproducer/DefaultQueryExecutor 等，对齐 MySQL EET 行为）、`GaussDBCODDTestOracle`；补充 EET/CODD 所需 AST（`GaussDBText`、`GaussDBUnionSelect`、`GaussDBWithSelect`、`GaussDBCteDefinition`、`GaussDBCteTableReference`、`GaussDBDerivedTable`、`GaussDBPrintedExpression`、`GaussDBOracleAlias`、`GaussDBOracleExpressionBag`）及 `GaussDBToStringVisitor` 输出；`GaussDBMOracleFactory` 增加 `EET`、`CODDTEST`（`requiresAllTablesToContainRows` 与 MySQL 一致）；`QUERY_PARTITIONING` 保持 `CompositeTestOracle` + 基数/行集不一致断言转 `IgnoreMeException`

## v0.1.50 | 2026-04-10
- 新增 SQLancer GaussDB-M Phase3：`GaussDBMPivotedQuerySynthesisOracle`（PQS）、`CERTOracle` + `GaussDBMCERTExplainParser`（按 EXPLAIN 列名解析 `rows` / `type` 等）、`GaussDBMDQPOracle`、`GaussDBMDQEOracle`、`GaussDBMFuzzer` + `GaussDBMRandomQuerySynthesizer`；`GaussDBMOracleFactory` 独立接线并覆写 `PQS`/`CERT` 的 `requiresAllTablesToContainRows`；补充表达式 `getExpectedValue`、`GaussDBExpectedValueVisitor`、`GaussDBMErrors.getInsertUpdateErrors`；`SQLancerResultSet` 暴露 `getMetaData()` 供 EXPLAIN 列定位

## v0.1.49 | 2026-04-10
- 新增 SQLancer GaussDB-M Phase2：`GaussDBMTLPAggregateOracle` / `GaussDBMTLPHavingOracle` / `GaussDBMTLPGroupByOracle` / `GaussDBMTLPDistinctOracle`；`GaussDBMOracleFactory` 独立接线 `AGGREGATE`/`HAVING`/`GROUP_BY`/`DISTINCT`/`NOREC`/`TLP_WHERE`，`QUERY_PARTITIONING` 组合上述与 MySQL 一致的 `CompositeTestOracle` + 基数不匹配转 `IgnoreMeException`

## v0.1.48 | 2026-04-10
- 扩展 SQLancer GaussDB-M Phase1：`GaussDBAggregate`/`GaussDBIfFunction`/`GaussDBCaseWhen`、`GaussDBSelect` 的 DISTINCT/ALL、`GaussDBToStringVisitor`（含列别名 refN、OFFSET、聚合与 IF/CASE）、`GaussDBMErrors`（`getExpressionHavingErrors` 等）；`GaussDBMExpressionGenerator` 实现 `NoRECGenerator`/`CERTGenerator` 与随机 JOIN；新增 `GaussDBMTLPBase`（TLP 基类）

## v0.1.47 | 2026-04-09
- 新增 SQLancer GaussDB JDBC 驱动集成：将 `src/sqlancer/gaussdb/gsjdbc4.jar` 作为本地 system 依赖接入，并在打包时复制到 `target/lib/`，确保运行 `gaussdb-m` 时可加载且不影响其它 DBMS

## v0.1.46 | 2026-04-09
- 新增 SQLancer GaussDB-M 最小回归单测：覆盖 `gaussdb-m` 命令可见性、`--oracle` 枚举隔离与日志目录 `logs/<dbms>/<run>/` 分桶创建
- 修复 SQLancer 单测在高版本 JDK 下的 JaCoCo 兼容性：将 `jacoco-maven-plugin` 升级到 `0.8.14`，避免 `Unsupported class file major version 69`

## v0.1.45 | 2026-04-09
- 新增 SQLancer GaussDB-M 命令入口：新增 `sqlancer.gaussdbm` Provider/Options/GlobalState/Schema（以及对应 oracle/generator/visitor/ast 最小闭环），CLI 命令固定为 `gaussdb-m`，并移除旧 `gaussdb` 命令注册入口

## v0.1.44 | 2026-04-09
- 新增 GaussDB M-Compatibility SQLancer 第一阶段最小 SQL 子集白名单与禁用项清单：提供 `references/gaussdb-m-compatibility/sqlancer-phase1-sql-subset.md`，用于白名单驱动生成与后续渐进扩展

## v0.1.42 | 2026-04-09
- 优化 GaussDB M-Compatibility 团队参考：补充按语法类别的约束限制与使用场景文档（`references/gaussdb-m-compatibility/constraints-by-category.md`、`usage-scenarios.md`），用于支撑团队分享与快速落类

## v0.1.43 | 2026-04-09
- 优化 GaussDB M-Compatibility skill 覆盖范围：从仅第4~7章扩展为全量第1~7章（整本），补充第1~3章范围索引与接入/创建相关约束摘要（`constraints-ch1-3.md`）

## v0.1.41 | 2026-04-09
- 新增 GaussDB M-Compatibility 团队共享参考：提供 `references/gaussdb-m-compatibility/`，沉淀基础语法范围与关键约束摘要，用于在不直接携带原始语法文档时的分享与共识对齐

## v0.1.40 | 2026-04-09
- 新增 GaussDB M-Compatibility 兼容性 skill：提供 `superpowers-main/skills/gaussdb-m-compatibility/`，按渐进式披露输出兼容性结论，并要求回答带章节与页码引用（范围：文档第4~7章）

## v0.1.39 | 2026-04-09
- 优化 清理调试临时产物：清空 `sqlancer-main/sqlancer-main/logs/`，清理 `sqlancer-main/sqlancer-main/target/` 构建输出，并移除运行生成的 `database*.tmp` 临时目录

## v0.1.38 | 2026-04-09
- 新增 Linux 可运行的 MySQL oracles smoke 脚本：提供 `scripts/run-mysql-oracles-smoke.sh`（bash），用于在 Linux 环境逐个执行 MySQL 全部 `--oracle`，并按“允许发现 DB bug / 禁止工具非法错误”规则输出汇总报告

## v0.1.37 | 2026-04-09
- 修复 MySQL oracles smoke 脚本判定逻辑：将除工具内部非法错误（如 `SQLSyntaxErrorException`、NPE、`AssertionError: DATE`）以外的 `AssertionError` 统一归类为“发现 DB bug（允许）”，并修正输出字段避免误判导致未能正确阻断 `TOOL_ILLEGAL`

## v0.1.36 | 2026-04-09
- 新增 MySQL oracles 工具健康检查脚本：提供 `scripts/run-mysql-oracles-smoke.ps1`，逐个执行 MySQL 全部 `--oracle`，允许发现数据库逻辑错误（AssertionError mismatch），但将非法 SQL/工具崩溃（如 `SQLSyntaxErrorException`、NPE、`AssertionError: DATE`）判为失败，并输出汇总报告

## v0.1.35 | 2026-04-09
- 调整日志目录结构：默认日志路径由 `logs/<oracle>_YYYY_MMDD_HHMM/<dbms>/` 改为 `logs/<dbms>/<oracle>_YYYY_MMDD_HHMM/`；`--log-dir` 作为日志根目录生效

## v0.1.34 | 2026-04-09
- 新增日志输出目录配置：新增全局参数 `--log-dir` 支持将日志输出到指定目录；若不指定则默认输出到 `logs/<oracle>_YYYY_MMDD_HHMM/<dbms>/`（例如 `logs/eet_2026_0409_1453/mysql/`），避免不同运行互相覆盖

## v0.1.32 | 2026-04-09
- 新增 EET 跳过原因统计：当 MySQL EET 执行因 SQL 错误返回 `null` 而 `IgnoreMe` 跳过时，自动捕获 `SQLException` 信息并聚类计数，周期性写入 `eet-failures.txt`（Top 10），用于定位成功率不足的主要触发场景

## v0.1.33 | 2026-04-09
- 修复 MySQL EET 变换在 temporal 类型上的崩溃：`MySQLEETTransformer.sameTypeFallback()` 补齐 `DATE/TIME/DATETIME/TIMESTAMP/YEAR` 的 fallback 常量生成，避免遇到新增加的日期类型时抛 `AssertionError(DATE)`

## v0.1.31 | 2026-04-08
- 修复 MySQL `--oracle QUERY_PARTITIONING` 运行中断：该 oracle 组合原本额外包含 `CODDTEST`，会触发 CODD 路径下的不稳定/非法 SQL；`QUERY_PARTITIONING` 现在仅保留查询划分相关的 oracles（TLP/HAVING/GROUP_BY/AGGREGATE/DISTINCT/NOREC）

## v0.1.30 | 2026-04-08
- 修复 MySQL 子查询序列化：`MySQLToStringVisitor` 之前无论 `fromList` 是否为空都会输出 ` FROM `，导致 `SELECT 1 FROM ` 这类非法 SQL；现在仅在 `fromList` 非空时生成 FROM/JOIN 子句，避免 CODD/TLP 等 oracle 生成的标量子查询报语法错误

## v0.1.29 | 2026-04-08
- 修复 MySQL 随机表达式的 temporal 转换报错：在 `MySQLErrors` 的表达式预期错误中加入 `Incorrect TIMESTAMP/DATETIME/DATE/TIME value`，避免 TLP/CODD 等 oracle 因 MySQL 对空串/非法字符串转 temporal 抛错而中断

## v0.1.28 | 2026-04-08
- 修复 MySQL JDBC 读取异常：连接串增加 `zeroDateTimeBehavior=CONVERT_TO_NULL`，避免在 `ResultSet#getObject()` 读取到 `0000-00-00 ...` 这类零日期/时间值时抛出 `Zero date value prohibited`，导致 SQLancer 运行中断

## v0.1.27 | 2026-04-08
- 修复 sqlancer MySQL CODD：`MySQLOracleAlias` 仅用于 CODDTest 的 expected-value 跟踪，生成可执行 SQL 时不应输出伪函数 `ORACLE_ALIAS(...)`（会触发 MySQL 语法错误）；`MySQLToStringVisitor` 现在只打印其 `originalExpression`，并在 `originalExpression=null` 时降级输出 `NULL`；补充 `MySQLToStringVisitorTest#visitOracleAliasToString`

## v0.1.26 | 2026-04-07
- 修复 EET 变换：`MySQLEETTransformer.tryConstBoolTransform()` 生成布尔扩展表达式时捕获 `IgnoreMeException` 并返回 `null`，避免重试逻辑被异常直接打断导致不稳定回归失败

## v0.1.25 | 2026-04-07
- 新增 sqlancer MySQL temporal 常量生成与函数类型推导兼容：`MySQLExpressionGenerator.generateConstant()` 在 `testDates=true`（含 PQS）下可生成 `DATE/TIME/DATETIME/TIMESTAMP/YEAR` 常量并支持 fsp=6；`MySQLComputableFunction.castToMostGeneralType()` 支持 temporal most-general type 的字符串解析回写；新增单测 `MySQLExpressionGeneratorTemporalTest`

## v0.1.24 | 2026-04-07
- 新增 sqlancer MySQL `getRandomRowValue` temporal 抽取：`MySQLTables.getRandomRowValue()` 对 `DATE/TIME/DATETIME/TIMESTAMP/YEAR` 使用对应 JDBC getter 并映射为可引用的字符串字面量常量；新增隔离单测 `MySQLRowValueTemporalExtractionTest`

## v0.1.23 | 2026-04-07
- 新增 sqlancer MySQLSchema temporal 类型支持：`MySQLSchema.MySQLDataType` 增加 `DATE/TIME/DATETIME/TIMESTAMP/YEAR`，`getColumnType()` 支持信息架构小写映射；补充 temporal 映射单测

## v0.1.16 | 2026-04-07
- 修复 sqlancer 编译失败：移除 `MySQLCODDTestOracle` 中未使用的字段，避免在 `maven-compiler-plugin` 启用 `<failOnWarning>true</failOnWarning>` 时因 warning 导致构建失败

## v0.1.17 | 2026-04-07
- 修复 SQLancer 帮助与文档误导：隐藏 MySQL 中当前未生效的 options（避免 `--help` 列出大量无效参数），并为 help 可见参数添加回归测试校验；同步更新 MySQL 分析文档对 option 生效性的表述

## v0.1.18 | 2026-04-07
- 优化 SQLancer help：在 `mysql` 命令的帮助输出中补充 `--oracle` 的可选项枚举列表与默认值（与代码运行时保持一致）

## v0.1.19 | 2026-04-07
- 优化 SQLancer help：`mysql --help` 不再重复打印 `--oracle` 默认值，仅补充可选项列表（默认值沿用 JCommander 的 `Default: [...]` 输出）

## v0.1.20 | 2026-04-07
- 修复构建失败：调整 `org.eclipse.jdt.core.prefs` 中与文档注释引用相关的 unused 检查开关，避免 ECJ 批量编译时因“忽略编译器选项”产生 warning 并在 `failOnWarning=true` 下导致编译失败

## v0.1.21 | 2026-04-07
- 优化命令行体验：MySQL 子命令支持 `mysql --help/-h`（不要求把 `--help` 放在 DBMS 名称之前），并输出 mysql 专属帮助与 `--oracle` 可选项列表

## v0.1.22 | 2026-04-07
- 修复编译失败：适配 JCommander 1.82 的命令帮助输出方式（使用 `jc.getCommands().get(cmd).usage()` 替代不存在的重载）

## v0.1.15 | 2025-03-21
- 新增 MySQL 参数 `--engines=E1,E2,...`：建表时仅从指定引擎名生成 `ENGINE=...`（支持自定义引擎名）；`MySQLSchema.MySQLEngine` 增加 `UNKNOWN` 以兼容信息架构中的未知引擎；补充 `MySQLOptionsTest` 与用户指南用法示例

## v0.1.14 | 2025-03-21
- 优化 [sqlancer_user_guide_0319.md](sqlancer_user_guide_0319.md)：新增 §3.1.1「EET Oracle」——功能清单、命令行用法、七条等价变换规则（原理与适用场景）、团队使用建议与延伸阅读

## v0.1.13 | 2025-03-20
- 修复 EET 递归变换运行时 NPE：`MySQLComputableFunction.getExpectedValue` 在子表达式 PQS 为 null 时未判空；`MySQLUnaryPrefixOperation` / `MySQLUnaryPostfixOperation` / `MySQLInOperation` 补充 null 防护；`MySQLEETExpressionTree` 对 `EXISTS` 使用 `new MySQLExists(inner, ex.getExpectedValue())` 避免对变换后子树重复求 PQS

## v0.1.12 | 2025-03-20
- 新增 EET §3.2：`MySQLEETExpressionTree.mapChildren` + `MySQLEETTransformer` 先递归子表达式再对根节点做等价变换；`MySQLCollate` 增加 `getCollate()` 供结构映射
- 新增 EET §六：`MySQLEETComponentReducer` 两阶段 component 缩减（阶段 1：原 AST 节点 NULL 替换 + 按 `reductionSeed` 重算 eqTransform；阶段 2：变换后查询剥离 CASE）；`MySQLEETReproducer` 支持 AST 与 `--use-reducer` 联调；`sqlancer_eet_design_0319.md` 更新 §3.2 / §6.3

## v0.1.11 | 2025-03-20
- 修复 EET：`MySQLEETTransformer` 将 `MySQLText`（CTE/派生表外层 `eet_cte.ref*` 等）列为 Rule7 不变换，避免变换后 `CASE` 引入基表列导致 `Unknown column 't0.c0' in 'field list'`；`EETDefaultQueryExecutor` 使用 `executeAndGet(..., false)` 将执行失败转为跳过而非 `AssertionError`

## v0.1.10 | 2025-03-20
- 修复 EET：`MySQLEETQueryGenerator` 中 WITH/派生表外层 `WHERE` 不再使用 `generateBooleanExpression()`（会引用基表列），改为仅引用 `eet_cte.ref*` / `eet_sub.ref*`，避免 `Unknown column 't2.c0' in 'where clause'`

## v0.1.9 | 2025-03-19
- 新增 EET SQL 生成器形态：`MySQLEETQueryGenerator` 支持 plain / UNION / WITH(CTE) / 派生表；`MySQLEETQueryTransformer.eqTransformRoot` 递归变换 UNION、CTE 与派生表子查询；`MySQLEETOracle` 使用随机形态生成；补充 `MySQLEETQueryTransformerTest` 用例

## v0.1.8 | 2025-03-19
- 优化 [sqlancer_eet_design_0319.md](sqlancer_eet_design_0319.md)：新增 §十二「实现对照（代码与测试清单）」——包路径、类职责、测试类与运行示例
- 优化 [sqlancer_user_guide_0319.md](sqlancer_user_guide_0319.md)：补充 MySQL `--oracle EET` 说明与总表 EET 条目（本仓库扩展）

## v0.1.7 | 2025-03-19
- 新增 SQLancer MySQL EET Oracle 实现（`sqlancer.mysql.oracle.eet`）：`EETMultisetComparator`、`MySQLEETTransformer`、`MySQLEETQueryTransformer`、`MySQLEETOracle`、`MySQLEETReproducer`、`MySQLOracleFactory.EET`；AST 节点 `MySQLPrintedExpression`；`SQLancerResultSet.getColumnCount()`；配套 JUnit 单元测试与 `TestMySQLEET`

## v0.1.6 | 2025-03-19
- 优化 sqlancer_eet_design_0319.md：纳入 Ask 模式建议的论文对齐内容——§1.4 形式化预言（Eq. 1）、§2.5 仅 SELECT 与 DML 范围、§3.1.2 UNION/CTE、§3.3 Rule 7 节点枚举、§3.6 三值逻辑与 CASE、§4.5 比较假设与非确定性、§八 会话说明、§十 规则扩展；原「设计完备性检查清单」调整为 §十一并扩充检查项

## v0.1.5 | 2025-03-19
- 优化 sqlancer_eet_design_0319.md：新增 §1.3「与现有 Test Oracle 的隔离（非回归约束）」——EET 独立实现、opt-in、不修改既有 Oracle 与 QUERY_PARTITIONING 默认组合；更新 §七与完备性清单

## v0.1.4 | 2025-03-19
- 优化 sqlancer_eet_design_0319.md：修正结果比较逻辑——EET 需多列多行 multiset 比较，补充 `getFullResultSetAsMultiset` 与 `compareResultMultisets` 实现示例

## v0.1.3 | 2025-03-19
- 新增 [sqlancer_eet_design_0319.md](sqlancer_eet_design_0319.md)：SQLancer 中实现 EET Oracle 的设计文档，基于 EET-main 逻辑与 SQLancer 架构的模块映射与实现说明
- 优化 sqlancer_eet_design_0319.md：补充递归变换、JOIN ON 条件、类型一致性、随机重试、const_bool 特例、结果比较、用例缩减、设计完备性检查清单

## v0.1.2 | 2025-03-19
- 新增 [eet_analyze.md](eet_analyze.md)：EET 论文《Detecting Logic Bugs in Database Engines via Equivalent Expression Transformation》与 EET-main 工程实现分析

## v0.1.1 | 2025-03-19
- 新增 [pdf-paper skill](../.cursor/skills/pdf-paper/SKILL.md)：PDF 阅读与提取、学术论文学习、关键洞察总结

## v0.1.0 | 2025-03-19
- 初始化 release_notes.md 与 release-notes 规则
