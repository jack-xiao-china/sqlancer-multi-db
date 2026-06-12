# EDC 项目与 SQLancer Oracle 全方位对比分析

## 1. 概述

本文档对 SIGMOD 2026 论文《Detecting Logic Bugs in DBMSs via Equivalent Data Construction》（EDC）及其源码实现，与 SQLancer 当前全部 24 种 Test Oracle 进行全方位对比，评估各自优劣势，并给出改进建议。

---

## 2. EDC 核心方法论

### 2.1 核心思想

**数据等价构造（Equivalent Data Construction）**：对于 SQL 查询中的数据操作表达式（函数、算术、谓词、聚合），将其替换为预计算的结果值，查询语义不应改变——如果结果不一致，说明 DBMS 在数据操作实现上存在逻辑 Bug。

```
原始查询: SELECT CONCAT(a1, a2) FROM Tbase WHERE CONCAT(a1, a2)
等价查询: SELECT r FROM Ttrans WHERE r
（Ttrans.r = 预计算 CONCAT(a1, a2) 的结果）
```

### 2.2 与 SQLancer 已集成的 EDC Oracle 的区别

**重要区分**：SQLancer 已集成的 `EDCRadarBase`（位于 `common/oracle/EDCRadarBase.java`，工厂枚举 `EDC_RADAR`）采用的是 **Radar 项目的 EDC 方法**——即"原始数据库 vs 去约束的裸数据库"对比，检测的是**约束优化 Bug**。而 SIGMOD 2026 论文的 EDC 方法是**数据操作表达式 vs 预计算结果**对比，检测的是**数据类型/操作实现 Bug**。两者同名但原理完全不同，现已通过命名区分：`EDC_RADAR` vs `EDC_DATA`。

| 对比维度 | SQLancer EDC_RADAR | 论文 EDC_DATA (SIGMOD 2026) |
|----------|--------------------------|------------------------|
| 测试对象 | 约束优化（NOT NULL/FK/CHECK） | 数据操作实现（函数/算术/谓词/聚合） |
| 等价构造 | 去除所有约束的裸数据库 | 预计算表达式结果的派生表 |
| Bug 类型 | 优化器误用约束信息 | 隐式类型转换/溢出/精度丢失 |
| 典型 Bug | WHERE NOT NULL 优化错误 | CONCAT('0.6782','979') 被误判为 FALSE |

---

## 3. EDC 源码项目分析

### 3.1 项目概况

| 属性 | 值 |
|------|-----|
| 语言 | Python 3.9 |
| 代码量 | ~400 行核心逻辑 |
| 支持 DBMS | MySQL、MariaDB、Percona、PostgreSQL、TiDB、OceanBase、ClickHouse、DaMeng |
| 操作数（MySQL） | 381（从 seed 文件加载） |
| 数据类型数 | 40+ |
| 无 Reducer | 发现 Bug 后只记录完整 SQL 序列，不做缩减 |

### 3.2 核心流程

```
1. 随机选择操作类型 (AGGREGATE/FUNCTION/PREDICATE)
2. 随机选择具体操作（从 seed/{db}/agg|func|pred 文件）
3. 随机生成列类型和名称（1-5列，40+ 数据类型）
4. 构造测试表达式：如 CONCAT(c0, c1)、SUM(c0)、c0 > c1
5. 创建原始表 t0，插入 1-30 行随机数据
6. 验证表达式在 t0 上可执行（失败则跳过）
7. 创建派生表 t1（预计算表达式结果）
   - 非聚合：CREATE TABLE t1 AS SELECT (expr) AS c0, other_cols FROM t0
   - 聚合：CREATE TABLE t1 AS SELECT (expr) AS c0, group_cols FROM t0 GROUP BY group_cols
8. 获取派生列类型（查 information_schema）
9. 生成 50 条等价查询对并比较结果
   - 聚合：WHERE + GROUP BY + HAVING → WHERE + AND(having_cond)
   - 函数：WHERE + ORDER BY（随机）
   - 谓词：WHERE(expr AND/OR other_cond) + ORDER BY
10. 结果不一致 → 记录 Bug
```

### 3.3 表达式生成器

深度控制的递归 AST 生成：
- depth=1: COLUMN、CONSTANT
- depth>1: =, !=, >, <=, AND, OR
- depth>2: CASE WHEN、子查询(IN/NOT IN)

