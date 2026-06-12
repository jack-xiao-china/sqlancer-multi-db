# GaussDB-M EDC_DATA Seed File 二次审视分析

> 基于 GaussDB M-Compatibility 开发指南 (2026-04-02, 660页) vs 当前 v2.5.1 seed files
> 分析日期: 2026-06-11

---

## 0. 当前状态

| Seed File | 条目数 | 上次优化 |
|-----------|--------|---------|
| func.txt  | 215    | v2.5.1 (移除133项, 新增8项) |
| agg.txt   | 9      | v2.5.1 (移除10项) |
| type.txt  | 28     | v2.5.1 (移除13项, 新增6项) |
| pred.txt  | 15     | 未变更 |

---

## 1. Bug: func.txt 重复条目

| 函数 | 行号 | 说明 |
|------|------|------|
| CONV | L31, L212 | 上次新增时重复 |
| CRC32 | L36, L213 | 上次新增时重复 |

**影响**: 这两个函数被选中的概率是其他函数的 2x，导致测试分布不均。
**修复**: 删除 L212-213 的重复行。修复后 func.txt: 215 → 213。

---

## 2. func.txt: Seed File 中的函数有效性验证

### 2.1 B 类函数实测验证（MySQL 8.4.8 vs GaussDB-M）

通过 JDBC 逐一对比测试 14 个 PDF 未记录的函数：

| 函数 | MySQL 8.4.8 | GaussDB-M | 判定 |
|------|:-----------:|:---------:|------|
| **SOUNDEX** | ✓ `H400` | ✓ `H400` | ✅ **保留** — PDF 遗漏，实际支持 |
| **IS_IPV4_COMPAT** | ✓ `0` | ✓ `0` | ✅ **保留** — PDF 遗漏，实际支持 |
| **IS_IPV4_MAPPED** | ✓ `1` | ✓ `1` | ✅ **保留** — PDF 遗漏，实际支持 |
| **CHARSET** | ✓ `utf8mb4` | ✓ `utf8` | ✅ **保留** — PDF 遗漏，实际支持 |
| **SESSION_USER** | ✓ `tpcc@localhost` | ✓ `sqlbuilder1` | ✅ **保留** — PDF 遗漏，实际支持 |
| UUID_TO_BIN | ✓ | ✗ `function does not exist` | ❌ 移除 |
| IS_UUID | ✓ | ✗ `function does not exist` | ❌ 移除 |
| COERCIBILITY | ✓ | ✗ `function does not exist` | ❌ 移除 |
| WEIGHT_STRING | ✓ | ✗ `function does not exist` | ❌ 移除 |
| NAME_CONST | ✓ | ✗ `function does not exist` | ❌ 移除 |
| UpdateXML | ✓ | ✗ `function does not exist` | ❌ 移除 |
| ExtractValue | ✓ | ✗ `function does not exist` | ❌ 移除 |
| FORMAT_BYTES | ✓ | ✗ `function does not exist` | ❌ 移除 |
| FORMAT_PICO_TIME | ✓ | ✗ `function does not exist` | ❌ 移除 |

**结论**: GaussDB-M 的实际兼容性比 PDF 文档更强。5 个函数 PDF 未记录但实际可用，9 个函数确认不支持。

### 2.2 0-arg 函数（EDC_DATA 代码限制，非产品问题）

以下函数 GaussDB-M **完全支持**，但 EDC_DATA 框架无法正确调用：

| 函数 | 期望调用 | 当前生成 | 问题 |
|------|---------|---------|------|
| `PI` | `PI()` | `PI(c0)` | 0-arg 函数不接受参数 |
| `NOW` | `NOW()` | `NOW(c0)` | 同上 |
| `DATABASE` | `DATABASE()` | `DATABASE(c0)` | 同上 |
| `USER` | `USER()` | `USER(c0)` | 同上 |
| `UUID` | `UUID()` | `UUID(c0)` | 同上 |
| `UUID_SHORT` | `UUID_SHORT()` | `UUID_SHORT(c0)` | 同上 |
| `CONNECTION_ID` | `CONNECTION_ID()` | `CONNECTION_ID(c0)` | 同上 |
| `FOUND_ROWS` | `FOUND_ROWS()` | `FOUND_ROWS(c0)` | 同上 |
| `VERSION` | `VERSION()` | `VERSION(c0)` | 同上 |
| `LOCALTIME` | `LOCALTIME` | `LOCALTIME(c0)` | 同上 |
| `LOCALTIMESTAMP` | `LOCALTIMESTAMP` | `LOCALTIMESTAMP(c0)` | 同上 |
| `SCHEMA` | `SCHEMA()` | `SCHEMA(c0)` | 同上 |
| `SYSTEM_USER` | `SYSTEM_USER()` | `SYSTEM_USER(c0)` | 同上 |
| `UTC_DATE` | `UTC_DATE()` | `UTC_DATE(c0)` | 同上 |

