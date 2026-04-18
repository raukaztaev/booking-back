#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

echo "[chaos] restarting app container"
compose restart app
wait_for_http_ok "${BASE_URL}/actuator/health" 180
echo "[chaos] app is healthy again"

