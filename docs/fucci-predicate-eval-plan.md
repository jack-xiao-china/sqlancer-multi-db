# Fucci Oracle P0 增强方案：谓词求值 + 精确范围锁

> 基于 Troc vs SQLancer v3 对比分析，修复两个 P0 级差距

## Context

SQLancer v2.2.0 的 Fucci MT Oracle 在 SELECT 预测时**不做 WHERE 过滤**——将 `beforeView` 中所有可见行的所有列值扁平化为 `predictedData`，导致：
1. 只能在 SER 级别比较最终状态，无法逐语句比较 SELECT 结果
2. 锁分析只能处理 `WHERE col = N` 模式（正则提取），范围查询退化为表级锁

**预期收益**：
- MT Oracle 检测能力提升 ~3x（从"最终状态比较"升级为"逐语句 SELECT 结果比较"）
- 减少 30-50% 的 Undecided 判定（精确范围锁替代保守表级锁）

## 总体架构

```
┌──────────────────────────────────────────────────────────────────────┐
│                        PredicateEvaluator (NEW)                      │
│  JSqlParser 解析 WHERE → ExpressionEvalVisitor 逐行求值              │
│  支持: =, !=, <, >, <=, >=, AND, OR, NOT, IS NULL,                  │
│        IN, BETWEEN, LIKE, +, -, *, /, CASE, 函数回退                 │
│  保守策略: 求值失败 → 包含所有行（避免误报）                           │
└─────────────────────────────┬────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
    ┌─────────────────┐ ┌───────────┐ ┌──────────────────┐
    │  FucciMTOracle  │ │ FucciMVCC │ │ FucciLockAnalyzer│
    │  SELECT预测过滤  │ │ Analyzer  │ │ 精确范围锁检测    │
    │  +列投影        │ │ 视图元数据 │ │ matchedRows集合  │
    └─────────────────┘ └───────────┘ └──────────────────┘
```

---

## Phase 1: 谓词求值引擎（基础，无外部依赖）

### 1.1 `src/sqlancer/fucci/eval/ColumnResolver.java` (NEW, ~60行)

列名到 `Object[]` 数组索引的映射器。

```java
public final class ColumnResolver {
    private final Map<String, Integer> nameToIndex;  // 小写归一化
    private final String tableName;

    public ColumnResolver(FucciTable table);
    public ColumnResolver(String tableName, List<String> columnNames);

    /** 解析列名 → 索引。支持 "col" 和 "table.col" 形式。未找到返回 -1。*/
    public int resolve(String columnName);
}
```

**设计要点**：大小写不敏感（MySQL 默认行为 + PG 未引用标识符折叠）。`table.col` 形式处理限定引用。

### 1.2 `src/sqlancer/fucci/eval/ExpressionEvalVisitor.java` (NEW, ~350行)

实现 JSqlParser 的 `ExpressionVisitor` 接口，对单行数据求值表达式 AST。

**支持的表达式类型**：

| 类别 | JSqlParser 类 | 求值逻辑 |
|------|-------------|---------|
| **比较** | `EqualsTo, NotEqualsTo, MinorThan, GreaterThan, MinorThanEquals, GreaterThanEquals` | `compare(left, right)` 跨类型数值比较 |
| **逻辑** | `AndExpression, OrExpression, NotExpression` | SQL 三值逻辑（NULL AND FALSE = FALSE） |
| **NULL** | `IsNullExpression` | `inner == null`，处理 `isNot()` 标志 |
| **IN** | `InExpression` | 遍历 `ExpressionList`，任一相等即 true |
| **BETWEEN** | `Between` | `compare(col, lo) >= 0 && compare(col, hi) <= 0` |
| **LIKE** | `LikeExpression` | SQL LIKE 模式转 Java 正则（`%`→`.*`, `_`→`.`） |
| **算术** | `Addition, Subtraction, Multiplication, Division` | `toNumber()` 归一化后运算，除零保护 |
| **字面量** | `LongValue, DoubleValue, StringValue, NullValue` | 直接返回对应 Java 类型 |
| **列引用** | `Column` | 通过 `ColumnResolver.resolve()` 查找数组索引 |
| **括号** | `Parenthesis` | 递归求值内部表达式 |
| **CASE** | `CaseExpression` | 顺序求值 `whenClauses`，返回首个匹配 |
| **符号** | `SignedExpression` | 负号取反 |
| **函数/子查询** | `Function, SubSelect` | **设置 `evaluationFailed = true`**（太依赖 DBMS） |

