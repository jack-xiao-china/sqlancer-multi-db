# SQLancer Extended - New Features README

## Overview

This is an extended version of SQLancer with additional features and improvements for both PostgreSQL and MySQL testing. It includes new test oracles, extended data type support, improved error handling for internationalized environments, and better cross-platform compatibility.

## New Features (Compared to Official SQLancer)

### 1. New Test Oracles

Four new test oracles have been added to provide more comprehensive database testing. These oracles are available for **both PostgreSQL and MySQL**:

#### DQE (Delete/Update Query Equivalence)
- **Purpose**: Tests that DELETE and UPDATE operations with the same WHERE clause affect the same rows
- **Mechanism**: Compares rows selected by a WHERE clause with rows affected by UPDATE and DELETE using the same predicate
- **Usage**: `--oracle DQE`
- **Characteristics**: Uses transactions with savepoints to leave database unchanged after testing

#### DQP (Deterministic Query Partitioning)
- **Purpose**: Tests query determinism - ensures the same query returns consistent results
- **Mechanism**: Executes the same SELECT query twice and compares results
- **Usage**: `--oracle DQP`
- **Characteristics**: Tests GROUP BY and HAVING clause stability

#### EET (Equivalent Expression Transformation)
- **Purpose**: Tests semantic equivalence of transformed expressions
- **Mechanism**: Applies equivalence-preserving transformations (e.g., `a = b` to `b = a`) and verifies results match
- **Usage**: `--oracle EET`
- **Characteristics**: Handles complex expression trees and multiset comparison

#### CODDTEST
- **Purpose**: Tests expression folding correctness based on Codd's relational algebra principles
- **Mechanism**: Evaluates a boolean predicate, folds it to a constant, and compares COUNT(*) results. Supports 3 modes:
  - `EXPRESSION`: Always use expression folding (predicate → constant)
  - `SUBQUERY`: Always use subquery folding
  - `RANDOM`: Randomly choose between expression and subquery
- **Usage**: `--oracle CODDTEST --coddtest-model EXPRESSION`
- **Supported DBMS**: MySQL, PostgreSQL, GaussDB-M, GaussDB-A
- **Characteristics**: Focuses on predicate evaluation consistency; detects constant folding and subquery optimization bugs

### 2. PostgreSQL Extended Type Coverage

PostgreSQL generators now cover the following type families by default without per-type feature flags:

- Temporal types: `DATE`, `TIME`, `TIMETZ`, `TIMESTAMP`, `TIMESTAMPTZ`, `INTERVAL`
- Semi-structured and binary types: `JSON`, `JSONB`, `BYTEA`
- Identifier and collection types: `UUID`, `ENUM`, `ARRAY`

The current PostgreSQL-specific tuning options are:

| Option | Description |
|--------|-------------|
| `--coverage-policy` | Controls stability vs. coverage (`BALANCED`, `CONSERVATIVE`, `AGGRESSIVE`) |
| `--pg-table-columns` | Number of columns in generated PostgreSQL tables |
| `--pg-generate-sql-num` | Number of rows generated per PostgreSQL `INSERT ... VALUES` |
| `--pg-index-model` | Force a specific PostgreSQL index generation model |
| `--extensions` | Comma-separated extensions created in each test database |
| `--connection-url` | PostgreSQL connection URL |

**Example**:
```bash
java -jar sqlancer.jar postgres --coverage-policy AGGRESSIVE --pg-index-model 6 --oracle WHERE
```

### 2.1 PostgreSQL DDL/DML Coverage

The PostgreSQL action set now includes additional schema and data-manipulation statements with conservative weights:

- DDL: `DROP TABLE`, `DROP VIEW`, `DROP SEQUENCE`, `ALTER SEQUENCE`, `ALTER INDEX`
- Object DDL: composite `CREATE TYPE`, simple SQL `CREATE FUNCTION`, `CREATE RULE`
- Index access methods: `BTREE`, `HASH`, `GIST`, `GIN`, `SPGIST`, `BRIN`
- DML: PostgreSQL 15+ `MERGE`
- COPY: low-frequency `COPY ... TO STDOUT` coverage probe

