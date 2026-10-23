CREATE TABLE claims (
    claim_id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    state VARCHAR(80) NOT NULL,
    workflow_version VARCHAR(40) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    information_request_count INTEGER NOT NULL DEFAULT 0 CHECK (information_request_count >= 0),
    facts TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_claims_tenant_state ON claims(tenant_id, state);

CREATE TABLE claim_transition_audit (
    audit_id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims(claim_id),
    from_state VARCHAR(80) NOT NULL,
    to_state VARCHAR(80) NOT NULL,
    actor_id VARCHAR(100) NOT NULL,
    actor_role VARCHAR(80) NOT NULL,
    sequence_number BIGINT NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    workflow_version VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (claim_id, sequence_number)
);

CREATE TABLE workflow_tasks (
    task_id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims(claim_id),
    required_role VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL,
    task_type VARCHAR(100) NOT NULL,
    assigned_to VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_tasks_role_status ON workflow_tasks(required_role, status);
CREATE INDEX idx_tasks_claim_status ON workflow_tasks(claim_id, status);

CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims(claim_id),
    transition_id UUID NOT NULL REFERENCES claim_transition_audit(audit_id),
    action_type VARCHAR(100) NOT NULL,
    status VARCHAR(40) NOT NULL,
    payload TEXT NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_token UUID,
    lease_until TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (transition_id, action_type)
);
CREATE INDEX idx_outbox_status_retry ON outbox_events(status, next_retry_at);
CREATE INDEX idx_outbox_claim ON outbox_events(claim_id);

CREATE TABLE idempotency_requests (
    tenant_id VARCHAR(100) NOT NULL,
    actor_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    claim_id UUID NOT NULL REFERENCES claims(claim_id),
    request_hash VARCHAR(64) NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (tenant_id, actor_id, idempotency_key)
);

-- Durable receipt for the mock external provider: its event ID is the idempotency key.
CREATE TABLE effect_receipts (
    event_id UUID PRIMARY KEY REFERENCES outbox_events(event_id),
    result VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
