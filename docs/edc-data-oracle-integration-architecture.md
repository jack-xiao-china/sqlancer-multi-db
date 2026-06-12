# EDC 数据操作测试 Oracle 集成架构设计

## Executive Summary

本文档设计将 EDC (Equivalent Data Construction) 论文的核心算法完整集成到 SQLancer 框架，作为一个独立的 `EDC_DATA` Test Oracle。

**核心设计原则**：
1. **严格等价**：完整复用 EDC Python 项目的全部功能，不裁剪任何特性
2. **独立集成**：作为新 Oracle 注册，不影响现有 24 种 Oracle 的任何行为
3. **基础设施复用**：使用 SQLancer 的 GlobalState、Connection、Logger、ExpectedErrors 等基础设施
4. **多 DBMS 支持**：优先实现 MySQL/PostgreSQL/GaussDB-M/GaussDB-A（与现有 EDCBase 一致）

**关键设计决策**：
- 命名为 `EDC_DATA`（CLI 选项 `--oracle EDC_DATA`），区别于现有 `EDC`（Radar 风格）
- 新建 `src/sqlancer/common/oracle/edcdata/` 包，包含所有 EDC 数据操作测试逻辑
- Seed 文件（操作列表、类型列表）作为 Java Resources 打包，运行时加载
- 每个测试场景在独立数据库中运行，完全隔离
- 实现 Reproducer 支持，可重放 Bug 场景

---

## Architecture Overview

### Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           SQLancer Framework                              │
├─────────────────────────────────────────────────────────────────────────┤
│  TestOracle Interface                                                     │
│    ├─ NoRECOracle                                                         │
│    ├─ TLPWhereOracle                                                      │
│    ├─ EETOracle                                                           │
│    ├─ EDCBase (Radar-style)                                               │
│    └─ EDCDataOracleBase (NEW - EDC paper-style)                          │
│         ├─ MySQLEDCDataOracle                                             │
│         ├─ PostgresEDCDataOracle                                          │
│         ├─ GaussDBMEDCDataOracle                                          │
│         └─ GaussDBAEDCDataOracle                                          │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                    EDC_DATA Package (NEW)                                 │
├─────────────────────────────────────────────────────────────────────────┤
│  edcdata/                                                                 │
│    ├─ EDCDataOracleBase.java        (abstract base, core algorithm)      │
│    ├─ EDCDataTestScenario.java      (test scenario data class)           │
│    ├─ EDCDataOperationDefinition.java (operation metadata)              │
│    ├─ EDCDataExpressionBuilder.java (expression generation)             │
│    ├─ EDCDataQueryBuilder.java      (SQL query construction)            │
│    ├─ EDCDataTableBuilder.java      (table construction)                │
│    ├─ EDCDataResultComparator.java  (result comparison)                 │
│    ├─ EDCDataConfig.java            (configuration & seed loader)       │
│    └─ seeds/                                                             │
│         ├─ mysql/                                                         │
│         │    ├─ func.txt (340 operations)                                │
│         │    ├─ agg.txt (19 operations)                                  │
│         │    ├─ pred.txt (15 operations)                                 │
│         │    └─ type.txt (35 data types)                                 │
│         ├─ postgres/                                                      │
│         │    ├─ func.txt (2692 operations)                               │
│         │    ├─ agg.txt (17 operations)                                  │
│         │    ├─ pred.txt (14 operations)                                 │
│         │    └─ type.txt (12 data types)                                 │
│         ├─ gaussdbm/                                                      │
│         │    ├─ func.txt (340 operations, MySQL-compatible)             │
│         │    ├─ agg.txt (19 operations)                                  │
│         │    ├─ pred.txt (15 operations)                                 │
│         │    └─ type.txt (35 data types)                                 │
│         └─ gaussdba/                                                      │
│              ├─ func.txt (Oracle-compatible functions)                  │
│              ├─ agg.txt (Oracle-compatible aggregates)                  │
│              ├─ pred.txt (Oracle-compatible predicates)                 │
│              └─ type.txt (Oracle-compatible data types)                 │
└─────────────────────────────────────────────────────────────────────────┘
```

### Core Algorithm Flow (from EDC Python main.py)

```
for each test iteration:
  1. Select random operation type (AGGREGATE/FUNCTION/PREDICATE)
  2. Select random operation from seed file
  3. Generate random column types and names (1-5 columns)
  4. Build test expression: e.g., CONCAT(c0, c1), SUM(c0), c0 > c1
  5. Create original table t0 with random schema
  6. Insert 1-30 random rows into t0
  7. Validate test expression on t0 (skip if error)
  8. Create derived table t1 = SELECT (test_expr) AS c0, other_cols FROM t0
     - For AGGREGATE: include GROUP BY other_cols
  9. Get derived column type from information_schema
  10. For each of 50 SELECT iterations:
      a. Build base SELECT with WHERE/HAVING/ORDER BY
      b. Build derived SELECT by replacing test_expr with c0
      c. Execute both queries
      d. Compare sorted results
      e. If mismatch: report bug and break
