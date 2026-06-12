# EDC_DATA Oracle GaussDB 适配方案

> **✅ 适配完成（v2.5.0, 2026-06-11）**：所有阻断性 Bug 已修复，GaussDB-M 集成测试通过（200 次运行，81% 成功率，0 派生表创建失败）。

## 1. 环境验证结果（2026-06-11 实测）

**GaussDB 版本**: GaussDB Kernel 507.0.0 build 1268bd4d (2026-03-23)  
**连接地址**: 121.37.186.131:19995

### GaussDB-M (testm, MySQL 兼容模式)

| 测试项 | 结果 | 说明 |
|--------|------|------|
| JDBC 连接 | ✅ | `jdbc:opengauss://...` 正常 |
| Schema 隔离 | ✅ | `CREATE SCHEMA` + `SET search_path` 正常 |
| `DROP SCHEMA ... CASCADE` | ❌ | M 兼容模式不支持 CASCADE |
| `INFORMATION_SCHEMA` 查询 | ✅ | `TABLE_SCHEMA = current_schema` 可用 |
| `CREATE TABLE AS SELECT` (直接列) | ✅ | `SELECT c0 AS c0 FROM ...` 正常 |
| `CREATE TABLE AS SELECT` (表达式) | ❌ | **任何函数/算术表达式都报错** |
| `CREATE TABLE + INSERT INTO SELECT` | ✅ | 两步法可行 |

**关键限制**: GaussDB-M 的 `CREATE TABLE AS SELECT` **不支持任何表达式**（包括 `ABS()`, `CONCAT()`, `c0+1`, `CAST()` 等），仅支持直接列引用。这意味着当前的派生表创建方式完全不适用。

### GaussDB-A (testa, Oracle 兼容模式)

| 测试项 | 结果 | 说明 |
|--------|------|------|
| JDBC 连接 | ✅ | 正常 |
| Schema 隔离 | ✅ | `CREATE SCHEMA ... CASCADE` + `SET search_path` 正常 |
| `all_tab_columns` 视图 | ❌ | **不存在**！当前代码会直接 crash |
| `information_schema.columns` | ✅ | 可用，`table_schema = current_schema` |
| `CREATE TABLE AS SELECT` (表达式) | ✅ | `(c0 + 1) AS c0` 正常 |
| Oracle 函数 (NVL, DECODE, CONCAT) | ✅ | 全部正常 |

**关键问题**: 当前 `GaussDBAEDCDataTableBuilder` 使用 `all_tab_columns` 查询列类型，但该视图在 GaussDB-A 上**不存在**，必须改用 `information_schema.columns`。

---

## 2. 需修复的问题（共 4 个）

### 问题 A：GaussDB-M 不支持 CTAS 表达式（严重 — 架构级）

**现状**: `GaussDBMEDCDataTableBuilder.generateDerivedTableSQL()` 生成 `CREATE TABLE AS SELECT (expr) AS c0, ...`，在 GaussDB-M 上 100% 失败。

**修复方案**: 改为两步法（`requiresSeparateInsert() = true`）

```
步骤 1: 先获取派生列类型（在原始表上执行 SELECT 推断）
步骤 2: CREATE TABLE edc_t1_N (c0 TYPE, c1 TYPE, ...)  — 空表
步骤 3: INSERT INTO edc_t1_N SELECT (expr) AS c0, ... FROM edc_t0_N
```

**核心改动**：需要修改基类 `EDCDataTableBuilder.createDerivedTable()` 的流程，在 `requiresSeparateInsert()` 模式下先获取类型再创建空表：

```java
// 新增流程（requiresSeparateInsert = true 时）:
if (requiresSeparateInsert()) {
    // 1. 先查询派生列类型
    String exprType = queryDerivedColumnType(scenario, testExpr, errors);
    // 2. 创建空表（显式列定义）
    String createSQL = generateDerivedTableWithTypes(scenario, exprType, ...);
    // 3. INSERT 填充数据
    String insertSQL = generateDerivedInsertSQL(scenario, testExpr, ...);
}
```

**需要新增的抽象方法**:
- `generateDerivedTableWithTypes(scenario, exprType, otherColumnTypes, ...)` — 用显式类型创建空表
- `queryDerivedColumnType(scenario, testExpr, errors)` — 通过 LIMIT 0 查询推断表达式类型

