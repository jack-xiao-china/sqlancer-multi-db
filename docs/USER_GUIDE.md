# SQLancer User Guide

**Version**: v0.1.82 (2026-04-25)  
**Supported Databases**: MySQL, PostgreSQL, GaussDB-A, GaussDB-PG, GaussDB-M, and 20+ other DBMS

## Introduction

SQLancer is a tool for testing database management systems by generating random SQL queries and checking for bugs. This extended version includes:

- **14 Test Oracles** for MySQL and PostgreSQL (including EET, DQE, DQP, CODDTEST)
- **GaussDB Support**: A-compatibility mode (Oracle-style), PG-compatibility mode, and M-compatibility mode (MySQL-style)
- **Extended Data Types**: JSON, BLOB, temporal types, arrays, enums, spatial types
- **Internationalization**: Handles non-English server error messages
- **Single-Jar Packaging**: All dependencies bundled, run with `java -jar`

---

## Quick Start

### Build

```bash
# Clone project
git clone https://github.com/jack-xiao-china/sqlancer-multi-db.git
cd sqlancer-multi-db

# Build (lib/ contains openGauss JDBC driver)
mvn clean package -DskipTests

# Output: ~387MB single jar with all dependencies
ls -lh target/sqlancer-2.0.0.jar
```

### Run

**Recommended** (all dependencies included in jar):
```bash
java -jar target/sqlancer-2.0.0.jar mysql --oracle QUERY_PARTITIONING
java -jar target/sqlancer-2.0.0.jar gaussdb-m --host xxx --port xxx --username xxx --password xxx
```

**Alternative** (use external classpath):
```bash
# Linux/macOS
java -cp "target/sqlancer-2.0.0.jar:target/lib/*" sqlancer.Main mysql --oracle NOREC

# Windows
java -cp "target/sqlancer-2.0.0.jar;target/lib/*" sqlancer.Main mysql --oracle NOREC
```

---

## Supported Databases and Oracles

### Oracle Quick Reference

| DBMS | Available Oracles |
|------|-------------------|
| **MySQL** | TLP_WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, CODDTEST, EET, FUZZER |
| **PostgreSQL** | NOREC, PQS, TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, QUERY_PARTITIONING, CERT, DQP, DQE, EET, CODDTEST, FUZZER |
| **GaussDB-A** | TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, EET, FUZZER |
| **GaussDB-PG** | TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, EET, FUZZER |
| **GaussDB-M** | TLP_WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, NOREC, QUERY_PARTITIONING, PQS, CERT, DQP, DQE, EET, CODDTEST, FUZZER |
| SQLite3 | NoREC, WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, QUERY_PARTITIONING, PQS, CODDTEST, FUZZER |
| TiDB | WHERE, HAVING, QUERY_PARTITIONING, CERT, DQP |
| CockroachDB | NOREC, WHERE, HAVING, AGGREGATE, GROUP_BY, DISTINCT, QUERY_PARTITIONING, CERT |
| DuckDB | NOREC, WHERE, HAVING, GROUP_BY, AGGREGATE, DISTINCT, QUERY_PARTITIONING |
| MariaDB | NOREC, DQP |

---

## Prerequisites

### System Requirements
- Java 17 or higher
- Database server (MySQL 8.0+, PostgreSQL 12-18, GaussDB)
- Network access to database server

### Database Setup
1. Ensure the database server is running and accessible
2. Create a user with sufficient permissions (CREATE/DROP DATABASE/SCHEMA)
3. Test databases/schemas will be created/dropped automatically

---

# MySQL Testing

## Basic Usage

```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 3306 \
    --username root \
    --password your_password \
    mysql --oracle QUERY_PARTITIONING
```

## MySQL Oracles

