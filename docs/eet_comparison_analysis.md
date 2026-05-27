# EET 原生工具 vs SQLancer EET Oracle 对比分析

> 分析日期：2026-05-25
> 原生 EET 源码：`D:\Jack.Xiao\dbtools\EET-main`（C++/SQLsmith）
> SQLancer EET 源码：`src/sqlancer/common/oracle/eet/` + 各 DBMS 实现（Java）

---

## 1. 项目架构对比

| 维度 | 原生 EET | SQLancer EET |
|------|---------|-------------|
| **语言** | C++ | Java |
| **基础框架** | SQLsmith（语法随机生成器） | SQLancer（差分测试框架） |
| **架构风格** | 平面结构，每节点自带 `equivalent_transform()` | 模板方法+策略适配器模式（EETOracleBase + EETTransformAdapter） |
| **AST 遍历** | 节点自递归：每个 AST 节点有 `equivalent_transform()` 方法 | 外部遍历器：`EETTransformerBase.transformExpression()` 递归 + `adapter.mapChildren()` |
| **变换输出机制** | 双态渲染：同一 AST 通过 `is_transformed` 标志切换输出原始/变换 | 拷贝式：`mapChildren` 生成新节点，原始和变换为独立 AST |
| **DBMS 支持** | 8个（MySQL/PG/SQLite/ClickHouse/TiDB/OB/YugaByte/Cockroach） | 4个（MySQL/PG/GaussDB-A/GaussDB-M），GaussDB-A/M 未重构 |
| **代码复用** | DBMS 间共享同一 AST 和变换逻辑，仅 schema 适配不同 | MySQL/PG 共享 common base；GaussDB-A/M 仍有本地拷贝 |

**关键差异**：原生 EET 用 C++ 的 **原地变换+双态渲染**（同一 AST 两种输出模式），SQLancer 用 Java 的 **拷贝变换**（生成新 AST）。前者更紧凑但难以并行，后者天然支持并行和类型安全。

---

## 2. 核心算法对比

| 维度 | 原生 EET | SQLancer EET |
|------|---------|-------------|
| **变换触发** | 每个节点独立决定是否变换（内置 `equivalent_transform()`） | 外部 `EETTransformerBase` 递归遍历，对每个节点尝试所有规则 |
| **规则优先级** | 节点内置变换先执行，语义改写作为节点的 `equivalent_transform()` 分支 | **语义规则优先**：先尝试所有语义改写规则，再尝试 CASE/Bool 包裹 |
| **随机重试** | 无显式重试（每个节点只做一次变换决策） | 最多 200 次重试（`transformExpression` 的 retry loop） |
| **back_transform** | 每个节点有 `back_transform()` 方法，用于最小化还原 | `EETBackTransformTracker` 记录变换步骤，但**当前未用于还原**；最小化靠 ComponentReducer 的节点替换策略 |
| **子节点递归顺序** | 先变换自身，再递归变换子节点 | 先递归变换子节点（`mapChildren`），再对自身尝试规则 |

---

## 3. 语义改写规则对比

### 3.1 规则覆盖率

| 规则 | 原生 EET | SQLancer MySQL | SQLancer PG | SQLancer GaussDB-A | SQLancer GaussDB-M |
|------|---------|---------------|------------|-------------------|-------------------|
| **DeMorgan** (AND↔OR+NOT) | ✅ | ✅ | ✅ | ❌（未重构） | ❌（未重构） |
| **BETWEEN→比较** | ✅ | ✅ | ✅ | ❌ | ❌ |
| **EXISTS→IN** | ✅ | ✅ | ✅ | ❌ | ❌ |
| **IN→EXISTS** | ✅ | ✅ | ✅ | ❌ | ❌ |
| **INTERSECT→EXISTS** | ✅ | ❌（MySQL无INTERSECT） | ✅（stub，返回null） | ❌ | ❌ |
| **EXCEPT→NOT EXISTS** | ✅ | ❌（MySQL无EXCEPT） | ✅（stub，返回null） | ❌ | ❌ |
| **Bool CASE包裹**（True/False AND/OR） | ✅ 2种 | ✅ 2种 | ✅ 2种 | ✅（本地实现） | ✅（本地实现） |
| **Value CASE包裹**（CASE WHEN） | ✅ 4种 | ✅ 4种 | ✅ 4种 | ✅（本地实现） | ✅（本地实现） |
| **ConstBool 变换**（0/1重写） | ✅ | ✅ | ✅ | ✅ | ✅ |
| **总计语义规则** | 7类 | 4个 | 6个（2个stub） | 0个 | 0个 |

