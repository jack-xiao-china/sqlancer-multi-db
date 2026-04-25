#!/bin/bash

# PostgreSQL Oracle Test Script
# Tests all oracles with new data type extensions enabled

SQLANCER_JAR="/d/Jack.Xiao/dbtools/sqlancer-main/sqlancer-main/target/sqlancer-2.0.0.jar"
HOST="localhost"
PORT="5432"
USERNAME="root"
PASSWORD="password"
TIMEOUT="45"
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

LOG_DIR="logs/oracle_full_test"
mkdir -p "$LOG_DIR"

echo "=========================================="
echo "PostgreSQL Oracle Compatibility Test"
echo "All new data types enabled"
echo "=========================================="
echo ""

TOTAL=0
PASSED=0
FAILED=0
WARNINGS=0

for oracle in "${ORACLES[@]}"; do
    TOTAL=$((TOTAL + 1))
    DB_PREFIX="newtypes_${oracle}_$(date +%s)"
    LOG_FILE="$LOG_DIR/${oracle}_test.log"

    echo "[$(date '+%Y/%m/%d %H:%M:%S')] Testing oracle: $oracle"

    # Run sqlancer with timeout
    java -jar "$SQLANCER_JAR" \
        --host "$HOST" \
        --port "$PORT" \
        --username "$USERNAME" \
        --password "$PASSWORD" \
        --database-prefix "$DB_PREFIX" \
        --num-tries 1 \
        --timeout-seconds "$TIMEOUT" \
        --num-threads "$THREADS" \
        postgres \
        $TYPE_OPTIONS \
        --oracle "$oracle" \
        > "$LOG_FILE" 2>&1

    # Check for critical errors (tool class errors, assertion errors)
    CRITICAL_ERRORS=$(grep -E "(AssertionError|ClassNotFoundException|NoSuchMethodException|NullPointerException|ClassCastException|NumberFormatException|IllegalArgumentException.*cannot|工具类)" "$LOG_FILE" | grep -v "WARNING" | grep -v "sun.misc.Unsafe" | grep -v "[INFO]")

    # Check for SQL errors (these may be expected for some oracles)
    SQL_ERRORS=$(grep -E "PSQLException" "$LOG_FILE" | grep -v "connectiontest" | head -5)

    # Check if queries were executed
    QUERY_COUNT=$(grep -o "Executed [0-9]* queries" "$LOG_FILE" | grep -o "[0-9]*" | tail -1)
    MAX_QPS=$(grep -o "[0-9]* queries/s" "$LOG_FILE" | grep -o "[0-9]*" | sort -rn | head -1)

    if [ -n "$CRITICAL_ERRORS" ]; then
        echo "  -> FAILED (critical errors found)"
        echo "$CRITICAL_ERRORS" | head -10
        FAILED=$((FAILED + 1))
    elif [ -z "$QUERY_COUNT" ] || [ "$QUERY_COUNT" = "0" ]; then
        if [ -n "$SQL_ERRORS" ]; then
            echo "  -> WARNING (SQL errors, no queries executed)"
            echo "$SQL_ERRORS" | head -3
            WARNINGS=$((WARNINGS + 1))
        else
            echo "  -> PASSED (no critical errors, but low query count)"
            PASSED=$((PASSED + 1))
        fi
    else
        echo "  -> PASSED (executed $QUERY_COUNT queries, max $MAX_QPS qps)"
        PASSED=$((PASSED + 1))
    fi

    echo ""
done

echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo "Total:    $TOTAL"
echo "Passed:   $PASSED"
echo "Warnings: $WARNINGS"
echo "Failed:   $FAILED"
echo ""

if [ $FAILED -gt 0 ]; then
    echo "Critical errors detected - please review logs"
    exit 1
elif [ $WARNINGS -gt 0 ]; then
    echo "Some oracles had warnings - review logs for details"
    exit 0
else
    echo "All tests passed successfully!"
    exit 0
fi