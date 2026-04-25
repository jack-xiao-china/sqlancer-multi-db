# CODDTest 移植到 MySQL 的设计方案

> 设计日期: 2026-03-28
> 目标 MySQL 版本: 8.0+

## 1. 背景

### 1.1 什么是 CODDTest
CODDTest 是一种基于 Codd 关系代数理论的测试 Oracle，通过验证查询等价性来检测数据库优化器的正确性。其核心思想是：
- 生成一个原始查询（original query）
- 将查询中的表达式"折叠"（fold）为常量值，生成等价的折叠查询（folded query）
- 比较两个查询的结果集，如果不一致则可能发现 bug

### 1.2 当前状态
- SQLite3 已实现 `SQLite3CODDTestOracle`
- MySQL 尚未实现该 Oracle

---

## 2. SQLite3 CODDTest 架构分析

### 2.1 核心类结构

```
sqlancer.common.oracle.CODDTestBase (基类)
└── sqlancer.sqlite3.oracle.SQLite3CODDTestOracle (SQLite实现)
```

### 2.2 核心测试流程 (check() 方法)

```
1. 决定测试模式
   ├── EXPRESSION: 测试简单表达式折叠
   └── SUBQUERY: 测试子查询折叠

2. 生成辅助查询 (Auxiliary Query)
   ├── genSimpleSelect(): 生成包含表达式的简单 SELECT
   └── genSelectWithCorrelatedSubquery(): 生成相关子查询

3. 执行原始查询 vs 折叠查询对比
   ├── 情况1: 依赖表达式 (dependent expression)
   ├── 情况2: 空结果集 → 使用 EXISTS/NOT EXISTS
   ├── 情况3: 标量子查询 (1行1列) → 作为常量
   ├── 情况4: 单列多行 → 使用 IN 操作符
   └── 情况5: 多列 → 使用临时表/CTE/派生表

4. 结果比较
   └── compareResult(): 比较结果集是否一致
```

### 2.3 关键 AST 组件

SQLite3 特有的 CODDTest 相关 AST 类：

| 类名 | 用途 | MySQL 对应需求 |
|------|------|---------------|
| `SQLite3ExpressionBag` | 可替换表达式的容器 | 需要新建 `MySQLOracleExpressionBag` |
| `SQLite3Values` | VALUES 子句表示 | MySQL 支持 `VALUES` 语句 |
| `SQLite3ResultMap` | 结果映射 | 需要新建 `MySQLResultMap` |
| `SQLite3WithClause` | CTE WITH 子句 | MySQL 8.0+ 支持 CTE ✅ |
| `SQLite3Alias` | 别名表达式 | MySQL 有类似概念 |
| `SQLite3Exist` | EXISTS 表达式 | MySQL 支持 EXISTS ✅ |
| `SQLite3Typeof` | typeof() 函数 | MySQL 使用其他方式判断类型 |

### 2.4 核心方法详解

```java
// 生成简单 SELECT (表达式测试)
private SQLite3Select genSimpleSelect()
- 随机选择表和列
- 生成 WHERE 条件表达式
- 将表达式放入 fetchColumns 以获取结果
- 构建 SQLite3ResultMap 用于后续折叠

// 生成相关子查询
private SQLite3Select genSelectWithCorrelatedSubquery()
- 生成外层查询和内层查询
- 内层查询引用外层表的列
- 构建依赖关系

// 生成 SELECT 表达式
private SQLite3Select genSelectExpression(table, specificCondition)
- 构建 FROM 子句
- 添加 JOIN 子句
- 设置 WHERE 条件
- 设置 GROUP BY / HAVING
- 设置 ORDER BY

// 结果比较
private boolean compareResult(r1, r2)
- 比较列数
- 比较每列的值数量
- 排序后比较值
```

---

## 3. MySQL 移植可行性分析

### 3.1 语法兼容性对比 (MySQL 8.0+)

