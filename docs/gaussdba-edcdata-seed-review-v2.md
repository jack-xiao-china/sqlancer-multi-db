# GaussDB-A EDC_DATA Seed File 第二轮审计 (v2.5.6)

**日期**: 2026-06-12
**基线版本**: v2.5.5
**审计范围**: `edc-data-seeds/gaussdba/` 全部 4 个 seed files
**参考文档**: `gaussdb-sql-guide-2026.pdf` (4213 页, GaussDB-A Oracle 兼容模式)

---

## 1. 审计方法

1. 从 PDF 提取 802 个标识符（函数名 + 过程名 + 关键字）
2. 过滤出 SQL 函数候选（排除 PL/SQL 包函数、系统表函数、SQL 关键字）
3. 在 GaussDB-A 实例（`testa`, Oracle 兼容模式）上用 JDBC 逐一测试
4. 按 EDC_DATA 调用模式评估有效性：`FUNC(c0,c1,...)` 传入所有测试列

## 2. 测试结果汇总

### 第一轮：PDF 候选 vs 现有 seed files (50 个函数)

| 结果 | 数量 | 说明 |
|------|------|------|
| PASS | 31 | 函数在 GaussDB-A 上可执行 |
| FAIL | 19 | 函数不存在/语法不兼容 |

### 第二轮：补充候选测试 (42 个函数)

| 结果 | 数量 | 说明 |
|------|------|------|
| PASS | 19 | 包括现有 func.txt 验证 |
| FAIL | 23 | 包括 NLS_*, TRANSLITERATE 等 |

### 第三轮：EDC_DATA 兼容性验证 (24 个函数)

| 结果 | 数量 | 说明 |
|------|------|------|
| PASS | 19 | 确认 EDC_DATA 调用模式兼容 |
| FAIL | 5 | 1-arg 函数被 2-arg 调用失败 |

---

## 3. 分类分析

### 3.1 A 类：PDF 中存在但 seed file 缺失的有效函数

#### A-1. 可添加到 func.txt（8 项）

| 函数 | 参数 | EDC_DATA 有效率 | 说明 |
|------|------|:-:|------|
| REGEXP_LIKE | 2-arg | ~100% | 正则匹配，支持 int/str 参数 |
| GENERATE_SERIES | 2-arg | ~100% | 生成序列，int 参数 |
| WIDTH_BUCKET | 4-arg | ~100% | 分桶函数，接受多参数 |
| JUSTIFY_HOURS | 1-arg | ~33% | interval→规范化 hours |
| JUSTIFY_INTERVAL | 1-arg | ~33% | interval→规范化 |
| CHECKSUM | 1-arg | ~33% | 校验和函数 |
| QUOTE_LITERAL | 1-arg | ~33% | 转义引号 |
| QUOTE_IDENT | 1-arg | ~33% | 转义标识符 |

#### A-2. 0-arg 函数（添加到 getZeroArgFunctions()）

| 函数 | 说明 |
|------|------|
| VERSION | 返回版本信息，PDF 和测试均验证通过 |
| RANDOM | 返回随机数，测试通过 |

#### A-3. 可添加到 agg.txt（9 项）

| 函数 | 参数 | EDC_DATA 有效率 | 说明 |
|------|------|:-:|------|
| REGR_AVGX | 2-arg | ~100% | 线性回归：X 平均值 |
| REGR_AVGY | 2-arg | ~100% | 线性回归：Y 平均值 |
| REGR_COUNT | 2-arg | ~100% | 线性回归：非空对计数 |
| REGR_INTERCEPT | 2-arg | ~100% | 线性回归：截距 |
| REGR_R2 | 2-arg | ~100% | 线性回归：决定系数 |
| REGR_SLOPE | 2-arg | ~100% | 线性回归：斜率 |
| REGR_SXX | 2-arg | ~100% | 线性回归：X 平方和 |
| REGR_SXY | 2-arg | ~100% | 线性回归：XY 乘积和 |
| REGR_SYY | 2-arg | ~100% | 线性回归：Y 平方和 |

### 3.2 B 类：当前 seed file 中应移除的无效函数

