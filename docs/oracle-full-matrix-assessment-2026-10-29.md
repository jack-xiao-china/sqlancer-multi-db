# 全量 Oracle 冒烟与集成测试评估报告（2026-10-29）

> 范围：4 DBMS × 24 oracle（20 常规 + TX_INFER/WRITE_CHECK/WRITE_CHECK_REPRODUCE + FUCCI DT/RANDOM）
> 参数：`--num-tries 10 --timeout-seconds 180 --num-threads 2`，jar `v2.7.11`（ef2ea9c2）
> 集成层说明：仓库 JUnit DBMS 集成测试（`TestMySQL*` 等）硬编码 provider 默认凭证 `sqlancer/sqlancer` 且 GaussDB 测试指向 localhost，与本环境不兼容（自动跳过）；本矩阵即实际集成层。单测层全量 `mvn test` 另行归因。

## 一、总览

| DBMS | 通过 | 报告 | 通过率 |
|------|------|------|--------|
| PostgreSQL | 17/24 | 7 | 71% |
| GaussDB-A | 14/24 | 10 | 58% |
| GaussDB-M | 11/24 | 13 | 46% |
| MySQL | 8/24 | 16 | 33% |

本会话（v2.7.10/v2.7.11）修复过的 14 个 oracle-DBMS 组合在全量矩阵中**全部保持通过**（GaussDB 9 + MySQL 3 + PG 2）。

## 二、确诊工具类问题（12 项，按优先级）

| # | 问题 | 影响范围 | 根因定位 |
|---|------|----------|----------|
| 1 | FUCCI 连接生命周期：连接关闭后继续使用 | MySQL（11s 即崩，本地库可排除负载因素）+ GaussDB-M/A 同款 `connection has been closed` | FUCCI 事务调度器的连接管理（跨库共性，待栈级定位） |
| 2 | WRITE_CHECK_REPRODUCE 独立运行 | 4/4 库同款 `Read case from file failed` | 需要前置 WRITE_CHECK 产生的 case 文件；`run_all_tests.sh` 将其列入可独立运行 oracle 属用法缺陷 |
| 3 | GaussDB-M TLP 族 join 生成 | AGGREGATE/DISTINCT/GROUP_BY/HAVING/QUERY_PARTITIONING 失配 SQL 中再现 `t1, t2_0 RIGHT JOIN t2_0` 重复表 + `NATURAL JOIN t1`（ON）模式 | TLP 族走 `getRandomJoinClauses` 路径，v2.7.10 只修了 CODDTEST 的 `genJoinExpression`（tables.remove + NATURAL 守卫）——同款修复未覆盖该路径；**其失配发现被此缺陷污染，修复后需复跑分辨真伪** |
| 4 | MySQL SONAR | 6 秒即崩 | `MySQLVisitor.java:191` 未处理 `MySQLPostfixText` → `AssertionError`（v2.7.9 修过 PostgresSonar 同款缺口，MySQL 版未同步） |
| 5 | MySQL EDC_RADAR | 种子数据插入即崩 | 生成零日期 `'0000-00-00 00:00:00'`，MySQL 严格模式拒绝 → 未处理 RuntimeException |
| 6 | PG EDC_RADAR | 等价状态构建崩溃 | `PostgresEDCRadarOracle.constructEquivalentState:238` 生成非法数字字面量（syntax error near ".56"） |
| 7 | GaussDB-M PQS | NPE ×4 | `GaussDBMPivotedQuerySynthesisOracle.generateRectifiedExpression:103`：`getExpectedValue()` 返回 Java null 时直接 `.isNull()` |
| 8 | MySQL EET DML 视图目标 | EET_DELETE/INSERT_SELECT | DML 目标随机选到视图 `v0` → `not updatable / not insertable-into` 未列入容忍 |
| 9 | MySQL DQE | 错误覆盖 | `SELECT has different errors from UPDATE`——SELECT/UPDATE 错误集不对称容忍缺口 |
| 10 | PG SONAR | ALTER 预期错误缺口 | `ALTER TABLE ... ALTER c6 RESET(n_distinct...)` 失败未列入 expected errors |
| 11 | PG WRITE_CHECK | locale | 服务端 zh_CN 错误消息（乱码）无法匹配英文预期错误——v2.7.9 已知环境项，建议实例设 `lc_messages='C'` |
| 12 | MySQL PQS | 双因 | `Out of sort memory`（服务端排序缓冲，应列预期）+ `AssertionError: DECIMAL`（期望值比较，待定位） |

## 三、oracle 按设计产出的发现（需人工研判，非工具缺陷）