| Oracle | What It Tests | Performance |
|--------|---------------|-------------|
| NOREC | Optimization correctness | ~3000 queries/s |
| TLP_WHERE | WHERE clause partitioning | ~2700 queries/s |
| HAVING | HAVING clause partitioning | Moderate |
| AGGREGATE | Aggregate function correctness | Moderate |
| DISTINCT | DISTINCT handling | Moderate |
| GROUP_BY | GROUP BY expressions | Moderate |
| QUERY_PARTITIONING | Combined WHERE+HAVING+AGGREGATE+DISTINCT+NOREC | Default |
| PQS | Pivoted Query Synthesis | Moderate |
| CERT | Cardinality estimation | Moderate |
| DQP | Differential Query Plans | ~2000 queries/s |
| DQE | SELECT/UPDATE/DELETE equivalence | ~23 queries/s |
| EET | Equivalent Expression Transformation | ~340 queries/s |
| CODDTEST | Constant-driven optimization testing | Moderate |
| FUZZER | Random query generation | ~3000 queries/s |

## MySQL Data Types

| Type Category | Types Supported |
|---------------|-----------------|
| Numeric | INT, BIGINT, SMALLINT, TINYINT, FLOAT, DOUBLE, DECIMAL, BIT |
| String | VARCHAR, TEXT, TINYTEXT, MEDIUMTEXT, LONGTEXT, CHAR |
| Binary | BINARY, VARBINARY, BLOB, TINYBLOB, MEDIUMBLOB, LONGBLOB |
| Temporal | DATE, TIME, DATETIME, TIMESTAMP, YEAR |
| JSON | JSON (with JSON_TYPE, JSON_VALID functions) |
| Enum/Set | ENUM, SET |
| Spatial | GEOMETRY, POINT, LINESTRING, POLYGON |

## MySQL Feature Flags

### Constraint Testing
| Option | Default | Description |
|--------|---------|-------------|
| `--test-foreign-keys` | true | Test foreign key constraints |
| `--test-check-constraints` | true | Allow CHECK constraints |
| `--test-generated-columns` | true | Test generated columns |
| `--test-primary-keys` | true | Allow primary keys |
| `--test-unique-constraints` | true | Allow unique constraints |

### Query Features
| Option | Default | Description |
|--------|---------|-------------|
| `--test-joins` | true | Allow JOIN clauses |
| `--test-in-operator` | true | Allow IN operator |
| `--test-window-functions` | true | Allow window functions |
| `--test-common-table-expressions` | true | Allow WITH clauses (CTE) |
| `--test-union` | true | Allow UNION statements |
| `--test-group-sets` | true | Allow GROUPING SETS, ROLLUP, CUBE |

### Storage Engines
```bash
mysql --engines InnoDB,MyISAM,MEMORY
```

---

# PostgreSQL Testing

## Basic Usage

```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 5432 \
    --username postgres \
    --password your_password \
    postgres --oracle QUERY_PARTITIONING
```

## PostgreSQL Oracles

| Oracle | What It Tests | Performance |
|--------|---------------|-------------|
| NOREC | Optimization correctness | ~3000 queries/s |
| TLP_WHERE | WHERE clause partitioning | ~800 queries/s |
| HAVING | HAVING clause partitioning | Moderate |
| AGGREGATE | Aggregate correctness | Moderate |
| DISTINCT | DISTINCT handling | Moderate |
| GROUP_BY | GROUP BY expressions | Moderate |
| QUERY_PARTITIONING | Combined TLP_WHERE+HAVING+AGGREGATE | Default |
| PQS | Pivoted Query Synthesis | Moderate |
| CERT | Cardinality estimation | Moderate |
| DQP | Differential Query Plans | Moderate |
| DQE | SELECT/UPDATE/DELETE equivalence | Moderate |
| EET | Equivalent Expression Transformation | Moderate |
| CODDTEST | Constant-driven optimization | Moderate |
| FUZZER | Random queries | ~3000 queries/s |

## PostgreSQL Data Types

| Type Category | Types Supported |
|---------------|-----------------|
| Numeric | INT, BIGINT, SMALLINT, SERIAL, BIGSERIAL, FLOAT, DOUBLE, DECIMAL |
| String | VARCHAR, CHAR, TEXT |
| Temporal | DATE, TIME, TIMETZ, TIMESTAMP, TIMESTAMPTZ |
| JSON | JSON, JSONB |
| Binary | BYTEA |
| UUID | UUID |
| Array | ARRAY types (INT[], VARCHAR[], etc.) |
| Enum | ENUM types |
| Spatial | GEOMETRY, POINT (with PostGIS extension) |

