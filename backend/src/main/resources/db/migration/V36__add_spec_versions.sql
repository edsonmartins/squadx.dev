-- Control Panel — spec-versioning-materialization: semantic version history of a change's spec.
-- The Control Panel owns versioning; each approved version is materialized to Git (ADR-0001, RFC-0002).

CREATE TABLE spec_versions (
    id BIGSERIAL PRIMARY KEY,
    change_id BIGINT NOT NULL REFERENCES changes(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    current BOOLEAN NOT NULL DEFAULT FALSE,
    summary TEXT,
    author_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    commit VARCHAR(255),                       -- Git sha once materialized (null before)
    content_hash VARCHAR(255),                 -- SHA-256 of rendered content (idempotency)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_spec_version_change_version UNIQUE (change_id, version)
);
CREATE INDEX idx_spec_versions_change ON spec_versions(change_id);
