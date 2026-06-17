# SQLancer — Database Logic Bug Detection

> Extended version of [SQLancer](https://github.com/sqlancer/sqlancer) with 25+ test oracles for MySQL, PostgreSQL, GaussDB-A, GaussDB-M.

**Version**: v2.7.2 (2026-06-17) · [Release Notes](docs/release_notes.md) · [Full User Guide](docs/USER_GUIDE.md) · [中文指南](docs/user_guide_cn.md)

---

## Quick Start

### Build

```bash
git clone https://github.com/jack-xiao-china/sqlancer-multi-db.git
cd sqlancer-multi-db
mvn clean package -DskipTests
# Output: target/sqlancer-2.7.2.jar (~4MB, deps in target/lib/)
```

### Run

CLI format: **global options before DBMS command, DBMS options after it**.

```bash
# MySQL
java -jar target/sqlancer-2.7.2.jar \
    --host localhost --port 3306 --username root --password your_password \
    mysql --oracle QUERY_PARTITIONING

# PostgreSQL
java -jar target/sqlancer-2.7.2.jar \
    --host localhost --port 5432 --username postgres --password your_password \
    postgres --oracle TLP_WHERE

# GaussDB-A (requires --target-database)
java -jar target/sqlancer-2.7.2.jar \
    --host your_host --port 8000 --username your_user --password your_password \
    gaussdb-a --target-database gaussdb_a_test --oracle QUERY_PARTITIONING

# GaussDB-M (requires --target-database)
java -jar target/sqlancer-2.7.2.jar \
    --host your_host --port 19995 --username your_user --password your_password \
    gaussdb-m --target-database gaussdb_m_test --oracle AGGREGATE

# CODDTEST with mode selection
java -jar target/sqlancer-2.7.2.jar \
    --host localhost --port 3306 --username root --password your_password \
    mysql --oracle CODDTEST --coddtest-model EXPRESSION
```

> ⚠️ GaussDB-A and GaussDB-M **require `--target-database`** pointing to a pre-created compatibility database. See [GaussDB Setup](#gaussdb-setup) below.

---

## Supported DBMS

| DBMS | Oracles | Key Features | Status |
|------|---------|--------------|--------|
| **MySQL** | 25 | JSON, BLOB, Spatial, Full-text, CTE, Window | ✅ Verified |
| **PostgreSQL** | 25 | Temporal, JSONB, Array, UUID, MERGE, COPY | ✅ Verified |
| **GaussDB-A** | 25 | Oracle-compatible SQL, CLOB/BLOB, CODDTEST, SONAR, EDC_RADAR | ✅ Verified |
| **GaussDB-M** | 25 | MySQL-compatible SQL, same Oracle set as MySQL | ✅ Verified |

---

## Test Oracle Overview

### Oracle Categories

| Category | Oracles | Description |
|----------|---------|-------------|
| **Standard** | NOREC, PQS, TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, CERT, FUZZER | From original SQLancer |
| **Expression Transform** | EET, CODDTEST | Expression equivalence tree & constant folding |
| **Mutation Equivalence** | DQE, DQP | DELETE/UPDATE equivalence & determinism |
| **Database Construction** | EDC_RADAR | Equivalent database construction (constraint optimization) |
| **Data Operation** | EDC_DATA | Equivalent data construction (data operation testing) |
| **Performance** | SONAR | Optimized vs unoptimized comparison |
| **Transaction/MVCC** | WRITE_CHECK, WRITE_CHECK_REPRODUCE, FUCCI, TX_INFER | Isolation & MVCC testing |
| **JOIN Optimizer** | **JIR** (SIGMOD 2026) | Join Implication Reasoning — 6 rules |
| **Composite** | QUERY_PARTITIONING | Combined (TLP_WHERE + HAVING + AGGREGATE) |

### CODDTEST Oracle

CODDTEST (Constant Optimization Detection via Differential Testing) detects **query optimization bugs** by comparing folded vs unfolded query results. Supports 3 modes via `--coddtest-model`:

| Mode | Description |
|------|-------------|
| `EXPRESSION` | Always use expression folding (predicate → constant) |
| `SUBQUERY` | Always use subquery folding |
| `RANDOM` | Randomly choose between expression and subquery |

Available on: **MySQL, PostgreSQL, GaussDB-M, GaussDB-A**

### Cross-DBMS Oracle Matrix

| Oracle | PostgreSQL | MySQL | GaussDB-M | GaussDB-A |
|--------|:----------:|:-----:|:---------:|:---------:|
| NOREC | ✅ | ✅ | ✅ | ✅ |
| PQS | ✅ | ✅ | ✅ | ✅ |
| TLP_WHERE | ✅ | ✅ | ✅ | ✅ |
| HAVING | ✅ | ✅ | ✅ | ✅ |
| AGGREGATE | ✅ | ✅ | ✅ | ✅ |
| DISTINCT | ✅ | ✅ | ✅ | ✅ |
| GROUP_BY | ✅ | ✅ | ✅ | ✅ |
| CERT | ✅ | ✅ | ✅ | ✅ |
| FUZZER | ✅ | ✅ | ✅ | ✅ |
| DQE | ✅ | ✅ | ✅ | ✅ |
| DQP | ✅ | ✅ | ✅ | ✅ |
| EET | ✅ | ✅ | ✅ | ✅ |
| EET_INSERT_SELECT | ✅ | ✅ | ✅ | ✅ |
| EET_UPDATE | ✅ | ✅ | ✅ | ✅ |
| EET_DELETE | ✅ | ✅ | ✅ | ✅ |
| **CODDTEST** | ✅ | ✅ | ✅ | ✅ |
| EDC_RADAR | ✅ | ✅ | ✅ | ✅ |
| EDC_DATA | ✅ | ✅ | ✅ | ✅ |
| SONAR | ✅ | ✅ | ✅ | ✅ |
| WRITE_CHECK | ✅ | ✅ | ✅ | ✅ |
| WRITE_CHECK_REPRODUCE | ✅ | ✅ | ✅ | ✅ |
| FUCCI | ✅ | ✅ | ✅ | ✅ |
| TX_INFER | ✅ | ✅ | ✅ | ✅ |
| **JIR** | ✅ | ✅ | ✅ | ✅ |
| QUERY_PARTITIONING | ✅ | ✅ | ✅ | ✅ |

---

## JIR Oracle (SIGMOD 2026)

Join Implication Reasoning detects **JOIN optimizer bugs** by comparing query results across different JOIN types using 6 semantic implication rules:

| Rule | Description | Example |
|------|-------------|---------|
| **1. LEFT_JOIN_DECOMPOSITION** | `L LEFT JOIN R ON c` = `L INNER JOIN R ON c` ∪ `L ANTI JOIN R ON c` | Decompose LEFT into INNER + ANTI |
| **2. LEFT_RIGHT_SYMMETRY** | `L LEFT JOIN R ON c` ≡ `R RIGHT JOIN L ON c` | Swap direction, same result |
| **3. SEMI_ANTI_COMPLEMENT** | `L ⋈ R` = `L SEMI JOIN R ON c` ∪ `L ANTI JOIN R ON c` | Semi + Anti cover all rows |
| **4. FULL_JOIN_DECOMPOSITION** | `L FULL JOIN R ON c` = INNER + LEFT_ANTI + RIGHT_ANTI | PG / GaussDB-A only |
| **5. CROSS_JOIN_EQUIVALENCE** | `L CROSS JOIN R` ≡ `L INNER JOIN R ON TRUE` | CROSS ≡ INNER with ON TRUE |
| **6. NATURAL_JOIN_EXPLICATION** | `NATURAL JOIN` ≡ `INNER JOIN ON (matching columns)` | Explicit equijoin condition |

```bash
java -jar target/sqlancer-2.7.2.jar --host localhost --port 3306 \
    --username root --password your_password \
    mysql --oracle JIR
```

> Paper: Rigger et al., "Detecting Join Bugs in Database Engines via Join Implication Reasoning", SIGMOD 2026. · [Papers](docs/PAPERS.md)

---

## GaussDB Setup

GaussDB-A and GaussDB-M require **pre-created compatibility databases**. SQLancer connects to the target database and uses **schema isolation** (DROP/CREATE SCHEMA + SET search_path) for test isolation.

### Create Compatibility Databases

```sql
-- A-compatible (Oracle style)
CREATE DATABASE testa WITH dbcompatibility 'A';

-- M-compatible (MySQL style)
CREATE DATABASE testm WITH DBCOMPATIBILITY 'M';
```

### Connect

```bash
# GaussDB-A
java -jar target/sqlancer-2.7.2.jar \
    --host your_host --port 8000 --username your_user --password your_password \
    gaussdb-a --target-database testa --oracle QUERY_PARTITIONING

# GaussDB-M
java -jar target/sqlancer-2.7.2.jar \
    --host your_host --port 19995 --username your_user --password your_password \
    gaussdb-m --target-database testm --oracle QUERY_PARTITIONING

# GaussDB-A with CODDTEST
java -jar target/sqlancer-2.7.2.jar \
    --host your_host --port 8000 --username your_user --password your_password \
    gaussdb-a --target-database testa --oracle CODDTEST --coddtest-model RANDOM
```

---

## Key Features (Extended vs Official SQLancer)

| Feature | Description |
|---------|-------------|
| **25+ Test Oracles** | 14 standard + 11 new (JIR, EDC_RADAR, EDC_DATA, SONAR, DQE, DQP, EET, CODDTEST, WRITE_CHECK, FUCCI, TX_INFER) |
| **GaussDB Multi-Mode** | A/M compatibility modes with schema isolation |
| **CODDTEST 3 Modes** | EXPRESSION / SUBQUERY / RANDOM via `--coddtest-model` |
| **Extended Data Types** | PG: Temporal, JSONB, Array, UUID · MySQL: JSON, Spatial, Full-text, CTE |
| **SQLSTATE Error Handling** | Works on non-English (Chinese, German, Japanese) servers |
| **Coverage Policy** | `--coverage-policy` (CONSERVATIVE/BALANCED/AGGRESSIVE) for PostgreSQL |
| **Cross-Platform** | Windows path handling, OS-aware tablespace defaults |
| **Lightweight Packaging** | ~4MB jar, deps in `target/lib/`, manifest Class-Path loading |

---

## Common Options

| Option | Description | Default |
|--------|-------------|---------|
| `--oracle <name>` | Test oracle(s), comma-separated | QUERY_PARTITIONING |
| `--num-queries N` | Queries per database | varies by oracle |
| `--num-tries N` | Test iterations | 1 |
| `--timeout-seconds N` | Max runtime per try | 120 |
| `--seed N` | Random seed for reproduction | timestamp |
| `--use-reducer` | Minimize bug-triggering queries | false |
| `--log-each-select` | Log every SQL statement | true |

### DBMS-Specific Options

| DBMS | Key Options |
|------|-------------|
| **PostgreSQL** | `--coverage-policy`, `--pg-table-columns`, `--pg-index-model`, `--extensions` |
| **MySQL** | `--engines`, `--test-json`, `--test-spatial`, `--test-unsigned`, `--test-window-functions` |
| **GaussDB-A** | `--target-database`, `--enable-clob-blob`, `--coddtest-model` |
| **GaussDB-M** | `--target-database`, `--coddtest-model` |

---

## Bug Reproduction

When a bug is found, SQLancer logs the seed and query:

```bash
java -jar target/sqlancer-2.7.2.jar --seed <seed_value> \
    --host localhost --port 3306 --username root --password your_password \
    mysql --oracle <oracle_name>
```

Logs are saved to `logs/<dbms>/<oracle>_YYYY_MMDD_HHMM/`.

---

## Documentation

| Document | Description |
|----------|-------------|
| [USER_GUIDE.md](docs/USER_GUIDE.md) | Complete user guide (English) |
| [user_guide_cn.md](docs/user_guide_cn.md) | 中文用户指南 |
| [README_NEW_FEATURES.md](docs/README_NEW_FEATURES.md) | New features vs official SQLancer |
| [PAPERS.md](docs/PAPERS.md) | Academic papers (PQS, NoREC, TLP, QPG, CERT, DQP, JIR) |
| [Release Notes](docs/release_notes.md) | Version history |

---

## Build with Custom Version

```bash
# Default version (from pom.xml)
mvn package -DskipTests
# → sqlancer-2.7.2.jar

# Custom version
mvn package -Drevision=2.8.0 -DskipTests
# → sqlancer-2.8.0.jar
```

---

## License

MIT License — same as [official SQLancer](https://github.com/sqlancer/sqlancer).