### 3.2 EXISTS→IN 变换细节对比

| 维度 | 原生 EET | SQLancer |
|------|---------|---------|
| **NULL安全处理** | `CASE WHEN pred IS NULL THEN FALSE ELSE pred END` | 完全一致 |
| **GROUP BY/WINDOW 跳过** | 跳过 GROUP BY 或 WINDOW 的子查询 | 跳过 GROUP BY |
| **UNION 子查询** | 仅包裹 unioned_query 本身 | 仅处理单 SELECT，不处理 UNION |

### 3.3 IN→EXISTS 变换细节对比

| 维度 | 原生 EET | SQLancer |
|------|---------|---------|
| **NULL安全处理** | `CASE WHEN (lhs IN ...) IS NOT NULL THEN EXISTS(...) ELSE NULL END` | 完全一致 |
| **UNION 子查询** | 两侧分别变换后构造 EXISTS over union | 仅处理单元素 IN，不处理 UNION |

### 3.4 INTERSECT/EXCEPT→EXISTS 变换对比

| 维度 | 原生 EET | SQLancer |
|------|---------|---------|
| **NULL感知等值** | `q1.col=q2.col OR (q1.col IS NULL AND q2.col IS NULL)` | 逻辑一致，但 PG 实现**尚未完成**（返回 null） |
| **GROUP BY/WINDOW 跳过** | 无显式跳过 | 跳过 GROUP BY 或 WINDOW |

---

## 4. 查询生成（语法覆盖）对比

| SQL 特性 | 原生 EET | SQLancer MySQL | SQLancer PG |
|----------|---------|---------------|------------|
| **Plain SELECT** | ✅ | ✅ | ✅ |
| **UNION/UNION ALL** | ✅ | ✅ | ✅ |
| **INTERSECT** | ✅ | ❌（MySQL不支持） | ✅ |
| **EXCEPT** | ✅ | ❌ | ✅ |
| **WITH/CTE** | ✅ | ✅ | ✅ |
| **Derived table（FROM子查询）** | ✅ | ✅ | ✅ |
| **LATERAL** | ✅ | ❌ | ❌ |
| **TABLESAMPLE** | ✅ | ❌ | ❌ |
| **JOIN（INNER/LEFT/CROSS/RIGHT/FULL）** | ✅ 全部类型 | ✅（变换ON条件） | ✅（变换ON条件） |
| **GROUP BY + HAVING** | ✅ | ✅ | ✅ |
| **Window 函数** | ✅ | ❌（不生成） | ❌（不生成） |
| **ORDER BY** | ✅ | ❌ | ❌ |
| **LIMIT** | ✅ | ❌ | ❌ |
| **SELECT FOR UPDATE** | ✅ | ❌ | ❌ |
| **Subquery in SELECT list** | ✅ | ❌ | ❌ |
| **INSERT...SELECT** | ✅（有独立 tester） | ❌ | ❌ |
| **CREATE TRIGGER** | ✅（部分DBMS） | ❌ | ❌ |
| **MERGE/UPSERT** | ✅（语法存在） | ❌ | ❌ |

**关键差距**：原生 EET 的查询生成覆盖了 LATERAL、TABLESAMPLE、Window 函数、ORDER BY、LIMIT、SELECT FOR UPDATE、scalar subquery、INSERT...SELECT 等高级特性。SQLancer EET 当前只覆盖 SELECT+UNION+CTE+derived+JOIN，**缺少大量高级语法特性**。

---

## 5. Oracle/验证逻辑对比

