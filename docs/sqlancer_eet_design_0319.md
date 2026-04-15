# SQLancer EET Oracle 实现设计文档

**文档日期**：2025-03-19  
**依据**：EET-main 源码逻辑、[eet_analyze.md](eet_analyze.md)、SQLancer 架构

---

## 一、EET-main 与 SQLancer 模块对应关系

### 1.1 核心模块映射

| EET-main 模块 | 职责 | SQLancer 对应 |
|---------------|------|---------------|
| `grammar.cc` / `query_spec` | 生成 SELECT AST | `MySQLExpressionGenerator` + `TLPWhereGenerator` 接口 |
| `value_expr::factory()` | 生成 value 表达式 | `MySQLExpressionGenerator.generateExpression()` |
| `bool_expr::factory()` | 生成 bool 表达式 | `MySQLExpressionGenerator.generateBooleanExpression()` |
| `value_expr::equivalent_transform()` | value 表达式 Rule 3–6 | **新建** `MySQLEETTransformer.transformValueExpr()` |
| `bool_expr::equivalent_transform()` | bool 表达式 Rule 1–2 | **新建** `MySQLEETTransformer.transformBoolExpr()` |
| `printed_expr` | copy_expr（打印原表达式字符串） | **新建** `MySQLPrintedExpression` |
| `case_expr` | CASE WHEN 结构 | 复用 `MySQLCaseOperator` |
| `bool_term` | AND/OR，构造 true/false_expr | 复用 `MySQLBinaryLogicalOperation` |
| `qcn_select_tester::eq_transform_query()` | AST 遍历与变换调用 | **新建** `MySQLEETQueryTransformer.eqTransformQuery()` |
| `qcn_select_tester::qcn_test_without_initialization()` | 执行 + 结果比较 | **新建** `MySQLEETOracle.check()` |
| `general_process::compare_*` | 结果比较 | `ComparatorHelper` 或自定义 multiset 比较 |

### 1.2 数据结构对应

| EET-main | SQLancer |
|----------|----------|
| `query_spec` | `MySQLSelect` |
| `select_list->value_exprs` | `MySQLSelect.getFetchColumns()` |
| `from_clause->reflist` | `MySQLSelect.getFromList()` + `getJoinList()` |
| `search` (WHERE) | `MySQLSelect.getWhereClause()` |
| `group_clause->having_cond_search` | `MySQLSelect.getHavingClause()` |
| `value_expr` / `bool_expr` | `MySQLExpression` |
| `bool_term` (AND/OR) | `MySQLBinaryLogicalOperation` |
| `case_expr` | `MySQLCaseOperator` |
| `not_expr` | `MySQLUnaryPrefixOperation(NOT)` |
| `null_predicate` | `MySQLUnaryPostfixOperation(IS NULL/NOT NULL)` |
| `scope->refs` | `AbstractTables` / `MySQLSchema` |

### 1.3 与现有 Test Oracle 的隔离（非回归约束）

**目标**：新增 EET Oracle 后，**默认行为与未集成 EET 时完全一致**；仅在用户显式启用 EET 时执行 EET 逻辑。

| 约束 | 说明 |
|------|------|
| **独立 Oracle 实现** | EET 仅通过**新建**类实现（如 `MySQLEETOracle`、`MySQLEETQueryTransformer`、`MySQLEETTransformer`），**禁止**在既有 Oracle 类（`MySQLTLPBase`、`MySQLTLP*Oracle`、`NoRECOracle` 包装、`MySQLPivotedQuerySynthesisOracle`、`MySQLDQEOracle`、`MySQLDQPOracle`、`MySQLFuzzer`、`CERTOracle` 等）中插入 EET 分支或改写其 `check()` / 生成逻辑。 |
| **工厂注册：仅新增枚举项** | 在 `MySQLOracleFactory` 中**仅增加**新枚举常量（如 `EET`）及对应 `create()`，**不修改**已有枚举常量的 `create()` 实现与返回值类型契约。 |
| **默认选项不变** | `MySQLOptions`（或等价 DBMS 配置）中 **`oracles` 默认值不得自动包含 EET**；用户需通过命令行（如 `--oracle EET`）或显式配置列表才会运行 EET。未指定 EET 时，执行路径与集成前相同。 |
| **不并入 QUERY_PARTITIONING** | `QUERY_PARTITIONING` 通过 `CompositeTestOracle` 组合固定子 Oracle 列表。**不得**在未做产品决策与回归验证前，将 EET 加入该组合；避免改变现有「分区/组合」Oracle 的行为与统计。若未来要组合，应单独增加新的组合枚举或文档化变更。 |
| **共享代码：最小侵入** | 复用 `MySQLExpressionGenerator`、`MySQLErrors`、`TestOracleUtils` 等时，**优先**在 EET 包内组合调用；若必须扩展生成器，应通过**子类、新工厂方法或独立 Helper** 完成，避免修改 `MySQLExpressionGenerator` 的全局默认行为导致其他 Oracle 生成分布变化。 |
| **AST 与状态** | 变换应对 `generateSelect()` 产出的 AST 做**副本变换**（见 §4.2），不就地污染可能被其他模块复用的对象；不在 `MySQLGlobalState` 上设置仅 EET 使用且影响后续迭代的持久标志。 |
| **Comparator / 结果比较** | multiset 比较逻辑放在 **EET 私有工具方法或类**中；**不修改** `ComparatorHelper` 等共享类的既有方法签名与语义，以免 TLP、NoREC 等依赖的行为变化。 |
| **回归验证** | 集成后应在 CI/本地用**默认 Oracle 列表**（不含 EET）跑一轮既有用例；启用 EET 时单独验证 EET 路径。 |

