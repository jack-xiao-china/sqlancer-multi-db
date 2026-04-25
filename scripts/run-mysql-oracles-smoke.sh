#!/usr/bin/env bash
set -u

# MySQL oracles smoke (tool health check).
# - Allowed: SQLancer finds DB bugs (AssertionError mismatch etc.).
# - Disallowed: SQLancer tool-illegal errors (invalid SQL, internal crashes).

JAR_PATH="${JAR_PATH:-target/sqlancer-2.0.0.jar}"
LOG_BASE_DIR="${LOG_BASE_DIR:-target/logs}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-password}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-60}"
NUM_QUERIES="${NUM_QUERIES:-5000}"
NUM_THREADS="${NUM_THREADS:-1}"

ORACLES=(
  "AGGREGATE"
  "HAVING"
  "GROUP_BY"
  "DISTINCT"
  "NOREC"
  "TLP_WHERE"
  "PQS"
  "CERT"
  "FUZZER"
  "DQP"
  "DQE"
  "EET"
  "CODDTEST"
  "QUERY_PARTITIONING"
)

mkdir -p "$LOG_BASE_DIR" || exit 1
SUMMARY_PATH="$LOG_BASE_DIR/mysql-oracles-smoke-summary.txt"
CSV_PATH="$LOG_BASE_DIR/mysql-oracles-smoke-results.csv"
RUN_OUT_DIR="$LOG_BASE_DIR/mysql-oracles-smoke-out"

rm -f "$SUMMARY_PATH" "$CSV_PATH"
mkdir -p "$RUN_OUT_DIR" || exit 1

echo "\"oracle\",\"exit\",\"status\"" > "$CSV_PATH"

classify_output() {
  local out_file="$1"
  local exit_code="$2"

  # Tool-illegal errors: generator/serializer/internal crash or invalid SQL.
  if grep -E -q \
    'java\.sql\.SQLSyntaxErrorException|java\.lang\.NullPointerException|java\.lang\.ClassCastException|java\.lang\.ArrayIndexOutOfBoundsException|java\.lang\.AssertionError:[[:space:]]*DATE|ORACLE_ALIAS\(' \
    "$out_file"; then
    echo "TOOL_ILLEGAL"
    return 0
  fi

  # Detect "SELECT ... FROM" with trailing FROM (best-effort).
  if grep -E -q 'SELECT .* FROM[[:space:]]*$' "$out_file"; then
    echo "TOOL_ILLEGAL"
    return 0
  fi

  # Allowed: SQLancer finds DB logic bugs -> AssertionError.
  if grep -E -q 'java\.lang\.AssertionError' "$out_file"; then
    echo "DB_BUG_FOUND"
    return 0
  fi

  # DB/environment failures (reported).
  if grep -E -q \
    'Communications link failure|No operations allowed after connection closed|ConnectionIsClosedException|java\.net\.SocketException' \
    "$out_file"; then
    echo "DB_OR_ENV_FAILURE"
    return 0
  fi

  if [[ "$exit_code" == "0" ]]; then
    echo "OK"
    return 0
  fi

  echo "TOOL_ILLEGAL"
}

tool_illegal_oracles=()

for o in "${ORACLES[@]}"; do
  echo
  echo "==== Running MySQL oracle $o ===="

  out_file="$RUN_OUT_DIR/$o.out.txt"
  rm -f "$out_file"

  java -jar "$JAR_PATH" \
    --log-dir "$LOG_BASE_DIR" \
    --host "$DB_HOST" \
    --port "$DB_PORT" \
    --username "$DB_USER" \
    --password "$DB_PASS" \
    --num-tries 1 \
    --timeout-seconds "$TIMEOUT_SECONDS" \
    --num-queries "$NUM_QUERIES" \
    --log-each-select true \
    --num-threads "$NUM_THREADS" \
    mysql --oracle "$o" >"$out_file" 2>&1

  exit_code="$?"
  status="$(classify_output "$out_file" "$exit_code")"

  echo "\"$o\",\"$exit_code\",\"$status\"" >> "$CSV_PATH"

  if [[ "$status" != "OK" ]]; then
    {
      echo "===== ORACLE $o status=$status exit=$exit_code ====="
      cat "$out_file"
      echo
    } >> "$SUMMARY_PATH"
  fi

  if [[ "$status" == "TOOL_ILLEGAL" ]]; then
    tool_illegal_oracles+=("$o")
  fi
done

if [[ "${#tool_illegal_oracles[@]}" -gt 0 ]]; then
  echo
  echo "TOOL_ILLEGAL detected in: ${tool_illegal_oracles[*]}"
  echo "See: $SUMMARY_PATH"
  exit 2
fi

echo
echo "Smoke passed (no tool-illegal errors)."
echo "Results: $CSV_PATH"
echo "Details: $SUMMARY_PATH (only non-OK)"
exit 0

