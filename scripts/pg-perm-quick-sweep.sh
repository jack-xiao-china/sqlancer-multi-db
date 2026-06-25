#!/bin/bash
# Quick sweep: run each postgres oracle for SHORT_DURATION seconds as tpcc01,
# surface any permission-class errors. Fast diagnostic before 5-min confirmation.
set -uo pipefail
DUR="${1:-45}"
LOGDIR="logs/perm-sweep"
mkdir -p "$LOGDIR"
SUMMARY="$LOGDIR/summary.txt"
: > "$SUMMARY"

# Default-param oracle list (FUCCI/CODDTEST combos covered separately in confirm phase)
ORACLES=(
  NOREC PQS TLP_WHERE HAVING AGGREGATE DISTINCT GROUP_BY
  QUERY_PARTITIONING CERT FUZZER DQP DQE
  EET EET_UPDATE EET_DELETE EET_INSERT_SELECT
  CODDTEST SONAR EDC_RADAR EDC_DATA TX_INFER JIR
)

for o in "${ORACLES[@]}"; do
  logf="$LOGDIR/${o}.log"
  ./scripts/pg-perm-harness.sh "$o" "$DUR" "$logf" > "$LOGDIR/${o}.report" 2>&1
  pc=$(grep -m1 "perm_error_count=" "$LOGDIR/${o}.report" | cut -d= -f2)
  tc=$(grep -m1 "tool_exception_count=" "$LOGDIR/${o}.report" | cut -d= -f2)
  printf "%-22s perm=%-4s tool=%-4s\n" "$o" "${pc:-?}" "${tc:-?}" | tee -a "$SUMMARY"
done
echo "=== sweep complete ===" | tee -a "$SUMMARY"
