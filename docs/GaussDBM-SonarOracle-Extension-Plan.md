# GaussDB-M SonarOracle 功能扩展评估报告

## 概述

本报告基于 GaussDB-M MySQL兼容模式的语法支持范围，评估 SonarOracle 需要扩展的功能特性，包括基础功能、表达式、函数、索引等。

---

## 1. GaussDB-M 语法支持现状分析

### 1.1 数据类型支持

GaussDB-M 已支持的数据类型（从 GaussDBMSchema 分析）：

| 数据类型 | 状态 | 说明 |
|----------|------|------|
| INT | ✅ 支持 | 整数类型 |
| VARCHAR | ✅ 支持 | 字符串类型 |
| FLOAT | ✅ 支持 | 单精度浮点 |
| DOUBLE | ✅ 支持 | 双精度浮点 |
| DECIMAL | ✅ 支持 | 精确数值 |
| DATE | ✅ 支持 | 日期类型 |
| TIME | ✅ 支持 | 时间类型 |
| DATETIME | ✅ 支持 | 日期时间 |
| TIMESTAMP | ✅ 支持 | 时间戳 |
| YEAR | ✅ 支持 | 年类型 |

**未支持但MySQL有**：
| 数据类型 | MySQL支持 | GaussDB-M状态 |
|----------|-----------|---------------|
| JSON | ✅ | ❌ 未支持 |
| BIT | ✅ | ❌ 未支持 |
| BINARY/VARBINARY | ✅ | ❌ 未支持 |
| ENUM | ✅ | ❌ 未支持 |
| SET | ✅ | ❌ 未支持 |
| TEXT/BLOB | ✅ | ❌ 未支持 |

### 1.2 AST节点支持现状

**已实现的AST节点（25个）**：

| AST节点 | 文件 | 功能 |
|---------|------|------|
| GaussDBAggregate | ✅ | 聚合函数(COUNT/SUM/MIN/MAX) |
| GaussDBCaseWhen | ✅ | CASE WHEN表达式 |
| GaussDBColumnReference | ✅ | 列引用 |
| GaussDBBinaryLogicalOperation | ✅ | AND/OR逻辑运算 |
| GaussDBBetweenOperation | ✅ | BETWEEN操作 |
| GaussDBBinaryComparisonOperation | ✅ | 比较运算(=,<,>,<=,>=,!=,LIKE) |
| GaussDBConstant | ✅ | 常量(INT/STRING/NULL/BOOLEAN) |
| GaussDBIfFunction | ✅ | IF函数 |
| GaussDBJoin | ✅ | JOIN(INNER/LEFT/RIGHT/CROSS) |
| GaussDBManuelPredicate | ✅ | 手动SQL片段（SonarOracle） |
| GaussDBPostfixText | ✅ | 表达式别名（SonarOracle） |
| GaussDBSelect | ✅ | SELECT语句 |
| GaussDBTableReference | ✅ | 表引用 |
| GaussDBText | ✅ | 文本片段 |
| GaussDBUnaryPostfixOperation | ✅ | IS NULL |
| GaussDBUnaryPrefixOperation | ✅ | NOT |
| GaussDBWithSelect | ✅ | CTE(WITH语句) |
| GaussDBUnionSelect | ✅ | UNION查询 |
| GaussDBDerivedTable | ✅ | 派生表（子查询） |
| GaussDBCteTableReference | ✅ | CTE表引用 |
| GaussDBCteDefinition | ✅ | CTE定义 |
| GaussDBOracleAlias | ✅ | Oracle别名 |
| GaussDBPrintedExpression | ✅ | 打印表达式 |
| GaussDBOracleExpressionBag | ✅ | 表达式集合 |

### 1.3 ExpressionGenerator Actions枚举现状

**当前只有7种表达式类型**：

| Actions | 状态 | 功能 |
|---------|------|------|
| COLUMN | ✅ | 列引用 |
| LITERAL | ✅ | 常量生成 |
| BINARY_LOGICAL_OPERATOR | ✅ | AND/OR |
| BINARY_COMPARISON_OPERATION | ✅ | 比较运算 |
| BETWEEN_OPERATOR | ✅ | BETWEEN |
| UNARY_PREFIX_OPERATION | ✅ | NOT |
| UNARY_POSTFIX_OPERATION | ✅ | IS NULL |

