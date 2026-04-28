# SonarOracle 语法支持范围评估

## 概述

本报告评估 SonarOracle 对 MySQL、PostgreSQL、GaussDB-M 三种数据库的语法支持范围。

---

## 1. MySQL SonarOracle 语法支持

### 1.1 核心语法结构

| 语法类型 | 支持状态 | 说明 |
|----------|----------|------|
| SELECT 基本查询 | ✅ 支持 | SELECT ... FROM ... WHERE |
| JOIN 语句 | ✅ 支持 | INNER/LEFT/RIGHT/CROSS/NATURAL JOIN |
| WHERE 条件 | ✅ 支持 | 嵌套子查询结构 |
| ORDER BY | ✅ 支持 | 随机生成排序表达式 |

### 1.2 表达式类型

| 表达式类型 | 支持状态 | Actions枚举 |
|------------|----------|-------------|
| 列引用 (COLUMN) | ✅ | Actions.COLUMN |
| 常量 (LITERAL) | ✅ | Actions.LITERAL |
| 一元前缀操作 (NOT, !) | ✅ | Actions.UNARY_PREFIX_OPERATION |
| 一元后缀操作 (IS NULL, IS TRUE) | ✅ | Actions.UNARY_POSTFIX |
| 二元逻辑操作 (AND, OR) | ✅ | Actions.BINARY_LOGICAL_OPERATOR |
| 二元比较操作 (=, <, >, <=, >=, !=, LIKE) | ✅ | Actions.BINARY_COMPARISON_OPERATION |
| 二元算术操作 (+, -, *, /, %) | ✅ | Actions.ARITHMETIC_OPERATION |
| CAST 转换 | ✅ | Actions.CAST |
| IN 操作 | ✅ | Actions.IN_OPERATION |
| BETWEEN 操作 | ✅ | Actions.BETWEEN_OPERATOR |
| CASE WHEN | ✅ | Actions.CASE_OPERATOR |
| EXISTS 子查询 | ✅ | Actions.EXISTS |

### 1.3 Fetch Column 表达式（ColumnExpressionActions）

| 类型 | 支持状态 | 说明 |
|------|----------|------|
| BINARY_COMPARISON_OPERATION | ✅ | 列比较表达式 |
| BINARY_OPERATION | ✅ | 列运算表达式 |
| BINARY_ARITH | ✅ | 算术运算表达式 |
| COLUMN | ✅ | 直接列引用 |
| COMPUTABLE_FUNCTION | ✅ | 计算函数调用 |
| AGGREGATE_FUNCTION | ✅ | 聚合函数调用 |

### 1.4 窗口函数（MySQLWindowFunction）

支持 **20+ 种窗口函数**：

| 函数名 | 参数数量 | 类型 |
|--------|----------|------|
| ROW_NUMBER | 0 | 排名函数 |
| RANK | 0 | 排名函数 |
| DENSE_RANK | 0 | 排名函数 |
| CUME_DIST | 0 | 分布函数 |
| PERCENT_RANK | 0 | 分布函数 |
| LAG | 1 | 偏移函数 |
| LEAD | 1 | 偏移函数 |
| AVG | 1 | 聚合窗口 |
| BIT_AND | 1 | 位聚合 |
| BIT_OR | 1 | 位聚合 |
| BIT_XOR | 1 | 位聚合 |
| COUNT | 1 | 聚合窗口 |
| MAX | 1 | 聚合窗口 |
| MIN | 1 | 聚合窗口 |
| STD/STDDEV | 1 | 统计窗口 |
| STDDEV_POP | 1 | 统计窗口 |
| STDDEV_SAMP | 1 | 统计窗口 |
| VAR_POP | 1 | 统计窗口 |
| VAR_SAMP | 1 | 统计窗口 |
| VARIANCE | 1 | 统计窗口 |
| SUM | 1 | 聚合窗口 |

**窗口函数语法格式**：
```sql
FUNCTION_NAME(expr) OVER (PARTITION BY column)
```

### 1.5 计算函数（MySQLComputableFunction）

支持 **60+ 种函数**：

#### 控制流函数
| 函数 | 参数 | 说明 |
|------|------|------|
| IF | 3 | 条件判断 |
| IFNULL | 2 | NULL替换 |
| COALESCE | 可变 | 多参数NULL替换 |

