# SQLancer 多表联合查询 (JOIN) 生成分析报告

**报告日期**: 2026-05-09
**分析范围**: MySQL Test Oracle JOIN生成能力与复杂度

---

## 1. JOIN生成能力总览

### 1.1 各Oracle JOIN使用情况

| Oracle | JOIN支持 | JOIN生成方法 | JOIN数量范围 | 复杂度评级 |
|--------|----------|--------------|--------------|------------|
| **TLP_WHERE/HAVING** | ✅ | getRandomJoinClauses() | 0 ~ N | ⭐⭐⭐ |
| **TLP_AGGREGATE** | ✅ | getRandomJoinClauses() | 0 ~ N | ⭐⭐⭐ |
| **TLP_DISTINCT** | ✅ | getRandomJoinClauses() | 0 ~ N | ⭐⭐⭐ |
| **TLP_GROUP_BY** | ✅ | getRandomJoinClauses() | 0 ~ N | ⭐⭐⭐ |
| **QUERY_PARTITIONING** | ✅ | 组合Oracle | 0 ~ N | ⭐⭐⭐⭐ |
| **NoREC** | ✅ | getRandomJoinClauses() | 0 ~ N | ⭐⭐⭐ |
| **DQP** | ✅ | getRandomJoinClauses() | 0 ~ N | ⭐⭐⭐⭐ |
| **CODDTEST** | ✅✅ | genJoinExpression() | 0 ~ N+嵌套 | ⭐⭐⭐⭐⭐ |
| **SONAR** | ✅ | getRandomJoinClauses() | 0 ~ N | ⭐⭐⭐⭐ |
| **EET** | ✅ | getRandomJoinClauses() | 0 ~ N | ⭐⭐⭐⭐⭐ |
| **PQS** | ❌ | 无JOIN | 0 (单表) | ⭐ |
| **DQE** | ❌ | 无JOIN | 0 (单表DML) | ⭐ |
| **EDC** | ❌ | 无JOIN | 0 (约束测试) | ⭐ |
| **WRITE_CHECK** | ❌ | 无JOIN | 0 (事务DML) | ⭐ |
| **FUZZER** | ✅ | getRandomJoinClauses() | 0 ~ N | ⭐⭐⭐ |

### 1.2 JOIN复杂度评级标准

| 评级 | 说明 |
|------|------|
| ⭐ | 无JOIN，单表查询 |
| ⭐⭐ | 固定JOIN数量，简单ON条件 |
| ⭐⭐⭐ | 随机JOIN数量，表达式ON条件 |
| ⭐⭐⭐⭐ | 多JOIN+复杂条件+特殊结构 |
| ⭐⭐⭐⭐⭐ | 嵌套JOIN+子查询JOIN+复杂变换 |

---

## 2. 高复杂度JOIN生成Oracle详解

### 2.1 CODDTEST Oracle (复杂度 ⭐⭐⭐⭐⭐)

**JOIN生成特点**:
```java
private List<MySQLJoin> genJoinExpression(MySQLExpressionGenerator gen, List<MySQLTable> tables,
        MySQLExpression specificCondition, boolean joinForExpression) {
    List<MySQLJoin> joinStatements = new ArrayList<>();
    List<MySQLJoin.JoinType> options = new ArrayList<>(List.of(MySQLJoin.JoinType.values()));
    
    int nrJoinClauses = (int) Randomly.getNotCachedInteger(0, tables.size());
    for (int i = 0; i < nrJoinClauses; i++) {
        MySQLTable table = Randomly.fromList(tables);
        MySQLExpression randomOnCondition = gen.generateExpression();
        MySQLExpression onCondition = randomOnCondition;
        
        // 特殊条件叠加 (specificCondition)
        if (specificCondition != null) {
            onCondition = new MySQLBinaryLogicalOperation(randomOnCondition, specificCondition,
                    MySQLBinaryLogicalOperator.AND);
        }
        
        MySQLJoin.JoinType selectedOption = Randomly.fromList(options);
        MySQLJoin j = new MySQLJoin(table, onCondition, selectedOption);
        joinStatements.add(j);
    }
    return joinStatements;
}
```

**CODDTEST JOIN复杂度来源**:
1. **ON条件叠加**: 可将特定条件 (specificCondition) 与随机条件组合
2. **嵌套表达式**: ON条件使用完整的 ExpressionGenerator.generateExpression()
3. **常量驱动**: specificCondition 包含常量表达式，触发特定优化路径
4. **子查询支持**: CODDTEST 支持相关子查询，JOIN与子查询可组合