- **TLP 族变形失配**：MySQL（AGGREGATE/GROUP_BY/TLP_WHERE/QP）、GaussDB-A（HAVING/TLP_WHERE 单发）、GaussDB-M（同族但被工具问题 #3 污染）
- **CERT 估计不一致**：GaussDB-A ×10、GaussDB-M ×1、MySQL ×2（弱谓词估计反降的单调性违反）
- **EET DML row-state mismatch**：4/4 库均现（MySQL×最多、A×8、M×27）——EET DML 已知难点，建议专项
- **TX_INFER 终态不一致**：M/A/PG 三库高频（5-7 报告/3min）——系统性，真发现或共性噪声需专项分辨
- **WRITE_CHECK 查询结果不一致**：A ×1；PG ×8（与 locale 问题混合）
- **PG EDC_DATA** ×4（`pg_get_replica_identity_index` 等函数期望值失配）
- **PG FUCCI** ×10 报告（无异常头——MVCC 违反候选发现）
- **MySQL EDC_DATA**（ST_GeomFromText 位运算期望值）

## 四、单测层归因（全量 `mvn test`：1354 测试，44F+4E，78 跳过）

双基线对比（fed24b74 会话前 / 3e615f81 PR#15 前）裁决：

| 归属 | 数量 | 明细 |
|------|------|------|
| 本会话引入 | 2 | GaussDBAggregateAVG/StatisticsTest 聚合计数 23→20（已修复） |
| PR #15 引入 | 1 | EETMultisetComparatorTest.rowKey 编码变更未同步测试 |
| 早于 PR #15 存量 | ~31 in-scope | 过期计数（oracle 15/16→25 ×4）、MySQLOptionsHelp（fucci 参数）、GaussDBExists（×2）、MySQLCaseOperator（×1E）、Sonar 系（×27）、Visitor（×2）——历史发布长期 `-DskipTests`，测试腐化未察觉 |
| 库外 DBMS | ~17 | SQLite/H2/HSQLDB 集成测试超时 + TestUsageNamingConvention（4 库规则范围外，不修） |

## 五、结论与建议

**总体判断**：工具类问题仍存在，但全部为存量且模式明确——多数与已修复问题同款（M 的 TLP join = CODDTEST 修复未覆盖的第二条路径；MySQL SONAR = Postgres SONAR 同款）。本会话两轮修复（v2.7.10/v2.7.11）的成果在本轮全量验证中零回退。

**建议修复顺序（v2.7.12 候选）**：
1. GaussDB-M TLP 族 join（#3）——解锁 5 个被污染 oracle 的真伪分辨
2. FUCCI 连接生命周期（#1）——跨库
3. MySQL SONAR/EDC_RADAR + PG EDC_RADAR/SONAR（#4/#5/#10）——各为小修
4. GaussDB-M PQS NPE + MySQL EET 视图目标 + DQE/PQS 错误覆盖（#7/#8/#9/#12）
5. WRITE_CHECK_REPRODUCE 用法修正（#2，改 run_all_tests.sh 文档/逻辑）
6. 环境项：PG 实例设 `lc_messages='C'`（#11）
7. 专项（不确定工具/发现归属）：EET DML row-state、TX_INFER 终态
8. 测试债清理：PR#15 的 rowKey 测试 + 过期计数断言

**日志位置**：`.claude/tmp/full-smoke/<dbms>-<oracle>.log`（96 份）

---

## 附记（v2.7.12 修复后状态，2026-10-29）

第二节 12 项工具问题的处置结果：

| # | 问题 | 处置 |
|---|------|------|
| 1 | FUCCI 连接生命周期 | ⏸ 专项（栈已定位 TxBase.reproduceDatabase:37 死连接） |
| 2 | WRITE_CHECK_REPRODUCE 用法 | ✅ 脚本注释说明前置条件 |
| 3 | GaussDB-M TLP 族 join | ✅ 修复（MySQL 同款一并修） |
| 4 | MySQL SONAR PostfixText | ✅ 修复（分发链补全 + 终态拒预测） |
| 5 | MySQL EDC_RADAR 零日期 | ✅ 构造失败改跳过（剩余报告为 oracle 真发现） |
| 6 | PG EDC_RADAR 字面量 | ✅ money 校验 + 跳过语义 |
| 7 | GaussDB-M PQS NPE | ✅ 空传播×7 + 终态拒预测 + 行值兜底（冒烟 0 报告） |
| 8 | MySQL EET 视图目标 | ✅ 三 oracle 过滤视图 |
| 9 | MySQL DQE 错误覆盖 | ✅ 3156/3170 update-specific + 诊断增强（剩余报告为发现/设计项） |
| 10 | PG SONAR ALTER | ✅ identity 错误×3 分支 |
| 11 | PG WRITE_CHECK locale | 📋 环境项（建议实例设 lc_messages='C'） |
| 12 | MySQL PQS 双因 | ✅ 行值兜底 + DATE null + sort-memory（冒烟 0 报告） |

另修复矩阵外的连带发现：MySQL TLP 族 FROM 构造同款、MySQL FROM-less SELECT 渲染 ISE、MySQLTableGenerator BIT 溢出、MySQLVisitor 分发链缺失四类型、单测测试债（41 用例全绿）。第三节发现类维持待研判。
