#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FRONTEND_DIR="$ROOT_DIR/frontend"
BACKEND_DIR="$ROOT_DIR/backend"
CLIENT_DIR="$ROOT_DIR/client"
COMPOSE_FILE="$FRONTEND_DIR/e2e-real/docker-compose.real.yml"

RUN_SUFFIX="${E2E_REAL_RUN_SUFFIX:-$$}"
PROJECT_NAME="${E2E_REAL_PROJECT_NAME:-squadx-e2e-real-${RUN_SUFFIX}}"
API_PORT="${E2E_API_PORT:-8082}"
FRONTEND_PORT="${E2E_FRONTEND_PORT:-3002}"
DB_PORT="${E2E_DB_PORT:-55432}"
REDIS_PORT="${E2E_REDIS_PORT:-56379}"
JWT_SECRET="${JWT_SECRET:-c3F1YWR4LWRldi1qd3Qtc2VjcmV0LWZvci1lMmUtcmVhbC0zMi1ieXRlcw==}"
SERVICE_SECRET="${SQUADX_SERVICE_SECRET:-squadx-service-secret-for-e2e-real-32b}"
AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-dummy-access-key}"
AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-dummy-secret-key}"
AWS_REGION="${AWS_REGION:-us-east-1}"
AWS_S3_BUCKET="${AWS_S3_BUCKET:-squadx-recordings}"
OAUTH2_GOOGLE_CLIENT_ID="${OAUTH2_GOOGLE_CLIENT_ID:-dummy-google-client-id}"
OAUTH2_GOOGLE_CLIENT_SECRET="${OAUTH2_GOOGLE_CLIENT_SECRET:-dummy-google-client-secret}"
OAUTH2_MICROSOFT_CLIENT_ID="${OAUTH2_MICROSOFT_CLIENT_ID:-dummy-microsoft-client-id}"
OAUTH2_MICROSOFT_CLIENT_SECRET="${OAUTH2_MICROSOFT_CLIENT_SECRET:-dummy-microsoft-client-secret}"
OAUTH2_MICROSOFT_TENANT_ID="${OAUTH2_MICROSOFT_TENANT_ID:-common}"
OAUTH2_OKTA_CLIENT_ID="${OAUTH2_OKTA_CLIENT_ID:-dummy-okta-client-id}"
OAUTH2_OKTA_CLIENT_SECRET="${OAUTH2_OKTA_CLIENT_SECRET:-dummy-okta-client-secret}"
OAUTH2_OKTA_ISSUER_URI="${OAUTH2_OKTA_ISSUER_URI:-https://example.okta.com/oauth2/default}"

BACKEND_PID=""
BACKEND_LOG_FILE="/tmp/${PROJECT_NAME}-backend.log"
DAEMON_PID=""
DAEMON_LOG_FILE="/tmp/${PROJECT_NAME}-daemon.log"

