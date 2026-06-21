# Blink API

Spring Boot backend for the Blink URL shortener.

## Stack

- Java 21
- Spring Boot 3
- Spring Web
- Spring Data JPA
- PostgreSQL
- Redis
- Swagger
- Docker / Docker Compose

## Features

- `POST /api/urls` create short links
- `GET /api/urls` list links for the dashboard
- `GET /api/urls/{code}/stats` view analytics
- `DELETE /api/urls/{id}` delete a link
- `GET /{shortCode}` redirect using Redis-first lookup
- custom aliases
- expiring links
- Redis-backed create rate limit
- health check at `/actuator/health`
- Swagger UI at `/swagger-ui/index.html`

## Run locally

1. Copy `.env.example` to `.env` if you want your own values.
2. Start local services:

```powershell
docker compose up -d
```

3. Run the API:

```powershell
.\mvnw.cmd spring-boot:run
```

## Quality checks

Java projects usually do not use Husky. This repo uses Maven-native checks instead:

- `spotless:check` for formatting
- `test` for unit tests
- `verify` as the one command CI and local hooks should run

Useful commands:

```powershell
.\mvnw.cmd spotless:apply
.\mvnw.cmd verify
```

Optional local git hook:

```powershell
git config core.hooksPath .githooks
```

## Deploy on Render

This repo now includes [render.yaml](D:/Blink/Blink-api/render.yaml) for a Blueprint deploy.

1. Push `D:\Blink\Blink-api` to GitHub.
2. In Render, click `New` -> `Blueprint`.
3. Connect the repo and deploy the blueprint.
4. After Render creates the resources, set:
   - `APP_BASE_URL` to your Render backend URL
   - `CORS_ALLOWED_ORIGINS` to your frontend URL

The blueprint creates:

- one Docker web service: `blink-api`
- one Postgres database: `blink-db`
- one Key Value instance: `blink-cache`

## Environment variables

- `SERVER_PORT`
- `APP_BASE_URL`
- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `REDIS_URL`
- `RATE_LIMIT_REQUESTS_PER_MINUTE`
- `CORS_ALLOWED_ORIGINS`

## Example requests

Create:

```http
POST /api/urls
Content-Type: application/json

{
  "url": "https://example.com/very-long-url",
  "customAlias": "blink123",
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

List:

```http
GET /api/urls
```

Stats:

```http
GET /api/urls/blink123/stats
```

Delete:

```http
DELETE /api/urls/0f8fad5b-d9cb-469f-a165-70867728950e
```

Redirect:

```http
GET /blink123
```