```

---

## Detailed Component Design

### 1. EDCDataOracleBase

**Location**: `src/sqlancer/common/oracle/edcdata/EDCDataOracleBase.java`

**Purpose**: Abstract base class implementing the core EDC data operation testing algorithm.

**Key Responsibilities**:
- Load operation definitions and type definitions from seed files
- Generate test scenarios (operation + column types + test expression)
- Coordinate table construction, query generation, and result comparison
- Provide abstract hooks for DBMS-specific implementations

**Interface**:
```java
public abstract class EDCDataOracleBase<S extends SQLGlobalState<?, ?>> 
    implements TestOracle<S> {
    
    protected final S state;
    protected final EDCDataConfig config;
    protected final EDCDataExpressionBuilder exprBuilder;
    protected final EDCDataQueryBuilder queryBuilder;
    protected final EDCDataTableBuilder tableBuilder;
    protected final EDCDataResultComparator resultComparator;
    
    private Reproducer<S> reproducer;
    private String lastQueryString;
    
    public EDCDataOracleBase(S state, EDCDataConfig config) {
        this.state = state;
        this.config = config;
        this.exprBuilder = createExpressionBuilder(config);
        this.queryBuilder = createQueryBuilder(config);
        this.tableBuilder = createTableBuilder(config);
        this.resultComparator = new EDCDataResultComparator();
    }
    
    @Override
    public void check() throws Exception {
        // 1. Generate test scenario
        EDCDataTestScenario scenario = generateTestScenario();
        
        // 2. Create original table
        createOriginalTable(scenario);
        
        // 3. Insert random data (1-30 rows)
        insertRandomData(scenario);
        
        // 4. Validate test expression
        if (!validateTestExpression(scenario)) {
            throw new IgnoreMeException(); // Skip invalid scenarios
        }
        
        // 5. Create derived table
        createDerivedTable(scenario);
        
        // 6. Get derived column type
        String derivedType = getDerivedColumnType(scenario);
        
        // 7. Run SELECT iterations
        for (int i = 0; i < config.getSelectCount(); i++) {
            // 7a. Build base and derived queries
            String baseQuery = queryBuilder.buildBaseQuery(scenario);
            String derivedQuery = queryBuilder.buildDerivedQuery(scenario);
            
            // 7b. Execute both
            List<String> baseResult = executeQuery(baseQuery);
            List<String> derivedResult = executeQuery(derivedQuery);
            
            // 7c. Compare results
            if (!resultComparator.areEqual(baseResult, derivedResult)) {
                reproducer = new EDCDataReproducer<>(scenario, baseQuery, derivedQuery);
                lastQueryString = baseQuery;
                throw new AssertionError(formatBugReport(scenario, baseQuery, derivedQuery, 
                    baseResult, derivedResult));
            }
        }
    }
    
    // Abstract methods for DBMS-specific implementations
    protected abstract EDCDataExpressionBuilder createExpressionBuilder(EDCDataConfig config);
    protected abstract EDCDataQueryBuilder createQueryBuilder(EDCDataConfig config);
    protected abstract EDCDataTableBuilder createTableBuilder(EDCDataConfig config);
    
    @Override
    public Reproducer<S> getLastReproducer() {
        return reproducer;
    }
    
    @Override
    public String getLastQueryString() {
        return lastQueryString;
    }
}
```

### 2. EDCDataTestScenario

**Location**: `src/sqlancer/common/oracle/edcdata/EDCDataTestScenario.java`

**Purpose**: Data class representing a single test scenario.

**Fields**:
```java
public class EDCDataTestScenario {
    private final String operationName;           // e.g., "CONCAT", "SUM", ">"
    private final EDCDataOperationType opType;    // AGGREGATE, FUNCTION, PREDICATE
    private final List<String> columnNames;       // e.g., ["c0", "c1", "c2"]
    private final List<String> columnTypes;       // e.g., ["VARCHAR(10)", "INT", "DATE"]
    private final String testExpression;          // e.g., "CONCAT(c0, c1)"
    private final String baseTableName;           // e.g., "t0"
    private final String derivedTableName;        // e.g., "t1"
    private final String derivedColumnName;       // e.g., "c0"
    private final List<String> otherColumnNames;  // Columns not in test expression
    private final List<String> otherColumnTypes;  // Types of other columns
}
```

### 3. EDCDataOperationDefinition

**Location**: `src/sqlancer/common/oracle/edcdata/EDCDataOperationDefinition.java`

**Purpose**: Metadata for a single operation (function, aggregate, or predicate).

**Fields**:
```java
public class EDCDataOperationDefinition {
    private final String name;              // e.g., "CONCAT", "SUM", ">"
    private final EDCDataOperationType type; // FUNCTION, AGGREGATE, PREDICATE
    private final int minArity;             // Minimum number of arguments
    private final int maxArity;             // Maximum number of arguments (-1 for variadic)
    private final String returnType;        // Expected return type (or "ANY" for dynamic)
    private final List<String> argTypes;    // Expected argument types (or empty for ANY)
}
```

**Loading**: Loaded from seed files at initialization:
```java
public static List<EDCDataOperationDefinition> loadFromSeedFile(String resourcePath) {
    // Read lines from resource file
    // Parse each line into operation definition
    // Return list of definitions
}
```

### 4. EDCDataExpressionBuilder

**Location**: `src/sqlancer/common/oracle/edcdata/EDCDataExpressionBuilder.java`

**Purpose**: Generate test expressions and WHERE conditions with depth-controlled AST.

**Key Methods** (equivalent to EDC Python's `ExprGenerator`):
```java
public class EDCDataExpressionBuilder {
    private final EDCDataConfig config;
    
