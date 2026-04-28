# GaussDB-M MySQL兼容模式语法覆盖评估

## 概述

本报告详细评估 GaussDB-M MySQL兼容模式在 SQLancer SonarOracle 中的语法覆盖情况，包括操作符、表达式、函数、数据类型四个维度，并提供扩展建议。

---

## 1. 操作符覆盖评估

### 1.1 比较操作符

| 操作符 | MySQL语法 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|--------|----------|-----------|---------------|------|----------|
| `=` | 相等比较 | ✅ EQUALS | ✅ EQUALS | 已支持 | - |
| `<>` / `!=` | 不相等 | ✅ NOT_EQUALS | ✅ NOT_EQUALS (<>) | 已支持 | 添加!=别名 |
| `<` | 小于 | ✅ LESS | ✅ SMALLER | 已支持 | 命名统一 |
| `<=` | 小于等于 | ✅ LESS_EQUALS | ✅ SMALLER_EQUALS | 已支持 | 命名统一 |
| `>` | 大于 | ✅ GREATER | ✅ GREATER | 已支持 | - |
| `>=` | 大于等于 | ✅ GREATER_EQUALS | ✅ GREATER_EQUALS | 已支持 | - |
| **LIKE** | 模式匹配 | ✅ LIKE | ❌ **缺失** | **必须扩展** | P0 |
| **<=>** | NULL安全相等 | ✅ IS_EQUALS_NULL_SAFE | ❌ **缺失** | **需要扩展** | P1 |

**覆盖率**: 6/8 = **75%**

### 1.2 逻辑操作符

| 操作符 | MySQL语法 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|--------|----------|-----------|---------------|------|----------|
| AND | 逻辑与 | ✅ | ✅ | 已支持 | - |
| && | 逻辑与(别名) | ✅ | ❌ 别名不支持 | 可选扩展 | P2 |
| OR | 逻辑或 | ✅ | ✅ | 已支持 | - |
| \|\| | 逻辑或(别名) | ✅ | ❌ 别名不支持 | 可选扩展 | P2 |
| **XOR** | 逻辑异或 | ✅ | ❌ **缺失** | **需要扩展** | P1 |

**覆盖率**: 2/3 = **67%** (核心操作符)

### 1.3 算术操作符

| 操作符 | MySQL语法 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|--------|----------|-----------|---------------|------|----------|
| + | 加法 | ✅ PLUS | ❌ **缺失** | **必须扩展** | P0 |
| - | 减法 | ✅ MINUS | ❌ **缺失** | **必须扩展** | P0 |
| * | 乘法 | ✅ MULTIPLICATION | ❌ **缺失** | **必须扩展** | P0 |
| / | 除法 | ✅ DIVISION | ❌ **缺失** | **必须扩展** | P0 |
| % / MOD | 取模 | ✅ MOD | ❌ **缺失** | **必须扩展** | P0 |
| DIV | 整数除法 | ✅ | ❌ 缺失 | P1 | |
| - (负号) | 一元负号 | ✅ MINUS | ❌ **缺失** | **必须扩展** | P0 |
| + (正号) | 一元正号 | ✅ PLUS | ❌ 缺失 | P2 | |

**覆盖率**: 0/8 = **0%** (完全缺失)

### 1.4 位操作符

| 操作符 | MySQL语法 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|--------|----------|-----------|---------------|------|----------|
| & | 位AND | ✅ MySQLBinaryOperation.AND | ❌ **缺失** | P1 |
| \| | 位OR | ✅ MySQLBinaryOperation.OR | ❌ **缺失** | P1 |
| ^ | 位XOR | ✅ MySQLBinaryOperation.XOR | ❌ **缺失** | P1 |
| ~ | 位NOT | ✅ BIT_INVERT | ❌ **缺失** | P1 |
| << | 左移 | ✅ | ❌ **缺失** | P2 |
| >> | 右移 | ✅ | ❌ **缺失** | P2 |

**覆盖率**: 0/6 = **0%** (完全缺失)

### 1.5 一元操作符

| 操作符 | MySQL语法 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|--------|----------|-----------|---------------|------|----------|
| NOT | 逻辑非 | ✅ NOT | ✅ NOT | 已支持 | - |
| ! | 逻辑非(别名) | ✅ | ❌ 别名不支持 | P2 |
| IS NULL | NULL测试 | ✅ IS_NULL | ✅ IS_NULL | 已支持 | - |
| IS NOT NULL | 非NULL测试 | ✅ IS_NOT_NULL | ✅ IS_NOT_NULL | 已支持 | - |
| **IS TRUE** | TRUE测试 | ✅ IS_TRUE | ❌ **缺失** | P1 |
| **IS FALSE** | FALSE测试 | ✅ IS_FALSE | ❌ **缺失** | P1 |
| IS NOT TRUE | 非TRUE测试 | ✅ (negate) | ❌ 缺失 | P2 |
| IS NOT FALSE | 非FALSE测试 | ✅ (negate) | ❌ 缺失 | P2 |

