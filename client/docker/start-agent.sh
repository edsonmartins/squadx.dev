#!/bin/bash
# SquadX Agent Startup Script
# Initializes the agent environment and waits for commands

set -euo pipefail

LIVE_VIEW=${1:-}

echo "=== SquadX Agent Starting ==="
echo "User: $(whoami)"
echo "Working Directory: $(pwd)"
echo "Python: $(python --version)"
echo "Git: $(git --version)"

# Verify workspace is writable
if [ ! -w /workspace ]; then
    echo "ERROR: /workspace is not writable"
    exit 1
fi

# Initialize live view if requested
if [ "$LIVE_VIEW" = "--live-view" ]; then
    echo "Starting Live View mode..."

    # Start Xvfb (virtual display)
    Xvfb :99 -screen 0 ${RESOLUTION:-1280x720}x24 &
    XVFB_PID=$!
    sleep 1

    # Start window manager
    fluxbox &
    FLUXBOX_PID=$!
    sleep 1

    # Start VNC server
    x11vnc -display :99 -forever -shared -rfbport 5900 -nopw &
    VNC_PID=$!

    echo "Live View started on VNC port 5900"

    # Cleanup on exit
    trap "kill $XVFB_PID $FLUXBOX_PID $VNC_PID 2>/dev/null || true" EXIT
fi

# Signal ready
echo "=== Agent Ready ==="

# Keep container running and wait for signals
# In production, this will be replaced by the tool execution loop
exec tail -f /dev/null
