#!/bin/sh
set -eu

server_name="${SERVER_NAME:-_}"
enable_tls="$(printf '%s' "${ENABLE_TLS:-false}" | tr '[:upper:]' '[:lower:]')"
enable_local_tls="$(printf '%s' "${ENABLE_LOCAL_TLS:-false}" | tr '[:upper:]' '[:lower:]')"
acme_root="${ACME_CHALLENGE_ROOT:-/var/www/certbot}"
tls_cert_path="${TLS_CERT_PATH:-}"
tls_key_path="${TLS_KEY_PATH:-}"
local_tls_cert_path="${LOCAL_TLS_CERT_PATH:-/etc/nginx/local-certs/localhost.crt}"
local_tls_key_path="${LOCAL_TLS_KEY_PATH:-/etc/nginx/local-certs/localhost.key}"
hsts_max_age="${HSTS_MAX_AGE:-31536000}"
generated_local_tls_certificate="false"

primary_server_name="${server_name%% *}"
if [ -z "$tls_cert_path" ] && [ "$primary_server_name" != "_" ]; then
  tls_cert_path="/etc/letsencrypt/live/${primary_server_name}/fullchain.pem"
fi

if [ -z "$tls_key_path" ] && [ "$primary_server_name" != "_" ]; then
  tls_key_path="/etc/letsencrypt/live/${primary_server_name}/privkey.pem"
fi

mkdir -p /etc/nginx/conf.d "${acme_root}/.well-known/acme-challenge"

if [ "$enable_local_tls" = "true" ]; then
  tls_cert_path="$local_tls_cert_path"
  tls_key_path="$local_tls_key_path"

  mkdir -p "$(dirname "$tls_cert_path")" "$(dirname "$tls_key_path")"

  if [ ! -f "$tls_cert_path" ] || [ ! -f "$tls_key_path" ]; then
    openssl req -x509 -nodes -sha256 -days 825 \
      -newkey rsa:2048 \
      -keyout "$tls_key_path" \
      -out "$tls_cert_path" \
      -subj "/CN=localhost" \
      -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
    generated_local_tls_certificate="true"
  fi
fi

write_common_locations() {
  cat <<'EOF'
  location = /healthz {
    access_log off;
    add_header Content-Type text/plain;
    return 200 'ok';
  }

  location = /api {
    return 301 /api/;
  }

  location /api/ {
    proxy_pass http://gateway-service:8080/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Port $server_port;
  }

  location / {
    try_files $uri $uri/ /index.html;
  }
EOF
}

if [ "$enable_tls" = "true" ] && [ -f "$tls_cert_path" ] && [ -f "$tls_key_path" ]; then
  cat > /etc/nginx/conf.d/default.conf <<EOF
server {
  listen 80;
  listen [::]:80;
  server_name ${server_name};

  client_max_body_size 4m;

  location ^~ /.well-known/acme-challenge/ {
    root ${acme_root};
    try_files \$uri =404;
  }

  location = /healthz {
    access_log off;
    add_header Content-Type text/plain;
    return 200 'ok';
  }

  location / {
    return 301 https://\$host\$request_uri;
  }
}

server {
  listen 443 ssl;
  listen [::]:443 ssl;
  http2 on;
  server_name ${server_name};

  client_max_body_size 4m;

  root /usr/share/nginx/html;
  index index.html;

  ssl_certificate ${tls_cert_path};
  ssl_certificate_key ${tls_key_path};
  ssl_protocols TLSv1.2 TLSv1.3;
  ssl_session_cache shared:SSL:10m;
  ssl_session_timeout 1d;
  add_header Strict-Transport-Security "max-age=${hsts_max_age}; includeSubDomains" always;

  location ^~ /.well-known/acme-challenge/ {
    root ${acme_root};
    try_files \$uri =404;
  }

$(write_common_locations)
}
EOF
  if [ "$generated_local_tls_certificate" = "true" ]; then
    echo "Generated local self-signed certificate for localhost"
  fi
  echo "Generated HTTPS Nginx config for ${server_name}"
else
  cat > /etc/nginx/conf.d/default.conf <<EOF
server {
  listen 80;
  listen [::]:80;
  server_name ${server_name};

  client_max_body_size 4m;

  root /usr/share/nginx/html;
  index index.html;

  location ^~ /.well-known/acme-challenge/ {
    root ${acme_root};
    try_files \$uri =404;
  }

$(write_common_locations)
}
EOF
  if [ "$enable_tls" = "true" ]; then
    echo "TLS requested but certificate files are not available yet. Generated HTTP bootstrap config for ACME validation."
  else
    echo "Generated HTTP Nginx config"
  fi
fi
