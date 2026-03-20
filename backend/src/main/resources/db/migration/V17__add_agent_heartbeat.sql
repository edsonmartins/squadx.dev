-- Agent lifecycle: heartbeat and state tracking
ALTER TABLE agents ADD COLUMN last_heartbeat TIMESTAMP;
ALTER TABLE agents ADD COLUMN lifecycle_state VARCHAR(20) DEFAULT 'READY';

CREATE INDEX idx_agents_lifecycle ON agents(lifecycle_state);
CREATE INDEX idx_agents_heartbeat ON agents(last_heartbeat);