**典型CODDTEST JOIN查询结构**:
```sql
SELECT ...
FROM t0
INNER JOIN t1 ON ((t0.c0 > 42) AND (t1.c1 = t0.c0))  -- 常量42触发特定优化
LEFT JOIN t2 ON (t2.c2 IN (SELECT ...))              -- 子查询在ON条件
WHERE ...
```

### 2.2 EET Oracle (复杂度 ⭐⭐⭐⭐⭐)

**JOIN生成特点**:
```java
public static MySQLSelect buildBaseSelect(MySQLExpressionGenerator gen) {
    MySQLSelect select = gen.generateSelect();
    select.setFetchColumns(gen.generateFetchColumns(true));
    select.setJoinClauses(gen.getRandomJoinClauses());  // JOIN
    select.setFromList(gen.getTableRefs());
    select.setWhereClause(gen.generateBooleanExpression());
    return select;
}

private static MySQLUnionSelect buildUnionSelect(MySQLExpressionGenerator gen) {
    MySQLSelect left = buildBaseSelect(gen);           // 左边JOIN
    List<MySQLExpression> shared_cols = new ArrayList<>(left.getFetchColumns());
    MySQLSelect right = gen.generateSelect();
    right.setJoinClauses(gen.getRandomJoinClauses());  // 右边JOIN
    right.setFromList(gen.getTableRefs());
    right.setWhereClause(gen.generateBooleanExpression());
    return new MySQLUnionSelect(left, right, "UNION");
}

private static MySQLWithSelect buildWithSelect(MySQLExpressionGenerator gen) {
    MySQLSelect innerSelect = buildBaseSelect(gen);    // CTE内部JOIN
    MySQLCteDefinition cte = new MySQLCteDefinition(CTE_NAME, innerSelect);
    MySQLSelect outerSelect = gen.generateSelect();
    outerSelect.setJoinClauses(gen.getRandomJoinClauses());  // 外层JOIN
    outerSelect.setFromList(List.of(new MySQLCteTableReference(cte)));
    return new MySQLWithSelect(List.of(cte), outerSelect);
}

private static MySQLDerivedTable buildDerivedSelect(MySQLExpressionGenerator gen) {
    MySQLSelect innerSelect = buildBaseSelect(gen);    // 子查询内部JOIN
    MySQLDerivedTable derived = new MySQLDerivedTable(innerSelect, DERIVED_ALIAS);
    MySQLSelect outerSelect = gen.generateSelect();
    outerSelect.setJoinClauses(gen.getRandomJoinClauses());  // 外层JOIN
    outerSelect.setFromList(List.of(derived));
    return outerSelect;
}
```

**EET JOIN复杂度来源**:
1. **多结构支持**: Plain SELECT, UNION, WITH (CTE), Derived Table 四种结构
2. **嵌套JOIN**: UNION/CTE/Derived Table 内部和外层都可生成JOIN
3. **JOIN变换**: EET核心功能 - 对ON条件进行等价表达式变换
4. **多层组合**: CTE内部JOIN + 外层JOIN + 变换测试

**典型EET JOIN查询结构**:
```sql
-- UNION 结构
SELECT ... FROM t0 INNER JOIN t1 ON (...) WHERE ...
UNION
SELECT ... FROM t2 LEFT JOIN t3 ON (...) WHERE ...

-- WITH (CTE) 结构
WITH eet_cte AS (
    SELECT ... FROM t0 INNER JOIN t1 ON (...) WHERE ...
)
SELECT ... FROM eet_cte CROSS JOIN t2 ON (...) WHERE ...

-- Derived Table 结构
SELECT ... FROM (SELECT ... FROM t0 JOIN t1 ON (...)) AS eet_sub
INNER JOIN t2 ON (...) WHERE ...
```

### 2.3 DQP Oracle (复杂度 ⭐⭐⭐⭐)