值生成覆盖 40+ 数据类型（INT 系列、FLOAT/DOUBLE、DECIMAL、VARCHAR/CHAR/TEXT、DATE/TIME/DATETIME/TIMESTAMP、JSON、UUID、BIT、BINARY/BLOB、ENUM、空间类型、ARRAY/TUPLE/MAP/VECTOR）

### 3.4 项目不足

| 问题 | 说明 |
|------|------|
| 无 Reducer | Bug 只能通过完整 SQL 序列复现，无法最小化 |
| 单线程 | 无并发测试能力 |
| 简陋的 CLI | 仅 `python main.py <target> [--debug]`，无灵活配置 |
| 无回归测试 | 无任何单元/集成测试 |
| 表达式生成深度有限 | 最大 depth=5，且 CASE/子查询仅在 depth>2 时才可能 |
| 无阻抗反馈 | 不会跟踪哪些表达式类型成功率低并跳过 |
| 种子文件硬编码 | 每个DBMS的操作列表是静态文本文件，非动态发现 |

---

## 4. SQLancer Oracle 全景与分类

### 4.1 Oracle 分类矩阵

| 类别 | Oracle | 核心等价关系 | 检测的 Bug 类型 |
|------|--------|-------------|---------------|
| **谓词测试** | NoREC | 优化查询 vs 非优化查询 | 谓词优化 Bug |
| **谓词测试** | TLP_WHERE | p = TRUE∪FALSE∪NULL | NULL处理、谓词评估 |
| **谓词测试** | SONAR | WHERE push-down vs flag过滤 | WHERE 优化 Bug |
| **查询结构** | TLP_GROUP_BY | GROUP BY 分区 | GROUP BY 优化 Bug |
| **查询结构** | TLP_HAVING | HAVING 分区 | HAVING Bug |
| **查询结构** | TLP_DISTINCT | DISTINCT 分区 | DISTINCT Bug |
| **查询结构** | TLP_AGGREGATE | 聚合分区 | 聚合计算 Bug |
| **查询结构** | CERT | 查询突变 + 行数变化 | 查询变换 Bug |
| **JOIN测试** | JIR | JOIN 类型语义蕴含 | JOIN 优化 Bug |
| **表达式测试** | EET | 语义保真变换(DeMorgan等) | 表达式变换 Bug |
| **表达式测试** | EET_UPDATE/DELETE/INSERT_SELECT | DML 语义变换 | DML谓词 Bug |
| **表达式测试** | CODDTEST | 常量折叠 | 常量折叠优化 Bug |
| **约束测试** | EDC(SQLancer版) | 原始DB vs 裸DB | 约束优化 Bug |
| **执行测试** | DQE | SELECT vs UPDATE/DELETE 路径 | 路径执行 Bug |
| **执行测试** | DQP | 优化器开关切换 | 优化器路径 Bug |
| **结果正确性** | PQS | Pivot行包含性 | 缺失行 Bug |
| **事务测试** | WRITE_CHECK | 调度 vs 可串行化 | 隔离级别 Bug |
| **事务测试** | FUCCI(DT/MT/CS) | MVCC模拟 | MVCC 可见性 Bug |
| **事务测试** | TX_INFER | 辅助版本表推理 | 隔离推理 Bug |
| **随机测试** | FUZZER | 无（纯随机） | 崩溃/错误 Bug |

### 4.2 Bug 检测维度覆盖图

```
                    ┌─────────────────────────────────────────────┐
                    │              Bug 检测维度                    │
                    └─────────────────────────────────────────────┘
    
    查询优化层      │  谓词优化  │  JOIN优化  │  聚合优化  │  约束优化
    ───────────────│───────────│────────── │────────── │──────────
    NoREC           │    ✅     │    ❌     │    ❌     │    ❌
    TLP系列         │    ✅     │    ❌     │    ✅     │    ❌
    JIR             │    ❌     │    ✅     │    ❌     │    ❌
    CERT            │    ✅     │    ✅     │    ✅     │    ❌
    EDC(SQLancer)   │    ❌     │    ❌     │    ❌     │    ✅
    EET             │    ✅     │    ❌     │    ❌     │    ❌
    PQS             │    ✅     │    ✅     │    ✅     │    ❌
    
    数据操作层      │  类型转换  │  函数实现  │  算术运算  │  精度丢失
    ───────────────│───────────│────────── │────────── │──────────
    论文EDC         │    ✅     │    ✅     │    ✅     │    ✅
    CODDTEST        │    ⚠️     │    ⚠️     │    ❌     │    ❌
    EET             │    ⚠️     │    ⚠️     │    ❌     │    ❌
    TLP             │    ⚠️     │    ❌     │    ❌     │    ❌
    
    事务隔离层      │  MVCC可见  │  调度序列  │  死锁处理  │  串行化
    ───────────────│───────────│────────── │────────── │──────────
    WRITE_CHECK     │    ❌     │    ✅     │    ✅     │    ✅
    FUCCI           │    ✅     │    ✅     │    ✅     │    ✅
    TX_INFER        │    ✅     │    ✅     │    ❌     │    ✅
```

