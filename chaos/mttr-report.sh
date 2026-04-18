#!/usr/bin/env bash
set -euo pipefail

LOG_FILE=${1:-}
if [ -z "${LOG_FILE}" ] || [ ! -f "${LOG_FILE}" ]; then
  echo "Usage: $0 <smoke-log.csv>"
  exit 1
fi

awk -F',' '
BEGIN {
  total = 0
  failed = 0
  inOutage = 0
  outages = 0
  recoveries = 0
  outageStart = 0
  mttrSum = 0
}
$3 == "heartbeat" {
  total++
  isOk = ($7 == "true")
  if (!isOk) {
    failed++
    if (!inOutage) {
      inOutage = 1
      outages++
      outageStart = $1
    }
  } else {
    if (inOutage) {
      inOutage = 0
      recoveries++
      mttr = $1 - outageStart
      mttrSum += mttr
      printf("Recovery %d: outage_start=%s recovery=%s mttr_seconds=%d\n", recoveries, outageStart, $1, mttr)
    }
  }
}
END {
  if (total == 0) {
    print "No heartbeat data found in log."
    exit 1
  }
  availability = ((total - failed) / total) * 100
  printf("Heartbeats: %d\n", total)
  printf("Failed heartbeats: %d\n", failed)
  printf("Availability: %.2f%%\n", availability)
  printf("Outages detected: %d\n", outages)
  if (recoveries > 0) {
    printf("Average MTTR: %.2f seconds\n", mttrSum / recoveries)
  } else {
    print "Average MTTR: N/A (no completed recovery windows)"
  }
  if (inOutage) {
    print "System was still in outage state at the end of log."
  }
}
' "${LOG_FILE}"

