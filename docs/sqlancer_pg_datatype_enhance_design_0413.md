# SQLancer PostgreSQL 数据类型增强：时间/JSON/UUID/bytea/数组/Enum（设计与计划，2026-04-13）

## 背景与目标

现状：仓库内 SQLancer 的 PostgreSQL 适配器已覆盖核心查询/部分 DDL/DML，并支持多种 test oracle（`NOREC/PQS/TLP*/CERT/DQE/DQP/FUZZER/EET/CODDTEST`）。但 PostgreSQL 官方文档（18.3）中的关键类型体系（时间、JSON、UUID、bytea、数组、enum 等）覆盖不足。

本次目标：在**不破坏现有 oracle 稳定性**前提下，为 PostgreSQL provider 增加以下类型能力，并做到“全流程闭环”：

- **时间类型**：`date/time/timestamp/timestamptz/interval`
- **JSON/JSONB**
- **UUID**
- **bytea**
- **数组**（先做 1 维，元素类型受限）
- **Enum**（包含对象创建与列引用）

约束与策略（已确认）：

- **PQS 保持 strict**：继续沿用 `generateOnlyKnown` 思路，新增类型默认不进入 PQS 流程。
- **默认进入策略（balanced）**：
  - 默认进入：`FUZZER`、`TLP_WHERE/HAVING/AGGREGATE`、`NOREC`、`CERT`
  - 灰度/显式开关进入：`DQE/DQP/EET`
  - 不进入：`PQS`

## 非目标（第一阶段明确不做）

- 覆盖 PostgreSQL 全部类型与对象（例如 XML、全文检索、几何、多范围、pg_lsn 等暂不做）
- 让新增类型全面进入 PQS（等值 containment check 与 pivotRow 全链路扩容成本高、稳定性风险大）
- 引入存储过程/触发器/自定义运算符等复杂对象（可作为后续阶段）

## 总体架构：让“新类型”贯穿 SQLancer 的测试全流程

SQLancer 测试闭环可抽象为：

```mermaid
flowchart TD
  Options[PostgresOptions/featureFlags] --> Provider[PostgresProvider.generateDatabase]
  Provider --> Objects[预创建对象(enumTypes等)]
  Provider --> DDL[CREATE TABLE/ALTER/INDEX/VIEW...]
  DDL --> Schema[PostgresSchema.fromConnection]
  Schema --> DML[INSERT/UPDATE/DELETE生成]
  Schema --> Expr[PostgresExpressionGenerator]
  Expr --> Oracles[TestOracles]
  Oracles --> Exec[JDBC执行]
  Exec --> Errors[ExpectedErrors/IgnoreMe]
  Exec --> Repro[Reproducer/Logs]
```

要“兼容所有 oracle”，关键在于：**类型建模 + 常量生成 + schema 反射 + 表达式生成 + expected errors + 会话确定性**必须闭环，且可通过开关/灰度控制进入范围。

## 设计要点 1：类型建模与特性开关（Feature Flags）

### 1.1 新增类型枚举与最小可用集合

建议把类型分为“原子类型”和“容器/引用类型”两层（保持现有结构演进最小）：

- **原子**：TIME/DATE/TIMESTAMP/TIMESTAMPTZ/INTERVAL、UUID、BYTEA、JSON/JSONB
- **容器**：`ARRAY(elementType)`（第一阶段仅 1 维）  
- **引用**：`ENUM(enumTypeName)`（来自预创建对象）

实现上有两种可行落点：

- **方案 A（演进式，推荐）**：扩展 `PostgresSchema.PostgresDataType`（枚举）+ 增加必要的“附加信息”容器（例如用 `PostgresCompoundDataType` 扩展出 ARRAY/ENUM 的携带字段）
- **方案 B（重构式）**：引入更完整的 `PostgresType` 类层次（需要更大改动面）

本设计推荐**方案 A**，用最少的改动覆盖主要场景。

### 1.2 特性开关

在 `PostgresOptions` 增加类型组开关（默认关闭，便于灰度与降噪）：

- `--enable-time-types`
- `--enable-json`
- `--enable-uuid`
- `--enable-bytea`
- `--enable-arrays`
- `--enable-enum`

再增加 oracle 组开关（用于 balanced 默认策略 + 可调参）：

- `--datatype-coverage-policy=balanced|conservative|aggressive`（默认 `balanced`）
- `--enable-newtypes-in-dqe-dqp-eet`（默认 false，或按低概率灰度）

与 `generateOnlyKnown` 的关系：

- 当启用 PQS 时（或用户显式要求 strict），`generateOnlyKnown=true`，类型池强制收缩为“已知语义子集”，新增类型不进入 PQS 路径。

## 设计要点 2：对象预创建（Enum Types）

为支持 enum 列类型，需要在建表前创建 enum 对象：

- 在 `PostgresProvider.generateDatabase()` 的早期阶段（建表前）：
  - 当 `--enable-enum` 时创建若干 enum：`CREATE TYPE e0 AS ENUM ('a','b','c',...)`
  - 将 enum type 名称与 labels 写入 `globalState`（供 DDL/常量/表达式生成）

约束建议：