---

## 2. GaussDB-M vs MySQL SonarOracle 对比

### 2.1 ExpressionGenerator 对比

| 表达式类型 | MySQL Actions | GaussDB-M Actions | 差距 |
|------------|--------------|-------------------|------|
| 列引用 | COLUMN ✅ | COLUMN ✅ | 无 |
| 常量 | LITERAL ✅ | LITERAL ✅ | 无 |
| 一元前缀 | UNARY_PREFIX ✅ | UNARY_PREFIX ✅ | 无 |
| 一元后缀 | UNARY_POSTFIX ✅ | UNARY_POSTFIX ✅ | 仅IS_NULL |
| 二元逻辑 | BINARY_LOGICAL ✅ | BINARY_LOGICAL ✅ | 无 |
| 二元比较 | BINARY_COMPARISON ✅ | BINARY_COMPARISON ✅ | 无 |
| BETWEEN | BETWEEN ✅ | BETWEEN ✅ | 无 |
| **算术运算** | ARITHMETIC ✅ | ❌ 缺失 | **需扩展** |
| **二元运算** | BINARY_OPERATION ✅ | ❌ 缺失 | **需扩展** |
| **计算函数** | COMPUTABLE_FUNCTION ✅ | ❌ 缺失 | **需扩展** |
| **时间函数** | TEMPORAL_FUNCTION ✅ | ❌ 缺失 | **需扩展** |
| **CAST** | CAST ✅ | ❌ 缺失 | **需扩展** |
| **IN操作** | IN_OPERATION ✅ | ❌ 缺失 | **需扩展** |
| **EXISTS** | EXISTS ✅ | ❌ 缺失 | **需扩展** |
| **CASE** | CASE_OPERATOR ✅ | AST已有但未用 | **需集成** |

### 2.2 函数支持对比

#### 聚合函数

| 函数 | MySQL | GaussDB-M | 状态 |
|------|-------|-----------|------|
| COUNT/COUNT_DISTINCT | ✅ | ✅ | 已支持 |
| SUM/SUM_DISTINCT | ✅ | ✅ | 已支持 |
| MIN/MIN_DISTINCT | ✅ | ✅ | 已支持 |
| MAX/MAX_DISTINCT | ✅ | ✅ | 已支持 |
| AVG | ✅ | ❌ | **需扩展** |
| BIT_AND/BIT_OR/BIT_XOR | ✅ | ❌ | **需扩展** |
| STDDEV/VARIANCE | ✅ | ❌ | **需扩展** |

#### 计算函数（MySQL有60+，GaussDB-M无）

| 函数类别 | MySQL | GaussDB-M | 需扩展 |
|----------|-------|-----------|--------|
| 控制流(IF/IFNULL/COALESCE) | ✅ | IF ✅ | COALESCE需扩展 |
| 数学函数(ABS/CEIL/FLOOR等) | ✅ 15+ | ❌ | **全部需扩展** |
| 字符串函数(CONCAT/LENGTH等) | ✅ 20+ | ❌ | **全部需扩展** |
| JSON函数 | ✅ 10+ | ❌ | **全部需扩展** |
| 时间函数 | ✅ 20+ | ❌ | **全部需扩展** |

#### 窗口函数（MySQL有20+，GaussDB-M无）

| 窗口函数 | MySQL | GaussDB-M |
|----------|-------|-----------|
| ROW_NUMBER/RANK/DENSE_RANK | ✅ | ❌ **需扩展** |
| LAG/LEAD | ✅ | ❌ **需扩展** |
| 聚合窗口(SUM/AVG/COUNT OVER) | ✅ | ❌ **需扩展** |

### 2.3 JOIN支持对比

| JOIN类型 | MySQL | GaussDB-M | 状态 |
|----------|-------|-----------|------|
| INNER JOIN | ✅ | ✅ | 已支持 |
| LEFT JOIN | ✅ | ✅ | 已支持 |
| RIGHT JOIN | ✅ | ✅ | 已支持 |
| CROSS JOIN | ✅ | ✅ | 已支持 |
| NATURAL JOIN | ✅ | ❌ | **需扩展** |
| FULL OUTER JOIN | ❌ | ❌ | MySQL不支持 |

### 2.4 SonarOracle专用方法对比

