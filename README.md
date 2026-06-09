# SQLancer — Database Logic Bug Detection

> Extended version of [SQLancer](https://github.com/sqlancer/sqlancer) with 25+ test oracles for MySQL, PostgreSQL, GaussDB-A, GaussDB-M, and more.

**Version**: v2.4.5 (2026-06-09) · [Release Notes](docs/release_notes.md) · [Full User Guide](docs/USER_GUIDE.md)

---

## Quick Start

### Build

```bash
git clone https://github.com/jack-xiao-china/sqlancer-multi-db.git
cd sqlancer-multi-db
mvn clean package -DskipTests
# Output: target/sqlancer-2.4.5.jar (~4MB, deps in target/lib/)
```

### Run

CLI format: **global options before DBMS command, DBMS options after it**.

```bash
# MySQL
java -jar target/sqlancer-2.4.5.jar \
    --host localhost --port 3306 --username root --password your_password \
    mysql --oracle QUERY_PARTITIONING

# PostgreSQL
java -jar target/sqlancer-2.4.5.jar \
    --host localhost --port 5432 --username postgres --password your_password \
    postgres --oracle TLP_WHERE

# GaussDB-A (requires --target-database)
java -jar target/sqlancer-2.4.5.jar \
    --host your_host --port 8000 --username your_user --password your_password \
    gaussdb-a --target-database gaussdb_a_test --oracle QUERY_PARTITIONING

# GaussDB-M (requires --target-database)
java -jar target/sqlancer-2.4.5.jar \
    --host your_host --port 19995 --username your_user --password your_password \
    gaussdb-m --target-database gaussdb_m_test --oracle AGGREGATE
```

> ⚠️ GaussDB-A and GaussDB-M **require `--target-database`** pointing to a pre-created compatibility database. See [GaussDB Setup](#gaussdb-setup) below.

---

## Supported DBMS

| DBMS | Oracles | Key Features | Status |
|------|---------|--------------|--------|
| **MySQL** | 24 | JSON, BLOB, Spatial, Full-text, CTE, Window | ✅ Verified |
| **PostgreSQL** | 24 | Temporal, JSONB, Array, UUID, MERGE, COPY | ✅ Verified |
| **GaussDB-A** | 21 | CLOB, BLOB, Oracle-compatible SQL | ✅ Verified |
| **GaussDB-M** | 24 | MySQL-compatible SQL, same Oracle set as MySQL | ✅ Verified |
| **GaussDB-PG** | 21 | PostgreSQL-compatible SQL | ✅ Verified |
| SQLite3 | 10 | Baseline coverage | ✅ |
| TiDB | 5 | Limited set | ✅ |
| CockroachDB | 8 | Baseline coverage | ✅ |
| DuckDB | 7 | Baseline coverage | ✅ |
| MariaDB | 2 | NOREC, DQP | ✅ |

---

## Test Oracle Overview

### Oracle Categories

| Category | Oracles | Description |
|----------|---------|-------------|
| **Standard** | NOREC, PQS, TLP_WHERE, HAVING, AGGREGATE, DISTINCT, GROUP_BY, CERT, FUZZER | From original SQLancer |
| **TLP Extensions** | WHERE (PostgreSQL alias for TLP_WHERE) | Alias |
| **Expression Transform** | EET, CODDTEST | Semantic equivalence & constant folding |
| **Mutation Equivalence** | DQE, DQP | DELETE/UPDATE equivalence & determinism |
| **Database Construction** | EDC | Equivalent database construction |
| **Performance** | SONAR (MySQL/PG/GaussDB-M) | Optimized vs unoptimized comparison |
| **Transaction/MVCC** | WRITE_CHECK, WRITE_CHECK_REPRODUCE, FUCCI, TX_INFER | Isolation & MVCC testing |
| **JOIN Optimizer** | **JIR** (SIGMOD 2026) | Join Implication Reasoning — 6 rules |
| **Composite** | QUERY_PARTITIONING | Combined (TLP_WHERE + HAVING + AGGREGATE + ...) |

### Cross-DBMS Oracle Matrix

| Oracle | PostgreSQL | MySQL | GaussDB-M | GaussDB-A | GaussDB-PG |
|--------|:----------:|:-----:|:---------:|:---------:|:----------:|
| NOREC | ✅ | ✅ | ✅ | ✅ | ✅ |
| PQS | ✅ | ✅ | ✅ | ✅ | ✅ |
| TLP_WHERE / WHERE | ✅ | ✅ | ✅ | ✅ | ✅ |
| HAVING | ✅ | ✅ | ✅ | ✅ | ✅ |
| AGGREGATE | ✅ | ✅ | ✅ | ✅ | ✅ |
| DISTINCT | ✅ | ✅ | ✅ | ✅ | ✅ |
| GROUP_BY | ✅ | ✅ | ✅ | ✅ | ✅ |
| CERT | ✅ | ✅ | ✅ | ✅ | ✅ |
| FUZZER | ✅ | ✅ | ✅ | ✅ | ✅ |
| DQE | ✅ | ✅ | ✅ | ✅ | ✅ |
| DQP | ✅ | ✅ | ✅ | ✅ | ✅ |
| EET | ✅ | ✅ | ✅ | ✅ | ✅ |
| CODDTEST | ✅ | ✅ | ✅ | — | — |
| EDC | ✅ | ✅ | ✅ | ✅ | ✅ |
| SONAR | ✅ | ✅ | ✅ | — | — |
| WRITE_CHECK | ✅ | ✅ | ✅ | ✅ | ✅ |
| WRITE_CHECK_REPRODUCE | ✅ | ✅ | ✅ | ✅ | ✅ |
| FUCCI | ✅ | ✅ | ✅ | ✅ | ✅ |
| TX_INFER | ✅ | ✅ | ✅ | ✅ | ✅ |
| **JIR** | ✅ | ✅ | ✅ | ✅ | ✅ |
| QUERY_PARTITIONING | ✅ | ✅ | ✅ | ✅ | ✅ |

> Full descriptions and DBMS-specific details → [USER_GUIDE.md](docs/USER_GUIDE.md)

---

## JIR Oracle (New — SIGMOD 2026)

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
java -jar target/sqlancer-2.4.5.jar --host localhost --port 3306 \
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
CREATE DATABASE gaussdb_a_test WITH dbcompatibility 'A';

-- M-compatible (MySQL style)
CREATE DATABASE gaussdb_m_test WITH dbcompatibility 'B';

-- PG-compatible (PostgreSQL style)
CREATE DATABASE gaussdb_pg_test WITH dbcompatibility 'pg';
```

### Connect

```bash
# GaussDB-A
java -jar target/sqlancer-2.4.5.jar \
    --host your_host --port 8000 --username your_user --password your_password \
    gaussdb-a --target-database gaussdb_a_test --oracle QUERY_PARTITIONING

# GaussDB-M (M-compatibility mode)
java -jar target/sqlancer-2.4.5.jar \
    --host your_host --port 19995 --username your_user --password your_password \
    gaussdb-m --target-database gaussdb_m_test --oracle QUERY_PARTITIONING

# GaussDB-PG (PG-compatibility mode, no --target-database needed)
java -jar target/sqlancer-2.4.5.jar \
    --host your_host --port 5432 --username your_user --password your_password \
    gaussdb-pg --oracle QUERY_PARTITIONING
```

---

## Key Features (Extended vs Official SQLancer)

| Feature | Description |
|---------|-------------|
| **25+ Test Oracles** | 14 standard + 11 new (JIR, EDC, SONAR, DQE, DQP, EET, CODDTEST, WRITE_CHECK, FUCCI, TX_INFER, WRITE_CHECK_REPRODUCE) |
| **GaussDB Multi-Mode** | A/PG/M compatibility modes with schema isolation |
| **Extended Data Types** | PG: Temporal, JSONB, Array, UUID · MySQL: JSON, Spatial, Full-text, CTE |
| **SQLSTATE Error Handling** | Works on non-English (Chinese, German, Japanese) servers — no English-only error matching |
| **Coverage Policy** | `--coverage-policy` (CONSERVATIVE/BALANCED/AGGRESSIVE) for PostgreSQL type/expression depth |
| **Cross-Platform** | Windows path handling, OS-aware tablespace defaults |
| **Lightweight Packaging** | ~4MB jar, deps in `target/lib/`, manifest Class-Path loading |

---

## Common Options

| Option | Description | Default |
|--------|-------------|---------|
| `--oracle <name>` | Test oracle(s), comma-separated | QUERY_PARTITIONING |
| `--num-queries N` | Queries per database | varies by oracle |
| `--num-tries N` | Test iterations (higher = more bug findings) | 1 |
| `--timeout-seconds N` | Max runtime per try | 120 |
| `--seed N` | Random seed for reproduction | timestamp |
| `--use-reducer` | Minimize bug-triggering queries | false |
| `--log-each-select` | Log every SQL statement | true |

### DBMS-Specific Options

| DBMS | Key Options |
|------|-------------|
| **PostgreSQL** | `--coverage-policy`, `--pg-table-columns`, `--pg-index-model`, `--extensions` |
| **MySQL** | `--engines`, `--test-json`, `--test-spatial`, `--test-unsigned`, `--test-window-functions` |
| **GaussDB-A** | `--target-database`, `--enable-clob-blob` |
| **GaussDB-M** | `--target-database` |

> Full option list → [USER_GUIDE.md](docs/USER_GUIDE.md)

---

## Bug Reproduction

When a bug is found, SQLancer logs the seed and query:

```bash
# Reproduce with the seed
java -jar target/sqlancer-2.4.5.jar --seed <seed_value> \
    --host localhost --port 3306 --username root --password your_password \
    mysql --oracle <oracle_name>
```

Logs are saved to `logs/<dbms>/<test_name>/`.

---

## Documentation

| Document | Description |
|----------|-------------|
| [USER_GUIDE.md](docs/USER_GUIDE.md) | Complete user guide (English) |
| [user_guide_cn.md](docs/user_guide_cn.md) | 中文用户指南 |
| [README_NEW_FEATURES.md](docs/README_NEW_FEATURES.md) | New features vs official SQLancer |
| [PAPERS.md](docs/PAPERS.md) | Academic papers (PQS, NoREC, TLP, QPG, CERT, DQP, JIR) |
| [Oracle Statistics](docs/sqlancer_oracle_supported_statistics_20260415.md) | Cross-DBMS oracle support matrix |
| [Release Notes](docs/release_notes.md) | Version history |

---

## Build with Custom Version

```bash
# Default version (from pom.xml)
mvn package -DskipTests
# → sqlancer-2.4.5.jar

# Custom version
mvn package -Drevision=2.5.0 -DskipTests
# → sqlancer-2.5.0.jar

# Git-count-based version
mvn package -Drevision=2.4.$(git rev-list --count HEAD) -DskipTests
```

---

## License

MIT License — same as [official SQLancer](https://github.com/sqlancer/sqlancer).