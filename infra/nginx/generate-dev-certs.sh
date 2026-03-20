#!/bin/bash
set -e
CERT_DIR="$(dirname "$0")/certs"
mkdir -p "$CERT_DIR"
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout "$CERT_DIR/key.pem" \
  -out "$CERT_DIR/cert.pem" \
  -subj "/CN=localhost/O=SquadX Dev"
echo "Self-signed certificates generated in $CERT_DIR/"
