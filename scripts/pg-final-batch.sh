#!/bin/bash
set -uo pipefail
LOGDIR="logs/perm-final2"
ORACLES=(NOREC PQS TLP_WHERE HAVING AGGREGATE DISTINCT GROUP_BY QUERY_PARTITIONING CERT FUZZER DQP DQE EET EET_UPDATE EET_DELETE EET_INSERT_SELECT CODDTEST SONAR EDC_RADAR EDC_DATA TX_INFER JIR FUCCI)
for o in "${ORACLES[@]}"; do
  logf="$LOGDIR/${o}.log"
  timeout --signal=KILL 320 java -jar target/sqlancer-2.7.9.jar --host localhost --port 5432 --username tpcc01 --password 'Taurus@123' --num-tries 100000 --timeout-seconds 60 --log-each-select true --num-threads 1 postgres --oracle "$o" > "$logf" 2>&1
  tasklist 2>/dev/null | grep -i "java.exe" | awk '{print $2}' | while read p; do taskkill //F //PID "$p" 2>/dev/null; done
  pc=$(grep -acE 'permission denied|must be (owner|superuser)|insufficient privilege|row-level security' "$logf")
  tc=$(grep -acE 'java\.lang\.AssertionError|Exception in thread' "$logf")
  printf "%-20s perm=%-4s tool=%-4s\n" "$o" "$pc" "$tc" | tee -a "$LOGDIR/summary.txt"
done
echo "=== FINAL BATCH COMPLETE ===" | tee -a "$LOGDIR/summary.txt"
