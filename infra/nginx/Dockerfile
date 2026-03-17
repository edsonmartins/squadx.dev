FROM nginx:1.27-alpine

# Install openssl for dhparam generation
RUN apk add --no-cache openssl

# Generate Diffie-Hellman parameters for DHE cipher suites
RUN mkdir -p /etc/nginx/ssl && \
    openssl dhparam -out /etc/nginx/ssl/dhparam.pem 2048

# Copy nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Create directories for logs and certbot
RUN mkdir -p /var/log/nginx /var/www/certbot

EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]