## PostgreSQL-Specific Options

```bash
--coverage-policy BALANCED          # CONSERVATIVE|BALANCED|AGGRESSIVE
--pg-table-columns 10               # Number of columns in CREATE TABLE
--pg-generate-sql-num 3             # Rows in INSERT ... VALUES
--pg-index-model 0                  # 0-6 for different index patterns
--extensions "pg_trgm,postgis"      # Pre-create extensions
```

---

# GaussDB-A Testing (A-Compatibility / Oracle-style)

## Introduction

GaussDB-A supports Oracle-style SQL syntax and data types. Uses built-in openGauss JDBC driver with SM3 authentication support.

**⚠️ IMPORTANT: `--target-database` is REQUIRED**

You must specify an A-compatible database. The tool will NOT connect to `postgres` by default.

## Prerequisites

**Step 1: Create A-compatible database (REQUIRED)**
```sql
-- Connect to GaussDB as administrator
CREATE DATABASE gaussdb_a_test WITH dbcompatibility 'A';

-- Verify compatibility mode
SELECT datcompatibility FROM pg_database WHERE datname = 'gaussdb_a_test';
-- Should return: A
```

**Step 2: Grant permissions**
```sql
-- Grant CREATE/DROP SCHEMA permissions to test user
GRANT ALL PRIVILEGES ON DATABASE gaussdb_a_test TO root;
```

## Basic Usage

```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 8000 \
    --username root --password password \
    --password your_password \
    gaussdb-a --target-database gaussdb_a_test --oracle QUERY_PARTITIONING
```

## Error Handling

**Without `--target-database`:**
```
Exception: The following option is required: [--target-database]

Usage: gaussdb-a [options]
  * --target-database    A-compatible database to connect (REQUIRED)
```

**If database is not A-compatible:**
```
[WARN] Database 'xxx' compatibility mode is not 'A'
[WARN] To create A-compatible database: CREATE DATABASE xxx WITH dbcompatibility 'A'
```

## Test Isolation Strategy

GaussDB-A uses **Schema isolation** (not database isolation):
- Connects to specified A-compatible database
- Creates schemas: `database0`, `database1`, `database2`...
- Sets `search_path` to test schema
- Tables created within schema for isolation

This follows Oracle's schema-based isolation pattern.

## GaussDB-A Oracles

| Oracle | Description |
|--------|-------------|
| TLP_WHERE | TLP WHERE clause partitioning |
| HAVING | TLP HAVING clause partitioning |
| AGGREGATE | TLP aggregate function partitioning |
| DISTINCT | TLP DISTINCT partitioning |
| GROUP_BY | TLP GROUP BY partitioning |
| NOREC | NoREC optimization detection |
| QUERY_PARTITIONING | Combined oracle (default) |
| PQS | Pivoted Query Synthesis |
| CERT | Cardinality estimation testing |
| DQP | Differential Query Plans |
| DQE | SELECT/UPDATE/DELETE equivalence |
| EET | Equivalent Expression Transformation |
| FUZZER | Random query generation |

## GaussDB-A Data Types

| Type Category | Types Supported |
|---------------|-----------------|
| Numeric | NUMBER, INTEGER, BIGINT, FLOAT, DOUBLE |
| String | VARCHAR2, CHAR, CLOB (optional) |
| Temporal | DATE, TIMESTAMP |
| Binary | BLOB (optional, enable with `--enable-clob-blob`) |
| Boolean | BOOLEAN |

## GaussDB-A Options

| Option | Required | Description |
|--------|----------|-------------|
| `--target-database` | **YES** | A-compatible database name (must exist) |
| `--enable-clob-blob` | No | Enable CLOB/BLOB type generation |

## Complete Example

```bash
# 1. Create A-compatible database first
# CREATE DATABASE gaussdb_a_test WITH dbcompatibility 'A';

# 2. Run comprehensive test
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 8000 \
    --username root --password password \
    --username root --password password \
    --num-tries 10 \
    --timeout-seconds 300 \
    gaussdb-a \
    --target-database gaussdb_a_test \
    --oracle QUERY_PARTITIONING,DQP,EET
```

---

