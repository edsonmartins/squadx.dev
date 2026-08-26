ALTER TABLE code_intelligence_snapshots
    ADD COLUMN IF NOT EXISTS scip_artifact_sha256 VARCHAR(64);
