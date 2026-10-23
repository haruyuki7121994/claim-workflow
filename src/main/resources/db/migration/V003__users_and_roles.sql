CREATE TABLE IF NOT EXISTS app_users (
    user_id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    subject VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (tenant_id, subject)
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL REFERENCES app_users(user_id),
    role VARCHAR(80) NOT NULL CHECK (role IN (
        'document_clerk', 'team_lead', 'assessor', 'finance', 'system'
    )),
    PRIMARY KEY (user_id, role)
);

CREATE INDEX IF NOT EXISTS idx_app_users_tenant_enabled
    ON app_users(tenant_id, enabled);
CREATE INDEX IF NOT EXISTS idx_user_roles_role
    ON user_roles(role);

INSERT INTO app_users (user_id, tenant_id, subject, display_name, enabled, created_at, updated_at) VALUES
    ('10000000-0000-0000-0000-000000000001', 'tenant-a', 'clerk-1', 'Document Clerk', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000002', 'tenant-a', 'lead-1', 'Team Lead', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000003', 'tenant-a', 'assessor-1', 'Claims Assessor', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000004', 'tenant-a', 'finance-1', 'Finance Officer', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000005', 'tenant-a', 'system-1', 'Workflow System', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000006', 'tenant-a', 'assessor-2', 'Backup Claims Assessor', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_roles (user_id, role) VALUES
    ('10000000-0000-0000-0000-000000000001', 'document_clerk'),
    ('10000000-0000-0000-0000-000000000002', 'team_lead'),
    ('10000000-0000-0000-0000-000000000003', 'assessor'),
    ('10000000-0000-0000-0000-000000000004', 'finance'),
    ('10000000-0000-0000-0000-000000000005', 'system'),
    ('10000000-0000-0000-0000-000000000006', 'assessor')
ON CONFLICT (user_id, role) DO NOTHING;

REVOKE ALL ON app_users, user_roles FROM PUBLIC;
GRANT SELECT ON app_users, user_roles TO claimworkflow_app;
