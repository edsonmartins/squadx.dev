#!/usr/bin/env bash
# =============================================================================
# SquadX.dev — Let's Encrypt SSL Certificate Renewal Script
# =============================================================================
# This script renews SSL certificates using certbot with the webroot plugin
# and reloads nginx to pick up the new certificates.
#
# Crontab schedule (run twice daily, as recommended by Let's Encrypt):
#   0 2,14 * * * /path/to/infra/nginx/ssl-renew.sh >> /var/log/ssl-renew.log 2>&1
#
# Prerequisites:
#   - certbot installed on the host
#   - Webroot directory accessible at /var/www/certbot
#   - nginx container named "squadx-nginx" running
# =============================================================================

set -euo pipefail

DOMAIN="squadx.dev"
WEBROOT="/var/www/certbot"
CERT_DIR="./certs"
NGINX_CONTAINER="squadx-nginx"
EMAIL="admin@squadx.dev"
LOG_PREFIX="[ssl-renew]"

log() {
    echo "${LOG_PREFIX} $(date '+%Y-%m-%d %H:%M:%S') $1"
}

log "Starting certificate renewal for ${DOMAIN}..."

# Request/renew certificate using webroot authentication
certbot certonly \
    --webroot \
    --webroot-path "${WEBROOT}" \
    --domain "${DOMAIN}" \
    --domain "www.${DOMAIN}" \
    --email "${EMAIL}" \
    --agree-tos \
    --non-interactive \
    --keep-until-expiring \
    --quiet

# Check if certbot succeeded
if [ $? -ne 0 ]; then
    log "ERROR: certbot renewal failed"
    exit 1
fi

log "Certificate renewal succeeded. Copying to nginx ssl directory..."

# Copy renewed certificates to the nginx-accessible directory
cp "/etc/letsencrypt/live/${DOMAIN}/fullchain.pem" "${CERT_DIR}/cert.pem"
cp "/etc/letsencrypt/live/${DOMAIN}/privkey.pem" "${CERT_DIR}/key.pem"

# Set restrictive permissions on the private key
chmod 600 "${CERT_DIR}/key.pem"
chmod 644 "${CERT_DIR}/cert.pem"

log "Reloading nginx configuration..."

# Gracefully reload nginx (zero-downtime)
docker exec "${NGINX_CONTAINER}" nginx -s reload

if [ $? -eq 0 ]; then
    log "Nginx reloaded successfully. Certificate renewal complete."
else
    log "ERROR: Failed to reload nginx. Manual intervention required."
    exit 1
fi