| 方法 | MySQL | GaussDB-M | 状态 |
|------|-------|-----------|------|
| generateFetchColumnExpression() | ✅ 6种 | ❌ 无 | **必须扩展** |
| generateWhereColumnExpression() | ✅ 3种 | ❌ 无 | **必须扩展** |
| generateWindowFuc() | ✅ | ❌ 无 | **必须扩展** |
| getRandomJoinClauses()扩展版 | ✅ NATURAL支持 | ❌ 仅基础 | **需扩展** |

---

## 3. GaussDB-M SonarOracle 功能扩展建议

### 3.1 P0 - 必须扩展（核心功能，直接影响检测能力）

| 序号 | 功能 | MySQL支持 | GaussDB-M现状 | 工作量 | 优先级 |
|------|------|----------|---------------|--------|--------|
| 1 | **generateFetchColumnExpression()** | ✅ 6种表达式类型 | ❌ 仅简单列引用 | 1天 | P0 |
| 2 | **generateWhereColumnExpression()** | ✅ 3种表达式类型 | ❌ 无 | 0.5天 | P0 |
| 3 | **算术运算(+,-,*,/,%)** | ✅ ARITHMETIC_OPERATION | ❌ 缺失 | 0.5天 | P0 |
| 4 | **IN操作** | ✅ IN_OPERATION | ❌ 缺失 | 0.5天 | P0 |
| 5 | **CASE WHEN表达式生成** | ✅ CASE_OPERATOR | AST已有但未集成到Generator | 0.5天 | P0 |

**P0合计工作量**: 约2.5天

### 3.2 P1 - 重要扩展（提升覆盖率50%以上）

| 序号 | 功能 | MySQL支持 | GaussDB-M现状 | 工作量 | 优先级 |
|------|------|----------|---------------|--------|--------|
| 6 | **窗口函数(ROW_NUMBER/RANK等)** | ✅ 20+种 | ❌ 无AST无Generator | 1.5天 | P1 |
| 7 | **计算函数基础集** | ✅ 60+ | ❌ 无 | 2天 | P1 |
| | - 数学函数(ABS/CEIL/FLOOR/ROUND/MOD/SIGN) | 15+ | | | |
| | - 字符串函数(CONCAT/LENGTH/UPPER/LOWER/TRIM) | 10+ | | | |
| | - 控制流(COALESCE/IFNULL扩展) | 3+ | | | |
| 8 | **时间函数(NOW/CURDATE/YEAR/MONTH等)** | ✅ 20+ | ❌ 无 | 1天 | P1 |
| 9 | **CAST类型转换** | ✅ CAST | ❌ 缺失 | 0.5天 | P1 |
| 10 | **EXISTS子查询** | ✅ EXISTS | ❌ 缺失 | 0.5天 | P1 |
| 11 | **NATURAL JOIN支持** | ✅ | ❌ 缺失 | 0.5天 | P1 |

**P1合计工作量**: 约5.5天

### 3.3 P2 - 可选扩展（高级特性，覆盖率提升20%）

| 序号 | 功能 | MySQL支持 | GaussDB-M现状 | 工作量 | 优先级 |
|------|------|----------|---------------|--------|--------|
| 12 | **JSON函数(JSON_EXTRACT/JSON_CONTAINS等)** | ✅ 10+ | ❌ 无 | 1天 | P2 |
| 13 | **位运算函数(BIT_AND/BIT_OR/BIT_XOR)** | ✅ | ❌ 无 | 0.5天 | P2 |
| 14 | **统计函数(STDDEV/VARIANCE)** | ✅ | ❌ 无 | 0.5天 | P2 |
| 15 | **IS TRUE/IS FALSE后缀操作扩展** | ✅ | 仅IS_NULL | 0.5天 | P2 |
| 16 | **GaussDB特有函数检测** | 待调研 | ❌ | 1天 | P2 |

**P2合计工作量**: 约3天

---

## 4. 详细扩展方案

### 4.1 generateFetchColumnExpression 实现

参考MySQL实现，添加6种FetchColumn表达式类型：

