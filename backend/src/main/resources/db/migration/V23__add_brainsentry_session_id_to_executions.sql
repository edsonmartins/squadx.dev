ALTER TABLE executions
    ADD COLUMN IF NOT EXISTS brain_sentry_session_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_executions_brain_sentry_session_id
    ON executions(brain_sentry_session_id);
