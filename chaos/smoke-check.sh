#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="${SCRIPT_DIR}/logs"
mkdir -p "${LOG_DIR}"

BASE_URL=${BASE_URL:-http://localhost:8080}
MANAGER_EMAIL=${MANAGER_EMAIL:-manager@booking.local}
MANAGER_PASSWORD=${MANAGER_PASSWORD:-Password123!}
ADMIN_EMAIL=${ADMIN_EMAIL:-admin@booking.local}
ADMIN_PASSWORD=${ADMIN_PASSWORD:-Password123!}
RESOURCE_ID=${RESOURCE_ID:-1}
INTERVAL_SECONDS=${INTERVAL_SECONDS:-5}
DURATION_SECONDS=${DURATION_SECONDS:-300}
LOG_FILE=${LOG_FILE:-"${LOG_DIR}/smoke-$(date -u +%Y%m%dT%H%M%SZ).csv"}

echo "epoch,timestamp,kind,name,http_code,latency_ms,ok,note" > "${LOG_FILE}"

request() {
  local method="$1"
  local url="$2"
  local payload="${3:-}"
  local token="${4:-}"

  local response_file
  response_file=$(mktemp)
  local write_out_file
  write_out_file=$(mktemp)

  local -a curl_args
  curl_args=(-sS -o "${response_file}" -w "%{http_code} %{time_total}" -X "${method}" "${url}")
  if [ -n "${token}" ]; then
    curl_args+=(-H "Authorization: Bearer ${token}")
  fi
  if [ -n "${payload}" ]; then
    curl_args+=(-H "Content-Type: application/json" -d "${payload}")
  fi

  if ! curl "${curl_args[@]}" > "${write_out_file}" 2>/dev/null; then
    echo "000 0" > "${write_out_file}"
  fi

  REQUEST_STATUS=$(awk '{print $1}' "${write_out_file}")
  REQUEST_SECONDS=$(awk '{print $2}' "${write_out_file}")
  REQUEST_BODY=$(cat "${response_file}")

  rm -f "${response_file}" "${write_out_file}"
}

json_field() {
  local body="$1"
  local field="$2"
  python3 -c "import json,sys; obj=json.loads(sys.argv[1]); print(obj.get(sys.argv[2], ''))" "${body}" "${field}" 2>/dev/null || true
}

log_line() {
  local epoch="$1"
  local ts="$2"
  local kind="$3"
  local name="$4"
  local status="$5"
  local latency_ms="$6"
  local ok="$7"
  local note="$8"
  echo "${epoch},${ts},${kind},${name},${status},${latency_ms},${ok},${note}" >> "${LOG_FILE}"
}

iterations=$((DURATION_SECONDS / INTERVAL_SECONDS))
if [ "${iterations}" -lt 1 ]; then
  iterations=1
fi

for ((i = 1; i <= iterations; i++)); do
  epoch=$(date +%s)
  timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  total_checks=0
  passed_checks=0

  request "POST" "${BASE_URL}/api/auth/login" "{\"email\":\"${MANAGER_EMAIL}\",\"password\":\"${MANAGER_PASSWORD}\"}" ""
  manager_login_ok=false
  manager_token=""
  if [ "${REQUEST_STATUS}" = "200" ]; then
    manager_token=$(json_field "${REQUEST_BODY}" "accessToken")
    if [ -n "${manager_token}" ]; then
      manager_login_ok=true
    fi
  fi
  total_checks=$((total_checks + 1))
  [ "${manager_login_ok}" = true ] && passed_checks=$((passed_checks + 1))
  manager_latency_ms=$(python3 -c "print(int(float('${REQUEST_SECONDS}')*1000))" 2>/dev/null || echo 0)
  log_line "${epoch}" "${timestamp}" "detail" "login_manager" "${REQUEST_STATUS}" "${manager_latency_ms}" "${manager_login_ok}" "manager_login"

  booking_create_ok=false
  booking_id=""
  if [ "${manager_login_ok}" = true ]; then
    start_time=$(date -u -d "@$((epoch + i * 240 + 3600))" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -v+"$((i * 240 + 3600))"S +"%Y-%m-%dT%H:%M:%SZ")
    end_time=$(date -u -d "@$((epoch + i * 240 + 5400))" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -v+"$((i * 240 + 5400))"S +"%Y-%m-%dT%H:%M:%SZ")
    booking_payload="{\"resourceId\":${RESOURCE_ID},\"startTime\":\"${start_time}\",\"endTime\":\"${end_time}\"}"
    request "POST" "${BASE_URL}/api/bookings" "${booking_payload}" "${manager_token}"
    if [ "${REQUEST_STATUS}" = "201" ]; then
      booking_id=$(json_field "${REQUEST_BODY}" "id")
      [ -n "${booking_id}" ] && booking_create_ok=true
    fi
  else
    REQUEST_STATUS="000"
    REQUEST_SECONDS="0"
  fi
  total_checks=$((total_checks + 1))
  [ "${booking_create_ok}" = true ] && passed_checks=$((passed_checks + 1))
  create_latency_ms=$(python3 -c "print(int(float('${REQUEST_SECONDS}')*1000))" 2>/dev/null || echo 0)
  log_line "${epoch}" "${timestamp}" "detail" "create_booking" "${REQUEST_STATUS}" "${create_latency_ms}" "${booking_create_ok}" "booking_creation"

  confirm_ok=false
  if [ "${booking_create_ok}" = true ] && [ -n "${booking_id}" ]; then
    request "PATCH" "${BASE_URL}/api/bookings/${booking_id}/status" "{\"status\":\"CONFIRMED\"}" "${manager_token}"
    if [ "${REQUEST_STATUS}" = "200" ]; then
      confirm_ok=true
    fi
  else
    REQUEST_STATUS="000"
    REQUEST_SECONDS="0"
  fi
  total_checks=$((total_checks + 1))
  [ "${confirm_ok}" = true ] && passed_checks=$((passed_checks + 1))
  confirm_latency_ms=$(python3 -c "print(int(float('${REQUEST_SECONDS}')*1000))" 2>/dev/null || echo 0)
  log_line "${epoch}" "${timestamp}" "detail" "confirm_booking" "${REQUEST_STATUS}" "${confirm_latency_ms}" "${confirm_ok}" "booking_confirmation"

  manager_forbidden_ok=false
  if [ "${manager_login_ok}" = true ]; then
    request "GET" "${BASE_URL}/api/users?page=0&size=5" "" "${manager_token}"
    if [ "${REQUEST_STATUS}" = "403" ]; then
      manager_forbidden_ok=true
    fi
  else
    REQUEST_STATUS="000"
    REQUEST_SECONDS="0"
  fi
  total_checks=$((total_checks + 1))
  [ "${manager_forbidden_ok}" = true ] && passed_checks=$((passed_checks + 1))
  forbidden_latency_ms=$(python3 -c "print(int(float('${REQUEST_SECONDS}')*1000))" 2>/dev/null || echo 0)
  log_line "${epoch}" "${timestamp}" "detail" "rbac_forbidden_manager_users" "${REQUEST_STATUS}" "${forbidden_latency_ms}" "${manager_forbidden_ok}" "manager_should_be_forbidden"

  admin_list_ok=false
  request "POST" "${BASE_URL}/api/auth/login" "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}" ""
  admin_token=""
  if [ "${REQUEST_STATUS}" = "200" ]; then
    admin_token=$(json_field "${REQUEST_BODY}" "accessToken")
  fi
  if [ -n "${admin_token}" ]; then
    request "GET" "${BASE_URL}/api/users?page=0&size=5" "" "${admin_token}"
    if [ "${REQUEST_STATUS}" = "200" ]; then
      admin_list_ok=true
    fi
  else
    REQUEST_STATUS="000"
    REQUEST_SECONDS="0"
  fi
  total_checks=$((total_checks + 1))
  [ "${admin_list_ok}" = true ] && passed_checks=$((passed_checks + 1))
  admin_latency_ms=$(python3 -c "print(int(float('${REQUEST_SECONDS}')*1000))" 2>/dev/null || echo 0)
  log_line "${epoch}" "${timestamp}" "detail" "rbac_admin_users" "${REQUEST_STATUS}" "${admin_latency_ms}" "${admin_list_ok}" "admin_should_pass"

  suite_ok=false
  if [ "${passed_checks}" -eq "${total_checks}" ]; then
    suite_ok=true
  fi
  log_line "${epoch}" "${timestamp}" "heartbeat" "critical_suite" "-" "0" "${suite_ok}" "pass=${passed_checks}_total=${total_checks}"

  if [ "$i" -lt "${iterations}" ]; then
    sleep "${INTERVAL_SECONDS}"
  fi
done

echo "Smoke checks completed. Log: ${LOG_FILE}"

