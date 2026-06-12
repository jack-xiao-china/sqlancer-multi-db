# GaussDB-M EDC_DATA Seed File 语法覆盖分析

> 基于《GaussDB M-Compatibility 开发指南》(2026-04-02) 与当前 seed file 对比

## 1. 结论先行

当前 GaussDB-M 的 seed file 直接复制自 MySQL，存在 **3 类系统性问题**：

| 问题类别 | 数量 | 影响级别 | 影响 |
|---------|------|---------|------|
| **A. Seed 中有但 GaussDB-M 不支持** | ~100 项 | 🔴 高 | 浪费测试迭代，降低有效覆盖率 |
| **B. GaussDB-M 支持但 Seed 中缺失** | ~20 项 | 🟡 中 | 漏测，缩小了测试面 |
| **C. 用法不兼容** | 7 项 | 🔴 高 | 窗口函数需要 OVER 子句，但 EDC_DATA 不生成 |

**综合影响**：预估当前 seed file 在 GaussDB-M 上的**有效测试覆盖率约 65%**，约 35% 的测试迭代被浪费在必然失败的函数/类型上。

---

## 2. 详细对比

### 2.1 函数 (func.txt) — 340 项

#### A. Seed 中有但 GaussDB-M 不支持（或属于 MySQL 内部函数）

**MySQL 内部/权限函数（~20 项）** — 在 GaussDB-M 上必然报错：

| 函数 | 说明 | 建议 |
|------|------|------|
| `CAN_ACCESS_COLUMN` | MySQL 内部权限检查 | ❌ 移除 |
| `CAN_ACCESS_DATABASE` | MySQL 内部权限检查 | ❌ 移除 |
| `CAN_ACCESS_TABLE` | MySQL 内部权限检查 | ❌ 移除 |
| `CAN_ACCESS_USER` | MySQL 内部权限检查 | ❌ 移除 |
| `CAN_ACCESS_VIEW` | MySQL 内部权限检查 | ❌ 移除 |
| `GET_DD_COLUMN_PRIVILEGES` | MySQL 数据字典内部函数 | ❌ 移除 |
| `GET_DD_CREATE_OPTIONS` | MySQL 数据字典内部函数 | ❌ 移除 |
| `GET_DD_INDEX_SUB_PART_LENGTH` | MySQL 数据字典内部函数 | ❌ 移除 |
| `GTID_SUBSET` | MySQL GTID 复制函数 | ❌ 移除 |
| `GTID_SUBTRACT` | MySQL GTID 复制函数 | ❌ 移除 |
| `WAIT_FOR_EXECUTED_GTID_SET` | MySQL GTID 复制函数 | ❌ 移除 |
| `WAIT_UNTIL_SQL_THREAD_AFTER_GTIDS` | MySQL GTID 复制函数 | ❌ 移除 |
| `MASTER_POS_WAIT` | MySQL 复制函数 | ❌ 移除 |
| `SOURCE_POS_WAIT` | MySQL 复制函数 | ❌ 移除 |
| `GET_LOCK` | MySQL 用户锁函数 | ❌ 移除 |
| `RELEASE_LOCK` | MySQL 用户锁函数 | ❌ 移除 |
| `RELEASE_ALL_LOCKS` | MySQL 用户锁函数 | ❌ 移除 |
| `IS_FREE_LOCK` | MySQL 用户锁函数 | ❌ 移除 |
| `IS_USED_LOCK` | MySQL 用户锁函数 | ❌ 移除 |
| `VALIDATE_PASSWORD_STRENGTH` | MySQL 密码验证插件 | ❌ 移除 |
| `ROLES_GRAPHML` | MySQL 角色图函数 | ❌ 移除 |
| `STATEMENT_DIGEST` | MySQL Performance Schema | ❌ 移除 |
| `STATEMENT_DIGEST_TEXT` | MySQL Performance Schema | ❌ 移除 |
| `ICU_VERSION` | MySQL ICU 版本信息 | ❌ 移除 |
| `LOAD_FILE` | 文件读取（需 FILE 权限） | ❌ 移除 |
| `VALUES` | INSERT...VALUES 上下文函数 | ❌ 移除 |

**空间函数（~70 项）** — GaussDB-M 文档中**完全没有**空间/几何函数章节：

| 类别 | 函数示例 | 数量 | 建议 |
|------|---------|------|------|
| ST_* 函数 | ST_Area, ST_Buffer, ST_Contains... | ~50 | ❌ 移除 |
| MBR* 函数 | MBRContains, MBRDisjoint... | ~9 | ❌ 移除 |
| 几何构造器 | Point, Polygon, MultiPoint... | ~5 | ❌ 移除 |
| 其他 | ST_FrechetDistance, ST_HausdorffDistance... | ~6 | ❌ 移除 |

