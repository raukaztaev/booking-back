#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

DOCKER_COMPOSE_BIN=${DOCKER_COMPOSE_BIN:-"docker compose"}
read -r -a COMPOSE_CMD <<< "${DOCKER_COMPOSE_BIN}"

APP_CONTAINER=${APP_CONTAINER:-booking-app}
POSTGRES_CONTAINER=${POSTGRES_CONTAINER:-booking-postgres}
BASE_URL=${BASE_URL:-http://localhost:8080}

compose() {
  (
    cd "${PROJECT_ROOT}"
    "${COMPOSE_CMD[@]}" "$@"
  )
}

wait_for_postgres_health() {
  local timeout_seconds="${1:-120}"
  local waited=0
  while [ "$waited" -lt "$timeout_seconds" ]; do
    local health
    health=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}' "${POSTGRES_CONTAINER}" 2>/dev/null || true)
    if [ "$health" = "healthy" ]; then
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
  echo "Postgres did not become healthy within ${timeout_seconds}s"
  return 1
}

wait_for_http_ok() {
  local url="${1}"
  local timeout_seconds="${2:-120}"
  local waited=0
  while [ "$waited" -lt "$timeout_seconds" ]; do
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" "${url}" || true)
    if [ "${code}" = "200" ]; then
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
  echo "Endpoint ${url} did not return 200 within ${timeout_seconds}s"
  return 1
}

