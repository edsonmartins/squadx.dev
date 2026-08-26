CREATE TABLE execution_artifacts (
    id BIGSERIAL PRIMARY KEY,
    execution_id BIGINT NOT NULL REFERENCES executions(id) ON DELETE CASCADE,
    artifact_key VARCHAR(160) NOT NULL,
    type VARCHAR(64) NOT NULL,
    format VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    git_revision VARCHAR(128),
    checksum_sha256 VARCHAR(64) NOT NULL,
    evidence_json TEXT,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_execution_artifact_key UNIQUE (execution_id, artifact_key)
);

CREATE INDEX idx_execution_artifacts_execution_created
    ON execution_artifacts (execution_id, created_at DESC);

