# RADAR EDC Oracle Integration Design

## 1. Overview

### 1.1 Project Background
- **RADAR Project**: Located at `d:\Jack.Xiao\dbtools\radar`, implements EDC (Equivalent Database Construction) Oracle
- **SQLancer Project**: Located at `d:\Jack.Xiao\dbtools\sqlancer-main\sqlancer-main`, a database testing framework with multiple test oracles
- **Goal**: Integrate RADAR's EDC Oracle into SQLancer as a new independent test oracle

### 1.2 EDC Oracle Principle
EDC Oracle detects bugs by:
1. Creating an "equivalent" database without constraints (NOT NULL, UNIQUE, FOREIGN KEY, CHECK, etc.)
2. Generating random SELECT queries on both original and equivalent databases
3. Comparing results between optimized (original DB) and non-optimized (equivalent DB) queries
4. Result mismatch indicates potential optimizer bug

### 1.3 Integration Requirements
| Requirement | Description |
|-------------|-------------|
| Independence | New oracle should not affect existing oracle functionality |
| Completeness | All RADAR features must be preserved, no feature reduction |
| Compatibility | Support PostgreSQL, GaussDB-M databases with MySQL-equivalent functionality |
| Isolation | Code logic isolation, no interference with existing components |

## 2. Architecture Analysis

### 2.1 RADAR Project Structure
```
radar/src/sqlancer/
├── common/oracle/
│   ├── EDCBase.java          # Core EDC oracle base class
│   ├── TestOracle.java       # Oracle interface
│   └── ...
├── mysql/
│   ├── MySQLEDC.java         # MySQL raw DB construction
│   ├── MySQLEDCOracle.java   # MySQL EDC oracle implementation
│   ├── MySQLGlobalState.java
│   ├── MySQLSchema.java
│   └── ...
├── cockroachdb/              # PostgreSQL-compatible DBMS
│   ├── CockroachDBEDC.java
│   ├── CockroachDBEDCOracle.java
│   └── ...
```

### 2.2 SQLancer Project Structure
```
sqlancer/src/sqlancer/
├── common/oracle/
│   ├── TestOracle.java       # Oracle interface (compatible)
│   ├── NoRECBase.java
│   ├── CERTOracleBase.java
│   └── ...
├── mysql/
│   ├── MySQLOracleFactory.java  # Oracle registration enum
│   ├── MySQLGlobalState.java
│   └── oracle/
│       ├── MySQLDQEOracle.java
│       └── ...
├── postgres/
│   ├── PostgresOracleFactory.java
│   ├── PostgresGlobalState.java
│   └── ...
├── gaussdbm/
│   ├── GaussDBMOracleFactory.java
│   ├── GaussDBMGlobalState.java
│   └── ...
```

### 2.3 Key Interfaces Comparison

| Interface | RADAR | SQLancer | Compatibility |
|-----------|-------|----------|---------------|
| TestOracle | `TestOracle<G extends GlobalState<?,?,?>>` | `TestOracle<G extends GlobalState<?,?,?>>` | **Compatible** |
| OracleFactory | `OracleFactory<G extends GlobalState<?,?,?>>` | `OracleFactory<G extends GlobalState<?,?,?>>` | **Compatible** |
| GlobalState | Different structure | Different structure | **Need adaptation** |

## 3. Integration Architecture

### 3.1 Module Structure Design

```
sqlancer/src/sqlancer/
├── common/oracle/
│   ├── EDCBase.java              # Copied from RADAR, adapted to SQLancer
│   └── TestOracle.java           # Already exists, compatible
├── mysql/oracle/
│   ├── MySQLEDC.java             # MySQL raw DB construction
│   └── MySQLEDCOracle.java       # MySQL EDC oracle implementation
├── postgres/oracle/
│   ├── PostgresEDC.java          # PostgreSQL raw DB construction (NEW)
│   └── PostgresEDCOracle.java    # PostgreSQL EDC oracle implementation (NEW)
├── gaussdbm/oracle/
│   ├── GaussDBMEDC.java          # GaussDB-M raw DB construction (NEW)
│   └── GaussDBMEDCOracle.java    # GaussDB-M EDC oracle implementation (NEW)
```

### 3.2 Class Hierarchy

```
                    TestOracle (Interface)
                           │
                           │
                    EDCBase (Abstract Base)
                    /      |       \
                   /       |        \
    MySQLEDCOracle  PostgresEDCOracle  GaussDBMEDCOracle
          │              │                │
          │              │                │
    MySQLEDC      PostgresEDC      GaussDBMEDC
    (Raw DB       (Raw DB          (Raw DB
     Helper)       Helper)          Helper)
```

