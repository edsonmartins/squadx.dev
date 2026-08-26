CREATE TABLE harnesses (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    harness_key VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    vendor VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    model VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_harness_org_key UNIQUE (organization_id, harness_key)
);
CREATE INDEX idx_harnesses_org ON harnesses(organization_id);

CREATE TABLE harness_models (
    harness_id BIGINT NOT NULL REFERENCES harnesses(id) ON DELETE CASCADE,
    model VARCHAR(255) NOT NULL,
    PRIMARY KEY (harness_id, model)
);