- labels 使用简单 ASCII，小集合（3~8 个），避免编码/locale 差异
- enum type 数量小（1~3 个），避免对象干扰与 schema 读取成本

## 设计要点 3：DDL（appendDataType）与 DML（INSERT/UPDATE/DELETE）闭环

### 3.1 DDL：类型输出规则（`appendDataType`）

扩展 `PostgresCommon.appendDataType(...)`：

- 时间：`date`、`time`、`timetz`、`timestamp`、`timestamptz`、`interval`
- UUID：`uuid`
- bytea：`bytea`
- JSON：`json`/`jsonb`
- 数组：第一阶段仅输出受限元素类型的 `integer[]/text[]/uuid[]/timestamptz[]`
- enum：输出 `e0/e1...`（从 `globalState` 中随机选择已创建的 enum type）

注意：

- 时间/JSON/bytea/数组的“修饰参数”（例如 precision、jsonpath、数组维度）第一阶段不引入或低概率引入，优先稳定可执行。

### 3.2 DML：插入/更新/删除需要做到“新类型列可落地”

第一阶段的最小要求：

- `INSERT`：对新类型列生成**可解析且稳定**的常量写入
- `UPDATE`：对新类型列允许更新为常量（表达式更新后续灰度）
- `DELETE`：WHERE/RETURNING 可不依赖新类型，但允许其出现在 SELECT list/RETURNING

## 设计要点 4：常量生成（最关键的稳定性控制）

新增类型必须提供“安全常量（safe）”与“压力常量（stress）”两档，且默认在强对比 oracle 中优先使用 safe。

推荐常量策略（示例）：

- **timestamptz**：`('2020-01-01 00:00:00+00')::timestamptz`（避免依赖 TimeZone/DateStyle）
- **interval**：`('1 day')::interval`（先少量单位组合）
- **jsonb**：`('{"k": 1, "s": "x"}')::jsonb`（确保合法 JSON）
- **uuid**：`('00000000-0000-0000-0000-000000000001')::uuid`
- **bytea**：`decode('DEADBEEF','hex')`
- **数组**：`ARRAY[1,2,3]::integer[]`、`ARRAY['a','b']::text[]`（长度小）
- **enum**：`('a')::e0`（label 从已知集合取）

强对比 oracle（TLP/NoREC/CERT/DQE/DQP/EET）默认约束：

- 禁用或极低概率使用 stress 常量（例如超长 bytea、大 JSON、很大数组、复杂 interval）
- 函数调用限制为 `IMMUTABLE/STABLE`（VOLATILE 默认只在 FUZZER）

## 设计要点 5：schema 反射（从数据库读回类型信息）

扩展 `PostgresSchema.fromConnection()` / `getTableColumns()` 的列类型解析，使其能识别新增类型：

- 时间：`date`、`time without time zone`、`time with time zone`、`timestamp without time zone`、`timestamp with time zone`、`interval`
- `uuid`、`bytea`、`json/jsonb`
- 数组：不能仅依赖 `information_schema.columns.data_type`；需要读取 `udt_name` 或 `pg_type`（识别 `typtype/typelem`）
- enum：通常 `data_type='USER-DEFINED'`，需要结合 `udt_name` + `pg_type.typtype='e'` 识别，并绑定到已创建 enum 类型集合（若遇到未跟踪 enum，也可先当 TEXT 降级处理，避免 AssertionError）

设计原则：

- **允许降级**：schema 读取时遇到暂不支持/未知类型，应可选择“降级为 TEXT/跳过列”而不是直接崩溃（减少 false negative）。

## 设计要点 6：表达式生成（分层引入，按 oracle 风险控制）

### 6.1 分层策略

以“不会破坏 oracle 假设”为前提逐步引入：

- **层 0（可执行）**：仅作为常量/投影列出现（SELECT list / RETURNING）
- **层 1（安全谓词）**：`IS NULL`、enum/uuid/time 的等值/范围比较（同类型）
- **层 2（类型特定操作）**：
  - time：`+/- interval`、`date_trunc(...)`（仅 stable/immutable）
  - jsonb：`->>` 提取为 text 后再比较（先避免复杂 jsonpath）
  - 数组：`array_length`、低概率下标（配 expected errors）
  - bytea：`length/encode`（稳定函数）
- **层 3（更复杂组合）**：进入 join 条件、聚合、窗口等（后续阶段灰度）

### 6.2 与 oracle 的默认进入策略（balanced）绑定

- FUZZER：允许更激进（更高概率进入层 2/3）
- TLP/NoREC/CERT：默认层 0~2（保守概率）
- DQE/DQP/EET：默认不启用新类型，除非显式开关或低概率灰度
- PQS：strict，不进入新类型

## 设计要点 7：ExpectedErrors 与会话确定性（降噪必需）

### 7.1 ExpectedErrors 分组维护

在 `PostgresCommon` 增加按类型组的 errors 收集方法，例如：

- `getTimeTypeErrors()` / `addTimeTypeErrors(errors)`
- `getJsonErrors()` / `addJsonErrors(errors)`
- `getByteaErrors()` / `addByteaErrors(errors)`
- `getArrayErrors()` / `addArrayErrors(errors)`
- `getEnumErrors()` / `addEnumErrors(errors)`

