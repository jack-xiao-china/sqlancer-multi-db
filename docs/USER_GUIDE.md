# SQLancer User Guide

## Introduction

SQLancer is a tool for testing database management systems by generating random SQL queries and checking for bugs. This guide covers both PostgreSQL and MySQL testing with the extended version that includes additional oracles and extensive data type support.

## Prerequisites

### System Requirements
- Java 17 or higher
- Database server (PostgreSQL 12-18 or MySQL 5.7-8.0+)
- Network access to database server

### Database Setup
1. Ensure the database server is running and accessible
2. Create a user with sufficient permissions (CREATE/DROP DATABASE)
3. Test databases will be created/dropped automatically

---

# PostgreSQL Testing

## Basic Usage

### Quick Test Command
```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 5432 \
    --username postgres \
    --password your_password \
    postgres
```

### Connection Parameters
| Parameter | Description | Default |
|-----------|-------------|---------|
| `--host` | PostgreSQL server host | localhost |
| `--port` | PostgreSQL server port | 5432 |
| `--username` | Database username | Required |
| `--password` | Database password | Required |
| `--database-prefix` | Prefix for test databases | Random |

### Test Control Parameters
| Parameter | Description | Default |
|-----------|-------------|---------|
| `--num-tries` | Number of databases to test | 1 |
| `--timeout-seconds` | Timeout per database (seconds) | 120 |
| `--seed` | Random seed for reproduction | Time-based |

## PostgreSQL-Specific Options

### Oracle Selection

The `--oracle` parameter selects test oracles. Multiple oracles can be combined with commas.

```bash
--oracle WHERE,DQE,DQP,EET
```

#### PostgreSQL Oracles

| Oracle | What It Tests | Performance |
|--------|---------------|-------------|
| NOREC | Optimization correctness (IN vs EXISTS) | Fast (~3000 queries/s) |
| PQS | Query synthesis correctness | Moderate |
| WHERE | WHERE clause partitioning equivalence | Fast (~800 queries/s) |
| HAVING | HAVING clause partitioning | Moderate |
| AGGREGATE | Aggregate function correctness | Moderate |
| DISTINCT | DISTINCT handling | Moderate |
| GROUP_BY | GROUP BY expressions | Slow (complex queries) |
| QUERY_PARTITIONING | Combined WHERE+HAVING+AGGREGATE | Moderate |
| CERT | CERT optimization testing | Moderate |
| FUZZER | Random query generation | Very fast (~3000 queries/s) |
| **DQE** | DELETE/UPDATE equivalence (NEW) | Uses transactions |
| **DQP** | Query determinism (NEW) | Same query twice |
| **EET** | Expression transformation (NEW) | Complex expression trees |
| **CODDTEST** | Expression folding (NEW) | Predicate evaluation |

### Generation and Coverage Options

PostgreSQL generators now include temporal, JSON/JSONB, UUID, BYTEA, ARRAY, and ENUM support by default. The current tuning knobs are:

```bash
--coverage-policy CONSERVATIVE   # Stable types only (reproduction)
--coverage-policy BALANCED       # Default (moderate coverage)
--coverage-policy AGGRESSIVE     # Maximum coverage (edge cases)
--pg-table-columns 12            # Number of columns in CREATE TABLE
--pg-generate-sql-num 5          # Number of rows in INSERT ... VALUES
--pg-index-model 6               # Expression index mode
```

| Option | Default | Use Case |
|--------|---------|----------|
| `--coverage-policy` | BALANCED | Balance stability vs. type/expression coverage |
| `--pg-table-columns` | 10 | Increase schema width to stress predicates and joins |
| `--pg-generate-sql-num` | 3 | Increase rows inserted per statement |
| `--pg-index-model` | 0 | Force a specific index generation pattern |
| `--extensions` | empty | Pre-create PostgreSQL extensions in each test database |
| `--connection-url` | `postgresql://localhost:5432/test` | Override host/port/db entry URL in one option |

### Tablespace Testing

```bash
--test-tablespaces true \
--tablespace-path "/custom/path"
```

OS-dependent defaults:
- Linux: Disabled by default
- macOS: Disabled (different /tmp handling)
- Windows: Disabled (use custom path)

---

# MySQL Testing

## Basic Usage

### Quick Test Command
```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 3306 \
    --username root \
    --password your_password \
    mysql
```

