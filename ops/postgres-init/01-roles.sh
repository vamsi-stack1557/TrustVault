#!/bin/bash
set -euo pipefail

: "${APP_DB_PASSWORD:?APP_DB_PASSWORD is required}"
: "${MIGRATION_DB_PASSWORD:?MIGRATION_DB_PASSWORD is required}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=app_password="$APP_DB_PASSWORD" \
  --set=migrator_password="$MIGRATION_DB_PASSWORD" <<'EOSQL'
CREATE ROLE trustvault_migrator LOGIN PASSWORD :'migrator_password';
CREATE ROLE trustvault_app LOGIN PASSWORD :'app_password';
GRANT CONNECT ON DATABASE trustvault TO trustvault_migrator, trustvault_app;
ALTER SCHEMA public OWNER TO trustvault_migrator;
GRANT USAGE, CREATE ON SCHEMA public TO trustvault_migrator;
GRANT USAGE ON SCHEMA public TO trustvault_app;
EOSQL