**MySQL 8.0 新增但 GaussDB-M 未收录的 JSON 函数（8 项）**：

| 函数 | GaussDB-M 文档 | 建议 |
|------|---------------|------|
| `JSON_OVERLAPS` | ❌ 未记录 | ❌ 移除 |
| `JSON_PRETTY` | ❌ 未记录 | ❌ 移除 |
| `JSON_TABLE` | ❌ 未记录 | ❌ 移除 |
| `JSON_VALUE` | ❌ 未记录 | ❌ 移除 |
| `JSON_SCHEMA_VALID` | ❌ 未记录 | ❌ 移除 |
| `JSON_SCHEMA_VALIDATION_REPORT` | ❌ 未记录 | ❌ 移除 |
| `JSON_STORAGE_FREE` | ❌ 未记录 | ❌ 移除 |
| `JSON_STORAGE_SIZE` | ❌ 未记录 | ❌ 移除 |

**其他不兼容函数**：

| 函数 | 说明 | 建议 |
|------|------|------|
| `MEMBER OF` | MySQL JSON 操作符，非标准函数 | ❌ 移除 |
| `MATCH` | MySQL 全文搜索，语法特殊 | ❌ 移除 |
| `BIN_TO_UUID` | MySQL 8.0 UUID 转换 | ⚠️ 待验证 |
| `UUID_TO_BIN` | MySQL 8.0 UUID 转换 | ⚠️ 待验证 |

#### B. GaussDB-M 支持但 Seed 中缺失

| 函数 | 类别 | GaussDB-M 文档 | 建议 |
|------|------|---------------|------|
| `DIV` | 算术 | ✅ 4.5.6 整除操作符 | ✅ 新增 |
| `CURDATE()` | 日期 | ✅ 4.5.8 | ✅ 新增 |
| `CURRENT_DATE` | 日期 | ✅ 4.5.8 | ✅ 新增 |
| `CURRENT_TIME` | 日期 | ✅ 4.5.8 | ✅ 新增 |
| `CURRENT_TIMESTAMP` | 日期 | ✅ 4.5.8 | ✅ 新增 |
| `CURTIME()` | 日期 | ✅ 4.5.8 | ✅ 新增 |
| `LAST_DAY()` | 日期 | ✅ 4.5.8 | ✅ 新增 |
| `REPEAT()` | 字符串 | ✅ 4.5.7 | ✅ 新增 |
| `SPACE()` | 字符串 | ✅ 4.5.7 | ✅ 新增 |
| `BIT_LENGTH()` | 字符串 | ✅ 4.5.7 | ✅ 新增 |
| `MID()` | 字符串 | ✅ 4.5.7 (SUBSTR 别名) | ✅ 新增 |
| `GROUP_CONCAT()` | 聚合 | ✅ 4.5.11 | ✅ 新增 |
| `ANY_VALUE()` | 聚合 | ✅ 4.5.19.13 | ✅ 新增 |
| `RAND()` | 数学 | ✅ 4.5.6 | ✅ 新增 |
| `CONV()` | 数学 | ✅ 4.5.6 | ✅ 新增 |
| `CRC32()` | 数学 | ✅ 4.5.6 | ✅ 新增 |

#### C. 用法不兼容 — 窗口函数

以下函数在 seed file 中存在，但它们需要 `OVER (...)` 子句才能执行。EDC_DATA 当前以 `FUNC(col1, col2)` 方式调用，窗口函数会**直接报语法错误**：

| 函数 | 说明 | 建议 |
|------|------|------|
| `FIRST_VALUE` | 窗口函数，需 OVER 子句 | ❌ 移除 |
| `LAST_VALUE` | 窗口函数，需 OVER 子句 | ❌ 移除 |
| `LAG` | 窗口函数，需 OVER 子句 | ❌ 移除 |
| `LEAD` | 窗口函数，需 OVER 子句 | ❌ 移除 |
| `NTH_VALUE` | 窗口函数，需 OVER 子句 | ❌ 移除 |
| `NTILE` | 窗口函数，需 OVER 子句 | ❌ 移除 |
| `ROW_NUMBER` | 窗口函数，需 OVER 子句 | ❌ 移除 |

---

### 2.2 聚合函数 (agg.txt) — 20 项

#### A. Seed 中有但 GaussDB-M 不支持