    public String generateExprOnColumn(String table, List<String> columnNames, 
                                       List<String> columnTypes, int depth) {
        if (depth == 1) {
            // Return COLUMN or CONSTANT
            return Randomly.fromOptions("COLUMN", "CONSTANT");
        } else if (depth > 1) {
            // Include comparison and logical operators
            // =, !=, >, <=, AND, OR
        } else if (depth > 2) {
            // Include CASE and SUBQUERY
            // CASE WHEN ... THEN ... ELSE ... END
            // expr IN (SELECT ... FROM ...)
        }
    }
    
    public String generateRandomValue(String dataType) {
        // Generate random value for 40+ data types
        // INT: random int in range
        // VARCHAR: random string
        // DATE: random date
        // JSON: random JSON object
        // etc.
    }
    
    public String generateExprConstant(String type) {
        // Generate constant expression: (value + value) or (value * value)
    }
}
```

**Implementation Notes**:
- Depth control: EDC Python uses depth 1-5, with CASE/SUBQUERY only at depth > 2
- Value generation: Must support all 40+ data types from seed files
- Type-aware: Expression generation considers column types to avoid incompatibility

### 5. EDCDataQueryBuilder

**Location**: `src/sqlancer/common/oracle/edcdata/EDCDataQueryBuilder.java`

**Purpose**: Build base and derived SQL queries for each operation type.

**Key Methods** (equivalent to EDC Python's `SQLGenerator`):
```java
public class EDCDataQueryBuilder {
    private final EDCDataConfig config;
    private final EDCDataExpressionBuilder exprBuilder;
    
    public String generateAggSelect(EDCDataTestScenario scenario) {
        // SELECT expr, other_cols FROM t0
        // WHERE condition GROUP BY other_cols
        // HAVING having_cond
        String baseQuery = String.format(
            "SELECT %s, %s FROM %s WHERE %s GROUP BY %s HAVING %s",
            scenario.getTestExpression(),
            String.join(", ", scenario.getOtherColumnNames()),
            scenario.getBaseTableName(),
            exprBuilder.generateExprOnColumn(...),
            String.join(", ", scenario.getOtherColumnNames()),
            exprBuilder.generateExprOnColumn(...)
        );
        
        String derivedQuery = baseQuery
            .replace(scenario.getBaseTableName(), scenario.getDerivedTableName())
            .replace(scenario.getTestExpression(), scenario.getDerivedColumnName());
        
        return derivedQuery;
    }
    