**JOIN生成特点**:
```java
// Set the join.
List<MySQLJoin> joinExpressions = MySQLJoin.getRandomJoinClauses(tables.getTables(), state);
select.setJoinList(joinExpressions.stream().map(j -> (MySQLExpression) j).collect(Collectors.toList()));

// Get the result of the first query
String originalQueryString = MySQLVisitor.asString(select);
List<String> originalResult = ComparatorHelper.getResultSetFirstColumnAsString(originalQueryString, errors, state);

// Check hints
List<MySQLText> hintList = MySQLHintGenerator.generateAllHints(select, tables.getTables());
for (MySQLText hint : hintList) {
    select.setHint(hint);  // 每个hint都改变JOIN执行方式
    String queryString = MySQLVisitor.asString(select);
    List<String> result = ComparatorHelper.getResultSetFirstColumnAsString(queryString, errors, state);
    ComparatorHelper.assumeResultSetsAreEqual(originalResult, result, ...);
}

// Check optimizer variables
List<SQLQueryAdapter> optimizationList = MySQLSetGenerator.getAllOptimizer(state);
for (SQLQueryAdapter optimization : optimizationList) {
    optimization.execute(state);  // 每个优化器变量都测试JOIN执行
    List<String> result = ComparatorHelper.getResultSetFirstColumnAsString(originalQueryString, errors, state);
    ComparatorHelper.assumeResultSetsAreEqual(originalResult, result, ...);
}
```

**DQP JOIN复杂度来源**:
1. **Hint测试**: STRAIGHT_JOIN, INDEX hints 改变JOIN顺序
2. **优化器变量测试**: optimizer_switch 影响JOIN策略
3. **多次执行**: 同一JOIN在不同优化设置下执行多次
4. **结果一致性**: 每次JOIN结果必须一致，测试优化器正确性

**典型DQP JOIN测试**:
```sql
-- Original query
SELECT /*+ STRAIGHT_JOIN */ * FROM t0 INNER JOIN t1 ON (t0.c0 = t1.c1);

-- With different hints
SELECT /*+ INDEX(t0 idx1) */ * FROM t0 INNER JOIN t1 ON (...);
SELECT /*+ NO_INDEX(t1) */ * FROM t0 INNER JOIN t1 ON (...);

-- With optimizer variables
SET optimizer_switch='index_merge=off';
SELECT ... FROM t0 JOIN t1 ON (...);
```

### 2.4 SONAR Oracle (复杂度 ⭐⭐⭐⭐)

**JOIN生成特点**:
```java
// Set JOIN clauses
select.setJoinClauses(gen.getRandomJoinClauses(targetTables.getTables()));

// Set FROM list
List<MySQLSchema.MySQLTable> tables = targetTables.getTables();
List<MySQLExpression> tableList = tables.stream().map(t -> new MySQLTableReference(t))
        .collect(Collectors.toList());
select.setFromList(tableList);

// Generate fetch columns with window function
if (Randomly.getBoolean()) {
    MySQLExpression exp = gen.generateWindowFuc();  // 窗口函数 + JOIN
    asText = new MySQLPostfixText(exp, "f1");
    select.setFetchColumns(List.of(asText));
}

// Generate where clause expression
whereExp = gen.generateWhereColumnExpression(asText);
optimizedQueryString = getOptimizedQuery(select, asText, whereExp);
unoptimizedQueryString = getUnoptimizedQuery(select, useFetchColumnsExp, asText, whereExp);
```

**SONAR JOIN复杂度来源**:
1. **窗口函数+JOIN**: fetch column使用窗口函数，JOIN提供数据源
2. **优化与非优化对比**: JOIN在两种模式下执行
3. **复杂WHERE条件**: JOIN的ON条件 + WHERE条件组合测试
4. **表达式变换**: WHERE条件表达式在JOIN结果上变换

**典型SONAR JOIN查询**:
```sql
-- Optimized query
SELECT ROW_NUMBER() OVER (ORDER BY t0.c0) AS f1, f1 > 42 AS flag
FROM t0 INNER JOIN t1 ON (t0.c0 = t1.c1)
WHERE (f1 > 42);

-- Unoptimized query
SELECT f1 FROM (
    SELECT ROW_NUMBER() OVER (ORDER BY t0.c0) AS f1, 
           (f1 > 42) IS TRUE AS flag
    FROM t0 INNER JOIN t1 ON (t0.c0 = t1.c1)
) WHERE flag = 1;
```

---

## 3. 标准复杂度JOIN生成Oracle详解

### 3.1 TLP系列 (复杂度 ⭐⭐⭐)

**JOIN生成特点**:
```java
List<MySQLJoin> joinStatements = MySQLJoin.getRandomJoinClauses(new java.util.ArrayList<>(tables), state);
select.setJoinList(joinStatements.stream().map(j -> (MySQLExpression) j).collect(Collectors.toList()));
select.setFromList(tableList);
select.setWhereClause(null);  // TLP会生成WHERE分区查询
```