### 1.4 形式化测试预言（论文 Eq. 1）

论文给出：若表达式 \(E\) 与 \(E'\) **语义等价**（记作 \(E \equiv E'\)），将查询 \(Q\) 中 \(E\) 替换为 \(E'\) 得到 \(Q' = Q[E'/E]\)，则对任意符合规范的 DBMS 应有：

\[
E \equiv E' \;\Rightarrow\; \mathit{DB}(Q) \equiv \mathit{DB}(Q')
\]

其中 \(\mathit{DB}(Q)\) 表示被测引擎执行 \(Q\) 得到的结果。EET Oracle 将「等价」落实为 **结果 multiset 相等**（见 §4.3）：若 \(\mathit{multiset}(\mathit{DB}(Q)) \neq \mathit{multiset}(\mathit{DB}(Q'))\) 且二次验证仍不一致，则判定存在逻辑缺陷。实现与文档表述均应对齐「**替换子表达式后的整查询**」而非仅比较 SQL 字符串。

---

## 二、SQL 生成逻辑（对齐 EET-main）

### 2.1 EET-main 流程（qcn_select_tester 构造）

```
1. initial_scope.new_stmt()
2. select_query = make_shared<query_spec>(0, &initial_scope)
3. select_query->out(s) → original_query
4. execute_query(original_query)  // 若结果行数 > MAX 则重试
5. skip_one_original_execution = true  // 后续不再重复执行 original
```

### 2.2 SQLancer 对应流程

沿用 TLP/NoREC 的生成模式：

```java
// 1. 取表和列
AbstractTables<MySQLTable, MySQLColumn> targetTables =
    TestOracleUtils.getRandomTableNonEmptyTables(state.getSchema());

// 2. 生成 SELECT（对齐 query_spec）
MySQLExpressionGenerator gen = new MySQLExpressionGenerator(state).setColumns(targetTables.getColumns());
MySQLSelect select = (MySQLSelect) gen.generateSelect();

// 3. 设置各子句（对齐 select_list, from_clause, search）
select.setFetchColumns(gen.generateFetchColumns(true));
select.setJoinClauses(gen.getRandomJoinClauses());
select.setFromList(gen.getTableRefs());
select.setWhereClause(gen.generateBooleanExpression());

// 4. 可选：GROUP BY、HAVING、ORDER BY、LIMIT
// 与 EET-main 的 has_group 分支一致
```

**对应关系**：

| EET-main | SQLancer |
|----------|----------|
| `query_spec` 构造 | `gen.generateSelect()` |
| `select_list->value_exprs` | `gen.generateFetchColumns()` |
| `from_clause->reflist` | `gen.getTableRefs()` + `gen.getJoinClauses()` |
| `search` (WHERE) | `gen.generateBooleanExpression()` |

### 2.3 EET-main 特殊逻辑在 SQLancer 中的处理

| 项目 | EET-main | SQLancer 做法 |
|------|----------|----------------|
| 子查询 | `eq_transform_table_ref` 递归处理 `table_subquery` | 遍历 `getFromList()`、`getJoinList()`，遇子查询递归变换 |
| has_group | 有 GROUP 时不变换 select_list | `getGroupByClause()!=null` 时跳过 fetchColumns 变换 |
| 行数限制 | `MAX_PROCESS_ROW_NUM` | 可选：`ComparatorHelper` 返回行数超阈值则重试 |
| ClickHouse 限制 | select 中无子查询 | MySQL 无需此分支 |

### 2.4 EET-main 的查询生成重试与约束

- **重试循环**：`query_spec` 生成后执行，若结果行数 `> MAX_PROCESS_ROW_NUM`（10000）或执行异常则 `continue` 重试（qcn_select_tester.cc:18–24）
- **skip_one_original_execution**：首次执行 original 后置 true，后续 `qcn_test_without_initialization` 不再重复执行 original，仅执行 qit 并比较
- **scope 与 refs**：`value_expr::factory` 和 `bool_expr::factory` 依赖 `scope->refs` 提供可用列/表，SQLancer 需通过 `setColumns` 注入等效上下文

### 2.5 设计范围：SELECT 与 EET-main 的 DML

| 项目 | 说明 |
|------|------|
| **本设计范围** | 与当前 SQLancer MySQL Oracle 主流用法一致，**仅覆盖 SELECT**（单条 `MySQLSelect` 的生成、变换与执行比较）。 |
| **EET-main 工程** | 除 `qcn_select_tester` 外，还存在 UPDATE/DELETE 等 tester；论文方法可推广，但 **本设计不实现 DML 路径**，避免与现有 DQE 等 Oracle 职责混淆。 |
| **后续扩展** | 若需 DML，应另立 `MySQLEET*UpdateOracle` 等，并单独评审对 schema/数据状态的影响。 |

---

## 三、SQL 变换逻辑（严格对齐 EET-main）

### 3.1 AST 遍历顺序

EET-main 的 `eq_transform_query` 顺序（qcn_select_tester.cc:29–51）：

```
1. select_list->value_exprs  （若无 group）
2. from_clause->reflist.back() → eq_transform_table_ref
3. search (WHERE)
4. group_clause->having_cond_search (若有 group)
```

`eq_transform_table_ref` 递归逻辑：

```
- table_or_query_name → 不变换（Rule 7）
- table_subquery → eq_transform_query(subquery)
- joined_table → eq_transform_table_ref(lhs), eq_transform_table_ref(rhs)
```

**注意**：EET-main 的 `eq_transform_table_ref` 仅递归 lhs/rhs，**未显式变换** `joined_table.condition`（JOIN ON 条件）。SQLancer 的 `MySQLJoin` 将 ON 条件独立存储于 `getOnClause()`，**建议对 JOIN ON 条件也做变换**，以提升覆盖。

SQLancer 对应实现（伪代码）：

```java
void eqTransformQuery(MySQLSelect select) {
    if (!hasGroup(select)) {
        for (MySQLExpression expr : select.getFetchColumns()) {
            transformExpression(expr);
        }
    }
    eqTransformTableRefs(select.getFromList(), select.getJoinList());
    if (select.getWhereClause() != null) {
        transformExpression(select.getWhereClause());
    }
    if (select.getHavingClause() != null) {
        transformExpression(select.getHavingClause());
    }
}

void eqTransformTableRefs(List<MySQLExpression> fromList, List<MySQLJoin> joinList) {
    // FROM: 纯表引用 Rule 7 不变换
    for (MySQLExpression ref : fromList) {
        if (ref instanceof MySQLTableReference) continue;
        // 子查询：若 SQLancer 支持 FROM 子查询 AST，递归 eqTransformQuery
    }
    // JOIN ON 条件（EET-main 未显式变换，SQLancer 建议补充）
    for (MySQLJoin join : joinList) {
        MySQLExpression onCond = join.getOnClause();  // MySQLJoin 使用 getOnClause
        if (onCond != null) transformExpression(onCond);
    }
}
```

### 3.1.1 EET-main 未变换的子句

| 子句 | EET-main | 说明 |
|------|----------|------|
| ORDER BY | 不变换 | `query_spec.order_clause` 未在 `eq_transform_query` 中处理 |
| LIMIT | 不变换 | `query_spec.limit_num` 为常量 |
| GROUP BY 列列表 | 不变换 | 仅变换 `having_cond_search` |
| 窗口函数 partition_by/order_by | 不变换 | `named_window` 未在 select 的 eq_transform 中处理 |

首版实现可仅覆盖 SELECT 列表、FROM/JOIN、WHERE、HAVING，与 EET-main 保持一致。

### 3.1.2 复合查询与 CTE（首版已知限制）

| 结构 | 论文期望 | 本设计首版 |
|------|----------|------------|
| **UNION / INTERSECT / EXCEPT** | 理想情况下应对各分支内查询递归做 `eqTransformQuery` | 若 `generateSelect()` **不产生**复合查询，则无额外工作；若后续生成器支持，需为每种 compound 增加遍历与变换，否则列为**已知漏测**并在完备性清单跟踪。 |
| **WITH（CTE）** | 应对 CTE 内层 `SELECT` 递归变换 | 与上类似：首版以生成器实际产出为准；**未覆盖 CTE 内层时应在实现注释与文档中明示**。 |

与论文「任意复杂 SELECT」的表述相比，首版以 **SQLancer 实际 AST 覆盖范围** 为界；扩展时保持与 EET-main 相同的遍历契约即可。

### 3.2 递归变换与复合表达式

EET-main 中，**复合表达式**先对自身应用 Rule 1–6，再对子表达式递归调用 `equivalent_transform`：

- `case_expr::equivalent_transform()`：先 `value_expr::equivalent_transform()`，再 `condition->equivalent_transform()`、`true_expr->equivalent_transform()`、`false_expr->equivalent_transform()`
- `bool_binop::equivalent_transform()`：先 `bool_expr::equivalent_transform()`，再 `lhs->equivalent_transform()`、`rhs->equivalent_transform()`
- `in_query`、`exists_predicate`、`comp_subquery`：除自身变换外，还会递归变换子查询内的 `search`、`select_list` 等

因此，**每个可变换节点**既可能被父节点选中的变换点，也会对子节点递归。SQLancer 实现时，`transformExpression` 应递归处理子表达式（如 `MySQLCaseOperator` 的 condition、thenExpr、elseExpr）。

**实现（本仓库）**：`MySQLEETTransformer.transformExpression` 先通过 `MySQLEETExpressionTree.mapChildren(transformExpression, …)` 对子表达式做完整递归（覆盖 `MySQLCaseOperator`、二元/一元逻辑与比较、`MySQLInOperation`、`MySQLExists`、`MySQLCastOperation`、`MySQLBetweenOperation`、`MySQLPrintedExpression`、`MySQLComputableFunction`、`MySQLAggregate` 等），再对**当前根节点**应用一次 Rule 1–6（`tryConstBoolTransform` / `transformBoolExpr` / `transformValueExpr`）。`MySQLText` / `MySQLTableReference` 仍为 Rule 7 不变换。

### 3.3 7 条变换规则实现

#### Rule 1–2：bool_expr

EET-main（bool_expr.cc:67–83）：

- `is_case_true` → `true_expr AND share_this`（Rule 2）
- `!is_case_true` → `false_expr OR share_this`（Rule 1）

`true_expr` / `false_expr` 定义：

```
true_expr(p)  = (p) OR (NOT p) OR (p IS NULL)
false_expr(p) = (p) AND (NOT p) AND (p IS NOT NULL)
```

SQLancer 实现示例：

```java
MySQLExpression transformBoolExpr(MySQLExpression expr) {
    MySQLExpression randomBool = gen.generateBooleanExpression();
    int choice = Randomly.getInteger(0, 5);  // 0..2 -> Rule 2, 3..5 -> Rule 1
    boolean useTrueExpr = (choice <= 2);

    MySQLExpression notRand = new MySQLUnaryPrefixOperation(randomBool, NOT);
    MySQLExpression randIsNull = new MySQLUnaryPostfixOperation(randomBool, IS_NULL, false);
    MySQLExpression randIsNotNull = new MySQLUnaryPostfixOperation(randomBool, IS_NULL, true);  // IS NOT NULL

    MySQLExpression base;
    if (useTrueExpr) {
        // p OR (NOT p) OR (p IS NULL)
        MySQLExpression part1 = new MySQLBinaryLogicalOperation(randomBool, notRand, OR);
        base = new MySQLBinaryLogicalOperation(part1, randIsNull, OR);
        // true_expr AND expr
        return new MySQLBinaryLogicalOperation(base, expr, AND);
    } else {
        // p AND (NOT p) AND (p IS NOT NULL)
        MySQLExpression part1 = new MySQLBinaryLogicalOperation(randomBool, notRand, AND);
        base = new MySQLBinaryLogicalOperation(part1, randIsNotNull, AND);
        return new MySQLBinaryLogicalOperation(base, expr, OR);
    }
}
```

#### Rule 3–6：value_expr

EET-main（value_expr.cc:77–106）choice 映射：

- `choice <= 3` → Rule 4：`CASE WHEN true_expr THEN expr ELSE rand_expr END`
- `choice 4–6` → Rule 3：`CASE WHEN false_expr THEN rand_expr ELSE expr END`
- `choice 7–9` → Rule 5/6：`CASE WHEN rand_bool THEN copy/copy ELSE expr END`

SQLancer 实现示例：

```java
MySQLExpression transformValueExpr(MySQLExpression expr) {
    MySQLExpression randomValue = gen.generateExpression(expr.getType());  // 需类型一致
    MySQLExpression randomBool = gen.generateBooleanExpression();

    int choice = Randomly.getInteger(0, 8);  // d9() -> 0..8

    if (choice <= 2) {
        // Rule 4
        MySQLExpression trueExpr = buildTrueExpr(randomBool);
        return new MySQLCaseOperator(null,
            List.of(trueExpr), List.of(expr), randomValue);
    } else if (choice <= 5) {
        // Rule 3
        MySQLExpression falseExpr = buildFalseExpr(randomBool);
        return new MySQLCaseOperator(null,
            List.of(falseExpr), List.of(randomValue), expr);
    } else {
        // Rule 5 or 6
        MySQLExpression copy = copyExpr(expr);  // printed_expr 逻辑
        if (Randomly.getBoolean()) {
            return new MySQLCaseOperator(null,
                List.of(randomBool), List.of(copy), expr);   // Rule 5
        } else {
            return new MySQLCaseOperator(null,
                List.of(randomBool), List.of(expr), copy);   // Rule 6
        }
    }
}
```

#### copy_expr：printed_expr 逻辑

EET-main（printed_expr.cc）：将原表达式通过 `out()` 打印为字符串，再嵌入为新表达式。

SQLancer 实现选项：

```java
// 方案 A：MySQLPrintedExpression
class MySQLPrintedExpression implements MySQLExpression {
    private final String printedSql;  // MySQLToStringVisitor.visit(expr) 的结果
    MySQLPrintedExpression(MySQLExpression expr) {
        this.printedSql = MySQLVisitor.asString(expr);  // 可能需带括号
    }
    // visit 时直接 append printedSql
}

// 方案 B：深拷贝 AST（若 SQLancer 已有 copy 机制）
MySQLExpression copyExpr(MySQLExpression expr) {
    return MySQLExpressionDeepCopy.visit(expr);  // 需实现深拷贝 Visitor
}
```

EET 论文采用 printed 形式即可满足 copy_expr 语义；若无现成 deep copy，方案 A 更稳妥。

#### Rule 7：CASE-WHEN 不适用

EET-main：`table_or_query_name` 等表引用不参与变换。

SQLancer：`MySQLTableReference`、表名等表引用不调用 `transformExpression`，保持 Rule 7。

**Rule 7 节点枚举（实现时对照）**：下列情形应走 **Rule 7（不变换）** 或等价地跳过变换，与论文「保守策略」及 EET-main 一致；若 AST 类型随版本增减，应同步更新本表。

| 类别 | 典型节点 / 位置 |
|------|-----------------|
| 表引用 | `MySQLTableReference`、纯表名/别名等无表达式语义的引用 |
| 已由 §3.1.1 覆盖 | `ORDER BY` 表达式列表、`LIMIT` 常量、`GROUP BY` 列列表（仅 HAVING 中条件参与变换时除外）、窗口 `PARTITION BY`/`ORDER BY`（若未纳入遍历） |
| 不适于套 CASE | 无法保证类型/语义的内部节点（依方言与生成器判定） |
| 常量 NULL 特例 | `const_bool` / `NULL` 等按 §3.3 const_bool 分支，不强行套 Rule 3–6 |

#### const_bool 特殊处理（EET-main const_bool.cc）

`TRUE`/`FALSE` 常量有专门逻辑，等价于 Rule 1/2 的特例：

- `TRUE` → `TRUE OR extend_expr`（extend_expr 为随机 bool）
- `FALSE` → `FALSE AND extend_expr`
- `NULL` → 不变换

SQLancer 的 `MySQLConstant` 若为布尔常量，可单独分支处理。

### 3.4 规则选择策略

与论文 3.2.3 一致：

- **布尔表达式**：随机选 Rule 1–6
- **非布尔且 CASE WHEN 适用**：随机选 Rule 3–6
- **CASE WHEN 不适用**：Rule 7，不变换

### 3.5 类型一致性与随机生成重试

**类型一致性**（EET-main case_expr.cc:11–20）：

- `rand_expr` 与 `expr` 的返回类型必须一致，否则部分 DBMS 会触发歧义行为
- 若 `true_expr.type != false_expr.type`，EET 会重试 `value_expr::factory` 直至类型匹配

**随机生成重试**（value_expr.cc:56–70, bool_expr.cc:48–58）：

- `value_expr::equivalent_transform` 和 `bool_expr::equivalent_transform` 使用 `while(1) try-catch`，`factory()` 失败时 `continue` 重试
- SQLancer 的 `MySQLExpressionGenerator.generateExpression()` 可能抛 `IgnoreMeException`，需在 `transformExpression` 中捕获并重试

### 3.6 三值逻辑与 CASE WHEN（论文 Eq. 13）

SQL 布尔为**三值逻辑**：TRUE、FALSE、UNKNOWN（NULL）。论文对简单 `CASE` 的语义可概括为：条件为 TRUE 时走「第一分支」，为 **FALSE 或 NULL** 时走 ELSE（Eq. 13 类表述）。

与 EET 规则的关系简述：

- **`true_expr(p)`**（\(p \lor \lnot p \lor (p\ \text{IS NULL})\)）在 SQL 语义下为**永真**，故 Rule 4 中 `WHEN true_expr THEN expr` 始终取 `expr`。
- **`false_expr(p)`**（\(p \land \lnot p \land (p\ \text{IS NOT NULL})\)）为**永假**，故 Rule 3 中 `WHEN false_expr THEN rand_expr` 从不取 THEN 支，整体等价于 `expr`。
- Rule 5/6 中 `rand_bool` 为随机布尔表达式，THEN/ELSE 与 `copy_expr(expr)` 的组合在真值分支上仍保持与 `expr` 同值（在 `copy_expr` 与 `expr` 语义一致的前提下）。

实现时不必重复证明，但应知晓：**NULL 分支与 FALSE 一同落入 ELSE** 是 SQL 标准行为，EET-main 的规则构造已将该语义考虑在内；若 MySQL 与论文侧方言在边界上存在差异，通过 `ExpectedErrors` 与方言测试收敛。

---

## 四、整体流程与 check() 逻辑

### 4.1 与 EET-main 流程对应

```
EET-main (qcn_test):
  1. initial_origin_and_qit_query:
     - original_query = query->out()
     - eq_transform_query(select)
     - qit_query = select->out()
     - back_transform_query(select)
  2. execute original → original_result
  3. execute qit → qit_result
  4. original_result == qit_result ? no bug : bug
  5. 若不一致，再执行一次做验证
```

SQLancer 对应：

```java
@Override
public void check() throws SQLException {
    // 1. 生成原始查询
    MySQLSelect select = buildRandomSelect();  // 对齐 query_spec 生成
    String originalQuery = MySQLVisitor.asString(select);

    // 2. 变换（就地或返回副本，见下）
    MySQLSelect transformedSelect = eqTransformQuery(select);
    String transformedQuery = MySQLVisitor.asString(transformedSelect);

    // 3. 执行并比较（需实现 multiset 风格的全结果集比较）
    List<List<String>> originalResult = getFullResultSetAsMultiset(originalQuery, state, errors);
    List<List<String>> transformedResult = getFullResultSetAsMultiset(transformedQuery, state, errors);

    if (!compareResultMultisets(originalResult, transformedResult)) {
        // 二次验证
        List<List<String>> orig2 = getFullResultSetAsMultiset(originalQuery, state, errors);
        List<List<String>> trans2 = getFullResultSetAsMultiset(transformedQuery, state, errors);
        if (!compareResultMultisets(orig2, trans2)) {
            reproducer = new MySQLEETReproducer(originalQuery, transformedQuery, ...);
            throw new AssertionError("EET logic bug: result mismatch");
        }
    }
}
```

### 4.2 变换方式：就地 vs 副本

EET-main 采用**就地变换**：`equivalent_transform()` 在节点上设置 `eq_value_expr`，`out()` 在 `is_transformed` 时打印等价形式。

SQLancer 可选两种方式：

- **方案 A（推荐）**：构建变换后的**副本 AST**，不修改原 `MySQLSelect`。遍历原 AST，对每个表达式调用 `transformExpression` 返回新表达式，组装成新的 `MySQLSelect`。与 SQLancer 不可变风格更契合。
- **方案 B**：在表达式节点上增加“变换态”，类似 EET。需要扩展 `MySQLExpression` 接口或在 `MySQLToStringVisitor` 中根据状态选择输出，侵入性较大。

### 4.3 结果比较（对齐 EET-main）

EET-main 使用 `multiset<row_output>` 比较（qcn_tester.hh:26–27），**行顺序无关**。比较逻辑（qcn_select_tester.cc:279–301）：

1. `original_result != qit_result` 时进入验证
2. 再次执行 original 和 qit，若仍不一致则判定为 bug
3. 若二次执行后一致，则视为非稳定复现，`return true`（不报 bug）

SQLancer 的 `ComparatorHelper.getResultSetFirstColumnAsString` 仅返回**第一列**的 `List<String>`，TLP 使用 `HashSet` 比较（去重后比较，非 multiset）。EET 需**多列、多行**的 multiset 比较（行可重复、顺序无关），建议：

- 在 EET Oracle 中实现 `getFullResultSetAsMultiset`：通过 `SQLancerResultSet` 遍历所有列，将每行转为 `List<String>`，收集为 `List<List<String>>` 或等价结构
- 比较时对两个结果集排序后逐行比较，或实现 multiset 等价比较（行数相同且每行出现次数一致）

**multiset 比较实现示例**：

```java
// 获取完整结果集（每行 = List<String>，支持多列）
List<List<String>> getFullResultSetAsMultiset(MySQLQueryAdapter q, MySQLGlobalState state, List<String> errors) {
    List<List<String>> rows = new ArrayList<>();
    try (SQLancerResultSet rs = q.executeAndGet(state)) {
        if (rs == null) return rows;
        int cols = rs.getColumnCount();
        while (rs.next()) {
            List<String> row = new ArrayList<>();
            for (int i = 0; i < cols; i++) row.add(rs.getString(i + 1));
            rows.add(row);
        }
    }
    return rows;
}

// multiset 等价：排序后逐行比较（行可重复，顺序无关）
boolean compareResultMultisets(List<List<String>> a, List<List<String>> b) {
    if (a.size() != b.size()) return false;
    List<String> aSorted = a.stream().map(r -> String.join("\t", r)).sorted().toList();
    List<String> bSorted = b.stream().map(r -> String.join("\t", r)).sorted().toList();
    return aSorted.equals(bSorted);
}
```

### 4.4 错误处理与 ExpectedErrors

EET-main（qcn_tester.cc:105–124）：

- 执行异常时，若 `ignore_crash == false`，保存 unexpected.sql 并 `abort`
- 若 `ignore_crash == true`，sleep 1 分钟后 `throw`，由上层重试

SQLancer 通过 `ExpectedErrors` 过滤预期错误，未匹配则抛 `AssertionError` 或 `IgnoreMeException`。EET Oracle 需复用 `MySQLErrors.getExpressionErrors()` 等，对变换后 SQL 的预期错误做声明。

### 4.5 结果比较：非确定性、类型与排序规则（实现假设）

以下与论文工具常隐式假设一致，设计层显式写出，避免误报/漏报争议：

| 风险 | 说明 |
|------|------|
| **LIMIT 无 ORDER BY** | 多数字库 multiset 意义下仍应一致；若优化器/计划导致不稳定集合（极少见），可能产生假阳性。可与 EET-main 一样通过行数阈值、重试或生成策略规避。 |
| **浮点与 DECIMAL** | 用 `ResultSet.getString` 拼行键时，与引擎内部二进制运算的十进制表示可能不完全一致；若出现边界不一致，需评估改为类型感知比较或规范化格式。 |
| **排序规则（collation）** | 字符串列在不同比较路径下若 collation 不一致，理论上可能影响 multiset 字符串化；首版可与 EET-main 类似依赖 JDBC 返回字符串，异常时再收紧。 |
| **事务与隔离** | 纯只读 SELECT 在同一连接、默认隔离级别下通常足够；若未来与其它 Oracle 共享连接或引入写入，需单独说明会话与隔离级别，避免读到中间状态。 |

---

## 五、新建类清单与职责

| 类 | 职责 |
|----|------|
| `MySQLEETOracle` | 实现 `TestOracle<MySQLGlobalState>`，驱动 check、Reproducer |
| `MySQLEETQueryTransformer` | 对 `MySQLSelect` 做 `eqTransformQuery`，调用 Transformer |
| `MySQLEETTransformer` | 实现 7 条规则：`transformBoolExpr`, `transformValueExpr`, `buildTrueExpr`, `buildFalseExpr`, `copyExpr` |
| `MySQLPrintedExpression`（可选） | copy_expr：保存原表达式 SQL 字符串 |
| `MySQLEETReproducer` | 实现 `Reproducer`，复现 original vs transformed 差异 |

---

## 六、用例缩减（Test-Case Reduction）

EET-main 实现两阶段缩减（qcn_select_tester.cc:317–402，论文 Figure 5）：

### 6.1 阶段 1：缩减原查询与变换后查询

- 为每个可变换表达式分配 `component_id`（`set_compid_for_query`）
- 遍历 component_id，将对应表达式替换为 `const_bool(-1)`（NULL）或常量
- 若替换后仍能触发 bug，则保留替换；否则恢复
- 原查询与变换后查询需**同步**缩减（替换同一 component 时两边一致）

### 6.2 阶段 2：缩减变换后查询中的变换

- 对每个已变换的 component，尝试 `back_transform`（撤销变换）
- 若撤销后仍能触发 bug，则保持撤销；否则恢复变换
- 直至无法再缩减

### 6.3 SQLancer 对应

- SQLancer 的 `--use-reducer` 使用 delta-debugging，需为 EET 实现专用 `Reproducer`
- `MySQLEETReproducer.bugStillTriggers()`：重新执行 original 与 transformed，比较结果是否仍不一致
- 首版可实现基础 Reproducer，完整两阶段缩减可作为后续增强

**实现（本仓库，增强）**：

| 阶段 | 行为 |
|------|------|
| **阶段 1** | `MySQLEETComponentReducer`：对原查询 AST 做 DFS，将可变换节点（非 Rule7 叶子）逐个尝试替换为 `NULL`；每次替换后对缩减后的原查询用与 Oracle 相同的 `reductionSeed` 重新执行 `eqTransformRoot`；若 multiset 仍不一致则保留该缩减。若重算变换失败，则回退使用 Oracle 捕获的 `transformedAst` 副本。 |
| **阶段 2** | 在变换后查询 AST 上 DFS 查找 `MySQLCaseOperator`，尝试用 ELSE 或首个 THEN 分支整体替换该 CASE 节点；若仍触发不一致则保留。 |

启用 `--use-reducer` 时，在 `MySQLEETReproducer.bugStillTriggers()` 首次调用中执行上述两阶段（需 Oracle 传入 AST + `generator` + `targetTables` + `reductionSeed`）。全局 `StatementReducer` 仍对 statement 列表做 delta-debugging，EET 组件缩减与之叠加。

---

## 七、实现顺序建议

1. **MySQLEETTransformer**：Rule 1–2、3–6，`buildTrueExpr` / `buildFalseExpr`，`copyExpr`
2. **MySQLEETQueryTransformer**：`eqTransformQuery`、`eqTransformTableRef` 遍历与递归
3. **MySQLEETOracle**：整合生成、变换、执行、比较与 Reproducer
4. **MySQLOracleFactory**：**仅新增** `EET` 枚举项与 `create()`，不改既有项；**不**将 EET 写入默认 `oracles` 或 `QUERY_PARTITIONING` 组合（见 §1.3）
5. **ExpectedErrors**：在 `MySQLEETOracle` 内组合 `MySQLErrors`；**不**全局改写 `MySQLErrors` 静态内容除非确为全库共性（需谨慎评审）
6. **MySQLEETReproducer**（可选）：支持 `--use-reducer` 的用例缩减

---

## 八、MySQL 8.0 特别注意事项

- `gen.generateExpression(type)` 需保证与 `expr` 类型一致，避免隐式转换引起误报
- JOIN ON 条件：对 `MySQLJoin.getOnClause()` 做变换
- 子查询：FROM/JOIN 中若有子查询（如 `MySQLStringExpression` 等），需解析内部 SELECT 或制定递归策略
- `MySQLUnaryPostfixOperation`：正确区分 `IS NULL` 与 `IS NOT NULL` 的构造
- **只读与会话**：EET 路径以 SELECT 为主时，不改变表数据；与 §4.5 一致，保持单会话执行原查询与变换查询的可重复性即可

---

## 九、7 条变换规则与选择策略（论文 Table 2）

| No. | 类型 | 适用表达式 | 变换规则 |
|-----|------|------------|----------|
| 1 | Determined Boolean | bool_expr | `bool_expr → false_expr OR bool_expr` |
| 2 | Determined Boolean | bool_expr | `bool_expr → true_expr AND bool_expr` |
| 3 | Redundant Branch | expr | `expr → CASE WHEN false_expr THEN rand_expr ELSE expr END` |
| 4 | Redundant Branch | expr | `expr → CASE WHEN true_expr THEN expr ELSE rand_expr END` |
| 5 | Redundant Branch | expr | `expr → CASE WHEN rand_bool THEN copy_expr(expr) ELSE expr END` |
| 6 | Redundant Branch | expr | `expr → CASE WHEN rand_bool THEN expr ELSE copy_expr(expr) END` |
| 7 | 保守 | CASE-WHEN 不适用 | `expr → expr`（不变） |

**规则选择策略**：

- 布尔表达式：Rule 1–6 随机
- 非布尔且 CASE WHEN 适用：Rule 3–6 随机
- CASE WHEN 不适用：Rule 7

---

## 十、规则扩展与论文可扩展性（非首版必做）

论文指出 EET 易于扩展：新增变换规则时，应满足 **\(E \equiv E'\)**（在目标 SQL 方言语义下），并通过与现有规则相同的 AST 挂接点接入（如 `transformExpression` / `transformValueExpr` / `transformBoolExpr`）。扩展项包括：

- 新规则的**适用表达式类型**与随机选择权重；
- 与 **§3.6** 三值逻辑、`CASE` 语义是否冲突的评审；
- **§1.3** 隔离约束：避免修改既有 Oracle 类，仅在 EET 模块内增加分支或新类。

论文中提及的未来方向（如更多 JOIN 等价类）若落地，同样需单独评审与测试。

---

## 十一、设计完备性检查清单

| 检查项 | EET-main 依据 | 设计覆盖 |
|--------|---------------|----------|
| 形式化预言 Eq. 1 | 论文 §3 | ✓ §1.4 |
| 三值逻辑与 CASE（Eq. 13） | 论文 §3 | ✓ §3.6 |
| Rule 7 节点枚举 | 论文保守规则、表引用 | ✓ §3.3 |
| 7 条规则与选择策略 | 论文 Table 2、value_expr.cc、bool_expr.cc | ✓ 第三节 |
| AST 遍历顺序 | qcn_select_tester.cc eq_transform_query | ✓ 3.1 |
| 递归变换 | case_expr、bool_binop、in_query 等 | ✓ 3.2 |
| JOIN ON 条件 | grammar.hh joined_table.condition | ✓ 3.1（建议补充） |
| 子查询递归 | eq_transform_table_ref table_subquery | ✓ 3.1 |
| 复合查询 / CTE 范围 | 论文任意 SELECT | ✓ §3.1.2 |
| 仅 SELECT、DML 非本设计 | EET-main 多 tester | ✓ §2.5 |
| 类型一致性 | case_expr.cc 类型检查 | ✓ 3.5 |
| 随机生成重试 | value_expr/bool_expr while-try | ✓ 3.5 |
| const_bool 特例 | const_bool.cc | ✓ 3.3 |
| 结果 multiset 比较 | qcn_tester multiset | ✓ 4.3 |
| 比较假设与非确定性 | 实现层面 | ✓ §4.5、§八 |
| 二次验证 | qcn_test_without_initialization | ✓ 4.1 |
| 用例缩减两阶段 | minimize_testcase | ✓ 第六节 |
| 未变换子句范围 | ORDER BY、LIMIT、GROUP BY 列 | ✓ 3.1.1 |
| copy_expr / printed_expr | printed_expr.cc | ✓ 3.3 |
| ExpectedErrors | qcn_tester execute_query | ✓ 4.4 |
| 与既有 Oracle 隔离、默认行为不变 | §1.3 非回归约束 | ✓ 1.3、七 |
| 规则扩展原则 | 论文可扩展性 | ✓ §十 |

---

## 十二、实现对照（代码与测试清单）

与 EET 按模块开发计划（M0–M6）对齐，当前仓库中实现位置如下（便于评审与回归）。

### 12.1 源码包 `sqlancer.mysql.oracle.eet`

| 类 | 职责 |
|----|------|
| `EETMultisetComparator` | multiset 行比较、`rowKey` |
| `EETResultSetUtil` | 从 `SQLancerResultSet` 读取全行 |
| `EETQueryExecutor` / `EETDefaultQueryExecutor` | 执行 SQL 并返回行列表（可注入，便于单测） |
| `MySQLEETTransformer` | Rule 1–7、`const_bool`、`buildTrueExpr`/`buildFalseExpr`、`copyExpr` |
| `MySQLEETQueryTransformer` | `eqTransformQuery`（副本语义、遍历顺序见 §3.1） |
| `MySQLEETOracle` | `TestOracle`、二次验证、`MAX_PROCESS_ROW_NUM` |
| `MySQLEETReproducer` | `Reproducer.bugStillTriggers` |

### 12.2 相关扩展（非 eet 包）

| 位置 | 说明 |
|------|------|
| `sqlancer.mysql.ast.MySQLPrintedExpression` | `copy_expr` / printed SQL |
| `sqlancer.mysql.MySQLVisitor` / `MySQLToStringVisitor` / `MySQLExpectedValueVisitor` | `visit(MySQLPrintedExpression)` |
| `sqlancer.common.query.SQLancerResultSet` | `getColumnCount()` |
| `sqlancer.mysql.MySQLOracleFactory.EET` | 工厂注册（默认 `oracles` 仍不含 EET，见 §1.3） |

### 12.3 单元 / 集成测试

| 测试类 | 路径（相对 `sqlancer-main/test`） |
|--------|-------------------------------------|
| `EETMultisetComparatorTest` | `sqlancer/mysql/oracle/eet/` |
| `MySQLEETTransformerTest` | `sqlancer/mysql/oracle/eet/` |
| `MySQLEETQueryTransformerTest` | `sqlancer/mysql/oracle/eet/` |
| `MySQLEETReproducerTest` | `sqlancer/mysql/oracle/eet/` |
| `MySQLPrintedExpressionTest` | `sqlancer/mysql/ast/` |
| `TestMySQLEET` | `sqlancer/dbms/`（需 `MYSQL_ENV`，`--oracle EET`） |

### 12.4 运行命令示例

```bash
# 需先 mvn package；默认仍为 TLP_WHERE，显式指定 EET：
java -jar sqlancer-*.jar --num-threads 4 mysql --oracle EET
```

JDK 较新时若 JaCoCo 与字节码版本不兼容，构建/测试可加 `-Djacoco.skip=true`。

---

*本设计严格遵循 EET-main 的 SQL 生成与变换逻辑，7 条规则及选择策略与论文一致；EET 以独立 Oracle 与 opt-in 方式集成，不破坏既有 Test Oracle 行为。形式化预言、三值逻辑与范围边界见 §1.4、§3.6、§2.5、§3.1.2。*