**关键发现**：SQLancer 当前所有 Oracle 在**数据操作层**的覆盖几乎为空白——只有 CODDTEST 和 EET 有间接触及（⚠️ 表示仅部分覆盖），而论文 EDC 在此维度实现了系统性覆盖。这正是论文宣称"EDC 发现的 35 个 Bug 中没有与现有工具重叠"的原因。

---

## 5. 全方位对比

### 5.1 EDC（论文版）vs 每种 SQLancer Oracle

#### vs NoREC

| 维度 | EDC(论文) | NoREC |
|------|-----------|-------|
| 等价基础 | 数据值等价(expr = precomputed) | 优化 vs 非优化行数 |
| 覆盖范围 | 数据类型×操作组合（381-1351种） | WHERE 谓词 |
| Bug 特性 | 隐式类型转换、溢出、精度丢失 | 谓词优化错误 |
| 多表支持 | ❌（仅单表） | ✅（可含 JOIN） |
| 数据类型深度 | ✅✅（40+类型，系统性覆盖） | ⚠️（依赖 ExpressionGenerator 的类型） |
| 论文实测重叠 | 0 个重叠 Bug | — |

**结论**：完全互补，无重叠。NoREC 测优化器，EDC 测数据操作实现。

#### vs TLP 系列

| 维度 | EDC(论文) | TLP_WHERE | TLP_AGGREGATE |
|------|-----------|-----------|---------------|
| 等价基础 | 数据值等价 | 三值逻辑分区 | 聚合分区 |
| 覆盖范围 | 函数/算术/谓词/聚合 × 类型 | WHERE 谓词 | 聚合函数 |
| Bug 特性 | 数据操作实现错误 | NULL处理、谓词评估 | 聚合计算错误 |
| 多表支持 | ❌ | ✅ | ✅ |
| 论文实测重叠 | 3 个（SQL函数相关） | 3 个（JOIN相关，EDC不测） | — |

**结论**：几乎互补。TLP 的 6 个独有 Bug 全涉及 JOIN，EDC 的 35 个独有 Bug 全涉及数据操作。两者重叠仅出现在 SQL 函数作为 WHERE 条件时。

#### vs PQS

| 维度 | EDC(论文) | PQS |
|------|-----------|-----|
| 等价基础 | 数据值等价 | Pivot行包含性 |
| 需要Ground Truth | ❌（等价对比） | ✅（Rectification） |
| 覆盖范围 | 数据操作 × 类型组合 | 查询结果正确性 |
| Bug 特性 | 数据操作实现错误 | 缺失行（应该返回但没返回） |
| 数据类型深度 | ✅✅ | ⚠️ |

**结论**：PQS 测"查询漏行"，EDC 测"数据操作值错误"，互补。

#### vs EET

| 维度 | EDC(论文) | EET |
|------|-----------|-----|
| 等价基础 | 数据值等价(expr = precomputed) | 语义保真变换(DeMorgan等) |
| 变换层级 | 数据层（值替换） | 语法层（表达式重写） |
| Bug 特性 | 数据操作实现错误 | 表达式变换优化错误 |
| 数据类型深度 | ✅✅ | ⚠️（变换不触及类型转换） |
| 论文实测重叠 | 0 个 | — |
| DML支持 | ❌ | ✅（UPDATE/DELETE/INSERT_SELECT） |

**结论**：完全互补。EET 在语法层变换，EDC 在数据层替换。EET 测"优化器是否正确变换了表达式"，EDC 测"DBMS是否正确计算了表达式值"。