**覆盖率**: 3/8 = **38%**

### 1.6 特殊操作符

| 操作符 | MySQL语法 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|--------|----------|-----------|---------------|------|----------|
| BETWEEN | 范围比较 | ✅ MySQLBetweenOperation | ✅ GaussDBBetweenOperation | 已支持 | - |
| IN | 成员测试 | ✅ MySQLInOperation | ❌ **缺失** | **必须扩展** | P0 |
| EXISTS | 存在测试 | ✅ MySQLExists | ❌ **缺失** | **必须扩展** | P0 |
| NOT IN | 非成员测试 | ✅ | ❌ 缺失 | P0 (随IN) |
| NOT EXISTS | 不存在测试 | ✅ | ❌ 缺失 | P0 (随EXISTS) |
| REGEXP/RLIKE | 正则匹配 | ✅ | ❌ **缺失** | P2 |
| SOUNDS LIKE | 音似比较 | ✅ SOUNDEX | ❌ 缺失 | P2 |

**覆盖率**: 1/6 = **17%**

### 1.7 操作符总体覆盖

| 类别 | MySQL数量 | GaussDB-M数量 | 覆盖率 |
|------|-----------|---------------|--------|
| 比较操作符 | 8 | 6 | 75% |
| 逻辑操作符 | 3 | 2 | 67% |
| 算术操作符 | 8 | 0 | **0%** |
| 位操作符 | 6 | 0 | **0%** |
| 一元操作符 | 8 | 3 | 38% |
| 特殊操作符 | 6 | 1 | **17%** |
| **总计** | **33** | **12** | **36%** |

---

## 2. 表达式覆盖评估

### 2.1 基础表达式

| 表达式类型 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|------------|-----------|---------------|------|----------|
| 列引用 | ✅ MySQLColumnReference | ✅ GaussDBColumnReference | 已支持 | - |
| 常量 | ✅ MySQLConstant | ✅ GaussDBConstant | 已支持 | 需扩展类型 |
| 文本片段 | ✅ MySQLText | ✅ GaussDBText | 已支持 | - |
| 别名表达式 | ✅ MySQLPostfixText | ✅ GaussDBPostfixText | 已支持 | - |
| 手动SQL片段 | ✅ MySQLManuelPredicate | ✅ GaussDBManuelPredicate | 已支持 | - |

### 2.2 复合表达式

| 表达式类型 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|------------|-----------|---------------|------|----------|
| 二元比较 | ✅ MySQLBinaryComparisonOperation | ✅ GaussDBBinaryComparisonOperation | 已支持 | 需补LIKE/<=> |
| 二元逻辑 | ✅ MySQLBinaryLogicalOperation | ✅ GaussDBBinaryLogicalOperation | 已支持 | 需补XOR |
| 二元算术 | ✅ MySQLBinaryArithmeticOperation | ❌ **缺失** | **必须扩展** | P0 |
| 二元位运算 | ✅ MySQLBinaryOperation | ❌ **缺失** | P1 |
| BETWEEN | ✅ MySQLBetweenOperation | ✅ GaussDBBetweenOperation | 已支持 | - |
| IN操作 | ✅ MySQLInOperation | ❌ **缺失** | **必须扩展** | P0 |
| EXISTS | ✅ MySQLExists | ❌ **缺失** | **必须扩展** | P0 |
| 一元前缀 | ✅ MySQLUnaryPrefixOperation | ✅ GaussDBUnaryPrefixOperation | 已支持 | 需补PLUS/MINUS |
| 一元后缀 | ✅ MySQLUnaryPostfixOperation | ✅ GaussDBUnaryPostfixOperation | 已支持 | 需补IS_TRUE/IS_FALSE |

### 2.3 高级表达式

| 表达式类型 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|------------|-----------|---------------|------|----------|
| CASE WHEN | ✅ MySQLCaseOperator | ✅ GaussDBCaseWhen (AST) | AST已有但未集成到Generator | **P0集成** |
| CAST | ✅ MySQLCastOperation | ❌ **缺失** | **必须扩展** | P1 |
| COLLATE | ✅ MySQLCollate | ❌ **缺失** | P2 |
| 聚合函数 | ✅ MySQLAggregate | ✅ GaussDBAggregate | 已支持 | 需补AVG等 |
| 窗口函数 | ✅ MySQLWindowFunction | ❌ **缺失** | **必须扩展** | P1 |
| 计算函数 | ✅ MySQLComputableFunction | ❌ **缺失** | **必须扩展** | P1 |
| 时间函数 | ✅ MySQLTemporalFunction | ❌ **缺失** | **必须扩展** | P1 |

### 2.4 查询结构表达式