### Connection Parameters
| Parameter | Description | Default |
|-----------|-------------|---------|
| `--host` | MySQL server host | localhost |
| `--port` | MySQL server port | 3306 |
| `--username` | Database username | Required |
| `--password` | Database password | Required |
| `--engines` | Storage engines (comma-separated) | InnoDB |

## MySQL-Specific Options

### Oracle Selection

MySQL supports the same 14 oracles as PostgreSQL:

| Oracle | What It Tests | MySQL Notes |
|--------|---------------|-------------|
| NOREC | Optimization correctness | Works well |
| PQS | Query synthesis | Works well |
| WHERE | WHERE clause partitioning | Works well |
| HAVING | HAVING clause partitioning | Works well |
| AGGREGATE | Aggregate functions | Works well |
| DISTINCT | DISTINCT handling | Works well |
| GROUP_BY | GROUP BY expressions | Works well |
| QUERY_PARTITIONING | Combined oracle | Default for MySQL |
| CERT | CERT optimization | Works well |
| FUZZER | Random queries | Works well |
| **DQE** | DELETE/UPDATE equivalence (NEW) | Works well |
| **DQP** | Query determinism (NEW) | Works well |
| **EET** | Expression transformation (NEW) | Works well |
| **CODDTEST** | Expression folding (NEW) | Works well |

### MySQL Feature Flags

MySQL has extensive feature flags for fine-grained testing control:

#### Constraint Testing
| Option | Default | Description |
|--------|---------|-------------|
| `--test-foreign-keys` | true | Test foreign key constraints |
| `--test-check-constraints` | true | Allow CHECK constraints |
| `--test-generated-columns` | true | Test generated columns |
| `--test-primary-keys` | true | Allow primary keys |
| `--test-unique-constraints` | true | Allow unique constraints |
| `--test-not-null` | true | Allow NOT NULL constraints |
| `--test-auto-increment` | true | Allow AUTO_INCREMENT |

#### Query Features
| Option | Default | Description |
|--------|---------|-------------|
| `--test-joins` | true | Allow JOIN clauses |
| `--test-in-operator` | true | Allow IN operator |
| `--test-window-functions` | true | Allow window functions |
| `--test-common-table-expressions` | true | Allow WITH clauses (CTE) |
| `--test-union` | true | Allow UNION statements |
| `--test-group-sets` | true | Allow GROUPING SETS, ROLLUP, CUBE |
| `--test-having` | true | Allow HAVING clauses |
| `--test-distinct` | true | Allow DISTINCT |
| `--test-limit` | true | Allow LIMIT |
| `--test-order-by` | true | Allow ORDER BY |
| `--test-nulls-first-last` | true | Allow NULLS FIRST/LAST |

#### Function Testing
| Option | Default | Description |
|--------|---------|-------------|
| `--test-functions` | true | Allow functions in expressions |
| `--test-json` | true | Allow JSON functions/operators |
| `--test-spatial` | true | Allow spatial functions/types (GEOMETRY, POINT, etc.) |
| `--test-full-text-search` | true | Allow full-text search features |
| `--test-math-functions` | true | Allow mathematical functions |
| `--test-string-functions` | true | Allow string functions |
| `--test-aggregate-functions` | true | Allow aggregate functions |
| `--test-cast` | true | Allow CAST operations |
| `--test-concat` | true | Allow CONCAT function |
| `--test-case-expressions` | true | Allow CASE expressions |

#### MySQL Data Types
| Option | Default | Description |
|--------|---------|-------------|
| `--test-integers` | true | Allow INT, BIGINT, SMALLINT, TINYINT |
| `--test-floats` | true | Allow FLOAT, DOUBLE |
| `--test-decimals` | true | Allow DECIMAL type |
| `--test-dates` | true | Allow DATE, DATETIME, TIMESTAMP, TIME, YEAR |
| `--test-varchar` | true | Allow VARCHAR |
| `--test-text` | true | Allow TEXT, TINYTEXT, MEDIUMTEXT, LONGTEXT |
| `--test-binary` | true | Allow BINARY, VARBINARY, BLOB types |
| `--test-bit` | true | Allow BIT type |
| `--test-boolean` | true | Allow BOOLEAN (BOOL) |
| `--test-enums` | true | Allow ENUM type |
| `--test-sets` | true | Allow SET type |
| `--test-unsigned` | true | Allow UNSIGNED integers (MySQL-specific) |
| `--test-zerofill` | true | Allow ZEROFILL integers (MySQL-specific) |
| `--test-json-data-type` | true | Allow JSON data type |
| `--test-array-data-type` | true | Allow ARRAY type (MySQL 8.0+) |

