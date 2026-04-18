#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

echo "[chaos] removing DB latency"
docker exec "${POSTGRES_CONTAINER}" sh -lc "tc qdisc del dev eth0 root >/dev/null 2>&1 || true"
echo "[chaos] DB latency removed"

