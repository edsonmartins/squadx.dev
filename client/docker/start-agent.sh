#!/bin/bash
# SquadX Agent Startup Script
# Initializes the agent environment and waits for commands

set -euo pipefail

LIVE_VIEW=${1:-}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[OK]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo "=============================================="
echo "         SquadX Agent Sandbox"
echo "=============================================="
echo ""

log_info "User: $(whoami)"
log_info "Working Directory: $(pwd)"
log_info "Hostname: $(hostname)"
echo ""

# Print available tools
log_info "Available Development Tools:"
echo "  - Python: $(python3 --version 2>&1 | head -1)"
echo "  - Node.js: $(node --version 2>&1)"
echo "  - npm: $(npm --version 2>&1)"
echo "  - pnpm: $(pnpm --version 2>&1)"
echo "  - Java: $(java --version 2>&1 | head -1)"
echo "  - Maven: $(mvn --version 2>&1 | head -1)"
echo "  - Git: $(git --version 2>&1)"
echo ""

# Verify workspace is writable
if [ ! -w /workspace ]; then
    log_error "/workspace is not writable"
    exit 1
fi
log_success "Workspace is writable"

# Initialize live view if requested
if [ "$LIVE_VIEW" = "--live-view" ]; then
    echo ""
    log_info "Starting Live View mode..."

    RESOLUTION=${RESOLUTION:-1280x720x24}
    VNC_PORT=${VNC_PORT:-5900}
    NOVNC_PORT=${NOVNC_PORT:-6080}

    # Create necessary directories
    mkdir -p /tmp/.X11-unix

    # Start Xvfb (virtual display)
    log_info "Starting Xvfb on display :99 (${RESOLUTION})"
    Xvfb :99 -screen 0 ${RESOLUTION} -ac +extension GLX +render -noreset &
    XVFB_PID=$!
    sleep 2

    # Verify Xvfb started
    if ! kill -0 $XVFB_PID 2>/dev/null; then
        log_error "Failed to start Xvfb"
        exit 1
    fi
    log_success "Xvfb started (PID: $XVFB_PID)"

    # Start window manager
    log_info "Starting Fluxbox window manager"
    DISPLAY=:99 fluxbox &
    FLUXBOX_PID=$!
    sleep 1
    log_success "Fluxbox started (PID: $FLUXBOX_PID)"

    # Start VNC server
    log_info "Starting x11vnc on port $VNC_PORT"
    x11vnc -display :99 -forever -shared -rfbport $VNC_PORT -nopw -quiet &
    VNC_PID=$!
    sleep 1

    if ! kill -0 $VNC_PID 2>/dev/null; then
        log_error "Failed to start VNC server"
        exit 1
    fi
    log_success "VNC server started (PID: $VNC_PID)"

    # Start noVNC (web-based VNC client)
    log_info "Starting noVNC on port $NOVNC_PORT"
    websockify --web=/usr/share/novnc $NOVNC_PORT localhost:$VNC_PORT &
    NOVNC_PID=$!
    sleep 1

    if ! kill -0 $NOVNC_PID 2>/dev/null; then
        log_warn "noVNC failed to start (web access unavailable)"
    else
        log_success "noVNC started (PID: $NOVNC_PID)"
    fi

    echo ""
    echo "=============================================="
    echo "  Live View Ready!"
    echo "=============================================="
    echo ""
    echo "  VNC:   vnc://localhost:${VNC_PORT}"
    echo "  Web:   http://localhost:${NOVNC_PORT}/vnc.html"
    echo ""
    echo "=============================================="

    # Open terminal in the X session
    DISPLAY=:99 xfce4-terminal --geometry=120x35+50+50 --title="SquadX Agent" &

    # Cleanup function
    cleanup() {
        log_info "Shutting down Live View..."
        kill $NOVNC_PID 2>/dev/null || true
        kill $VNC_PID 2>/dev/null || true
        kill $FLUXBOX_PID 2>/dev/null || true
        kill $XVFB_PID 2>/dev/null || true
        log_success "Cleanup complete"
    }
    trap cleanup EXIT INT TERM
fi

# Signal ready
echo ""
log_success "=== SquadX Agent Ready ==="
echo ""

# Keep container running and wait for signals
# In production, this will be replaced by the tool execution loop
exec tail -f /dev/null