# GaussDB-PG Testing (PG-Compatibility)

## Introduction

GaussDB-PG supports PostgreSQL-style SQL syntax. Uses built-in openGauss JDBC driver.

## Prerequisites

**Step 1: Create PG-compatible database (REQUIRED)**
```sql
CREATE DATABASE gaussdb_pg_test WITH dbcompatibility 'pg';

-- Verify
SELECT datcompatibility FROM pg_database WHERE datname = 'gaussdb_pg_test';
-- Should return: pg
```

## Basic Usage

```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 8000 \
    --username root --password password \
    --password your_password \
    gaussdb-pg --target-database gaussdb_pg_test --oracle QUERY_PARTITIONING
```

## Test Isolation Strategy

Same as GaussDB-A: **Schema isolation** within specified database.
- Creates schemas: `database0`, `database1`...
- Sets `search_path` for isolation

## GaussDB-PG Oracles

Same oracle set as GaussDB-A, with PostgreSQL-compatible syntax.

| Oracle | Description |
|--------|-------------|
| TLP_WHERE | TLP WHERE clause partitioning |
| HAVING | TLP HAVING clause partitioning |
| AGGREGATE | TLP aggregate function partitioning |
| DISTINCT | TLP DISTINCT partitioning |
| GROUP_BY | TLP GROUP BY partitioning |
| NOREC | NoREC optimization detection |
| QUERY_PARTITIONING | Combined oracle (default) |
| PQS | Pivoted Query Synthesis |
| CERT | Cardinality estimation testing |
| DQP | Differential Query Plans |
| DQE | SELECT/UPDATE/DELETE equivalence |
| EET | Equivalent Expression Transformation |
| FUZZER | Random query generation |

## GaussDB-PG Data Types

| Type Category | Types Supported |
|---------------|-----------------|
| Numeric | INT, BIGINT, SMALLINT, FLOAT, DOUBLE, DECIMAL |
| String | VARCHAR, CHAR, TEXT |
| Temporal | DATE, TIME, TIMESTAMP (optional, enable with `--enable-time-types`) |
| Boolean | BOOLEAN |

## GaussDB-PG Options

| Option | Required | Description |
|--------|----------|-------------|
| `--target-database` | **YES** | PG-compatible database name |
| `--enable-time-types` | No | Enable DATE, TIME, TIMESTAMP types |

## Complete Example

```bash
# Create PG-compatible database first
# CREATE DATABASE gaussdb_pg_test WITH dbcompatibility 'pg';

java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 8000 \
    --username root --password password \
    --username root --password password \
    gaussdb-pg \
    --target-database gaussdb_pg_test \
    --enable-time-types \
    --oracle QUERY_PARTITIONING
```

---

# GaussDB-M Testing (M-Compatibility / MySQL-style)

## Introduction

GaussDB-M supports MySQL-style SQL syntax. Uses built-in openGauss JDBC driver with automatic M-compatibility database creation.

## Basic Usage

```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 19995 \
    --username root \
    --password password \
    gaussdb-m --oracle QUERY_PARTITIONING
```

**Note**: GaussDB-M automatically creates M-compatible test databases. No `--target-database` required.

## Test Isolation Strategy

GaussDB-M uses **Database isolation**:
- Connects to `postgres` initially
- Creates databases: `database0`, `database1`... with `DBCOMPATIBILITY 'M'`
- Switches to test database for testing

This follows MySQL's database-based isolation pattern.

## GaussDB-M Oracles

| Oracle | Description |
|--------|-------------|
| TLP_WHERE | TLP WHERE clause partitioning |
| HAVING | TLP HAVING clause partitioning |
| GROUP_BY | TLP GROUP BY partitioning |
| AGGREGATE | TLP aggregate function partitioning |
| DISTINCT | TLP DISTINCT partitioning |
| NOREC | NoREC optimization detection |
| QUERY_PARTITIONING | Combined oracle (default) |
| PQS | Pivoted Query Synthesis |
| CERT | Cardinality estimation testing |
| DQP | Differential Query Plans |
| DQE | SELECT/UPDATE/DELETE equivalence |
| EET | Equivalent Expression Transformation |
| CODDTEST | Constant-driven optimization testing |
| FUZZER | Random query generation |