| 维度 | 原生 EET | SQLancer EET |
|------|---------|-------------|
| **比较方法** | multiset（C++ `std::multiset`） | multiset（排序字符串key列表比较） |
| **NULL处理** | NULL 值按字符串表示比较（依赖 JDBC driver 串化） | NULL 统一转为字符串 `"null"` 后比较 |
| **重复确认** | ✅ 双次执行确认 | ✅ 双次执行确认 |
| **浮点精度** | 四舍五入到2位小数后 BKDR hash 比较 | 无浮点规范化，原始字符串比较 |
| **结果行数上限** | `MAX_PROCESS_ROW_NUM=10000` | `MAX_PROCESS_ROW_NUM=10000` |
| **Crash检测** | ✅（捕获异常分类 + 服务器进程监控 + `--ignore-crash` 继续） | ✅（`EETCrashTracker`：SQLState 08xx/XXxx/58xx + MySQL errorCode） |
| **Crash后的处理** | 终止测试或继续（可选） | 抛 `IgnoreMeException` 跳过当前测试 |

---

## 6. DML 测试对比

| 维度 | 原生 EET | SQLancer EET |
|------|---------|-------------|
| **UPDATE 测试** | ✅ `qcn_update_tester` | ✅ `EETDMLOracleBase` → MySQL/PG 实现 |
| **DELETE 测试** | ✅ `qcn_delete_tester` | ✅ `EETDMLOracleBase` → MySQL/PG 实现 |
| **INSERT...SELECT** | ✅ `qcn_insert_select_tester` | ❌ |
| **变换范围** | UPDATE: SET列表+WHERE；DELETE: WHERE | UPDATE: WHERE；DELETE: WHERE（**SET列表不变换**） |
| **数据恢复** | `dut->reset_to_backup()` | DELETE ALL + INSERT from snapshot |
| **差值计算** | UPDATE: after-before; DELETE: before-after | 同逻辑（`computeChangedRows/computeDeletedRows`） |
| **GaussDB-A/M DML** | — | ❌ 无 DML oracle |

**关键差异**：原生 EET 的 UPDATE 测试也变换 SET 列表中的表达式，SQLancer 只变换 WHERE 条件。原生 EET 支持 INSERT...SELECT 测试，SQLancer 不支持。

---

## 7. Bug 最小化/Reproducer 对比

| 维度 | 原生 EET | SQLancer EET |
|------|---------|-------------|
| **查询最小化** | ✅ 两轮：①原查询节点→NULL替换 ②变换节点→back_transform还原 | ✅ 两阶段：①原AST节点→NULL替换+重变换 ②CASE节点→ELSE/THEN分支替换 |
| **数据库最小化** | ✅ delta-debugging：逐步删除 CREATE/INSERT 语句 | ❌ 无数据库最小化 |
| **还原方式** | `back_transform()` 方法逐节点还原 | 重新变换（使用保存的 seed 重播），非逐节点还原 |
| **GaussDB-A** | — | ❌ 无 component reduction |
| **输出文件** | `origin.sql`/`eet.sql`/`origin.out`/`eet.out`/`minimized/` | AssertionError 含完整 SQL 文本 |

---

## 8. Impedance/反馈机制对比

| 维度 | 原生 EET | SQLancer EET |
|------|---------|-------------|
| **阻抗跟踪** | ✅ `impedance.cc`：按 production 类型跟踪成功/失败率，>99%失败率 blacklist | ✅ `EETImpedanceTracker`：按表达式类型跟踪，>99% blacklist |
| **Back-transform记录** | ✅ 每个节点 `back_transform()` 可逐节点还原 | ✅ `EETBackTransformTracker` 记录每步，但**当前未使用** |
| **Blacklist 生效** | 生成阶段：`prod::match()` 检查 blacklist | 变换阶段：无直接 blacklist 集成 |
| **Failure Stats** | 无 | ✅ `EETFailureStats`（MySQL/GaussDB-M）：聚合失败错误消息，写 `target/eet-failures.txt` |

---

## 9. 配置选项对比

| 选项 | 原生 EET | SQLancer EET |
|------|---------|-------------|
| **测试轮数** | `--db-test-num` (默认50) | `--num-queries` |
| **表数量** | `--db-table-num` | SQLancer 全局 `--max-num-tables` |
| **随机种子** | `--seed` | `--random-seed` |
| **忽略 Crash** | `--ignore-crash` | 无（crash → IgnoreMeException 自动跳过） |
| **CPU亲和性** | `--cpu-affinity` | 无 |
| **多 DBMS 端口/路径** | 每个 DBMS 有独立选项 | `--host`/`--port` + DBMS 子命令 |
| **变换覆盖策略** | 无 | 无（默认随机） |
| **查询形状策略** | 无 | PG 有 CONSERVATIVE/AGGRESSIVE/BALANCED |

