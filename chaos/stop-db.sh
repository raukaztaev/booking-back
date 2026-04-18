#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

DURATION_SECONDS=${1:-30}

echo "[chaos] stopping postgres for ${DURATION_SECONDS}s"
compose stop postgres
sleep "${DURATION_SECONDS}"
echo "[chaos] starting postgres"
compose start postgres
wait_for_postgres_health 180
echo "[chaos] postgres recovered"

