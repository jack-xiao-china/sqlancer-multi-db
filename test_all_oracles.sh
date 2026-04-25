#!/bin/bash

# PostgreSQL Oracle Test Script
# Tests all oracles with new data type extensions enabled

SQLANCER_JAR="/d/Jack.Xiao/dbtools/sqlancer-main/sqlancer-main/target/sqlancer-2.0.0.jar"
HOST="localhost"
PORT="5432"
USERNAME="root"
PASSWORD="password"
DB_PREFIX="test_oracle"
TIMEOUT="30"
THREADS="1"

# Type options
TYPE_OPTIONS="--enable-time-types true --enable-json true --enable-uuid true --enable-bytea true --enable-arrays true --enable-enum true --enable-newtypes-in-dqe-dqp-eet true"

# List of all oracles
ORACLES=(
    "NOREC"
    "PQS"
    "WHERE"
    "TLP_WHERE"
    "HAVING"
    "AGGREGATE"
    "DISTINCT"
    "GROUP_BY"
    "QUERY_PARTITIONING"
    "CERT"
    "FUZZER"
    "DQP"
    "DQE"
    "EET"
    "CODDTEST"
)

LOG_DIR="logs/oracle_test_results"
mkdir -p "$LOG_DIR"

echo "=========================================="
echo "PostgreSQL Oracle Compatibility Test"
echo "All new data types enabled"
echo "=========================================="
echo ""

TOTAL=0
PASSED=0
FAILED=0

for oracle in "${ORACLES[@]}"; do
    TOTAL=$((TOTAL + 1))
    LOG_FILE="$LOG_DIR/${oracle}_test.log"
    ERROR_LOG="$LOG_DIR/${oracle}_errors.log"

    echo "[$(date '+%Y/%m/%d %H:%M:%S')] Testing oracle: $oracle"

    # Run sqlancer with timeout
    java -jar "$SQLANCER_JAR" \
        --host "$HOST" \
        --port "$PORT" \
        --username "$USERNAME" \
        --password "$PASSWORD" \
        --database-prefix "${DB_PREFIX}_${oracle}" \
        --num-tries 1 \
        --timeout-seconds "$TIMEOUT" \
        --num-threads "$THREADS" \
        postgres \
        $TYPE_OPTIONS \
        --oracle "$oracle" \
        > "$LOG_FILE" 2>&1

    # Check for errors
    # Look for assertion errors, exceptions, or tool class errors
    ERRORS=$(grep -E "(AssertionError|Exception|ERROR|error:|Failed|failed|ClassNotFound|NoSuchMethod|NullPointer)" "$LOG_FILE" | grep -v "WARNING" | grep -v "sun.misc.Unsafe" | grep -v "[INFO]")

    # Check if queries were executed (for oracles that should execute queries)
    QUERY_COUNT=$(grep -o "Executed [0-9]+ queries" "$LOG_FILE" | grep -o "[0-9]+" | tail -1)

    if [ -z "$ERRORS" ] && [ "$QUERY_COUNT" != "" ] && [ "$QUERY_COUNT" -gt "0" ]; then
        echo "  -> PASSED (executed $QUERY_COUNT queries)"
        PASSED=$((PASSED + 1))
    elif [ -z "$ERRORS" ]; then
        # Check if it's a known non-query oracle or expected behavior
        if grep -q "IgnoreMeException" "$LOG_FILE" || grep -q "does not support" "$LOG_FILE"; then
            echo "  -> SKIPPED (oracle not applicable or ignored)"
            PASSED=$((PASSED + 1))
        else
            echo "  -> WARNING (no queries executed, check logs)"
            echo "$ERRORS" > "$ERROR_LOG"
            FAILED=$((FAILED + 1))
        fi
    else
        echo "  -> FAILED (errors found)"
        echo "$ERRORS" > "$ERROR_LOG"
        FAILED=$((FAILED + 1))
    fi

    echo ""
done

echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo "Total:  $TOTAL"
echo "Passed: $PASSED"
echo "Failed: $FAILED"
echo ""

if [ $FAILED -gt 0 ]; then
    echo "Error details are in: $LOG_DIR/*_errors.log"
    echo ""
    echo "Files with errors:"
    ls -la "$LOG_DIR"/*_errors.log 2>/dev/null
fi

exit $FAILED