---

## 10. GaussDB-A/M 当前状态

| 维度 | GaussDB-A | GaussDB-M |
|------|-----------|-----------|
| **是否继承 EETOracleBase** | ❌（inline `check()`） | ❌（inline `check()`） |
| **是否使用 EETTransformerBase** | ❌（本地拷贝变换算法） | ❌（本地拷贝变换算法） |
| **语义改写规则** | 0个 | 0个 |
| **表达式节点覆盖** | 10种 | 11种 |
| **查询生成** | 仅 plain SELECT（UNION/CTE/derived 为 stub） | 完整（UNION/CTE/derived） |
| **DML oracle** | ❌ | ❌ |
| **Component reduction** | ❌ | ✅（本地实现） |
| **本地拷贝类** | EETMultisetComparator/EETQueryExecutor/EETResultSetUtil | 同上 |

---

## 11. 综合差距评估

### 11.1 SQLancer EET 优于原生 EET 的方面

1. **架构清晰度**：模板方法+适配器模式比 C++ 节点自递归更易维护和扩展
2. **类型安全**：Java 泛型 `EETTransformAdapter<E>` 保证方言 AST 类型安全
3. **并行支持**：拷贝式变换天然支持并行（原生 EET 的双态渲染依赖全局标志）
4. **Crash 分类**：`EETCrashTracker` 按 SQLState/ErrorCode 系统化分类，原生 EET 主要靠字符串匹配
5. **Failure Stats**：`EETFailureStats` 聚合失败模式便于分析
6. **集成框架**：SQLancer 统一的 GlobalState/Schema/Connection 管理比原生 EET 各 DBMS 独立实现更一致

### 11.2 原生 EET 优于 SQLancer EET 的方面

1. **语义规则数量**：7类规则 vs MySQL 4个/PG 6个（2个stub）
2. **查询生成覆盖**：原生支持 LATERAL/TABLESAMPLE/Window/ORDER BY/LIMIT/scalar subquery/INSERT...SELECT/TRIGGER 等，SQLancer 严重不足
3. **数据库最小化**：原生有 delta-debugging 数据库最小化，SQLancer 无
4. **back_transform 机制**：原生每个节点可逐节点还原，SQLancer 仅靠重播 seed 重变换
5. **DBMS 覆盖范围**：8个 vs 4个（GaussDB-A/M 未重构）
6. **UPDATE SET 变换**：原生变换 SET 列表表达式，SQLancer 仅变换 WHERE
6. **INSERT...SELECT 测试**：原生有独立 tester，SQLancer 无
7. **GROUP BY/WINDOW 跳过更完善**：原生 EXISTS→IN 同时跳过 WINDOW，SQLancer 仅跳过 GROUP BY

### 11.3 优先改进建议

| 优先级 | 改进项 | 影响 |
|--------|--------|------|
| **P0** | GaussDB-A/M 重构到 common base | 消除代码拷贝，启用语义规则，减少维护成本 |
| **P0** | PG INTERSECT/EXCEPT→EXISTS 实现 | 当前 2 个规则为 stub，PG 独有特性未利用 |
| **P1** | 查询生成扩展（LATERAL/Window/ORDER BY/LIMIT/scalar subquery） | 大幅提升语法覆盖，发现更多深层 bug |
| **P1** | UPDATE SET 列表变换 | 原生 EET 变换 SET 表达式，SQLancer 只变换 WHERE |
| **P2** | INSERT...SELECT tester | 增加一种 DML 测试维度 |
| **P2** | 数据库最小化（delta-debugging） | 缩小 bug reproducer，提高报告质量 |
| **P2** | EXISTS→IN 增加 WINDOW 跳过条件 | 防止非等价变换导致假阳性 |
| **P3** | back_transform 实际集成到最小化 | 当前 EETBackTransformTracker 未使用 |
| **P3** | Impedance blacklist 集成到生成阶段 | 当前 blacklist 仅影响变换，不影响查询生成 |