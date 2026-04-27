# SQLancer 对 PostgreSQL（18.3）覆盖度分析与增强建议（2026-04-13）

## 结论摘要（先给结论）

- **总体判断**：当前仓库内的 SQLancer PostgreSQL 适配器对 **“核心关系型查询 + 部分 DDL/DML”** 覆盖较好（尤其是围绕 TLP/NoREC/PQS/CERT/DQE/DQP/FUZZER/EET 等 oracle 所需的 `SELECT`/表达式/聚合/连接/CTE/窗口函数等），但对 PostgreSQL 18.3 文档中大量 **数据类型（Chapter 8）、对象（Chapter 5.14 及后续各章）、以及更宽的 SQL 语法面（Chapter 4/6/7/9/11 等）** 覆盖仍偏窄。
- **最大短板**：**数据类型与类型相关语法**（cast、运算符、类型字面量、类型特定操作）覆盖不足；其次是 **数据库对象维度**（自定义类型/域/枚举/扩展对象/触发器/函数/权限/模式等）以及 **部分 SQL 语句形态**（例如 `MERGE`、更多 `INSERT/UPDATE/DELETE` 变体、更多 `SELECT` 子句与高级特性）。
- **如果要“覆盖更加充分”**：建议从三条主线入手：
  1) **扩充可生成的数据类型矩阵**（并保证常量生成、表达式、索引/约束、schema 解析链路一致），  
  2) **扩充对象与 DDL 面**（新增更多对象生成与变更 action，配套 expected errors），  
  3) **扩充查询语法面**（`SELECT` 子句组合、集合操作、窗口、CTE、`RETURNING`/`FROM`/`USING` 等变体）并逐步解除“PQS 限制模式”下的降级策略。

## 参考基准：PostgreSQL 18.3 官方文档的“覆盖面”框架

以下章节框架来自你提供的 `postgresql-18.3-doc-A4.pdf` 的目录（目录中与本次分析强相关的部分）：

- **SQL 语法**：`Chapter 4 SQL Syntax`（Lexical Structure / Value Expressions / Calling Functions）
- **DDL/对象**：`Chapter 5 Data Definition`（Constraints / Schemas / Partitioning / Foreign Data / Other Database Objects 等）
- **DML**：`Chapter 6 Data Manipulation`（INSERT/UPDATE/DELETE/RETURNING）
- **查询**：`Chapter 7 Queries`（CTE、集合操作、排序、LIMIT/OFFSET、VALUES、WITH 等）
- **数据类型**：`Chapter 8 Data Types`（数值/货币/字符/二进制/时间/枚举/几何/网络/位串/全文检索/UUID/XML/JSON/数组/复合/范围/域/OID/pg_lsn/伪类型等）
- **函数与运算符**：`Chapter 9 Functions and Operators`

（目录摘录示例：`8. Data Types` 下包含 `JSON Types / Arrays / Composite Types / Range Types / Domain Types / Pseudo-Types` 等多个子类目。）

## SQLancer（本仓库）对 PostgreSQL 的现状画像（基于代码）

### 覆盖面 1：可生成/解析的数据类型（Data Types）

SQLancer 当前在 `sqlancer.postgres.PostgresSchema.PostgresDataType` 中的类型枚举为：

- `INT, BOOLEAN, TEXT, DECIMAL, FLOAT, REAL, RANGE, MONEY, BIT, INET`

并且在开启 PQS 时有显式“降级模式”（`PostgresProvider.generateOnlyKnown = true`），会进一步移除 `DECIMAL/FLOAT/REAL/INET/RANGE/MONEY/BIT` 等类型，使生成范围更窄。

对照 PostgreSQL 18.3 文档 Chapter 8 的类型体系，当前覆盖属于：

- **已覆盖（部分）**：整数/布尔/文本/数值（仅 numeric/float 的一小部分映射）、货币（money）、网络地址（inet 的一个子集）、位串（bit）、范围（仅 `int4range` 映射，且表达式生成非常有限）。
- **明显缺失（大量）**：
  - **时间类型**：`date/time/timestamp/interval` 等
  - **JSON / JSONB**、**UUID**、**XML**
  - **bytea（二进制）**
  - **数组**、**复合类型**、**域（domain）**、**枚举（enum）**
  - **几何类型**、**全文检索类型（tsvector/tsquery）**
  - **CIDR/MACADDR 等网络类型扩展**
  - **多范围（multirange）**、`pg_lsn`、OID/reg* 系列更系统化支持
  - **伪类型**（这类通常不应做表列类型，但会出现在函数签名/表达式推导里）

