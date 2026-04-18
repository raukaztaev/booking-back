#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

DURATION_SECONDS=${1:-45}
WORKERS=${2:-2}

echo "[chaos] starting app CPU stress for ${DURATION_SECONDS}s with ${WORKERS} workers"
PIDS=$(
  docker exec "${APP_CONTAINER}" sh -lc "for i in \$(seq 1 ${WORKERS}); do yes > /dev/null & echo \$!; done" \
    | tr '\n' ' '
)

sleep "${DURATION_SECONDS}"

for pid in ${PIDS}; do
  docker exec "${APP_CONTAINER}" sh -lc "kill ${pid} >/dev/null 2>&1 || true"
done

echo "[chaos] app CPU stress stopped"

