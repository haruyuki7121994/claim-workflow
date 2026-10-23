# Mock precondition APIs and workflow tests

Start PostgreSQL and apply V001–V003 as the schema owner. For an existing schema,
`./infra/configure-runtime-role.sh` repairs the runtime role and applies V002/V003.
Run the application with `SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run`.
The simulation endpoints are enabled only in the `local` and `test` profiles.

All endpoints below use `POST /api/v1/mock/<operation>/{claimId}` with
`Authorization: Bearer <token>`. They update evidence, not the claim state.
Use the returned `version` for the next operation or transition. State, tenant,
role, assigned assessor, and optimistic version checks remain enforced.
Document upload simulates completion; it does not store files.

| Operation | Required role/state | Body fields besides `expectedVersion` | Result |
| --- | --- | --- | --- |
| `upload-documents` | document_clerk / SUBMITTED | none | `documentsComplete=true` |
| `assign-assessor` | team_lead / DOCUMENTS_VERIFIED | `assessorId` | assigned assessor ID |
| `complete-assessment` | assigned assessor / UNDER_ASSESSMENT | none | `assessmentComplete=true` |
| `rejection-reason` | assigned assessor / UNDER_ASSESSMENT | `description` | rejection reason |
| `missing-information` | assigned assessor / UNDER_ASSESSMENT | `description` | missing info description |
| `receive-information` | document_clerk / PENDING_INFO | none | `newInformationReceived=true` |
| `confirm-payment` | finance / PAYMENT_INITIATED | `paymentReference` | payment reference |
| `acknowledge-rejection` | system / REJECTED | none | `memberAcknowledged=true` |
| `expire-appeal` | system / REJECTED | none | deadline set to current time |
| `create-payment-request` | finance / APPROVED | no body or version required | executes this claim's pending outbox actions |
| `process-payment` | finance / PAYMENT_INITIATED | no body or version required | executes this claim's pending outbox actions |

The two payment execution routes also accept either APPROVED or PAYMENT_INITIATED.
They execute the existing DI action handlers and persist real outbox receipts.
`paymentRequestCreated` depends on a completed CREATE_PAYMENT_REQUEST event;
`paymentConfirmed` requires both a reference and a completed TRIGGER_PAYMENT event.
`amountWithinPolicyLimit` is computed from `amount` and `policyLimit` supplied to
`POST /api/v1/claims`; it is never overridden by a flag.

```sh
curl -X POST "http://localhost:8080/api/v1/mock/upload-documents/$CLAIM_ID" \
  -H "Authorization: Bearer $CLERK_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"expectedVersion":0}'
```

Then call `POST /api/v1/claims/{claimId}/transitions` with an `Idempotency-Key`
header and `{"toState":"DOCUMENTS_VERIFIED","expectedVersion":1,"reason":"Documents checked"}`.

## Five challenge scenarios

`WorkflowEndToEndTests` exercises the HTTP controllers through MockMvc, Spring
Security, real DI guards, service transactions, JPA, and PostgreSQL. JWT identities
are supplied by Spring Security test support, so these tests do not depend on login
seed data. Each test creates its own claim; no audit records are deleted.

1. Happy path ends CLOSED with five ordered transitions.
2. Rejection path ends CLOSED after acknowledgement with four transitions.
3. Information loop proceeds through approval and payment to CLOSED with eight transitions.
4. SUBMITTED → APPROVED returns 409 INVALID_TRANSITION and leaves no successful audit/effects.
5. Finance approval returns 403 UNAUTHORIZED_TRANSITION and preserves state, version, tasks and effects.

Additional tests cover missing preconditions, amount limits, exactly three requests,
fourth-attempt rejection, stale versions, tenant isolation, assessor assignment,
idempotency, payment dependencies, and database audit mutation denial.

```sh
./mvnw -Dtest=WorkflowEndToEndTests test
./mvnw -Dtest=GuardHandlerTests,ApiExceptionHandlerTests,ActionHandlerRegistryTests,WorkflowDefinitionBindingTests test
```

Use the default PostgreSQL Maven profile. `-Poffline-check` omits the JDBC driver and
cannot run integration tests. Set `TEST_DATABASE_URL`, `TEST_DATABASE_USER`, and
`TEST_DATABASE_PASSWORD` when using another PostgreSQL instance. The runtime test
role must be `claimworkflow_app` (or equivalently restricted), never the table owner.
A failure to create the datasource/context prevents all integration cases from running;
inspect the first `Caused by` entry rather than subsequent context-threshold errors.

## Export submission evidence

Run `python3 scripts/run-workflow-scenarios.py` to run exactly the five challenge
scenarios and export the configuration, test results, and database audit trails.
See [Workflow submission evidence](examples/README.md) for output files and options.

## Mock route returns "No static resource"

`MockConditionController` is registered only for the `local` and `test` profiles.
A missing mock route can therefore fall through to Spring's static-resource handler.
Restart the backend with:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

For IntelliJ, set **Active profiles** to `local` in the Spring Boot run configuration,
or add the program argument `--spring.profiles.active=local`. Verify the startup
log reports the `local` profile. Activating a Vite mode does not activate a backend
Spring profile. Check that Vite's `API_PROXY_TARGET` points to this backend instance.

The assessment endpoint uses POST with a bearer token and JSON body
`{"expectedVersion":<current claim version>}`. The caller must be the assigned
assessor and the claim must be `UNDER_ASSESSMENT`.