Trigger generation and privilege management statements such as `GRANT`/`REVOKE` are intentionally not enabled in this pass.

### 3. MySQL Extended Feature Flags

MySQL has extensive feature flags for fine-grained testing control:

#### MySQL-Specific Data Types
| Option | Description |
|--------|-------------|
| `--test-unsigned` | UNSIGNED integers (MySQL-specific) |
| `--test-zerofill` | ZEROFILL integers (MySQL-specific) |
| `--test-enums` | ENUM type |
| `--test-sets` | SET type (MySQL-specific) |
| `--test-json-data-type` | JSON data type |
| `--test-array-data-type` | ARRAY type (MySQL 8.0+) |

#### MySQL Feature Testing
| Option | Description |
|--------|-------------|
| `--test-spatial` | Spatial types (GEOMETRY, POINT, POLYGON) |
| `--test-full-text-search` | Full-text search features |
| `--test-window-functions` | Window functions |
| `--test-common-table-expressions` | WITH clauses (CTE) |
| `--test-triggers` | Triggers |
| `--test-procedures` | Stored procedures |
| `--test-events` | Scheduled events |
| `--test-generated-columns` | Generated columns |
| `--test-multi-table-delete` | Multi-table DELETE |
| `--test-multi-table-update` | Multi-table UPDATE |

#### MySQL Storage Engine Testing
```bash
mysql --engines InnoDB,MyISAM,MEMORY
```

**Example**:
```bash
java -jar sqlancer.jar mysql \
    --test-json true \
    --test-spatial true \
    --test-unsigned true \
    --engines InnoDB,MyISAM \
    --oracle QUERY_PARTITIONING
```

### 4. Coverage Policy (PostgreSQL)

The `--coverage-policy` option controls the balance between type/expression coverage and test stability:

| Policy | Description | Use Case |
|--------|-------------|----------|
| `BALANCED` | Default - moderate coverage | General testing |
| `CONSERVATIVE` | Limited to stable types | When bugs need clear reproduction |
| `AGGRESSIVE` | Maximum type coverage | Finding edge cases |

### 5. Improved Error Handling for Internationalized Environments

Added SQLSTATE-based error handling to support non-English database servers:

- **Problem**: Original error matching relied on English error messages, failing on localized servers (e.g., Chinese, German)
- **Solution**: Added `COMMON_EXPECTED_SQLSTATES` in `SQLQueryAdapter` that handles ~40 SQLSTATE codes covering:
  - Data exceptions (22xxx series)
  - Integrity constraint violations (23xxx series)
  - Syntax/access errors (42xxx series)
  - Feature not supported (0A000)

**Benefit**: Testing now works correctly regardless of server locale settings for both PostgreSQL and MySQL.

### 6. Cross-Platform Improvements

#### Windows Platform Support
- Default tablespace testing disabled on Windows (different path format)
- Use `--tablespace-path` to specify a valid Windows path if needed
- Automatic OS detection for appropriate defaults

#### Tablespace Testing (PostgreSQL)
- `--test-tablespaces`: Enable/disable tablespace creation testing
- `--tablespace-path`: Specify custom tablespace directory
- OS-dependent defaults:
  - Linux: Disabled by default
  - macOS: Disabled by default (different /tmp handling)
  - Windows: Disabled by default (use custom path)

## Quick Start

### PostgreSQL Basic Testing
```bash
java -jar sqlancer.jar \
    --host localhost --port 5432 \
    --username postgres --password password \
    postgres --oracle WHERE
```

### PostgreSQL Extended Testing with New Oracles
```bash
java -jar sqlancer.jar \
    --host localhost --port 5432 \
    --username postgres --password password \
    postgres --oracle DQE,DQP,EET,CODDTEST --coddtest-model RANDOM
```

### PostgreSQL Full Coverage Testing
```bash
java -jar sqlancer.jar \
    --host localhost --port 5432 \
    --username postgres --password password \
    postgres \
    --coverage-policy AGGRESSIVE \
    --pg-table-columns 12 \
    --pg-generate-sql-num 5 \
    --pg-index-model 6 \
    --oracle QUERY_PARTITIONING
```

