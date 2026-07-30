#!/usr/bin/env bash
# Post-install smoke for Dev LIGHT / macOS (ADR-0009 §3.5).
#
# Usage:
#   ./scripts/smoke-mac.sh
#   SQUADX_INSTALL_DIR=$HOME/.squadx ./scripts/smoke-mac.sh
#
# Exit: 0 ok · 1 failed · 2 missing tool

set -euo pipefail

INSTALL_DIR="${SQUADX_INSTALL_DIR:-$HOME/.squadx}"
RED=$'\033[31m'; GRN=$'\033[32m'; YLW=$'\033[33m'; RST=$'\033[0m'
ok()   { echo "${GRN}✓${RST} $*"; }
warn() { echo "${YLW}!${RST} $*"; }
fail() { echo "${RED}✗${RST} $*"; }

if [[ "$(uname -s)" != "Darwin" ]]; then
  warn "smoke-mac is intended for macOS (host=$(uname -s)); continuing best-effort"
fi

if [[ -f "$INSTALL_DIR/env.sh" ]]; then
  # shellcheck disable=SC1090
  source "$INSTALL_DIR/env.sh"
  ok "sourced $INSTALL_DIR/env.sh"
else
  warn "no $INSTALL_DIR/env.sh — using process env only"
fi

# Colima socket if DOCKER_HOST unset
if [[ -z "${DOCKER_HOST:-}" ]]; then
  for sock in "${HOME}/.colima/default/docker.sock" "${HOME}/.colima/docker.sock"; do
    if [[ -S "$sock" ]]; then
      export DOCKER_HOST="unix://${sock}"
      ok "DOCKER_HOST=$DOCKER_HOST"
      break
    fi
  done
fi

rc=0
if command -v colima >/dev/null 2>&1; then
  if colima status 2>/dev/null | grep -qi Running; then
    ok "Colima running"
  else
    fail "Colima not running — colima start"
    rc=1
  fi
else
  warn "colima not in PATH"
fi

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  ok "docker daemon reachable"
else
  fail "docker not reachable"
  rc=1
fi

CLIENT="${INSTALL_DIR}/bin/squadx-client"
if [[ ! -x "$CLIENT" ]]; then
  CLIENT="$(command -v squadx-client 2>/dev/null || true)"
fi
if [[ -z "$CLIENT" || ! -x "$CLIENT" ]]; then
  fail "squadx-client not found — run ./scripts/install-mac-client.sh"
  exit 2
fi
ok "using $CLIENT"

if "$CLIENT" doctor; then
  ok "doctor passed"
else
  fail "doctor failed"
  rc=1
fi

if [[ $rc -eq 0 ]]; then
  ok "Mac Dev LIGHT smoke complete"
else
  fail "Mac smoke finished with failures"
fi
exit "$rc"