**根因**: `generateTestScenario()` 中 `testColumn = getInteger(1, maxCol+1)` 最小为 1，代码总是传入至少 1 列。
**影响**: 这 14 个函数完全浪费 — 每次被随机选中都会报错并被 `validateTestExpression()` 跳过。
**修复**: 需要修改 `EDCDataOracleBase.java`，对 0-arg 函数特殊处理（见第 7 节方案）。

### 2.3 汇总：建议移除

| 类别 | 函数 | 数量 |
|------|------|------|
| 实测确认不支持 | UUID_TO_BIN, IS_UUID, COERCIBILITY, WEIGHT_STRING, NAME_CONST, UpdateXML, ExtractValue, FORMAT_BYTES, FORMAT_PICO_TIME | 9 |
| 0-arg 代码限制 | PI, NOW, DATABASE, USER, UUID, UUID_SHORT, CONNECTION_ID, FOUND_ROWS, VERSION, LOCALTIME, LOCALTIMESTAMP, SCHEMA, SYSTEM_USER, UTC_DATE | 14 |
| **合计** | | **23** |

修复后 func.txt: 213 (去重后) - 23 = **190 项**

---

## 3. func.txt: PDF 中文档但 Seed File 缺失的函数

### 3.1 可直接添加（兼容当前代码）

| 函数 | PDF 章节 | 签名 | 能否用 EDC_DATA 测试 |
|------|---------|------|---------------------|
| `RANDOM_BYTES` | 4.5.7 | `RANDOM_BYTES(len)` | ✓ 单参数，可正常生成 |
| `SHA` | 4.5.7 | `SHA(str)` | ✓ SHA1 的别名 |
| `LAST_INSERT_ID` | 4.5.17 | `LAST_INSERT_ID()` / `LAST_INSERT_ID(expr)` | ✓ 有参数形式 |
| `ROW_COUNT` | 4.5.17 | `ROW_COUNT()` | ⚠ 0-arg |
| `SLEEP` | 4.5.17 | `SLEEP(duration)` | ⚠ 会拖慢测试 |
| `BENCHMARK` | 4.5.17 | `BENCHMARK(count, expr)` | ⚠ 资源密集 |
| `PASSWORD` | 4.5.14 | `PASSWORD(str)` | ✓ 但已废弃 |
| `INTERVAL` | 4.5.5 | `INTERVAL(N, N1, N2, ...)` | ✓ 多参数比较函数 |
| `REGEXP` | 4.5.4 | `str REGEXP pattern` | ✗ 模式匹配操作符，不在 func 范畴 |

**建议新增**: `RANDOM_BYTES`, `SHA`, `INTERVAL` (3 项)
**不新增**: `SLEEP` (慢), `BENCHMARK` (资源密集), `PASSWORD` (废弃), `LAST_INSERT_ID`/`ROW_COUNT` (上下文依赖)

### 3.2 需代码改造才能添加

| 函数 | PDF 章节 | 原因 |
|------|---------|------|
| `CURDATE` | 4.5.8 | 0-arg 函数 |
| `CURRENT_DATE` | 4.5.8 | 0-arg 函数 |
| `CURRENT_TIME` | 4.5.8 | 0-arg 函数（可选 scale 参数） |
| `CURRENT_TIMESTAMP` | 4.5.8 | 0-arg 函数（可选 scale 参数） |
| `CURTIME` | 4.5.8 | 0-arg 函数 |
| `LAST_DAY` | 4.5.8 | 单参数，但 EDC_DATA 会传多列 |

---

## 4. agg.txt 缺漏分析

### PDF 4.5.11 记录的聚合函数

| 函数 | 在 agg.txt | 说明 |
|------|-----------|------|
| AVG | ✓ | |
| BIT_AND | ✓ | |
| BIT_OR | ✓ | |
| BIT_XOR | ✓ | |
| COUNT | ✓ | |
| GROUP_CONCAT | ✗ | 需要特殊语法: `GROUP_CONCAT(DISTINCT expr ORDER BY ... SEPARATOR str)` |
| MAX | ✓ | |
| MIN | ✓ | |
| STD | ✓ | |
| SUM | ✓ | |

