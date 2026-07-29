#!/usr/bin/env bash
# Local end-to-end smoke without Kubernetes:
#   postgres+redis (compose) → backend → create task → client daemon claims it
#
# Prerequisites:
#   - Colima/Docker (or Linux Docker)
#   - JDK 21 (JAVA_HOME)
#   - Optional: ANTHROPIC_API_KEY or OPENAI_API_KEY for a full agent run
#     (without keys the daemon still claims the task and fails at LLM — still useful)
#
# Usage (from repo root):
#   ./scripts/homolog-local-smoke.sh up          # deps + print how to start backend
#   ./scripts/homolog-local-smoke.sh seed        # login, project, task, start execution
#   ./scripts/homolog-local-smoke.sh client      # run daemon in foreground
#   ./scripts/homolog-local-smoke.sh status      # execution status
#
# Gotchas discovered during homolog:
#   - Host Postgres on :5432 shadows compose → use 55432/56379 (see compose-homolog ports)
#   - JWT_SECRET must be base64 (jjwt Decoders.BASE64)
#   - Empty OAuth2 client registrations break boot → exclude OAuth2ClientAutoConfiguration
#   - Empty AWS_ACCESS_KEY_ID still activates S3 beans → set dummy or leave unset carefully

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:${JAVA_HOME:-}/bin:${PATH}"
if [[ -z "${DOCKER_HOST:-}" && -S "${HOME}/.colima/default/docker.sock" ]]; then
  export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
fi

API="${SQUADX_API_URL:-http://127.0.0.1:8080}"
PG_PORT="${HOMOLOG_PG_PORT:-55432}"
REDIS_PORT="${HOMOLOG_REDIS_PORT:-56379}"
COMPOSE_PORTS="/tmp/squadx-compose-homolog-ports.yml"

write_compose_ports() {
  cat > "$COMPOSE_PORTS" <<EOF
services:
  postgres:
    ports: !override
      - "${PG_PORT}:5432"
  redis:
    ports: !override
      - "${REDIS_PORT}:6379"
EOF
}

cmd_up() {
  write_compose_ports
  docker compose -f docker-compose.yml -f "$COMPOSE_PORTS" up -d postgres redis
  echo "Postgres :${PG_PORT}  Redis :${REDIS_PORT}"
  cat <<EOF

Start the backend in another terminal (JDK 21):

  export JAVA_HOME=\${JAVA_HOME:-\$HOME/.sdkman/candidates/java/21.0.2-graalce}
  export PATH="\$JAVA_HOME/bin:\$PATH"
  export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:${PG_PORT}/squadx
  export SPRING_DATASOURCE_USERNAME=squadx
  export SPRING_DATASOURCE_PASSWORD=squadx_dev_password
  export SPRING_DATA_REDIS_HOST=127.0.0.1
  export SPRING_DATA_REDIS_PORT=${REDIS_PORT}
  export JWT_SECRET=\$(openssl rand -base64 48 | tr -d '\\n')
  export AWS_ACCESS_KEY_ID=local-dev-not-real
  export AWS_SECRET_ACCESS_KEY=local-dev-not-real
  export SPRING_AUTOCONFIGURE_EXCLUDE=\\
org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration

  cd backend && ./mvnw spring-boot:run -DskipTests

Then:  $0 seed && $0 client
EOF
}

login_token() {
  curl -sf -X POST "$API/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"email":"admin@squadx.dev","password":"admin123"}' \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["access_token"])'
}