### MySQL Basic Testing
```bash
java -jar sqlancer.jar \
    --host localhost --port 3306 \
    --username root --password password \
    mysql --oracle QUERY_PARTITIONING
```

### MySQL Extended Testing
```bash
java -jar sqlancer.jar \
    --host localhost --port 3306 \
    --username root --password password \
    mysql \
    --oracle DQE,DQP,EET,CODDTEST --coddtest-model RANDOM \
    --test-json true \
    --test-spatial true \
    --engines InnoDB,MyISAM
```

## Available Oracles

All 25+ oracles are available for **PostgreSQL, MySQL, and GaussDB-M**, with **25 oracles** for **GaussDB-A**:

| Oracle | Description | Requires Rows |
|--------|-------------|---------------|
| NOREC | No-rec oracle (index optimization) | No |
| PQS | Pivoted Query Synthesis | Yes (all tables) |
| WHERE | TLP WHERE variant | No |
| HAVING | TLP HAVING variant | No |
| AGGREGATE | TLP Aggregate variant | No |
| DISTINCT | TLP Distinct variant | No |
| GROUP_BY | TLP Group By variant | No |
| QUERY_PARTITIONING | Composite (WHERE+HAVING+AGGREGATE+DISTINCT+NOREC) | No |
| CERT | CERT oracle | Yes (all tables) |
| FUZZER | Random query fuzzer | No |
| **DQE** | Delete/Update Equivalence (NEW) | No |
| **DQP** | Deterministic Query Partitioning (NEW) | No |
| **EET** | Equivalent Expression Transformation (NEW) | No |
| **CODDTEST** | Expression folding test (3 modes: EXPRESSION/SUBQUERY/RANDOM) (NEW) | No |
| **EDC_RADAR** | Equivalent Database Construction - constraint optimization (NEW) | No |
| **EDC_DATA** | Equivalent Data Construction - data operation testing (NEW) | No |
| **SONAR** | Optimized vs unoptimized comparison (NEW) | No |
| **WRITE_CHECK** | Transaction isolation testing (NEW) | No |
| **WRITE_CHECK_REPRODUCE** | Reproduce WRITE_CHECK bugs (NEW) | No |
| **FUCCI** | MVCC-based testing (NEW) | No |
| **TX_INFER** | MVCC version inference (NEW) | No |
| **JIR** | Join Implication Reasoning (NEW — SIGMOD 2026) | No |

## Configuration Options Summary

### PostgreSQL-Specific Options

| Option | Default | Description |
|--------|---------|-------------|
| `--oracle` | QUERY_PARTITIONING | Test oracle(s) to use |
| `--coverage-policy` | BALANCED | Coverage strategy |
| `--pg-table-columns` | 10 | Number of columns in generated tables |
| `--pg-generate-sql-num` | 3 | Number of rows per generated INSERT |
| `--pg-index-model` | 0 | PostgreSQL index generation model |
| `--test-collations` | true | Test different collations |
| `--test-tablespaces` | false | Test tablespace creation |
| `--tablespace-path` | OS-dependent | Custom tablespace path |
| `--extensions` | "" | Extensions to create (comma-separated) |

### MySQL-Specific Options

| Option | Default | Description |
|--------|---------|-------------|
| `--oracle` | QUERY_PARTITIONING | Test oracle(s) to use |
| `--engines` | InnoDB | Storage engines (comma-separated) |
| `--test-json` | true | Allow JSON functions/operators |
| `--test-spatial` | true | Allow spatial types/functions |
| `--test-full-text-search` | true | Allow full-text search |
| `--test-window-functions` | true | Allow window functions |
| `--test-common-table-expressions` | true | Allow WITH clauses |
| `--test-unsigned` | true | Allow UNSIGNED integers |
| `--test-zerofill` | true | Allow ZEROFILL integers |
| `--test-enums` | true | Allow ENUM type |
| `--test-sets` | true | Allow SET type |
| `--test-json-data-type` | true | Allow JSON data type |
| `--test-array-data-type` | true | Allow ARRAY (MySQL 8.0+) |
| `--test-foreign-keys` | true | Test foreign key constraints |
| `--test-triggers` | true | Allow triggers |
| `--test-procedures` | true | Allow stored procedures |
| `--test-events` | true | Allow scheduled events |
| `--test-generated-columns` | true | Allow generated columns |