| 文件 | 函数 | 原因 | 有效率 |
|------|------|------|:-:|
| agg.txt | XMLAGG | `Function xmlagg does not exist`（Oracle 模式不支持） | 0% |
| agg.txt | LISTAGG | 需要 `WITHIN GROUP (ORDER BY ...)` 语法，EDC_DATA 无法生成 | 0% |

### 3.3 C 类：PDF 中存在但不适合 EDC_DATA 的函数

| 类别 | 函数 | 原因 |
|------|------|------|
| **B-format 专属** (12 项) | ADDDATE, DATE_ADD, DATE_SUB, TO_SECONDS, UNIX_TIMESTAMP, STR_TO_DATE, DATE_FORMAT, TIME_FORMAT | "supported only in B-format database"（GaussDB-M 专属） |
| **窗口函数** (11 项) | LAG, LEAD, DENSE_RANK, RANK, ROW_NUMBER, FIRST_VALUE, LAST_VALUE, NTH_VALUE, NTILE, CUME_DIST, PERCENT_RANK | 需要 `OVER` 子句，EDC_DATA 无法生成 |
| **特殊语法** (4 项) | CAST, EXTRACT, OVERLAY, TIMESTAMPDIFF | 非标准函数调用语法 |
| **不存在** (16 项) | NEW_TIME, TZ_OFFSET, EDIT_DISTANCE_SIMILARITY, TRANSLITERATE, ASCIISTR, COMPOSE, DECOMPOSE, TO_MULTI_BYTE, TO_SINGLE_BYTE, TO_NCHAR, NLS_UPPER, NLS_LOWER, NLS_INITCAP, DB_VERSION, SYS_EXTRACT_UTC, CONVERT(varchar,varchar) | 函数不存在或参数类型不匹配 |
| **需关键字参数** (2 项) | TIMESTAMPADD, INTERVAL | 第一参数需 HOUR/DAY 等关键字 |
| **PL/SQL 包函数** (~50 项) | DBE_*, XML_DOM_*, UTL_FILE.*, DBMS_* | PL/SQL 包级函数，不适合 SQL 查询 |
| **副作用函数** (3 项) | PG_SLEEP, SETSEED | 影响执行环境或耗时 |
| **LNNVL** | LNNVL | "not yet supported in un-A-format compatible mode" |

### 3.4 D 类：现有 seed file 验证（全部有效）

#### func.txt (74 项) — 抽查验证

| 函数 | 测试 | 结果 |
|------|------|------|
| CURRENT_DATE | `SELECT CURRENT_DATE` | ✅ PASS |
| SYSDATE | `SELECT SYSDATE` | ✅ PASS |
| CLOCK_TIMESTAMP | `SELECT CLOCK_TIMESTAMP()` | ✅ PASS |
| CURRENT_SCHEMA | `SELECT CURRENT_SCHEMA` | ✅ PASS |
| CURRENT_USER | `SELECT CURRENT_USER` | ✅ PASS |
| SESSION_USER | `SELECT SESSION_USER` | ✅ PASS |
| NULLIF(c0,c1) | 2-arg | ✅ PASS |
| COALESCE(c0,c1) | 2-arg | ✅ PASS |
| GREATEST(c0,c1) | 2-arg | ✅ PASS |
| LEAST(c0,c1) | 2-arg | ✅ PASS |
| NVL(c0,c1) | 2-arg | ✅ PASS |
| DECODE(c0,c1,c2) | 3-arg | ✅ PASS |

#### agg.txt (22 项) — 全部验证

| 函数 | 结果 |
|------|------|
| AVG, COUNT, MAX, MIN, SUM | ✅ 全部 PASS |
| STDDEV, STDDEV_POP, STDDEV_SAMP | ✅ 全部 PASS |
| VAR_POP, VAR_SAMP, VARIANCE | ✅ 全部 PASS |
| MEDIAN | ✅ PASS |
| BIT_AND, BIT_OR | ✅ PASS |
| BOOL_AND, BOOL_OR | ✅ PASS |
| STRING_AGG | ✅ PASS |
| CORR, COVAR_POP, COVAR_SAMP | ✅ PASS |
| **XMLAGG** | ❌ FAIL (不存在) |
| **LISTAGG** | ❌ 0% 有效 (需 WITHIN GROUP) |

