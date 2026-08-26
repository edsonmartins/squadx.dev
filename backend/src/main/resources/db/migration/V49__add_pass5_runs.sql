-- Control Panel — pass5-validation: record of conformance-gate runs. Idempotent per (task, pr_sha)
-- so re-running the same PR head does not produce divergent outcomes (RFC-0004 §6).

CREATE TABLE pass5_runs (
    id BIGSERIAL PRIMARY KEY,
    spec_task_id BIGINT NOT NULL REFERENCES spec_tasks(id) ON DELETE CASCADE,
    pr_sha VARCHAR(255),
    outcome VARCHAR(20) NOT NULL,              -- PASS | FAIL
    critique TEXT,
    coverage_total INTEGER NOT NULL DEFAULT 0,
    coverage_covered INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_pass5_run_task_sha UNIQUE (spec_task_id, pr_sha)
);
CREATE INDEX idx_pass5_runs_task ON pass5_runs(spec_task_id);