| 表达式类型 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|------------|-----------|---------------|------|----------|
| SELECT | ✅ MySQLSelect | ✅ GaussDBSelect | 已支持 | - |
| JOIN | ✅ MySQLJoin | ✅ GaussDBJoin | 已支持 | 需补NATURAL |
| UNION | ✅ MySQLUnionSelect | ✅ GaussDBUnionSelect | 已支持 | - |
| WITH (CTE) | ✅ MySQLWithSelect | ✅ GaussDBWithSelect | 已支持 | - |
| 派生表 | ✅ MySQLDerivedTable | ✅ GaussDBDerivedTable | 已支持 | - |
| 子查询引用 | ✅ MySQLCteTableReference | ✅ GaussDBCteTableReference | 已支持 | - |

### 2.5 表达式总体覆盖

| 类别 | MySQL数量 | GaussDB-M数量 | 覆盖率 |
|------|-----------|---------------|--------|
| 基础表达式 | 5 | 5 | 100% |
| 复合表达式 | 10 | 5 | **50%** |
| 高级表达式 | 7 | 1 | **14%** |
| 查询结构 | 6 | 6 | 100% |
| **总计** | **28** | **17** | **61%** |

---

## 3. 函数覆盖评估

### 3.1 聚合函数

| 函数 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|------|-----------|---------------|------|----------|
| COUNT | ✅ | ✅ COUNT | 已支持 | - |
| COUNT DISTINCT | ✅ | ✅ COUNT_DISTINCT | 已支持 | - |
| SUM | ✅ | ✅ SUM | 已支持 | - |
| SUM DISTINCT | ✅ | ✅ SUM_DISTINCT | 已支持 | - |
| MIN | ✅ | ✅ MIN | 已支持 | - |
| MIN DISTINCT | ✅ | ✅ MIN_DISTINCT | 已支持 | - |
| MAX | ✅ | ✅ MAX | 已支持 | - |
| MAX DISTINCT | ✅ | ✅ MAX_DISTINCT | 已支持 | - |
| **AVG** | ✅ | ❌ **缺失** | **必须扩展** | P0 |
| **AVG DISTINCT** | ✅ | ❌ 缺失 | P0 |
| BIT_AND | ✅ | ❌ 缺失 | P1 |
| BIT_OR | ✅ | ❌ 缺失 | P1 |
| BIT_XOR | ✅ | ❌ 缺失 | P1 |
| STD/STDDEV | ✅ | ❌ 缺失 | P1 |
| STDDEV_POP | ✅ | ❌ 缺失 | P1 |
| STDDEV_SAMP | ✅ | ❌ 缺失 | P1 |
| VAR_POP | ✅ | ❌ 缺失 | P1 |
| VAR_SAMP | ✅ | ❌ 缺失 | P1 |
| VARIANCE | ✅ | ❌ 缺失 | P1 |
| GROUP_CONCAT | ✅ | ❌ 缺失 | P2 |

**覆盖率**: 8/20 = **40%**

### 3.2 控制流函数

| 函数 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|------|-----------|---------------|------|----------|
| IF | ✅ | ✅ GaussDBIfFunction | 已支持 | - |
| IFNULL | ✅ MySQLComputableFunction | ❌ 缺失 | P1 |
| NULLIF | ✅ | ❌ 缺失 | P1 |
| COALESCE | ✅ | ❌ 缺失 | P1 |
| CASE | ✅ MySQLCaseOperator | ✅ GaussDBCaseWhen (AST) | AST已有 | P0集成 |

**覆盖率**: 2/5 = **40%**

### 3.3 数学函数

| 函数 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|------|-----------|---------------|------|----------|
| ABS | ✅ | ❌ 缺失 | P1 |
| CEIL/CEILING | ✅ | ❌ 缺失 | P1 |
| FLOOR | ✅ | ❌ 缺失 | P1 |
| ROUND | ✅ | ❌ 缺失 | P1 |
| TRUNCATE | ✅ | ❌ 缺失 | P2 |
| MOD | ✅ | ❌ 缺失 | P1 |
| SIGN | ✅ | ❌ 缺失 | P1 |
| SQRT | ✅ | ❌ 缺失 | P2 |
| POWER/POW | ✅ | ❌ 缺失 | P2 |
| EXP | ✅ | ❌ 缺失 | P2 |
| LOG | ✅ | ❌ 缺失 | P2 |
| LOG10 | ✅ | ❌ 缺失 | P2 |
| LN | ✅ | ❌ 缺失 | P2 |
| RAND | ✅ | ❌ 缺失 | P1 |
| CRC32 | ✅ | ❌ 缺失 | P2 |
| DEGREES | ✅ | ❌ 缺失 | P2 |
| RADIANS | ✅ | ❌ 缺失 | P2 |
| SIN/COS/TAN | ✅ | ❌ 缺失 | P2 |
| ASIN/ACOS/ATAN | ✅ | ❌ 缺失 | P2 |
| ATAN2 | ✅ | ❌ 缺失 | P2 |
| CONV | ✅ | ❌ 缺失 | P2 |
| PI | ✅ | ❌ 缺失 | P2 |
| LEAST | ✅ | ❌ 缺失 | P1 |
| GREATEST | ✅ | ❌ 缺失 | P1 |
| BIT_COUNT | ✅ | ❌ 缺失 | P1 |