**缺失**: `GROUP_CONCAT`

**分析**: EDC_DATA 生成 `AGG(c0,c1,...)` — 对于 `GROUP_CONCAT(c0,c1)` 退化为 `CONCAT(c0,c1)`，丢失 DISTINCT/ORDER BY/SEPARATOR 语义。测试价值有限。

**建议**: 不新增 GROUP_CONCAT。当前 9 个聚合函数已覆盖 PDF 文档的全部标准聚合（除 GROUP_CONCAT 外）。

### 上轮移除的聚合函数再审视

| 函数 | v2.5.1 移除原因 | 再审视结论 |
|------|---------------|-----------|
| STDDEV | STD 的别名 | PDF 4.5.11 仅列 STD，移除正确 |
| STDDEV_POP, STDDEV_SAMP | GaussDB-M 不支持 | PDF 未记录，移除正确 |
| VAR_POP, VAR_SAMP, VARIANCE | GaussDB-M 不支持 | PDF 未记录，移除正确 |
| JSON_ARRAYAGG, JSON_OBJECTAGG | GaussDB-M 不支持 | PDF 未记录，移除正确 |
| MEDIAN | GaussDB-M 不支持 | PDF 未记录，移除正确 |

**结论**: agg.txt 当前状态良好，无需变更。

---

## 5. type.txt 缺漏分析

### PDF 4.6 完整类型 vs 当前 type.txt

| 类型分类 | PDF 记录 | type.txt 状态 | 说明 |
|---------|---------|--------------|------|
| 布尔 | BOOLEAN, BOOL | ✓ BOOLEAN | |
| 字符 | CHAR, VARCHAR, TEXT, TINYTEXT, MEDIUMTEXT, LONGTEXT | ✓ 全部 | |
| 日期 | DATE, TIME, DATETIME, TIMESTAMP, YEAR | ✓ 全部 | |
| 位串 | BIT | ✓ | |
| 有符号整数 | TINYINT, SMALLINT, MEDIUMINT, INT, BIGINT | ✓ 全部 | |
| 无符号整数 | TINYINT~BIGINT UNSIGNED | ✓ 全部 (v2.5.1 新增) | |
| 定点数 | NUMERIC, DECIMAL, DEC, FIXED | ✓ DECIMAL | DECIMAL→NUMERIC 内部映射 |
| 浮点数 | FLOAT, FLOAT4, FLOAT8, DOUBLE, REAL | ✓ FLOAT, DOUBLE | |
| JSON | JSON | ✓ | |
| 枚举 | ENUM | ✓ | |
| 二进制 | BINARY, VARBINARY, BLOB, TINYBLOB, MEDIUMBLOB, LONGBLOB | ✗ 全部移除 | JDBC 驱动 Bug |
| 集合 | SET | ✗ 缺失 | |

### 5.1 可新增的类型

| 类型 | PDF 引用 | 新增价值 | 代码改动 |
|------|---------|---------|---------|
| `NUMERIC` | 4.6.7.3 | 测试 DECIMAL 别名路径 | 需 mapType() 添加映射 |
| `FLOAT4` | 4.6.7.4 | 测试单精度浮点 | 需 mapType() + generateRandomValue() |
| `FLOAT8` | 4.6.7.4 | 测试双精度浮点 | 需 mapType() + generateRandomValue() |
| `REAL` | 4.6.7.4 | 测试 REAL 类型 | 需 mapType() + generateRandomValue() |
| `SET` | 4.6.10 | 测试集合类型 | 需 ENUM 类似的值列表生成逻辑 |

### 5.2 不可新增的类型

| 类型 | 原因 |
|------|------|
| BINARY, VARBINARY | GaussDB JDBC 驱动 `PgResultSet.getString()` 抛 NumberFormatException |
| BLOB, TINYBLOB, MEDIUMBLOB, LONGBLOB | 同上 |
| DOUBLE PRECISION | 过长，且 DOUBLE 已覆盖 |
| DEC, FIXED | NUMERIC 的别名，添加冗余 |
| BOOL | BOOLEAN 的别名，添加冗余 |

### 5.3 建议

**新增 4 项**: `NUMERIC`, `FLOAT4`, `FLOAT8`, `REAL`
**不新增**: `SET`（需要类似 ENUM 的值列表后处理，代码改动较大，留 P2）

---

## 6. pred.txt 缺漏分析