### 3.3 OracleFactory Registration

Each DBMS OracleFactory needs to add EDC enum:

```java
// MySQLOracleFactory.java
public enum MySQLOracleFactory implements OracleFactory<MySQLGlobalState> {
    // ... existing oracles
    EDC {
        @Override
        public TestOracle<MySQLGlobalState> create(MySQLGlobalState globalState) throws SQLException {
            return new MySQLEDCOracle(globalState);
        }
    },
    // ...
}

// PostgresOracleFactory.java - similar addition
// GaussDBMOracleFactory.java - similar addition
```

## 4. Implementation Plan

### 4.1 Phase 1: Core EDCBase Integration (Day 1)

**Task**: Integrate EDCBase from RADAR to SQLancer common oracle directory

**Files**:
- `src/sqlancer/common/oracle/EDCBase.java`

**Changes Required**:
1. Adapt EDCBase to use SQLancer's infrastructure:
   - Replace RADAR's `SQLConnection` with SQLancer's `SQLConnection`
   - Replace RADAR's `SQLQueryAdapter` with SQLancer's `SQLQueryAdapter`
   - Adapt `StateLogger` and `MainOptions` references
   - Adapt `ExpectedErrors` handling

**Key Methods to Preserve**:
- `check()` - Main oracle check method
- `obtainTableSchemas()` - Get table metadata (abstract)
- `constructEquivalentState()` - Create raw DB (abstract)
- `generateQueryString()` - Generate test query (abstract)
- `getOptimizedResult()` - Execute query on original DB
- `getNonOptimizedResult()` - Execute query on raw DB
- `containsNewDatabaseStructure()` - Check for new DB structure

### 4.2 Phase 2: MySQL EDC Oracle (Day 2)

**Task**: Implement MySQL EDC Oracle based on RADAR implementation

**Files**:
- `src/sqlancer/mysql/oracle/MySQLEDCOracle.java`
- `src/sqlancer/mysql/MySQLEDC.java`

**Implementation Details**:

```java
// MySQLEDCOracle.java
public class MySQLEDCOracle extends EDCBase<MySQLGlobalState> implements TestOracle<MySQLGlobalState> {
    
    public MySQLEDCOracle(MySQLGlobalState originalState) {
        super(originalState);
        MySQLErrors.addExpressionErrors(errors);
    }

    @Override
    public Map<String, Map<String, List<String>>> obtainTableSchemas(MySQLGlobalState state) throws SQLException {
        // Parse CREATE TABLE statements for column metadata
        // Handle generated columns, foreign keys, constraints
    }

    @Override
    public MySQLGlobalState constructEquivalentState(MySQLGlobalState state) {
        MySQLEDC edc = new MySQLEDC(state);
        return edc.createRawDB();
    }

    @Override
    public String generateQueryString(MySQLGlobalState state) {
        // Use MySQLExpressionGenerator to generate SELECT query
        // Generate WHERE clause, fetch columns, FROM tables
    }
}

// MySQLEDC.java
public class MySQLEDC {
    public MySQLGlobalState createRawDB() throws SQLException {
        // 1. Create new database with suffix "_raw"
        // 2. Copy tables without constraints
        // 3. Copy data from original tables
        // 4. Return new GlobalState for raw DB
    }
}
```

### 4.3 Phase 3: PostgreSQL EDC Oracle (Day 3)

**Task**: Create PostgreSQL EDC Oracle implementation

**Files**:
- `src/sqlancer/postgres/oracle/PostgresEDCOracle.java`
- `src/sqlancer/postgres/PostgresEDC.java`

**Implementation Details**:

```java
// PostgresEDCOracle.java
public class PostgresEDCOracle extends EDCBase<PostgresGlobalState> implements TestOracle<PostgresGlobalState> {
    
    public PostgresEDCOracle(PostgresGlobalState originalState) {
        super(originalState);
        PostgresErrors.addExpressionErrors(errors);
    }

    @Override
    public Map<String, Map<String, List<String>>> obtainTableSchemas(PostgresGlobalState state) throws SQLException {
        // PostgreSQL-specific schema parsing:
        // - Use information_schema.columns for column metadata
        // - Use pg_constraint for constraint information
        // - Handle generated columns, foreign keys, check constraints
    }

    @Override
    public PostgresGlobalState constructEquivalentState(PostgresGlobalState state) {
        PostgresEDC edc = new PostgresEDC(state);
        return edc.createRawDB();
    }

    @Override
    public String generateQueryString(PostgresGlobalState state) {
        // Use PostgresExpressionGenerator
        // PostgreSQL-specific query generation
    }
}

// PostgresEDC.java
public class PostgresEDC {
    public PostgresGlobalState createRawDB() throws SQLException {
        // PostgreSQL-specific raw DB creation:
        // - CREATE DATABASE with "_raw" suffix
        // - CREATE TABLE without constraints
        // - INSERT data from original tables
    }
}
```

