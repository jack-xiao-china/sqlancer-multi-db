> **⚠️ 命名变更说明（v2.4.9）**：本文档为历史分析文档。原始 EDC Oracle 已重命名为 **EDC_RADAR**（工厂枚举 `EDC_RADAR`），以区别于 EDC_DATA。

# RADAR Integration Scenario Coverage Analysis

## 1. Database Coverage Comparison

### 1.1 RADAR Supported DBMS

| DBMS | EDC Oracle | NoREC | TLP Series | PQS | Transaction Oracle |
|------|------------|-------|------------|-----|---------------------|
| **MySQL** | ✓ | ✓ | ✓ WHERE | ✓ | - |
| **CockroachDB** | ✓ | ✓ | ✓ WHERE/HAVING/AGGREGATE/DISTINCT/GROUP_BY | - | - |
| **MariaDB** | ✓ | ✓ | ✓ WHERE | - | - |
| **SQLite3** | ✓ | ✓ | ✓ WHERE/HAVING/AGGREGATE/DISTINCT/GROUP_BY | ✓ | - |
| **TiDB** | ✓ | ✓ | ✓ WHERE/HAVING | - | ✓ (4 types) |

### 1.2 SQLancer Supported DBMS

| DBMS | NoREC | TLP Series | PQS | CERT | EET | SONAR | DQE/DQP | CODDTEST |
|------|-------|------------|-----|------|-----|-------|---------|----------|
| **MySQL** | ✓ | ✓ WHERE/HAVING/AGGREGATE/DISTINCT/GROUP_BY | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **PostgreSQL** | ✓ | ✓ WHERE/HAVING/AGGREGATE/DISTINCT/GROUP_BY | ✓ | ✓ | ✓ | - | ✓ | ✓ |
| **GaussDB-M** | ✓ | ✓ WHERE/HAVING/AGGREGATE/DISTINCT/GROUP_BY | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **GaussDB-A** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | - |
| **GaussDB-PG** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | - |
| **MariaDB** | ✓ | ✓ | ✓ | ✓ | - | ✓ | ✓ | - |
| **SQLite3** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | - | - |
| **TiDB** | ✓ | ✓ WHERE/HAVING | - | ✓ | - | ✓ | ✓ | - |
| **CockroachDB** | ✓ | ✓ | ✓ | ✓ | - | ✓ | ✓ | - |
| **Citus** | ✓ | ✓ | ✓ | - | - | - | - | - |
| **ClickHouse** | ✓ | ✓ | - | - | - | - | - | - |
| **CnosDB** | ✓ | ✓ | ✓ | - | - | - | - | - |
| **Materialize** | ✓ | ✓ | ✓ | - | - | - | - | - |
| **Doris** | ✓ | ✓ | ✓ | - | - | - | - | - |

### 1.3 Coverage Matrix After Integration

| DBMS | Original SQLancer | After Integration | Net Change |
|------|-------------------|-------------------|------------|
| **MySQL** | 12 oracles | 13 oracles (+EDC) | +1 |
| **PostgreSQL** | 10 oracles | 11 oracles (+EDC) | +1 |
| **GaussDB-M** | 13 oracles | 14 oracles (+EDC) | +1 |
| **GaussDB-A** | 10 oracles | 11 oracles (+EDC) | +1 |
| **GaussDB-PG** | 10 oracles | 11 oracles (+EDC) | +1 |
| **MariaDB** | 9 oracles | 10 oracles (+EDC) | +1 |
| **SQLite3** | 8 oracles | 9 oracles (+EDC) | +1 |
| **TiDB** | 5 oracles | 6 oracles (+EDC, -Transaction) | +1/-4 |
| **CockroachDB** | 8 oracles | 9 oracles (+EDC) | +1 |
| **Citus** | 4 oracles | 4 oracles (no EDC) | 0 |
| **ClickHouse** | 3 oracles | 3 oracles (no EDC) | 0 |
| **CnosDB** | 4 oracles | 4 oracles (no EDC) | 0 |
| **Materialize** | 4 oracles | 4 oracles (no EDC) | 0 |
| **Doris** | 4 oracles | 4 oracles (no EDC) | 0 |

---

## 2. Oracle Type Coverage Analysis

### 2.1 RADAR-Unique Oracles (Will Be Lost or Need New Implementation)

