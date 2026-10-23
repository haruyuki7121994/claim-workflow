# Local database

PostgreSQL matches the persistence design in `docs/system_design.md` for claims,
audit history, workflow tasks, idempotency, and outbox events. This setup uses the
[official PostgreSQL image](https://hub.docker.com/_/postgres) directly through
Docker Compose; a custom Dockerfile is unnecessary.

## Start and connect

Install Docker with Compose v2 and start the Docker engine. Run these commands
from the repository root:

```sh
docker compose -f infra/compose.yaml config --quiet
docker compose -f infra/compose.yaml up -d --wait
docker compose -f infra/compose.yaml ps
```

Default connection settings (development only):

| Setting | Value |
| --- | --- |
| Host | `127.0.0.1` |
| Port | `5432` |
| Database | `claimworkflow` |
| Username | `claimworkflow` |
| Password | `claimworkflow_local` |
| JDBC URL | `jdbc:postgresql://127.0.0.1:5432/claimworkflow` |

Open a SQL shell using the configured database and user:

```sh
docker compose -f infra/compose.yaml exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Override `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, or `POSTGRES_PORT`
through shell environment variables. For example, if port 5432 is occupied:

```sh
POSTGRES_PORT=5433 docker compose -f infra/compose.yaml up -d --wait
```

Use the same overrides for subsequent Compose commands and update client
connection settings accordingly. Database name, user, and password initialize
only an empty data volume; changing them later does not update an existing database.

## Lifecycle and application integration

```sh
# View database logs.
docker compose -f infra/compose.yaml logs -f postgres

# Remove the container while preserving database data.
docker compose -f infra/compose.yaml down

# Delete all local database data and remove the container.
docker compose -f infra/compose.yaml down --volumes
```

The named volume preserves data across container recreation. PostgreSQL is
exposed only on the host loopback interface.

On first startup, PostgreSQL runs the scripts in `infra/init/`. They create the
JPA schema, indexes, restricted `claimworkflow_app` role, and append-only audit
protections. Existing volumes are never reinitialized automatically.

If the database or Docker volume existed before `claimworkflow_app` was added,
create or repair the runtime role and reapply its grants:

```sh
./infra/configure-runtime-role.sh
```

The script accepts the same `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`,
`POSTGRES_USER`, `POSTGRES_PASSWORD`, and `APP_DB_PASSWORD` overrides as the
Compose setup. The administrative account must be able to create roles and must
own the application tables.

Run the application with the local profile and the application role:

```sh
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

The default local application connection is
`jdbc:postgresql://127.0.0.1:5432/claimworkflow`, user `claimworkflow_app`, and
password `claimworkflow_app_local`. Override these with `DATABASE_URL`,
`DATABASE_USER`, and `DATABASE_PASSWORD`.

## Seed users

`V003__users_and_roles.sql` creates the tenant-aware `app_users` and
`user_roles` tables. It seeds one enabled `tenant-a` user for every workflow
role:

| JWT subject | Role |
| --- | --- |
| `clerk-1` | `document_clerk` |
| `lead-1` | `team_lead` |
| `assessor-1` | `assessor` |
| `assessor-2` | `assessor` |
| `finance-1` | `finance` |
| `system-1` | `system` |

These records contain profile and authorization reference data only. JWT remains
the authentication mechanism, and no passwords or tokens are stored in these
tables. For an existing database, run `./infra/configure-runtime-role.sh` to
apply the user migration after creating the runtime database role.

## Audit table permissions

`claim_transition_audit` must permit only `INSERT` and `SELECT` for the application.
Its runtime role must not own the table, be a superuser, or have direct or inherited
`UPDATE`, `DELETE`, or `TRUNCATE` privileges. Avoid cascading changes or deletes
that could alter audit history. Keep migration/owner credentials separate.

The `POSTGRES_USER` account created by this image is a bootstrap superuser, so the
administrative connection details above must not be used by the application.
`V002__audit_permissions.sql` grants the runtime role only `SELECT` and `INSERT`
on the audit table and adds triggers that reject updates, deletes, and truncation.

## PostgreSQL integration tests

Start the local database, then run:

```sh
TEST_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/claimworkflow \
TEST_DATABASE_USER=claimworkflow_app \
TEST_DATABASE_PASSWORD=claimworkflow_app_local \
./mvnw test
```

Tests use PostgreSQL directly; no in-memory database is used. Test records use
random UUIDs and remain in the local development database so the audit trail is
never deleted by test cleanup.
