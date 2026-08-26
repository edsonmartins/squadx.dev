ALTER TABLE execution_artifacts ADD COLUMN artifact_group VARCHAR(160);
ALTER TABLE execution_artifacts ADD COLUMN view_role VARCHAR(32);
ALTER TABLE execution_artifacts ADD COLUMN base_revision VARCHAR(128);

UPDATE execution_artifacts
SET view_role = 'CURRENT'
WHERE type = 'ARCHITECTURE_MAP' AND format = 'JSON' AND view_role IS NULL;

CREATE INDEX idx_execution_artifacts_group ON execution_artifacts (artifact_group);
CREATE INDEX idx_execution_artifacts_baseline
    ON execution_artifacts (type, format, view_role, created_at DESC);
