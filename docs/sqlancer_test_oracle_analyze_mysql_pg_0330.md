# SQLancer MySQL和PostgreSQL Test Oracles 分析报告
*分析日期: 2026-03-30*

## **MySQL Test Oracles (12 types)**

### **1. AGGREGATE - Ternary Logic Partitioning for Aggregate Functions**
- **Purpose**: Detects logic bugs in aggregate functions (COUNT, SUM, MIN, MAX)
- **Algorithm**:
  1. Generates original query with aggregate functions
  2. Creates partitioned queries: original WHERE, negated WHERE, IS NULL
  3. Combines partitions with UNION ALL and outer aggregate
  4. Compares original vs combined results
- **File**: `MySQLTLPAggregateOracle.java`
- **Use Case**: Testing MySQL-specific aggregate function bugs, especially with ORDER BY and LIMIT

### **2. HAVING - TLP for HAVING Clauses**
- **Purpose**: Tests HAVING clause logic with aggregate functions
- **Algorithm**: Uses ternary logic partitioning on HAVING conditions
- **File**: `MySQLTLPHavingOracle.java`
- **Use Case**: Detecting bugs in filtering grouped results

### **3. GROUP_BY - TLP for GROUP BY Operations**
- **Purpose**: Tests GROUP BY clause implementation
- **Algorithm**: Ternary logic partitioning applied to GROUP BY predicates
- **File**: `MySQLTLPGroupByOracle.java`
- **Use Case**: Finding issues with grouping operations and aggregation

### **4. DISTINCT - TLP for DISTINCT Queries**
- **Purpose**: Tests DISTINCT clause implementation
- **Algorithm**: Ternary logic partitioning with DISTINCT operations
- **File**: `MySQLTLPDistinctOracle.java`
- **Use Case**: Detecting bugs in duplicate removal operations

### **5. NOREC - Non-optimizing Reference Engine Construction**
- **Purpose**: Detects optimization bugs by comparing optimized vs non-optimized execution
- **Algorithm**: Disables query optimizer and compares results
- **File**: `NoRECOracle.java` (common)
- **Use Case**: Testing MySQL's query optimizer correctness

### **6. TLP_WHERE - TLP for WHERE Clauses**
- **Purpose**: Tests WHERE clause logic using ternary partitioning
- **Algorithm**: Partitions by predicate, NOT predicate, and IS NULL
- **File**: `TLPWhereOracle.java` (common)
- **Use Case**: Detecting logic errors in filtering conditions

### **7. PQS - Pivoted Query Synthesis**
- **Purpose**: Detects bugs by generating queries to retrieve specific pivot rows
- **Algorithm**:
  1. Selects pivot row randomly
  2. Generates WHERE clause to return only that row
  3. Validates pivot row is in results
- **File**: `MySQLPivotedQuerySynthesisOracle.java`
- **Use Case**: Testing row retrieval logic and indexing

### **8. CERT - Cardinality Estimation Restriction Testing**
- **Purpose**: Finds performance issues through cardinality estimate violations
- **Algorithm**: Compares cardinality estimates between original and restrictive queries
- **File**: `CERTOracle.java` (common)
- **Use Case**: Optimizer performance tuning and cost estimation accuracy

### **9. FUZZER - Random Query Fuzzer**
- **Purpose**: Generates random SQL queries for bug detection
- **Algorithm**: Random query generation with error handling
- **File**: `MySQLFuzzer.java`
- **Use Case**: Exploratory testing and finding edge cases

### **10. DQP - Differential Query Plans**
- **Purpose**: Detects plan generation inconsistencies
- **Algorithm**: Compares execution plans for similar queries
- **File**: `MySQLDQPOracle.java`
- **Use Case**: Testing query plan stability and correctness

### **11. DQE - Differential Query Execution**
- **Purpose**: Detects logic bugs by comparing SELECT vs UPDATE vs DELETE
- **Algorithm**:
  1. Adds auxiliary columns for row tracking
  2. Executes SELECT, UPDATE, DELETE with same WHERE
  3. Compares accessed rows and error patterns
- **File**: `MySQLDQEOracle.java`
- **Use Case**: Testing predicate evaluation consistency across operations

### **12. EET - Expression Equivalence Testing (Enhanced)**
- **Purpose**: Tests expression equivalence through transformations
- **Algorithm**:
  1. Generates random SQL expressions
  2. Applies transformations using `MySQLEETQueryTransformer`
  3. Compares original and transformed query results
