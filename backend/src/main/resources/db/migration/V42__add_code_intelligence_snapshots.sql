CREATE TABLE code_intelligence_snapshots (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    repository_url VARCHAR(1000) NOT NULL,
    revision VARCHAR(128) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    provider_version VARCHAR(80),
    external_snapshot_id VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    indexed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ux_ci_snapshot_project_revision_provider UNIQUE (project_id, revision, provider)
);

CREATE INDEX ix_ci_snapshot_org_status
    ON code_intelligence_snapshots (organization_id, status);

CREATE TABLE code_intelligence_index_jobs (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT NOT NULL REFERENCES code_intelligence_snapshots(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    attempt INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_ci_job_snapshot_status
    ON code_intelligence_index_jobs (snapshot_id, status);

