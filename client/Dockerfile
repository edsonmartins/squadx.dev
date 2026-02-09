# SquadX Client Dockerfile
FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    git \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY pyproject.toml .
RUN pip install --no-cache-dir -e .

# Copy source code
COPY squadx_client/ squadx_client/

# Create non-root user
RUN useradd -m -s /bin/bash squadx
RUN chown -R squadx:squadx /app

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
