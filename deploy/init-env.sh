#!/bin/sh
set -eu
cd "$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
if [ -e .env ]; then
  printf '%s\n' '.env already exists; preserved without replacing credentials.'
  exit 0
fi
command -v openssl >/dev/null 2>&1 || { printf '%s\n' 'openssl is required.' >&2; exit 1; }
umask 077
 
set -C
{
  printf 'NEIGHBORLIFE_WEB_PORT=8080\n'
  printf 'NEIGHBORLIFE_MYSQL_ROOT_PASSWORD=%s\n' "$(openssl rand -hex 24)"
  printf 'NEIGHBORLIFE_MYSQL_PASSWORD=%s\n' "$(openssl rand -hex 24)"
  printf 'NEIGHBORLIFE_REDIS_PASSWORD=%s\n' "$(openssl rand -hex 24)"
  printf 'NEIGHBORLIFE_MONITOR_TOKEN=%s\n' "$(openssl rand -hex 24)"
  printf 'NEIGHBORLIFE_ADMIN_IDS=\nNEIGHBORLIFE_PAYMENT_MOCK_ENABLED=false\n'
} > .env
printf '%s\n' 'Created private .env. Keep it with the persistent database volumes.'