**核心方法 `compare(Object left, Object right)`**：

```java
// JDBC 返回 Integer/Long/Double/BigDecimal/String
// JSqlParser 解析为 LongValue/DoubleValue/StringValue
// 跨类型数值比较: 都转 double 比较
if (l instanceof Number && r instanceof Number) {
    return Double.compare(((Number) l).doubleValue(), ((Number) r).doubleValue());
}
// 同类型: 直接 compareTo
// 兜底: toString().compareTo()
```

**回退策略**：遇到未支持的表达式类型 → 设 `evaluationFailed = true` → 调用方视为"包含该行"。

### 1.3 `src/sqlancer/fucci/eval/PredicateEvaluator.java` (NEW, ~100行)

高层门面，封装解析+批量求值。

```java
public final class PredicateEvaluator {
    /** 对 data 中所有行求值 whereClause，返回满足条件的 rowId 集合。
     *  解析或求值失败 → 返回全部 rowId（保守）。*/
    public static Set<Integer> evaluate(String whereClause, ColumnResolver resolver,
                                         Map<Integer, Object[]> data);

    /** 单行求值。失败 → 返回 true（保守）。*/
    public static boolean evaluateSingle(String whereClause, ColumnResolver resolver,
                                          Object[] rowData);
}
```

### 1.4 `test/sqlancer/fucci/eval/PredicateEvaluatorTest.java` (NEW, ~150行)

覆盖 12 个测试场景：简单等值、范围比较、AND/OR 组合、IS NULL、IN、BETWEEN、LIKE、跨类型数值比较（Integer vs Long）、解析失败回退、NULL 谓词回退、NOT 表达式、算术表达式。

---

## Phase 2: 视图列元数据 + SELECT 谓词过滤

**目标**：让 `beforeView` 携带列名信息，`FucciMTOracle` 用 PredicateEvaluator 过滤 SELECT 结果。

### 2.1 `src/sqlancer/fucci/mvcc/View.java` (MODIFY)

新增字段和方法：

```java
private String[] columnNames;  // 列名数组，索引对应 Object[] 位置
private String tableName;      // 视图所属表名

public View(String tableName, String[] columnNames);  // 新构造函数
public String[] getColumnNames();
public String getTableName();
public int getColumnIndex(String name);  // 按名称查找列索引

// 修改 copy() 保留 columnNames 和 tableName
```

**现有代码兼容**：无参构造函数和所有现有方法不变。`columnNames` 和 `tableName` 可选（null 时退化为现有行为）。

### 2.2 `src/sqlancer/fucci/mvcc/MVCCSimulator.java` (MODIFY)

新增方法 `buildViewWithRowIds()`，保留 rowId 映射：

```java
/** 返回 tableName → (rowId → visibleRowData)，保留原始 rowId */
public Map<String, Map<Integer, Object[]>> buildViewWithRowIds(
        FucciTransaction curTx, VisibilityRule rule);

/** 获取指定表的版本链（供范围锁评估使用）*/
public Map<Integer, List<Version>> getVersionChains(String tableName);
```

**现有 `buildView()` 保持不变**，标记 `@Deprecated`。

### 2.3 `src/sqlancer/fucci/mvcc/FucciMVCCAnalyzer.java` (MODIFY)

1. 构造函数新增 `FucciSchema schema` 参数
2. 新增 `resolveColumnNames(tableName)` 从 schema 获取列名
3. 修改 `processSelect()`：
   - 调用 `simulator.buildViewWithRowIds()` 代替 `buildView()`
   - 从 SQL 提取目标表名
   - 用目标表的行数据 + 列名创建带元数据的 View
4. 新增 `convertViewToView(viewData, targetTable, columnNames)` 重载