| 功能 | SQLite | MySQL 8.0+ | 兼容性 |
|------|--------|------------|--------|
| EXISTS/NOT EXISTS | ✅ | ✅ | 完全兼容 |
| IN 子查询 | ✅ | ✅ | 完全兼容 |
| CTE (WITH 子句) | ✅ | ✅ | 完全兼容 |
| 派生表 | ✅ | ✅ | 完全兼容 |
| VALUES 语句 | ✅ | ✅ | 语法略有差异 |
| 临时表 | ✅ | ✅ | 完全兼容 |
| 相关子查询 | ✅ | ✅ | 完全兼容 |
| JOIN 类型 | 6种 | 类似 | 基本兼容 |
| typeof() | ✅ | ❌ | 需用其他方式 |

### 3.2 MySQL 已有基础设施

MySQL 已有的 AST 组件（可直接复用）：
- `MySQLSelect` - SELECT 语句
- `MySQLJoin` - JOIN 子句
- `MySQLExists` - EXISTS 表达式
- `MySQLInOperation` - IN 操作符
- `MySQLDerivedTable` - 派生表 ✅
- `MySQLWithSelect` - CTE ✅
- `MySQLCteTableReference` - CTE 表引用 ✅
- `MySQLConstant` - 常量表示
- `MySQLAggregate` - 聚合函数
- `MySQLExpressionGenerator` - 表达式生成器

### 3.3 需要新增的 AST 组件

```java
// 1. MySQLOracleExpressionBag - 可替换表达式的容器
public class MySQLOracleExpressionBag extends MySQLExpression {
    private MySQLExpression innerExpr;

    public void updateInnerExpr(MySQLExpression expr);
    public MySQLExpression getInnerExpr();
}

// 2. MySQLResultMap - 结果映射
public class MySQLResultMap extends MySQLExpression {
    private MySQLValues values;
    private List<MySQLColumnReference> columns;
    private List<MySQLConstant> summary;
}

// 3. MySQLValues - VALUES 子句
public class MySQLValues extends MySQLExpression {
    private Map<String, List<MySQLConstant>> values;
    private List<MySQLColumn> columns;
}

// 4. MySQLTableAndColumnRef - 表和列引用
public class MySQLTableAndColumnRef extends MySQLExpression {
    private MySQLTable table;
}

// 5. MySQLOracleAlias - Oracle 别名
public class MySQLOracleAlias extends MySQLExpression {
    private MySQLExpression originalExpression;
    private MySQLExpression aliasExpression;
}

// 6. MySQLTypeof - 类型判断 (使用 MySQL 特有方式)
public class MySQLTypeof extends MySQLExpression {
    private MySQLExpression innerExpr;
    // MySQL 使用: IFNULL(NULLIF(expr, ''), 'NULL') 等方式判断类型
}
```

---

## 4. 详细实现方案

### 4.1 新增文件清单

```
src/sqlancer/mysql/oracle/MySQLCODDTestOracle.java      # 主 Oracle 类
src/sqlancer/mysql/ast/MySQLOracleExpressionBag.java    # 表达式容器
src/sqlancer/mysql/ast/MySQLValues.java                 # VALUES 子句
src/sqlancer/mysql/ast/MySQLResultMap.java              # 结果映射
src/sqlancer/mysql/ast/MySQLTableAndColumnRef.java      # 表列引用
src/sqlancer/mysql/ast/MySQLOracleAlias.java            # Oracle 别名
src/sqlancer/mysql/ast/MySQLTypeof.java                 # 类型判断
src/sqlancer/mysql/ast/MySQLWithClause.java             # WITH 子句 (如不存在)
```

### 4.2 修改现有文件

#### 4.2.1 MySQLOracleFactory.java
```java
CODDTest {
    @Override
    public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
        return new MySQLCODDTestOracle(globalState);
    }

    @Override
    public boolean requiresAllTablesToContainRows() {
        return true;
    }
}
```

#### 4.2.2 MySQLOptions.java
```java
public enum CODDTestModel {
    RANDOM, EXPRESSION, SUBQUERY;

    public boolean isRandom() { return this == RANDOM; }
    public boolean isExpression() { return this == EXPRESSION; }
    public boolean isSubquery() { return this == SUBQUERY; }
}

@Parameter(names = { "--coddtest-model" }, description = "Apply CODDTest on EXPRESSION, SUBQUERY, or RANDOM")
public CODDTestModel coddTestModel = CODDTestModel.RANDOM;
```

#### 4.2.3 MySQLVisitor.java & MySQLToStringVisitor.java
添加新增 AST 类的 visit 方法。

