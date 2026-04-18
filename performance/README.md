# Performance testing (k6)

Набор нагрузочных сценариев покрывает 3 high-risk участка:

1. `auth-login.js`  
Endpoint: `POST /api/auth/login`
2. `create-booking.js`  
Endpoint: `POST /api/bookings` (+ setup через `POST /api/resources`, `POST /api/auth/register`)
3. `protected-endpoints.js`  
Endpoints: `PATCH /api/bookings/{id}/status`, `GET /api/users` (RBAC: manager=403, admin=200)

Причина выбора: это самые рискованные для прод-ошибок цепочки (auth, конфликтная booking-логика, подтверждение и роль-based доступ).

## Load profiles

`LOAD_PROFILE` поддерживает:

- `normal`: 10 VUs, ~3m
- `peak`: 30 VUs, ~4.5m
- `spike`: резкий jump до 80 VUs
- `endurance`: 10 VUs, ~11.5m

Сценарии и thresholds уже зашиты в scripts.

## Prerequisites

1. Запустить систему:

```bash
docker compose up -d --build
```

2. Установить `k6` (macOS/Linux):

```bash
# macOS
brew install k6

# Linux (пример для Debian/Ubuntu)
sudo gpg -k
sudo apt-get update && sudo apt-get install -y gnupg ca-certificates
curl -s https://dl.k6.io/key.gpg | sudo gpg --dearmor -o /usr/share/keyrings/k6-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install -y k6
```

## Run commands

```bash
# 1) Login/auth profile
LOAD_PROFILE=normal BASE_URL=http://localhost:8080 k6 run performance/auth-login.js
LOAD_PROFILE=peak BASE_URL=http://localhost:8080 k6 run performance/auth-login.js
LOAD_PROFILE=spike BASE_URL=http://localhost:8080 k6 run performance/auth-login.js
LOAD_PROFILE=endurance BASE_URL=http://localhost:8080 k6 run performance/auth-login.js

# 2) Create booking profile
LOAD_PROFILE=normal BASE_URL=http://localhost:8080 k6 run performance/create-booking.js
LOAD_PROFILE=peak BASE_URL=http://localhost:8080 k6 run performance/create-booking.js
LOAD_PROFILE=spike BASE_URL=http://localhost:8080 k6 run performance/create-booking.js
LOAD_PROFILE=endurance BASE_URL=http://localhost:8080 k6 run performance/create-booking.js

# 3) Booking confirmation + RBAC protected endpoints
LOAD_PROFILE=normal BASE_URL=http://localhost:8080 k6 run performance/protected-endpoints.js
LOAD_PROFILE=peak BASE_URL=http://localhost:8080 k6 run performance/protected-endpoints.js
LOAD_PROFILE=spike BASE_URL=http://localhost:8080 k6 run performance/protected-endpoints.js
LOAD_PROFILE=endurance BASE_URL=http://localhost:8080 k6 run performance/protected-endpoints.js
```

## Metrics to collect from k6 output

- `http_req_duration`:
  - `avg` (average response time)
  - `med` (median)
  - `p(95)` (95th percentile)
- `http_reqs` / execution time => throughput (requests/sec)
- `http_req_failed` => error rate
- `checks` => functional pass rate under load

## Container CPU/Memory monitoring during load

В отдельном терминале:

```bash
chmod +x performance/docker-stats.sh
INTERVAL_SECONDS=2 ./performance/docker-stats.sh performance/docker-stats.log
```

Лог можно импортировать в spreadsheet для графиков CPU/RAM по `booking-app` и `booking-postgres`.

