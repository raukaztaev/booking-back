#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

DELAY_MS=${1:-250}
JITTER_MS=${2:-50}

echo "[chaos] adding DB latency: ${DELAY_MS}ms +/- ${JITTER_MS}ms"
if docker exec "${POSTGRES_CONTAINER}" sh -lc "command -v tc >/dev/null 2>&1 || apk add --no-cache iproute2 >/dev/null 2>&1; tc qdisc replace dev eth0 root netem delay ${DELAY_MS}ms ${JITTER_MS}ms distribution normal"; then
  echo "[chaos] DB latency injected"
else
  echo "[chaos] failed to inject DB latency using tc/netem"
  echo "[chaos] fallback: use './chaos/stop-db.sh <seconds>' scenario for DB fault testing"
  exit 1
fi