## GaussDB-M Options

| Option | Description |
|--------|-------------|
| No specific options | Uses global MySQL-compatible settings |

## Complete Example

```bash
java -jar target/sqlancer-2.0.0.jar \
    --host localhost \
    --port 19995 \
    --username root \
    --password password \
    --num-tries 10 \
    --timeout-seconds 300 \
    gaussdb-m \
    --oracle QUERY_PARTITIONING,AGGREGATE,DQP
```

## GaussDB Compatibility Modes Comparison

| Mode | Database Syntax | Isolation Method | `--target-database` |
|------|-----------------|------------------|---------------------|
| **GaussDB-A** | Oracle-style | Schema | **REQUIRED** |
| **GaussDB-PG** | PostgreSQL-style | Schema | **REQUIRED** |
| **GaussDB-M** | MySQL-style | Database | Optional (auto-creates) |

---

# Global Options

## Common Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `--num-threads N` | Number of concurrent test threads | 1 |
| `--num-tries N` | Number of test iterations | 1 |
| `--timeout-seconds N` | Maximum runtime per iteration | 120 |
| `--seed N` | Random seed for reproduction | Time-based |
| `--log-dir <dir>` | Log directory base | logs/<dbms>/ |
| `--log-each-select` | Log each SQL statement | true |
| `--use-reducer` | Enable delta-debugging reduction | false |
| `--qpg-enable` | Enable Query Plan Guidance | false |

---

# Test Scenarios

## Quick Bug Hunting

```bash
# MySQL
java -jar sqlancer.jar mysql --oracle NOREC,TLP_WHERE --num-tries 50 --timeout-seconds 60

# PostgreSQL
java -jar sqlancer.jar postgres --oracle NOREC,TLP_WHERE --num-tries 50 --timeout-seconds 60

# GaussDB-PG (requires target-database)
java -jar sqlancer.jar gaussdb-pg --target-database gaussdb_pg_test \
    --oracle TLP_WHERE --num-tries 50 --timeout-seconds 60

# GaussDB-A (requires target-database)
java -jar sqlancer.jar gaussdb-a --target-database gaussdb_a_test \
    --oracle TLP_WHERE --num-tries 50 --timeout-seconds 60

# GaussDB-M (auto-creates database)
java -jar sqlancer.jar gaussdb-m --oracle TLP_WHERE --num-tries 50 --timeout-seconds 60
```

## Comprehensive Testing

```bash
# MySQL with all oracles
java -jar sqlancer.jar mysql \
    --oracle QUERY_PARTITIONING,DQE,DQP,EET \
    --num-tries 100 --timeout-seconds 300

# PostgreSQL with aggressive coverage
java -jar sqlancer.jar postgres \
    --oracle QUERY_PARTITIONING,DQE,DQP,EET \
    --coverage-policy AGGRESSIVE \
    --pg-table-columns 12 \
    --pg-generate-sql-num 5

# GaussDB-A comprehensive
java -jar sqlancer.jar gaussdb-a \
    --target-database gaussdb_a_test \
    --oracle QUERY_PARTITIONING,DQP,DQE,EET \
    --num-tries 100 --timeout-seconds 300

# GaussDB-PG comprehensive
java -jar sqlancer.jar gaussdb-pg \
    --target-database gaussdb_pg_test \
    --enable-time-types \
    --oracle QUERY_PARTITIONING,DQP,DQE,EET \
    --num-tries 100 --timeout-seconds 300
```

## Bug Reproduction

```bash
java -jar sqlancer.jar mysql \
    --seed 1776222510722 \
    --oracle EET \
    --num-tries 1

# GaussDB-A reproduction
java -jar sqlancer.jar gaussdb-a \
    --target-database gaussdb_a_test \
    --seed 1776222510722 \
    --oracle EET \
    --num-tries 1
```

---

# Performance and Stability

## Verified Performance (v0.1.73)