### 问题 B：GaussDB-A `all_tab_columns` 不存在（严重）

**现状**: `GaussDBAEDCDataTableBuilder.generateDerivedTypeQuery()` 查询 `all_tab_columns`，在 GaussDB-A 上报 `Relation "all_tab_columns" does not exist`。

**修复方案**: 改用 `information_schema.columns`（已验证可用）

```java
// 修复前
SELECT data_type FROM all_tab_columns WHERE table_name = 'EDC_T1_1' AND column_name = 'C0'

// 修复后
SELECT data_type FROM information_schema.columns 
WHERE table_schema = current_schema AND table_name = 'edc_t1_1' AND column_name = 'c0'
```

注意：`information_schema` 中表名/列名用小写，不需要 `.toUpperCase()`。

### 问题 C：GaussDB-M 错误处理偏少（低）

当前仅 4 个错误模式，需补充至约 10 个（从 MySQL 19 个中选适用的）。

### 问题 D：GaussDB-A 错误处理偏少（低）

当前仅 4 个 Oracle 风格错误模式，需补充至约 10 个。

---

## 3. 实施计划

### Phase 1：修复核心 Bug（预计 1-2 天）

| 步骤 | 内容 | 涉及文件 |
|------|------|---------|
| 1.1 | 修改基类 `EDCDataTableBuilder`：支持 `requiresSeparateInsert()` 模式下的类型推断 + 显式建表流程 | `EDCDataTableBuilder.java` |
| 1.2 | 新增 `generateDerivedTableWithTypes()` 抽象方法 | `EDCDataTableBuilder.java` |
| 1.3 | `GaussDBMEDCDataTableBuilder`: `requiresSeparateInsert() → true`，实现 `generateDerivedTableWithTypes()` 和 `generateDerivedInsertSQL()` | `GaussDBMEDCDataTableBuilder.java` |
| 1.4 | `GaussDBMEDCDataTableBuilder`: 类型查询改用 `current_schema`（从 `DATABASE()` 改） | 同上 |
| 1.5 | `GaussDBAEDCDataTableBuilder`: 类型查询从 `all_tab_columns` 改为 `information_schema.columns` | `GaussDBAEDCDataTableBuilder.java` |
| 1.6 | 补充 GaussDB-M ExpectedErrors（~10 个模式） | `GaussDBMEDCDataOracle.java` |
| 1.7 | 补充 GaussDB-A ExpectedErrors（~10 个模式） | `GaussDBAEDCDataOracle.java` |
| 1.8 | PostgreSQL `PostgresEDCDataTableBuilder` 适配新流程（验证 `requiresSeparateInsert=true` 路径） | `PostgresEDCDataTableBuilder.java` |
| 1.9 | 编译验证 `mvn compile -q` | — |

### Phase 2：GaussDB-M 集成测试（预计 1-2 天）

| 步骤 | 内容 |
|------|------|
| 2.1 | 连接 GaussDB-M，运行 `--oracle EDC_DATA --num-tries 500` |
| 2.2 | 分析日志：重点关注 CTAS 替代方案是否稳定 |
| 2.3 | 迭代修复：补充 ExpectedErrors、修正 SQL 语法兼容性 |
| 2.4 | 验证 Seed 文件：逐个确认 func.txt 中 340 个函数在 GaussDB-M 上可用 |
| 2.5 | 裁剪 Seed 文件：移除不支持的函数/类型（如 VECTOR、部分空间类型） |
| 2.6 | 目标：稳定运行 500 次无 crash |

### Phase 3：GaussDB-A 集成测试（预计 1-2 天）

| 步骤 | 内容 |
|------|------|
| 3.1 | 连接 GaussDB-A，运行 `--oracle EDC_DATA --num-tries 500` |
| 3.2 | 分析日志：重点关注 Oracle 语法兼容性 |
| 3.3 | 迭代修复：补充 ExpectedErrors |
| 3.4 | 验证 Seed 文件：确认 66 个 Oracle 函数 + 16 个类型可用 |
| 3.5 | 裁剪/扩展 Seed 文件 |
| 3.6 | 目标：稳定运行 500 次无 crash |

### Phase 4：回归验证 + 文档（预计 0.5 天）

