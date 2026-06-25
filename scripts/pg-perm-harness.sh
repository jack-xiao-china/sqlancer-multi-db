#!/bin/bash
# PostgreSQL oracle permission-error harness (Ralph loop).
# Runs one oracle (+param combo) as a NON-SUPERUSER for a fixed wall-clock duration,
# then scans the log for permission / superuser / owner / privilege class errors and
# reports counts + distinct samples.
#
# Usage:
#   ./scripts/pg-perm-harness.sh <ORACLE> <DURATION_SEC> <LOG_FILE> [EXTRA_ARGS...]
# Example:
#   ./scripts/pg-perm-harness.sh NOREC 300 logs/perm_norec.log --log-each-select true

set -uo pipefail

ORACLE="${1:?ORACLE required}"
DURATION="${2:?DURATION_SEC required}"
LOG="${3:?LOG_FILE required}"
shift 3
EXTRA=("$@")

JAR="${JAR:-$(ls -t target/sqlancer-*.jar 2>/dev/null | grep -vE 'shaded|sources|javadoc' | head -1)}"
[ -z "$JAR" ] && { echo "ERROR: no sqlancer jar found in target/"; exit 2; }
HOST="${PG_HOST:-localhost}"
PORT="${PG_PORT:-5432}"
USER="${PG_USER:-tpcc01}"
PASS="${PG_PASSWORD:-Taurus@123}"

mkdir -p "$(dirname "$LOG")"

# Use a large num-tries so the run is bounded by wall-clock timeout.
NUM_TRIES=100000

echo "=== harness start: oracle=$ORACLE duration=${DURATION}s user=$USER ===" | tee "$LOG"
# Use coreutils timeout to bound the run wall-clock. On Windows git-bash, signals
# to java.exe are unreliable, so also force-kill any stray java after timeout.
timeout --signal=KILL "$((DURATION + 20))" java -jar "$JAR" --host "$HOST" --port "$PORT" --username "$USER" --password "$PASS" --num-tries "$NUM_TRIES" --timeout-seconds 60 --log-each-select true --num-threads 1 postgres --oracle "$ORACLE" "${EXTRA[@]}" >>"$LOG" 2>&1
RC=$?
# Belt-and-suspenders: kill any java that survived the timeout (signals are flaky on Windows).
tasklist 2>/dev/null | grep -i "java.exe" | awk '{print $2}' | while read p; do taskkill //F //PID "$p" 2>/dev/null; done
echo "=== harness done: exit=$RC ===" >>"$LOG"

# ---- Permission / superuser / owner / privilege error scan ----
PERM_PAT='permission denied|must be owner of|must be superuser|must be member of|must have .* privilege|EXECUTE privilege|CREATE privilege|USAGE privilege|cannot set role|permission denied to use role|unrecognized configuration parameter|cannot be changed|requires superuser|superuser-only|insufficient privilege|must be able to SET ROLE'

echo "---PERM_SCAN_START---" | tee -a "$LOG"
PERM_HITS=$(grep -aiE "$PERM_PAT" "$LOG" || true)
PERM_COUNT=$(echo -n "$PERM_HITS" | grep -acE . || true)
echo "perm_error_count=$PERM_COUNT"

if [ "$PERM_COUNT" -gt 0 ]; then
  echo "--- distinct permission error samples (top 20) ---"
  echo "$PERM_HITS" | grep -aoiE "$PERM_PAT"[^\"]* | sort | uniq -c | sort -rn | head -20
fi

# ---- Tool-class exception scan (AssertionError / unexpected stacks, excluding normal DDL fails) ----
TOOL_PAT='java\.lang\.AssertionError|NullPointerException|Exception in thread|UnreachableStatementException'
TOOL_COUNT=$(grep -acE "$TOOL_PAT" "$LOG" || true)
echo "tool_exception_count=$TOOL_COUNT"
echo "=== harness report end ==="