**覆盖率**: 0/25 = **0%** (完全缺失)

### 3.4 字符串函数

| 函数 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|------|-----------|---------------|------|----------|
| CONCAT | ✅ | ❌ 缺失 | P1 |
| CONCAT_WS | ✅ | ❌ 缺失 | P1 |
| LENGTH | ✅ | ❌ 缺失 | P1 |
| CHAR_LENGTH | ✅ | ❌ 缺失 | P1 |
| UPPER | ✅ | ❌ 缺失 | P1 |
| LOWER | ✅ | ❌ 缺失 | P1 |
| TRIM | ✅ | ❌ 缺失 | P1 |
| LTRIM | ✅ | ❌ 缺失 | P1 |
| RTRIM | ✅ | ❌ 缺失 | P1 |
| LEFT | ✅ | ❌ 缺失 | P1 |
| RIGHT | ✅ | ❌ 缺失 | P1 |
| SUBSTRING/SUBSTR | ✅ | ❌ 缺失 | P1 |
| MID | ✅ | ❌ 缺失 | P1 |
| REPLACE | ✅ | ❌ 缺失 | P1 |
| LOCATE | ✅ | ❌ 缺失 | P1 |
| INSTR | ✅ | ❌ 缺失 | P1 |
| LPAD | ✅ | ❌ 缺失 | P1 |
| RPAD | ✅ | ❌ 缺失 | P1 |
| REVERSE | ✅ | ❌ 缺失 | P1 |
| REPEAT | ✅ | ❌ 缺失 | P1 |
| SPACE | ✅ | ❌ 缺失 | P1 |
| ASCII | ✅ | ❌ 缺失 | P1 |
| ORD | ✅ | ❌ 缺失 | P2 |
| HEX | ✅ | ❌ 缺失 | P1 |
| UNHEX | ✅ | ❌ 缺失 | P1 |
| BIN | ✅ | ❌ 缺失 | P1 |
| OCT | ✅ | ❌ 缺失 | P1 |
| FORMAT | ✅ | ❌ 缺失 | P2 |
| ELT | ✅ | ❌ 缺失 | P2 |
| FIELD | ✅ | ❌ 缺失 | P2 |
| MAKE_SET | ✅ | ❌ 缺失 | P2 |
| EXPORT_SET | ✅ | ❌ 缺失 | P2 |
| SUBSTRING_INDEX | ✅ | ❌ 缺失 | P2 |
| INSERT | ✅ | ❌ 缺失 | P2 |
| LIKE | ✅ | ❌ 缺失 | P2 |
| SOUNDEX | ✅ | ❌ 缺失 | P2 |

**覆盖率**: 0/35 = **0%** (完全缺失)

### 3.5 时间日期函数

| 函数 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|------|-----------|---------------|------|----------|
| NOW | ✅ | ❌ 缺失 | P1 |
| CURDATE | ✅ | ❌ 缺失 | P1 |
| CURTIME | ✅ | ❌ 缺失 | P1 |
| DATE | ✅ | ❌ 缺失 | P1 |
| TIME | ✅ | ❌ 缺失 | P1 |
| YEAR | ✅ | ❌ 缺失 | P1 |
| MONTH | ✅ | ❌ 缺失 | P1 |
| DAY/DAYOFMONTH | ✅ | ❌ 缺失 | P1 |
| DAYOFWEEK | ✅ | ❌ 缺失 | P1 |
| DAYOFYEAR | ✅ | ❌ 缺失 | P1 |
| WEEK | ✅ | ❌ 缺失 | P1 |
| QUARTER | ✅ | ❌ 缺失 | P1 |
| HOUR | ✅ | ❌ 缺失 | P1 |
| MINUTE | ✅ | ❌ 缺失 | P1 |
| SECOND | ✅ | ❌ 缺失 | P1 |
| DATEDIFF | ✅ | ❌ 缺失 | P1 |
| TIMEDIFF | ✅ | ❌ 缺失 | P1 |
| DATE_ADD | ✅ | ❌ 缺失 | P1 |
| DATE_SUB | ✅ | ❌ 缺失 | P1 |
| ADDDATE | ✅ | ❌ 缺失 | P1 |
| SUBDATE | ✅ | ❌ 缺失 | P1 |
| LAST_DAY | ✅ | ❌ 缺失 | P1 |
| TO_DAYS | ✅ | ❌ 缺失 | P1 |
| FROM_DAYS | ✅ | ❌ 缺失 | P1 |
| UNIX_TIMESTAMP | ✅ | ❌ 缺失 | P1 |
| FROM_UNIXTIME | ✅ | ❌ 缺失 | P1 |
| STR_TO_DATE | ✅ | ❌ 缺失 | P2 |
| DATE_FORMAT | ✅ | ❌ 缺失 | P2 |
| TIME_FORMAT | ✅ | ❌ 缺失 | P2 |
| EXTRACT | ✅ | ❌ 缺失 | P1 |
| TIMESTAMP | ✅ | ❌ 缺失 | P1 |
| TIMESTAMPADD | ✅ | ❌ 缺失 | P2 |
| TIMESTAMPDIFF | ✅ | ❌ 缺失 | P2 |
| GET_FORMAT | ✅ | ❌ 缺失 | P2 |
| PERIOD_ADD | ✅ | ❌ 缺失 | P2 |
| PERIOD_DIFF | ✅ | ❌ 缺失 | P2 |
| MONTHNAME | ✅ | ❌ 缺失 | P2 |
| DAYNAME | ✅ | ❌ 缺失 | P2 |