| 步骤 | 内容 |
|------|------|
| 4.1 | 回归测试：MySQL + PostgreSQL EDC_DATA 确认未受影响 |
| 4.2 | 更新 release_notes.md + 版本号 |
| 4.3 | 更新 README.md + USER_GUIDE.md GaussDB 章节 |

---

## 4. 基类改动详细设计

当前 `EDCDataTableBuilder.createDerivedTable()` 流程：

```
┌─────────────────────────────────────────────┐
│ 1. generateDerivedTableSQL() → CREATE TABLE │
│    AS SELECT (expr) AS c0, ... FROM t0      │
│ 2. execute(createSQL)                        │
│ 3. if (requiresSeparateInsert)               │
│    → generateDerivedInsertSQL()              │
│    → execute(insertSQL)                      │
└─────────────────────────────────────────────┘
```

修改后（`requiresSeparateInsert = true` 时）：

```
┌─────────────────────────────────────────────────────┐
│ if (requiresSeparateInsert):                         │
│   1. queryExprType(testExpr) → 通过 LIMIT 0 推断类型 │
│   2. generateDerivedTableWithTypes(exprType) →       │
│      CREATE TABLE edc_t1_N (c0 TYPE, c1 TYPE, ...)  │
│   3. execute(createSQL) → 创建空表                    │
│   4. generateDerivedInsertSQL() →                     │
│      INSERT INTO edc_t1_N SELECT (expr), ... FROM t0 │
│   5. execute(insertSQL) → 填充数据                    │
│ else:                                                │
│   1. generateDerivedTableSQL() → CTAS (原流程不变)    │
│   2. execute(createSQL)                               │
└─────────────────────────────────────────────────────┘
```

**表达式类型推断方法**（新增）:

```java
// 在原始表上执行 SELECT (expr) LIMIT 0，然后通过 INFORMATION_SCHEMA 
// 或 JDBC ResultSetMetaData 获取表达式返回类型
protected String queryExprType(EDCDataTestScenario scenario, String testExpr) {
    String query = "SELECT (" + testExpr + ") AS _probe FROM " 
                   + scenario.getBaseTableName() + " LIMIT 0";
    // 使用 JDBC DatabaseMetaData 获取列类型
    // 或创建临时视图再查 INFORMATION_SCHEMA
}
```

**影响范围**：
- MySQL / GaussDB-M: `requiresSeparateInsert = false` → **不受影响**（MySQL 继续用 CTAS）
- PostgreSQL: `requiresSeparateInsert = true` → 需要适配新流程（当前 PG 的 `WITH NO DATA` 方式需要调整）
- GaussDB-M: `requiresSeparateInsert = true` → **新流程**
- GaussDB-A: `requiresSeparateInsert = false` → **不受影响**（CTAS 正常）

---

## 5. 风险与应对

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| 基类改动影响 PostgreSQL 已有实现 | 中 | 高 | PostgreSQL 也需回归测试，可能需要单独适配 |
| GaussDB-M 表达式类型推断失败 | 中 | 中 | 备选：用 `ResultSetMetaData` 从 `PreparedStatement` 获取 |
| GaussDB-M Seed 文件中大量函数不可用 | 高 | 中 | 集成测试阶段逐一验证并裁剪 |
| GaussDB-A 部分 Oracle 语法不兼容 | 中 | 低 | ExpectedErrors 过滤 + Seed 文件裁剪 |

---

## 6. 验收标准

| 指标 | 目标 |
|------|------|
| GaussDB-M 集成测试 | `--oracle EDC_DATA --num-tries 500` 稳定运行，无 crash |
| GaussDB-A 集成测试 | `--oracle EDC_DATA --num-tries 500` 稳定运行，无 crash |
| MySQL/PG 回归 | EDC_DATA 功能不受影响 |
| 编译 | 0 errors, 0 warnings |
| 文档 | release_notes + README + USER_GUIDE 同步更新 |

---

## 7. 工作量估算

| 阶段 | 工作量 | 前置条件 |
|------|--------|---------|
| Phase 1: 核心修复 + 基类改动 | 1-2 天 | 无 |
| Phase 2: GaussDB-M 测试 | 1-2 天 | GaussDB-M 实例可用 ✅ |
| Phase 3: GaussDB-A 测试 | 1-2 天 | GaussDB-A 实例可用 ✅ |
| Phase 4: 回归 + 文档 | 0.5 天 | Phase 2-3 完成 |
| **合计** | **3.5-6.5 天** | |