#### 数学函数
| 函数 | 参数 | 说明 |
|------|------|------|
| ABS | 1 | 绝对值 |
| CEIL/FLOOR | 1 | 取整 |
| ROUND | 1 | 四舍五入 |
| MOD | 2 | 取模 |
| SIGN | 1 | 符号 |
| BIT_COUNT | 1 | 位计数 |
| LEAST/GREATEST | 可变 | 最小/最大值 |

#### 字符串函数
| 函数 | 参数 | 说明 |
|------|------|------|
| CONCAT | 可变 | 字符串连接 |
| CONCAT_WS | 可变 | 带分隔符连接 |
| LENGTH/CHAR_LENGTH | 1 | 长度 |
| UPPER/LOWER | 1 | 大小写转换 |
| TRIM/LTRIM/RTRIM | 1 | 去空格 |
| LEFT/RIGHT | 2 | 取子串 |
| SUBSTRING | 可变 | 子串 |
| REPLACE | 3 | 替换 |
| LOCATE/INSTR | 可变 | 查找位置 |
| LPAD/RPAD | 3 | 填充 |
| REVERSE | 1 | 反转 |
| REPEAT | 2 | 重复 |
| SPACE | 1 | 空格串 |
| ASCII | 1 | ASCII码 |

#### JSON 函数
| 函数 | 参数 | 说明 |
|------|------|------|
| JSON_TYPE | 1 | JSON类型 |
| JSON_VALID | 1 | JSON验证 |
| JSON_EXTRACT | 可变 | JSON提取 |
| JSON_ARRAY | 可变 | JSON数组 |
| JSON_OBJECT | 可变 | JSON对象 |
| JSON_REMOVE | 可变 | JSON删除 |
| JSON_CONTAINS | 可变 | JSON包含检查 |
| JSON_KEYS | 可变 | JSON键列表 |

#### 时间日期函数
| 函数 | 参数 | 说明 |
|------|------|------|
| NOW | 0 | 当前时间 |
| CURDATE | 0 | 当前日期 |
| CURTIME | 0 | 当前时间 |
| YEAR/MONTH/DAY | 1 | 提取年月日 |
| DAYOFWEEK/DAYOFMONTH/DAYOFYEAR | 1 | 天数 |
| WEEK/QUARTER | 1 | 周/季度 |
| HOUR/MINUTE/SECOND | 1 | 时分秒 |
| DATEDIFF | 2 | 日期差 |
| LAST_DAY | 1 | 月末日期 |
| TO_DAYS/FROM_DAYS | 1 | 天数转换 |

#### TemporalFunction（额外时间函数）
| 函数 | 说明 |
|------|------|
| DATE_ADD | 日期加 |
| DATE_SUB | 日期减 |
| ADDDATE | 添加日期 |
| SUBDATE | 减日期 |

### 1.6 聚合函数（MySQLAggregateFunction）

| 函数 | 说明 |
|------|------|
| AVG | 平均值 |
| BIT_AND/BIT_OR/BIT_XOR | 位聚合 |
| COUNT | 计数 |
| MAX/MIN | 最大最小值 |
| STD/STDDEV_POP/STDDEV_SAMP | 标准差 |
| VAR_POP/VAR_SAMP/VARIANCE | 方差 |
| SUM | 求和 |

### 1.7 CAST 类型

| 类型 | 支持 |
|------|------|
| SIGNED/UNSIGNED | ✅ |
| BINARY | ✅ |
| CHAR | ✅ |
| DATE/DATETIME/TIME | ✅ |
| DECIMAL | ✅ |
| JSON | ✅ |

### 1.8 JOIN 类型

| 类型 | 支持 |
|------|------|
| INNER JOIN | ✅ |
| LEFT OUTER JOIN | ✅ |
| RIGHT OUTER JOIN | ✅ |
| CROSS JOIN | ✅ |
| NATURAL JOIN | ✅ |

---

## 2. PostgreSQL SonarOracle 语法支持

### 2.1 核心语法结构

| 语法类型 | 支持状态 | 说明 |
|----------|----------|------|
| SELECT 基本查询 | ✅ | SELECT ... FROM ... WHERE |
| JOIN 语句 | ✅ | 支持多种JOIN类型 |
| WHERE 条件 | ✅ | 嵌套子查询结构 |
| FROM 子查询 | ✅ | PostgresFromTable |

### 2.2 表达式类型（BooleanExpression枚举）

