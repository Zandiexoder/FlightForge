#!/bin/sh
set -eu

# Default for internal Docker network
: "${BACKEND_URL:=http://backend:8000}"

# Normalize common misconfigurations from deployment platforms:
# - backend:8000      -> http://backend:8000
# - http://backend:8000/ -> http://backend:8000
case "$BACKEND_URL" in
  http://*|https://*) ;;
  *) BACKEND_URL="http://$BACKEND_URL" ;;
esac
BACKEND_URL="${BACKEND_URL%/}"
export BACKEND_URL

envsubst '$BACKEND_URL' \
  < /etc/nginx/templates/default.conf.template \
  > /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'
