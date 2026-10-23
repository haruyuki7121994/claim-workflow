# Claims workspace — React + Vite

## Run locally

Start PostgreSQL with the schema migrations and seeded users, then start Spring Boot:

```sh
# From the repository root
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

In another terminal:

```sh
cd frontend
npm ci
npm run dev
```

Open **http://localhost:5173**. Use Node 20.19+ or 22.12+ and npm. Vite proxies
`/api` to Spring Boot at `http://127.0.0.1:8080`, so no CORS changes are needed.
To change the backend address, copy `.env.example` to `.env.local`, update
`API_PROXY_TARGET`, and restart Vite.

## Use the workspace

1. Connect using tenant `tenant-a` and a seeded user ID. `clerk-1` creates claims
   and uploads documents; `lead-1` assigns assessors; `assessor-1` reviews claims;
   `finance-1` processes payment; `system-1` closes rejected claims.
2. Select a claim or open its full UUID. The overview shows its current state,
   amount, policy limit, assigned assessor, and information request count.
3. View all configured outgoing transitions, including required roles, guard
   names, readiness, and blocking reasons. Only authorized, ready transitions
   can be submitted. Enter a reason to commit the transition.
4. Use **Prepare claim conditions** to call the local simulation endpoints for
   document upload, assessor assignment, assessment completion and other evidence.
   These routes require the backend `local` profile. See `docs/mock-preconditions.md`.
5. Switch accounts to perform the next role's action. The selected claim is kept
   when switching within the same tenant.
6. Open **Audit trail** to inspect ordered transitions, user/role, timestamp and
   reason. **Export JSON** downloads the complete history across all pages.

The UI supplies the current claim version automatically and uses an idempotency
key for each transition, retaining it for a retry of the same command. Concurrent
modification errors reload the latest claim for review. Tokens stay in the
browser tab's session storage and are cleared on sign-out or a 401 response.

There are no synthetic claim records or successful API responses in the UI.
Claims and audit history require the running API and PostgreSQL.

## Build and verify

```sh
npm test
npm run build
npm run preview
```

The production files are in `frontend/dist/`; preview runs at
**http://localhost:4173** and uses the same API proxy. For deployment, serve the
built files and reverse-proxy `/api` to Spring Boot. The existing Spring Boot
static console remains at port 8080; the React app is a separate frontend.

The transitions endpoint supports `?includeUnauthorized=true` to show all paths
and their role requirements. Its default response remains filtered by role;
server-side execution authorization is always enforced.
