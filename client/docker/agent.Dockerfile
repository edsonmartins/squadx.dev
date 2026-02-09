# SquadX Agent Sandbox - Hardened Docker Image
# Phase 1: Docker Hardened (as per DECISAO-ARQUITETURAL-SANDBOXING.md)
#
# Security Features:
# - Non-root user execution
# - Minimal base image
# - Read-only filesystem (at runtime)
# - No network access (at runtime via --network=none)
# - Seccomp profile restricted syscalls
# - Dropped capabilities
#
# Build: docker build -f docker/agent.Dockerfile -t squadx/agent:latest .
# Run (hardened):
#   docker run --rm --read-only \
#     --cap-drop=ALL \
#     --security-opt no-new-privileges:true \
#     --security-opt seccomp=/path/to/docker/seccomp/agent.json \
#     --user 1000:1000 \
#     --memory=2g --cpus=2.0 --pids-limit=256 \
#     --network=none \
#     --tmpfs /tmp:size=100M,noexec,nosuid \
#     -v /workspace:/workspace:rw \
#     squadx/agent:latest

FROM python:3.11-slim-bookworm AS base

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    # Git for version control operations
    git \
    # Build essentials for some Python packages
    gcc \
    g++ \
    # Node.js for frontend projects
    nodejs \
    npm \
    # Common utilities
    curl \
    wget \
    jq \
    # File utilities
    tree \
    ripgrep \
    # For live view (optional, Phase 2)
    # xvfb \
    # x11vnc \
    # fluxbox \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean

# Create non-root user for security
RUN groupadd -g 1000 agent && \
    useradd -u 1000 -g agent -m -s /bin/bash agent

# Set up workspace directory
RUN mkdir -p /workspace /app /home/agent/.cache && \
    chown -R agent:agent /workspace /app /home/agent

# Install Python dependencies in a virtual environment
COPY --chown=agent:agent docker/requirements-agent.txt /app/
RUN pip install --no-cache-dir -r /app/requirements-agent.txt

# Copy agent runtime code
COPY --chown=agent:agent squadx_client/tools /app/tools/
COPY --chown=agent:agent docker/start-agent.sh /app/

# Make startup script executable
RUN chmod +x /app/start-agent.sh

# Set working directory
WORKDIR /workspace

# Switch to non-root user
USER agent

# Environment variables
ENV HOME=/home/agent \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PATH="/home/agent/.local/bin:${PATH}"

# Git configuration (safe directory for mounted volumes)
RUN git config --global --add safe.directory /workspace && \
    git config --global user.email "agent@squadx.dev" && \
    git config --global user.name "SquadX Agent"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD python -c "print('healthy')" || exit 1

# Default command
CMD ["/app/start-agent.sh"]


# =============================================================================
# Live View Image (Phase 2 - with X11/VNC support)
# =============================================================================
FROM base AS live-view

USER root

# Install X11 and VNC for live view
RUN apt-get update && apt-get install -y --no-install-recommends \
    xvfb \
    x11vnc \
    fluxbox \
    xterm \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean

USER agent

# X11 environment
ENV DISPLAY=:99 \
    RESOLUTION=1280x720

# Override entrypoint for live view mode
CMD ["/app/start-agent.sh", "--live-view"]