    public String generateFuncSelect(EDCDataTestScenario scenario) {
        // SELECT cols FROM t0 WHERE condition [ORDER BY cols]
        String baseQuery = String.format(
            "SELECT %s FROM %s WHERE %s",
            String.join(", ", selectedCols),
            scenario.getBaseTableName(),
            exprBuilder.generateExprOnColumn(...)
        );
        
        if (Randomly.getBoolean()) {
            baseQuery += " ORDER BY " + String.join(", ", orderCols);
        }
        
        String derivedQuery = baseQuery
            .replace(scenario.getBaseTableName(), scenario.getDerivedTableName())
            .replace(scenario.getTestExpression(), scenario.getDerivedColumnName());
        
        return derivedQuery;
    }
    
    public String generatePredSelect(EDCDataTestScenario scenario) {
        // SELECT cols FROM t0 WHERE (test_expr AND/OR condition) [ORDER BY cols]
        String baseQuery = String.format(
            "SELECT %s FROM %s WHERE (%s) %s %s",
            String.join(", ", selectedCols),
            scenario.getBaseTableName(),
            scenario.getTestExpression(),
            Randomly.fromOptions("AND", "OR"),
            exprBuilder.generateExprOnColumn(...)
        );
        
        if (Randomly.getBoolean()) {
            baseQuery += " ORDER BY " + String.join(", ", orderCols);
        }
        
        String derivedQuery = baseQuery
            .replace(scenario.getBaseTableName(), scenario.getDerivedTableName())
            .replace(scenario.getTestExpression(), scenario.getDerivedColumnName());
        
        return derivedQuery;
    }
}
```

### 6. EDCDataTableBuilder

**Location**: `src/sqlancer/common/oracle/edcdata/EDCDataTableBuilder.java`

**Purpose**: Construct original and derived tables.

**Key Methods**:
```java
public class EDCDataTableBuilder {
    private final S state;
    private final EDCDataConfig config;
    
    public void createOriginalTable(EDCDataTestScenario scenario) throws SQLException {
        // CREATE TABLE t0 (c0 TYPE0, c1 TYPE1, ...)
        String createSQL = String.format(
            "CREATE TABLE %s (%s)",
            scenario.getBaseTableName(),
            buildColumnDefinitions(scenario.getColumnNames(), scenario.getColumnTypes())
        );
        executeSQL(createSQL);
    }
    
    public void createDerivedTable(EDCDataTestScenario scenario) throws SQLException {
        // CREATE TABLE t1 AS SELECT (test_expr) AS c0, other_cols FROM t0 [GROUP BY other_cols]
        String createSQL;
        if (scenario.getOpType() == EDCDataOperationType.AGGREGATE) {
            createSQL = String.format(
                "CREATE TABLE %s AS SELECT (%s) AS %s, %s FROM %s GROUP BY %s",
                scenario.getDerivedTableName(),
                scenario.getTestExpression(),
                scenario.getDerivedColumnName(),
                String.join(", ", scenario.getOtherColumnNames()),
                scenario.getBaseTableName(),
                String.join(", ", scenario.getOtherColumnNames())
            );
        } else {
            createSQL = String.format(
                "CREATE TABLE %s AS SELECT (%s) AS %s, %s FROM %s",
                scenario.getDerivedTableName(),
                scenario.getTestExpression(),
                scenario.getDerivedColumnName(),
                String.join(", ", scenario.getOtherColumnNames()),
                scenario.getBaseTableName()
            );
        }
        executeSQL(createSQL);
    }
    
    public String getDerivedColumnType(EDCDataTestScenario scenario) throws SQLException {
        // Query information_schema to get type of derived column
        // SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS 
        // WHERE TABLE_NAME = 't1' AND COLUMN_NAME = 'c0'
        String query = String.format(
            "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '%s' AND COLUMN_NAME = '%s'",
            scenario.getDerivedTableName(),
            scenario.getDerivedColumnName()
        );
        return executeQuery(query).get(0);
    }
    
    public void insertRandomData(EDCDataTestScenario scenario) throws SQLException {
        // INSERT INTO t0 (c0, c1, ...) VALUES (random_val, random_val, ...)
        int rowCount = Randomly.getInteger(1, 30);
        for (int i = 0; i < rowCount; i++) {
            String insertSQL = String.format(
                "INSERT INTO %s (%s) VALUES (%s)",
                scenario.getBaseTableName(),
                String.join(", ", scenario.getColumnNames()),
                generateRandomValues(scenario.getColumnTypes())
            );
            executeSQL(insertSQL);
        }
    }
}
```

### 7. EDCDataResultComparator

**Location**: `src/sqlancer/common/oracle/edcdata/EDCDataResultComparator.java`

**Purpose**: Compare query results with EDC's sorting-based approach.

**Key Methods**:
```java
public class EDCDataResultComparator {
    
