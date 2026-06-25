#!/bin/bash
# Re-run previously-failing oracles with the latest jar to confirm tool=0 after fixes.
set -uo pipefail
DUR="${1:-45}"
LOGDIR="logs/perm-refail"; mkdir -p "$LOGDIR"; : > "$LOGDIR/summary.txt"
ORACLES=(EET EET_UPDATE EET_INSERT_SELECT SONAR EDC_RADAR EDC_DATA TX_INFER JIR)
for o in "${ORACLES[@]}"; do
  ./scripts/pg-perm-harness.sh "$o" "$DUR" "$LOGDIR/${o}.log" > "$LOGDIR/${o}.report" 2>&1
  pc=$(grep -m1 "perm_error_count=" "$LOGDIR/${o}.report" | cut -d= -f2)
  tc=$(grep -m1 "tool_exception_count=" "$LOGDIR/${o}.report" | cut -d= -f2)
  printf "%-22s perm=%-4s tool=%-4s\n" "$o" "${pc:-?}" "${tc:-?}" | tee -a "$LOGDIR/summary.txt"
done
echo "=== refail-sweep complete ===" | tee -a "$LOGDIR/summary.txt"