cmd_seed() {
  curl -sf "$API/api/v1/health" >/dev/null || { echo "backend not up at $API"; exit 1; }
  TOKEN=$(login_token)
  AUTH="Authorization: Bearer $TOKEN"
  ORG_ID=$(curl -sf "$API/api/v1/organizations/my" -H "$AUTH" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["content"][0]["id"])')
  PROJS=$(curl -sf "$API/api/v1/projects/my" -H "$AUTH")
  PROJ_ID=$(echo "$PROJS" | python3 -c 'import sys,json; c=json.load(sys.stdin)["data"]["content"]; print(c[0]["id"] if c else "")')
  if [[ -z "$PROJ_ID" ]]; then
    PROJ_ID=$(curl -sf -X POST "$API/api/v1/projects" -H "$AUTH" -H 'Content-Type: application/json' \
      -d "{\"name\":\"Homolog Local\",\"description\":\"smoke\",\"organization_id\":$ORG_ID}" \
      | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["id"])')
  fi
  # Seeded agent id 1 (Claude Fullstack) from V3 migration
  AGENT_ID="${HOMOLOG_AGENT_ID:-1}"
  TASK=$(curl -sf -X POST "$API/api/v1/tasks" -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"title\":\"Homolog smoke $(date +%H%M%S)\",\"description\":\"Local smoke task\",\"status\":\"TODO\",\"priority\":\"MEDIUM\",\"project_id\":$PROJ_ID}")
  TASK_ID=$(echo "$TASK" | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["id"])')
  KEY="homolog-$(date +%s)-$RANDOM"
  EXEC=$(curl -sf -X POST "$API/api/v1/executions" -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"task_id\":$TASK_ID,\"agent_id\":$AGENT_ID,\"idempotency_key\":\"$KEY\"}")
  EXEC_ID=$(echo "$EXEC" | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["id"])')
  mkdir -p /tmp
  cat > /tmp/squadx-smoke.env <<EOF
TOKEN=$TOKEN
ORG_ID=$ORG_ID
PROJ_ID=$PROJ_ID
TASK_ID=$TASK_ID
EXEC_ID=$EXEC_ID
AGENT_ID=$AGENT_ID
API=$API
EOF
  echo "Seeded task=$TASK_ID execution=$EXEC_ID (agent=$AGENT_ID)"
  echo "env: /tmp/squadx-smoke.env"
}

cmd_client() {
  [[ -f /tmp/squadx-smoke.env ]] || { echo "run: $0 seed first"; exit 1; }
  # shellcheck disable=SC1091
  source /tmp/squadx-smoke.env
  cd "$ROOT/client"
  [[ -d .venv ]] || { python3 -m venv .venv; .venv/bin/pip install -q -e ".[dev]"; }
  export SQUADX_API_URL="${API}"
  export SQUADX_WS_URL="${API/http/ws}/ws"
  export SQUADX_API_TOKEN="$TOKEN"
  export SQUADX_AGENT_IMAGE="${SQUADX_AGENT_IMAGE:-squadx/agent:latest}"
  export SQUADX_EGRESS_PROXY_IMAGE="${SQUADX_EGRESS_PROXY_IMAGE:-squadx/egress-proxy:latest}"
  export SQUADX_EGRESS_SIDECAR="${SQUADX_EGRESS_SIDECAR:-true}"
  export SQUADX_ENABLE_VNC="${SQUADX_ENABLE_VNC:-false}"
  export SQUADX_POLL_FALLBACK_INTERVAL_SECONDS="${SQUADX_POLL_FALLBACK_INTERVAL_SECONDS:-5}"
  export SQUADX_BLOCK_CLOUD_METADATA="${SQUADX_BLOCK_CLOUD_METADATA:-false}"
  export DOCKER_HOST="${DOCKER_HOST:-unix://${HOME}/.colima/default/docker.sock}"
  echo "Starting daemon → claims pending executions (e.g. $EXEC_ID)"
  exec .venv/bin/python -m squadx_client.main start --foreground
}

cmd_status() {
  [[ -f /tmp/squadx-smoke.env ]] || { echo "no /tmp/squadx-smoke.env"; exit 1; }
  # shellcheck disable=SC1091
  source /tmp/squadx-smoke.env
  TOKEN=$(login_token)
  curl -sf "$API/api/v1/executions/$EXEC_ID" -H "Authorization: Bearer $TOKEN" | python3 -m json.tool | head -80
}

case "${1:-}" in
  up) cmd_up ;;
  seed) cmd_seed ;;
  client) cmd_client ;;
  status) cmd_status ;;
  *) sed -n '2,30p' "$0"; exit 0 ;;
esac
