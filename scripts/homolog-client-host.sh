#!/usr/bin/env bash
# Homolog smoke for the Docker *host* that runs the client daemon (#40 / #41).
# Does NOT require Kubernetes. Use against staging OR a local docker-compose backend.
#
# Usage:
#   ./scripts/homolog-client-host.sh check          # prerequisites only
#   ./scripts/homolog-client-host.sh build-images   # agent + live + egress-proxy
#   ./scripts/homolog-client-host.sh pull-ghcr      # pull published images from GHCR
#   ./scripts/homolog-client-host.sh test-egress    # integration tests (needs Docker + xt_set)
#   ./scripts/homolog-client-host.sh all            # check → build → test-egress
#
# Exit codes: 0 ok · 1 failed check · 2 missing tool

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Colima on macOS does not use /var/run/docker.sock — point the Python docker
# SDK (and this shell) at the VM socket when present and DOCKER_HOST is unset.
if [[ -z "${DOCKER_HOST:-}" ]]; then
  for sock in "${HOME}/.colima/docker.sock" "${HOME}/.colima/default/docker.sock"; do
    if [[ -S "$sock" ]]; then
      export DOCKER_HOST="unix://${sock}"
      break
    fi
  done
fi

RED=$'\033[31m'; GRN=$'\033[32m'; YLW=$'\033[33m'; RST=$'\033[0m'
ok()   { echo "${GRN}✓${RST} $*"; }
warn() { echo "${YLW}!${RST} $*"; }
fail() { echo "${RED}✗${RST} $*"; }

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "missing command: $1"
    return 1
  fi
  ok "found $1 ($(command -v "$1"))"
}

cmd_check() {
  local rc=0
  echo "== Prerequisites (client host / #40) =="
  need_cmd docker || rc=1
  if command -v docker >/dev/null 2>&1; then
    if docker info >/dev/null 2>&1; then
      ok "docker daemon reachable"
    else
      fail "docker daemon not reachable (start Colima: colima start · or Docker Desktop)"
      rc=1
    fi
  fi

  # Kernel features for egress (only meaningful on Linux; Colima VM is Linux under the hood
  # but xt_set is inspected *inside* privileged containers / host netns — report best-effort).
  if [[ "$(uname -s)" == "Linux" ]]; then
    if lsmod 2>/dev/null | grep -qE '^ip_set|^xt_set' \
      || (command -v modprobe >/dev/null && sudo -n modprobe ip_set 2>/dev/null); then
      ok "ip_set/xt_set available (or loadable)"
    else
      warn "ip_set/xt_set not confirmed — egress IT may fail-closed (needs Linux host)"
    fi
  else
    warn "host is $(uname -s): full egress netns/ipset proof needs Linux (or Colima IT inside the VM)."
    warn "Image builds + non-integration client tests still work here."
  fi

  if [[ -f client/pyproject.toml ]]; then
    ok "repo client/ present"
  else
    fail "run from the squadx.dev repo root"
    rc=1
  fi

  echo ""
  echo "== Published images (optional) =="
  if docker images --format '{{.Repository}}:{{.Tag}}' 2>/dev/null \
      | grep -qE 'squadx/agent:|ghcr.io/.*/agent:'; then
    docker images --format '{{.Repository}}:{{.Tag}}' | grep -E 'agent|egress' || true
    ok "sandbox-related images present locally"
  else
    warn "no agent/egress images yet — run: $0 build-images   or   $0 pull-ghcr"
  fi

  return "$rc"
}

cmd_build_images() {
  need_cmd docker
  docker info >/dev/null
  echo "== Building sandbox images (Makefile) =="
  make build-sandbox-images
  docker images | grep -E 'squadx/(agent|egress-proxy)' || {
    fail "expected squadx/agent and squadx/egress-proxy tags"
    exit 1
  }
  ok "images built"
}

cmd_pull_ghcr() {
  need_cmd docker
  local repo="${GHCR_REPO:-ghcr.io/edsonmartins/squadx.dev}"
  echo "== Pulling from $repo (may need: echo \$GHCR_TOKEN | docker login ghcr.io -u USER --password-stdin) =="
  docker pull "${repo}/agent:latest"
  docker pull "${repo}/agent:live"
  docker pull "${repo}/egress-proxy:latest"
  docker tag "${repo}/agent:latest" squadx/agent:latest
  docker tag "${repo}/agent:live" squadx/agent:live
  docker tag "${repo}/egress-proxy:latest" squadx/egress-proxy:latest
  ok "pulled and tagged as squadx/* for local defaults"
}

cmd_test_egress() {
  need_cmd docker
  docker info >/dev/null
  if ! docker images --format '{{.Repository}}:{{.Tag}}' | grep -q 'egress-proxy'; then
    warn "egress-proxy image missing — building"
    make build-egress-proxy
  fi
  echo "== Integration: egress sidecar (SQUADX_DOCKER_IT=1) =="
  (
    cd client
    if [[ ! -d .venv ]]; then
      python3 -m venv .venv
      .venv/bin/pip install -q -e ".[dev]"
    fi
    # shellcheck disable=SC1091
    source .venv/bin/activate
    SQUADX_DOCKER_IT=1 pytest -m integration tests/test_egress_sidecar.py -v
  )
  ok "egress integration tests finished"
}

cmd_all() {
  cmd_check
  cmd_build_images
  cmd_test_egress
  echo ""
  ok "homolog client-host path complete (check + images + egress IT)"
  echo "Next: point client/deploy env at a backend (staging or local compose) and start the daemon."
  echo "See client/deploy/README.md and documentos/HOMOLOGACAO-LOCAL-DOCKER.md"
}

usage() {
  sed -n '2,14p' "$0"
}

case "${1:-}" in
  check)        cmd_check ;;
  build-images) cmd_build_images ;;
  pull-ghcr)    cmd_pull_ghcr ;;
  test-egress)  cmd_test_egress ;;
  all)          cmd_all ;;
  -h|--help|help|"") usage; exit 0 ;;
  *) fail "unknown command: $1"; usage; exit 2 ;;
esac
