#!/usr/bin/env bash
# SquadX Client — Dev LIGHT installer for macOS (ADR-0009 Fase 3)
#
# Prepares a Mac mini / laptop to run the client daemon against SaaS or a local API.
# Uses Homebrew + Colima + Docker CLI (not Docker Desktop required).
# Does NOT install Kubernetes, Java, Postgres, or the control-plane stack.
#
# Usage (from a monorepo checkout):
#   ./scripts/install-mac-client.sh
#   ./scripts/install-mac-client.sh --pull-images
#   ./scripts/install-mac-client.sh --skip-images --skip-brew
#   ./scripts/install-mac-client.sh --non-interactive
#
# Env:
#   SQUADX_INSTALL_DIR   default: $HOME/.squadx
#   SQUADX_GHCR_REPO     default: ghcr.io/edsonmartins/squadx.dev
#   SQUADX_API_URL       pre-seed env wizard
#   SQUADX_API_TOKEN
#   OPENROUTER_API_KEY / OPENAI_API_KEY / ANTHROPIC_API_KEY
#
# After install:
#   source ~/.squadx/env.sh
#   squadx-client doctor
#   squadx-client start -f
#
set -euo pipefail

# shellcheck source=lib/sandbox-images.sh
source "$(cd "$(dirname "$0")" && pwd)/lib/sandbox-images.sh"

INSTALL_DIR="${SQUADX_INSTALL_DIR:-$HOME/.squadx}"
GHCR_REPO="${SQUADX_GHCR_REPO:-ghcr.io/edsonmartins/squadx.dev}"
PULL_IMAGES=0
SKIP_IMAGES=0
SKIP_BREW=0
SKIP_COLIMA=0
NON_INTERACTIVE=0

RED=$'\033[31m'; GRN=$'\033[32m'; YLW=$'\033[33m'; BLU=$'\033[34m'; RST=$'\033[0m'
info() { echo "${BLU}[INFO]${RST} $*"; }
ok()   { echo "${GRN}[OK]${RST} $*"; }
warn() { echo "${YLW}[WARN]${RST} $*"; }
fail() { echo "${RED}[ERROR]${RST} $*"; exit 1; }

usage() {
  sed -n '2,28p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --pull-images) PULL_IMAGES=1; shift ;;
    --skip-images) SKIP_IMAGES=1; shift ;;
    --skip-brew) SKIP_BREW=1; shift ;;
    --skip-colima) SKIP_COLIMA=1; shift ;;
    --non-interactive) NON_INTERACTIVE=1; shift ;;
    -h|--help) usage ;;
    *) fail "unknown arg: $1" ;;
  esac
done

need_cmd() { command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"; }

require_macos() {
  [[ "$(uname -s)" == "Darwin" ]] || fail "install-mac-client.sh is for macOS (got $(uname -s)). Use install-vps.sh on Linux."
}

ensure_path_homebrew() {
  # Apple Silicon and Intel common prefixes
  for p in /opt/homebrew/bin /usr/local/bin; do
    if [[ -x "$p/brew" ]]; then
      export PATH="$p:$PATH"
      return
    fi
  done
}

export_colima_docker_host() {
  if [[ -n "${DOCKER_HOST:-}" ]]; then
    return
  fi
  for sock in \
    "${HOME}/.colima/default/docker.sock" \
    "${HOME}/.colima/docker.sock"; do
    if [[ -S "$sock" ]]; then
      export DOCKER_HOST="unix://${sock}"
      info "DOCKER_HOST=${DOCKER_HOST}"
      return
    fi
  done
}

install_brew_deps() {
  if [[ "$SKIP_BREW" -eq 1 ]]; then
    warn "skipping brew package install"
    return
  fi
  ensure_path_homebrew
  if ! command -v brew >/dev/null 2>&1; then
    fail "Homebrew not found. Install from https://brew.sh then re-run."
  fi
  ok "brew $(brew --version | head -1)"

  local pkgs=(python@3.12 git colima docker)
  info "Ensuring brew packages: ${pkgs[*]}"
  for pkg in "${pkgs[@]}"; do
    if brew list --formula "$pkg" >/dev/null 2>&1 \
      || brew list --cask "$pkg" >/dev/null 2>&1; then
      ok "brew: $pkg already installed"
    else
      info "brew install $pkg"
      brew install "$pkg"
    fi
  done

  # Prefer python3.12 on PATH when available
  if [[ -x /opt/homebrew/opt/python@3.12/bin/python3.12 ]]; then
    export PATH="/opt/homebrew/opt/python@3.12/bin:$PATH"
  elif [[ -x /usr/local/opt/python@3.12/bin/python3.12 ]]; then
    export PATH="/usr/local/opt/python@3.12/bin:$PATH"
  fi
}

start_colima() {
  if [[ "$SKIP_COLIMA" -eq 1 ]]; then
    warn "skipping Colima start"
    return
  fi
  need_cmd colima
  if colima status 2>/dev/null | grep -qi 'Running'; then
    ok "Colima already running"
  else
    info "Starting Colima (first boot may take a few minutes)..."
    # Modest defaults for Mac mini / laptop agent sandboxes
    colima start --cpu 4 --memory 8 --disk 60 || colima start
    ok "Colima started"
  fi
  export_colima_docker_host
  need_cmd docker
  if docker info >/dev/null 2>&1; then
    ok "Docker daemon reachable via Colima"
  else
    fail "docker info failed after Colima start — check: colima status && docker context ls"
  fi
}

check_python() {
  need_cmd python3
  local pyv
  pyv=$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')
  python3 -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)' \
    || fail "Python 3.11+ required (found $pyv)"
  ok "Python $pyv"
}