**覆盖率**: 0/38 = **0%** (完全缺失)

### 3.6 JSON函数

| 函数 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|------|-----------|---------------|------|----------|
| JSON_TYPE | ✅ | ❌ 缺失 | P2 |
| JSON_VALID | ✅ | ❌ 缺失 | P2 |
| JSON_EXTRACT | ✅ | ❌ 缺失 | P2 |
| JSON_ARRAY | ✅ | ❌ 缺失 | P2 |
| JSON_OBJECT | ✅ | ❌ 缺失 | P2 |
| JSON_REMOVE | ✅ | ❌ 缺失 | P2 |
| JSON_CONTAINS | ✅ | ❌ 缺失 | P2 |
| JSON_KEYS | ✅ | ❌ 缺失 | P2 |
| JSON_MERGE | ✅ | ❌ 缺失 | P2 |
| JSON_MERGE_PRESERVE | ✅ | ❌ 缺失 | P2 |
| JSON_MERGE_PATCH | ✅ | ❌ 缺失 | P2 |
| JSON_SET | ✅ | ❌ 缺失 | P2 |
| JSON_INSERT | ✅ | ❌ 缺失 | P2 |
| JSON_REPLACE | ✅ | ❌ 缺失 | P2 |
| JSON_UNQUOTE | ✅ | ❌ 缺失 | P2 |
| JSON_QUOTE | ✅ | ❌ 缺失 | P2 |
| JSON_SEARCH | ✅ | ❌ 缺失 | P2 |
| JSON_VALUE | ✅ | ❌ 缺失 | P2 |
| JSON_ARRAY_APPEND | ✅ | ❌ 缺失 | P2 |
| JSON_ARRAY_INSERT | ✅ | ❌ 缺失 | P2 |

**覆盖率**: 0/20 = **0%** (完全缺失)

### 3.7 窗口函数

| 函数 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|------|-----------|---------------|------|----------|
| ROW_NUMBER | ✅ | ❌ 缺失 | **必须扩展** | P1 |
| RANK | ✅ | ❌ 缺失 | P1 |
| DENSE_RANK | ✅ | ❌ 缺失 | P1 |
| CUME_DIST | ✅ | ❌ 缺失 | P1 |
| PERCENT_RANK | ✅ | ❌ 缺失 | P1 |
| NTILE | ✅ | ❌ 缺失 | P1 |
| LAG | ✅ | ❌ 缺失 | P1 |
| LEAD | ✅ | ❌ 缺失 | P1 |
| FIRST_VALUE | ✅ | ❌ 缺失 | P1 |
| LAST_VALUE | ✅ | ❌ 缺失 | P1 |
| NTH_VALUE | ✅ | ❌ 缺失 | P1 |
| 聚合窗口(SUM/AVG OVER) | ✅ | ❌ 缺失 | P1 |

**覆盖率**: 0/12 = **0%** (完全缺失)

### 3.8 其他函数

| 函数类别 | MySQL支持 | GaussDB-M支持 | 状态 |
|----------|-----------|---------------|------|
| 信息函数(DATABASE/USER等) | ✅ | ❌ 缺失 | P2 |
| 加密函数(MD5/SHA等) | ✅ | ❌ 缺失 | P2 |
| 类型转换函数 | ✅ | ❌ 缺失 | P1 |
| 流程控制函数 | ✅ | 部分(IF) | 需补COALESCE |

### 3.9 函数总体覆盖

| 类别 | MySQL数量 | GaussDB-M数量 | 覆盖率 |
|------|-----------|---------------|--------|
| 聚合函数 | 20 | 8 | 40% |
| 控制流函数 | 5 | 2 | 40% |
| 数学函数 | 25 | 0 | **0%** |
| 字符串函数 | 35 | 0 | **0%** |
| 时间函数 | 38 | 0 | **0%** |
| JSON函数 | 20 | 0 | **0%** |
| 窗口函数 | 12 | 0 | **0%** |
| 其他函数 | 15 | 0 | **0%** |
| **总计** | **170** | **10** | **6%** |

---

## 4. 数据类型覆盖评估

