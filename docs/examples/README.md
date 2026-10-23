# Workflow submission evidence

Generate a new evidence bundle for the five scenarios in `docs/AI_Challenge_14.md`:

```sh
python3 scripts/run-workflow-scenarios.py
```

Start PostgreSQL and apply the schema migrations first. The script uses the
repository Maven wrapper and the `test` profile with PostgreSQL. Override the
connection using `TEST_DATABASE_URL`, `TEST_DATABASE_USER`, and
`TEST_DATABASE_PASSWORD` if needed. No H2 or other replacement database is used.

Each run creates a new timestamped directory under `docs/examples/` containing:

- `workflow.yaml`: the exact state machine configuration used by the tests.
- `RESULTS.md`: a readable result table for the five required scenarios.
- `scenario-results.json`: test status, claim ID, duration, expected and actual audit counts.
- `audit-trails.json`: audit rows exported from PostgreSQL, grouped by scenario.
- `scenario-evidence/*.json`: HTTP requests/responses, persisted audit history, workflow progress and effects.
- `surefire/`: the JUnit XML and text reports from this run.
- `maven.log`: the complete Maven output.

A successful bundle has `complete: true` in `scenario-results.json`. The exporter
returns a nonzero exit code if a scenario fails, an audit export is unavailable,
or an audit count differs from the scenario expectation. Failed or incomplete
runs are retained as evidence and are not presented as successful submissions.

Audit entry expectations are 5, 4, 8, 0, and 2 for scenarios 1 through 5. The last
scenario performs two authorized setup transitions before attempting an
unauthorized approval; that rejected approval must not add an audit row.

An empty audit array means that the database query returned zero successful
transitions. A null audit value means that database evidence could not be
exported. No audit rows are synthesized or removed by these tests.

To use an installed Maven executable or a custom output directory:

```sh
python3 scripts/run-workflow-scenarios.py --maven /path/to/mvn --output /tmp/workflow-evidence
```

The output directory must not already exist. Use `--offline` only when Maven,
plugins, and the PostgreSQL driver are already cached. Extra Maven options can
be supplied with `--maven-arg=-Dproperty=value`.