install_client() {
  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
  CLIENT_SRC="$REPO_ROOT/client"
  [[ -d "$CLIENT_SRC" ]] || fail "client/ not found at $CLIENT_SRC — run from a monorepo checkout"

  mkdir -p "$INSTALL_DIR"/{bin,data,workspaces}
  info "Installing client into $INSTALL_DIR"

  # Sync client sources into install dir (editable install points here)
  mkdir -p "$INSTALL_DIR/src"
  if command -v rsync >/dev/null 2>&1; then
    rsync -a --delete \
      --exclude '.venv' --exclude '__pycache__' --exclude '.pytest_cache' --exclude '*.pyc' \
      "$CLIENT_SRC/" "$INSTALL_DIR/src/client/"
  else
    rm -rf "$INSTALL_DIR/src/client"
    mkdir -p "$INSTALL_DIR/src/client"
    cp -a "$CLIENT_SRC/." "$INSTALL_DIR/src/client/"
  fi

  if [[ ! -d "$INSTALL_DIR/.venv" ]]; then
    info "Creating virtualenv..."
    python3 -m venv "$INSTALL_DIR/.venv"
  fi
  info "pip install -e client..."
  "$INSTALL_DIR/.venv/bin/pip" install --upgrade pip -q
  "$INSTALL_DIR/.venv/bin/pip" install -e "$INSTALL_DIR/src/client" -q

  ln -sfn "$INSTALL_DIR/.venv/bin/squadx-client" "$INSTALL_DIR/bin/squadx-client"
  ok "squadx-client → $INSTALL_DIR/bin/squadx-client"
}