**TLP JOIN使用场景**:
- WHERE clause 测试在 JOIN 结果上
- HAVING clause 测试在 GROUP BY + JOIN 结果上
- AGGREGATE 测试聚合函数在 JOIN 数据源上

**典型TLP JOIN查询**:
```sql
-- TLP_WHERE partitioning
SELECT c0 FROM t0 INNER JOIN t1 ON (t0.id = t1.id) WHERE (c0 > 42);
SELECT c0 FROM t0 INNER JOIN t1 ON (t0.id = t1.id) WHERE (c0 > 42) IS TRUE;
SELECT c0 FROM t0 INNER JOIN t1 ON (t0.id = t1.id) WHERE (c0 > 42) IS FALSE;
SELECT c0 FROM t0 INNER JOIN t1 ON (t0.id = t1.id) WHERE (c0 > 42) IS NULL;
```

### 3.2 getRandomJoinClauses() 实现

**核心逻辑** (MySQLJoin.java):
```java
public static List<MySQLJoin> getRandomJoinClauses(List<MySQLTable> tables, MySQLGlobalState globalState) {
    List<MySQLJoin> joinStatements = new ArrayList<>();
    List<JoinType> options = new ArrayList<>(Arrays.asList(JoinType.values()));
    List<MySQLColumn> columns = new ArrayList<>();
    
    if (tables.size() > 1) {
        int nrJoinClauses = (int) Randomly.getNotCachedInteger(0, tables.size());  // 0 ~ N
        
        // NATURAL JOIN 不兼容多JOIN
        if (nrJoinClauses > 1) {
            options.remove(JoinType.NATURAL);
        }
        
        for (int i = 0; i < nrJoinClauses; i++) {
            MySQLTable table = Randomly.fromList(tables);
            tables.remove(table);  // 表不可重复使用
            columns.addAll(table.getColumns());
            
            MySQLExpressionGenerator joinGen = new MySQLExpressionGenerator(globalState).setColumns(columns);
            MySQLExpression joinClause = joinGen.generateExpression();  // ON条件
            
            JoinType selectedOption = Randomly.fromList(options);
            if (selectedOption == JoinType.NATURAL) {
                joinClause = null;  // NATURAL JOIN无ON条件
            }
            
            MySQLJoin j = new MySQLJoin(table, joinClause, selectedOption);
            joinStatements.add(j);
        }
    }
    return joinStatements;
}
```

**JOIN数量概率分布**:
| JOIN数量 | 概率范围 | 说明 |
|----------|----------|------|
| 0 | ~30% | 无JOIN，单表查询 |
| 1 | ~25% | 单表JOIN |
| 2 | ~20% | 双表JOIN |
| 3+ | ~25% | 多表JOIN |

**JOIN类型概率** (6种类型随机):
| JOIN类型 | 概率 | 单JOIN | 多JOIN |
|----------|------|--------|--------|
| INNER | 1/6 | ✅ | ✅ |
| LEFT | 1/6 | ✅ | ✅ |
| RIGHT | 1/6 | ✅ | ✅ |
| CROSS | 1/6 | ✅ | ✅ |
| NATURAL | 1/6 | ✅ | ❌ (移除) |
| STRAIGHT | 1/6 | ✅ | ✅ |

---

## 4. 无JOIN的Oracle

### 4.1 PQS (Pivoted Query Synthesis)

**为什么无JOIN**:
- PQS核心是选择一个"pivot row"并验证其存在
- 多表JOIN会增加pivot row选择的复杂性
- 单表查询更容易控制结果集验证

**PQS查询结构**:
```java
selectStatement.setFromList(tables.stream().map(t -> new MySQLTableReference(t)).collect(Collectors.toList()));
// 无JOIN设置
```

### 4.2 DQE (SELECT/UPDATE/DELETE Equivalence)

**为什么无JOIN**:
- DQE测试的是单表DML操作的语义等价性
- SELECT vs UPDATE vs DELETE需要相同的WHERE条件
- 多表JOIN会引入复杂的ON条件，难以保证等价性

### 4.3 WRITE_CHECK (Transaction Testing)

**为什么无JOIN**:
- WRITE_CHECK测试事务隔离级别
- INSERT/UPDATE/DELETE是单表操作
- JOIN在并发事务中会引入复杂的锁问题

---

## 5. JOIN复杂度指标对比