### PDF 4.5.1-4.5.5 记录的操作符 vs 当前 pred.txt

| 操作符 | PDF | pred.txt | 说明 |
|--------|-----|----------|------|
| `>`, `<`, `>=`, `<=`, `=`, `<>` | ✓ | ✓ | |
| `AND`, `OR`, `XOR` | ✓ | ✓ | |
| `LIKE`, `NOT LIKE` | ✓ | ✓ | |
| `IN`, `NOT IN` | ✓ | ✓ | |
| `IS`, `IS NULL`, `IS NOT NULL` | ✓ | ✓ | |
| `BETWEEN` | ✓ | ✓ | |
| `<=>` (NULL安全等于) | ✓ 4.5.5 | ✗ | 需要代码改动 |
| `REGEXP` | ✓ 4.5.4 | ✗ | 需要代码改动 |
| `NOT REGEXP` | ✓ 4.5.4 | ✗ | 需要代码改动 |
| `SOUNDS LIKE` | — | ✗ | PDF 未记录 |
| `RLIKE` | REGEXP 别名 | ✗ | 需要代码改动 |

### 6.1 `<=>` (NULL-safe equality)

```sql
-- 语义: NULL <=> NULL 返回 TRUE (不同于 NULL = NULL 返回 NULL)
SELECT c0 <=> c1 FROM t;
```

**代码改动需求**:
1. `generateTestScenario()`: 添加 `else if (op.equals("<=>")) testColumn = Math.max(2, testColumn);`
2. `generateTestExpression()`: 在二元操作符列表中添加 `"<=>"`
3. `ExpectedErrors`: 添加可能的错误模式

### 6.2 `REGEXP` / `NOT REGEXP`

```sql
SELECT c0 REGEXP c1 FROM t;  -- c1 的值作为正则模式
```

**代码改动需求**:
1. 同 `<=>` 的列数约束
2. 表达式生成中添加 REGEXP 为二元操作符
3. `ExpectedErrors`: 添加 "Invalid regular expression" 等模式

### 6.3 建议

**P1 新增**: `<=>` 和 `REGEXP`（需要修改 `EDCDataOracleBase.java`，约 +6 行代码）
**不新增**: `NOT REGEXP`, `RLIKE`（与 REGEXP 冗余，且 pred.txt 已有 NOT LIKE 覆盖否定模式）

---

## 7. 代码架构级问题

### 7.1 函数 arity 不感知（最高影响）

**现状**: `generateTestExpression()` 对所有函数统一传入全部 test columns。

**影响量化**（假设 testColumn 均匀分布 [1,3]）:

| 函数类型 | testColumn=1 | testColumn=2 | testColumn=3 | 有效概率 |
|---------|-------------|-------------|-------------|---------|
| 0-arg (PI, NOW...) | ❌ PI(c0) | ❌ PI(c0,c1) | ❌ PI(c0,c1,c2) | **0%** |
| 1-arg (ABS, CEIL...) | ✓ ABS(c0) | ❌ ABS(c0,c1) | ❌ ABS(c0,c1,c2) | **33%** |
| 2-arg (POW, ATAN2...) | ❌ POW(c0) | ✓ POW(c0,c1) | ❌ POW(c0,c1,c2) | **33%** |
| 3-arg (IF, REPLACE...) | ❌ IF(c0) | ❌ IF(c0,c1) | ✓ IF(c0,c1,c2) | **33%** |
| N-arg (CONCAT, COALESCE...) | ✓ | ✓ | ✓ | **100%** |

当前 func.txt 约 190 个有效函数中:
- ~14 个 0-arg 函数: 完全浪费
- ~50 个 1-arg 函数: 67% 场景浪费
- ~20 个 2-arg 函数: 67% 场景浪费
- ~10 个 3-arg 函数: 67% 场景浪费
- ~90 个 N-arg 函数: 全部有效

**整体有效场景比例**: 约 **65%**（依赖 `validateTestExpression()` 兜底跳过非法调用）

### 7.2 改造方案（P2，非本次范围）

**方案 A: Seed 文件格式扩展**
```
# func.txt 扩展格式
ABS:1
CONCAT:2+
IF:3
PI:0
NOW:0,1     # 接受 0 或 1 个参数
```

**方案 B: 代码内置 arity 映射**
```java
// EDCDataConfig 中维护函数 arity 表
Map<String, int[]> funcArity = Map.of(
    "ABS", new int[]{1, 1},
    "CONCAT", new int[]{1, 10},
    "PI", new int[]{0, 0},
    "NOW", new int[]{0, 1}
);
```