    public boolean areEqual(List<String> result1, List<String> result2) {
        // Sort both results before comparison
        List<String> sorted1 = new ArrayList<>(result1);
        List<String> sorted2 = new ArrayList<>(result2);
        Collections.sort(sorted1);
        Collections.sort(sorted2);
        
        // Compare element by element
        if (sorted1.size() != sorted2.size()) {
            return false;
        }
        for (int i = 0; i < sorted1.size(); i++) {
            if (!normalizeValue(sorted1.get(i)).equals(normalizeValue(sorted2.get(i)))) {
                return false;
            }
        }
        return true;
    }
    
    private String normalizeValue(String value) {
        // Remove trailing zeros: "1.000" -> "1"
        // Handle NULL: "NULL" -> null
        // Trim whitespace
        if (value == null || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        return value.replaceAll("[\\.]0+$", "").trim();
    }
}
```

### 8. EDCDataConfig

**Location**: `src/sqlancer/common/oracle/edcdata/EDCDataConfig.java`

**Purpose**: Configuration and seed file loader.

**Key Methods**:
```java
public class EDCDataConfig {
    private final List<EDCDataOperationDefinition> functions;
    private final List<EDCDataOperationDefinition> aggregates;
    private final List<EDCDataOperationDefinition> predicates;
    private final List<String> dataTypes;
    
    private final int maxLoop = 10000000;  // From EDC config.py
    private final int testColumnCount = 2; // Max columns in test expression
    private final int otherColumnCount = 2; // Max additional columns
    private final int selectCount = 50;    // Queries per test iteration
    
    public static EDCDataConfig loadForDBMS(String dbmsName) {
        // Load seed files from resources/edc-data-seeds/{dbms}/
        String basePath = "/edc-data-seeds/" + dbmsName + "/";
        
        List<EDCDataOperationDefinition> functions = 
            EDCDataOperationDefinition.loadFromSeedFile(basePath + "func.txt");
        List<EDCDataOperationDefinition> aggregates = 
            EDCDataOperationDefinition.loadFromSeedFile(basePath + "agg.txt");
        List<EDCDataOperationDefinition> predicates = 
            EDCDataOperationDefinition.loadFromSeedFile(basePath + "pred.txt");
        List<String> dataTypes = loadDataTypes(basePath + "type.txt");
        
        return new EDCDataConfig(functions, aggregates, predicates, dataTypes);
    }
}
```

### 9. DBMS-Specific Implementations

**MySQLEDCDataOracle**:
```java
public class MySQLEDCDataOracle extends EDCDataOracleBase<MySQLGlobalState> {
    public MySQLEDCDataOracle(MySQLGlobalState state) {
        super(state, EDCDataConfig.loadForDBMS("mysql"));
    }
    
    @Override
    protected EDCDataExpressionBuilder createExpressionBuilder(EDCDataConfig config) {
        return new MySQLEDCDataExpressionBuilder(config);
    }
    
    @Override
    protected EDCDataQueryBuilder createQueryBuilder(EDCDataConfig config) {
        return new MySQLEDCDataQueryBuilder(config);
    }
    
