# RADAR EDC Oracle MySQL Integration - Detailed Design & Development Plan

## Phase 1: MySQL EDC Integration (First Implementation)

---

## 1. Architecture Analysis

### 1.1 RADAR vs SQLancer MySQL Comparison

| Component | RADAR | SQLancer | Compatibility |
|-----------|-------|----------|---------------|
| `MySQLGlobalState` | Simple, extends `SQLGlobalState` | Extended with PQS support, compression flag | **Compatible** (same base) |
| `MySQLSchema` | 5 data types (INT, VARCHAR, FLOAT, DOUBLE, DECIMAL) | 17 data types (including DATE, TIME, DATETIME, BIT, ENUM, SET, JSON) | **SQLancer richer** |
| `MySQLExpressionGenerator` | 11 Actions | 14 Actions + NoREC/TLP/CERT interfaces | **SQLancer richer** |
| `MySQLVisitor` | Basic toString | Full visitor with more AST types | **SQLancer richer** |
| `MySQLErrors` | Simple error list | Comprehensive error handling | **SQLancer richer** |

**Conclusion**: SQLancer MySQL implementation is more complete. RADAR components can directly use SQLancer infrastructure.

### 1.2 Integration Strategy

**Approach**: Copy RADAR EDC core logic, adapt to SQLancer infrastructure

| RADAR Component | Integration Action | SQLancer Replacement |
|-----------------|-------------------|---------------------|
| `EDCBase` | Copy + Adapt | Use SQLancer's `SQLConnection`, `ExpectedErrors` |
| `MySQLEDCOracle` | Copy + Adapt | Use SQLancer's `MySQLExpressionGenerator` |
| `MySQLEDC` | Copy + Adapt | Use SQLancer's `MySQLGlobalState` |

---

## 2. Detailed Design

### 2.1 File Structure

```
sqlancer/src/sqlancer/
├── common/oracle/
│   └── EDCBase.java                 # NEW: Core EDC base class
├── mysql/
│   ├── MySQLOracleFactory.java      # MODIFY: Add EDC enum
│   ├── MySQLEDC.java                # NEW: Raw DB construction helper
│   └── oracle/
│       └── MySQLEDCOracle.java      # NEW: MySQL EDC oracle implementation
```

### 2.2 EDCBase Core Class Design

**Location**: `src/sqlancer/common/oracle/EDCBase.java`

