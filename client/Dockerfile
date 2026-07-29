# SquadX Client Dockerfile
FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    && rm -rf /var/lib/apt/lists/*

# hatchling reads `readme = "README.md"` from pyproject.toml at metadata
# generation — without it, `pip install` fails with OSError (see CI Docker job).
# Source must be present before a non-editable install.
COPY pyproject.toml README.md ./
COPY squadx_client/ squadx_client/

# Production image: regular install (not editable). Editable requires a
# writable tree and was never needed in the published image.
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir .

# Create non-root user
RUN useradd -m -s /bin/bash squadx \
    && chown -R squadx:squadx /app

USER squadx

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV SQUADX_LOG_LEVEL=INFO

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD python -c "import squadx_client" || exit 1

# Run the daemon
ENTRYPOINT ["squadx-client"]
CMD ["start", "--foreground"]
