# GaussDB-M EDC_DATA Seed File 第三轮审视

> 基于 v2.5.3 当前状态 vs GaussDB M-Compatibility 开发指南 (2026-04-02)
> 分析日期: 2026-06-12

---

## 0. 当前状态 (v2.5.3)

| Seed File | 条目数 | 最近优化 |
|-----------|--------|---------|
| func.txt  | 205    | v2.5.3 (+14 个 0-arg) |
| agg.txt   | 9      | v2.5.1 |
| type.txt  | 32     | v2.5.2 (+4 浮点别名) |
| pred.txt  | 17     | v2.5.2 (+<=>, REGEXP) |

---

## 1. func.txt 缺漏分析

### 1.1 实测确认：4 个函数 0% 有效率（应移除）

通过 JDBC 测试 EDC_DATA 实际生成的语法：

| 函数 | EDC_DATA 生成 (2列) | EDC_DATA 生成 (1列) | 有效率 | 原因 |
|------|--------------------|--------------------|--------|------|
| **CAST** | `CAST(c0,c1)` ❌ syntax error | `CAST(c0)` ❌ syntax error | **0%** | 需 `CAST(x AS type)` 特殊语法 |
| **EXTRACT** | `EXTRACT(c0,c1)` ❌ syntax error | `EXTRACT(c0)` ❌ syntax error | **0%** | 需 `EXTRACT(unit FROM x)` 特殊语法 |
| **IN** | `IN(c0,c1)` ❌ syntax error | `IN(c0)` ❌ syntax error | **0%** | 是谓词非函数，pred.txt 已有正确实现 |
| **NOT IN** | `NOT IN(c0,c1)` ❌ syntax error | `NOT IN(c0)` ❌ syntax error | **0%** | 同上 |

**影响**: 4 个函数每次被随机选中都必然失败，浪费约 2% 的测试迭代（4/205）。
**修复**: 从 func.txt 移除。

### 1.2 func.txt 与 pred.txt 交叉重复

| 条目 | func.txt | pred.txt | func.txt 效果 | pred.txt 效果 |
|------|----------|----------|--------------|--------------|
| IN | ✓ (L64) | ✓ (L12) | `IN(c0,c1)` ❌ | `c0 IN (c1,c2)` ✅ |
| NOT IN | ✓ (L121) | ✓ (L14) | `NOT IN(c0,c1)` ❌ | `c0 NOT IN (c1,c2)` ✅ |

IN/NOT IN 是谓词，不是函数。放在 func.txt 中导致 EDC_DATA 生成非法语法。
**修复**: 从 func.txt 移除 IN 和 NOT IN（已在 1.1 中包含）。

### 1.3 实测确认：3 个函数 33% 有效率（保留）

| 函数 | EDC_DATA 生成 (2列) | EDC_DATA 生成 (1列) | 有效率 |
|------|--------------------|--------------------|--------|
| DEFAULT | `DEFAULT(c0,c1)` ❌ | `DEFAULT(c0)` ✅ null | 33% |
| CHARSET | `CHARSET(c0,c1)` ❌ | `CHARSET(c0)` ✅ binary | 33% |
| COLLATION | `COLLATION(c0,c1)` ❌ | `COLLATION(c0)` ✅ binary | 33% |

**判定**: 保留。虽然 2/3 场景浪费，但 1/3 有效且可测试特定功能。

### 1.4 PDF 记录但 seed file 缺失的函数（实测可用）

| 函数 | PDF 章节 | GaussDB-M 实测 | 可加入 | 条件 |
|------|---------|:--------------:|--------|------|
| CURDATE | 4.5.8 | ✅ `2026-06-12` | ✅ | 加入 ZERO_ARG_FUNCTIONS |
| CURRENT_DATE | 4.5.8 | ✅ `2026-06-12` | ✅ | 加入 ZERO_ARG_FUNCTIONS |
| CURRENT_TIME | 4.5.8 | ✅ `14:08:46` | ✅ | 加入 ZERO_ARG_FUNCTIONS |
| CURRENT_TIMESTAMP | 4.5.8 | ✅ `2026-06-12 14:08:46` | ✅ | 加入 ZERO_ARG_FUNCTIONS |
| CURTIME | 4.5.8 | ✅ `14:08:46` | ✅ | 加入 ZERO_ARG_FUNCTIONS |
| LAST_DAY | 4.5.8 | ✅ `2026-06-30` | ✅ | 单参数函数，正常生成 |
| ANY_VALUE | 4.5.17 | ✅ `42` | ⚠ | 语义为聚合，放 agg.txt 更合适 |
| PASSWORD | 4.5.14 | — | ❌ | MySQL 已废弃，不建议添加 |