#### Schema Objects
| Option | Default | Description |
|--------|---------|-------------|
| `--test-views` | true | Allow views creation |
| `--test-triggers` | true | Allow triggers |
| `--test-procedures` | true | Allow stored procedures |
| `--test-events` | true | Allow scheduled events |
| `--test-indexes` | true | Allow indexes |
| `--test-temp-tables` | true | Allow TEMPORARY tables |
| `--test-comments` | true | Allow table/column comments |

#### DML Features
| Option | Default | Description |
|--------|---------|-------------|
| `--test-multi-table-delete` | true | Allow multi-table DELETE |
| `--test-multi-table-update` | true | Allow multi-table UPDATE |
| `--test-anonymous-columns` | true | Allow anonymous columns in INSERT |

#### Control Flow (Stored Programs)
| Option | Default | Description |
|--------|---------|-------------|
| `--test-for-loop` | true | Allow FOR loop constructs |
| `--test-if-conditions` | true | Allow IF conditions |

#### Miscellaneous
| Option | Default | Description |
|--------|---------|-------------|
| `--test-collations` | true | Test different collations |

### MySQL Storage Engines

Specify storage engines for table creation:

```bash
mysql --engines InnoDB,MyISAM,MEMORY
```

Common engines:
- **InnoDB**: Default, transaction-safe, row-level locking, foreign keys
- **MyISAM**: Non-transactional, table-level locking, faster for read-heavy
- **MEMORY**: In-memory tables, fast but data lost on restart
- **NDB**: MySQL Cluster engine

---

# Testing Scenarios

## PostgreSQL Scenarios

### Scenario 1: Quick Bug Hunting
```bash
java -jar sqlancer.jar postgres \
    --oracle NOREC,WHERE \
    --num-tries 50 \
    --timeout-seconds 60
```

### Scenario 2: Comprehensive Testing with Extended Types
```bash
java -jar sqlancer.jar postgres \
    --oracle QUERY_PARTITIONING,DQE,DQP,EET \
    --coverage-policy AGGRESSIVE \
    --pg-table-columns 12 \
    --pg-generate-sql-num 5 \
    --pg-index-model 6 \
    --num-tries 100
```

### Scenario 3: Bug Reproduction
```bash
java -jar sqlancer.jar postgres \
    --seed 1776222510722 \
    --oracle EET \
    --coverage-policy CONSERVATIVE \
    --num-tries 1
```

### Scenario 4: Non-English Server Testing
```bash
# Works with Chinese, German, Japanese PostgreSQL servers
java -jar sqlancer.jar postgres \
    --host chinese-pg-server \
    --oracle WHERE
```

## MySQL Scenarios

### Scenario 1: Basic Testing
```bash
java -jar sqlancer.jar mysql \
    --oracle QUERY_PARTITIONING \
    --num-tries 10
```

### Scenario 2: JSON Testing
```bash
java -jar sqlancer.jar mysql \
    --oracle WHERE,EET \
    --test-json true \
    --test-json-data-type true
```

### Scenario 3: Multi-Engine Testing
```bash
java -jar sqlancer.jar mysql \
    --engines InnoDB,MyISAM \
    --oracle DQE,DQP
```

### Scenario 4: Spatial Data Testing
```bash
java -jar sqlancer.jar mysql \
    --test-spatial true \
    --oracle FUZZER
```

### Scenario 5: Minimal Feature Testing (Faster)
```bash
java -jar sqlancer.jar mysql \
    --test-joins false \
    --test-window-functions false \
    --test-common-table-expressions false \
    --test-triggers false \
    --test-procedures false \
    --oracle NOREC,WHERE
```

### Scenario 6: MySQL-Specific Features
```bash
java -jar sqlancer.jar mysql \
    --test-unsigned true \
    --test-zerofill true \
    --test-enums true \
    --test-sets true \
    --oracle QUERY_PARTITIONING
```

---

# Output and Logging

## Log File Structure

Logs are stored in: `logs/<dbms>/<test_name>_<date>_<time>/`

Each database generates:
- `*.log` - Complete SQL execution log
- `*final*.log` - Bug-triggering queries (if bugs found)

## Understanding Log Output

**Normal execution:**
```
[2026/04/15 10:45:40] Executed 7621 queries (1013 queries/s)
```

