# Booking System Backend

Production-style pet project на Java 17 + Spring Boot для бронирования ресурсов (например, meeting rooms).

## Структура проекта

```text
booking-back
├── build.gradle
├── Dockerfile
├── docker-compose.yml
├── src
│   ├── main
│   │   ├── java/org/example/bookingback
│   │   │   ├── config
│   │   │   ├── controller
│   │   │   ├── dto
│   │   │   ├── entity
│   │   │   ├── exception
│   │   │   ├── mapper
│   │   │   ├── repository
│   │   │   ├── security
│   │   │   └── service
│   │   └── resources
│   │       ├── application.yml
│   │       └── db/changelog
│   └── test
│       ├── java/org/example/bookingback
│       │   ├── controller
│       │   └── service
│       └── resources/application-test.yml
└── gradle
```

## Возможности

- JWT auth с access + refresh token flow
- RBAC: `USER`, `MANAGER`, `ADMIN`
- CRUD ресурсов
- Бронирования с проверкой пересечений
- Ограничение доступа к restricted resource
- История действий по бронированиям
- Liquibase миграции
- Swagger / OpenAPI
- Unit и integration тесты
- JaCoCo отчёты

## Бизнес-правила

- Пересечение определяется как `start < existing.end AND end > existing.start`
- Нельзя бронировать в прошлом
- `startTime` должен быть раньше `endTime`
- Статусы: `PENDING -> CONFIRMED -> CANCELLED`
- Нелегальные переходы запрещены
- `USER` видит только свои бронирования
- Чужой booking возвращает `404`, если пользователь не владелец ресурса и не `MANAGER`/`ADMIN`
- Restricted resource можно бронировать только владельцу, `MANAGER`/`ADMIN` или пользователю с grant в `resource_accesses`

## Роли

- `USER`: регистрация, авторизация, свои бронирования
- `MANAGER`: управление ресурсами, подтверждение/отмена бронирований
- `ADMIN`: полный доступ

## Seed users

- `admin@booking.local / Password123!`
- `manager@booking.local / Password123!`

## Локальный запуск

1. Поднять PostgreSQL.
2. Задать переменные окружения при необходимости:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/booking_db
export DB_USERNAME=booking
export DB_PASSWORD=booking
export JWT_SECRET=change-me-change-me-change-me-change-me-1234567890
```

3. Запустить приложение:

```bash
./gradlew bootRun
```

## Docker

Собрать и запустить:

```bash
./gradlew bootJar
docker compose up --build
```

## Swagger

- UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Тесты и покрытие

```bash
./gradlew test
```

JaCoCo HTML:

```text
build/reports/jacoco/test/html/index.html
```

## Примеры curl

Регистрация:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Password123!"}'
```

Логин:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"manager@booking.local","password":"Password123!"}'
```

Создание ресурса:

```bash
curl -X POST http://localhost:8080/api/resources \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Board Room","description":"Main room","capacity":12,"restricted":false}'
```

Создание бронирования:

```bash
curl -X POST http://localhost:8080/api/bookings \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"resourceId":1,"startTime":"2026-05-01T10:00:00Z","endTime":"2026-05-01T11:00:00Z"}'
```

Подтверждение бронирования:

```bash
curl -X PATCH http://localhost:8080/api/bookings/1/status \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"status":"CONFIRMED"}'
```

Refresh token:

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<REFRESH_TOKEN>"}'
```

## Основные endpoint'ы

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/users/me`
- `GET /api/users`
- `POST /api/resources`
- `GET /api/resources`
- `GET /api/resources/{id}`
- `PUT /api/resources/{id}`
- `DELETE /api/resources/{id}`
- `POST /api/bookings`
- `GET /api/bookings/my`
- `GET /api/bookings/{id}`
- `GET /api/bookings/resource/{resourceId}`
- `PATCH /api/bookings/{id}/status`
- `DELETE /api/bookings/{id}`
