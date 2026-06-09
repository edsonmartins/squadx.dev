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
# Build:
#   docker build -f docker/agent.Dockerfile -t squadx/agent:latest .
#   docker build -f docker/agent.Dockerfile --target live-view -t squadx/agent:live .
#
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
#
# Run with Live View:
#   docker run --rm \
#     -p 5900:5900 -p 6080:6080 \
#     -v /workspace:/workspace:rw \
#     squadx/agent:live

FROM python:3.11-slim-bookworm AS base

# Install system dependencies and Node.js LTS
RUN apt-get update && apt-get install -y --no-install-recommends \
    # Git for version control operations
    git \
    # Build essentials for native packages
    gcc \
    g++ \
    make \
    # Common utilities
    curl \
    wget \
    jq \
    unzip \
    # File utilities
    tree \
    ripgrep \
    fd-find \
    # Process utilities
    procps \
    htop \
    # Network tools (for package downloads during build)
    ca-certificates \
    gnupg \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js 20 LTS from NodeSource
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# Install pnpm and yarn globally
RUN npm install -g pnpm@latest yarn@latest typescript ts-node \
    && npm cache clean --force

# Install external coding-agent CLIs for the runtime adapter (BYO agent).
# When an agent's runtime_kind=EXTERNAL_CLI, the daemon shells out to one of
# these binaries inside the sandbox instead of the native LangGraph loop.
# Provider API keys are injected at runtime via the sandbox environment.
# NOTE: the daemon streams the CLI's stdout back as live execution logs. Showing
# the CLI in a visible VNC terminal (live-view) is a follow-up — it needs the
# exec to run inside an xterm on DISPLAY=:99 while preserving stdout capture.
RUN npm install -g \
        @anthropic-ai/claude-code \
        @openai/codex \
        @google/gemini-cli \
    && npm cache clean --force

# Install Java 21 (Temurin/Adoptium)
RUN curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb bookworm main" > /etc/apt/sources.list.d/adoptium.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends temurin-21-jdk \
    && rm -rf /var/lib/apt/lists/*

# Install Maven from Apache archive
RUN curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz | tar -xz -C /opt \
    && ln -s /opt/apache-maven-3.9.6/bin/mvn /usr/local/bin/mvn

# Install Gradle
RUN curl -fsSL https://services.gradle.org/distributions/gradle-8.5-bin.zip -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && ln -s /opt/gradle-8.5/bin/gradle /usr/local/bin/gradle \
    && rm /tmp/gradle.zip

# Clean up
RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Create non-root user for security
RUN groupadd -g 1000 agent && \
    useradd -u 1000 -g agent -m -s /bin/bash agent

# Set up workspace directory
RUN mkdir -p /workspace /app /home/agent/.cache && \
    chown -R agent:agent /workspace /app /home/agent

# Install Python dependencies in a virtual environment
COPY --chown=agent:agent docker/requirements-agent.txt /app/
RUN pip install --no-cache-dir -r /app/requirements-agent.txt

# Copy agent startup script
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
# Live View Image (with X11/VNC/noVNC support)
# =============================================================================
FROM base AS live-view

USER root

# Install X11, VNC, and noVNC for web-based live view
RUN apt-get update && apt-get install -y --no-install-recommends \
    # X11 virtual framebuffer
    xvfb \
    # VNC server
    x11vnc \
    # Window manager (lightweight)
    fluxbox \
    openbox \
    # Terminal emulators
    xterm \
    xfce4-terminal \
    # Font support
    fonts-dejavu \
    fonts-liberation \
    fonts-noto-mono \
    # noVNC for web access
    novnc \
    websockify \
    # Additional X utilities
    x11-utils \
    x11-xserver-utils \
    dbus-x11 \
    # File manager
    pcmanfm \
    # Text editors
    nano \
    vim \
    # Clipboard support
    xclip \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean

# Create noVNC symlink for easier access
RUN ln -s /usr/share/novnc/vnc.html /usr/share/novnc/index.html

# Set up fluxbox configuration
RUN mkdir -p /home/agent/.fluxbox
COPY --chown=agent:agent docker/fluxbox/ /home/agent/.fluxbox/

USER agent

# X11 environment
ENV DISPLAY=:99 \
    RESOLUTION=1280x720x24 \
    VNC_PORT=5900 \
    NOVNC_PORT=6080

# Expose ports
EXPOSE 5900 6080

# Override entrypoint for live view mode
CMD ["/app/start-agent.sh", "--live-view"]