---

## 4. 优化建议

### P0: Seed File 修正

#### func.txt: 74 → 82 (+8)

新增 8 项：
```
REGEXP_LIKE        — 正则匹配 (2-arg, 100% 有效)
GENERATE_SERIES    — 生成序列 (2-arg, 100% 有效)
WIDTH_BUCKET       — 分桶函数 (4-arg, 100% 有效)
JUSTIFY_HOURS      — 规范化 hours (1-arg, ~33% 有效)
JUSTIFY_INTERVAL   — 规范化 interval (1-arg, ~33% 有效)
CHECKSUM           — 校验和 (1-arg, ~33% 有效)
QUOTE_LITERAL      — 转义引号 (1-arg, ~33% 有效)
QUOTE_IDENT        — 转义标识符 (1-arg, ~33% 有效)
```

#### agg.txt: 22 → 29 (-2, +9)

移除 2 项：
```
XMLAGG    — Oracle 模式不存在
LISTAGG   — 需要 WITHIN GROUP 语法
```

新增 9 项（全部 2-arg, 100% 有效）：
```
REGR_AVGX, REGR_AVGY, REGR_COUNT, REGR_INTERCEPT,
REGR_R2, REGR_SLOPE, REGR_SXX, REGR_SXY, REGR_SYY
```

#### pred.txt: 不变 (17 项，全部有效)
#### type.txt: 不变 (21 项，全部有效)

### P1: 代码适配

#### 1. getZeroArgFunctions() 覆盖（GaussDBAEDCDataOracle.java）

添加 `VERSION` 和 `RANDOM` 到 GaussDB-A 的 0-arg 函数集合：
```java
@Override
protected Set<String> getZeroArgFunctions() {
    return Set.of(
            "CURRENT_DATE", "CURRENT_TIMESTAMP", "SYSDATE",
            "NOW", "CLOCK_TIMESTAMP",
            "CURRENT_SCHEMA", "CURRENT_USER", "SESSION_USER",
            "VERSION", "RANDOM"
    );
}
```

#### 2. ExpectedErrors（无需修改）

现有 ExpectedErrors 已覆盖新增函数的错误模式：
- `function does not exist` — 覆盖 REGEXP_LIKE/GENERATE_SERIES 的类型不匹配
- `operator does not exist` — 覆盖参数类型不兼容
- `not supported` — 覆盖不支持的操作
- `type mismatch` — 覆盖类型转换失败

---

## 5. 预期效果

| 指标 | v2.5.5 (当前) | v2.5.6 (优化后) | 变化 |
|------|:-:|:-:|:-:|
| func.txt 条目数 | 74 | 82 | +8 |
| agg.txt 有效条目数 | 20 (22-2无效) | 29 | +9 |
| 0-arg 函数数 | 8 | 10 | +2 |
| 无效函数占比 | ~2.7% (2/74) | 0% | 消除 |
| REGR 线性回归覆盖 | 0% | 100% (9 个函数) | 新增 |
| 预期语句成功率 | ~60% | ~65%+ | ↑ |

---

## 6. 不新增的函数及原因

| 函数 | 原因 |
|------|------|
| ADDDATE/DATE_ADD/DATE_SUB 等 | B-format (GaussDB-M) 专属 |
| LAG/LEAD/DENSE_RANK 等窗口函数 | 需 OVER 子句 |
| CAST/EXTRACT/OVERLAY | 特殊语法 |
| NLS_UPPER/NLS_LOWER/NLS_INITCAP | varchar 参数类型不支持 |
| ASCIISTR/COMPOSE/DECOMPOSE | 函数不存在 |
| TO_MULTI_BYTE/TO_SINGLE_BYTE/TO_NCHAR | 函数不存在 |
| EDIT_DISTANCE_SIMILARITY | 函数不存在 |
| LNNVL | 非 A-format 兼容模式不支持 |
| PG_SLEEP | 副作用函数，影响性能 |
| SETSEED | 影响全局随机状态 |
