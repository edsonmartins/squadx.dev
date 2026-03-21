#!/usr/bin/env bash
# SquadX Client - Universal Install Script
# Usage: curl -fsSL https://get.squadx.dev | bash
#
# Installs the SquadX Client on macOS, Linux, and WSL.
# Requirements: Python 3.11+, pip, git, Docker

set -euo pipefail

REPO="https://github.com/squadx-dev/squadx.dev.git"
INSTALL_DIR="${SQUADX_INSTALL_DIR:-$HOME/.squadx}"
VERSION="${SQUADX_VERSION:-latest}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── Detect OS ───────────────────────────────────────────────
detect_os() {
    case "$(uname -s)" in
        Darwin*) OS="macos" ;;
        Linux*)  OS="linux" ;;
        MINGW*|MSYS*|CYGWIN*) OS="windows" ;;
        *) error "Unsupported operating system: $(uname -s)" ;;
    esac
    info "Detected OS: $OS"
}

# ── Check prerequisites ────────────────────────────────────
check_prerequisites() {
    info "Checking prerequisites..."

    # Python 3.11+
    if command -v python3 &>/dev/null; then
        PYTHON_VERSION=$(python3 -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')
        PYTHON_MAJOR=$(echo "$PYTHON_VERSION" | cut -d. -f1)
        PYTHON_MINOR=$(echo "$PYTHON_VERSION" | cut -d. -f2)
        if [ "$PYTHON_MAJOR" -lt 3 ] || { [ "$PYTHON_MAJOR" -eq 3 ] && [ "$PYTHON_MINOR" -lt 11 ]; }; then
            error "Python 3.11+ is required (found $PYTHON_VERSION)"
        fi
        ok "Python $PYTHON_VERSION"
    else
        error "Python 3 is not installed. Install Python 3.11+ first."
    fi

    # pip
    if command -v pip3 &>/dev/null || python3 -m pip --version &>/dev/null 2>&1; then
        ok "pip available"
    else
        error "pip is not installed. Install pip first."
    fi

    # git
    if command -v git &>/dev/null; then
        ok "git $(git --version | awk '{print $3}')"
    else
        error "git is not installed."
    fi

    # Docker
    if command -v docker &>/dev/null; then
        if docker info &>/dev/null 2>&1; then
            ok "Docker $(docker --version | awk '{print $3}' | tr -d ',')"
        else
            warn "Docker is installed but the daemon is not running. Start Docker before using SquadX agents."
        fi
    else
        warn "Docker is not installed. Required for agent sandboxing."
    fi
}

# ── Install SquadX Client ──────────────────────────────────
install_client() {
    info "Installing SquadX Client to $INSTALL_DIR..."

    if [ -d "$INSTALL_DIR/client" ]; then
        info "Updating existing installation..."
        cd "$INSTALL_DIR/client"
        git pull --quiet
    else
        mkdir -p "$INSTALL_DIR"
        git clone --depth 1 "$REPO" "$INSTALL_DIR/repo" 2>/dev/null || true
        ln -sfn "$INSTALL_DIR/repo/client" "$INSTALL_DIR/client"
    fi

    cd "$INSTALL_DIR/client"

    # Install in a virtual environment
    if [ ! -d "$INSTALL_DIR/venv" ]; then
        info "Creating virtual environment..."
        python3 -m venv "$INSTALL_DIR/venv"
    fi

    source "$INSTALL_DIR/venv/bin/activate"

    info "Installing dependencies..."
    pip install --quiet --upgrade pip
    pip install --quiet -e "$INSTALL_DIR/client"

    ok "SquadX Client installed successfully"
}

# ── Create shell wrapper ───────────────────────────────────
create_wrapper() {
    WRAPPER="$INSTALL_DIR/bin/squadx-client"
    mkdir -p "$INSTALL_DIR/bin"

    cat > "$WRAPPER" << 'WRAPPER_EOF'
#!/usr/bin/env bash
SQUADX_HOME="${SQUADX_INSTALL_DIR:-$HOME/.squadx}"
source "$SQUADX_HOME/venv/bin/activate"
exec squadx-client "$@"
WRAPPER_EOF

    chmod +x "$WRAPPER"
    ok "Created wrapper at $WRAPPER"
}

# ── Update PATH ────────────────────────────────────────────
update_path() {
    SHELL_NAME=$(basename "$SHELL")
    BIN_DIR="$INSTALL_DIR/bin"

    case "$SHELL_NAME" in
        zsh)  RC_FILE="$HOME/.zshrc" ;;
        bash) RC_FILE="$HOME/.bashrc" ;;
        fish) RC_FILE="$HOME/.config/fish/config.fish" ;;
        *)    RC_FILE="$HOME/.profile" ;;
    esac

    if ! grep -q "squadx" "$RC_FILE" 2>/dev/null; then
        if [ "$SHELL_NAME" = "fish" ]; then
            echo "set -gx PATH $BIN_DIR \$PATH" >> "$RC_FILE"
        else
            echo "export PATH=\"$BIN_DIR:\$PATH\"" >> "$RC_FILE"
        fi
        info "Added $BIN_DIR to PATH in $RC_FILE"
        warn "Run 'source $RC_FILE' or open a new terminal to use squadx-client"
    fi
}

# ── Create default config ─────────────────────────────────
create_default_config() {
    ENV_FILE="$INSTALL_DIR/.env"
    if [ ! -f "$ENV_FILE" ]; then
        cat > "$ENV_FILE" << 'ENV_EOF'
# SquadX Client Configuration
# See https://docs.squadx.dev/configuration for all options

# Required: API connection
SQUADX_API_URL=http://localhost:8080
SQUADX_WS_URL=ws://localhost:8080/ws
# SQUADX_API_TOKEN=

# Required: At least one LLM provider API key
# OPENAI_API_KEY=sk-...
# ANTHROPIC_API_KEY=sk-ant-...

# Optional: Default model (default: gpt-4o)
# SQUADX_DEFAULT_MODEL=gpt-4o

# Optional: Supabase for Live Streaming
# SUPABASE_URL=
# SUPABASE_ANON_KEY=
ENV_EOF
        info "Created default config at $ENV_FILE"
        warn "Edit $ENV_FILE to add your API keys before using SquadX"
    fi
}

# ── Main ───────────────────────────────────────────────────
main() {
    echo ""
    echo -e "${BLUE}╔══════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC}    SquadX.dev Client Installer       ${BLUE}║${NC}"
    echo -e "${BLUE}║${NC}    AI Development Squad Platform     ${BLUE}║${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════╝${NC}"
    echo ""

    detect_os
    check_prerequisites
    install_client
    create_wrapper
    update_path
    create_default_config

    echo ""
    echo -e "${GREEN}╔══════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║${NC}    Installation complete!            ${GREEN}║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════╝${NC}"
    echo ""
    echo "  Next steps:"
    echo "  1. Edit ~/.squadx/.env to add your API keys"
    echo "  2. Run: squadx-client --help"
    echo "  3. Docs: https://docs.squadx.dev"
    echo ""
}

main "$@"