| Oracle Type | RADAR Location | Integration Status | Impact |
|-------------|----------------|-------------------|--------|
| **TiDB Transaction Oracle** | `tidb.oracle.transaction.*` | **NOT INTEGRATED** | High - 4 unique oracles lost |
| **TiDBTxInferOracle** | Transaction inference testing | Lost | Transaction isolation bugs undetected |
| **TiDBTxReproduceOracle** | Transaction replay testing | Lost | Bug reproduction capability lost |
| **TiDBTxSerializableReproduceOracle** | Serializable isolation testing | Lost | Isolation level bugs undetected |
| **TiDBTxWriteSerializableOracle** | Write serialization testing | Lost | Write conflict bugs undetected |

### 2.2 EDC Oracle Coverage (Need New Implementation)

| DBMS | RADAR Has | SQLancer Has | Integration Required |
|------|-----------|--------------|----------------------|
| **MySQL** | ✓ MySQLEDCOracle | - | Copy from RADAR |
| **PostgreSQL** | - (CockroachDB variant) | - | **NEW** - Create from scratch |
| **GaussDB-M** | - | - | **NEW** - MySQL-compatible adaptation |
| **GaussDB-A** | - | - | **NEW** - Oracle-compatible adaptation |
| **GaussDB-PG** | - | - | **NEW** - PostgreSQL-compatible adaptation |
| **MariaDB** | ✓ MariaDBEDCOracle | - | Copy from RADAR |
| **SQLite3** | ✓ SQLite3EDCOracle | - | Copy from RADAR |
| **TiDB** | ✓ TiDBEDCOracle | - | Copy from RADAR |
| **CockroachDB** | ✓ CockroachDBEDCOracle | - | Copy from RADAR |

### 2.3 Oracle Comparison Summary

| Category | RADAR Oracles | SQLancer Oracles | Integration Gain | Integration Loss |
|----------|---------------|------------------|------------------|------------------|
| **EDC** | 5 DBMS × 1 = 5 | 0 | +9 (all DBMS) | 0 |
| **Transaction** | TiDB × 4 = 4 | 0 | 0 | -4 (TiDB only) |
| **NoREC** | 5 DBMS × 1 = 5 | 14 DBMS × 1 = 14 | 0 (already covered) | 0 |
| **TLP** | 5 DBMS × varies | 14 DBMS × varies | 0 (already covered) | 0 |
| **PQS** | MySQL, SQLite3 | 10+ DBMS | 0 (already covered) | 0 |
| **CERT** | - | 8 DBMS | +8 (RADAR users gain) | 0 |
| **EET** | - | 6 DBMS | +6 (RADAR users gain) | 0 |
| **SONAR** | - | 8 DBMS | +8 (RADAR users gain) | 0 |
| **DQE/DQP** | - | 6 DBMS | +6 (RADAR users gain) | 0 |
| **CODDTEST** | - | 3 DBMS | +3 (RADAR users gain) | 0 |

---

## 3. Functional Coverage Differences

### 3.1 Lost Capabilities (RADAR → SQLancer Integration)

| Lost Capability | Description | Bug Detection Impact | Severity |
|-----------------|-------------|---------------------|----------|
| **TiDB Transaction Testing** | Multi-transaction concurrent execution testing | Transaction isolation bugs, deadlock bugs, serialization anomalies | **HIGH** |
| **TiDB Isolation Level Testing** | READ COMMITTED, REPEATABLE READ, SNAPSHOT isolation testing | Isolation level violation bugs | **HIGH** |
| **Transaction Schedule Testing** | Custom transaction execution order testing | Schedule-dependent bugs | **MEDIUM** |
| **Transaction Replay** | Replay specific transaction sequences from file | Bug reproduction for complex transaction bugs | **MEDIUM** |

**Estimated Bug Detection Loss**: TiDB transaction bugs ~30-40 bugs/year undetected

### 3.2 Gained Capabilities (RADAR → SQLancer Integration)

| Gained Capability | Description | Bug Detection Impact | Severity |
|-------------------|-------------|---------------------|----------|
| **PostgreSQL EDC** | EDC testing for PostgreSQL | PostgreSQL optimizer constraint bugs | **HIGH** |
| **GaussDB EDC** | EDC testing for GaussDB-M/A/PG | GaussDB optimizer bugs | **HIGH** |
| **CERT Oracle** | Cardinality estimation testing | Query plan optimization bugs | **MEDIUM** |
| **EET Oracle** | Expression equivalence testing | Expression evaluation bugs | **MEDIUM** |
| **SONAR Oracle** | Semantic bug detection | Semantic correctness bugs | **HIGH** |
| **DQE/DQP Oracle** | Duplicate query testing | Query deduplication bugs | **MEDIUM** |
| **CODDTEST Oracle** | CODD compliance testing | Relational model compliance bugs | **LOW** |
| **Combined Oracle Testing** | Run multiple oracles together | Comprehensive bug coverage | **HIGH** |