#### vs EDC_RADAR（SQLancer 版/Radar 风格）

| 维度 | EDC_DATA(论文) | EDC_RADAR(SQLancer) |
|------|-----------|---------------|
| 等价基础 | 表达式值 vs 预计算值 | 原始DB vs 去约束裸DB |
| 测试对象 | 数据操作实现 | 约束信息优化 |
| Bug 类型 | 类型转换/溢出/精度丢失 | NOT NULL/FK/CHECK 优化错误 |
| 数据类型深度 | ✅✅ | ❌（不关注类型） |
| 派生表构造 | 预计算表达式结果 | 去除所有约束 |

**结论**：同名但完全不同。建议将论文版 EDC 以新名称（如 `EDC_DATA`）集成到 SQLancer，避免与现有 EDC 混淆。

#### vs JIR

| 维度 | EDC(论文) | JIR |
|------|-----------|-----|
| 等价基础 | 数据值等价 | JOIN 类型语义蕴含 |
| 测试对象 | 数据操作 | JOIN 操作 |
| 多表支持 | ❌ | ✅✅（至少2表） |
| Bug 特性 | 数据操作实现 | JOIN 优化器 |
| 论文实测重叠 | 0 个 | — |

**结论**：完全互补。JIR 专注 JOIN，EDC 专注数据操作。

#### vs CODDTEST

| 维度 | EDC(论文) | CODDTEST |
|------|-----------|----------|
| 等价基础 | 表达式值 vs 预计算值 | 表达式 vs 常量折叠值 |
| 变换层级 | 整个表达式替换为预计算列 | 表达式替换为计算出的常量 |
| 覆盖深度 | ✅✅（系统性覆盖操作×类型） | ⚠️（依赖表达式生成器） |
| 差异 | 派生表是持久化的，可多次查询 | 常量只用于单次比较 |

**结论**：有部分重叠（都是"值替换"），但 CODDTEST 的常量折叠是查询内替换，EDC 是跨表替换。EDC 更系统化，CODDTEST 更轻量。

#### vs 事务 Oracle（WRITE_CHECK/FUCCI/TX_INFER）

| 维度 | EDC(论文) | 事务Oracle |
|------|-----------|-----------|
| 测试对象 | 数据操作实现 | 事务隔离/MVCC |
| 等价基础 | 数据值等价 | 调度等价/MVCC可见性等价 |
| Bug 类型 | 完全不同（数据层 vs 事务层） | 完全不同 |

**结论**：无任何重叠，完全不同领域。

---

## 6. EDC 优势与劣势

### 6.1 优势

| # | 优势 | 说明 |
|---|------|------|
| A1 | **填补空白维度** | SQLancer 在数据操作层几乎没有覆盖，EDC 系统性覆盖函数×类型×操作组合 |
| A2 | **与现有 Oracle 无重叠** | 论文实测 35 个独有 Bug，0 个与 NoREC/EET/Radar 重叠 |
| A3 | **Bug 类型独特** | 隐式类型转换（57.4%）、日期操作（24.1%）、字符串处理（16.7%）——这些 Bug 其他工具无法发现 |
| A4 | **操作覆盖广** | 每个 DBMS 381-1351 种操作，SQLancer ExpressionGenerator 远达不到 |
| A5 | **等价性直观** | "表达式值 = 预计算值"是最简单的等价关系，容易理解和实现 |
| A6 | **MySQL SET 配置测试** | 随机修改 sql_mode/charset/time_zone 等配置，测试配置影响下的数据操作行为 |

### 6.2 劣势

| # | 劣势 | 说明 |
|---|------|------|
| D1 | **仅单表** | 不支持 JOIN，论文承认 TLP 发现的 6 个 JOIN Bug EDC 无法覆盖 |
| D2 | **无 Reducer** | Bug 发现后无最小化，复现效率低 |
| D3 | **Python 实现** | 无法直接集成到 Java SQLancer 框架 |
| D4 | **无并发** | 单线程无限循环，无法利用 SQLancer 的多线程架构 |
| D5 | **操作列表静态** | seed 文件硬编码，无法动态发现 DBMS 新增操作 |
| D6 | **表达式生成简陋** | depth≤5，仅支持比较/逻辑/CASE/子查询，无窗口函数、WITH、UNION |
| D7 | **窗口函数不支持** | RANK/ROW_NUMBER 等排序敏感操作无法等价构造 |
| D8 | **无阻抗反馈** | 不跟踪操作成功率，大量时间浪费在不兼容的类型×操作组合上 |

