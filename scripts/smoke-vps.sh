#!/usr/bin/env bash
# Post-install smoke for Team DOCKER / VPS (ADR-0009 §1.5).
#
# Validates that a host prepared with install-vps.sh (or equivalent) can run the
# client daemon prerequisites: doctor, images, optional systemd unit, optional
# API health. Does NOT require Kubernetes.
#
# Usage (from repo root, or on the VPS after install):
#   ./scripts/smoke-vps.sh                  # doctor + images + daemon/unit hints
#   ./scripts/smoke-vps.sh doctor           # only squadx-client doctor
#   ./scripts/smoke-vps.sh systemd          # check unit active (if installed)
#   ./scripts/smoke-vps.sh all              # doctor + systemd + env presence
#
# Env:
#   SQUADX_CLIENT_BIN   path to squadx-client (default: first on PATH, then venv)
#   SQUADX_ENV_FILE     default /etc/squadx/squadx-client.env
#   SKIP_DOCKER=1       pass --skip-docker to doctor
#   SKIP_API=1          pass --skip-api to doctor
#
# Exit: 0 ok · 1 failed check · 2 missing tool

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${SQUADX_ENV_FILE:-/etc/squadx/squadx-client.env}"
UNIT_NAME="${SQUADX_SYSTEMD_UNIT:-squadx-client}"

RED=$'\033[31m'; GRN=$'\033[32m'; YLW=$'\033[33m'; RST=$'\033[0m'
ok()   { echo "${GRN}✓${RST} $*"; }
warn() { echo "${YLW}!${RST} $*"; }
fail() { echo "${RED}✗${RST} $*"; }

resolve_client() {
  if [[ -n "${SQUADX_CLIENT_BIN:-}" && -x "${SQUADX_CLIENT_BIN}" ]]; then
    echo "${SQUADX_CLIENT_BIN}"
    return
  fi
  if command -v squadx-client >/dev/null 2>&1; then
    command -v squadx-client
    return
  fi
  for candidate in \
    /opt/squadx-client/.venv/bin/squadx-client \
    "${ROOT}/client/.venv/bin/squadx-client"; do
    if [[ -x "$candidate" ]]; then
      echo "$candidate"
      return
    fi
  done
  return 1
}

load_env_if_present() {
  if [[ -f "$ENV_FILE" ]]; then
    # shellcheck disable=SC1090
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE" || true
    set +a
    ok "loaded env from $ENV_FILE"
  else
    warn "no env file at $ENV_FILE (using process environment)"
  fi
}

cmd_doctor() {
  local bin
  if ! bin="$(resolve_client)"; then
    fail "squadx-client not found — install client or set SQUADX_CLIENT_BIN"
    return 2
  fi
  ok "using $bin"
  load_env_if_present

  local args=()
  [[ "${SKIP_DOCKER:-0}" == "1" ]] && args+=(--skip-docker)
  [[ "${SKIP_API:-0}" == "1" ]] && args+=(--skip-api)

  echo "== squadx-client doctor =="
  if "$bin" doctor "${args[@]+"${args[@]}"}"; then
    ok "doctor passed"
    return 0
  fi
  fail "doctor reported FAIL checks"
  return 1
}

cmd_systemd() {
  if [[ "$(uname -s)" != "Linux" ]]; then
    warn "systemd smoke is Linux-only (host=$(uname -s))"
    return 0
  fi
  if ! command -v systemctl >/dev/null 2>&1; then
    warn "systemctl not available"
    return 0
  fi
  if systemctl cat "${UNIT_NAME}.service" >/dev/null 2>&1; then
    ok "unit ${UNIT_NAME}.service installed"
  else
    warn "unit ${UNIT_NAME}.service not installed (run scripts/install-vps.sh)"
    return 0
  fi
  if systemctl is-active --quiet "${UNIT_NAME}"; then
    ok "unit ${UNIT_NAME} is active"
  else
    fail "unit ${UNIT_NAME} is not active — systemctl status ${UNIT_NAME}"
    systemctl status "${UNIT_NAME}" --no-pager 2>/dev/null || true
    return 1
  fi
  if journalctl -u "${UNIT_NAME}" -n 20 --no-pager 2>/dev/null | grep -qiE 'daemon|started|connected|ready|error'; then
    ok "recent journal lines present for ${UNIT_NAME}"
    journalctl -u "${UNIT_NAME}" -n 8 --no-pager 2>/dev/null || true
  else
    warn "no recent journal lines (service may be idle)"
  fi
  return 0
}

cmd_env() {
  if [[ -f "$ENV_FILE" ]]; then
    ok "env file exists: $ENV_FILE"
    if grep -qE '^SQUADX_API_TOKEN=(change-me|your-api-token-here)?$' "$ENV_FILE" 2>/dev/null \
      || ! grep -qE '^SQUADX_API_TOKEN=.+' "$ENV_FILE" 2>/dev/null; then
      fail "SQUADX_API_TOKEN still missing/placeholder in $ENV_FILE"
      return 1
    fi
    ok "SQUADX_API_TOKEN looks set"
  else
    warn "env file missing: $ENV_FILE"
  fi
  return 0
}

cmd_all() {
  local rc=0
  cmd_env || rc=1
  cmd_doctor || rc=$?
  cmd_systemd || rc=1
  echo ""
  if [[ $rc -eq 0 ]]; then
    ok "VPS smoke complete"
  else
    fail "VPS smoke finished with failures (exit $rc)"
  fi
  return "$rc"
}

usage() {
  sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'
}

main() {
  local cmd="${1:-all}"
  case "$cmd" in
    doctor)  cmd_doctor ;;
    systemd) cmd_systemd ;;
    env)     cmd_env ;;
    all)     cmd_all ;;
    -h|--help|help) usage ;;
    *)
      fail "unknown command: $cmd"
      usage
      exit 2
      ;;
  esac
}

main "${1:-all}"