```java
private void processSelect(FucciTransaction tx, FucciTxStatement stmt, VisibilityRule rule) {
    // ... snapshot creation ...
    String targetTable = extractTableName(stmt.getTxQueryAdapter().getQueryString());
    Map<String, Map<Integer, Object[]>> viewData = simulator.buildViewWithRowIds(tx, rule);
    String[] colNames = resolveColumnNames(targetTable);
    View beforeView = convertViewToView(viewData, targetTable, colNames);
    stmt.setBeforeView(beforeView);
    stmt.setAfterView(beforeView.copy());
}
```

### 2.4 `src/sqlancer/fucci/oracle/FucciMTOracle.java` (MODIFY)

替换 `simulateMVCCExecution()` 中的 predictedData 构建逻辑：

```java
// 旧: 扁平化所有行所有列
for (int rowId : beforeView.getRowIds()) {
    for (Object val : rowData) { predictedData.add(val); }
}

// 新: WHERE过滤 + 列投影
private List<Object> buildPredictedData(FucciTxStatement stmt) {
    View view = stmt.getBeforeView();
    Set<Integer> matchingRows = filterByPredicate(stmt, view);  // Phase 1 谓词求值
    int[] projectedCols = resolveProjectedColumns(stmt, view);  // JSqlParser 解析 SELECT 列

    for (int rowId : matchingRows) {
        Object[] rowData = view.getRow(rowId);
        if (rowData == null || view.isDeleted(rowId)) continue;
        if (projectedCols != null) {
            for (int idx : projectedCols) predictedData.add(rowData[idx]);
        } else {
            for (Object val : rowData) predictedData.add(val);  // SELECT * 回退
        }
    }
}
```

`filterByPredicate()`：
- 有 predicate + columnNames → 调用 `PredicateEvaluator.evaluate()`
- 无 predicate（`SELECT * FROM t`）→ 返回所有非删除行
- 无 columnNames → 返回所有非删除行（保守回退）

`resolveProjectedColumns()`：
- 用 JSqlParser 解析 SELECT 列列表
- `SELECT c0, c1` → `[0, 1]`
- `SELECT *` 或解析失败 → `null`（回退到全部列）

**`compareWithSimulation()` 修改**：
- 当前仅 SER 比较 SELECT 结果（因无 WHERE 过滤）
- 新增谓词过滤后，所有隔离级别都可比较 SELECT 结果
- **保守策略**：初始保持 SER-only，通过 `FucciOptions` 开关控制是否扩展到所有级别

---

## Phase 3: 精确范围锁检测

**目标**：用 PredicateEvaluator 确定语句影响的精确行集合，替代正则提取和表级锁回退。

### 3.1 `src/sqlancer/fucci/lock/LockObject.java` (MODIFY)

1. 新增 `LockObjectType.RANGE` 枚举值
2. 新增字段 `Set<Integer> matchedRows`（谓词匹配的行 ID 集合）
3. 新增构造函数 `LockObject(String tableName, Set<Integer> matchedRows)`
4. 在 `isCompatibleWith()` 中处理 RANGE 类型：

```java
case RANGE:
    if (other.objectType == RANGE) return !rowsOverlap(matchedRows, other.matchedRows);
    if (other.objectType == ROW) return !matchedRows.contains(other.rowId);
    // ...
```

### 3.2 `src/sqlancer/fucci/transaction/FucciTxStatement.java` (MODIFY)

增强 `extractInvolvedRowIds()`：新增带 View + ColumnResolver 参数的重载。

```java
public void extractInvolvedRowIds(View view, ColumnResolver resolver) {
    String predicate = getPredicate();
    if (predicate != null && view != null && resolver != null) {
        Set<Integer> matched = PredicateEvaluator.evaluate(predicate, resolver, view.getData());
        if (!matched.isEmpty()) {
            this.involvedRowIds = matched.stream().mapToInt(Integer::intValue).toArray();
            return;
        }
    }
    // 回退到正则提取
    extractInvolvedRowIds();
}
```

### 3.3 `src/sqlancer/fucci/lock/FucciLockAnalyzer.java` (MODIFY)

1. 修改 `inferLockObject()`：
   - `rowIds.length == 1` → ROW 锁（不变）
   - `rowIds.length > 1` → RANGE 锁（带 matchedRows 集合）
   - `rowIds == null` → TABLE 锁（不变）