### 4.1 数值类型

| 数据类型 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|----------|-----------|---------------|------|----------|
| INT | ✅ MySQLIntConstant | ✅ GaussDBIntConstant | 已支持 | - |
| INTEGER | ✅ | ✅ | 已支持 | - |
| TINYINT | ✅ | ✅ (作为INT) | 基本支持 | P2细化 |
| SMALLINT | ✅ | ✅ (作为INT) | 基本支持 | P2细化 |
| MEDIUMINT | ✅ | ✅ (作为INT) | 基本支持 | P2细化 |
| BIGINT | ✅ | ✅ (作为INT) | 基本支持 | P2细化 |
| FLOAT | ✅ | ✅ GaussDBDataType.FLOAT | Schema支持 | 需AST常量 |
| DOUBLE | ✅ MySQLDoubleConstant | ✅ GaussDBDataType.DOUBLE | Schema支持 | 需AST常量 |
| DECIMAL | ✅ | ✅ GaussDBDataType.DECIMAL | Schema支持 | 需AST常量 |
| UNSIGNED | ✅ | ❌ 缺失 | P1 |
| **BIT** | ✅ MySQLBitConstant | ❌ **缺失** | **需要扩展** | P1 |

**覆盖率**: 9/11 = **82%** (Schema层面)

### 4.2 字符串类型

| 数据类型 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|----------|-----------|---------------|------|----------|
| CHAR | ✅ | ✅ VARCHAR覆盖 | 基本支持 | P2细化 |
| VARCHAR | ✅ MySQLTextConstant | ✅ GaussDBStringConstant | 已支持 | - |
| TEXT | ✅ | ✅ VARCHAR覆盖 | 基本支持 | P2细化 |
| TINYTEXT | ✅ | ✅ VARCHAR覆盖 | 基本支持 | - |
| MEDIUMTEXT | ✅ | ✅ VARCHAR覆盖 | 基本支持 | - |
| LONGTEXT | ✅ | ✅ VARCHAR覆盖 | 基本支持 | - |
| ENUM | ✅ MySQLEnumConstant | ❌ **缺失** | P2 |
| SET | ✅ MySQLSetConstant | ❌ **缺失** | P2 |
| **BINARY** | ✅ MySQLBinaryConstant | ❌ **缺失** | P1 |
| VARBINARY | ✅ MySQLBinaryConstant | ❌ **缺失** | P1 |
| BLOB | ✅ | ❌ 缺失 | P2 |

**覆盖率**: 6/11 = **55%**

### 4.3 时间类型

| 数据类型 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|----------|-----------|---------------|------|----------|
| DATE | ✅ MySQLDateConstant | ✅ GaussDBDataType.DATE | Schema+常量支持 | - |
| TIME | ✅ MySQLTimeConstant | ✅ GaussDBDataType.TIME | Schema+常量支持 | - |
| DATETIME | ✅ MySQLDateTimeConstant | ✅ GaussDBDataType.DATETIME | Schema+常量支持 | - |
| TIMESTAMP | ✅ MySQLTimestampConstant | ✅ GaussDBDataType.TIMESTAMP | Schema+常量支持 | - |
| YEAR | ✅ MySQLYearConstant | ✅ GaussDBDataType.YEAR | Schema+常量支持 | - |

**覆盖率**: 5/5 = **100%** ✅

### 4.4 高级类型

| 数据类型 | MySQL支持 | GaussDB-M支持 | 状态 | 扩展建议 |
|----------|-----------|---------------|------|----------|
| **JSON** | ✅ MySQLJSONConstant | ❌ **缺失** | **需要扩展** | P1 |
| GEOMETRY | ✅ | ❌ 缺失 | P2 |
| POINT | ✅ | ❌ 缺失 | P2 |
| LINESTRING | ✅ | ❌ 缺失 | P2 |
| POLYGON | ✅ | ❌ 缺失 | P2 |
| MULTIPOINT | ✅ | ❌ 缺失 | P2 |
| MULTILINESTRING | ✅ | ❌ 缺失 | P2 |
| MULTIPOLYGON | ✅ | ❌ 缺失 | P2 |
| GEOMETRYCOLLECTION | ✅ | ❌ 缺失 | P2 |
| **NULL** | ✅ MySQLNullConstant | ✅ GaussDBNullConstant | 已支持 | - |
| INTERVAL | ✅ MySQLIntervalConstant | ❌ 缺失 | P2 |

**覆盖率**: 1/11 = **9%**

### 4.5 数据类型总体覆盖

| 类别 | MySQL数量 | GaussDB-M数量 | 覆盖率 |
|------|-----------|---------------|--------|
| 数值类型 | 11 | 9 | 82% |
| 字符串类型 | 11 | 6 | 55% |
| 时间类型 | 5 | 5 | **100%** |
| 高级类型 | 11 | 1 | **9%** |
| **总计** | **38** | **21** | **55%** |

---