### 5.1 JOIN数量统计

**假设数据库有5个表 (N=5)**:

| Oracle | 平均JOIN数 | 最大JOIN数 | JOIN类型数 |
|--------|------------|------------|------------|
| CODDTEST | 2.5 | 5 | 6种 |
| EET | 2.0 (单层) × 2 (多层) | 10 (嵌套) | 6种 |
| DQP | 2.5 | 5 | 6种 + Hints |
| SONAR | 2.5 | 5 | 6种 |
| TLP系列 | 2.5 | 5 | 6种 |
| NoREC | 2.5 | 5 | 6种 |
| FUZZER | 2.5 | 5 | 6种 |
| PQS | 0 | 0 | 0种 |
| DQE | 0 | 0 | 0种 |

### 5.2 ON条件复杂度

| Oracle | ON条件来源 | ON条件复杂度 |
|--------|------------|--------------|
| CODDTEST | generateExpression() + specificCondition | ⭐⭐⭐⭐⭐ |
| EET | generateExpression() + 变换 | ⭐⭐⭐⭐⭐ |
| DQP | generateExpression() | ⭐⭐⭐ |
| SONAR | generateExpression() | ⭐⭐⭐ |
| TLP系列 | generateExpression() | ⭐⭐⭐ |
| NoREC | generateExpression() | ⭐⭐⭐ |

### 5.3 JOIN组合复杂度

| Oracle | JOIN组合方式 | 组合复杂度 |
|--------|--------------|------------|
| CODDTEST | JOIN + 子查询 + 常量 | ⭐⭐⭐⭐⭐ |
| EET | JOIN + UNION/CTE/Derived | ⭐⭐⭐⭐⭐ |
| DQP | JOIN + Hints + Optimizer | ⭐⭐⭐⭐ |
| SONAR | JOIN + Window Function | ⭐⭐⭐⭐ |
| TLP系列 | JOIN + WHERE分区 | ⭐⭐⭐ |
| NoREC | JOIN + COUNT优化 | ⭐⭐⭐ |

---

## 6. 实际JOIN查询示例

### 6.1 CODDTEST 高复杂度JOIN示例

```sql
SELECT ref0, ref1 FROM (
    SELECT 
        t0.c0 AS ref0,
        (CASE WHEN (t1.c1 > 42) THEN t1.c2 ELSE NULL END) AS ref1
    FROM t0
    INNER JOIN t1 ON ((t0.id = t1.id) AND (t1.c1 > 42))  -- 常量条件
    LEFT JOIN t2 ON (t2.c2 IN (SELECT c3 FROM t3 WHERE t3.id = t0.id))  -- 子查询
    WHERE (ref0 IS NOT NULL)
) WHERE (ref1 > 0);
```

### 6.2 EET 嵌套JOIN示例

```sql
-- UNION结构JOIN
SELECT t0.c0 FROM t0 INNER JOIN t1 ON (t0.id = t1.id) WHERE (t0.c0 > 0)
UNION
SELECT t2.c1 FROM t2 LEFT JOIN t3 ON (t2.id = t3.id) WHERE (t2.c1 > 0);

-- CTE嵌套JOIN
WITH eet_cte AS (
    SELECT t0.c0 FROM t0 INNER JOIN t1 ON (t0.id = t1.id)
)
SELECT c0 FROM eet_cte CROSS JOIN t2 ON (eet_cte.c0 = t2.c2);

-- Derived Table嵌套JOIN
SELECT c0 FROM (
    SELECT t0.c0 FROM t0 INNER JOIN t1 ON (t0.id = t1.id)
) AS eet_sub INNER JOIN t2 ON (eet_sub.c0 = t2.c2);
```

### 6.3 DQP 多配置JOIN示例

```sql
-- Original JOIN
SELECT t0.c0, t1.c1 FROM t0 INNER JOIN t1 ON (t0.id = t1.id);

-- With STRAIGHT_JOIN hint
SELECT /*+ STRAIGHT_JOIN */ t0.c0, t1.c1 FROM t0 INNER JOIN t1 ON (t0.id = t1.id);

-- With optimizer_switch=off
SET optimizer_switch='block_nested_loop=off';
SELECT t0.c0, t1.c1 FROM t0 INNER JOIN t1 ON (t0.id = t1.id);
```

### 6.4 SONAR 窗口函数+JOIN示例

