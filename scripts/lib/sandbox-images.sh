#!/usr/bin/env bash
# Shared helpers for Team DOCKER / Dev LIGHT installers (ADR-0009).
# Source from install-vps.sh / install-mac-client.sh:
#   # shellcheck source=lib/sandbox-images.sh
#   source "$(dirname "$0")/lib/sandbox-images.sh"
#
# Expects: info, ok, warn, fail helpers and optional GHCR_REPO.

squadx_pull_or_build_images() {
  local pull_images="${1:-0}"
  local skip_images="${2:-0}"
  local ghcr_repo="${3:-ghcr.io/edsonmartins/squadx.dev}"
  local script_dir root

  if [[ "$skip_images" -eq 1 ]]; then
    warn "skipping images"
    return 0
  fi

  if ! command -v docker >/dev/null 2>&1; then
    fail "docker not found"
  fi
  docker info >/dev/null 2>&1 || fail "docker daemon not reachable"

  if [[ "$pull_images" -eq 1 ]]; then
    info "Pulling sandbox images from $ghcr_repo ..."
    docker pull "${ghcr_repo}/agent:latest"
    docker pull "${ghcr_repo}/egress-proxy:latest"
    docker pull "${ghcr_repo}/agent:live" || warn "agent:live optional (Live View)"
    docker tag "${ghcr_repo}/agent:latest" squadx/agent:latest
    docker tag "${ghcr_repo}/egress-proxy:latest" squadx/egress-proxy:latest
    docker tag "${ghcr_repo}/agent:live" squadx/agent:live 2>/dev/null || true
    ok "images tagged as squadx/*"
    return 0
  fi

  if docker image inspect squadx/agent:latest >/dev/null 2>&1 \
    && docker image inspect squadx/egress-proxy:latest >/dev/null 2>&1; then
    ok "local squadx/agent and squadx/egress-proxy already present"
    return 0
  fi

  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  root="$(cd "$script_dir/../.." && pwd)"
  if [[ -f "$root/Makefile" ]] && command -v make >/dev/null 2>&1; then
    info "Building sandbox images via make build-sandbox-images (may take a while)..."
    (cd "$root" && make build-sandbox-images)
  else
    warn "Images missing. Re-run with --pull-images (docker login ghcr.io) or make build-sandbox-images"
  fi
}
