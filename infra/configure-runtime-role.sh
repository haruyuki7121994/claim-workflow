#!/bin/sh
set -eu

db_host="${POSTGRES_HOST:-127.0.0.1}"
db_port="${POSTGRES_PORT:-5432}"
db_name="${POSTGRES_DB:-claimworkflow}"
db_admin="${POSTGRES_USER:-claimworkflow}"
db_admin_password="${POSTGRES_PASSWORD:-claimworkflow_local}"
app_password="${APP_DB_PASSWORD:-claimworkflow_app_local}"
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

PGPASSWORD="$db_admin_password" psql \
  --host "$db_host" --port "$db_port" --username "$db_admin" --dbname "$db_name" \
  --set=ON_ERROR_STOP=1 --set=app_password="$app_password" <<'SQL'
SELECT format(
    'CREATE ROLE claimworkflow_app LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT',
    :'app_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'claimworkflow_app') \gexec
ALTER ROLE claimworkflow_app WITH LOGIN PASSWORD :'app_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SQL

PGPASSWORD="$db_admin_password" psql \
  --host "$db_host" --port "$db_port" --username "$db_admin" --dbname "$db_name" \
  --set=ON_ERROR_STOP=1 --file "$script_dir/../src/main/resources/db/migration/V002__audit_permissions.sql"

PGPASSWORD="$db_admin_password" psql \
  --host "$db_host" --port "$db_port" --username "$db_admin" --dbname "$db_name" \
  --set=ON_ERROR_STOP=1 --file "$script_dir/../src/main/resources/db/migration/V003__users_and_roles.sql"