write_env_sh() {
  local env_sh="$INSTALL_DIR/env.sh"
  local sock_default="${HOME}/.colima/default/docker.sock"
  cat > "$env_sh" <<EOF
# Generated by install-mac-client.sh — source before running the daemon
export PATH="${INSTALL_DIR}/bin:\${PATH}"
export SQUADX_DATA_DIR="${INSTALL_DIR}/data"
export SQUADX_WORKSPACE_PATH="${INSTALL_DIR}/workspaces"
# Colima socket (override if you use Docker Desktop)
if [[ -z "\${DOCKER_HOST:-}" && -S "${sock_default}" ]]; then
  export DOCKER_HOST="unix://${sock_default}"
elif [[ -z "\${DOCKER_HOST:-}" && -S "${HOME}/.colima/docker.sock" ]]; then
  export DOCKER_HOST="unix://${HOME}/.colima/docker.sock"
fi
# Load secrets if present
if [[ -f "${INSTALL_DIR}/squadx-client.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${INSTALL_DIR}/squadx-client.env"
  set +a
fi
EOF
  ok "wrote $env_sh"
}

prompt_value() {
  # $1=var name $2=prompt $3=default $4=secret(0/1)
  local name="$1" prompt="$2" default="${3:-}" secret="${4:-0}" value=""
  if [[ -n "${!name:-}" ]]; then
    echo "${!name}"
    return
  fi
  if [[ "$NON_INTERACTIVE" -eq 1 ]]; then
    echo "$default"
    return
  fi
  if [[ -n "$default" ]]; then
    prompt="$prompt [$default]"
  fi
  if [[ "$secret" == "1" ]]; then
    read -r -s -p "$prompt: " value
    echo "" >&2
  else
    read -r -p "$prompt: " value
  fi
  if [[ -z "$value" ]]; then
    echo "$default"
  else
    echo "$value"
  fi
}

write_client_env() {
  local env_file="$INSTALL_DIR/squadx-client.env"
  if [[ -f "$env_file" && "$NON_INTERACTIVE" -eq 1 ]]; then
    ok "keeping existing $env_file"
    return
  fi
  if [[ -f "$env_file" && "$NON_INTERACTIVE" -eq 0 ]]; then
    read -r -p "Overwrite $env_file? [y/N] " ans || true
    if [[ ! "${ans:-}" =~ ^[Yy]$ ]]; then
      ok "keeping existing $env_file"
      return
    fi
  fi

  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  EXAMPLE="$SCRIPT_DIR/../client/deploy/squadx-client.env.example"
  if [[ ! -f "$EXAMPLE" ]]; then
    warn "env example missing — writing minimal file"
    EXAMPLE=""
  fi

  info "Configuring API / LLM (wizard — leave blank to skip)"
  local api_url api_token ws_url model or_key
  api_url=$(prompt_value SQUADX_API_URL "SQUADX_API_URL" "https://api.squadx.dev")
  ws_url="${SQUADX_WS_URL:-}"
  if [[ -z "$ws_url" ]]; then
    # derive wss from https
    if [[ "$api_url" == https://* ]]; then
      ws_url="wss://${api_url#https://}/ws"
    elif [[ "$api_url" == http://* ]]; then
      ws_url="ws://${api_url#http://}/ws"
    else
      ws_url="ws://127.0.0.1:8080/ws"
    fi
  fi
  api_token=$(prompt_value SQUADX_API_TOKEN "SQUADX_API_TOKEN" "change-me" 1)
  or_key=$(prompt_value OPENROUTER_API_KEY "OPENROUTER_API_KEY (or leave empty)" "" 1)
  model=$(prompt_value SQUADX_DEFAULT_MODEL "SQUADX_DEFAULT_MODEL" "openrouter/openai/gpt-4o-mini")

  {
    echo "# Generated by install-mac-client.sh ($(date -u +%Y-%m-%dT%H:%MZ))"
    echo "SQUADX_API_URL=${api_url}"
    echo "SQUADX_WS_URL=${ws_url}"
    echo "SQUADX_API_TOKEN=${api_token}"
    echo "SQUADX_SANDBOX_BACKEND=docker"
    echo "SQUADX_AGENT_IMAGE=squadx/agent:latest"
    echo "SQUADX_EGRESS_PROXY_IMAGE=squadx/egress-proxy:latest"
    echo "SQUADX_EGRESS_SIDECAR=true"
    echo "SQUADX_NETWORK_POLICY=agent-default"
    echo "SQUADX_DEFAULT_MODEL=${model}"
    if [[ -n "$or_key" ]]; then
      echo "OPENROUTER_API_KEY=${or_key}"
    fi
    echo "# OPENAI_API_KEY="
    echo "# ANTHROPIC_API_KEY="
    echo "SQUADX_DATA_DIR=${INSTALL_DIR}/data"
    echo "SQUADX_WORKSPACE_PATH=${INSTALL_DIR}/workspaces"
  } > "$env_file"
  chmod 600 "$env_file"
  ok "wrote $env_file (mode 600)"
}

pull_or_build_images() {
  export_colima_docker_host
  squadx_pull_or_build_images "$PULL_IMAGES" "$SKIP_IMAGES" "$GHCR_REPO"
}

print_shell_hint() {
  local zrc="${HOME}/.zshrc"
  local line="source ${INSTALL_DIR}/env.sh"
  echo ""
  info "Add to your shell profile (once):"
  echo "  echo 'source ${INSTALL_DIR}/env.sh' >> ${zrc}"
  if [[ -f "$zrc" ]] && grep -qF "$INSTALL_DIR/env.sh" "$zrc" 2>/dev/null; then
    ok "already referenced in $zrc"
  fi
}

main() {
  echo ""
  echo "  SquadX Mac installer (Dev LIGHT / Colima) — ADR-0009"
  echo ""
  require_macos
  install_brew_deps
  check_python
  need_cmd git
  start_colima
  install_client
  write_env_sh
  write_client_env
  pull_or_build_images
  print_shell_hint

  # shellcheck disable=SC1090
  source "$INSTALL_DIR/env.sh"

  echo ""
  info "Running doctor..."
  if "$INSTALL_DIR/bin/squadx-client" doctor; then
    ok "doctor passed"
  else
    warn "doctor reported issues — fix env/images then re-run: source $INSTALL_DIR/env.sh && squadx-client doctor"
  fi

  echo ""
  ok "Install complete"
  echo ""
  echo "Next steps:"
  echo "  1. source $INSTALL_DIR/env.sh"
  echo "  2. edit $INSTALL_DIR/squadx-client.env   # API token + LLM key"
  echo "  3. squadx-client doctor"
  echo "  4. squadx-client start -f"
  echo ""
  echo "Docs: documentos/DEV-LIGHT-MAC.md"
  echo "Note: full egress packet-proof is Linux VPS; Colima is fine for Dev LIGHT."
  echo "Homebrew formula remains placeholder — this script is the supported path."
  echo ""
}

main