**方案 C: 验证重试**
```java
// 如果 validate 失败，减少 testColumn 重试
for (int cols = testColumn; cols >= 1; cols--) {
    expr = generateWithColumns(cols);
    if (validate(expr)) break;
}
```

**推荐**: 方案 A（最灵活），但需要改 EDCDataConfig 的解析逻辑。

---

## 8. WHERE 条件运算符硬编码

`EDCDataExpressionBuilder.generateExprOnColumn()` 中 WHERE 条件只用 6 种运算符:
`=`, `!=`, `>`, `<=`, `AND`, `OR`

**缺失的有用运算符**:
- `LIKE` — 可测试字符串模式匹配下的查询优化
- `BETWEEN` — 可测试范围查询
- `IS NULL` / `IS NOT NULL` — 可测试 NULL 处理

**影响**: 低 — WHERE 条件的目的是过滤数据而非测试运算符，6 种足够。
**建议**: P3，不紧急。

---

## 9. 综合优化建议

### P0 — 立即修复（无代码改动）

| # | 操作 | 影响 |
|---|------|------|
| 1 | 删除 func.txt 中 CONV/CRC32 重复行 (L212, L213) | -2 行 |
| 2 | 删除实测确认不支持的 9 个函数 (UUID_TO_BIN, IS_UUID, COERCIBILITY, WEIGHT_STRING, NAME_CONST, UpdateXML, ExtractValue, FORMAT_BYTES, FORMAT_PICO_TIME) | -9 行 |
| 3 | 删除 14 个 0-arg 无效函数 (PI, NOW, DATABASE, USER, UUID, UUID_SHORT, CONNECTION_ID, FOUND_ROWS, VERSION, LOCALTIME, LOCALTIMESTAMP, SCHEMA, SYSTEM_USER, UTC_DATE) | -14 行 |
| 4 | 新增 `RANDOM_BYTES`, `SHA`, `INTERVAL` | +3 行 |

预期: func.txt 213 → **190 项** (全部有效)
保留的 5 个 PDF 未记录但实测可用的函数: SOUNDEX, IS_IPV4_COMPAT, IS_IPV4_MAPPED, CHARSET, SESSION_USER

### P1 — 短期（少量代码改动）

| # | 操作 | 代码改动 |
|---|------|---------|
| 5 | pred.txt 新增 `<=>`, `REGEXP` | EDCDataOracleBase.java +6 行 |
| 6 | type.txt 新增 `NUMERIC`, `FLOAT4`, `FLOAT8`, `REAL` | GaussDBMEDCDataTableBuilder mapType() +8 行, EDCDataExpressionBuilder +4 分支 |

### P2 — 中期（架构改进）

| # | 操作 | 说明 |
|---|------|------|
| 7 | 函数 arity 感知 | 改造 seed file 格式 + EDCDataConfig 解析逻辑 |
| 8 | 恢复 0-arg 函数 | 依赖 #7 |
| 9 | SET 类型支持 | 需类似 ENUM 的值列表后处理 |
| 10 | WHERE 条件运算符扩展 | EDCDataExpressionBuilder 改造 |

### P3 — 不修复

| 项目 | 原因 |
|------|------|
| 二进制类型 (BINARY, BLOB...) | GaussDB JDBC 驱动 Bug，等待驱动修复 |
| GROUP_CONCAT | 语法特殊，测试价值有限 |
| 窗口函数 | 需 OVER 子句，EDC_DATA 架构不支持 |
| 0-arg 日期函数 (CURDATE等) | GaussDB-M 不支持，PDF 未记录 |

---

## 10. 实际结果

### v2.5.2 (Seed File 优化 + 代码适配)

| 指标 | 优化前 (v2.5.1) | 优化后 (v2.5.2) |
|------|---------------|---------------|
| func.txt 有效函数 | ~191 (含23个无效) | **191 (全部有效)** |
| type.txt 有效类型 | 28 | **32** |
| pred.txt 有效谓词 | 15 | **17** |
| 语句成功率 | 82% | **83%** |

### v2.5.3 (0-arg 函数支持)

| 指标 | v2.5.2 | v2.5.3 |
|------|--------|--------|
| func.txt 有效函数 | 191 | **205** (+14 个 0-arg) |
| 语句成功率 | 83% | **86%** |
| 0-arg 函数生成 | ❌ 全部报错 | ✅ `NOW()` / `PI()` 正确 |
| function does not exist | 0 | **0** |
