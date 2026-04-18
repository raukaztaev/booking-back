# Chaos / fault injection testing

Набор скриптов для controlled failure testing поверх текущего `docker compose`.

## Структура

- `chaos/stop-db.sh` - временно останавливает PostgreSQL и поднимает обратно.
- `chaos/add-db-latency.sh` - добавляет задержку сети в контейнер БД через `tc/netem`.
- `chaos/remove-db-latency.sh` - снимает сетевую задержку.
- `chaos/restart-app.sh` - рестарт `booking-app` и ожидание `/actuator/health`.
- `chaos/stress-app.sh` - CPU stress внутри app-контейнера (`yes > /dev/null`).
- `chaos/smoke-check.sh` - циклическая проверка критичных endpoint’ов, пишет CSV-лог доступности.
- `chaos/mttr-report.sh` - расчет availability и приблизительного MTTR из heartbeat-логов.
- `chaos/run-chaos-scenario.sh` - orchestration сценария + smoke + MTTR.

## Preconditions

1. Поднять систему:

```bash
docker compose up -d --build
```

2. Проверить здоровье:

```bash
curl http://localhost:8080/actuator/health
```

## Critical checks inside smoke loop

Каждый heartbeat интервалом `INTERVAL_SECONDS` проверяет:

1. `POST /api/auth/login` (manager login)
2. `POST /api/bookings` (create booking)
3. `PATCH /api/bookings/{id}/status` (confirm booking)
4. `GET /api/users` под manager (ожидаем `403`)
5. `GET /api/users` под admin (ожидаем `200`)

Пишется CSV: `epoch,timestamp,kind,name,http_code,latency_ms,ok,note`.

## Run scenarios

```bash
chmod +x chaos/*.sh

# 1) DB temporary unavailability
./chaos/run-chaos-scenario.sh db-stop

# 2) DB network latency
DB_DELAY_MS=250 DB_JITTER_MS=50 ./chaos/run-chaos-scenario.sh db-latency

# 3) App restart / downtime
./chaos/run-chaos-scenario.sh app-restart

# 4) Resource stress (CPU pressure)
STRESS_WORKERS=2 ./chaos/run-chaos-scenario.sh resource-stress
```

## Duration controls

```bash
FAULT_DURATION_SECONDS=45 WARMUP_SECONDS=20 POST_RECOVERY_SECONDS=40 INTERVAL_SECONDS=5 ./chaos/run-chaos-scenario.sh db-stop
```

## MTTR and availability

После каждого `run-chaos-scenario.sh` автоматически запускается:

```bash
./chaos/mttr-report.sh chaos/logs/<scenario>-<timestamp>.csv
```

Отчет выводит:

- total heartbeat count
- failed heartbeats
- availability %
- число outage-окон
- средний MTTR (seconds)

## Notes / fallback

- `db-latency` использует `tc/netem` внутри `booking-postgres`. Для этого в `docker-compose.yml` добавлен `cap_add: NET_ADMIN`.
- Если `tc` injection не срабатывает в конкретной среде Docker, fallback-сценарий для DB fault: `db-stop`.