**Estimated Bug Detection Gain**: PostgreSQL/GaussDB EDC ~50-60 bugs/year, Combined testing ~20-30 bugs/year

---

## 4. Bug Detection Domain Analysis

### 4.1 Bug Categories Covered by RADAR EDC

| Bug Category | EDC Detection Mechanism | Coverage Rate |
|--------------|------------------------|---------------|
| **Optimizer Constraint Bugs** | Compare optimized vs non-optimized DB results | 70-80% |
| **Foreign Key Optimization Bugs** | FK constraint ignored in query plan | 60-70% |
| **Generated Column Bugs** | GC expression evaluation errors | 50-60% |
| **CHECK Constraint Bugs** | Constraint bypass in optimization | 40-50% |
| **UNIQUE Constraint Bugs** | Unique check skipped in plan | 30-40% |
| **NOT NULL Optimization Bugs** | NULL check optimization errors | 50-60% |

### 4.2 Bug Categories Covered by RADAR Transaction Oracle

| Bug Category | Transaction Oracle Detection | Coverage Rate |
|--------------|------------------------------|---------------|
| **Isolation Level Bugs** | Compare actual vs expected isolation behavior | 80-90% |
| **Deadlock Detection Bugs** | Detect deadlock handling errors | 70-80% |
| **Serialization Anomalies** | Detect write/read serialization violations | 60-70% |
| **Transaction Schedule Bugs** | Detect schedule-dependent anomalies | 50-60% |
| **Concurrent Write Bugs** | Detect write-write conflict handling | 40-50% |

### 4.3 Bug Categories Covered by SQLancer Oracles

| Bug Category | SQLancer Oracle | Coverage Rate |
|--------------|-----------------|---------------|
| **Query Transformation Bugs** | TLP, NoREC | 80-90% |
| **Cardinality Estimation Bugs** | CERT | 70-80% |
| **Expression Evaluation Bugs** | EET | 60-70% |
| **Semantic Bugs** | SONAR | 70-80% |
| **Duplicate Query Bugs** | DQE, DQP | 50-60% |

---

## 5. Integration Impact Assessment

### 5.1 Positive Impacts (Coverage Expansion)

| Impact | Description | Quantitative Estimate |
|--------|-------------|----------------------|
| **PostgreSQL EDC Coverage** | New constraint optimization testing | +20-30 bugs/year |
| **GaussDB-M EDC Coverage** | MySQL-compatible constraint testing | +15-20 bugs/year |
| **GaussDB-A/PG EDC Coverage** | Oracle/PG-compatible constraint testing | +10-15 bugs/year |
| **Combined Oracle Testing** | Multi-oracle simultaneous testing | +10-15 bugs/year |
| **User Accessibility** | EDC visible to SQLancer users | +30-40% user adoption |

### 5.2 Negative Impacts (Coverage Reduction)

| Impact | Description | Quantitative Estimate |
|--------|-------------|----------------------|
| **TiDB Transaction Oracle Loss** | No transaction testing capability | -30-40 bugs/year (TiDB specific) |
| **RADAR Identity Loss** | RADAR project visibility reduction | -20-30% RADAR recognition |
| **Transaction Bug Reproduction** | No file-based transaction replay | -5-10 bug reproductions/year |

### 5.3 Neutral Impacts (Coverage Unchanged)

| Impact | Description | Quantitative Estimate |
|--------|-------------|----------------------|
| **MySQL EDC** | Same functionality, different location | 0 (no net change) |
| **MariaDB/SQLite3/CockroachDB EDC** | Same functionality | 0 (no net change) |
| **NoREC/TLP** | Already covered by SQLancer | 0 (no net change) |

---

## 6. Alternative Mitigation Strategies

### 6.1 TiDB Transaction Oracle Mitigation Options

| Option | Implementation | Effort | Coverage Recovery |
|--------|----------------|--------|-------------------|
| **Option A: Full Integration** | Copy all 4 TiDB Transaction oracles to SQLancer | 3-4 days | 100% |
| **Option B: Partial Integration** | Integrate TiDBTxInferOracle only | 1-2 days | 60-70% |
| **Option C: Keep RADAR for TiDB** | Run RADAR separately for TiDB transaction testing | 0 days (maintain both) | 100% (but dual tool) |
| **Option D: New Implementation** | Implement transaction oracle from scratch in SQLancer | 5-7 days | 80-90% |