**建议新增 6 个到 func.txt**: CURDATE, CURRENT_DATE, CURRENT_TIME, CURRENT_TIMESTAMP, CURTIME, LAST_DAY
**建议新增 1 个到 agg.txt**: ANY_VALUE

### 1.5 Seed file 中有但 PDF 未记录的函数（实测全部可用）

以下函数在 GaussDB-M 实测中全部正常工作，保留：

OCT, EXPORT_SET, MAKE_SET, GET_FORMAT, COMPRESS, UNCOMPRESS,
UNCOMPRESSED_LENGTH, TO_BASE64, FROM_BASE64, SOUNDEX, CHARSET,
SESSION_USER, IS_IPV4_COMPAT, IS_IPV4_MAPPED, OCT, INTERVAL

**结论**: 保留。GaussDB-M 实际兼容性比 PDF 文档更强。

### 1.6 汇总

| 操作 | 函数 | 数量 |
|------|------|------|
| **移除** | CAST, EXTRACT, IN, NOT IN | -4 |
| **新增** | CURDATE, CURRENT_DATE, CURRENT_TIME, CURRENT_TIMESTAMP, CURTIME, LAST_DAY | +6 |
| **净变化** | | **+2** |

修复后 func.txt: 205 - 4 + 6 = **207 项**

---

## 2. agg.txt 缺漏分析

### PDF 4.5.11 聚合函数 vs 当前 agg.txt

| 函数 | PDF | agg.txt | 说明 |
|------|-----|---------|------|
| AVG | ✓ | ✓ | |
| BIT_AND | ✓ | ✓ | |
| BIT_OR | ✓ | ✓ | |
| BIT_XOR | ✓ | ✓ | |
| COUNT | ✓ | ✓ | |
| GROUP_CONCAT | ✓ | ✗ | 需特殊语法，不适合 EDC_DATA |
| MAX | ✓ | ✓ | |
| MIN | ✓ | ✓ | |
| STD | ✓ | ✓ | |
| SUM | ✓ | ✓ | |

**缺失**:
- `GROUP_CONCAT`: 需 `DISTINCT`/`ORDER BY`/`SEPARATOR` 特殊语法，EDC_DATA 无法表达。不添加。
- `ANY_VALUE`: 实测可用。EDC_DATA 生成 `ANY_VALUE(c0,c1)` → 仅 c0 有效（33%），可添加到 agg.txt。

**建议**: 新增 `ANY_VALUE`，agg.txt 9 → **10 项**。

---

## 3. type.txt 缺漏分析

### PDF 4.6 数据类型 vs 当前 type.txt

| 分类 | PDF 记录 | type.txt | 说明 |
|------|---------|----------|------|
| 布尔 | BOOLEAN, BOOL | ✓ BOOLEAN | |
| 字符 | CHAR, VARCHAR, TEXT, TINYTEXT, MEDIUMTEXT, LONGTEXT | ✓ 全部 | |
| 日期 | DATE, TIME, DATETIME, TIMESTAMP, YEAR | ✓ 全部 | |
| 位串 | BIT | ✓ | |
| 有符号整数 | TINYINT, SMALLINT, MEDIUMINT, INT, BIGINT | ✓ 全部 | |
| 无符号整数 | TINYINT~BIGINT UNSIGNED | ✓ 全部 | |
| 定点数 | NUMERIC, DECIMAL, DEC, FIXED | ✓ NUMERIC, DECIMAL | |
| 浮点数 | FLOAT, FLOAT4, FLOAT8, DOUBLE, REAL | ✓ 全部 | |
| JSON | JSON | ✓ | |
| 枚举 | ENUM | ✓ | |
| 二进制 | BINARY, VARBINARY, BLOB, TINYBLOB, MEDIUMBLOB, LONGBLOB | ✗ | JDBC 驱动 Bug |
| 集合 | SET | ✗ | 需类似 ENUM 的值列表后处理 |

**缺失**:
- `SET('val1','val2',...)`: GaussDB-M 支持，但需类似 ENUM 的值列表后处理（代码改动），留 P2。
- 二进制类型: GaussDB JDBC 驱动 `PgResultSet.getString()` 对二进制数据抛 NumberFormatException，等待驱动修复。

