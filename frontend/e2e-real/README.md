# Real E2E Smoke

These tests run against a real local backend instead of mocked API routes.

Expected local services:

```bash
docker compose up -d postgres redis
```

Backend:

```bash
cd backend
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/squadx \
SPRING_DATASOURCE_USERNAME=squadx \
SPRING_DATASOURCE_PASSWORD=squadx_dev_password \
SPRING_DATA_REDIS_HOST=localhost \
SPRING_DATA_REDIS_PORT=6379 \
JWT_SECRET=dev-secret-key-change-in-production-min-32-chars \
./mvnw spring-boot:run
```

Frontend smoke:

```bash
cd frontend
pnpm test:e2e:real
```

Optional overrides:

- `E2E_API_URL`
- `E2E_FRONTEND_PORT`
- `E2E_ADMIN_EMAIL`
- `E2E_ADMIN_PASSWORD`

For an isolated local stack that avoids conflicts with existing services:

```bash
cd frontend
pnpm test:e2e:real:local
```

This uses:

- Postgres on `55432`
- Redis on `56379`
- Backend on `8082`
- Frontend on `3002`