```java
package sqlancer.common.oracle;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sqlancer.IgnoreMeException;
import sqlancer.SQLGlobalState;
import sqlancer.StateLogger;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;

/**
 * EDC Oracle Base Class - Equivalent Database Construction Oracle
 *
 * Principle: Create "raw database" without constraints (NOT NULL, UNIQUE, FK, CHECK, GENERATED),
 * then compare query results between original DB (optimized) and raw DB (non-optimized).
 * Result mismatch indicates potential optimizer bug.
 *
 * @param <S> GlobalState type for the specific DBMS
 */
public abstract class EDCBase<S extends SQLGlobalState<?, ?>> implements TestOracle<S> {

    protected final S originalState;
    protected S equivalentState;  // Raw database state
    protected final ExpectedErrors errors = new ExpectedErrors();
    protected final StateLogger logger;
    protected String queryString;

    public EDCBase(S originalState) {
        this.originalState = originalState;
        this.logger = originalState.getLogger();
    }

    /**
     * Main check method - generates query and compares results
     */
    @Override
    public void check() throws Exception {
        // 1. Generate random SELECT query
        queryString = generateQueryString(originalState);
        logger.writeCurrent(queryString);

        // 2. Execute on original DB (optimized)
        List<String> optimizedResult = getOptimizedResult(originalState);

        // 3. Execute on raw DB (non-optimized)
        List<String> nonOptimizedResult = getNonOptimizedResult(equivalentState);

        // 4. Compare results - mismatch indicates bug
        ComparatorHelper.assumeResultSetsAreEqual(
            optimizedResult, nonOptimizedResult,
            queryString, List.of(equivalentState.getDatabaseName()),
            originalState
        );
    }

    /**
     * Create equivalent state (raw DB) before checking
     */
    public void constructEquivalentState() {
        try {
            originalState.updateSchema();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.equivalentState = constructEquivalentState(originalState);
    }

    /**
     * Close equivalent state connection after checking
     */
    public void closeEquState() throws SQLException {
        if (equivalentState != null) {
            equivalentState.getConnection().close();
        }
    }

    // ==================== Abstract Methods (DBMS-specific) ====================

    /**
     * Obtain table schemas with constraint metadata
     * @return Map<TableName, Map<ColumnName, List<MetadataElements>>>
     */
    public abstract Map<String, Map<String, List<String>>> obtainTableSchemas(S state) throws SQLException;

    /**
     * Construct equivalent state (raw DB without constraints)
     */
    public abstract S constructEquivalentState(S state);

    /**
     * Generate random SELECT query string
     */
    public abstract String generateQueryString(S state);

    // ==================== Helper Methods ====================

    /**
     * Execute query and get result from optimized (original) DB
     */
    protected List<String> getOptimizedResult(S state) throws SQLException {
        List<String> resultSet = new ArrayList<>();
        SQLQueryAdapter q = new SQLQueryAdapter(queryString, errors);
        SQLancerResultSet result = null;
        try {
            result = q.executeAndGet(state);
            if (result == null) {
                throw new IgnoreMeException();
            }
            ResultSetMetaData metaData = result.getRs().getMetaData();
            int columns = metaData.getColumnCount();
            while (result.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    String resultTemp = result.getString(i);
                    if (resultTemp != null) {
                        resultTemp = resultTemp.replaceAll("[\\.]0+$", "");
                    }
                    row.append(resultTemp).append(",");
                }
                resultSet.add(row.toString());
            }
        } catch (Exception e) {
            if (e instanceof IgnoreMeException) {
                throw e;
            }
            if (errors.errorIsExpected(e.getMessage())) {
                throw new IgnoreMeException();
            }
            throw new AssertionError(queryString, e);
        } finally {
            if (result != null && !result.isClosed()) {
                result.close();
            }
        }
        return resultSet;
    }

    /**
     * Execute query and get result from non-optimized (raw) DB
     */
    protected List<String> getNonOptimizedResult(S state) throws SQLException {
        return getOptimizedResult(state);
    }

    /**
     * Check if database structure is new (for diversity)
     */
    public boolean containsNewDatabaseStructure(Set<Integer> databaseStructureSet) throws SQLException {
        Map<String, Map<String, List<String>>> tableSchemas = obtainTableSchemas(originalState);
        // Calculate hash based on constraint presence
        int hashcode = calculateStructureHash(tableSchemas);
        if (databaseStructureSet.contains(hashcode)) {
            return false;
        } else {
            databaseStructureSet.add(hashcode);
            return true;
        }
    }

    private int calculateStructureHash(Map<String, Map<String, List<String>>> tableSchemas) {
        // Hash based on constraint types present
        int hash = 0;
        for (Map<String, List<String>> tableSchema : tableSchemas.values()) {
            for (List<String> metadata : tableSchema.values()) {
                String metaStr = String.join(" ", metadata).toUpperCase();
                if (metaStr.contains("NOT NULL") || metaStr.contains("UNIQUE") ||
                    metaStr.contains("FOREIGN KEY") || metaStr.contains("PRIMARY") ||
                    metaStr.contains("CHECK") || metaStr.contains("GENERATED")) {
                    hash += metaStr.hashCode();
                }
            }
        }
        return hash;
    }
}
```

### 2.3 MySQLEDCOracle Implementation

**Location**: `src/sqlancer/mysql/oracle/MySQLEDCOracle.java`