---

## 7. 改进建议

### 7.1 P0：集成论文版 EDC 为新 Oracle（命名为 EDC_DATA）

**理由**：填补 SQLancer 在数据操作层的空白维度，预期可发现现有 Oracle 无法发现的 Bug 类型。

**实现要点**：

1. 新建 `EDCDataOracle`（位于 `common/oracle/edcdata/`），避免与现有 `EDCRadarBase`（Radar 风格，工厂枚举 `EDC_RADAR`）混淆
2. 利用 SQLancer 现有的 `GlobalState`、`ExpressionGenerator`、`Schema` 体系，而非 EDC Python 项目的简陋实现
3. 核心差异点实现：
   - 派生表构造：`CREATE TABLE t1 AS SELECT (expr) AS r, other_cols FROM t0`
   - 类型推断：查询 `information_schema.columns` 获取派生列类型
   - 等价查询生成：按论文 Table 2 的变换规则实现
   - 聚合等价：HAVING → WHERE 转换

**对齐论文**的关键配置：
- 每表最多 5 列输入 + 3 列附加/分组
- 每表最多 100 行
- 每次测试 1000 条 SELECT
- 操作列表从 DBMS 文档提取（MySQL: 381, PostgreSQL: 1351）

### 7.2 P1：动态操作发现替代静态种子文件

**当前问题**：EDC Python 项目用硬编码 `seed/{db}/func` 文件列出操作，无法适应 DBMS 版本更新。

**改进方案**：

1. 运行时查询 `information_schema` 和 DBMS 系统表动态发现可用函数
2. MySQL: `SELECT ROUTINE_NAME FROM information_schema.ROUTINES WHERE ROUTINE_TYPE='FUNCTION'`
3. PostgreSQL: `SELECT proname FROM pg_proc WHERE prokind='f'`
4. 过滤非确定性函数（RAND、NOW、UUID 等）
5. 构建操作×类型兼容矩阵，运行前做轻量 SELECT 验证

### 7.3 P2：利用 SQLancer ExpressionGenerator 增强 EDC

**当前问题**：EDC Python 项目的表达式生成器极简（depth≤5，仅比较/逻辑/CASE/子查询）。

**改进方案**：

1. 直接复用 SQLancer 各 DBMS 的 `ExpressionGenerator`（MySQL 已有 13 种 Action，PostgreSQL 更丰富）
2. 在 WHERE/HAVING 条件生成中使用 SQLancer 的完整表达式类型覆盖
3. 保留 EDC 的核心创新（表达式→预计算列替换），但用 SQLancer 更强的表达式生成能力填充 WHERE 条件

### 7.4 P3：多表扩展（超越论文的单表限制）

**论文承认的局限**：EDC 仅支持单表操作，无法发现 JOIN 相关的数据操作 Bug。

**改进方案**：

1. **两表等价构造**：对 JOIN 上的表达式做预计算
   ```
   -- 原始：SELECT CONCAT(t0.a, t1.b) FROM t0 JOIN t1 ON ...
   -- 派生：SELECT r FROM t_trans WHERE ...（t_trans.r = 预计算 CONCAT 值）
   ```
2. **子查询等价**：将子查询中的数据操作表达式替换为预计算值
3. 参考 JIR Oracle 的 JOIN 生成器，构建多表 EDC 测试场景

### 7.5 P4：MySQL SET 配置测试集成

**EDC 项目的独特特性**：随机修改 MySQL 配置参数（sql_mode、charset、time_zone、foreign_key_checks等），测试配置变更下的数据操作行为。

**改进方案**：

1. 在 `EDCDataOracle.check()` 中以低概率（10%）执行 `SET` 语句
2. 配置列表从 `MySQLOptions` 或专用配置文件加载
3. 重点测试：`sql_mode`（影响隐式类型转换行为）、`time_zone`（影响日期函数）、`character_set`（影响字符串处理）

### 7.6 P5：等价构造策略扩展

**当前 EDC 的等价构造方式**：`CREATE TABLE t1 AS SELECT expr AS r ... FROM t0`

**可扩展的等价构造策略**：