cleanup() {
  if [[ -n "$DAEMON_PID" ]] && kill -0 "$DAEMON_PID" >/dev/null 2>&1; then
    kill "$DAEMON_PID" >/dev/null 2>&1 || true
    wait "$DAEMON_PID" >/dev/null 2>&1 || true
  fi

  if [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
    kill "$BACKEND_PID" >/dev/null 2>&1 || true
    wait "$BACKEND_PID" >/dev/null 2>&1 || true
  fi

  docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" down >/dev/null 2>&1 || true
}

trap cleanup EXIT

wait_for_port() {
  local host="$1"
  local port="$2"
  local name="$3"

  for _ in $(seq 1 120); do
    if bash -lc "</dev/tcp/${host}/${port}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done

  echo "${name} did not become reachable on ${host}:${port}"
  return 1
}

echo "Starting isolated Postgres and Redis for real smoke..."
docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" up -d

echo "Waiting for Postgres on ${DB_PORT}..."
wait_for_port "127.0.0.1" "$DB_PORT" "Postgres"

echo "Waiting for Redis on ${REDIS_PORT}..."
wait_for_port "127.0.0.1" "$REDIS_PORT" "Redis"

echo "Starting backend on port ${API_PORT}..."
(
  cd "$BACKEND_DIR"
  SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${DB_PORT}/squadx" \
  SPRING_DATASOURCE_USERNAME="squadx" \
  SPRING_DATASOURCE_PASSWORD="squadx_dev_password" \
  SPRING_DATA_REDIS_HOST="127.0.0.1" \
  SPRING_DATA_REDIS_PORT="${REDIS_PORT}" \
  SPRING_AUTOCONFIGURE_EXCLUDE="org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration" \
  ORG_JOBRUNR_DASHBOARD_ENABLED="false" \
  JWT_SECRET="$JWT_SECRET" \
  SQUADX_SERVICE_SECRET="${SERVICE_SECRET}" \
  SERVER_PORT="${API_PORT}" \
  CORS_ALLOWED_ORIGINS="http://localhost:${FRONTEND_PORT},http://127.0.0.1:${FRONTEND_PORT}" \
  AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}" \
  AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}" \
  AWS_REGION="${AWS_REGION}" \
  AWS_S3_BUCKET="${AWS_S3_BUCKET}" \
  SQUADX_BRAINSENTRY_URL="${SQUADX_BRAINSENTRY_URL:-}" \
  SQUADX_BRAINSENTRY_TENANT_ID="${SQUADX_BRAINSENTRY_TENANT_ID:-default}" \
  SQUADX_BRAINSENTRY_TENANT_PREFIX="${SQUADX_BRAINSENTRY_TENANT_PREFIX:-org-}" \
  SQUADX_BRAINSENTRY_PER_ORGANIZATION_TENANT="${SQUADX_BRAINSENTRY_PER_ORGANIZATION_TENANT:-true}" \
  SQUADX_BRAINSENTRY_ENABLED="${SQUADX_BRAINSENTRY_ENABLED:-false}" \
  OAUTH2_GOOGLE_CLIENT_ID="${OAUTH2_GOOGLE_CLIENT_ID}" \
  OAUTH2_GOOGLE_CLIENT_SECRET="${OAUTH2_GOOGLE_CLIENT_SECRET}" \
  OAUTH2_MICROSOFT_CLIENT_ID="${OAUTH2_MICROSOFT_CLIENT_ID}" \
  OAUTH2_MICROSOFT_CLIENT_SECRET="${OAUTH2_MICROSOFT_CLIENT_SECRET}" \
  OAUTH2_MICROSOFT_TENANT_ID="${OAUTH2_MICROSOFT_TENANT_ID}" \
  OAUTH2_OKTA_CLIENT_ID="${OAUTH2_OKTA_CLIENT_ID}" \
  OAUTH2_OKTA_CLIENT_SECRET="${OAUTH2_OKTA_CLIENT_SECRET}" \
  OAUTH2_OKTA_ISSUER_URI="${OAUTH2_OKTA_ISSUER_URI}" \
  mvn spring-boot:run
) > "${BACKEND_LOG_FILE}" 2>&1 &
BACKEND_PID=$!

echo "Waiting for backend health endpoint..."
for _ in $(seq 1 120); do
  if curl -sS "http://127.0.0.1:${API_PORT}/api/v1/health/live" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! curl -sS "http://127.0.0.1:${API_PORT}/api/v1/health/live" >/dev/null 2>&1; then
  echo "Backend did not become healthy. See ${BACKEND_LOG_FILE}"
  exit 1
fi

if [[ "${E2E_DAEMON_SMOKE:-0}" == "1" ]]; then
  echo "Starting local daemon smoke client..."
  ACCESS_TOKEN="$(curl -sS -X POST "http://127.0.0.1:${API_PORT}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${E2E_ADMIN_EMAIL:-admin@squadx.dev}\",\"password\":\"${E2E_ADMIN_PASSWORD:-admin123}\"}" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("data") or {}).get("access_token",""))')"

  if [[ -z "$ACCESS_TOKEN" ]]; then
    echo "Could not obtain admin access token for daemon smoke"
    exit 1
  fi

  (
    cd "$CLIENT_DIR"
    PYTHONPATH="$CLIENT_DIR" \
    SQUADX_API_URL="http://127.0.0.1:${API_PORT}" \
    SQUADX_WS_URL="ws://127.0.0.1:${API_PORT}/ws" \
    SQUADX_API_TOKEN="$ACCESS_TOKEN" \
    SQUADX_BRAINSENTRY_URL="${SQUADX_BRAINSENTRY_URL:-}" \
    SQUADX_BRAINSENTRY_API_KEY="${SQUADX_BRAINSENTRY_API_KEY:-}" \
    SQUADX_BRAINSENTRY_TENANT_ID="${SQUADX_BRAINSENTRY_TENANT_ID:-default}" \
    SQUADX_SMOKE_EXECUTION_MODE="true" \
    SQUADX_SMOKE_EXECUTION_DELAY_SECONDS="0.25" \
    python3 -m squadx_client.main start --foreground
  ) > "${DAEMON_LOG_FILE}" 2>&1 &
  DAEMON_PID=$!
fi

echo "Running real E2E smoke..."
(
  cd "$FRONTEND_DIR"
  E2E_API_URL="http://127.0.0.1:${API_PORT}" \
  E2E_FRONTEND_PORT="${FRONTEND_PORT}" \
  E2E_SERVICE_SECRET="${SERVICE_SECRET}" \
  E2E_DAEMON_SMOKE="${E2E_DAEMON_SMOKE:-0}" \
  pnpm test:e2e:real
)