#### 4.2.4 MySQLErrors.java
添加 CODDTest 可能遇到的预期错误。

### 4.3 MySQLCODDTestOracle 核心实现

```java
package sqlancer.mysql.oracle;

public class MySQLCODDTestOracle extends CODDTestBase<MySQLGlobalState>
        implements TestOracle<MySQLGlobalState> {

    private final MySQLSchema s;
    private MySQLExpressionGenerator gen;
    private Reproducer<MySQLGlobalState> reproducer;

    // 折叠表达式相关
    private MySQLExpression foldedExpr;
    private MySQLExpression constantResOfFoldedExpr;

    // 上下文
    private List<MySQLTable> tablesFromOuterContext = new ArrayList<>();
    private List<MySQLJoin> joinsInExpr;

    // 结果存储
    Map<String, List<MySQLConstant>> auxiliaryQueryResult = new HashMap<>();
    Map<String, List<MySQLConstant>> selectResult = new HashMap<>();

    // 测试模式
    Boolean useSubqueryAsFoldedExpr;
    Boolean useCorrelatedSubqueryAsFoldedExpr;

    @Override
    public void check() throws SQLException {
        // 1. 确定测试模式
        useSubqueryAsFoldedExpr = useSubquery();
        useCorrelatedSubqueryAsFoldedExpr = useCorrelatedSubquery();

        // 2. 生成辅助查询
        MySQLSelect auxiliaryQuery = generateAuxiliaryQuery();

        // 3. 执行原始查询与折叠查询对比
        MySQLSelect originalQuery = null;
        Map<String, List<MySQLConstant>> foldedResult = new HashMap<>();
        Map<String, List<MySQLConstant>> originalResult = new HashMap<>();

        // 根据不同情况处理...

        // 4. 结果比较
        if (!compareResult(foldedResult, originalResult)) {
            throw new AssertionError("Results mismatch");
        }
    }

    // 核心方法实现...
}
```

### 4.4 MySQL 8.0+ 特有适配

#### 4.4.1 类型判断
MySQL 没有 `typeof()` 函数，使用以下方式：
```sql
-- 使用 JSON 函数 (MySQL 5.7+)
SELECT JSON_TYPE(CAST(expr AS JSON));

-- 或使用条件判断
SELECT
    CASE
        WHEN expr IS NULL THEN 'NULL'
        WHEN REGEXP_LIKE(CAST(expr AS CHAR), '^[0-9]+$') THEN 'INT'
        ELSE 'TEXT'
    END;
```

#### 4.4.2 VALUES 语法 (MySQL 8.0+)
```sql
-- MySQL 8.0+ VALUES 语法
SELECT * FROM (VALUES ROW(1, 'a'), ROW(2, 'b')) AS t(c1, c2);

-- 或使用 UNION ALL
SELECT 1 AS c1, 'a' AS c2
UNION ALL SELECT 2, 'b';
```

#### 4.4.3 WITH 子句 (MySQL 8.0+ CTE)
```sql
-- 标准 CTE 语法
WITH cte AS (
    SELECT ...
)
SELECT * FROM cte;

-- 递归 CTE
WITH RECURSIVE cte AS (
    SELECT ...
    UNION ALL
    SELECT ... FROM cte WHERE ...
)
SELECT * FROM cte;
```

### 4.5 错误处理

MySQL CODDTest 需要处理的预期错误：
```java
// MySQLErrors.java 添加
errors.add("Subquery returns more than 1 row");
errors.add("Unknown column");
errors.add("You can't specify target table");
errors.add("Expression #1 of ORDER BY clause is not in GROUP BY clause");
errors.add("Every derived table must have its own alias");
errors.add("Column count doesn't match value count");
// ... 更多 MySQL 特有错误
```

---

## 5. 实现步骤

### 阶段 1: AST 基础设施 (预计 2-3 天)
1. 创建 `MySQLOracleExpressionBag` 类
2. 创建 `MySQLValues` 类
3. 创建 `MySQLResultMap` 类
4. 创建 `MySQLTableAndColumnRef` 类
5. 更新 `MySQLVisitor` 和 `MySQLToStringVisitor`