### 覆盖面 2：数据库对象（Objects）与 DDL（Data Definition）

从 `PostgresProvider.Action` 以及各类 generator 可见当前已“会主动生成/变更”的对象/DDL 大致包括：

- **表（table）**：`CREATE TABLE` 支持 `TEMP/UNLOGGED`、`IF NOT EXISTS`、`LIKE ... INCLUDING/EXCLUDING`、列约束（`NOT NULL/NULL/UNIQUE/PRIMARY KEY/DEFAULT/CHECK/GENERATED`）、部分表级约束、`INHERITS`、`PARTITION BY (RANGE/LIST/HASH)`、`USING <access method>`、`WITH (...)`、`ON COMMIT ...`（临时表）等。
- **外键约束**：除随机 `FOREIGN KEY` 约束外，新增建表后 FK setup 阶段，主动准备类型匹配的 referenced/FK 列、UNIQUE 约束、seed 值池和外键关系；覆盖单向、链式、自引用、循环引用与 2-4 列复合外键。
  - FK setup 类型覆盖 `integer/boolean/TEXT/varchar(500)/char(500)/numeric/double precision/real/money/bit varying(500)/inet/uuid/date/time/timetz/timestamp/timestamptz/interval/bytea/int4range/ENUM`，类型池按稳定性加权并继续避免 json/array；普通 PostgreSQL schema/表达式层将 `TEXT`、`VARCHAR(500)`、`CHAR(500)` 拆成独立类型，DDL/Cast 输出确定且字符串长度统一使用 500。
  - FK setup 拓扑选择使用加权分布，优先覆盖单向/链式高收益场景，同时保留自引用、循环引用、2-4 列复合外键和低频已有列复用；分区表低频优先作为 child 参与 FK。
  - FK setup 在创建约束后为 child FK 列插入引用池 seed rows，低频为 child FK 列添加池值 DEFAULT 并覆盖 `SET DEFAULT` 动作。
  - FK setup 低频覆盖 `NOT VALID` 与可延后/即时 `VALIDATE CONSTRAINT`，并在 DELETE 中补充 referenced-table 外键限制相关 expected errors。
- **索引/聚簇**：`CREATE INDEX`、`CLUSTER`、`REINDEX`、`DROP INDEX`
- **视图**：`CREATE VIEW` 支持 `MATERIALIZED`、`OR REPLACE`、`TEMP/TEMPORARY`、`RECURSIVE`、`WITH ... CHECK OPTION`；并有 `ALTER VIEW RENAME COLUMN` 的 action。
- **删除/变更类 DDL**：独立生成 `DROP TABLE`、`DROP VIEW`、`DROP SEQUENCE`、`ALTER SEQUENCE`、`ALTER INDEX`
- **序列**：`CREATE SEQUENCE` 以及低频 `ALTER/DROP SEQUENCE`
- **类型/函数/规则对象**：enum 预置类型、composite `CREATE TYPE`、简单 SQL `CREATE FUNCTION`、`CREATE RULE`
- **统计对象**：`CREATE STATISTICS / ALTER / DROP`（并从 `pg_statistic_ext` 读取）
- **表空间**：`CREATE TABLESPACE`
- **会话/事务/维护类语句**：`SET/RESET/RESET ROLE`、`COMMIT/ROLLBACK/BEGIN`、`ANALYZE/VACUUM/TRUNCATE/DISCARD`、`COMMENT ON`、`NOTIFY/LISTEN/UNLISTEN`

对照 PostgreSQL “对象面”（文档 Chapter 5.14 及更广泛章节），当前明显欠缺的对象/DDL 方向包括（按对覆盖收益排序）：

- **类型系统对象**：已覆盖 enum/composite `CREATE TYPE`；仍缺 `CREATE DOMAIN`、range/base type、`CREATE CAST`、`CREATE OPERATOR/OPERATOR CLASS/FAMILY`（至少需要“被引用/被使用”的覆盖）
- **函数/过程/触发器生态**：已覆盖简单 SQL `CREATE FUNCTION` 与 `CREATE RULE`；`CREATE PROCEDURE`、`CREATE TRIGGER`、`CREATE EVENT TRIGGER` 暂未启用（触发器会显著影响 DML 副作用，需单独评审）
- **权限/角色/安全**：`CREATE ROLE/GRANT/REVOKE`、RLS policy（当前有 ALTER TABLE 的启用/强制动作，但缺少 `CREATE POLICY` 等；权限语句本轮暂不启用）
- **Schema/命名空间**：`CREATE SCHEMA`、search_path 变化（当前 extension 仅在单独 schema 里创建，但不生成 schema 对象变体）
- **外部数据（FDW）**：文档中属于重要对象面（当前未见 CREATE SERVER/USER MAPPING/FOREIGN TABLE 等生成）
- **物化视图刷新/依赖对象**：`REFRESH MATERIALIZED VIEW`、依赖链相关 DDL
- **分区对象更完整覆盖**：创建分区、attach/detach、分区约束/索引策略等

