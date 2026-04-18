#!/usr/bin/env bash
set -euo pipefail

OUTPUT_FILE=${1:-"performance/docker-stats.log"}
INTERVAL_SECONDS=${INTERVAL_SECONDS:-2}
CONTAINERS=${CONTAINERS:-"booking-app booking-postgres"}

mkdir -p "$(dirname "$OUTPUT_FILE")"
echo "timestamp,name,cpu_percent,mem_usage,mem_percent,net_io,block_io,pids" > "$OUTPUT_FILE"

echo "Writing docker stats to $OUTPUT_FILE every ${INTERVAL_SECONDS}s for: $CONTAINERS"
while true; do
  ts=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  while read -r line; do
    [ -z "$line" ] && continue
    echo "${ts},${line}" >> "$OUTPUT_FILE"
  done < <(docker stats --no-stream --format "{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}},{{.NetIO}},{{.BlockIO}},{{.PIDs}}" $CONTAINERS)
  sleep "$INTERVAL_SECONDS"
done