```java
package sqlancer.mysql.oracle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.beust.jcommander.Strings;

import sqlancer.Randomly;
import sqlancer.common.oracle.EDCBase;
import sqlancer.common.oracle.TestOracle;
import sqlancer.mysql.MySQLEDC;
import sqlancer.mysql.MySQLErrors;
import sqlancer.mysql.MySQLGlobalState;
import sqlancer.mysql.MySQLSchema;
import sqlancer.mysql.MySQLSchema.MySQLColumn;
import sqlancer.mysql.MySQLSchema.MySQLTable;
import sqlancer.mysql.ast.MySQLColumnReference;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

/**
 * MySQL EDC Oracle Implementation
 *
 * Detects MySQL optimizer bugs by comparing query results between:
 * - Original DB: Has constraints (NOT NULL, UNIQUE, FK, CHECK, GENERATED)
 * - Raw DB: No constraints, pure data copy
 */
public class MySQLEDCOracle extends EDCBase<MySQLGlobalState> implements TestOracle<MySQLGlobalState> {

    public MySQLEDCOracle(MySQLGlobalState originalState) {
        super(originalState);
        MySQLErrors.addExpressionErrors(errors);
    }

    @Override
    public Map<String, Map<String, List<String>>> obtainTableSchemas(MySQLGlobalState state) throws SQLException {
        Map<String, Map<String, List<String>>> tableSchema = new HashMap<>();
        List<String> foreignKeyList = new ArrayList<>();
        Pattern patternForColumnSet = Pattern.compile("\\((.*?)\\)");
        Pattern patternForColumn = Pattern.compile("`([^`]*?)`");
        boolean needComma;

        for (MySQLTable table : state.getSchema().getDatabaseTablesWithoutViews()) {
            String tableName = table.getName();
            try (Statement statement = state.getConnection().createStatement()) {
                ResultSet resultSet = statement.executeQuery("SHOW CREATE TABLE " + tableName);
                String createTable = null;
                if (resultSet.next()) {
                    createTable = resultSet.getString("Create Table");
                }
                if (createTable != null) {
                    tableSchema.put(tableName, new HashMap<>());
                    String[] createTableLines = createTable.split("\n");

                    // Handle column-related metadata
                    Map<String, String> generatedColumns = new HashMap<>();
                    String columnName = table.getColumns().get(0).getName();
                    needComma = createTableLines[1].startsWith("  `" + columnName + "`");

                    for (int i = 0; i < table.getColumns().size(); i++) {
                        columnName = table.getColumns().get(i).getName();
                        String columnRef = "`" + columnName + "`";
                        int offset = 5;
                        if (!needComma) {
                            columnRef = columnName;
                            offset = 3;
                        }
                        int start = createTableLines[i + 1].indexOf(columnRef) + offset;
                        String metadataString = createTableLines[i + 1].substring(start,
                            createTableLines[i + 1].length() - 1);

                        if (metadataString.contains("GENERATED")) {
                            generatedColumns.put(columnRef, metadataString);
                        } else {
                            tableSchema.get(tableName).put(columnRef, List.of(metadataString));
                        }
                    }

                    // Handle generated columns
                    for (String generatedColumn : generatedColumns.keySet()) {
                        String expression = generatedColumns.get(generatedColumn);
                        Matcher matcher = patternForColumn.matcher(expression);
                        List<String> metaElements = new ArrayList<>();
                        while (matcher.find()) {
                            String columnRef = matcher.group(1);
                            if (needComma) {
                                columnRef = "`" + columnRef + "`";
                            }
                            if (tableSchema.get(tableName).containsKey(columnRef)) {
                                metaElements.add(Strings.join(" ", tableSchema.get(tableName).get(columnRef)));
                            }
                        }
                        Collections.sort(metaElements);
                        metaElements.add(0, "GENERATED");
                        tableSchema.get(tableName).put(generatedColumn, metaElements);
                    }

                    // Handle table-related metadata (constraints)
                    for (int i = table.getColumns().size() + 1; i < createTableLines.length - 1; i++) {
                        if (createTableLines[i].contains("ENGINE")) {
                            tableSchema.get(tableName).put("CONFIGURATION",
                                List.of(createTableLines[i].substring(1)));
                            break;
                        }
                        // Parse constraints: UNIQUE, PRIMARY, FOREIGN KEY, CHECK, KEY
                        parseConstraint(createTableLines[i], tableName, tableSchema, foreignKeyList, needComma);
                    }
                }
            }
        }

        // Handle foreign key relationships
        if (!foreignKeyList.isEmpty()) {
            for (int i = 0; i < foreignKeyList.size() / 4; i++) {
                List<String> foreignExpression = new ArrayList<>();
                foreignExpression.add("FOREIGN KEY");
                String source = Strings.join(" ",
                    tableSchema.get(foreignKeyList.get(i)).get(foreignKeyList.get(i + 1)));
                String target = Strings.join(" ",
                    tableSchema.get(foreignKeyList.get(i + 2)).get(foreignKeyList.get(i + 3)));
                foreignExpression.add(source);
                foreignExpression.add(target);
                tableSchema.get(foreignKeyList.get(i)).put("FOREIGN KEY" + i, foreignExpression);
            }
        }
        return tableSchema;
    }

    private void parseConstraint(String line, String tableName,
            Map<String, Map<String, List<String>>> tableSchema,
            List<String> foreignKeyList, boolean needComma) {
        Pattern patternForColumnSet = Pattern.compile("\\((.*?)\\)");
        Pattern patternForColumn = Pattern.compile("`([^`]*?)`");

        Matcher matcherColumnSet = patternForColumnSet.matcher(line);
        List<String> compositeColumns = new ArrayList<>();
        if (matcherColumnSet.find()) {
            String columns = matcherColumnSet.group(1);
            if (columns.contains(",")) {
                Matcher matcherColumn = patternForColumn.matcher(columns);
                while (matcherColumn.find()) {
                    String columnRef = matcherColumn.group(1);
                    if (needComma) {
                        columnRef = "`" + columnRef + "`";
                    }
                    compositeColumns.add(columnRef);
                }
            } else {
                compositeColumns.add(matcherColumnSet.group(1));
            }
        }

        String metadataType = "";
        if (line.contains("UNIQUE")) {
            metadataType = "UNIQUE";
        } else if (line.contains("PRIMARY")) {
            metadataType = "PRIMARY";
        } else if (line.contains("FOREIGN")) {
            // Handle foreign key
            foreignKeyList.add(tableName);
            foreignKeyList.add(compositeColumns.get(0));
            String references = line.substring(line.indexOf("REFERENCES") + 11);
            Matcher matcher = patternForColumn.matcher(references);
            if (matcher.find()) {
                foreignKeyList.add(matcher.group(1));
            }
            if (matcher.find()) {
                if (needComma) {
                    foreignKeyList.add("`" + matcher.group(1) + "`");
                } else {
                    foreignKeyList.add(matcher.group(1));
                }
            }
            return;
        } else if (line.contains("CHECK")) {
            metadataType = "CHECK";
            // Parse CHECK constraint
            int start = line.indexOf(metadataType) + 5;
            String metadataString = line.substring(start, line.length() - 1);
            Matcher matcher = patternForColumn.matcher(metadataString);
            List<String> metaElements = new ArrayList<>();
            while (matcher.find()) {
                String columnRef = matcher.group(1);
                if (needComma) {
                    columnRef = "`" + columnRef + "`";
                }
                if (tableSchema.get(tableName).containsKey(columnRef)) {
                    metaElements.add(Strings.join(" ", tableSchema.get(tableName).get(columnRef)));
                }
            }
            Collections.sort(metaElements);
            metaElements.add(0, metadataType);
            tableSchema.get(tableName).put(metadataType + line.hashCode(), metaElements);
            return;
        } else if (line.contains("KEY")) {
            metadataType = "KEY";
        }

        List<String> columnRelatedMetadata = new ArrayList<>();
        for (String columnRef : compositeColumns) {
            if (tableSchema.get(tableName).containsKey(columnRef)) {
                columnRelatedMetadata.addAll(tableSchema.get(tableName).get(columnRef));
            }
        }
        Collections.sort(columnRelatedMetadata);
        columnRelatedMetadata.add(0, metadataType);
        tableSchema.get(tableName).put(metadataType + line.hashCode(), columnRelatedMetadata);
    }

    @Override
    public MySQLGlobalState constructEquivalentState(MySQLGlobalState state) {
        try {
            MySQLEDC edc = new MySQLEDC(state);
            return edc.createRawDB();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String generateQueryString(MySQLGlobalState state) {
        // Get random non-empty tables
        MySQLSchema.MySQLTables randomTables = state.getSchema().getRandomTableNonEmptyTables();
        List<MySQLColumn> columns = randomTables.getColumns();

        // Use SQLancer's ExpressionGenerator (richer than RADAR)
        MySQLExpressionGenerator generator = new MySQLExpressionGenerator(state).setColumns(columns);
        MySQLExpression randomWhereCondition = generator.generateExpression();

        // Build SELECT query
        List<MySQLExpression> tableRefs = randomTables.getTables().stream()
            .map(MySQLTableReference::new)
            .collect(Collectors.toList());

        MySQLSelect select = new MySQLSelect();
        select.setSelectType(MySQLSelect.SelectType.ALL);
        select.setFetchColumns(
            Randomly.nonEmptySubset(randomTables.getColumns()).stream()
                .map(c -> new MySQLColumnReference(c, null))
                .collect(Collectors.toList())
        );
        select.setFromList(tableRefs);
        select.setWhereClause(randomWhereCondition);

        return MySQLVisitor.asString(select);
    }
}
```

### 2.4 MySQLEDC Helper Class

**Location**: `src/sqlancer/mysql/MySQLEDC.java`

```java
package sqlancer.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import sqlancer.SQLConnection;

/**
 * MySQL Raw Database Construction Helper
 *
 * Creates "raw database" without constraints for EDC Oracle testing.
 * Raw DB contains only pure data - no NOT NULL, UNIQUE, FK, CHECK, GENERATED constraints.
 */
public class MySQLEDC {

    private final MySQLGlobalState state;

    public MySQLEDC(MySQLGlobalState state) {
        this.state = state;
    }

    /**
     * Create raw database (without constraints)
     * @return GlobalState for the raw database
     */
    public MySQLGlobalState createRawDB() throws SQLException {
        state.getState().logStatement("========Create RawDB========");

        // 1. Build connection
        String url = String.format(
            "jdbc:mysql://%s:%d?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true",
            state.getOptions().getHost(),
            state.getOptions().getPort()
        );
        Connection conn = DriverManager.getConnection(
            url,
            state.getOptions().getUserName(),
            state.getOptions().getPassword()
        );
        Statement statement = conn.createStatement();

        // 2. Create raw database with "_raw" suffix
        String rawDB = state.getDatabaseName() + "_raw";
        state.getState().logStatement("DROP DATABASE IF EXISTS " + rawDB);
        state.getState().logStatement("CREATE DATABASE " + rawDB);
        statement.execute("DROP DATABASE IF EXISTS " + rawDB);
        statement.execute("CREATE DATABASE " + rawDB);

        // 3. Switch to raw database
        state.getState().logStatement("USE " + rawDB);
        statement.execute("USE " + rawDB);

        // 4. Copy tables WITHOUT constraints
        for (MySQLSchema.MySQLTable table : state.getSchema().getDatabaseTablesWithoutViews()) {
            copyTableWithoutConstraints(statement, table);
        }

        state.getState().logStatement("========Finish Create========");
        statement.close();

        // 5. Create and return GlobalState for raw DB
        MySQLGlobalState rawState = new MySQLGlobalState();
        rawState.setDatabaseName(rawDB);
        rawState.setConnection(new SQLConnection(conn));
        rawState.setMainOptions(state.getOptions());
        rawState.setRandomly(state.getRandomly());
        rawState.setDbmsSpecificOptions(state.getDbmsSpecificOptions());

        return rawState;
    }

    /**
     * Copy table to raw DB without constraints
     * - Removes NOT NULL
     * - Removes UNIQUE
     * - Removes PRIMARY KEY
     * - Removes FOREIGN KEY
     * - Removes CHECK constraints
     * - Removes GENERATED columns (replaced with regular columns)
     */
    private void copyTableWithoutConstraints(Statement statement, MySQLSchema.MySQLTable table) throws SQLException {
        String tableName = table.getName();

        // Get column info from original DB
        StringBuilder createTableBuilder = new StringBuilder();
        createTableBuilder.append("CREATE TABLE ");
        createTableBuilder.append(tableName);
        createTableBuilder.append("(");

        ResultSet resultSet = statement.executeQuery(
            "SHOW FULL COLUMNS FROM " + state.getDatabaseName() + "." + tableName
        );

        while (resultSet.next()) {
            String columnName = resultSet.getString("Field");
            String columnType = resultSet.getString("Type");
            String collation = resultSet.getString("Collation");

            createTableBuilder.append(columnName);
            createTableBuilder.append(" ").append(columnType);

            // Add collation if non-default
            if (collation != null && !collation.equals("utf8mb4_0900_ai_ci")) {
                createTableBuilder.append(" COLLATE \"").append(collation).append("\"");
            }

            // NO constraints added - pure data columns only
            createTableBuilder.append(",");
        }

        String createTableString = createTableBuilder.toString();
        createTableString = createTableString.substring(0, createTableString.length() - 1); // Remove last comma
        createTableString += ")";

        // Create table in raw DB
        state.getState().logStatement(createTableString);
        statement.execute(createTableString);

        // Copy data from original table
        String copyData = String.format(
            "INSERT INTO %s SELECT * FROM %s.%s",
            tableName,
            state.getDatabaseName(),
            tableName
        );
        state.getState().logStatement(copyData);
        statement.execute(copyData);
    }
}
```

### 2.5 MySQLOracleFactory Update

**Location**: `src/sqlancer/mysql/MySQLOracleFactory.java` (Modification)

```java
// Add EDC enum to existing MySQLOracleFactory

public enum MySQLOracleFactory implements OracleFactory<MySQLGlobalState> {
    // ... existing oracles ...

    EDC {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            return new MySQLEDCOracle(globalState);
        }

        @Override
        public boolean requiresAllTablesToContainRows() {
            return true;  // EDC needs tables with data
        }
    },

    // ... existing oracles ...
}
```

### 2.6 MySQLOptions Update

**Location**: `src/sqlancer/mysql/MySQLOptions.java` (Modification)

```java
// Update --oracle parameter description

@Parameter(names = "--oracle", description = "Specifies which test oracle should be used, " +
    "Options: [AGGREGATE, CERT, CODDTEST, DISTINCT, DQE, DQP, EDC, EET, FUZZER, GROUP_BY, HAVING, " +
    "NOREC, PQS, QUERY_PARTITIONING, TLP_WHERE]")
public List<MySQLOracleFactory> oracles = Arrays.asList(MySQLOracleFactory.QUERY_PARTITIONING);
```

---

## 3. Development Plan - Phase 1

### 3.1 Task Breakdown

| Task ID | Task Description | Duration | Dependencies |
|---------|------------------|----------|--------------|
| T1.1 | Create EDCBase.java in common/oracle | 2 hours | None |
| T1.2 | Create MySQLEDC.java helper class | 2 hours | T1.1 |
| T1.3 | Create MySQLEDCOracle.java implementation | 3 hours | T1.1, T1.2 |
| T1.4 | Update MySQLOracleFactory.java | 0.5 hours | T1.3 |
| T1.5 | Update MySQLOptions.java description | 0.5 hours | T1.4 |
| T1.6 | Create unit tests | 2 hours | T1.1-T1.5 |
| T1.7 | Integration testing | 3 hours | T1.1-T1.6 |
| **Total** | **Phase 1 Complete** | **13 hours (~2 days)** | |

### 3.2 Development Steps

#### Step 1: EDCBase Creation (T1.1)

```
Action: Create new file
File: src/sqlancer/common/oracle/EDCBase.java
Source: Adapted from radar/src/sqlancer/common/oracle/EDCBase.java

Key adaptations:
- Use sqlancer.SQLGlobalState instead of radar.GlobalState
- Use sqlancer.common.query.SQLQueryAdapter instead of radar's
- Use sqlancer.StateLogger instead of radar's
- Keep same logic: check(), obtainTableSchemas(), constructEquivalentState()
```

#### Step 2: MySQLEDC Helper (T1.2)

```
Action: Create new file
File: src/sqlancer/mysql/MySQLEDC.java
Source: Adapted from radar/src/sqlancer/mysql/MySQLEDC.java

Key adaptations:
- Use sqlancer.mysql.MySQLGlobalState
- Use sqlancer.SQLConnection
- Use sqlancer.mysql.MySQLSchema
- MySQL schema read compatible with SQLancer's richer schema
```

#### Step 3: MySQLEDCOracle Implementation (T1.3)

```
Action: Create new file
File: src/sqlancer/mysql/oracle/MySQLEDCOracle.java
Source: Adapted from radar/src/sqlancer/mysql/oracle/MySQLEDCOracle.java

Key adaptations:
- Use sqlancer.mysql.gen.MySQLExpressionGenerator (richer version)
- Use sqlancer.mysql.MySQLVisitor
- Use sqlancer.mysql.MySQLErrors.addExpressionErrors()
- Keep same schema parsing logic (SHOW CREATE TABLE)
```

#### Step 4: OracleFactory Registration (T1.4, T1.5)

```
Action: Modify existing files
Files:
- src/sqlancer/mysql/MySQLOracleFactory.java (add EDC enum)
- src/sqlancer/mysql/MySQLOptions.java (update description)

No breaking changes - only additions
```

---

## 4. Testing Plan - Phase 1

### 4.1 Unit Tests

**Test File**: `test/sqlancer/mysql/oracle/MySQLEDCOracleTest.java`

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `testEDCOracleCreation` | Create EDC oracle instance | Successful creation |
| `testSchemaParsing` | Parse table schemas with constraints | Correct metadata extraction |
| `testRawDBCreation` | Create raw DB without constraints | Tables copied, constraints removed |
| `testQueryGeneration` | Generate random SELECT query | Valid SQL syntax |
| `testResultComparison` | Compare optimized vs non-optimized results | Correct comparison logic |
| `testErrorHandling` | Handle expected MySQL errors | Errors caught, no false positives |
| `testGeneratedColumn` | Handle generated columns | GC replaced with regular columns |
| `testForeignKey` | Handle foreign key constraints | FK removed in raw DB |
| `testCheckConstraint` | Handle CHECK constraints | CHECK removed in raw DB |
| `testNotNullConstraint` | Handle NOT NULL constraints | NOT NULL removed in raw DB |

### 4.2 Integration Tests

**Test Environment**:
- MySQL 8.4 database
- Connection: localhost:3306, user: tpcc, password: Taurus@123

**Test Commands**:

```bash
# Test 1: Basic EDC oracle run
java -jar sqlancer.jar --dbms mysql --oracle EDC \
  --host localhost --port 3306 \
  --username tpcc --password Taurus@123 \
  --max-generated-databases 10

# Test 2: EDC with other oracles combined
java -jar sqlancer.jar --dbms mysql --oracle EDC,TLP_WHERE,NOREC \
  --host localhost --port 3306 \
  --username tpcc --password Taurus@123 \
  --max-generated-databases 10

# Test 3: EDC only with timeout
java -jar sqlancer.jar --dbms mysql --oracle EDC \
  --host localhost --port 3306 \
  --username tpcc --password Taurus@123 \
  --timeout 60
```

### 4.3 Isolation Verification Tests

| Test ID | Test Description | Verification Criteria |
|---------|------------------|----------------------|
| I1 | Run existing TLP oracle | Same behavior as before EDC addition |
| I2 | Run existing NoREC oracle | Same behavior as before EDC addition |
| I3 | Run existing PQS oracle | Same behavior as before EDC addition |
| I4 | Run QUERY_PARTITIONING composite | Same behavior as before EDC addition |
| I5 | Run EDC alone | New EDC functionality works |
| I6 | Run EDC + TLP + NoREC | All oracles work in combination |

### 4.4 Bug Detection Test

**Test Scenario**: Create database with constraints that might trigger optimizer bugs

```sql
-- Create test database
CREATE DATABASE test_edc_constraints;
USE test_edc_constraints;

-- Create table with various constraints
CREATE TABLE t1 (
    c0 INT NOT NULL,
    c1 VARCHAR(100) UNIQUE,
    c2 INT CHECK (c2 > 0),
    c3 INT GENERATED ALWAYS AS (c0 + c2) STORED,
    PRIMARY KEY (c0)
);

CREATE TABLE t2 (
    c0 INT,
    c1 INT,
    FOREIGN KEY (c0) REFERENCES t1(c0)
);

-- Insert test data
INSERT INTO t1 (c0, c1, c2) VALUES (1, 'test', 10);
INSERT INTO t2 (c0, c1) VALUES (1, 100);
```

**Run EDC Oracle**:
```bash
java -jar sqlancer.jar --dbms mysql --oracle EDC \
  --host localhost --port 3306 \
  --database-prefix test_edc_constraints \
  --max-generated-databases 1
```

**Expected**: EDC creates raw DB without constraints, runs queries, compares results.

### 4.5 Success Criteria

| Criteria | Description | Acceptance Threshold |
|----------|-------------|---------------------|
| **Unit Test Pass Rate** | All unit tests pass | 100% |
| **Integration Test Pass Rate** | EDC runs without errors | 100% |
| **Isolation Test Pass Rate** | Existing oracles unaffected | 100% |
| **Bug Detection Capability** | Detect known optimizer bugs | ≥1 bug found |
| **Performance** | No significant slowdown | ≤10% overhead vs existing oracles |
| **Memory Usage** | No memory leaks | Stable memory during 1-hour run |

---

## 5. Risk Assessment - Phase 1

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Interface incompatibility | Low | High | Thorough interface comparison before coding |
| MySQL schema parsing failure | Medium | Medium | Test with various table structures |
| Raw DB creation failure | Medium | High | Handle edge cases (generated columns, views) |
| Result comparison false positives | Medium | High | Comprehensive expected error list |
| Performance degradation | Low | Medium | Profile and optimize if needed |
| Memory leak in equivalent state | Medium | Medium | Ensure connection closure |

---

## 6. Phase 2 Planning (Post-MySQL Success)

### 6.1 Phase 2 Trigger Conditions

| Condition | Threshold | Status |
|-----------|-----------|--------|
| MySQL EDC unit tests pass | 100% | Required |
| MySQL EDC integration tests pass | 100% | Required |
| MySQL EDC isolation verified | 100% | Required |
| MySQL EDC bug detection verified | ≥1 bug | Required |
| Code review approved | Pass | Required |

### 6.2 Phase 2: PostgreSQL EDC (After MySQL Success)

**Estimated Duration**: 2 days

| Task | Description | Duration |
|------|-------------|----------|
| Create PostgresEDC.java | PostgreSQL raw DB helper | 3 hours |
| Create PostgresEDCOracle.java | PostgreSQL EDC implementation | 4 hours |
| PostgreSQL schema parsing | Use pg_constraint, information_schema | 2 hours |
| Update PostgresOracleFactory | Add EDC enum | 0.5 hours |
| Unit tests | PostgreSQL-specific tests | 2 hours |
| Integration tests | Run against PostgreSQL | 3 hours |

### 6.3 Phase 3: GaussDB-M EDC (After PostgreSQL Success)

**Estimated Duration**: 1.5 days (MySQL-compatible, easier adaptation)

| Task | Description | Duration |
|------|-------------|----------|
| Create GaussDBMEDC.java | GaussDB-M raw DB helper | 2 hours |
| Create GaussDBMEDCOracle.java | GaussDB-M EDC implementation | 3 hours |
| Update GaussDBMOracleFactory | Add EDC enum | 0.5 hours |
| Unit tests | GaussDB-M-specific tests | 2 hours |
| Integration tests | Run against GaussDB-M | 3 hours |

---

## 7. Overall Timeline

| Phase | Duration | Milestone |
|-------|----------|-----------|
| **Phase 1** | 2 days | MySQL EDC working and tested |
| **Phase 1 Review** | 0.5 days | Code review, bug detection verification |
| **Phase 2** | 2 days | PostgreSQL EDC working and tested |
| **Phase 2 Review** | 0.5 days | PostgreSQL verification |
| **Phase 3** | 1.5 days | GaussDB-M EDC working and tested |
| **Final Integration** | 1 day | All 3 DBMS EDC integrated, docs updated |
| **Total** | **7.5 days** | Complete EDC integration |

---

## 8. Appendix: Key Differences Summary

### 8.1 RADAR → SQLancer Adaptation Points

| Component | RADAR | SQLancer | Adaptation Required |
|-----------|-------|----------|---------------------|
| `GlobalState` | `GlobalState<?,?,?>` | `SQLGlobalState<?,?>` | Minor (compatible) |
| `Connection` | `SQLancerDBConnection` | `SQLConnection` | Minor (same concept) |
| `Schema` | 5 data types | 17 data types | **Use SQLancer's** |
| `ExpressionGenerator` | 11 Actions | 14 Actions + interfaces | **Use SQLancer's** |
| `Visitor` | Basic toString | Full visitor | **Use SQLancer's** |
| `Errors` | Simple list | Comprehensive | **Use SQLancer's** |
| `QueryAdapter` | `SQLQueryAdapter` | `SQLQueryAdapter` | Same (compatible) |

### 8.2 Files to Create (New)

| File | Location | Purpose |
|------|----------|---------|
| `EDCBase.java` | common/oracle/ | Core EDC oracle base class |
| `MySQLEDC.java` | mysql/ | MySQL raw DB helper |
| `MySQLEDCOracle.java` | mysql/oracle/ | MySQL EDC oracle implementation |
| `PostgresEDC.java` | postgres/ (Phase 2) | PostgreSQL raw DB helper |
| `PostgresEDCOracle.java` | postgres/oracle/ (Phase 2) | PostgreSQL EDC oracle |
| `GaussDBMEDC.java` | gaussdbm/ (Phase 3) | GaussDB-M raw DB helper |
| `GaussDBMEDCOracle.java` | gaussdbm/oracle/ (Phase 3) | GaussDB-M EDC oracle |

### 8.3 Files to Modify (Existing)

| File | Location | Modification |
|------|----------|--------------|
| `MySQLOracleFactory.java` | mysql/ | Add EDC enum |
| `MySQLOptions.java` | mysql/ | Update oracle description |
| `PostgresOracleFactory.java` | postgres/ (Phase 2) | Add EDC enum |
| `PostgresOptions.java` | postgres/ (Phase 2) | Update oracle description |
| `GaussDBMOracleFactory.java` | gaussdbm/ (Phase 3) | Add EDC enum |
| `GaussDBMOptions.java` | gaussdbm/ (Phase 3) | Update oracle description |