# Repository Guidelines

## Project Structure & Module Organization

This is a Java 21, Spring Boot 4.1.1 Maven project for a claims workflow orchestrator.

- `api/`: REST controllers and error responses.
- `config/`: JWT security, YAML binding, and outbox scheduling.
- `domain/`: authenticated actor and other non-persistent domain concepts.
- `dto/`: validated API request records.
- `entity/`: JPA entities, composite IDs, and attribute converters.
- `repository/`: Spring Data interfaces, JPA query adapters, and append-only audit access.
- `service/`: workflow rules, guards, orchestration, and outbox processing.
- `src/main/resources/workflow.yaml`: states, transitions, guards, roles, tasks, and actions.
- `src/main/resources/db/migration/`: PostgreSQL schema and runtime-role grants.
- `src/main/resources/static/`: browser workflow console.
- `src/test/java/com/papaya/claimworkflow/`: tests, mirroring production packages.
- `docs/AI_Challenge_14.md`: functional requirements and acceptance criteria.
- `docs/system_design.md`: architecture and lifecycle flows.
- `pom.xml` and `.mvn/wrapper/`: dependencies and Maven tooling. Generated output belongs in ignored `target/`.

## Build, Test, and Development Commands

Use JDK 21 and the checked-in Maven wrapper. Initial execution requires network access to download Maven and dependencies.

- `./mvnw spring-boot:run`: start the application locally.
- `./mvnw test`: run the test suite.
- `docker compose -f infra/compose.yaml up -d --wait`: start PostgreSQL.
- `./mvnw clean verify`: clean, compile, test, and package the application.

On Windows, use `mvnw.cmd` instead of `./mvnw`.

## Coding Style & Naming Conventions

Follow existing Java formatting: four-space indentation and opening braces on the declaration line. Use lowercase package names, `PascalCase` classes, `camelCase` members, and `UPPER_SNAKE_CASE` constants. Use Lombok for constructors and accessors, but avoid `@Data` on JPA entities. No formatter or lint plugin is configured.

## Testing Guidelines

Use JUnit Jupiter and Spring Boot test support. Name test classes `*Tests` and use descriptive test methods. Tests run against PostgreSQL so locking, grants, and audit immutability match production behavior; do not introduce an in-memory database.

The challenge requires at least 15 tests covering valid and invalid transitions, preconditions, roles, and cycle limits, including all five documented scenarios. Verify the fourth information-request attempt is rejected. No automated coverage percentage is configured.

## Commit & Pull Request Guidelines

This directory has no Git metadata, so historical commit conventions cannot be verified. Use concise, imperative subjects, such as `Add transition precondition validation`.

PRs should describe behavior changes, link relevant issues or challenge requirements, and report validation commands and results. Include workflow configuration diffs and scenario/audit output when relevant; add screenshots for UI changes. Keep credentials and generated build files out of commits.