    @Override
    protected EDCDataTableBuilder createTableBuilder(EDCDataConfig config) {
        return new MySQLEDCDataTableBuilder(state, config);
    }
}
```

**PostgresEDCDataOracle**, **GaussDBMEDCDataOracle**, **GaussDBAEDCDataOracle**: Similar pattern.

### 10. Oracle Registration

**MySQLOracleFactory.java**:
```java
public enum MySQLOracleFactory implements OracleFactory<MySQLGlobalState> {
    // ... existing oracles ...
    EDC_DATA {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws Exception {
            return new MySQLEDCDataOracle(globalState);
        }
    }
}
```

**PostgresOracleFactory.java**, **GaussDBMOracleFactory.java**, **GaussDBAOracleFactory.java**: Similar pattern.

### 11. MainOptions Configuration

**MainOptions.java**:
```java
@Parameter(names = {"--edc-data-select-count"}, 
           description = "Number of SELECT queries per EDC_DATA test iteration (default: 50)")
private int edcDataSelectCount = 50;

@Parameter(names = {"--edc-data-test-column-count"}, 
           description = "Maximum columns in test expression for EDC_DATA (default: 2)")
private int edcDataTestColumnCount = 2;

@Parameter(names = {"--edc-data-other-column-count"}, 
           description = "Maximum additional columns for EDC_DATA (default: 2)")
private int edcDataOtherColumnCount = 2;
```

---

## Seed File Management

### File Structure

```
src/main/resources/edc-data-seeds/
  ├─ mysql/
  │    ├─ func.txt
  │    ├─ agg.txt
  │    ├─ pred.txt
  │    └─ type.txt
  ├─ postgres/
  │    ├─ func.txt
  │    ├─ agg.txt
  │    ├─ pred.txt
  │    └─ type.txt
  ├─ gaussdbm/
  │    ├─ func.txt (MySQL-compatible)
  │    ├─ agg.txt
  │    ├─ pred.txt
  │    └─ type.txt
  └─ gaussdba/
       ├─ func.txt (Oracle-compatible)
       ├─ agg.txt
       ├─ pred.txt
       └─ type.txt
```

### Seed File Format

Each line contains one operation name:
```
func.txt:
CONCAT
SUM
AVG
+
-
*
/

agg.txt:
SUM
AVG
COUNT
MAX
MIN

pred.txt:
>
<=
<>
=
AND
OR
LIKE

type.txt:
INT
VARCHAR
DATE
BOOLEAN
```

### Loading Strategy

1. **Build-time**: Copy seed files from EDC Python project to `src/main/resources/edc-data-seeds/`
2. **Runtime**: Load via `ClassLoader.getResourceAsStream()`
3. **Parsing**: Read line by line, trim whitespace, skip empty lines and comments
4. **Caching**: Load once per DBMS at initialization, cache in `EDCDataConfig`

### Seed File Sources

| DBMS | Source | Operations | Types |
|------|--------|------------|-------|
| MySQL | EDC Python `seed/mysql/` | 340 func, 19 agg, 15 pred | 35 |
| PostgreSQL | EDC Python `seed/postgres/` | 2692 func, 17 agg, 14 pred | 12 |
| GaussDB-M | MySQL-compatible (same as MySQL) | 340 func, 19 agg, 15 pred | 35 |
| GaussDB-A | Oracle-compatible (manual curation) | ~200 func, ~15 agg, ~15 pred | ~20 |

---

## Error Handling Strategy

### Integration with ExpectedErrors

```java
public class EDCDataOracleBase<S extends SQLGlobalState<?, ?>> {
    protected final ExpectedErrors errors = new ExpectedErrors();
    
    public EDCDataOracleBase(S state, EDCDataConfig config) {
        // ... initialization ...
        
        // Add common expected errors for data operation testing
        errors.add("Data too long for column");
        errors.add("Truncated incorrect");
        errors.add("Out of range value");
        errors.add("Division by zero");
        errors.add("Invalid use of group function");
        errors.add("Unknown column");
        errors.add("Table doesn't exist");
        
        // DBMS-specific errors added in subclass constructors
    }
    
