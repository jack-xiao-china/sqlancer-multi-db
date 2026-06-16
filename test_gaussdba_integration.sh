#!/bin/bash

# GaussDB-A Oracle Integration Test Script
# Tests all oracles including new CODDTEST with 3 modes
# Covers: query oracles + CODDTEST 3 modes + FUCCI 3 combos + transaction oracles

SQLANCER_JAR="/d/Jack.Xiao/dbtools/sqlancer-main/sqlancer-main/target/sqlancer-2.7.0.jar"
HOST="121.37.186.131"
PORT="19995"
USERNAME="sqlbuilder1"
PASSWORD="huawei@123"
TARGET_DB="testa"
NUM_QUERIES="10"
TIMEOUT="60"
THREADS="1"

LOG_DIR="logs/gaussdba_integration_test"
mkdir -p "$LOG_DIR"

echo "=========================================="
echo "GaussDB-A Oracle Integration Test"
echo "Target: $TARGET_DB on $HOST:$PORT"
echo "=========================================="
echo ""

TOTAL=0
PASSED=0
FAILED=0
WARNINGS=0

# === Part 1: Query Oracles ===
QUERY_ORACLES=(
    "TLP_WHERE"
    "NOREC"
    "QUERY_PARTITIONING"
    "HAVING"
    "AGGREGATE"
    "DISTINCT"
    "GROUP_BY"
    "PQS"
    "CERT"
    "DQP"
    "DQE"
    "EET"
    "EET_INSERT_SELECT"
    "EET_UPDATE"
    "EET_DELETE"
    "FUZZER"
    "EDC_DATA"
    "JIR"
)

# === Part 2: CODDTEST 3 Modes ===
CODDTEST_MODES=(
    "EXPRESSION"
    "SUBQUERY"
    "RANDOM"
)

# === Part 3: Transaction Oracles ===
TX_ORACLES=(
    "WRITE_CHECK"
    "TX_INFER"
)

# === Part 4: FUCCI Isolation Levels ===
FUCCI_LEVELS=(
    "READ_COMMITTED"
    "REPEATABLE_READ"
    "SERIALIZABLE"
)

run_test() {
    local oracle="$1"
    local extra_args="$2"
    local test_name="$3"
    TOTAL=$((TOTAL + 1))

    local LOG_FILE="$LOG_DIR/${test_name}.log"

    echo "[$(date '+%Y/%m/%d %H:%M:%S')] Testing: $test_name"

    java -jar "$SQLANCER_JAR" \
        --host "$HOST" \
        --port "$PORT" \
        --username "$USERNAME" \
        --password "$PASSWORD" \
        --num-queries "$NUM_QUERIES" \
        --timeout-seconds "$TIMEOUT" \
        --num-threads "$THREADS" \
        gaussdb-a \
        --target-database "$TARGET_DB" \
        --oracle "$oracle" \
        $extra_args \
        > "$LOG_FILE" 2>&1

    local exit_code=$?

    # Check for critical tool errors (not database bugs)
    local CRITICAL_ERRORS=$(grep -E "(ClassNotFoundException|NoSuchMethodException|NullPointerException.*sqlancer|ClassCastException.*sqlancer|IllegalArgumentException.*cannot.*convert|Invalid value for)" "$LOG_FILE" | grep -v "WARNING" | grep -v "sun.misc.Unsafe" | grep -v "UnsafeAccess")

    # Check for CODDTEST logic bugs (these are oracle findings, not tool failures)
    local CODDTEST_BUGS=$(grep -E "Results mismatch" "$LOG_FILE")

    # Check if the oracle actually ran (produced query output)
    local HAS_SCHEMA=$(grep -cE "CREATE SCHEMA|CREATE TABLE|INSERT INTO|SELECT" "$LOG_FILE")

    if [ -n "$CRITICAL_ERRORS" ]; then
        echo "  -> FAILED (tool errors)"
        echo "$CRITICAL_ERRORS" | head -5
        FAILED=$((FAILED + 1))
    elif [ $exit_code -ne 0 ] && [ -z "$CODDTEST_BUGS" ]; then
        # Non-zero exit but no CODDTEST logic bug -> possible tool issue
        local TOOL_ERROR=$(grep -E "Exception in thread|ParameterException" "$LOG_FILE" | head -3)
        if [ -n "$TOOL_ERROR" ]; then
            echo "  -> FAILED (tool exception)"
            echo "$TOOL_ERROR"
            FAILED=$((FAILED + 1))
        else
            echo "  -> WARNING (non-zero exit, possible DB bug)"
            WARNINGS=$((WARNINGS + 1))
        fi
    elif [ $HAS_SCHEMA -lt 3 ]; then
        echo "  -> WARNING (few queries executed)"
        WARNINGS=$((WARNINGS + 1))
    elif [ -n "$CODDTEST_BUGS" ]; then
        echo "  -> PASSED (found DB bug via CODDTEST - oracle working correctly)"
        PASSED=$((PASSED + 1))
    else
        echo "  -> PASSED"
        PASSED=$((PASSED + 1))
    fi

    echo ""
}

# Run query oracles
echo "--- Part 1: Query Oracles ---"
for oracle in "${QUERY_ORACLES[@]}"; do
    run_test "$oracle" "" "query_${oracle}"
done

# Run CODDTEST with 3 modes
echo "--- Part 2: CODDTEST 3 Modes ---"
for mode in "${CODDTEST_MODES[@]}"; do
    run_test "CODDTEST" "--coddtest-model $mode" "coddtest_${mode}"
done

# Run transaction oracles
echo "--- Part 3: Transaction Oracles ---"
for oracle in "${TX_ORACLES[@]}"; do
    run_test "$oracle" "" "tx_${oracle}"
done

# Run FUCCI with isolation levels
echo "--- Part 4: FUCCI Isolation Levels ---"
for level in "${FUCCI_LEVELS[@]}"; do
    run_test "FUCCI" "--fucci-isolation-level $level --fucci-schedule-count 3" "fucci_${level}"
done

echo "=========================================="
echo "GaussDB-A Integration Test Summary"
echo "=========================================="
echo "Total:    $TOTAL"
echo "Passed:   $PASSED"
echo "Warnings: $WARNINGS"
echo "Failed:   $FAILED"
echo ""

if [ $FAILED -gt 0 ]; then
    echo "FAILED tests detected - review logs in $LOG_DIR"
    exit 1
else
    echo "All tests completed!"
    exit 0
fi
