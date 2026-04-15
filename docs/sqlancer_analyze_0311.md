# SQLancer 工具分析报告

**分析日期**: 2025-03-11  
**参考来源**: [SQLancer GitHub 仓库](https://github.com/sqlancer/sqlancer)

---

## 一、概述

SQLancer 是业内流行的**数据库管理系统（DBMS）自动测试工具**，用于发现数据库实现中的逻辑错误与性能问题，而非用户编写的查询错误。该工具已在 SQLite、MySQL、PostgreSQL、TiDB 等成熟 DBMS 中发现数百个真实缺陷。

SQLancer 主要解决自动测试 DBMS 时的两大核心问题：

1. **测试输入生成**：自动生成 SQL 语句（模式创建、数据插入、查询等）
2. **测试预言（Test Oracle）**：判断执行结果是否正确，从而发现逻辑/性能/崩溃等各类缺陷

---

## 二、设计框架与原理

### 2.1 整体架构

SQLancer 采用 **Java + Maven** 实现，每个被测试的 DBMS 都有独立的 Provider 与生成器实现，整体流程可概括为：

```
[Schema 生成] → [数据/索引/视图等状态生成] → [查询生成] → [Test Oracle 校验] → [缺陷报告/日志]
```

- **多阶段 SQL 生成**：先创建表结构（Schema），再插入数据并构造索引、视图等状态，最后生成可被 Oracle 校验的查询。
- **DBMS 特定实现**：每个 DBMS 有独立的 `*Provider`、Schema、表达式生成器、ToString 访问器等，以适配不同 SQL 方言与类型系统。
- **预期错误机制**：通过 `ExpectedErrors` 声明“可接受的”错误（如唯一约束冲突），仅把未声明的内部错误或逻辑不一致视为缺陷。

### 2.2 测试输入生成

- **非引导式生成**：随机生成模式与查询，不依赖执行反馈。
- **反馈引导式生成（QPG）**：通过 `--qpg-enable` 启用 Query Plan Guidance，根据**已观察到的查询计划**决定是否变异数据库状态，以触发新的计划、覆盖更多 DBMS 行为。
- **表达式生成**：
  - **宽松型 DBMS（Untyped）**：如 MySQL、SQLite，不严格约束类型，依赖 DBMS 隐式类型转换。
  - **严格型 DBMS（Typed）**：如 PostgreSQL、CockroachDB，表达式生成时传入期望类型（如 WHERE 需要 boolean），递归生成类型一致的表达式，减少语义错误。

### 2.3 缺陷类型与对应 Oracle

| 缺陷类型       | 检测方式说明 |
|----------------|--------------|
| **逻辑错误**   | 多种 Test Oracle（TLP、NoREC、PQS、DQP、DQE、CODDTest）通过等价/差分比较发现结果集错误 |
| **性能问题**   | CERT 通过基数估计不一致发现潜在性能缺陷 |
| **内部/意外错误** | 未在 ExpectedErrors 中声明的错误会被报告 |
| **崩溃**       | 进程异常退出时由隐式 Oracle 判定为崩溃缺陷 |

---

## 三、各 Test Oracle 的差异

以下按时间顺序简述各 Oracle 的思路与差异。

### 3.1 Pivoted Query Synthesis (PQS) — OSDI 2020

- **思路**：随机选一行作为“轴心行”，生成**保证能选中该行**的查询；若执行结果中不包含该行，则判定为逻辑错误。
- **特点**：能发现多种逻辑错误，但实现成本高，且与后续基于蜕变/差分的方法相比维护成本大，**当前处于未维护状态**。

### 3.2 Non-optimizing Reference Engine Construction (NoREC) — ESEC/FSE 2020

- **思路**：将**可能被优化器优化的查询**改写为**几乎不会被优化的等价查询**，比较两者结果集；不一致则说明优化导致逻辑错误。
- **适用**：主要针对**带过滤谓词的简单查询**，擅长发现**优化器相关逻辑错误**。
- **与 TLP 区别**：不拆分查询为多子查询，而是“可优化版本 vs 不可优化版本”的成对比较。

### 3.3 Ternary Logic Partitioning (TLP) — OOPSLA 2020

- **思路**：将原查询按三值逻辑拆成**三个分区查询**，将三个结果组合后与原查询结果比较；不一致则判定为逻辑错误。
- **特点**：可检测**聚合、复杂表达式**等高级特性中的错误，是当前**使用最广泛的 Oracle 之一**。
- **与 NoREC 区别**：不只针对简单谓词，适用于更复杂的查询语义。

### 3.4 Differential Query Execution (DQE) — ICSE 2023

- **思路**：对**同一谓词 φ** 分别执行 SELECT、UPDATE、DELETE，检查执行结果是否一致（例如：被 UPDATE 更新的行应出现在带相同谓词的 SELECT 结果中）。
- **特点**：专门针对 **SELECT/UPDATE/DELETE 的逻辑一致性**，通过表中附加列标识行并跟踪修改状态。
- **支持**：目前主要支持 MySQL。

### 3.5 Query Plan Guidance (QPG) — ICSE 2023

- **定位**：不是独立的“结果校验 Oracle”，而是**测试输入生成策略**。
- **思路**：以**查询计划覆盖**为反馈，当一段时间内没有新计划出现时，通过变异数据库状态促使新计划被触发，再与 TLP/NoREC 等 Oracle 结合使用。
- **启用**：`--qpg-enable`，可与 TLP、NoREC 搭配，支持 SQLite、CockroachDB、TiDB、Materialize。

### 3.6 Cardinality Estimation Restriction Testing (CERT) — ICSE 2024

- **目标**：发现**性能相关问题**，而非逻辑错误。
- **思路**：从给定查询派生出**更严格**的查询，理论上其**估计基数（estimated cardinality）**应不大于原查询；若违反则视为潜在性能/基数估计问题。
- **支持**：TiDB、CockroachDB、MySQL。  
- **注意**：CERT 报告的问题属于“性能/估计”范畴，不一定像逻辑错误那样有明确对错界定。

### 3.7 Differential Query Plans (DQP) — SIGMOD 2024

- **思路**：**控制同一查询的不同执行计划**，比较这些计划下的结果是否一致；不一致则判定为逻辑错误。
- **特点**：从“不同计划应结果一致”的角度发现逻辑错误，与 NoREC（优化 vs 不优化）形成互补。
- **支持**：MySQL、MariaDB、TiDB。

### 3.8 Constant Optimization Driven Database System Testing (CODDTest) — SIGMOD 2025

- **思路**：假设会话内数据库状态可视为常量，对查询进行**常量折叠、常量传播**式的替换（用子表达式结果替换部分查询），比较替换前后结果是否一致。
- **特点**：能覆盖**子查询等高级特性**中的逻辑错误。
- **实现**：对应 GitHub PR [#1054](https://github.com/sqlancer/sqlancer/pull/1054)。

### 3.9 小结对比

| Oracle    | 主要目标     | 典型适用场景           | 与其它 Oracle 的差异 |
|-----------|--------------|------------------------|----------------------|
| PQS       | 逻辑错误     | 任意保证命中某一行的查询 | 轴心行 + 存在性检查，已停维护 |
| NoREC     | 优化器逻辑错误 | 简单带谓词查询         | 可优化 vs 不可优化版本比较 |
| TLP       | 逻辑错误     | 聚合、复杂表达式       | 三值逻辑分区组合比较 |
| DQE       | 逻辑错误     | SELECT/UPDATE/DELETE 一致性 | 同谓词下多语句结果一致性 |
| QPG       | 输入生成     | 与 TLP/NoREC 配合      | 基于查询计划的反馈引导生成 |
| CERT      | 性能/基数估计 | 基数估计违规           | 唯一侧重性能的 Oracle |
| DQP       | 逻辑错误     | 多计划结果一致性       | 同查询不同计划结果比较 |
| CODDTest  | 逻辑错误     | 子查询等高级特性       | 常量折叠/传播等价替换 |

---

## 四、运行方式

### 4.1 环境要求

- **Java**：11 及以上
- **Maven**：用于构建

### 4.2 构建与基本运行

```bash
git clone https://github.com/sqlancer/sqlancer
cd sqlancer
mvn package -DskipTests
cd target
java -jar sqlancer-*.jar --num-threads 4 sqlite3 --oracle NoREC
```

- **参数顺序**：全局选项（如 `--num-threads`）在 **DBMS 名称之前**；DBMS 专属选项（如 `--oracle NoREC`、`--test-rtree`）在 **DBMS 名称之后**。
- **嵌入式 DBMS**：SQLite、DuckDB、H2 等无需单独安装服务器，其二进制以 JAR 形式包含；崩溃会导致运行 SQLancer 的 JVM 一起退出。

### 4.3 常用选项示例

- `--num-threads N`：线程数  
- `--oracle NoREC | TLP | ...`：选择 Test Oracle（依 DBMS 支持情况而定）  
- `--num-tries N`：发现 N 个缺陷后退出  
- `--timeout-seconds N`：最大运行时间（秒）  
- `--qpg-enable`：启用 Query Plan Guidance（若该 DBMS 支持）  
- `--use-reducer`：启用实验性 delta-debugging 缩减复现用例  
- `--log-each-select`：记录每条发送的 SQL（默认开启）

查看全部选项：

```bash
java -jar sqlancer-*.jar --help
```

### 4.4 日志与复现

- **日志目录**：`target/logs/`  
- **每条 SQL**：默认记录在带 `-cur.log` 后缀的文件中。  
- **逻辑错误复现**：发现逻辑错误时会生成 `.log` 文件，包含**最后一笔查询**及**构造当前数据库状态**的语句，便于复现与缩减。

### 4.5 终止与版本建议

- **手动终止**：Ctrl+C。  
- 若不设 `--num-tries` 或 `--timeout-seconds`，工具会一直运行直到发现缺陷或用户中断。  
- **版本**：建议使用 GitHub 上最新源码构建；官方也会在 [GitHub Releases](https://github.com/sqlancer/sqlancer/releases)、Maven Central、DockerHub 发布不定期版本。

### 4.6 支持的 DBMS（截至 2025 年 1 月）

Citus、ClickHouse、CnosDB、CockroachDB、Databend、Apache DataFusion、Apache Doris、DuckDB、H2、HSQLDB、MariaDB、Materialize、MySQL、OceanBase、PostgreSQL、Presto、QuestDB、SQLite3、TiDB、YugabyteDB 等；各 DBMS 支持程度与可用 Oracle 不同，需以文档或 `--help` 为准。

---

## 五、总结

SQLancer 通过**多阶段随机/反馈引导的 SQL 生成**与**多种互补的 Test Oracle**，系统性地检测 DBMS 中的逻辑错误、性能问题与崩溃。各 Oracle 侧重点不同：NoREC/TLP 使用广泛，DQE/DQP/CODDTest 针对特定语句类型或高级特性，CERT 专注基数估计与性能，QPG 则提升输入生成质量。运行方式以 JAR + 命令行选项为主，日志与复现支持完善，适合集成到 CI 或长期稳定性测试中。

---

*本报告基于 SQLancer 官方 README、CONTRIBUTING、PAPERS 及 GitHub 仓库内容整理。*