| 表达式类型 | 支持状态 | 说明 |
|------------|----------|------|
| POSTFIX_OPERATOR | ✅ | IS NULL, IS TRUE等 |
| NOT | ✅ | 逻辑非 |
| BINARY_LOGICAL_OPERATOR | ✅ | AND, OR |
| BINARY_COMPARISON | ✅ | =, <, >, <=, >=, !=, LIKE |
| FUNCTION | ✅ | 内置函数 |
| CAST | ✅ | 类型转换 |
| LIKE | ✅ | LIKE模式匹配 |
| BETWEEN | ✅ | BETWEEN操作 |
| IN_OPERATION | ✅ | IN操作 |
| SIMILAR_TO | ✅ | SIMILAR TO正则 |
| POSIX_REGEX | ✅ | POSIX正则表达式 |
| BINARY_RANGE_COMPARISON | ✅ | 范围类型比较 |
| JSON_CONTAIN | ✅ | JSON包含操作 |

### 2.3 Fetch Column 表达式

| 类型 | 支持状态 | 说明 |
|------|----------|------|
| Binary comparison | ✅ | 列比较表达式 |
| Binary arithmetic | ✅ | 算术运算 |
| Aggregate function | ✅ | 聚合函数 |
| Cast operation | ✅ | 类型转换 |
| Column reference | ✅ | 直接列引用 |

### 2.4 PostgreSQL 特有语法

| 特性 | 支持状态 | 说明 |
|------|----------|------|
| JSON操作 (@>, ?, ?|) | ✅ | PostgresBinaryJsonOperation |
| Range类型 | ✅ | PostgresBinaryRangeOperation |
| POSIX正则 (~, ~*, !~, !~*) | ✅ | PostgresPOSIXRegularExpression |
| SIMILAR TO | ✅ | PostgresSimilarTo |
| 窗口函数 | ✅ | PostgresWindowFunction (完整支持) |
| 时间类型运算 | ✅ | PostgresTemporalBinaryArithmeticOperation |
| Bit操作 | ✅ | PostgresBinaryBitOperation |
| Collate | ✅ | PostgresCollate |
| Locking Clause | ✅ | FOR UPDATE/SHARE |

### 2.5 窗口函数（PostgresWindowFunction）

PostgreSQL 窗口函数支持更完整：
- WindowSpecification（窗口定义）
- WindowFrame（帧定义：ROWS/RANGE）
- PARTITION BY
- ORDER BY

### 2.6 内置函数

| 函数 | 支持状态 |
|------|----------|
| ABS | ✅ |
| LOWER/UPPER | ✅ |
| LENGTH | ✅ |
| ARRAY_LENGTH | ✅ |
| CARDINALITY | ✅ |
| NUM_NONNULLS/NUM_NULLS | ✅ |

### 2.7 JOIN 类型

| 类型 | 支持 |
|------|------|
| INNER JOIN | ✅ |
| LEFT OUTER JOIN | ✅ |
| RIGHT OUTER JOIN | ✅ |
| FULL OUTER JOIN | ✅ |
| CROSS JOIN | ✅ |

### 2.8 数据类型

PostgreSQL 支持更丰富的数据类型：
- INT, FLOAT, DOUBLE
- TEXT, CHAR, VARCHAR
- BOOLEAN
- DATE, TIME, TIMESTAMP, TIMESTAMPTZ
- JSON, JSONB
- ARRAY
- RANGE (int4range, int8range, numrange等)
- UUID
- BIT, VARBIT

---

## 3. GaussDB-M SonarOracle 语法支持

### 3.1 核心语法结构

| 语法类型 | 支持状态 | 说明 |
|----------|----------|------|
| SELECT 基本查询 | ✅ | SELECT ... FROM ... WHERE |
| JOIN 语句 | ✅ | GaussDBJoin.getRandomJoinClauses |
| WHERE 条件 | ✅ | 嵌套子查询结构 |

### 3.2 表达式类型（Actions枚举）

| 表达式类型 | 支持状态 | Actions枚举 |
|------------|----------|-------------|
| COLUMN | ✅ | 列引用 |
| LITERAL | ✅ | 常量 |
| BINARY_LOGICAL_OPERATOR | ✅ | AND, OR |
| BINARY_COMPARISON_OPERATION | ✅ | =, <, >, <=, >=, !=, LIKE |
| BETWEEN_OPERATOR | ✅ | BETWEEN操作 |
| UNARY_PREFIX_OPERATION | ✅ | NOT |
| UNARY_POSTFIX_OPERATION | ✅ | IS NULL |

### 3.3 GaussDB-M 与 MySQL 对比