## 5. 扩展优先级与工作量

### 5.1 P0 - 必须扩展（影响核心功能）

| 序号 | 功能 | 类别 | 工作量 | 影响 |
|------|------|------|--------|------|
| 1 | 算术操作符(+,-,*,/,%) | 操作符 | 0.5天 | 基础运算测试 |
| 2 | 一元算术(-,+负号正号) | 操作符 | 0.25天 | 基础运算测试 |
| 3 | IN操作符 | 操作符 | 0.5天 | 成员测试 |
| 4 | EXISTS操作符 | 操作符 | 0.5天 | 存在测试 |
| 5 | LIKE比较操作符 | 操作符 | 0.25天 | 模式匹配 |
| 6 | CASE WHEN集成到Generator | 表达式 | 0.25天 | 条件表达式 |
| 7 | generateFetchColumnExpression | SonarOracle | 1天 | 核心功能 |
| 8 | generateWhereColumnExpression | SonarOracle | 0.5天 | 核心功能 |
| 9 | AVG聚合函数 | 函数 | 0.25天 | 基础聚合 |

**P0合计**: 3.5天

### 5.2 P1 - 重要扩展（提升覆盖率50%）

| 序号 | 功能 | 类别 | 工作量 | 影响 |
|------|------|------|--------|------|
| 1 | 窗口函数(ROW_NUMBER等12种) | 函数 | 2天 | 窗口函数测试 |
| 2 | 数学函数基础集(ABS/CEIL/FLOOR/ROUND/MOD/SIGN等) | 函数 | 1天 | 数学运算测试 |
| 3 | 字符串函数基础集(CONCAT/LENGTH/UPPER/LOWER/TRIM等) | 函数 | 1天 | 字符处理测试 |
| 4 | 时间函数基础集(NOW/CURDATE/YEAR/MONTH/DAY等) | 函数 | 1天 | 时间处理测试 |
| 5 | CAST操作符 | 操作符 | 0.5天 | 类型转换 |
| 6 | <=> NULL安全相等 | 操作符 | 0.25天 | NULL处理 |
| 7 | XOR逻辑操作符 | 操作符 | 0.25天 | 逻辑运算 |
| 8 | IS TRUE/IS FALSE后缀操作 | 操作符 | 0.25天 | 布尔测试 |
| 9 | 位操作符(&,\|,^) | 操作符 | 0.5天 | 位运算测试 |
| 10 | BIT/BINARY数据类型 | 数据类型 | 0.5天 | 二进制数据 |
| 11 | JSON数据类型 | 数据类型 | 0.5天 | JSON数据 |
| 12 | COALESCE/IFNULL函数 | 函数 | 0.25天 | NULL处理 |
| 13 | NATURAL JOIN | JOIN | 0.25天 | JOIN测试 |

**P1合计**: 8天

### 5.3 P2 - 可选扩展（高级特性）

| 序号 | 功能 | 类别 | 工作量 | 影响 |
|------|------|------|--------|------|
| 1 | JSON函数(20种) | 函数 | 1天 | JSON处理 |
| 2 | 统计函数(STDDEV/VARIANCE等) | 函数 | 0.5天 | 统计测试 |
| 3 | 复杂字符串函数(SUBSTRING_INDEX等) | 函数 | 0.5天 | 字符处理 |
| 4 | 复杂时间函数(DATE_FORMAT等) | 函数 | 0.5天 | 时间格式化 |
| 5 | REGEXP/RLIKE操作符 | 操作符 | 0.25天 | 正则匹配 |
| 6 | 位移操作符(<<,>>) | 操作符 | 0.25天 | 位运算 |
| 7 | ENUM/SET数据类型 | 数据类型 | 0.5天 | 特殊类型 |
| 8 | 几何类型(GEOMETRY等) | 数据类型 | 1天 | GIS测试 |
| 9 | 函数别名(&&,\|\|等) | 操作符 | 0.25天 | 语法兼容 |
| 10 | GaussDB特有函数调研扩展 | 函数 | 1天 | 特有特性 |

**P2合计**: 5.25天

### 5.4 总工作量

| 优先级 | 工作量 | 覆盖率提升 |
|--------|--------|------------|
| P0 | 3.5天 | 36% → 50% |
| P1 | 8天 | 50% → 80% |
| P2 | 5.25天 | 80% → 95% |
| **总计** | **16.75天** | **95%覆盖** |

---

## 6. 扩展实施方案

### 6.1 操作符扩展方案

