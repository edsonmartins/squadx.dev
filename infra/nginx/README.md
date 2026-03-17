# SquadX.dev Nginx TLS Configuration

Production-grade nginx reverse proxy with TLS 1.3 termination for SquadX.dev.

## Architecture

```
Client -> nginx (443/TLS) -> backend (8080) / frontend (3000)
                |
                +-> HTTP/2, TLS 1.3, HSTS, security headers
```

## Local Development (Self-Signed Certificates)

Generate self-signed certificates for local development:

```bash
mkdir -p certs
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout certs/key.pem \
  -out certs/cert.pem \
  -subj "/C=US/ST=State/L=City/O=SquadX/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,DNS:squadx.dev,DNS:www.squadx.dev,IP:127.0.0.1"
```

## Docker Compose Usage

Start all services with TLS-enabled nginx:

```bash
docker compose -f docker-compose.yml -f infra/nginx/docker-compose.tls.yml up -d
```

Start with monitoring stack:

```bash
docker compose -f docker-compose.yml -f infra/nginx/docker-compose.tls.yml --profile monitoring up -d
```

Build nginx image separately:

```bash
docker compose -f docker-compose.yml -f infra/nginx/docker-compose.tls.yml build nginx
```

## Production with Let's Encrypt

### Initial Certificate Issuance

1. Ensure DNS A records point to your server for `squadx.dev` and `www.squadx.dev`.

2. Start nginx without TLS first (comment out the 443 server block temporarily, or use the HTTP-only config).

3. Run certbot:

```bash
certbot certonly \
  --webroot \
  --webroot-path /var/www/certbot \
  --domain squadx.dev \
  --domain www.squadx.dev \
  --email admin@squadx.dev \
  --agree-tos \
  --non-interactive
```

4. Copy certificates:

```bash
cp /etc/letsencrypt/live/squadx.dev/fullchain.pem certs/cert.pem
cp /etc/letsencrypt/live/squadx.dev/privkey.pem certs/key.pem
```

5. Start the full stack with TLS.

### Automatic Renewal

Set up a cron job for automatic certificate renewal:

```bash
# Run twice daily (recommended by Let's Encrypt)
0 2,14 * * * /path/to/infra/nginx/ssl-renew.sh >> /var/log/ssl-renew.log 2>&1
```

## Kubernetes with cert-manager

### Install cert-manager

```bash
helm repo add jetstack https://charts.jetstack.io
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --set crds.enabled=true
```

### Create ClusterIssuer

```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@squadx.dev
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
      - http01:
          ingress:
            class: nginx
```

### Deploy with Helm

```bash
helm upgrade --install squadx infra/helm/squadx \
  --namespace squadx \
  --create-namespace \
  -f infra/helm/squadx/values.yaml
```

cert-manager will automatically provision and renew TLS certificates via the ingress annotations.

## Verification

### Test TLS 1.3 connection

```bash
curl -vI https://localhost --insecure 2>&1 | grep -E 'TLS|SSL|HTTP/'
```

Expected output should show `TLSv1.3` and `HTTP/2`.

### Verify security headers

```bash
curl -sI https://localhost --insecure | grep -iE 'strict-transport|x-frame|x-content|x-xss|referrer'
```

Expected headers:

```
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Referrer-Policy: strict-origin-when-cross-origin
```

### Test HTTP to HTTPS redirect

```bash
curl -vI http://localhost 2>&1 | grep -E 'Location|301'
```

### Full TLS audit with nmap

```bash
nmap --script ssl-enum-ciphers -p 443 localhost
```

### Online testing (production)

- [SSL Labs](https://www.ssllabs.com/ssltest/analyze.html?d=squadx.dev) - Aim for A+ rating
- [Security Headers](https://securityheaders.com/?q=squadx.dev) - Aim for A+ rating

## File Structure

```
infra/nginx/
  nginx.conf              - Main nginx configuration (TLS 1.3, HTTP/2, security headers)
  Dockerfile              - Nginx container with dhparam generation
  docker-compose.tls.yml  - Docker Compose overlay for TLS-enabled proxy
  ssl-renew.sh            - Let's Encrypt certificate renewal script
  README.md               - This file
```

## Configuration Details

| Setting | Value |
|---------|-------|
| TLS Protocols | TLSv1.2, TLSv1.3 |
| Cipher Suite | TLS_AES_256_GCM_SHA384, TLS_CHACHA20_POLY1305_SHA256, TLS_AES_128_GCM_SHA256, ECDHE-ECDSA-AES256-GCM-SHA384, ECDHE-RSA-AES256-GCM-SHA384 |
| HSTS | max-age=31536000; includeSubDomains; preload |
| OCSP Stapling | Enabled |
| Session Tickets | Disabled (forward secrecy) |
| HTTP/2 | Enabled |
| Client Max Body | 50MB |
| Proxy Timeouts | Connect: 60s, Read: 300s, Send: 300s |
| Gzip | Enabled for text/html, application/json, text/css, application/javascript |
