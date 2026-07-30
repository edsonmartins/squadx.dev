#!/usr/bin/env bash
# SquadX Client — Team DOCKER / VPS installer (ADR-0009 Fase 1)
#
# Installs the daemon on a dedicated Linux host with Docker Engine.
# Does NOT install Kubernetes or the control-plane API.
#
# Usage (from a clone of the monorepo, as a user with sudo):
#   ./scripts/install-vps.sh
#   ./scripts/install-vps.sh --pull-images
#   ./scripts/install-vps.sh --skip-images
#   SQUADX_REPO_URL=... SQUADX_INSTALL_ROOT=/opt/squadx-client ./scripts/install-vps.sh
#
# After install:
#   sudoedit /etc/squadx/squadx-client.env
#   sudo systemctl enable --now squadx-client
#   sudo -u squadx /opt/squadx-client/.venv/bin/squadx-client doctor
#
set -euo pipefail

# shellcheck source=lib/sandbox-images.sh
source "$(cd "$(dirname "$0")" && pwd)/lib/sandbox-images.sh"

INSTALL_ROOT="${SQUADX_INSTALL_ROOT:-/opt/squadx-client}"
ENV_DIR="${SQUADX_ENV_DIR:-/etc/squadx}"
ENV_FILE="${ENV_DIR}/squadx-client.env"
SERVICE_NAME="squadx-client"
SERVICE_USER="${SQUADX_SERVICE_USER:-squadx}"
GHCR_REPO="${SQUADX_GHCR_REPO:-ghcr.io/edsonmartins/squadx.dev}"
PULL_IMAGES=0
SKIP_IMAGES=0
SKIP_SYSTEMD=0

RED=$'\033[31m'; GRN=$'\033[32m'; YLW=$'\033[33m'; BLU=$'\033[34m'; RST=$'\033[0m'
info() { echo "${BLU}[INFO]${RST} $*"; }
ok()   { echo "${GRN}[OK]${RST} $*"; }
warn() { echo "${YLW}[WARN]${RST} $*"; }
fail() { echo "${RED}[ERROR]${RST} $*"; exit 1; }

usage() {
  sed -n '2,20p' "$0"
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --pull-images) PULL_IMAGES=1; shift ;;
    --skip-images) SKIP_IMAGES=1; shift ;;
    --skip-systemd) SKIP_SYSTEMD=1; shift ;;
    -h|--help) usage ;;
    *) fail "unknown arg: $1" ;;
  esac
done

need_cmd() { command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"; }

require_linux() {
  [[ "$(uname -s)" == "Linux" ]] || fail "install-vps.sh is for Linux VPS hosts (got $(uname -s)). Use install-mac-client.sh for macOS."
}

require_root_or_sudo() {
  if [[ "$(id -u)" -eq 0 ]]; then
    SUDO=""
  elif command -v sudo >/dev/null 2>&1; then
    SUDO="sudo"
  else
    fail "run as root or install sudo"
  fi
}

check_prereqs() {
  info "Checking prerequisites..."
  need_cmd python3
  need_cmd git
  need_cmd docker
  if ! docker info >/dev/null 2>&1; then
    fail "Docker daemon not reachable. Install Docker Engine and start it."
  fi
  ok "Docker daemon reachable"
  PYV=$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')
  python3 -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)' \
    || fail "Python 3.11+ required (found $PYV)"
  ok "Python $PYV"
}

ensure_user() {
  if id "$SERVICE_USER" >/dev/null 2>&1; then
    ok "user $SERVICE_USER exists"
  else
    info "Creating system user $SERVICE_USER"
    $SUDO useradd --system --home "$INSTALL_ROOT" --shell /usr/sbin/nologin "$SERVICE_USER" \
      || $SUDO useradd --system --home "$INSTALL_ROOT" --shell /bin/false "$SERVICE_USER"
  fi
  if getent group docker >/dev/null 2>&1; then
    $SUDO usermod -aG docker "$SERVICE_USER" || true
    ok "user $SERVICE_USER in group docker"
  else
    warn "group docker not found — add $SERVICE_USER to the docker group manually"
  fi
}

