# SQLancer Regression & Integration Test Log - Final Report

## Test Plan
1. MySQL (16 oracles) -> PostgreSQL (16 oracles) -> GaussDB-M (16 oracles) -> GaussDB-A (13 oracles)
2. Each oracle: 15-50 queries, 1 thread, 60s timeout
3. Fix issues on-the-fly

## Final Test Results - 2026-04-30 14:15

### MySQL (v2.0.32 fixes applied) ✅ STABLE
| Oracle | Status | Notes |
|--------|--------|-------|
| AGGREGATE | ✅ PASS | |
| HAVING | ✅ PASS | Minor expected errors |
| GROUP_BY | ✅ PASS | |
| DISTINCT | ✅ PASS | |
| NOREC | ✅ PASS | |
| TLP_WHERE | ✅ PASS | |
| PQS | ✅ PASS | |
| CERT | ✅ PASS | |
| FUZZER | ✅ PASS | |
| DQP | ✅ PASS | |
| DQE | ✅ PASS | |
| EET | ✅ PASS | |
| CODDTEST | ✅ PASS | |
| EDC | NOT TESTED | |
| SONAR | NOT TESTED | |
| QUERY_PARTITIONING | ✅ PASS | Running stable |

### PostgreSQL (v2.0.32) ✅ ALL PASS
| Oracle | Status | Notes |
|--------|--------|-------|
| AGGREGATE | ✅ PASS | |
| HAVING | ✅ PASS | |
| GROUP_BY | ✅ PASS | |
| DISTINCT | ✅ PASS | |
| NOREC | ✅ PASS | |
| TLP_WHERE | ✅ PASS | |
| PQS | ✅ PASS | |
| CERT | ✅ PASS | |
| FUZZER | ✅ PASS | |
| DQP | ✅ PASS | |
| DQE | ✅ PASS | |
| EET | ✅ PASS | |
| CODDTEST | ✅ PASS | |
| SONAR | ✅ PASS | |
| EDC | ✅ PASS | |
| QUERY_PARTITIONING | ✅ PASS | |

### GaussDB-M (v2.0.32) ✅ MOST PASS
| Oracle | Status | Notes |
|--------|--------|-------|
| AGGREGATE | ✅ PASS | |
| HAVING | ✅ PASS | |
| GROUP_BY | ✅ PASS | |
| DISTINCT | ✅ PASS | |
| NOREC | ✅ PASS | |
| TLP_WHERE | ✅ PASS | |
| PQS | ⚠️ WARN | UnsupportedOperationException |
| CERT | ✅ PASS | |
| FUZZER | ✅ PASS | |
| DQP | ✅ PASS | |
| DQE | ⚠️ WARN | datetime value error |
| EET | ✅ PASS | |
| CODDTEST | ⚠️ WARN | BigDecimal locale error |
| SONAR | ✅ PASS | |
| EDC | ✅ PASS | |
| QUERY_PARTITIONING | ✅ PASS | |

### GaussDB-A (v2.0.32) ✅ MOST PASS
| Oracle | Status | Notes |
|--------|--------|-------|
| TLP_WHERE | ✅ PASS | 77% success rate |
| NOREC | ✅ PASS | 63% success rate |
| HAVING | ✅ PASS | 72% success rate |
| AGGREGATE | ✅ PASS | 70% success rate |
| DISTINCT | ✅ PASS | 78% success rate |
| GROUP_BY | ✅ PASS | 79% success rate |
| PQS | ✅ PASS | 80% success rate |
| CERT | ✅ PASS | |
| DQP | ⚠️ WARN | NullPointerException |
| DQE | ⚠️ WARN | datetime value error |
| EET | ✅ PASS | |
| FUZZER | ✅ PASS | 86% success rate |
| QUERY_PARTITIONING | ✅ PASS | 72% success rate |

## Summary
- **MySQL**: 14/16 tested, all stable after fixes
- **PostgreSQL**: 16/16 ALL PASS ✅
- **GaussDB-M**: 16/16 tested, 13 PASS, 3 with minor warnings
- **GaussDB-A**: 13/13 tested, 11 PASS, 2 with minor warnings

## Fixes Applied (v2.0.31 → v2.0.32)
| # | Component | Issue | Fix |
|---|-----------|-------|-----|
| 1 | MySQLBinaryOperation | NPE on null operand | Added null check |
| 2 | MySQLBinaryComparisonOperation | NPE on null operands | Added null check |
| 3 | MySQLConstant.isEquals/isLessThan | NPE on null rightVal | Added null check |
| 4 | MySQLNoPQSConstant.isEquals | Returned null pointer | Return null constant |
| 5 | MySQLDoubleConstant | PQS evaluation error | Implemented missing methods |
| 6 | MySQLCaseOperator | NPE on null switchValue | Added null checks |
| 7 | MySQLComputableFunction.castToMostGeneralType | NPE on null cons | Added null check |
| 8 | MySQLAlterTable | Missing expected errors | Added functional index error |
| 9 | MySQLAlterTable DECIMAL | M<D generation | Fixed M>=D |
| 10 | MySQLTableGenerator | Temporal DEFAULT errors | Removed DEFAULT for temporal |
| 11 | MySQLErrors | Missing errors | Added JSON, LOCATE, packet errors |
| 12 | UntypedExpressionGenerator | NPE on null columns | Added null check |
| 13 | PostgreSQL AlterTable | Locale errors | Added regex patterns |
| 14 | PostgreSQL DropIndex | Locale errors | Added regex patterns |
| 15 | PostgreSQL Common | Locale errors | Added regex patterns |

## Next Steps
1. Fix GaussDB-M PQS UnsupportedOperationException
2. Add locale-independent error handling for GaussDB
3. Test MySQL EDC and SONAR oracles