| 聚合函数 | GaussDB-M 文档 | 建议 |
|---------|---------------|------|
| `STDDEV` | ❌ 文档中未出现 | ❌ 移除 |
| `STDDEV_POP` | ❌ 文档中未出现 | ❌ 移除 |
| `STDDEV_SAMP` | ❌ 文档中未出现 | ❌ 移除 |
| `VAR_POP` | ❌ 文档中未出现 | ❌ 移除 |
| `VAR_SAMP` | ❌ 文档中未出现 | ❌ 移除 |
| `VARIANCE` | ❌ 文档中未出现 | ❌ 移除 |
| `JSON_ARRAYAGG` | ❌ 文档中未出现 | ❌ 移除 |
| `JSON_OBJECTAGG` | ❌ 文档中未出现 | ❌ 移除 |
| `MEDIAN` | ❌ 文档中未出现 | ❌ 移除 |
| `ST_Collect` | ❌ 空间函数不存在 | ❌ 移除 |

#### GaussDB-M 官方支持的聚合函数

| 函数 | 文档 | Seed 中 |
|------|------|---------|
| AVG | ✅ | ✅ |
| COUNT | ✅ | ✅ |
| MAX | ✅ | ✅ |
| MIN | ✅ | ✅ |
| SUM | ✅ | ✅ |
| BIT_AND | ✅ | ✅ |
| BIT_OR | ✅ | ✅ |
| BIT_XOR | ✅ | ✅ |
| STD | ✅ | ✅ |
| GROUP_CONCAT | ✅ | ❌ **缺失** |

**结论**：20 项中仅 10 项有效，10 项应移除。缺失 1 项 GROUP_CONCAT。

---

### 2.3 谓词 (pred.txt) — 15 项