install_tree() {
  # Locate monorepo client/ relative to this script
  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
  CLIENT_SRC="$REPO_ROOT/client"
  [[ -d "$CLIENT_SRC" ]] || fail "client/ not found at $CLIENT_SRC — run from a monorepo checkout"

  info "Installing into $INSTALL_ROOT"
  $SUDO mkdir -p "$INSTALL_ROOT"
  # Sync source (preserve .venv if re-run)
  if [[ -d "$INSTALL_ROOT/src/client" ]]; then
    info "Updating client sources..."
  fi
  $SUDO mkdir -p "$INSTALL_ROOT/src"
  $SUDO rsync -a --delete \
    --exclude '.venv' --exclude '__pycache__' --exclude '.pytest_cache' --exclude '*.pyc' \
    "$CLIENT_SRC/" "$INSTALL_ROOT/src/client/" \
    || {
      # fallback without rsync
      $SUDO rm -rf "$INSTALL_ROOT/src/client"
      $SUDO mkdir -p "$INSTALL_ROOT/src/client"
      $SUDO cp -a "$CLIENT_SRC/." "$INSTALL_ROOT/src/client/"
    }

  if [[ ! -d "$INSTALL_ROOT/.venv" ]]; then
    info "Creating virtualenv..."
    $SUDO python3 -m venv "$INSTALL_ROOT/.venv"
  fi
  info "pip install -e client..."
  $SUDO "$INSTALL_ROOT/.venv/bin/pip" install --upgrade pip -q
  $SUDO "$INSTALL_ROOT/.venv/bin/pip" install -e "$INSTALL_ROOT/src/client" -q
  $SUDO chown -R "$SERVICE_USER:$SERVICE_USER" "$INSTALL_ROOT"
  ok "package installed: $INSTALL_ROOT/.venv/bin/squadx-client"
}

install_env() {
  $SUDO mkdir -p "$ENV_DIR"
  if [[ -f "$ENV_FILE" ]]; then
    ok "keeping existing $ENV_FILE"
  else
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
    EXAMPLE="$SCRIPT_DIR/../client/deploy/squadx-client.env.example"
    $SUDO cp "$EXAMPLE" "$ENV_FILE"
    $SUDO chown root:"$SERVICE_USER" "$ENV_FILE"
    $SUDO chmod 640 "$ENV_FILE"
    warn "Created $ENV_FILE — edit tokens and LLM keys before starting"
  fi
}

install_systemd() {
  if [[ "$SKIP_SYSTEMD" -eq 1 ]]; then
    warn "skipping systemd unit"
    return
  fi
  if ! command -v systemctl >/dev/null 2>&1; then
    warn "systemctl not found — skip unit install"
    return
  fi
  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  UNIT_SRC="$SCRIPT_DIR/../client/deploy/squadx-client.service"
  $SUDO cp "$UNIT_SRC" "/etc/systemd/system/${SERVICE_NAME}.service"
  $SUDO systemctl daemon-reload
  ok "installed /etc/systemd/system/${SERVICE_NAME}.service"
  info "Start when ready:  sudo systemctl enable --now ${SERVICE_NAME}"
}

pull_images() {
  squadx_pull_or_build_images "$PULL_IMAGES" "$SKIP_IMAGES" "$GHCR_REPO"
}

main() {
  echo ""
  echo "  SquadX VPS installer (Team DOCKER) — ADR-0009"
  echo ""
  require_linux
  require_root_or_sudo
  check_prereqs
  ensure_user
  install_tree
  install_env
  install_systemd
  pull_images

  echo ""
  ok "Install complete"
  echo ""
  echo "Next steps:"
  echo "  1. sudoedit $ENV_FILE"
  echo "       - SQUADX_API_URL / SQUADX_WS_URL / SQUADX_API_TOKEN"
  echo "       - OPENROUTER_API_KEY or OPENAI_API_KEY / ANTHROPIC_API_KEY"
  echo "       - SQUADX_DEFAULT_MODEL (e.g. openrouter/openai/gpt-4o-mini)"
  echo "  2. sudo -u $SERVICE_USER $INSTALL_ROOT/.venv/bin/squadx-client doctor"
  echo "  3. sudo systemctl enable --now $SERVICE_NAME"
  echo "  4. sudo journalctl -u $SERVICE_NAME -f"
  echo ""
}

main
