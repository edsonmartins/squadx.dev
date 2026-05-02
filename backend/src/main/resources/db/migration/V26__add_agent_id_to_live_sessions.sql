ALTER TABLE live_sessions
    ADD COLUMN IF NOT EXISTS agent_id BIGINT REFERENCES agents(id);

CREATE INDEX IF NOT EXISTS idx_live_sessions_agent_id
    ON live_sessions(agent_id);