当前 pred.txt 包含：`>`, `<=`, `<>`, `=`, AND, OR, XOR, LIKE, NOT LIKE, IN, IS, NOT IN, BETWEEN, IS NULL, IS NOT NULL`

#### 缺失的 GaussDB-M 支持谓词

| 谓词 | GaussDB-M 文档 | 建议 |
|------|---------------|------|
| `<=>` | ✅ NULL 安全等于 (4.5.1) | ✅ 新增（需代码适配） |
| `!=` | ✅ 4.5.1 | ✅ 新增（与 <> 等价但可测试别名） |
| `DIV` | ✅ 整除操作符 (4.5.1) | ⚠️ 可加（二元操作） |
| `NOT` | ✅ 逻辑非 (4.5.2) | ⚠️ 需代码适配一元操作 |

---

### 2.4 数据类型 (type.txt) — 35 项

#### A. Seed 中有但 GaussDB-M 不支持的类型

| 类型 | 说明 | 建议 |
|------|------|------|
| `POINT` | 空间类型，GaussDB-M 无空间支持 | ❌ 移除 |
| `LINESTRING` | 空间类型 | ❌ 移除 |
| `POLYGON` | 空间类型 | ❌ 移除 |
| `MULTIPOINT` | 空间类型 | ❌ 移除 |
| `MULTILINESTRING` | 空间类型 | ❌ 移除 |
| `MULTIPOLYGON` | 空间类型 | ❌ 移除 |
| `GEOMETRYCOLLECTION` | 空间类型 | ❌ 移除 |

#### B. GaussDB-M 支持但 Seed 中缺失的类型

| 类型 | GaussDB-M 文档 | 建议 |
|------|---------------|------|
| `YEAR` | ✅ 4.6.5 日期时间类型 | ✅ 新增 |
| `TINYINT UNSIGNED` | ✅ 4.6.7.2 | ✅ 新增 |
| `SMALLINT UNSIGNED` | ✅ 4.6.7.2 | ✅ 新增 |
| `MEDIUMINT UNSIGNED` | ✅ 4.6.7.2 | ✅ 新增 |
| `INT UNSIGNED` | ✅ 4.6.7.2 | ✅ 新增 |
| `BIGINT UNSIGNED` | ✅ 4.6.7.2 | ✅ 新增 |

**UNSIGNED 类型的影响**：GaussDB-M 的 UNSIGNED 类型在算术运算时有不同的类型提升规则（4.5.6 表 4-41），是 GaussDB-M 特有行为，EDC_DATA 测试这类差异非常有价值。

---

### 2.5 mapType() 映射缺失

`GaussDBMEDCDataTableBuilder.mapType()` 当前未覆盖的类型：

| JDBC 类型名 | 应映射为 | 说明 |
|------------|---------|------|
| `year` | `year` | YEAR 类型 |
| `int unsigned` / `int1` | `integer unsigned` | UNSIGNED 整数 |
| `int2 unsigned` | `smallint unsigned` | |
| `int4 unsigned` | `integer unsigned` | |
| `int8 unsigned` | `bigint unsigned` | |
| `bit` | `bit` | BIT 类型 |
| `set` | `text` | SET 集合类型 |
| `enum` | `text` | ENUM 枚举类型 |

---

## 3. 影响评估

### 3.1 当前 Seed File 有效率

| 文件 | 总项数 | 有效项 | 有效率 |
|------|-------|-------|--------|
| func.txt | 340 | ~240 | ~71% |
| agg.txt | 20 | 10 | 50% |
| pred.txt | 15 | 15 | 100% |
| type.txt | 35 | ~28 | ~80% |

### 3.2 每次测试迭代的浪费

EDC_DATA 每次 `check()` 执行流程：
1. 随机选择 operation type (AGGREGATE/FUNCTION/PREDICATE)
2. 从对应 seed 中随机选择操作
3. 生成随机列类型（从 type.txt）
4. 创建表 → 插入数据 → 创建派生表 → 执行 50 次 SELECT 对比

**浪费路径**：
- 选中不支持的函数（~29% 概率）→ 必然报错/跳过 → 浪费 1 次完整迭代
- 选中不支持的聚合（50% 概率）→ 必然报错/跳过 → 浪费 1 次完整迭代
- 选中空间类型列（7/35 = 20% 概率）→ 建表失败 → 浪费 1 次完整迭代

**综合浪费率**：约 30-35% 的测试迭代被浪费。

### 3.3 漏测的有价值场景

| 缺失项 | 测试价值 | 说明 |
|--------|---------|------|
| `DIV` 整除 | 🔴 高 | GaussDB-M 特有操作符，类型提升规则独特 |
| `UNSIGNED` 类型 | 🔴 高 | GaussDB-M 独有行为，算术运算易出错 |
| `GROUP_CONCAT` | 🟡 中 | 常用聚合函数 |
| `YEAR` 类型 | 🟡 中 | GaussDB-M 支持但常被忽略 |
| `<=>` NULL安全等于 | 🟡 中 | MySQL/GaussDB-M 特有操作符 |
| 日期函数 (CURDATE等) | 🟡 中 | 基础函数覆盖 |

---

## 4. 建议

### 4.1 优先级排序

**P0 — 立即修复（影响测试有效性）**：

1. **从 func.txt 移除 ~100 项不支持的函数**（MySQL内部 + 空间 + 不兼容JSON + 窗口函数）
2. **从 agg.txt 移除 10 项不支持的聚合函数**
3. **从 type.txt 移除 7 项空间类型**

**P1 — 短期优化（提升测试覆盖）**：

4. **向 func.txt 新增 ~15 项 GaussDB-M 支持的函数**
5. **向 agg.txt 新增 GROUP_CONCAT**
6. **向 type.txt 新增 YEAR + 5 种 UNSIGNED 类型**
7. **补充 mapType() 映射**

**P2 — 中期增强（代码层面适配）**：

8. **向 pred.txt 新增 `<=>` 和 `DIV`**（需代码适配）
9. **EDCDataExpressionBuilder 支持 UNSIGNED 类型的随机值生成**

### 4.2 实施建议

由于 GaussDB-M 和 MySQL 的 seed file 当前完全相同，建议：

1. **独立维护 GaussDB-M 的 seed file**，不再与 MySQL 共用
2. 使用上述分析作为裁剪依据
3. 修改后运行 `--oracle EDC_DATA --num-tries 500` 验证有效覆盖率提升

### 4.3 预期效果

| 指标 | 修改前 | 修改后（预期） |
|------|--------|-------------|
| 有效迭代率 | ~65% | ~90%+ |
| 函数覆盖 | 240/340 有效 | 255/255 有效 |
| 聚合覆盖 | 10/20 有效 | 11/11 有效 |
| 类型覆盖 | 28/35 有效 | 34/34 有效 |
| 单轮测试耗时 | 含大量无效迭代 | 减少 ~30% 无效等待 |

---

## 5. 变更清单

### func.txt 需要移除的函数（~100项）

```
# MySQL 内部函数（~26项）
BIN_TO_UUID, CAN_ACCESS_COLUMN, CAN_ACCESS_DATABASE, CAN_ACCESS_TABLE,
CAN_ACCESS_USER, CAN_ACCESS_VIEW, GET_DD_COLUMN_PRIVILEGES,
GET_DD_CREATE_OPTIONS, GET_DD_INDEX_SUB_PART_LENGTH, GET_LOCK,
GTID_SUBSET, GTID_SUBTRACT, ICU_VERSION, IS_FREE_LOCK, IS_USED_LOCK,
LOAD_FILE, MASTER_POS_WAIT, MATCH, MEMBER OF, RELEASE_ALL_LOCKS,
RELEASE_LOCK, ROLES_GRAPHML, SOURCE_POS_WAIT, STATEMENT_DIGEST,
STATEMENT_DIGEST_TEXT, VALIDATE_PASSWORD_STRENGTH, VALUES,
WAIT_FOR_EXECUTED_GTID_SET, WAIT_UNTIL_SQL_THREAD_AFTER_GTIDS