```java
// GaussDBMExpressionGenerator.java 添加
private enum ColumnExpressionActions {
    BINARY_COMPARISON_OPERATION,
    BINARY_OPERATION,  // 需先添加算术运算AST
    BINARY_ARITH,      // 需先添加算术运算AST
    COLUMN,
    COMPUTABLE_FUNCTION, // 需先添加计算函数AST
    AGGREGATE_FUNCTION   // 已有GaussDBAggregate
}

public GaussDBExpression generateFetchColumnExpression(GaussDBTables targetTables) {
    switch (Randomly.fromOptions(ColumnExpressionActions.values())) {
    case BINARY_COMPARISON_OPERATION:
        return new GaussDBBinaryComparisonOperation(
            GaussDBColumnReference.create(targetTables.getColumns().get(0), null),
            generateExpression(2),
            BinaryComparisonOperator.getRandom());
    case BINARY_ARITH:
        return new GaussDBBinaryArithmeticOperation(...); // 需新建AST
    case AGGREGATE_FUNCTION:
        return generateAggregate();
    case COLUMN:
        return generateColumn();
    // ...
    }
}
```

### 4.2 算术运算AST扩展

需新建 `GaussDBBinaryArithmeticOperation.java`：

```java
package sqlancer.gaussdbm.ast;

public class GaussDBBinaryArithmeticOperation implements GaussDBExpression {
    private final GaussDBExpression left;
    private final GaussDBExpression right;
    private final ArithmeticOperator op;

    public enum ArithmeticOperator {
        ADD("+"), SUB("-"), MUL("*"), DIV("/"), MOD("%");
    }
    // ...
}
```

### 4.3 窗口函数AST扩展

需新建 `GaussDBWindowFunction.java`：

```java
package sqlancer.gaussdbm.ast;

public class GaussDBWindowFunction implements GaussDBExpression {
    private final GaussDBWindowFunction.GaussDBFunction func;
    private final GaussDBExpression expr;
    private final GaussDBExpression partitionBy;

    public enum GaussDBFunction {
        ROW_NUMBER("ROW_NUMBER", 0),
        RANK("RANK", 0),
        DENSE_RANK("DENSE_RANK", 0),
        SUM("SUM", 1),
        AVG("AVG", 1),
        COUNT("COUNT", 1),
        // GaussDB-M特有窗口函数待调研补充
    }
}
```

### 4.4 计算函数AST扩展

需新建 `GaussDBComputableFunction.java`：

```java
package sqlancer.gaussdbm.ast;

public class GaussDBComputableFunction implements GaussDBExpression {
    private final GaussDBFunction func;
    private final GaussDBExpression[] args;

    public enum GaussDBFunction {
        // 数学函数
        ABS("ABS", 1),
        CEIL("CEIL", 1),
        FLOOR("FLOOR", 1),
        ROUND("ROUND", 1),
        MOD("MOD", 2),
        SIGN("SIGN", 1),
        // 字符串函数
        CONCAT("CONCAT", true), // variadic
        LENGTH("LENGTH", 1),
        UPPER("UPPER", 1),
        LOWER("LOWER", 1),
        TRIM("TRIM", 1),
        // 控制流扩展
        COALESCE("COALESCE", true),
        // GaussDB-M特有函数待调研
    }
}
```

### 4.5 Visitor扩展

`GaussDBToStringVisitor.java` 需添加：

```java
public void visit(GaussDBBinaryArithmeticOperation op) {
    sb.append("(");
    visit(op.getLeft());
    sb.append(" ");
    sb.append(op.getOp().getText());
    sb.append(" ");
    visit(op.getRight());
    sb.append(")");
}

public void visit(GaussDBWindowFunction window) {
    sb.append(window.getFunction().getName());
    sb.append("(");
    if (window.getExpr() != null) {
        visit(window.getExpr());
    }
    sb.append(") OVER (PARTITION BY ");
    visit(window.getPartitionBy());
    sb.append(")");
}

public void visit(GaussDBComputableFunction func) {
    sb.append(func.getFunction().getName());
    sb.append("(");
    for (int i = 0; i < func.getArguments().length; i++) {
        if (i != 0) sb.append(", ");
        visit(func.getArguments()[i]);
    }
    sb.append(")");
}
```

---

## 5. GaussDB-M 特有语法调研建议

### 5.1 需调研的GaussDB-M特有功能