### 阶段 2: Oracle 主体实现 (预计 3-4 天)
1. 创建 `MySQLCODDTestOracle` 基本框架
2. 实现 `genSimpleSelect()` 方法
3. 实现 `genSelectWithCorrelatedSubquery()` 方法
4. 实现 `genSelectExpression()` 方法
5. 实现结果比较逻辑

### 阶段 3: 配置与集成 (预计 1 天)
1. 修改 `MySQLOracleFactory`
2. 修改 `MySQLOptions`
3. 添加预期错误处理

### 阶段 4: 测试与调试 (预计 2-3 天)
1. 单元测试
2. 集成测试
3. Bug 修复

---

## 6. 验证方案

### 6.1 功能验证
```bash
# 基本测试
java -jar sqlancer.jar --oracle CODDTest mysql

# 指定模式测试
java -jar sqlancer.jar --oracle CODDTest mysql --coddtest-model EXPRESSION
java -jar sqlancer.jar --oracle CODDTest mysql --coddtest-model SUBQUERY

# 长时间测试
java -jar sqlancer.jar --oracle CODDTest mysql --timeout-seconds 3600
```

### 6.2 正确性验证
- 确保 fold 后的查询与原始查询结果一致
- 手动验证生成的测试用例

---

## 7. 关键文件路径

### SQLite 参考
```
src/sqlancer/sqlite3/oracle/SQLite3CODDTestOracle.java
src/sqlancer/sqlite3/ast/SQLite3Expression.java
src/sqlancer/sqlite3/SQLite3Visitor.java
src/sqlancer/sqlite3/SQLite3Options.java
src/sqlancer/sqlite3/SQLite3OracleFactory.java
```

### MySQL 修改目标
```
src/sqlancer/mysql/oracle/MySQLCODDTestOracle.java      (新建)
src/sqlancer/mysql/ast/MySQLOracleExpressionBag.java    (新建)
src/sqlancer/mysql/ast/MySQLValues.java                 (新建)
src/sqlancer/mysql/MySQLOracleFactory.java              (修改)
src/sqlancer/mysql/MySQLOptions.java                    (修改)
src/sqlancer/mysql/MySQLVisitor.java                    (修改)
src/sqlancer/mysql/MySQLToStringVisitor.java            (修改)
```

---

## 8. 风险与注意事项

1. **MySQL 版本依赖**: 本方案假设 MySQL 8.0+，CTE 功能可用
2. **语法差异**: VALUES、类型判断等需要 MySQL 特有适配
3. **性能考虑**: 临时表操作可能影响测试效率
4. **误报处理**: 需要排除已知的 MySQL 行为差异导致的误报
5. **测试覆盖率**: 需要覆盖 EXPRESSION 和 SUBQUERY 两种模式

---

## 附录 A: SQLite CODDTest 关键代码片段

### A.1 表达式折叠核心逻辑
```java
// SQLite3CODDTestOracle.java 核心流程
@Override
public void check() throws SQLException {
    // 1. 生成辅助查询获取表达式结果
    SQLite3Select auxiliaryQuery = genSimpleSelect();

    // 2. 构建原始查询 (使用表达式)
    SQLite3ExpressionBag specificCondition = new SQLite3ExpressionBag(this.foldedExpr);
    SQLite3Select originalQuery = this.genSelectExpression(null, specificCondition);

    // 3. 构建折叠查询 (使用常量)
    specificCondition.updateInnerExpr(this.constantResOfFoldedExpr);

    // 4. 比较结果
    if (!compareResult(foldedResult, originalResult)) {
        throw new AssertionError(...);
    }
}
```

### A.2 结果映射构建
```java
// genSimpleSelect() 中的关键代码
SQLite3Values values = new SQLite3Values(queryRes, tempColumnList);
this.constantResOfFoldedExpr = new SQLite3ResultMap(values, columnRef, summary, null);
```

---

## 附录 B: MySQL 已有相关组件

MySQL 已有的可复用组件：
- `MySQLDerivedTable` - 派生表支持
- `MySQLWithSelect` - CTE 支持
- `MySQLCteTableReference` - CTE 表引用
- `MySQLExists` - EXISTS 表达式
- `MySQLInOperation` - IN 操作符

这些组件可以直接用于 CODDTest 实现。