**结论**: type.txt 当前 32 项已覆盖 GaussDB-M 所有可用类型（除 JDBC 限制和 SET 类型），**无需变更**。

---

## 4. pred.txt 缺漏分析

### PDF 4.5.1-4.5.5 操作符 vs 当前 pred.txt

| 操作符 | PDF | pred.txt | 说明 |
|--------|-----|----------|------|
| >, <, >=, <=, =, <> | ✓ | ✓ | |
| <=> (NULL安全等于) | ✓ | ✓ | v2.5.2 新增 |
| AND, OR, XOR | ✓ | ✓ | |
| LIKE, NOT LIKE | ✓ | ✓ | |
| REGEXP | ✓ | ✓ | v2.5.2 新增 |
| NOT REGEXP | ✓ | ✗ | REGEXP 的否定形式 |
| IN, NOT IN | ✓ | ✓ | |
| IS | ✓ | ✓ | |
| IS NULL, IS NOT NULL | ✓ | ✓ | |
| BETWEEN | ✓ | ✓ | |

**缺失**:
- `NOT REGEXP`: `c0 NOT REGEXP c1` — 与 REGEXP 互补，可添加。需 EDCDataOracleBase 代码适配（+2 行）。

**建议**: 新增 `NOT REGEXP`，pred.txt 17 → **18 项**。

---

## 5. 代码架构级问题（同前轮分析，未变）

### 5.1 函数 arity 不感知

EDC_DATA 对所有函数统一传入全部 test columns，导致：
- 1-arg 函数 (ABS, CEIL...)：67% 场景浪费
- 2-arg 函数 (POW, ATAN2...)：67% 场景浪费
- 3-arg 函数 (IF, REPLACE...)：67% 场景浪费
- N-arg 函数 (CONCAT, COALESCE...)：100% 有效

**当前整体有效场景比例**: 约 **80%**（v2.5.3 集成测试 86% 成功率）

### 5.2 WHERE 条件运算符硬编码

`EDCDataExpressionBuilder.generateExprOnColumn()` WHERE 条件仅用 6 种运算符：`=`, `!=`, `>`, `<=`, `AND`, `OR`。
缺失 LIKE、BETWEEN、IS NULL 等，但 WHERE 条件以过滤为目的，6 种足够。

---

## 6. 综合优化建议

### P0 — 立即修复（无代码改动）

| # | 操作 | 影响 |
|---|------|------|
| 1 | func.txt 移除 CAST, EXTRACT, IN, NOT IN | -4 行（0% 有效函数） |
| 2 | func.txt 新增 CURDATE, CURRENT_DATE, CURRENT_TIME, CURRENT_TIMESTAMP, CURTIME, LAST_DAY | +6 行 |
| 3 | agg.txt 新增 ANY_VALUE | +1 行 |

预期: func.txt 205 → **207**, agg.txt 9 → **10**

### P1 — 短期（少量代码改动）

| # | 操作 | 代码改动 |
|---|------|---------|
| 4 | ZERO_ARG_FUNCTIONS 新增 CURDATE, CURRENT_DATE, CURRENT_TIME, CURRENT_TIMESTAMP, CURTIME | +5 行常量 |
| 5 | pred.txt 新增 NOT REGEXP | EDCDataOracleBase.java +2 行 |

### P2 — 中期（架构改进）

| # | 操作 | 说明 |
|---|------|------|
| 6 | SET 类型支持 | 需类似 ENUM 的值列表后处理 |
| 7 | 函数 arity 感知 | 改造 seed file 格式 + EDCDataConfig 解析 |

### P3 — 不修复

| 项目 | 原因 |
|------|------|
| CAST / EXTRACT 特殊语法 | 需重构 generateTestExpression，改动大收益小 |
| 二进制类型 | GaussDB JDBC 驱动 Bug |
| GROUP_CONCAT | 语法特殊 |
| 窗口函数 | 需 OVER 子句 |

---

## 7. 预期效果

| 指标 | 当前 (v2.5.3) | P0+P1 后 |
|------|-------------|----------|
| func.txt 有效函数 | 205 (含4个0%有效) | **207 (全部有效)** |
| agg.txt 有效聚合 | 9 | **10** |
| pred.txt 有效谓词 | 17 | **18** |
| type.txt 有效类型 | 32 | **32** |
| 0% 有效函数数 | 4 (CAST/EXTRACT/IN/NOT IN) | **0** |
| 语句成功率 | 86% | **~88%** |
