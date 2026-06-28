-- Run Admission (ADR-0007, RFC-0005 §2): idempotent dedup of duplicate triggers and durable
-- follow-up requests when a run is already active for the same task.

-- Idempotency key on executions. Uniqueness is per task (a task belongs to exactly one org, so
-- (task_id, idempotency_key) already scopes per organization). NULL keys stay distinct in Postgres,
-- so API-initiated runs without a key are unaffected.
ALTER TABLE executions
    ADD COLUMN idempotency_key VARCHAR(128);

CREATE UNIQUE INDEX idx_executions_task_idempotency
    ON executions(task_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Durable follow-up requests: a second trigger that arrives while a run is active is queued here
-- and promoted to a new execution when the active one terminates.
CREATE TABLE follow_up_requests (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    active_execution_id BIGINT REFERENCES executions(id),
    requested_agent_id BIGINT REFERENCES agents(id),
    requested_by_email VARCHAR(255),
    source_payload TEXT,
    decision TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_follow_up_requests_task_status ON follow_up_requests(task_id, status, created_at);