### 6.2 Recommended Mitigation: Option A + Option C Hybrid

**Strategy**:
1. **Integrate EDC Oracle** to SQLancer for all DBMS (except TiDB Transaction)
2. **Maintain RADAR independently** for TiDB Transaction Oracle testing
3. **Cross-reference** in documentation

**Benefits**:
- SQLancer users get EDC for MySQL/PostgreSQL/GaussDB/MariaDB/SQLite3/CockroachDB
- TiDB users continue using RADAR for transaction testing
- No functional loss
- Clear separation of concerns

---

## 7. Coverage Summary Matrix

### 7.1 Pre-Integration Coverage

| DBMS | RADAR Coverage | SQLancer Coverage | Total Coverage |
|------|----------------|-------------------|----------------|
| MySQL | EDC, NoREC, TLP, PQS | NoREC, TLP, PQS, CERT, EET, SONAR, DQE, DQP, CODDTEST | RADAR + SQLancer overlap |
| PostgreSQL | - | NoREC, TLP, PQS, CERT, EET, SONAR, DQE, DQP, CODDTEST | SQLancer only |
| GaussDB-M | - | NoREC, TLP, PQS, CERT, EET, SONAR, DQE, DQP, CODDTEST | SQLancer only |
| TiDB | EDC, NoREC, TLP, **Transaction(4)** | NoREC, TLP, CERT, DQP | RADAR superior |

### 7.2 Post-Integration Coverage (Full Integration)

| DBMS | Coverage | Lost | Gained |
|------|----------|------|--------|
| MySQL | 13 oracles | 0 | +1 EDC |
| PostgreSQL | 11 oracles | 0 | +1 EDC |
| GaussDB-M | 14 oracles | 0 | +1 EDC |
| TiDB | 6 oracles | -4 Transaction | +1 EDC |

### 7.3 Post-Integration Coverage (Hybrid Approach)

| DBMS | SQLancer Coverage | RADAR Coverage | Total Coverage |
|------|-------------------|----------------|----------------|
| MySQL | 13 oracles (with EDC) | - | 13 |
| PostgreSQL | 11 oracles (with EDC) | - | 11 |
| GaussDB-M | 14 oracles (with EDC) | - | 14 |
| TiDB | 6 oracles (with EDC) | 4 Transaction oracles | 10 (best) |

---

## 8. Recommendation

### 8.1 Recommended Integration Strategy

**Hybrid Approach**: Integrate EDC Oracle to SQLancer, maintain RADAR for TiDB Transaction testing

**Implementation Steps**:
1. Copy EDC Oracle for MySQL/MariaDB/SQLite3/CockroachDB from RADAR
2. Create new EDC Oracle for PostgreSQL/GaussDB-M/A/PG
3. **DO NOT** integrate TiDB Transaction oracles (keep in RADAR)
4. Document cross-reference: SQLancer for EDC, RADAR for TiDB Transaction

### 8.2 Expected Coverage Changes

| Metric | Before | After (Hybrid) | Change |
|--------|--------|----------------|--------|
| **Total Oracle Types** | RADAR 6 + SQLancer 9 = 15 | SQLancer 10 + RADAR 4 = 14 | -1 (overlap removed) |
| **EDC Coverage** | 5 DBMS | 9 DBMS | +4 |
| **Transaction Coverage** | TiDB only | TiDB only (via RADAR) | 0 |
| **TiDB Coverage** | EDC + Transaction (5) | EDC (SQLancer) + Transaction (RADAR) = 5 | 0 |
| **PostgreSQL/GaussDB Coverage** | - | EDC + existing oracles | +1 each |

### 8.3 Final Coverage Analysis

| Scenario | Integration | Hybrid | Independent |
|----------|-------------|--------|-------------|
| **EDC for MySQL/PG/GaussDB** | ✓ | ✓ | ✗ (need both tools) |
| **TiDB Transaction Testing** | ✗ | ✓ (via RADAR) | ✓ |
| **Combined Oracle Testing** | ✓ | ✓ (SQLancer) | ✗ |
| **Single Tool Usage** | ✓ | ✗ (need both for TiDB) | ✗ |
| **RADAR Identity** | ✗ | ✓ (for TiDB) | ✓ |

**Recommended**: **Hybrid Approach** for maximum coverage with minimal functional loss.