### 覆盖面 3：SQL 语法（Syntax）与查询能力（Queries）

当前表达式/查询生成器提供了不少关键语法点（尤其是 oracle 需要的）：

- **表达式**：逻辑（AND/OR/NOT）、比较、算术、LIKE/BETWEEN/IN、SIMILAR TO、POSIX 正则、CAST、函数调用（按 `pg_proc` 读取函数 volatility 并筛选）、COLLATE（在特定模式下强制 `COLLATE "C"` 以稳定行为）、范围操作、位运算等。
- **SELECT**：支持 FROM 多表、JOIN（含 CROSS/INNER/LEFT/RIGHT 等）、WHERE、GROUP BY、HAVING、ORDER BY、LIMIT/OFFSET、`FOR <clause>`、子查询、CTE/递归（视图生成器中体现）、窗口函数（生成 window spec、frame）。
- **DML**：
  - `INSERT`：支持多行 VALUES、`OVERRIDING SYSTEM/USER VALUE`、`ON CONFLICT ... DO NOTHING`
  - `UPDATE`：基础 `SET ...` + 可选 `WHERE`
  - `DELETE`：支持 `ONLY`、可选 `WHERE`、可选 `RETURNING`
  - FK setup 生成的 `fk_`/`fk_ref_` 列在 INSERT/UPDATE 中按 schema 列类型优先使用稳定值池或 NULL，提升引用完整性相关语句的有效执行比例。

与 PostgreSQL 18.3 文档中的语法全景相比，主要差距集中在：

- **类型相关语法与操作符/函数面**：即便 Chapter 4/9 覆盖广，当前 generator 只覆盖很窄的类型集合与对应运算。
- **更丰富的 DML 变体**：
  - `INSERT ... ON CONFLICT DO UPDATE`（目前仅 DO NOTHING）
  - `INSERT ... SELECT ...`、`DEFAULT VALUES`
  - `UPDATE ... FROM ...`（PostgreSQL 常用）
  - `DELETE ... USING ...`
  - `MERGE` 已作为低频 DML action 接入，但仍可继续增强 `WHEN` 子句组合与列筛选策略
  - `COPY` 已低频覆盖 `COPY ... TO STDOUT`，主要作为语法探针，避免 JDBC COPY 交互模式带来高频噪声
  - 更系统化的 `RETURNING`（INSERT/UPDATE/DELETE 全覆盖与多列/表达式变体）
- **更丰富的查询语法点**：
  - `DISTINCT ON`、`GROUPING SETS/ROLLUP/CUBE`、`FILTER (WHERE ...)`（聚合）
  - `LATERAL`、更复杂的 set-returning function in FROM
  - `UNION/INTERSECT/EXCEPT` 在随机查询中的系统化组合
  - `VALUES` 子句作为查询源
  - `WITH ... MATERIALIZED/NOT MATERIALIZED`（PG12+）
- **对象相关语法点**（例如 domain/enum/array/json 的表达式/字面量/索引策略）

## 覆盖度“清单对照”（按三大维度）

### 1) 数据类型覆盖度（相对 Chapter 8）

- **覆盖较好（可用于大量随机表达式与 DDL）**
  - `INT / BOOLEAN / TEXT`：贯穿 DDL/DML/表达式/WHERE/HAVING/oracle 体系
- **覆盖有限（存在但深度不足）**
  - `DECIMAL/FLOAT/REAL/MONEY/INET/BIT/RANGE`：多数时候以常量或少量运算出现；PQS 模式下会被移除以确保“已知语义”
- **基本未覆盖**
  - 时间、JSON/JSONB、UUID、bytea、数组、复合类型、enum、domain、全文检索、几何、XML、多范围、pg_lsn 等

### 2) 对象覆盖度（相对 Chapter 5.*）

- **已覆盖**
  - table / index / view（含 materialized/recursive）/ sequence / statistics / tablespace
- **覆盖不足或缺失**
  - schema、role/privilege、policy（RLS 完整链路）、function/procedure/trigger、type/domain/enum/composite、FDW 对象等

### 3) 语法覆盖度（相对 Chapter 4/6/7/9）