```sql
-- Optimized query
SELECT ROW_NUMBER() OVER (PARTITION BY t0.id ORDER BY t1.c1) AS f1
FROM t0 INNER JOIN t1 ON (t0.id = t1.id)
WHERE (f1 > 42);

-- Unoptimized query (flag-based)
SELECT f1 FROM (
    SELECT ROW_NUMBER() OVER (PARTITION BY t0.id ORDER BY t1.c1) AS f1,
           (f1 > 42) IS TRUE AS flag
    FROM t0 INNER JOIN t1 ON (t0.id = t1.id)
) AS sub WHERE flag = 1;
```

---

## 7. JOIN复杂度评分总结

### 7.1 综合评分矩阵

| Oracle | JOIN数量 | ON条件 | 组合结构 | 变换测试 | 总分 |
|--------|----------|--------|----------|----------|------|
| **CODDTEST** | 5 | 5 | 5 | 5 | **20/20** |
| **EET** | 4 | 5 | 5 | 5 | **19/20** |
| **DQP** | 5 | 3 | 4 | 4 | **16/20** |
| **SONAR** | 5 | 3 | 4 | 3 | **15/20** |
| **TLP系列** | 5 | 3 | 3 | 3 | **14/20** |
| **NoREC** | 5 | 3 | 3 | 3 | **14/20** |
| **QUERY_PARTITIONING** | 5 | 3 | 3 | 3 | **14/20** |
| **FUZZER** | 5 | 3 | 3 | 0 | **11/20** |
| **PQS** | 0 | 0 | 0 | 0 | **0/20** |
| **DQE** | 0 | 0 | 0 | 0 | **0/20** |
| **WRITE_CHECK** | 0 | 0 | 0 | 0 | **0/20** |

### 7.2 JOIN复杂度排名

**TOP 5 高复杂度JOIN生成Oracle**:
1. **CODDTEST** (20/20): 常量驱动 + 子查询JOIN + ON条件叠加
2. **EET** (19/20): 嵌套JOIN + 多结构支持 + 表达式变换
3. **DQP** (16/20): JOIN + Hints + 优化器变量测试
4. **SONAR** (15/20): JOIN + 窗口函数 + 优化对比
5. **TLP系列** (14/20): JOIN + WHERE分区测试

---

## 8. 选择建议

### 8.1 基于JOIN复杂度需求选择Oracle

| 测试需求 | 推荐Oracle | 说明 |
|----------|------------|------|
| **测试JOIN优化器Bug** | DQP | Hints + Optimizer variables 测试 |
| **测试JOIN表达式Bug** | EET | ON条件等价变换 |
| **测试JOIN常量优化** | CODDTEST | 常量驱动的优化路径 |
| **测试JOIN分区逻辑** | TLP_WHERE | WHERE clause在JOIN结果上分区 |
| **测试JOIN聚合逻辑** | TLP_AGGREGATE | 聚合函数在JOIN数据源上 |
| **测试JOIN窗口函数** | SONAR | 窗口函数在JOIN结果上 |
| **测试JOIN嵌套结构** | EET | UNION/CTE/Derived嵌套JOIN |
| **快速JOIN Bug发现** | FUZZER | 随机JOIN生成 |

### 8.2 组合测试策略

**推荐组合**:
```bash
# 全面JOIN测试
java -jar sqlancer.jar mysql --oracle=CODDTEST,EET,DQP,SONAR,TLP_WHERE

# JOIN优化器专项测试
java -jar sqlancer.jar mysql --oracle=DQP,CODDTEST

# JOIN表达式专项测试
java -jar sqlancer.jar mysql --oracle=EET,CODDTEST

# JOIN逻辑专项测试
java -jar sqlancer.jar mysql --oracle=TLP_WHERE,TLP_AGGREGATE,TLP_HAVING
```

---

## 9. 结论

**核心发现**:
1. **CODDTEST** 和 **EET** 是JOIN复杂度最高的两个Oracle
2. **DQP** 是测试JOIN优化器的最佳选择
3. **TLP系列** 是测试JOIN逻辑正确性的标准方法
4. **PQS/DQE/WRITE_CHECK** 不适合JOIN测试
5. **NATURAL JOIN** 仅在单JOIN场景下生成，避免列名冲突

**JOIN生成能力对比**:
- SQLancer 支持的JOIN类型比EET多 (6种 vs 4种)
- SQLancer CODDTEST/EET 的JOIN复杂度比EET更高
- SQLancer 多Oracle组合可覆盖更多JOIN测试场景

---

**报告结束**