| Database | Oracle | Queries/s | Success Rate |
|----------|--------|-----------|--------------|
| MySQL | QUERY_PARTITIONING | ~2700 | 95% |
| MySQL | DQP | ~2000 | 99% |
| MySQL | DQE | ~23 | 96% |
| MySQL | EET | ~340 | 62% |
| PostgreSQL | QUERY_PARTITIONING | ~700 | 53% |
| GaussDB-PG | QUERY_PARTITIONING | ~3500 | 53% |
| GaussDB-A | TLP_WHERE | ~4000 | 62% |

## Multi-threaded Testing

```bash
--num-threads 4 --num-tries 10
```

---

# Troubleshooting

## Connection Issues

| Error | Solution |
|-------|----------|
| Connection refused | Verify host/port, check DBMS running |
| Access denied | Create user with proper permissions |
| SSL/TLS error | Add `sslmode=disable` to connection URL |

## GaussDB-Specific Issues

| Error | Solution |
|-------|----------|
| `--target-database is required` | Create A/PG-compatible database and specify with `--target-database` |
| "datcompatibility" field not found | PostgreSQL database connected, need GaussDB instance |
| Compatibility mode is not 'A' or 'pg' | Recreate database: `CREATE DATABASE xxx WITH dbcompatibility 'A'` |
| Session initialization failed | Check network connectivity and credentials |
| Cannot create schema | Grant CREATE SCHEMA permission to user |

## Creating Compatibility Databases

```sql
-- A-compatibility (Oracle-style)
CREATE DATABASE gaussdb_a_test WITH dbcompatibility 'A';

-- PG-compatibility (PostgreSQL-style)
CREATE DATABASE gaussdb_pg_test WITH dbcompatibility 'pg';

-- M-compatibility (MySQL-style) - auto-created by SQLancer
-- CREATE DATABASE gaussdb_m_test WITH dbcompatibility 'B';
```

## Memory Issues

```bash
java -Xmx4g -jar sqlancer.jar ...
```

---

# Best Practices

1. **Start with default settings** - well-tested and stable
2. **Use QUERY_PARTITIONING** for comprehensive coverage
3. **Save seeds** for interesting behaviors and bugs
4. **Monitor logs** in `logs/<dbms>/<oracle>_YYYY_MMDD_HHMM/`
5. **Test reproduction** before reporting bugs
6. **Use appropriate timeouts** - EET/DQE need more time

---

# Oracle Selection Guide

| Goal | Recommended Oracles |
|------|---------------------|
| Performance bugs | NOREC, DQP, CERT |
| Query correctness | TLP_WHERE, QUERY_PARTITIONING |
| DML correctness | DQE |
| Expression bugs | EET |
| Semantic correctness | CODDTEST |
| Crash detection | FUZZER |
| Comprehensive | QUERY_PARTITIONING + EET + DQE + DQP |

---

# Version History

## v0.1.83 (2026-04-25)
- **GaussDB-A/PG**: `--target-database` now REQUIRED
- **Error messages**: Clear guidance when parameter missing
- **Documentation**: Updated with prerequisite steps
- **Schema isolation**: Clarified A/PG use schema, M uses database

## v0.1.82 (2026-04-25)
- **Packaging**: All dependencies bundled into single jar (~387MB)
- **Driver**: Unified openGauss JDBC driver (supports SM3 authentication)
- **lib directory**: Local driver jars, no Maven download required
- **Simplified**: Run directly with `java -jar`

## v0.1.73 (2026-04-21)
- Integration verification: MySQL, PostgreSQL, GaussDB-PG, GaussDB-A all stable
- Fixed MySQL ALTER TABLE on views issue
- Fixed MySQL DQE oracle rowId column name
- Fixed MySQL JSON column indexing expected errors
- Verified all databases can run continuously discovering logic bugs

## v0.1.72 (2026-04-20)
- Optimized PostgreSQL query locking strategy
- Fixed log directory minute-based splitting issue

## v0.1.70-71 (2026-04-18-20)
- PostgreSQL code line convergence
- Added PostgreSQL temporal/JSON/UUID/ARRAY/ENUM support
- GaussDB-A constraint and DML generation
- MySQL JSON/BLOB/BINARY type support

---

# Getting Help

1. Check log files in `logs/<dbms>/`
2. Use `--seed` to reproduce issues
3. Refer to README.md for quick start
4. See docs/ directory for detailed documentation