- **已覆盖核心**
  - 常见表达式、JOIN、WHERE/GROUP BY/HAVING、ORDER BY、LIMIT/OFFSET、子查询、窗口函数（有一定概率）、部分 CTE/递归（主要在视图生成中体现）
  - DML 基本形态（INSERT/UPDATE/DELETE + 少量 Postgres 特性：`ON CONFLICT DO NOTHING`、`DELETE ONLY`、`RETURNING`），以及低频 `MERGE` / `COPY ... TO STDOUT`
- **明显缺口**
  - `UPDATE ... FROM`、`DELETE ... USING`、`INSERT ... SELECT`、`ON CONFLICT DO UPDATE`，以及更完整的 `MERGE`/`COPY` 变体
  - `DISTINCT ON`、`GROUPING SETS/ROLLUP/CUBE`、`FILTER`、更系统化 set operations（UNION/INTERSECT/EXCEPT）与 `VALUES`
  - 类型驱动的语法（array/json/range/tsvector 等）

## 为什么会“看起来覆盖不全”：SQLancer 的目标函数决定覆盖取舍

SQLancer 并非“实现一个完整 SQL 解析器/执行器”，而是围绕 test oracle 产出 **“可执行、能触发优化器/执行器差异、且可对比/可归约”** 的 SQL 子集。当前 Postgres 适配器的设计明显倾向：

- **优先覆盖能支撑 oracle 的查询子集**（TLP/NoREC/PQS/CERT/DQE/DQP/FUZZER/EET/CODDTEST）
- **为稳定性牺牲类型/语法广度**：例如 PQS 触发 `generateOnlyKnown`，主动缩小数据类型与表达式种类，降低 false positive

因此，“覆盖不全”并不等于“做得不好”，更多是 **目标导向的最小充分集**。如果你现在的需求是“覆盖更加充分”，就需要把目标函数从“oracle 可用子集”扩展到“PostgreSQL 方言更全面的可执行子集”，并配套更强的错误模型与降噪策略。

## 增强覆盖度的建议（可落地、按收益排序）

### 建议 A：先补齐“高收益数据类型”链路（类型枚举 → 常量 → 表列 → 表达式 → schema 解析 → expected errors）

优先顺序建议：

1) **时间类型**：`date/time/timestamp/timestamptz/interval`
2) **JSON/JSONB**：含操作符（`->`/`->>`/`#>`/`@>` 等）与函数（`jsonb_*`）
3) **UUID**、**bytea**
4) **数组**（至少 1 维）：字面量、下标、`ANY/ALL`、数组函数
5) **enum/domain**（先从 DDL 对象与列类型开始，再扩到表达式）

落地要点（避免“只加枚举导致到处 AssertionError”）：

- **`PostgresSchema.PostgresDataType` 扩容**：并让 `getColumnType()` 识别更多 `information_schema.columns.data_type` 的返回值映射。
- **`PostgresCommon.appendDataType()` 与 `PostgresExpressionGenerator.generateConstant()` 同步扩展**：确保新类型在 DDL 和常量生成两端都能落地。
- **表达式生成分层**：
  - 先只让新类型以“常量 + cast + 等值比较”出现，保证可执行；
  - 再逐步加入类型特定操作（json 操作符、数组操作、时间运算等）。
- **expected errors 白名单**：新增类型相关常见错误（invalid input syntax、cannot cast、out of range、operator does not exist 等），按特性逐步收敛，防止误报激增。

### 建议 B：扩展“对象面”的覆盖，优先补齐能影响优化器/执行器的对象

按对 PostgreSQL 执行器/优化器影响与可测性排序：

- **索引生态增强**：更多 index 类型与选项（例如 partial index、expression index、INCLUDE、collation、opclass），以及与 `ANALYZE/VACUUM` 的联动。
- **分区生态增强**：创建分区、ATTACH/DETACH、分区索引/约束更丰富组合（注意 expected errors 与稳定性）。
- **外键生态增强**：当前已使用 setup 阶段覆盖单向、链式、自引用、循环和复合外键；后续可继续扩大 seed rows 对复杂 NOT NULL/default 表结构的适配。
- **Schema/命名空间**：引入 `CREATE SCHEMA`、search_path 随机化（会显著增加解析路径覆盖）。
- **RLS 完整链路**：在已有 ALTER TABLE 开关基础上补 `CREATE POLICY`/`ALTER POLICY`/`DROP POLICY`（这会直接影响查询结果与计划）。
- **触发器/函数（谨慎推进）**：这是覆盖面大但也最容易引入不稳定与非确定性的方向。建议采用：
  - 仅生成 IMMUTABLE/STABLE 函数引用（你现在已根据 `pg_proc.provolatile` 做了筛选，这是一个很好的基础）
  - 触发器先从最简单、可预测的形式开始（例如维护冗余列），并限制随机性，确保 oracle 仍可对比

