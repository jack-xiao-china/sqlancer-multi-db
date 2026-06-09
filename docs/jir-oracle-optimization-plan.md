# JIR Oracle 集成优化方案

> **初始日期**：2026-06-05  
> **最后更新**：2026-06-09（v2.4.5）  
> **基于**：`docs/jir-oracle-comparison-analysis.md` + `docs/jir-oracle-third-round-analysis.md` 差异分析报告

## 背景与动机

当前 JIR Oracle 集成版本与原始实现（`GeneralJIROracle.java`）存在核心算法偏差和语义缺陷。经过三轮深度分析和优化，已完成全部关键修复。

## 优化总览

### 第一轮（v2.4.2）：核心算法对齐

| 优先级 | 项目 | 状态 |
|--------|------|------|
| P0 | Rule 1 核心算法重构（多列 + NULL 替换） | ✅ |
| P1 | MULTISET 比较语义 + canonicalizeResultValue | ✅ |
| P2 | GaussDB-A NATURAL JOIN AST 支持 | ✅ |
| P3 | GaussDB-A Rule 5 ON TRUE 语义修复 | ✅ |

### 第二轮（v2.4.3）：列选择修复 + 原始功能补齐

| 优先级 | 项目 | 状态 |
|--------|------|------|
| P0 | Rule 1 改为随机单列（修复 getString(1) 只比较第一列 BUG） | ✅ |
| P1 | 添加 ORDER BY 支持（对齐原始 generateOrderBys） | ✅ |
| P2 | 添加 Reproducer 验证模式（对齐原始 Reproducer） | ✅ |
| P3 | Rule 2-6 改为随机左表列（提升列覆盖多样性） | ✅ |

### 第三轮（v2.4.4）：共享 Fetch Columns + PostgreSQL NATURAL AST + 条件简化

| 优先级 | 项目 | 状态 |
|--------|------|------|
| P0 | Rules 2-6 共享 Fetch Columns（修复独立随机选列 BUG） | ✅ |
| P1 | PostgreSQL NATURAL JOIN AST 支持 | ✅ |
| P3 | MySQL createBaseSelect 条件简化 | ✅ |

## 实施细节

### 第一轮：核心算法对齐（v2.4.2）

#### P0: Rule 1 核心算法重构
为每个 Transformer 新增：
- `generateRandomFetchColumns()` — 随机选两表列
- `buildAntiJoinFetchColumns()` — 右表列 → NULL 替换
- `buildAntiJoinSQLWithCols()` — 使用自定义列构建 Anti-Join SQL

#### P1: MULTISET 比较语义
- `ComparatorHelper` 新增 `assumeResultSetsAreEqualMultiset()`
- `JIROracle` 调用处改用 multiset 比较 + `canonicalizeResultValue`

#### P2: GaussDB-A NATURAL JOIN
- `GaussDBAJoinType` 新增 `NATURAL` 枚举
- `GaussDBAToStringVisitor` 添加 NATURAL 渲染
- Rule 6 改用 AST 构建

#### P3: GaussDB-A ON TRUE
- `ON 1` 改为 `ON 1 = 1`（`GaussDBABinaryComparisonOperation`）

### 第二轮：列选择修复 + 原始功能补齐（v2.4.3）

#### P0: Rule 1 随机单列（关键 BUG 修复）
**问题**：`getResultSetFirstColumnAsString()` 只提取第一列，多列 SELECT 中右表列的 NULL 替换从未被验证。

**修复**：`generateRandomFetchColumns()` 改为返回单个随机列（来自左表或右表）：
```java
// Before: 多列（只比较第一列，右表列 NULL 替换被忽略）
List<MySQLColumn> subset = Randomly.nonEmptySubset(allCols);
return subset.stream().map(...).collect(Collectors.toList());

// After: 单列（NULL 替换精确验证）
return Collections.singletonList(
    MySQLColumnReference.create(Randomly.fromList(allCols), null));
```

#### P1: ORDER BY 支持
在 `JIROracle.check()` 中以低概率为 source query 添加 `ORDER BY 1`：
```java
if (Randomly.getBooleanWithRatherLowProbability()) {
    sourceQuery = sourceQuery + " ORDER BY 1";
}
```
对齐原始 `GeneralJIROracle` 的 `gen.generateOrderBys()` 行为，测试不同优化器路径。

#### P2: Reproducer 验证模式
新增 `JIRReproducer` 内部类：
- 检测到 mismatch 时保存 source/target 查询
- `bugStillTriggers()` 重放查询确认 Bug 可复现
- 区分确定性 optimizer Bug 和瞬时性数据不一致

#### P3: Rule 2-6 随机左表列
`getLeftTableFetchColumns()` 从固定 `cols.get(0)` 改为 `Randomly.fromList(cols)`，提升所有规则的列覆盖多样性。

## 修改文件清单