    protected List<String> executeQuery(String query) throws SQLException {
        SQLQueryAdapter q = new SQLQueryAdapter(query, errors);
        try (SQLancerResultSet rs = q.executeAndGet(state)) {
            if (rs == null) {
                throw new IgnoreMeException(); // Expected error
            }
            List<String> result = new ArrayList<>();
            while (rs.next()) {
                result.add(rs.getString(1)); // Assuming single-column result
            }
            return result;
        } catch (Exception e) {
            if (e instanceof IgnoreMeException) {
                throw (IgnoreMeException) e;
            }
            if (errors.errorIsExpected(e.getMessage())) {
                throw new IgnoreMeException();
            }
            throw new AssertionError(query, e);
        }
    }
}
```

### Common Expected Errors by DBMS

**MySQL/GaussDB-M**:
- `Data too long for column`
- `Truncated incorrect DOUBLE value`
- `Out of range value for column`
- `Division by zero`
- `Invalid use of group function`
- `Unknown column`
- `Table doesn't exist`
- `Incorrect datetime value`
- `Incorrect date value`
- `Incorrect time value`

**PostgreSQL/GaussDB-A**:
- `value too long for type`
- `division by zero`
- `invalid input syntax`
- `operator does not exist`
- `function does not exist`
- `aggregate function not allowed`

---

## Configuration and CLI

### New Command-Line Options

```bash
java -jar sqlancer.jar mysql --oracle EDC_DATA \
    --edc-data-select-count 100 \
    --edc-data-test-column-count 3 \
    --edc-data-other-column-count 3
```

### Option Descriptions

| Option | Default | Description |
|--------|---------|-------------|
| `--edc-data-select-count` | 50 | Number of SELECT queries per test iteration |
| `--edc-data-test-column-count` | 2 | Maximum columns in test expression |
| `--edc-data-other-column-count` | 2 | Maximum additional columns |
| `--edc-data-max-loop` | 10000000 | Maximum test iterations (for debugging) |

### Help Text

```
EDC_DATA Options:
  --edc-data-select-count=<int>       Number of SELECT queries per EDC_DATA test iteration (default: 50)
  --edc-data-test-column-count=<int>  Maximum columns in test expression for EDC_DATA (default: 2)
  --edc-data-other-column-count=<int> Maximum additional columns for EDC_DATA (default: 2)
  --edc-data-max-loop=<int>           Maximum test iterations for EDC_DATA (default: 10000000)
```

---

## Testing Strategy

### Unit Tests

**Location**: `test/sqlancer/common/oracle/edcdata/`

1. **EDCDataExpressionBuilderTest**
   - Test depth-controlled expression generation
   - Test value generation for all 40+ data types
   - Test type-aware expression generation

2. **EDCDataQueryBuilderTest**
   - Test AGGREGATE query generation
   - Test FUNCTION query generation
   - Test PREDICATE query generation
   - Test ORDER BY randomization

3. **EDCDataTableBuilderTest**
   - Test CREATE TABLE generation
   - Test CREATE TABLE AS SELECT generation
   - Test INSERT generation
   - Test type inference from information_schema

4. **EDCDataResultComparatorTest**
   - Test sorting-based comparison
   - Test NULL handling
   - Test floating point normalization
   - Test trailing zero removal

### Integration Tests

**Location**: `test/sqlancer/{dbms}/oracle/edcdata/`

1. **MySQLEDCDataOracleTest**
   - Run EDC_DATA on MySQL for 100 iterations
   - Verify no crashes
   - Verify bug detection (inject known bugs)

2. **PostgresEDCDataOracleTest**
   - Run EDC_DATA on PostgreSQL for 100 iterations
   - Verify no crashes
   - Verify bug detection (inject known bugs)

3. **GaussDBMEDCDataOracleTest**
   - Run EDC_DATA on GaussDB-M for 100 iterations
   - Verify no crashes

4. **GaussDBAEDCDataOracleTest**
   - Run EDC_DATA on GaussDB-A for 100 iterations
   - Verify no crashes

### Regression Tests

**Purpose**: Ensure EDC_DATA does not affect other oracles.

1. **OracleIsolationTest**
   - Run all existing oracles (NoREC, TLP, EET, etc.)
   - Verify they still work correctly
   - Verify no shared state pollution

2. **ConcurrentOracleTest**
   - Run EDC_DATA alongside other oracles in multi-threaded mode
   - Verify no race conditions
   - Verify no resource conflicts

---

## Migration Plan

### Phase 1: Core Infrastructure (Week 1)

1. Create `src/sqlancer/common/oracle/edcdata/` package
2. Implement `EDCDataOracleBase`, `EDCDataTestScenario`, `EDCDataOperationDefinition`
3. Implement `EDCDataExpressionBuilder`, `EDCDataQueryBuilder`, `EDCDataTableBuilder`
4. Implement `EDCDataResultComparator`, `EDCDataConfig`
5. Write unit tests for all core components
6. **Deliverable**: Core framework ready, no DBMS implementation yet

### Phase 2: Seed File Integration (Week 1)

1. Copy EDC Python seed files to `src/main/resources/edc-data-seeds/`
2. Implement seed file loader in `EDCDataConfig`
3. Verify all seed files load correctly
4. **Deliverable**: Seed files integrated, ready for DBMS implementation

### Phase 3: MySQL Implementation (Week 2)

1. Implement `MySQLEDCDataOracle`, `MySQLEDCDataExpressionBuilder`, `MySQLEDCDataQueryBuilder`, `MySQLEDCDataTableBuilder`
2. Register `EDC_DATA` in `MySQLOracleFactory`
3. Add `--edc-data-*` options to `MainOptions`
4. Write integration tests for MySQL
5. Run EDC_DATA on MySQL for 1000 iterations, verify no crashes
6. **Deliverable**: MySQL EDC_DATA fully functional

### Phase 4: PostgreSQL Implementation (Week 2)

1. Implement `PostgresEDCDataOracle`, `PostgresEDCDataExpressionBuilder`, `PostgresEDCDataQueryBuilder`, `PostgresEDCDataTableBuilder`
2. Register `EDC_DATA` in `PostgresOracleFactory`
3. Write integration tests for PostgreSQL
4. Run EDC_DATA on PostgreSQL for 1000 iterations, verify no crashes
5. **Deliverable**: PostgreSQL EDC_DATA fully functional

### Phase 5: GaussDB Implementation (Week 3)

1. Implement `GaussDBMEDCDataOracle` (reuse MySQL implementation)
2. Implement `GaussDBAEDCDataOracle` (Oracle-compatible)
3. Create GaussDB-A seed files (Oracle-compatible functions/types)
4. Register `EDC_DATA` in `GaussDBMOracleFactory` and `GaussDBAOracleFactory`
5. Write integration tests for GaussDB-M and GaussDB-A
6. Run EDC_DATA on both for 1000 iterations, verify no crashes
7. **Deliverable**: All 4 DBMS implementations complete

### Phase 6: Regression Testing & Documentation (Week 3)

1. Run all existing oracles alongside EDC_DATA
2. Verify no impact on existing oracles
3. Write user documentation for EDC_DATA
4. Update `docs/edc-data-integration-guide.md`
5. **Deliverable**: Fully tested, documented, ready for release

---

## Risk Assessment

### Risk 1: Seed File Incompatibility

**Description**: EDC Python seed files may contain operations not supported by SQLancer's DBMS implementations.

**Mitigation**:
- Validate each operation before use (execute `SELECT operation(col) FROM table LIMIT 1`)
- Skip invalid operations with `IgnoreMeException`
- Log skipped operations for debugging

**Likelihood**: Medium
**Impact**: Low (graceful degradation)

### Risk 2: Type Incompatibility

**Description**: Random type combinations may be invalid (e.g., `SUM(VARCHAR)`).

**Mitigation**:
- Validate test expression before creating derived table
- Skip invalid scenarios with `IgnoreMeException`
- Use ExpectedErrors to filter known type errors

**Likelihood**: High
**Impact**: Low (graceful degradation)

### Risk 3: Performance Overhead

**Description**: EDC_DATA creates two tables per test scenario, may be slow.

**Mitigation**:
- Limit test iterations per database (default: 50 SELECTs)
- Drop tables after each test scenario
- Use `--edc-data-select-count` to control iteration count

**Likelihood**: Low
**Impact**: Medium (slower testing)

### Risk 4: False Positives

**Description**: Result comparison may report false positives due to floating point precision, NULL handling, etc.

**Mitigation**:
- Normalize values before comparison (remove trailing zeros, handle NULL)
- Use ExpectedErrors to filter known non-bugs
- Implement Reproducer to confirm bugs before reporting

**Likelihood**: Medium
**Impact**: Medium (noise in bug reports)

### Risk 5: Resource Conflicts

**Description**: EDC_DATA may conflict with other oracles (shared database connections, table names).

**Mitigation**:
- Use unique table names per test scenario (e.g., `t0_{scenario_id}`, `t1_{scenario_id}`)
- Run each test scenario in isolated database
- No shared state between EDC_DATA and other oracles

**Likelihood**: Low
**Impact**: Low (isolation by design)

---

## Conclusion

This architecture design provides a complete, independent integration of EDC's data operation testing methodology into SQLancer. The key strengths:

1. **Complete Feature Preservation**: All EDC functionality (3 operation types, 40+ data types, seed files, expression generation, query building, result comparison) is preserved without cuts.

2. **Independent Integration**: EDC_DATA is a completely separate Oracle, registered alongside existing oracles, with no shared state or side effects.

3. **Infrastructure Reuse**: Leverages SQLancer's mature infrastructure (GlobalState, Connection, Logger, ExpectedErrors, Reproducer) while maintaining EDC's core algorithm.

4. **Multi-DBMS Support**: Designed for MySQL, PostgreSQL, GaussDB-M, GaussDB-A, with extensibility for additional DBMS.

5. **Robust Error Handling**: Integrates with ExpectedErrors to filter false positives, uses Reproducer to confirm bugs.

6. **Comprehensive Testing**: Unit tests, integration tests, and regression tests ensure correctness and isolation.

The implementation plan spans 3 weeks, with clear milestones and deliverables for each phase. Risk mitigation strategies address the most likely issues (type incompatibility, performance, false positives).

This design enables SQLancer to detect a new class of bugs (data operation implementation bugs) that existing oracles cannot find, significantly expanding its bug detection coverage.