- **Files**: `MySQLEETOracle.java`, `MySQLEETQueryTransformer.java`
- **Use Case**: Detecting expression evaluation bugs in MySQL

### **13. QUERY_PARTITIONING - Composite Oracle**
- **Purpose**: Combines multiple TLP oracles
- **Algorithm**: Runs WHERE, HAVING, GROUP BY, AGGREGATE, DISTINCT, NOREC sequentially
- **File**: Composite in `MySQLOracleFactory.java`
- **Use Case**: Comprehensive testing with multiple oracles

## **PostgreSQL Test Oracles (7 types)**

### **1. NOREC - Non-optimizing Reference Engine Construction**
- **Purpose**: Same as MySQL version, adapted for PostgreSQL
- **Algorithm**: Disables PostgreSQL's query optimizer
- **File**: `NoRECOracle.java` (common)
- **Use Case**: Testing PostgreSQL's query optimizer

### **2. PQS - Pivoted Query Synthesis**
- **Purpose**: Same concept as MySQL, PostgreSQL-specific implementation
- **Algorithm**: Pivot row testing with PostgreSQL syntax
- **File**: `PostgresPivotedQuerySynthesisOracle.java`
- **Use Case**: PostgreSQL-specific row retrieval testing

### **3. WHERE - TLP for WHERE Clauses**
- **Purpose**: Ternary logic partitioning for WHERE clauses
- **Algorithm**: Same as MySQL's TLP_WHERE
- **File**: `TLPWhereOracle.java` (common)
- **Use Case**: Testing PostgreSQL WHERE clause logic

### **4. HAVING - TLP for HAVING Clauses**
- **Purpose**: PostgreSQL-specific HAVING clause testing
- **Algorithm**: Ternary logic partitioning for HAVING
- **File**: `PostgresTLPHavingOracle.java`
- **Use Case**: PostgreSQL grouped result filtering

### **5. QUERY_PARTITIONING - Composite Oracle**
- **Purpose**: Combines WHERE and HAVING oracles
- **Algorithm**: Runs WHERE, HAVING, and aggregate oracles
- **File**: Composite in `PostgresOracleFactory.java`
- **Use Case**: Comprehensive PostgreSQL testing

### **6. CERT - Cardinality Estimation Restriction Testing**
- **Purpose**: PostgreSQL version of cardinality testing
- **Algorithm**: Parses PostgreSQL's EXPLAIN output differently
- **File**: `CERTOracle.java` (common)
- **Use Case**: PostgreSQL optimizer performance tuning

### **7. FUZZER - Random Query Fuzzer**
- **Purpose**: PostgreSQL-specific random query generation
- **Algorithm**: Random queries with PostgreSQL error handling
- **File**: `PostgresFuzzer.java`
- **Use Case**: PostgreSQL exploratory testing

## **Key Differences**

1. **Coverage**: MySQL has more oracles (12 vs 7), including MySQL-specific features like DQE, DQP, and enhanced EET
2. **EET Enhancement**: The EET oracle is enhanced in this version with sophisticated expression transformation
3. **Error Handling**: Each oracle has database-specific error handling for expected errors
4. **Requirements**: PQS and CERT require tables to have rows (`requiresAllTablesToContainRows()`)

## **Common Oracle Patterns**

- **TLP (Ternary Logic Partitioning)**: Used across multiple MySQL oracles for testing logic consistency
- **Metamorphic Testing**: Most oracles follow metamorphic testing principles - generate queries, transform them, and compare results
- **Error Handling**: Comprehensive expected error handling to distinguish bugs from expected database errors

## **Source Files**

**MySQL Oracles Factory**:
- `src/sqlancer/mysql/MySQLOracleFactory.java`

**PostgreSQL Oracles Factory**:
- `src/sqlancer/postgres/PostgresOracleFactory.java`

**EET Enhancement Files** (in this version):
- `src/sqlancer/mysql/oracle/eet/MySQLEETOracle.java`
- `src/sqlancer/mysql/oracle/eet/MySQLEETQueryTransformer.java`
- `src/sqlancer/mysql/oracle/eet/EETQueryExecutor.java`
- `src/sqlancer/mysql/oracle/eet/EETDefaultQueryExecutor.java`