| 文件 | 变更类型 | 版本 |
|------|----------|------|
| `src/sqlancer/ComparatorHelper.java` | 新增 `assumeResultSetsAreEqualMultiset()` | v2.4.2 |
| `src/sqlancer/common/oracle/jir/JIROracle.java` | multiset 比较 + ORDER BY + Reproducer | v2.4.2/v2.4.3 |
| `src/sqlancer/mysql/oracle/MySQLJIRTransformer.java` | Rule 1 重构 + 随机列 + 共享 fetchColumns | v2.4.2/v2.4.3/v2.4.4 |
| `src/sqlancer/postgres/oracle/ext/PostgresJIRTransformer.java` | Rule 1 重构 + 随机列 + 共享 fetchColumns + NATURAL AST | v2.4.2/v2.4.3/v2.4.4 |
| `src/sqlancer/gaussdbm/oracle/GaussDBMJIRTransformer.java` | Rule 1 重构 + 随机列 + 共享 fetchColumns | v2.4.2/v2.4.3/v2.4.4 |
| `src/sqlancer/gaussdba/oracle/GaussDBAJIRTransformer.java` | Rule 1 重构 + 随机列 + Rule 5/6 + 共享 fetchColumns | v2.4.2/v2.4.3/v2.4.4 |
| `src/sqlancer/gaussdba/ast/GaussDBAJoin.java` | 新增 NATURAL 枚举 | v2.4.2 |
| `src/sqlancer/gaussdba/GaussDBAToStringVisitor.java` | NATURAL 渲染 | v2.4.2 |
| `src/sqlancer/postgres/ast/PostgresJoin.java` | 新增 NATURAL 枚举 + createJoin 处理 | v2.4.4 |
| `src/sqlancer/postgres/PostgresToStringVisitor.java` | NATURAL 渲染 + ON clause 跳过 | v2.4.4 |
| `src/sqlancer/gaussdbm/GaussDBMProvider.java` | `createDatabase()` 改为 schema 隔离 + `--target-database` | v2.4.5 |
| `src/sqlancer/gaussdbm/GaussDBMOptions.java` | 新增 `--target-database` 参数 | v2.4.5 |

### 第四轮修复（v2.4.5）：GaussDB-M Provider 连接方式修复

#### 问题
GaussDB-M Provider 的 `createDatabase()` 使用 `CREATE DATABASE ... DBCOMPATIBILITY 'M'` 创建 M 兼容数据库，但：
1. 远程 GaussDB 实例不支持 `DBCOMPATIBILITY 'M'` 语法（报 `syntax error at or near "DBCOMPATIBILITY"`）
2. 即使语法正确，用户可能没有 CREATE DATABASE 权限
3. `DROP SCHEMA ... CASCADE` 在 M 兼容模式下不被支持

#### 修复
- 添加 `--target-database` 参数（与 GaussDB-A 保持一致）
- `createDatabase()` 改为连接预创建的 M 兼容数据库 + schema 隔离（DROP/CREATE SCHEMA + SET search_path）
- 去掉 `DROP SCHEMA ... CASCADE`（M 兼容模式不支持 CASCADE）
- `--target-database` 为必填参数，用户需预先创建 M 兼容数据库

#### 使用示例
```bash
# 1. 先在 GaussDB 中创建 M 兼容数据库
CREATE DATABASE testm WITH DBCOMPATIBILITY 'M';

# 2. 运行 JIR Oracle 测试
java -jar sqlancer.jar --username sqlbuilder1 --password your_password \
  --connection-url "jdbc:opengauss://host:port/testm" \
  gaussdb-m --oracle JIR --target-database testm
```

## 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 随机列可能选中右表列 | Rule 2-6 的 target 查询结构不匹配 | Rule 2-6 使用 `getLeftTableFetchColumns()`（仅左表） |
| ORDER BY 1 在部分 DBMS 不兼容 | 查询失败 | ExpectedErrors 机制捕获 |
| Reproducer 重放时表数据已变 | 误判为 false positive | 仅在 `bugStillTriggers()` 返回 true 时报告 |
| 共享 fetchColumns 时 AST 节点被修改 | 列列表不一致 | AST 节点只读不修改，可安全共享 |
| PostgreSQL NATURAL JOIN 渲染未在 SQLancer 其他模块使用 | 可能影响非 JIR Oracle 的 JOIN 生成 | NATURAL 仅在 JIR Transformer 中使用，PostgresJoin.getJoins() 不会随机选中 NATURAL |

## 验收标准

1. `mvn compile -q` 零错误零警告
2. Rule 1 生成的 SQL 为单列且包含右表列的 NULL 替换
3. Rules 2-6 source/target 查询使用同一 fetch columns（无独立随机选择）
4. PostgreSQL Rule 6 使用 AST 渲染 NATURAL JOIN（非原始 SQL 拼接）
5. 4 DBMS 测试均正常运行