```java
// 1. GaussDBBinaryComparisonOperation.java 添加
LIKE("LIKE") {
    @Override
    public GaussDBConstant getExpectedValue(GaussDBConstant l, GaussDBConstant r) {
        // 实现LIKE模式匹配
    }
},
IS_EQUALS_NULL_SAFE("<=>") {
    @Override
    public GaussDBConstant getExpectedValue(GaussDBConstant l, GaussDBConstant r) {
        // NULL-safe相等: NULL <=> NULL = TRUE
    }
}

// 2. GaussDBBinaryLogicalOperation.java 添加
XOR("XOR") {
    @Override
    public GaussDBConstant apply(GaussDBConstant left, GaussDBConstant right) {
        if (left.isNull() || right.isNull()) {
            return GaussDBConstant.createNullConstant();
        }
        boolean xorVal = left.asBooleanNotNull() ^ right.asBooleanNotNull();
        return GaussDBConstant.createBooleanConstant(xorVal);
    }
}

// 3. 新建 GaussDBBinaryArithmeticOperation.java
public enum GaussDBArithmeticOperator {
    ADD("+"), SUB("-"), MUL("*"), DIV("/"), MOD("%");
}

// 4. 新建 GaussDBInOperation.java
public class GaussDBInOperation implements GaussDBExpression {
    private final GaussDBExpression expr;
    private final List<GaussDBExpression> listElements;
    private final boolean isTrue; // NOT IN时为false
}

// 5. 新建 GaussDBExists.java
public class GaussDBExists implements GaussDBExpression {
    private final GaussDBExpression expr;
}
```

### 6.2 函数扩展方案

```java
// 1. GaussDBComputableFunction.java (新建)
public class GaussDBComputableFunction implements GaussDBExpression {
    private final GaussDBFunction func;
    private final GaussDBExpression[] args;
    
    public enum GaussDBFunction {
        // 数学函数
        ABS("ABS", 1), CEIL("CEIL", 1), FLOOR("FLOOR", 1),
        ROUND("ROUND", 1), MOD("MOD", 2), SIGN("SIGN", 1),
        // 字符串函数
        CONCAT("CONCAT", true), LENGTH("LENGTH", 1),
        UPPER("UPPER", 1), LOWER("LOWER", 1), TRIM("TRIM", 1),
        // 控制流
        COALESCE("COALESCE", true), IFNULL("IFNULL", 2);
    }
}

// 2. GaussDBTemporalFunction.java (新建)
public class GaussDBTemporalFunction implements GaussDBExpression {
    public enum TemporalFunctionKind {
        NOW, CURDATE, CURTIME, YEAR, MONTH, DAY, HOUR, MINUTE, SECOND;
    }
}

// 3. GaussDBWindowFunction.java (新建)
public class GaussDBWindowFunction implements GaussDBExpression {
    private final GaussDBFunction func;
    private final GaussDBExpression expr;
    private final GaussDBExpression partitionBy;
    
    public enum GaussDBFunction {
        ROW_NUMBER, RANK, DENSE_RANK, LAG, LEAD,
        SUM, AVG, COUNT, MIN, MAX;
    }
}
```

### 6.3 ExpressionGenerator扩展

```java
// GaussDBMExpressionGenerator.java 扩展Actions枚举
private enum Actions {
    COLUMN, LITERAL,
    BINARY_LOGICAL_OPERATOR,
    BINARY_COMPARISON_OPERATION,
    ARITHMETIC_OPERATION,       // 新增
    BINARY_OPERATION,           // 新增(位运算)
    COMPUTABLE_FUNCTION,        // 新增
    TEMPORAL_FUNCTION,          // 新增
    CAST,                       // 新增
    IN_OPERATION,               // 新增
    EXISTS,                     // 新增
    CASE_OPERATOR,              // 新增
    BETWEEN_OPERATOR,
    UNARY_PREFIX_OPERATION,
    UNARY_POSTFIX_OPERATION,
    WINDOW_FUNCTION             // 新增
}
```

---

## 7. 总结

### 7.1 当前覆盖率总览

| 维度 | MySQL数量 | GaussDB-M数量 | 当前覆盖率 |
|------|-----------|---------------|------------|
| 操作符 | 33 | 12 | **36%** |
| 表达式 | 28 | 17 | **61%** |
| 函数 | 170 | 10 | **6%** |
| 数据类型 | 38 | 21 | **55%** |
| **综合** | **269** | **60** | **22%** |

### 7.2 关键差距

**完全缺失的类别**：
- 算术操作符 (0%)
- 位操作符 (0%)
- 数学函数 (0%)
- 字符串函数 (0%)
- 时间函数 (0%)
- JSON函数 (0%)
- 窗口函数 (0%)

**主要缺失项**：
- IN/EXISTS操作符
- LIKE比较操作符
- CAST类型转换
- JSON/BIT数据类型
- generateFetchColumnExpression方法
- generateWhereColumnExpression方法

### 7.3 扩展后预期覆盖率

| 优先级 | 完成后覆盖率 | 工作量 |
|--------|--------------|--------|
| P0完成后 | **50%** | 3.5天 |
| P1完成后 | **80%** | 11.5天 |
| P2完成后 | **95%** | 16.75天 |

---

*评估日期: 2026-04-28*
*评估依据: MySQL AST/GaussDB-M AST代码对比分析*
*评估工具: Claude Code*