For full MySQL options list, see `USER_GUIDE.md`.

## Bug Reporting

When a bug is found, SQLancer generates:
1. Log files in `logs/<dbms>/<test_name>/` directory
2. Final log with the bug-triggering query
3. Seed value for reproduction

Use the seed value to reproduce:
```bash
# PostgreSQL
java -jar sqlancer.jar --seed <seed_value> postgres --oracle <oracle_name>

# MySQL
java -jar sqlancer.jar --seed <seed_value> mysql --oracle <oracle_name>
```

## License

Same as official SQLancer (MIT License).

## Contributing

Contributions welcome! Please ensure:
1. New features maintain compatibility with existing oracles
2. Add appropriate error handling for new error types
3. Document new command-line options

---

## Recent Feature Highlights (v2.7.3–v2.7.9)

| Version | Feature | Description |
|---------|---------|-------------|
| **v2.7.9** | PostgreSQL non-superuser full-oracle sweep | `readFunctions` skips superuser/restricted builtins (`pg_replication_origin_*`/`pg_ls_*`/`pg_promote`/`pg_read_file`/etc.); EDC_DATA `func.txt` seed cleaned of 79 restricted functions; `COMMON_EXPECTED_SQLSTATES` +8 states (`42701`/`42702`/`42710`/`23502`/`42830`/`42P04`/`55006`/`2BP01`) for non-English locale; SONAR `getUnoptimizedQuery` handles `PostgresBinaryComparisonOperation`; unhandled types `IgnoreMeException`; disabled `FORCE ROW LEVEL SECURITY` (RLS 42501 root cause) |
| **v2.7.8** | PostgreSQL non-superuser role fix | Removed `pg_current_logfile()` and 6 SUSET GUC params (`wal_compression`/`commit_delay`/`track_activities`/`track_counts`/`track_io_timing`/`track_functions`) from generation pools — eliminated `permission denied` false positives when testing cloud PostgreSQL instances with normal (non-superuser) roles |
| **v2.7.7** | PostgresIndex backsConstraint | `ADD CONSTRAINT USING INDEX` no longer selects indexes already backing a constraint (`EXISTS(pg_constraint WHERE conindid = indexrelid)` check) |
| **v2.7.6** | JIR P1: Multi-table JOIN chain | 2-3 table JOIN chains; preceding JOINs preserved, only last JOIN is transformation target |
| **v2.7.6** | JIR P1: CROSS 4 variants | Rule 5 expanded: CROSS ≡ INNER/LEFT/RIGHT/FULL JOIN ON TRUE (PostgreSQL adds FULL variant) |
| **v2.7.6** | JIR P1: Rule 4 full-anti | FULL JOIN decomposition now covers all columns (INNER + left-anti + right-anti) |
| **v2.7.5** | MySQLWildcard fix | `SELECT * AS ref0` no longer generated — `SELECT *` stays valid SQL for JIR |
| **v2.7.4** | JIR Rule3 algorithm fix | Corrected column-selection and algorithm bugs in semi-anti complement rule |
| **v2.7.4** | Row-level comparison | `ComparatorHelper.getResultSetAllColumnsAsString()` for multi-column equality checks |
| **v2.7.3** | JIR NPE fix | Null-safe sort in result comparison |
| **v2.7.3** | GaussDB-A CODDTEST robustness | Visitor/error-handling fixes for GaussDB-A CODDTEST modes |
| **v2.7.1** | GaussDB-A 25 oracle parity | SONAR + EDC_RADAR added → GaussDB-A reaches full 25 oracle parity with PostgreSQL |