| 功能类别 | 调研内容 | 测试价值 |
|----------|----------|----------|
| 分布式特性 | 分布式查询优化、分布式JOIN | 高 |
| 存储引擎 | 多存储引擎支持 | 中 |
| 兼容性差异 | 与MySQL不兼容的语法点 | 高 |
| 特有函数 | GaussDB-M特有的内置函数 | 高 |
| 性能优化 | 算子下推、索引优化特性 | 高 |
| SQL模式 | sql_mode兼容性差异 | 中 |

### 5.2 GaussDB-M兼容性限制

根据GaussDB官方文档，M兼容模式有以下限制：
1. 部分MySQL函数语法不完全兼容
2. 存储过程/触发器语法差异
3. JSON类型支持差异
4. 索引类型支持差异（如FULLTEXT）

**建议**: 创建专门的兼容性差异检测用例

---

## 6. Bug检测能力提升预估

### 6.1 当前检测能力（仅基础表达式）

| Bug类型 | 当前覆盖 | 检测能力 |
|---------|----------|----------|
| WHERE条件优化 | ✅ 基础 | 30% |
| JOIN优化 | ✅ 基础 | 40% |
| 窗口函数优化 | ❌ | 0% |
| 函数优化 | ❌ | 0% |
| CAST优化 | ❌ | 0% |
| IN/EXISTS优化 | ❌ | 0% |
| 算术运算优化 | ❌ | 0% |

**当前总体检测能力**: 约15-20%

### 6.2 P0扩展后检测能力

| Bug类型 | 扩展后覆盖 | 检测能力 |
|---------|------------|----------|
| WHERE条件优化 | ✅ 增强 | 50% |
| JOIN优化 | ✅ 增强 | 50% |
| 算术运算优化 | ✅ 新增 | 70% |
| IN操作优化 | ✅ 新增 | 70% |
| CASE WHEN优化 | ✅ 新增 | 60% |

**P0扩展后总体检测能力**: 约50%

### 6.3 P1扩展后检测能力

| Bug类型 | 扩展后覆盖 | 检测能力 |
|---------|------------|----------|
| 窗口函数优化 | ✅ 新增 | 80% |
| 函数优化 | ✅ 新增 | 70% |
| 时间函数优化 | ✅ 新增 | 60% |
| CAST优化 | ✅ 新增 | 70% |
| EXISTS优化 | ✅ 新增 | 70% |

**P1扩展后总体检测能力**: 约70-80%

---

## 7. 实施计划

### 7.1 阶段划分

| 阶段 | 功能范围 | 工作量 | 交付物 |
|------|----------|--------|--------|
| Phase 1 (P0) | 核心表达式扩展 | 2.5天 | 基础检测能力50% |
| Phase 2 (P1-A) | 窗口函数+聚合扩展 | 2天 | 窗口函数检测 |
| Phase 2 (P1-B) | 计算函数基础集 | 2天 | 函数优化检测 |
| Phase 2 (P1-C) | 时间函数+CAST+EXISTS | 2天 | 时间函数检测 |
| Phase 3 (P2) | 高级特性 | 3天 | 全面覆盖80% |

### 7.2 总工作量

| 阶段 | 工作量 |
|------|--------|
| P0 (必须) | 2.5天 |
| P1 (重要) | 5.5天 |
| P2 (可选) | 3天 |
| **总计** | **11天** |

---

## 8. 总结

### 8.1 当前差距

GaussDB-M SonarOracle 与 MySQL SonarOracle 相比：
- 表达式类型：7种 vs 13种（缺少6种）
- 函数支持：0个 vs 60+（完全缺失）
- 窗口函数：0个 vs 20+（完全缺失）
- FetchColumn表达式：无 vs 6种（必须扩展）
- WhereColumn表达式：无 vs 3种（必须扩展）

### 8.2 扩展优先级

1. **P0（2.5天）**: generateFetchColumnExpression、generateWhereColumnExpression、算术运算、IN操作、CASE WHEN
2. **P1（5.5天）**: 窗口函数、计算函数、时间函数、CAST、EXISTS、NATURAL JOIN
3. **P2（3天）**: JSON函数、位运算、统计函数、IS TRUE/FALSE扩展

### 8.3 预期收益

- P0完成后：检测能力从15%提升到50%
- P1完成后：检测能力从50%提升到70-80%
- P2完成后：检测能力达到80-90%

---

*评估日期: 2026-04-28*
*评估依据: GaussDB-M现有代码分析 + MySQL SonarOracle对比*
*评估工具: Claude Code*