### 建议 C：扩展 SQL 语法面，优先补齐“常用且可归约”的语句/子句

推荐优先级：

- **DML 变体优先补齐**（收益高、实现相对直接）：
  - `INSERT ... ON CONFLICT DO UPDATE`
  - `INSERT ... SELECT ...`
  - `UPDATE ... FROM ...`
  - `DELETE ... USING ...`
  - 继续增强 `MERGE` 的 `WHEN MATCHED/NOT MATCHED` 分支组合与 identity/generated 列筛选
  - 全面化 `RETURNING`（INSERT/UPDATE/DELETE）
- **SELECT 子句增强**：
  - `DISTINCT ON`
  - `FILTER (WHERE ...)`（聚合）
  - `GROUPING SETS / ROLLUP / CUBE`
  - `VALUES` 作为数据源
  - 集合操作（UNION/INTERSECT/EXCEPT）系统化进入随机查询生成器，而不仅在少数路径出现
- **CTE 细化**：支持 `WITH ... MATERIALIZED/NOT MATERIALIZED`（在可控概率下引入），并加强递归 CTE 的“合法形态”生成（降低目录里提到的结构性报错）。

### 建议 D：为“更大覆盖面”配套降噪策略（否则会被 false positive 淹没）

覆盖面一扩，最先爆的通常不是“语法生成”，而是：

- **expected errors 不完备** → 大量无效样本（误报/跳过/中断）
- **非确定性与依赖对象**（collation、timezone、locale、并发/锁）→ oracle 对比变脆

建议的工程化手段：

- **分层开关**：像 `generateOnlyKnown` 这样做“特性组开关”，从 CLI options 控制（例如 `--enable-json`, `--enable-array`, `--enable-time-types`）。
- **特性灰度**：每次只放开一组语法/类型，并建立“错误消息/错误码”的维护机制（本仓库已有类似 `PostgresCommon.addCommon...Errors()` 的集中入口，适合继续扩展）。
- **确定性设置**：固定 `TimeZone`、`DateStyle`、`lc_messages`（你已尝试设置 `lc_messages`），并对可能影响结果的参数做统一设置，减少“非 bug 差异”。
- **最小可归约路径优先**：新增语法时优先让它进入 FUZZER（更容易触发解析/执行器边界），然后再逐步进入 TLP/NoREC/CERT 等更严格对比的 oracle。

## 一个可执行的“扩面路线图”（建议）

你如果目标是“覆盖更加充分”，同时不希望稳定性崩掉，我建议：

- **第 1 阶段（快速扩面，稳）**：新增类型（时间 + UUID + bytea），补齐 DML 变体（UPDATE...FROM / DELETE...USING / INSERT...SELECT / ON CONFLICT DO UPDATE / RETURNING 全面化），并完善 expected errors。
- **第 2 阶段（类型驱动语法扩面）**：引入 JSON/JSONB 与数组（含基本操作符/函数），并让它们在表达式与索引中可控出现。
- **第 3 阶段（对象扩面）**：schema/search_path、RLS policy、分区 attach/detach、更多索引形态；函数/触发器放到最后，且强约束随机性。

## 附：本次分析使用的仓库证据点（便于你回溯）

- `sqlancer-main/sqlancer-main/src/sqlancer/postgres/PostgresSchema.java`：类型枚举与 `information_schema` 映射范围
- `sqlancer-main/sqlancer-main/src/sqlancer/postgres/gen/PostgresExpressionGenerator.java`：表达式/窗口/函数筛选策略
- `sqlancer-main/sqlancer-main/src/sqlancer/postgres/gen/PostgresTableGenerator.java`：CREATE TABLE（约束/继承/分区/存储参数）
- `sqlancer-main/sqlancer-main/src/sqlancer/postgres/gen/PostgresInsertGenerator.java`：INSERT 特性（含 `ON CONFLICT DO NOTHING`、bulk values、overriding）
- `sqlancer-main/sqlancer-main/src/sqlancer/postgres/gen/PostgresDeleteGenerator.java`：DELETE ONLY / RETURNING
- `sqlancer-main/sqlancer-main/src/sqlancer/postgres/PostgresOracleFactory.java`：启用的 oracle 集合（NoREC/PQS/TLP*/CERT/FUZZER/DQP/DQE/EET/CODDTEST）