| 策略 | 等价关系 | 检测的 Bug |
|------|---------|-----------|
| **值替换**（论文原版） | expr → precomputed column | 数据操作实现 Bug |
| **类型替换** | INT列 → VARCHAR(CAST(INT AS VARCHAR)) | 隐式类型转换 Bug |
| **NULL注入** | 非空列 → COALESCE(col, fallback) | NULL 处理 Bug |
| **精度替换** | DECIMAL(10,6) → DECIMAL(38,0) | 精度丢失 Bug |
| **约束构造** | 加 NOT NULL/CHECK 约束 | 约束评估 Bug（与 Radar 风格 EDC 融合） |

### 7.7 P6：EDC + EET 融合策略

**原理**：EET 的语义保真变换（DeMorgan、BETWEEN→比较、EXISTS→IN等）可以应用于 EDC 的派生表查询中，实现双重等价验证。

```
原始: SELECT expr FROM t0 WHERE complex_predicate
步骤1(EDC): SELECT r FROM t1 WHERE complex_predicate（expr→r替换）
步骤2(EET): SELECT r FROM t1 WHERE DeMorgan(complex_predicate)
三路对比: 原始 vs EDC变换 vs EET+EDC变换
```

任何一对不一致即为 Bug，且可区分 Bug 来源（数据操作层 vs 谓词优化层）。

### 7.7 P7：参考业内最新研究的改进

| 来源 | 启发 | 适用改进 |
|------|------|---------|
| **SQUIRREL (ISSTA 2023)** | 覆盖率引导的查询生成 | 用覆盖率指标指导 EDC 的操作×类型组合优先级 |
| **DOMINO (ICSE 2024)** | 约束满足的数据生成 | 为 EDC 的 base table 生成满足特定约束的数据（如"字符串含数字前缀为0"） |
| **Radar (ISSTA 2024)** | 元数据约束对比 | 与 SQLancer 版 EDC 融合，同时测约束优化和数据操作 |
| **SemBug/SONAR (ISSTA 2024)** | 优化/非优化路径对比 | 在 EDC 等价查询中注入优化器开关，测配置影响 |
| **TLP (ICSE 2020)** | 三值逻辑分区 | 将 EDC 的 WHERE 条件用 TLP 分区验证，双重等价 |
| **ADUSA (ASE 2021)** | 通用 SQL 数据生成 | 替代 EDC 的简单随机数据生成，提升触发边界值的概率 |

---

## 8. 优先级排序与实施路线

| 优先级 | 改进项 | 预期收益 | 实现周期 |
|--------|--------|---------|---------|
| **P0** | 集成论文版 EDC 为 `EDC_DATA` Oracle | 填补空白维度，预期发现新类型 Bug | 3-5 天 |
| **P1** | 动态操作发现替代静态种子 | 自适应 DBMS 版本，减少维护成本 | 1 天 |
| **P2** | 复用 SQLancer ExpressionGenerator | 大幅增强 WHERE 条件多样性 | 0.5 天（架构已支持） |
| **P3** | 多表扩展 | 覆盖 JOIN 上的数据操作 Bug | 2-3 天 |
| **P4** | MySQL SET 配置测试 | 发现配置影响下的 Bug | 1 天 |
| **P5** | 等价构造策略扩展 | 更多 Bug 类型 | 2 天 |
| **P6** | EDC + EET 融合 | 双重等价验证，区分 Bug 来源 | 2 天 |

---

## 9. 结论

1. **EDC（论文版）与 SQLancer 所有现有 Oracle 完全互补**：它检测的是数据操作实现层的 Bug（隐式类型转换、溢出、精度丢失），而现有 Oracle 几乎全部聚焦在查询优化层和事务隔离层。

2. **SQLancer 已集成的 EDC_RADAR**（约束优化），与论文 EDC_DATA（数据操作）已通过命名清晰区分。

3. **论文实测数据证明价值**：7 个 DBMS 上发现 54 个 Bug（39 个确认），其中 35 个与现有工具无重叠。24 小时对比中 EDC 发现 40 个 Bug vs TLP 9 个 + Radar 7 个 + EET 6 个。

4. **最大改进空间**：将论文 EDC 的核心创新（表达式→预计算列替换）与 SQLancer 的强大基础设施（ExpressionGenerator、多线程、Reducer、ExpectedErrors）结合，实现远超 Python 原版的测试能力。