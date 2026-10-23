#!/bin/sh
set -eu
# psql quoting prevents environment values from being interpreted as SQL.
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=ON_ERROR_STOP=1 --set=app_password="$APP_DB_PASSWORD" <<'SQL'
SELECT format(
    'CREATE ROLE claimworkflow_app LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT',
    :'app_password'
)
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'claimworkflow_app') \gexec
ALTER ROLE claimworkflow_app WITH LOGIN PASSWORD :'app_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
BEGIN;
\i /migrations/V001__workflow.sql
\i /migrations/V002__audit_permissions.sql
\i /migrations/V003__users_and_roles.sql
COMMIT;
SQL