2. 新增 `resolveInvolvedRowIds(stmt, currentView, schema)` 方法，在锁推断前用谓词求值填充 rowIds

3. 修改 `analyzeAndAcquire()` 签名新增 `View currentView, FucciSchema schema` 参数：
   - 先调用 `resolveInvolvedRowIds()`
   - 再执行现有锁推断+冲突检测流程

---

## Phase 4: 集成验证

### 验证步骤

| 步骤 | 命令 | 验证内容 |
|------|------|---------|
| Phase 1 完成 | `mvn compile -q` | 零错误零警告 |
| Phase 1 完成 | `mvn test -pl . -Dtest=PredicateEvaluatorTest` | 12 个测试全过 |
| Phase 2 完成 | `mvn compile -q` | 零错误零警告 |
| Phase 2 完成 | `mvn test` | 现有全部测试通过（无回归） |
| Phase 3 完成 | `mvn compile -q` | 零错误零警告 |
| Phase 3 完成 | `mvn test` | 现有全部测试通过 |
| 全部完成 | 更新 `release_notes.md` + `pom.xml` 版本号 |

---

## 文件清单

| 文件 | 操作 | Phase | 行数估算 |
|------|------|:-----:|--------:|
| `src/sqlancer/fucci/eval/ColumnResolver.java` | NEW | 1 | ~60 |
| `src/sqlancer/fucci/eval/ExpressionEvalVisitor.java` | NEW | 1 | ~350 |
| `src/sqlancer/fucci/eval/PredicateEvaluator.java` | NEW | 1 | ~100 |
| `test/sqlancer/fucci/eval/PredicateEvaluatorTest.java` | NEW | 1 | ~150 |
| `src/sqlancer/fucci/mvcc/View.java` | MODIFY | 2 | +30 |
| `src/sqlancer/fucci/mvcc/MVCCSimulator.java` | MODIFY | 2 | +40 |
| `src/sqlancer/fucci/mvcc/FucciMVCCAnalyzer.java` | MODIFY | 2 | +50/-10 |
| `src/sqlancer/fucci/oracle/FucciMTOracle.java` | MODIFY | 2 | +100/-15 |
| `src/sqlancer/fucci/lock/LockObject.java` | MODIFY | 3 | +40 |
| `src/sqlancer/fucci/transaction/FucciTxStatement.java` | MODIFY | 3 | +30 |
| `src/sqlancer/fucci/lock/FucciLockAnalyzer.java` | MODIFY | 3 | +40/-5 |
| `docs/release_notes.md` | MODIFY | 4 | +5 |
| `pom.xml` | MODIFY | 4 | ~1 |

**总计**：4 新建 + 9 修改，~940 行新增，~30 行删除

## 实施顺序

```
Phase 1 (PredicateEvaluator)  ← 无依赖，最先执行
    ↓
Phase 2 (视图元数据 + SELECT 过滤)  ← 依赖 Phase 1
    ↓
Phase 3 (精确范围锁)  ← 依赖 Phase 1 + 2
    ↓
Phase 4 (集成验证 + 版本更新)
```

## 风险评估

| 风险 | 等级 | 缓解措施 |
|------|:----:|---------|
| 跨类型数值比较 (Integer vs Long vs BigDecimal) | 中 | `Number.doubleValue()` 归一化 + 单元测试覆盖 |
| SQL 三值 NULL 逻辑错误 | 中 | 严格遵循 SQL 标准 NULL 语义 + 测试覆盖 |
| JSqlParser 不支持的表达式类型 | 低 | `evaluationFailed=true` 保守回退 |
| `buildViewWithRowIds()` 影响现有调用方 | 低 | 保留旧 `buildView()` 方法 |
| 列名与 Object[] 索引顺序不匹配 | 中 | 在 `initializeVersionChains()` 加断言验证 |
| 非 SER 级别 SELECT 比较产生误报 | 中 | 通过 FucciOptions 开关控制，初始仅 SER |
| `-failOnWarning` 编译严格模式 | 低 | 无未使用 import/变量 |