**Bug found:**
```
java.lang.AssertionError: SELECT ...
-- Time: 2026/04/15 10:45:42
-- Database: test0
-- seed value: 1776222510722
```

## Initial "0 queries/s" Period

The first few seconds show "0 queries/s" - this is normal:
- Database creation
- Table creation
- Data insertion
- Schema setup (indexes, constraints)

After preparation (~5-15 seconds), queries begin executing.

---

# Performance Optimization

## Multi-threaded Testing

```bash
--num-threads 4  # Number of concurrent test threads
```

## Timeout Considerations

| Timeout | Use Case |
|---------|----------|
| 60s | Quick testing |
| 120s | Balanced approach |
| 300s | Deep testing |

## Type/Feature Selection Impact

| Configuration | Impact | Recommendation |
|---------------|--------|----------------|
| Default types | Fastest | Daily testing |
| +time-types | Moderate | Weekly testing |
| +json/jsonb | Moderate | JSON-focused |
| +arrays | Slower | Array-specific bugs |
| +spatial | Slower | Spatial-specific bugs |

---

# Troubleshooting

## PostgreSQL Issues

### Connection Refused
```
Error: Connection refused
```
Solution: Check PostgreSQL running, verify host/port

### Permission Denied
```
Error: permission denied to create database
```
Solution: Grant CREATEDB permission:
```sql
ALTER USER your_user CREATEDB;
```

### Locale-Specific Errors
The extended version handles localized error messages (Chinese, German, etc.) automatically via SQLSTATE codes.

## MySQL Issues

### Connection Refused
Solution: Check MySQL running, verify port 3306

### Access Denied
```sql
CREATE USER 'tester'@'%' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON *.* TO 'tester'@'%';
FLUSH PRIVILEGES;
```

### Unknown Storage Engine
Solution: Ensure specified engines exist:
```sql
SHOW ENGINES;
```

## Memory Issues

```
Error: OutOfMemoryError
```
Solution: Increase Java heap:
```bash
java -Xmx4g -jar sqlancer.jar ...
```

---

# Best Practices

1. **Start with default types** - well-tested and stable
2. **Add types/features incrementally** - one new group at a time
3. **Use appropriate timeouts** - longer for complex oracles (EET, CODDTEST)
4. **Save seeds** - record for interesting behaviors
5. **Monitor logs** - check `logs/` directory regularly
6. **Test reproduction** - verify bugs can be reproduced

---

# Oracle Selection Guide

| Goal | Recommended Oracles |
|------|---------------------|
| Performance testing | NOREC, DQP |
| Query correctness | WHERE, QUERY_PARTITIONING |
| DML correctness | DQE |
| Expression bugs | EET |
| Semantic correctness | CODDTEST |
| Coverage testing | FUZZER |

---

# Version Compatibility

## PostgreSQL

| Version | Status | Notes |
|---------|--------|-------|
| 12-15 | Tested | Works well |
| 16-18 | Tested | Works well |

## MySQL

| Version | Status | Notes |
|---------|--------|-------|
| 5.7 | Tested | Works well (limited ARRAY support) |
| 8.0+ | Tested | Works well (full feature support) |

---

# Full Command Examples

## PostgreSQL Full Example
```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 5432 \
    --username tpcc \
    --password Taurus@123 \
    --num-tries 10 \
    --timeout-seconds 120 \
    postgres \
    --oracle QUERY_PARTITIONING,DQE,DQP,EET \
    --coverage-policy BALANCED \
    --pg-table-columns 12 \
    --pg-generate-sql-num 5 \
    --pg-index-model 6
```

## MySQL Full Example
```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 3306 \
    --username root \
    --password password \
    --num-tries 10 \
    --timeout-seconds 120 \
    mysql \
    --oracle QUERY_PARTITIONING,DQE,DQP,EET \
    --engines InnoDB \
    --test-json true \
    --test-spatial true \
    --test-window-functions true \
    --test-common-table-expressions true
```

## MySQL Minimal Example (Fast Testing)
```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 3306 \
    --username root \
    --password password \
    --num-tries 50 \
    --timeout-seconds 60 \
    mysql \
    --oracle NOREC,WHERE \
    --test-joins false \
    --test-window-functions false \
    --test-triggers false \
    --test-procedures false
```

---

# Getting Help

1. Check log files in `logs/<dbms>/`
2. Review error messages and stack traces
3. Use `--seed` to reproduce issues
4. Simplify test (reduce types, single oracle)
