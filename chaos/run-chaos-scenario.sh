#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

SCENARIO=${1:-db-stop}
FAULT_DURATION_SECONDS=${FAULT_DURATION_SECONDS:-45}
WARMUP_SECONDS=${WARMUP_SECONDS:-20}
POST_RECOVERY_SECONDS=${POST_RECOVERY_SECONDS:-40}
INTERVAL_SECONDS=${INTERVAL_SECONDS:-5}

mkdir -p "${SCRIPT_DIR}/logs"
RUN_ID=$(date -u +%Y%m%dT%H%M%SZ)
LOG_FILE="${SCRIPT_DIR}/logs/${SCENARIO}-${RUN_ID}.csv"

TOTAL_SMOKE_DURATION=$((WARMUP_SECONDS + FAULT_DURATION_SECONDS + POST_RECOVERY_SECONDS + 5))

echo "[chaos] scenario=${SCENARIO}"
echo "[chaos] log file: ${LOG_FILE}"
echo "[chaos] smoke duration: ${TOTAL_SMOKE_DURATION}s"

BASE_URL="${BASE_URL}" \
INTERVAL_SECONDS="${INTERVAL_SECONDS}" \
DURATION_SECONDS="${TOTAL_SMOKE_DURATION}" \
LOG_FILE="${LOG_FILE}" \
  "${SCRIPT_DIR}/smoke-check.sh" &
SMOKE_PID=$!

sleep "${WARMUP_SECONDS}"

case "${SCENARIO}" in
  db-stop)
    "${SCRIPT_DIR}/stop-db.sh" "${FAULT_DURATION_SECONDS}"
    ;;
  db-latency)
    "${SCRIPT_DIR}/add-db-latency.sh" "${DB_DELAY_MS:-250}" "${DB_JITTER_MS:-50}"
    sleep "${FAULT_DURATION_SECONDS}"
    "${SCRIPT_DIR}/remove-db-latency.sh"
    ;;
  app-restart)
    "${SCRIPT_DIR}/restart-app.sh"
    sleep "${FAULT_DURATION_SECONDS}"
    ;;
  resource-stress)
    "${SCRIPT_DIR}/stress-app.sh" "${FAULT_DURATION_SECONDS}" "${STRESS_WORKERS:-2}"
    ;;
  *)
    echo "Unknown scenario: ${SCENARIO}"
    echo "Supported: db-stop | db-latency | app-restart | resource-stress"
    kill "${SMOKE_PID}" >/dev/null 2>&1 || true
    exit 1
    ;;
esac

wait "${SMOKE_PID}"

echo "[chaos] scenario finished, generating MTTR report"
"${SCRIPT_DIR}/mttr-report.sh" "${LOG_FILE}"

