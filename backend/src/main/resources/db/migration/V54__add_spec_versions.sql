CREATE TABLE spec_versions (
    id BIGSERIAL PRIMARY KEY,
    change_id BIGINT NOT NULL REFERENCES changes(id) ON DELETE CASCADE,
    version VARCHAR(50) NOT NULL,
    current BOOLEAN NOT NULL DEFAULT FALSE,
    summary VARCHAR(500),
    author_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    commit_sha VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_spec_version_change_version UNIQUE (change_id, version)
);
CREATE UNIQUE INDEX uq_spec_version_current ON spec_versions(change_id) WHERE current = TRUE;
CREATE INDEX idx_spec_versions_change ON spec_versions(change_id);