# 窗口函数（7项）— 需要 OVER 子句
FIRST_VALUE, LAG, LAST_VALUE, LEAD, NTH_VALUE, NTILE, ROW_NUMBER

# 不支持的 JSON 函数（8项）
JSON_OVERLAPS, JSON_PRETTY, JSON_TABLE, JSON_VALUE,
JSON_SCHEMA_VALID, JSON_SCHEMA_VALIDATION_REPORT,
JSON_STORAGE_FREE, JSON_STORAGE_SIZE

# 空间函数（~70项）
ST_Area, ST_AsBinary, ST_AsGeoJSON, ST_AsText, ST_Buffer,
ST_Buffer_Strategy, ST_Centroid, ST_Contains, ST_ConvexHull,
ST_Crosses, ST_Difference, ST_Dimension, ST_Disjoint, ST_Distance,
ST_Distance_Sphere, ST_EndPoint, ST_Envelope, ST_Equals,
ST_ExteriorRing, ST_FrechetDistance, ST_GeoHash,
ST_GeomCollFromText, ST_GeomCollFromWKB, ST_GeometryN,
ST_GeometryType, ST_GeomFromGeoJSON, ST_GeomFromText,
ST_GeomFromWKB, ST_HausdorffDistance, ST_InteriorRingN,
ST_Intersection, ST_Intersects, ST_IsClosed, ST_IsEmpty,
ST_IsSimple, ST_IsValid, ST_LatFromGeoHash, ST_Latitude,
ST_Length, ST_LineFromText, ST_LineFromWKB,
ST_LineInterpolatePoint, ST_LineInterpolatePoints,
ST_LongFromGeoHash, ST_Longitude, ST_MakeEnvelope,
ST_MLineFromText, ST_MLineFromWKB, ST_MPointFromText,
ST_MPointFromWKB, ST_MPolyFromText, ST_MPolyFromWKB,
ST_NumGeometries, ST_NumInteriorRing, ST_NumPoints,
ST_Overlaps, ST_PointAtDistance, ST_PointFromGeoHash,
ST_PointFromText, ST_PointFromWKB, ST_PointN,
ST_PolyFromText, ST_PolyFromWKB, ST_Simplify, ST_SRID,
ST_StartPoint, ST_SwapXY, ST_SymDifference, ST_Touches,
ST_Transform, ST_Union, ST_Validate, ST_Within, ST_X, ST_Y,
MBRContains, MBRCoveredBy, MBRCovers, MBRDisjoint, MBREquals,
MBRIntersects, MBROverlaps, MBRTouches, MBRWithin,
Point, Polygon, MultiLineString, MultiPoint, MultiPolygon
```

### func.txt 需要新增的函数（~16项）

```
DIV, CURDATE, CURRENT_DATE, CURRENT_TIME, CURRENT_TIMESTAMP,
CURTIME, LAST_DAY, REPEAT, SPACE, BIT_LENGTH, MID,
GROUP_CONCAT, ANY_VALUE, RAND, CONV, CRC32
```

### agg.txt 需要移除的聚合（10项）

```
STDDEV, STDDEV_POP, STDDEV_SAMP, VAR_POP, VAR_SAMP,
VARIANCE, JSON_ARRAYAGG, JSON_OBJECTAGG, MEDIAN, ST_Collect
```

### agg.txt 需要新增的聚合（1项）

```
GROUP_CONCAT
```

### type.txt 需要移除的类型（7项）

```
POINT, LINESTRING, POLYGON, MULTIPOINT, MULTILINESTRING,
MULTIPOLYGON, GEOMETRYCOLLECTION
```

### type.txt 需要新增的类型（6项）

```
YEAR, TINYINT UNSIGNED, SMALLINT UNSIGNED, MEDIUMINT UNSIGNED,
INT UNSIGNED, BIGINT UNSIGNED
```
