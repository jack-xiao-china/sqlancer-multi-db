#!/bin/bash
# Full 5-minute confirmation sweep: every PostgreSQL oracle + parameter combo,
# run as NON-SUPERUSER tpcc01. Each combo runs DURATION seconds (default 300=5min).
# Appends results to logs/perm-full/summary.txt. Resumable: skips combos already done.
set -uo pipefail
DUR="${DUR:-300}"
LOGDIR="logs/perm-full"
mkdir -p "$LOGDIR"
SUMMARY="$LOGDIR/summary.txt"
touch "$SUMMARY"

run_one() {
  local label="$1"; shift
  local logf="$LOGDIR/${label}.log"
  local repf="$LOGDIR/${label}.report"
  # skip if already completed
  if grep -q "^${label} " "$SUMMARY" 2>/dev/null; then return; fi
  ./scripts/pg-perm-harness.sh "$@" > "$repf" 2>&1 </dev/null
  # harness args: ORACLE DURATION LOG [EXTRA...]; but we pass label-as-oracle? No.
}
# Use a dedicated runner inline since harness takes ORACLE DURATION LOG EXTRA
run_combo() {
  local label="$1"; local oracle="$2"; shift 2
  local extra=("$@")
  local logf="$LOGDIR/${label}.log"
  if grep -q "^${label} " "$SUMMARY" 2>/dev/null; then
    echo "SKIP $label (already done)"; return
  fi
  ./scripts/pg-perm-harness.sh "$oracle" "$DUR" "$logf" "${extra[@]}" > "$LOGDIR/${label}.report" 2>&1 </dev/null
  local pc tc
  pc=$(grep -m1 "perm_error_count=" "$LOGDIR/${label}.report" | cut -d= -f2)
  tc=$(grep -m1 "tool_exception_count=" "$LOGDIR/${label}.report" | cut -d= -f2)
  printf "%-32s perm=%-4s tool=%-4s\n" "$label" "${pc:-?}" "${tc:-?}" | tee -a "$SUMMARY"
}

# Base oracles
for o in NOREC PQS TLP_WHERE HAVING AGGREGATE DISTINCT GROUP_BY QUERY_PARTITIONING \
         CERT FUZZER DQP DQE EET EET_UPDATE EET_DELETE EET_INSERT_SELECT \
         SONAR EDC_RADAR EDC_DATA TX_INFER JIR; do
  run_combo "$o" "$o"
done

# CODDTEST models
for m in RANDOM EXPRESSION SUBQUERY; do
  run_combo "CODDTEST_$m" "CODDTEST" "--coddtest-model" "$m"
done

# FUCCI oracle-type x isolation-level (DT needs a reference DB, skip)
for ot in MT CS ALL; do
  for il in RANDOM READ_COMMITTED REPEATABLE_READ SERIALIZABLE; do
    run_combo "FUCCI_${ot}_${il}" "FUCCI" "--fucci-oracle" "$ot" "--fucci-isolation" "$il"
  done
done

echo "=== full sweep complete ===" | tee -a "$SUMMARY"
