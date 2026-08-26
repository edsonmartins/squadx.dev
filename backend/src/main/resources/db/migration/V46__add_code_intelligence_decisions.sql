CREATE TABLE code_intelligence_decisions (
 id BIGSERIAL PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 snapshot_id BIGINT NOT NULL REFERENCES code_intelligence_snapshots(id), title VARCHAR(200) NOT NULL,
 rationale TEXT NOT NULL, evidence_json TEXT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 brainsentry_memory_id VARCHAR(160), reviewed_by BIGINT, reviewed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_ci_decision_snapshot ON code_intelligence_decisions(snapshot_id, status);