典型新增错误（示例方向）：

- 时间：`invalid input syntax for type timestamp`, `date/time field value out of range`
- JSON：`invalid input syntax for type json`, `cannot extract elements from a scalar`
- 数组：`malformed array literal`, `array subscript out of range`
- bytea：`invalid input syntax for type bytea`
- enum：`invalid input value for enum`

### 7.2 会话确定性设置（Provider 建连后执行）

建议在连接测试库后执行：

- `SET TimeZone TO 'UTC'`
- `SET DateStyle TO 'ISO, YMD'`
- `SET IntervalStyle TO 'postgres'`
- 继续尝试：`SET lc_messages TO 'C'`（已有实现）

## Oracle 兼容性说明（为何该设计能“兼容全部 oracle”）

- **PQS**：保持 strict 子集，不引入新类型，从根上避免 pivotRow 等值 containment check 扩容带来的不稳定。
- **NoREC/TLP/CERT**：新增类型先以“安全常量+低风险谓词/表达式”进入；expected errors 与 session 确定性减少噪声。
- **DQE/DQP/EET**：默认不引入新类型（避免误报爆炸）；通过显式开关/灰度逐步放开。
- **FUZZER**：作为“扩面试验场”，更快覆盖新语法/新类型边界。

## 计划（Implementation Plan，按里程碑）

> 目标是每个里程碑都能让“全流程跑通”，并让所有 oracle 至少可运行（即便部分 oracle 默认不启用新类型）。

### Milestone 0：基础设施（开关 + 会话确定性）

- 修改：
  - 扩展 `PostgresOptions`：新增类型组开关、coverage policy 开关、DQE/DQP/EET 开关
  - `PostgresProvider.createDatabase()`：增加 `SET TimeZone/DateStyle/IntervalStyle` 等确定性设置
- 验证：
  - 不启用新开关时，现有所有 oracle 行为不变（回归）

### Milestone 1：时间类型（Date/Time/Timestamp/Interval）

- 修改：
  - 类型枚举与 `appendDataType`
  - 常量生成（safe 优先）
  - schema 解析映射补齐
  - 表达式层 1~2（同类型比较、`+ interval`）
  - expected errors（时间相关）
- 验证：
  - `FUZZER/TLP/NOREC/CERT` 默认可跑
  - `PQS` strict 不受影响

### Milestone 2：UUID 与 bytea

- 修改：
  - `uuid/bytea` 类型输出、常量生成、schema 映射
  - bytea 常量优先 `decode(hex,'hex')`
  - 少量安全函数（length/encode）低概率进入表达式
  - expected errors（uuid/bytea）
- 验证：同上

### Milestone 3：JSON/JSONB

- 修改：
  - `json/jsonb` 类型输出、常量生成、schema 映射
  - 表达式：优先 `->>` 提取为 text，再进入比较；`jsonb_typeof` 等稳定函数
  - expected errors（json）
- 验证：同上，注意误报率变化

### Milestone 4：数组（1 维、元素类型受限）

- 修改：
  - array 类型输出（受限元素类型）
  - 常量：`ARRAY[...]::type[]`
  - schema：识别 array（udt_name/pg_type）
  - 表达式：`array_length` + 低概率下标（含 errors）
- 验证：同上

### Milestone 5：Enum（对象创建 + 列引用 + 常量）

- 修改：
  - Provider 预创建 enum types，并存入 globalState
  - `appendDataType` 支持 enum
  - 常量：`'label'::enumType`
  - schema：识别 enum（USER-DEFINED + pg_type）
  - 表达式：等值/IN
- 验证：同上

### Milestone 6：DQE/DQP/EET 灰度放开

- 修改：
  - 将新类型引入 DQE/DQP/EET 的概率控制与开关
  - 补齐该路径的 expected errors（按报错收敛）
- 验证：
  - 显式开启后可运行，不开启时不受影响

## 风险与缓解

- **误报（false positive）激增**：新类型带来大量 `operator does not exist/cannot cast/invalid input`。
  - 缓解：分层引入 + 类型组开关 + expected errors 分组维护 + session 确定性设置。
- **非确定性**（时区/格式/locale/volatile 函数）影响 oracle 对比。
  - 缓解：固定会话参数；强对比 oracle 禁用 VOLATILE；safe 常量优先。
- **schema 识别不完备导致 AssertionError**（数组/enum）。
  - 缓解：schema 读取允许降级或跳过列；增强查询 `udt_name/pg_type`。

## 验收标准（Definition of Done）

- **兼容性**：在不启用新类型开关时，现有所有 oracle 回归通过（不引入行为变化）。
- **全流程闭环**：对每类新增类型，至少满足：
  - 可建表（DDL）→ 可插入/更新（DML）→ 可出现在查询表达式中（Expr）→ 被至少一个默认 oracle（balanced 默认集合）覆盖执行。
- **PQS**：仍 strict 且稳定运行（不被新类型破坏）。
- **可控扩面**：DQE/DQP/EET 在显式开关或灰度下可运行，关闭时不受影响。
