# SQLancer Test Oracle Support Statistics

**Date**: 2026/04/15
**Version**: SQLancer Extended (based on official SQLancer with additional oracles)

## Summary

This document provides a comprehensive statistics of test oracle support across MySQL, PostgreSQL, and GaussDB-M databases.

---

## Oracle Support Matrix

| Oracle | PostgreSQL | MySQL | GaussDB-M | Category | Notes |
|--------|------------|-------|-----------|----------|-------|
| **NOREC** | ✓ | ✓ | ✓ | Standard | IN vs EXISTS optimization test |
| **PQS** | ✓ | ✓ | ✓ | Standard | Pivoted Query Synthesis |
| **WHERE** | ✓ | - | - | Alias | PostgreSQL alias for TLP_WHERE |
| **TLP_WHERE** | ✓ | ✓ | ✓ | Standard | WHERE clause partitioning |
| **HAVING** | ✓ | ✓ | ✓ | Standard | HAVING clause partitioning |
| **AGGREGATE** | ✓ | ✓ | ✓ | Standard | Aggregate function test |
| **DISTINCT** | ✓ | ✓ | ✓ | Standard | DISTINCT handling |
| **GROUP_BY** | ✓ | ✓ | ✓ | Standard | GROUP BY expressions |
| **CERT** | ✓ | ✓ | ✓ | Standard | CERT optimization test |
| **FUZZER** | ✓ | ✓ | ✓ | Standard | Random query generation |
| **DQE** | ✓ | ✓ | ✓ | New | DELETE/UPDATE equivalence |
| **DQP** | ✓ | ✓ | ✓ | New | Deterministic query test |
| **EET** | ✓ | ✓ | ✓ | New | Expression transformation |
| **CODDTEST** | ✓ | ✓ | ✓ | New | Expression folding (Codd's rules) |
| **QUERY_PARTITIONING** | ✓ | ✓ | ✓ | Composite | Combined oracle |

---

## Detailed Statistics

### Total Oracle Count

| Database | Total Oracles | Unique Oracles | Composite Oracles |
|----------|---------------|----------------|-------------------|
| PostgreSQL | 15 | 14 | 1 (QUERY_PARTITIONING) |
| MySQL | 14 | 13 | 1 (QUERY_PARTITIONING) |
| GaussDB-M | 14 | 13 | 1 (QUERY_PARTITIONING) |

### Category Distribution

| Category | PostgreSQL | MySQL | GaussDB-M |
|----------|------------|-------|-----------|
| Standard Oracles | 10 | 10 | 10 |
| New Oracles (DQE/DQP/EET/CODDTEST) | 4 | 4 | 4 |
| Composite Oracles | 1 | 1 | 1 |
| Alias Oracles | 1 (WHERE) | 0 | 0 |

### Oracles Requiring All Tables to Contain Rows

| Oracle | PostgreSQL | MySQL | GaussDB-M |
|--------|------------|-------|-----------|
| PQS | ✓ | ✓ | ✓ |
| CERT | ✓ | ✓ | ✓ |
| CODDTEST | - | ✓ | ✓ |

**Note**: PostgreSQL CODDTEST does not require all tables to contain rows, while MySQL and GaussDB-M versions do.

---

## Oracle Descriptions

### Standard Oracles

| Oracle | Description | Mechanism |
|--------|-------------|-----------|
| NOREC | Tests optimization correctness | Compares IN vs EXISTS query results |
| PQS | Tests query synthesis correctness | Pivoted Query Synthesis approach |
| TLP_WHERE | Tests WHERE clause equivalence | Partitioning: original = TRUE + FALSE + NULL |
| HAVING | Tests HAVING clause equivalence | Partitioning with GROUP BY context |
| AGGREGATE | Tests aggregate functions | Partitioning with aggregate expressions |
| DISTINCT | Tests DISTINCT handling | Partitioning with DISTINCT semantics |
| GROUP_BY | Tests GROUP BY expressions | Partitioning with grouping context |
| CERT | Tests query execution plan correctness | Compares EXPLAIN output with actual results |
| FUZZER | Generates random queries | No correctness checking, pure fuzzing |

### New Oracles (Extended Version)

| Oracle | Description | Mechanism |
|--------|-------------|-----------|
| DQE | DELETE/UPDATE Query Equivalence | Compares rows affected by DELETE/UPDATE with SELECT using same WHERE clause |
| DQP | Deterministic Query Partitioning | Executes same query twice, verifies results match |
| EET | Equivalent Expression Transformation | Applies transformation rules, verifies semantic equivalence |
| CODDTEST | Expression Folding Test | Evaluates predicate, folds to constant, compares COUNT(*) results |

### Composite Oracles

| Oracle | PostgreSQL Components | MySQL Components | GaussDB-M Components |
|--------|----------------------|------------------|---------------------|
| QUERY_PARTITIONING | TLP_WHERE + HAVING + AGGREGATE (3) | TLP_WHERE + HAVING + GROUP_BY + AGGREGATE + DISTINCT + NOREC (6) | TLP_WHERE + HAVING + GROUP_BY + AGGREGATE + DISTINCT + NOREC (6) |

**Note**: PostgreSQL QUERY_PARTITIONING is more focused (3 components), while MySQL/GaussDB-M includes more comprehensive testing (6 components).

---

## Implementation Details

### PostgreSQL Oracle Factory
- **File**: `src/sqlancer/postgres/PostgresOracleFactory.java`
- **Total entries**: 15 (including WHERE alias)
- **Default oracle**: QUERY_PARTITIONING

### MySQL Oracle Factory
- **File**: `src/sqlancer/mysql/MySQLOracleFactory.java`
- **Total entries**: 14
- **Default oracle**: QUERY_PARTITIONING

### GaussDB-M Oracle Factory
- **File**: `src/sqlancer/gaussdbm/GaussDBMOracleFactory.java`
- **Total entries**: 14
- **Default oracle**: QUERY_PARTITIONING
- **Implementation note**: Follows MySQL OracleFactory structure for CLI consistency

---

## Cross-Database Oracle Compatibility

### Fully Compatible Oracles (All 3 databases)

| Oracle | Implementation Status |
|--------|----------------------|
| NOREC | Same implementation pattern, uses generic NoRECOracle |
| TLP_WHERE | Same implementation pattern, uses generic TLPWhereOracle |
| PQS | Database-specific implementation |
| HAVING | Database-specific implementation |
| AGGREGATE | Database-specific implementation |
| DISTINCT | Database-specific implementation |
| GROUP_BY | Database-specific implementation |
| CERT | Database-specific parsers, same core CERTOracle |
| FUZZER | Database-specific implementation |
| DQE | Database-specific implementation |
| DQP | Database-specific implementation |
| EET | Database-specific implementation |
| CODDTEST | Database-specific implementation |

### Generic Oracle Framework Usage

| Oracle | Generic Framework Used | Database-Specific Code |
|--------|------------------------|------------------------|
| NOREC | NoRECOracle<T> | Expression generator, error handling |
| TLP_WHERE | TLPWhereOracle<T> | Expression generator, error handling |
| CERT | CERTOracle<T> | Row count parser, query plan parser |
| QUERY_PARTITIONING | CompositeTestOracle<T> | Oracle composition |

### Database-Specific Implementations

| Oracle | PostgreSQL | MySQL | GaussDB-M |
|--------|------------|-------|-----------|
| PQS | PostgresPivotedQuerySynthesisOracle | MySQLPivotedQuerySynthesisOracle | GaussDBMPivotedQuerySynthesisOracle |
| HAVING | PostgresTLPHavingOracle | MySQLTLPHavingOracle | GaussDBMTLPHavingOracle |
| AGGREGATE | PostgresTLPAggregateOracle | MySQLTLPAggregateOracle | GaussDBMTLPAggregateOracle |
| DISTINCT | PostgresTLPDistinctOracle | MySQLTLPDistinctOracle | GaussDBMTLPDistinctOracle |
| GROUP_BY | PostgresTLPGroupByOracle | MySQLTLPGroupByOracle | GaussDBMTLPGroupByOracle |
| FUZZER | PostgresFuzzer | MySQLFuzzer | GaussDBMFuzzer |
| DQE | PostgresDQEOracle | MySQLDQEOracle | GaussDBMDQEOracle |
| DQP | PostgresDQPOracle | MySQLDQPOracle | GaussDBMDQPOracle |
| EET | PostgresEETOracle | MySQLEETOracle | GaussDBMEETOracle |
| CODDTEST | PostgresCODDTestOracle | MySQLCODDTestOracle | GaussDBCODDTestOracle |

---

## Usage Examples

### PostgreSQL
```bash
java -jar sqlancer.jar postgres --oracle NOREC,WHERE,DQE,DQP,EET,CODDTEST
```

### MySQL
```bash
java -jar sqlancer.jar mysql --oracle NOREC,TLP_WHERE,DQE,DQP,EET,CODDTEST
```

### GaussDB-M
```bash
java -jar sqlancer.jar gaussdbm --oracle NOREC,TLP_WHERE,DQE,DQP,EET,CODDTEST
```

---

## Oracle Naming Differences

| PostgreSQL | MySQL/GaussDB-M | Notes |
|------------|-----------------|-------|
| WHERE | TLP_WHERE | PostgreSQL provides alias; MySQL/GaussDB-M use original name |

---

## Historical Notes

1. **DQE, DQP, EET, CODDTEST** are new oracles added in the extended version
2. All four new oracles are supported across PostgreSQL, MySQL, and GaussDB-M
3. **QUERY_PARTITIONING** composition differs between databases:
   - PostgreSQL: Originally designed with 3 components
   - MySQL/GaussDB-M: Extended to 6 components for broader coverage
4. GaussDB-M OracleFactory follows MySQL's structure for CLI consistency

---

## Future Considerations

1. Consider standardizing QUERY_PARTITIONING composition across all databases
2. Evaluate adding WHERE alias to MySQL/GaussDB-M for consistency
3. Consider relaxing CODDTEST's "requires all tables to contain rows" requirement in MySQL/GaussDB-M

---

## Appendix: Source Code Locations

| Database | Oracle Factory Path |
|----------|---------------------|
| PostgreSQL | `src/sqlancer/postgres/PostgresOracleFactory.java` |
| MySQL | `src/sqlancer/mysql/MySQLOracleFactory.java` |
| GaussDB-M | `src/sqlancer/gaussdbm/GaussDBMOracleFactory.java` |

### New Oracle Implementation Files

| Oracle | PostgreSQL | MySQL | GaussDB-M |
|--------|------------|-------|-----------|
| DQE | `src/sqlancer/postgres/oracle/ext/PostgresDQEOracle.java` | `src/sqlancer/mysql/oracle/MySQLDQEOracle.java` | `src/sqlancer/gaussdbm/oracle/GaussDBMDQEOracle.java` |
| DQP | `src/sqlancer/postgres/oracle/ext/PostgresDQPOracle.java` | `src/sqlancer/mysql/oracle/MySQLDQPOracle.java` | `src/sqlancer/gaussdbm/oracle/GaussDBMDQPOracle.java` |
| EET | `src/sqlancer/postgres/oracle/ext/eet/PostgresEETOracle.java` | `src/sqlancer/mysql/oracle/eet/MySQLEETOracle.java` | `src/sqlancer/gaussdbm/oracle/eet/GaussDBMEETOracle.java` |
| CODDTEST | `src/sqlancer/postgres/oracle/ext/PostgresCODDTestOracle.java` | `src/sqlancer/mysql/oracle/MySQLCODDTestOracle.java` | `src/sqlancer/gaussdbm/oracle/GaussDBCODDTestOracle.java` |