**PostgreSQL Schema Query Reference**:
```sql
-- Column metadata
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'table_name';

-- Constraints
SELECT conname, contype, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'table_name'::regclass;
```

### 4.4 Phase 4: GaussDB-M EDC Oracle (Day 4)

**Task**: Create GaussDB-M EDC Oracle implementation

**Files**:
- `src/sqlancer/gaussdbm/oracle/GaussDBMEDCOracle.java`
- `src/sqlancer/gaussdbm/GaussDBMEDC.java`

**Implementation Details**:

GaussDB-M uses MySQL-compatible syntax, so implementation is similar to MySQL:
- Use MySQL-style `SHOW CREATE TABLE` for schema parsing
- Use MySQL-style `CREATE DATABASE` and `INSERT` statements
- Handle GaussDB-M specific errors via `GaussDBMErrors`

```java
// GaussDBMEDCOracle.java
public class GaussDBMEDCOracle extends EDCBase<GaussDBMGlobalState> implements TestOracle<GaussDBMGlobalState> {
    
    public GaussDBMEDCOracle(GaussDBMGlobalState originalState) {
        super(originalState);
        GaussDBMErrors.addExpressionErrors(errors);
    }

    @Override
    public Map<String, Map<String, List<String>>> obtainTableSchemas(GaussDBMGlobalState state) throws SQLException {
        // Similar to MySQL implementation
        // Use SHOW CREATE TABLE for schema parsing
    }

    @Override
    public GaussDBMGlobalState constructEquivalentState(GaussDBMGlobalState state) {
        GaussDBMEDC edc = new GaussDBMEDC(state);
        return edc.createRawDB();
    }

    @Override
    public String generateQueryString(GaussDBMGlobalState state) {
        // Use GaussDBMExpressionGenerator
    }
}
```

### 4.5 Phase 5: OracleFactory Registration (Day 5)

**Task**: Register EDC oracle in each OracleFactory

**Files**:
- `src/sqlancer/mysql/MySQLOracleFactory.java`
- `src/sqlancer/postgres/PostgresOracleFactory.java`
- `src/sqlancer/gaussdbm/GaussDBMOracleFactory.java`

**Registration Pattern**:

```java
// Add to each OracleFactory enum
EDC {
    @Override
    public TestOracle<GlobalState> create(GlobalState globalState) throws Exception {
        return new EDCOracle(globalState);
    }
    
    @Override
    public boolean requiresAllTablesToContainRows() {
        return true;  // EDC needs tables with data
    }
},
```

**Options Update**:

```java
// MySQLOptions.java, PostgresOptions.java, GaussDBMOptions.java
// Update --oracle description to include EDC option
@Parameter(names = "--oracle", description = "Specifies which test oracle should be used, Options: [..., EDC, ...]")
public List<OracleFactory> oracles = Arrays.asList(OracleFactory.QUERY_PARTITIONING);
```

## 5. Code Isolation Strategy

### 5.1 Package Isolation

All EDC-related classes are placed in dedicated oracle directories:
- `sqlancer.common.oracle.EDCBase` - Shared base class
- `sqlancer.mysql.oracle.MySQLEDCOracle` - MySQL-specific
- `sqlancer.postgres.oracle.PostgresEDCOracle` - PostgreSQL-specific
- `sqlancer.gaussdbm.oracle.GaussDBMEDCOracle` - GaussDB-M-specific

### 5.2 Naming Convention

All EDC classes use consistent naming:
- `EDCBase` - Base class
- `[DBMS]EDCOracle` - Oracle implementation
- `[DBMS]EDC` - Raw DB construction helper

### 5.3 No Modification to Existing Oracles

Implementation follows these rules:
1. Do not modify existing oracle implementations
2. Do not change existing OracleFactory enum values
3. Do not alter existing GlobalState classes
4. Only add new enum values to OracleFactory

### 5.4 Error Handling Isolation

Each DBMS EDC oracle has independent error handling:
```java
// MySQL-specific errors
MySQLErrors.addExpressionErrors(errors);

// PostgreSQL-specific errors
PostgresCommon.getCommonExpressionErrors();

// GaussDB-M-specific errors
GaussDBMErrors.getExpressionErrors();
```

