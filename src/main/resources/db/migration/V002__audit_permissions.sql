-- Run as the schema owner. claimworkflow_app is created by infra/init/001-bootstrap.sh.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'claimworkflow_app') THEN
        RAISE EXCEPTION 'Required database role claimworkflow_app does not exist'
            USING ERRCODE = '42704',
                  HINT = 'Run infra/configure-runtime-role.sh as the database administrator, then retry this migration.';
    END IF;
END;
$$;

REVOKE ALL ON claim_transition_audit FROM PUBLIC, claimworkflow_app;
GRANT SELECT, INSERT ON claim_transition_audit TO claimworkflow_app;
GRANT USAGE ON SCHEMA public TO claimworkflow_app;
GRANT SELECT, INSERT, UPDATE ON claims, workflow_tasks, outbox_events TO claimworkflow_app;
GRANT SELECT, INSERT ON idempotency_requests, effect_receipts TO claimworkflow_app;

-- Defense in depth: even an accidental future grant cannot mutate audit rows.
CREATE OR REPLACE FUNCTION reject_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'claim_transition_audit is insert and read only' USING ERRCODE = '42501';
END;
$$;
DROP TRIGGER IF EXISTS audit_no_update_delete ON claim_transition_audit;
CREATE TRIGGER audit_no_update_delete BEFORE UPDATE OR DELETE ON claim_transition_audit
    FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_mutation();
DROP TRIGGER IF EXISTS audit_no_truncate ON claim_transition_audit;
CREATE TRIGGER audit_no_truncate BEFORE TRUNCATE ON claim_transition_audit
    FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_mutation();
REVOKE ALL ON FUNCTION reject_audit_mutation() FROM PUBLIC;