| 特性 | GaussDB-M | MySQL |
|------|-----------|-------|
| 表达式复杂度 | 较简单 | 较丰富 |
| 窗口函数 | ❌ 不支持 | ✅ 支持20+种 |
| 计算函数 | ❌ 不支持 | ✅ 支持60+种 |
| CAST操作 | ❌ 不支持 | ✅ 支持 |
| IN操作 | ❌ 不支持 | ✅ 支持 |
| CASE WHEN | ❌ 不支持 | ✅ 支持 |
| EXISTS | ❌ 不支持 | ✅ 支持 |
| JSON函数 | ❌ 不支持 | ✅ 支持 |
| 时间函数 | ❌ 不支持 | ✅ 支持 |
| FetchColumn表达式 | ❌ 不支持扩展 | ✅ 支持6种类型 |
| WhereColumn表达式 | ❌ 不支持扩展 | ✅ 支持3种类型 |

### 3.4 GaussDB-M 限制说明

**当前GaussDB-M SonarOracle实现较为基础**：

1. **无 generateFetchColumnExpression** - 仅使用简单列引用
2. **无 generateWhereColumnExpression** - 仅使用标准WHERE条件
3. **无 generateWindowFuc** - 不支持窗口函数测试
4. **无 getRandomJoinClauses（扩展版）** - 使用基础JOIN支持

**测试覆盖范围受限**：
- 仅测试基本 WHERE 条件优化
- 无法测试窗口函数优化Bug
- 无法测试复杂表达式优化Bug

---

## 4. 语法支持对比总结

### 4.1 整体对比

| 维度 | MySQL | PostgreSQL | GaussDB-M |
|------|-------|------------|-----------|
| 表达式类型数 | 13种 | 13种 | 7种 |
| 内置函数数 | 60+ | 8+ | 0 |
| 窗口函数数 | 20+ | 完整 | 0 |
| JSON支持 | ✅ 完整 | ✅ 完整 | ❌ |
| Range类型 | ❌ | ✅ | ❌ |
| 正则表达式 | LIKE | POSIX+SIMILAR TO | LIKE |
| FetchColumn扩展 | ✅ 6种 | ✅ 5种 | ❌ |
| WhereColumn扩展 | ✅ 3种 | ✅ 3种 | ❌ |
| JOIN类型 | 5种 | 5种 | 支持 |

### 4.2 Bug检测能力评估

| Bug类型 | MySQL | PostgreSQL | GaussDB-M |
|---------|-------|------------|-----------|
| WHERE优化Bug | ✅ 高覆盖 | ✅ 高覆盖 | ⚠️ 中等覆盖 |
| 窗口函数Bug | ✅ 可检测 | ✅ 可检测 | ❌ 无法检测 |
| 函数优化Bug | ✅ 可检测 | ✅ 可检测 | ❌ 无法检测 |
| CAST优化Bug | ✅ 可检测 | ✅ 可检测 | ❌ 无法检测 |
| JOIN优化Bug | ✅ 可检测 | ✅ 可检测 | ✅ 可检测 |
| JSON优化Bug | ✅ 可检测 | ✅ 可检测 | ❌ 无法检测 |

---

## 5. GaussDB-M 扩展建议

### 5.1 需要添加的功能

| 优先级 | 功能 | 预估工作量 |
|--------|------|------------|
| P0 | generateFetchColumnExpression | 0.5天 |
| P0 | generateWhereColumnExpression | 0.5天 |
| P1 | 窗口函数支持 | 1天 |
| P1 | 计算函数支持 | 1天 |
| P2 | CAST操作支持 | 0.5天 |
| P2 | IN/BETWEEN扩展 | 0.5天 |

### 5.2 扩展后预期效果

扩展后 GaussDB-M SonarOracle 检测能力将与 MySQL 相当，可覆盖：
- 窗口函数优化Bug
- 函数计算优化Bug
- CAST类型转换Bug
- IN/EXISTS优化Bug

---

## 6. 结论

### MySQL SonarOracle
✅ **语法支持最完整**，覆盖60+函数、20+窗口函数、6种FetchColumn表达式类型

### PostgreSQL SonarOracle
✅ **语法支持丰富**，特有的JSON操作、Range类型、POSIX正则、完整窗口函数帧定义

### GaussDB-M SonarOracle
⚠️ **语法支持基础**，仅有基本表达式类型，缺少函数和窗口函数支持，建议扩展

---

*评估日期: 2026-04-28*
*评估工具: Claude Code*