## 6. Feature Completeness Checklist

| Feature | MySQL | PostgreSQL | GaussDB-M | Status |
|---------|-------|------------|-----------|--------|
| Raw DB Creation | ✓ | NEW | NEW | Required |
| Schema Parsing | ✓ | NEW | ✓ (MySQL-like) | Required |
| Query Generation | ✓ | NEW | ✓ | Required |
| Result Comparison | ✓ | ✓ | ✓ | Required |
| Foreign Key Handling | ✓ | NEW | ✓ | Required |
| Generated Column | ✓ | NEW | NEW | Required |
| CHECK Constraint | ✓ | NEW | NEW | Required |
| Error Handling | ✓ | ✓ | ✓ | Required |
| Structure Hashing | ✓ | ✓ | ✓ | Required |

## 7. Testing Strategy

### 7.1 Unit Tests

Create unit tests for each component:
- `MySQLEDCOracleTest.java`
- `PostgresEDCOracleTest.java`
- `GaussDBMEDCOracleTest.java`

Test coverage:
- Schema parsing correctness
- Raw DB creation verification
- Query generation validity
- Result comparison logic
- Error handling validation

### 7.2 Integration Tests

Run EDC oracle against real databases:
```bash
# MySQL
java -jar sqlancer.jar --dbms mysql --oracle EDC --host localhost --port 3306

# PostgreSQL
java -jar sqlancer.jar --dbms postgres --oracle EDC --host localhost --port 5432

# GaussDB-M
java -jar sqlancer.jar --dbms gaussdbm --oracle EDC --host localhost --port 19995
```

### 7.3 Isolation Verification

Verify no interference with existing oracles:
1. Run existing oracles (TLP, NoREC, PQS) - should work unchanged
2. Run QUERY_PARTITIONING composite oracle - should work unchanged
3. Run EDC oracle independently - should work correctly
4. Run EDC + other oracles combined - should work correctly

## 8. Timeline and Milestones

| Phase | Task | Duration | Milestone |
|-------|------|----------|-----------|
| Phase 1 | EDCBase Integration | 1 day | Core base class ready |
| Phase 2 | MySQL EDC Oracle | 1 day | MySQL EDC working |
| Phase 3 | PostgreSQL EDC Oracle | 1 day | PostgreSQL EDC working |
| Phase 4 | GaussDB-M EDC Oracle | 1 day | GaussDB-M EDC working |
| Phase 5 | OracleFactory Registration | 1 day | All oracles registered |
| Phase 6 | Testing & Documentation | 1 day | Complete integration |

**Total Duration**: 6 days

## 9. Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Interface incompatibility | High | Thorough interface comparison before implementation |
| PostgreSQL syntax differences | Medium | Reference CockroachDB implementation in RADAR |
| GaussDB-M specific errors | Low | Use existing GaussDBMErrors class |
| Performance impact | Low | EDC runs independently, no shared resources |
| Test coverage | Medium | Comprehensive unit and integration tests |

## 10. References

### 10.1 RADAR Project Key Files
- `radar/src/sqlancer/common/oracle/EDCBase.java` - Core oracle logic
- `radar/src/sqlancer/mysql/oracle/MySQLEDCOracle.java` - MySQL implementation
- `radar/src/sqlancer/mysql/MySQLEDC.java` - MySQL raw DB helper
- `radar/src/sqlancer/cockroachdb/oracle/CockroachDBEDCOracle.java` - PostgreSQL-compatible reference

### 10.2 SQLancer Project Key Files
- `sqlancer/src/sqlancer/common/oracle/TestOracle.java` - Oracle interface
- `sqlancer/src/sqlancer/mysql/MySQLOracleFactory.java` - MySQL oracle registration
- `sqlancer/src/sqlancer/postgres/PostgresOracleFactory.java` - PostgreSQL oracle registration
- `sqlancer/src/sqlancer/gaussdbm/GaussDBMOracleFactory.java` - GaussDB-M oracle registration

## 11. Summary

This integration design provides a comprehensive plan for adding RADAR's EDC Oracle to SQLancer. The design ensures:

1. **Complete Feature Preservation**: All RADAR EDC features are maintained
2. **Code Isolation**: No interference with existing oracles
3. **Database Compatibility**: MySQL, PostgreSQL, and GaussDB-M support
4. **Consistent Architecture**: Follows SQLancer's OracleFactory pattern
5. **Comprehensive Testing**: Unit and integration tests for all components

The estimated implementation duration is 6 days with clear milestones for each phase.