ALTER TABLE agents ADD COLUMN harness_id BIGINT REFERENCES harnesses(id) ON DELETE SET NULL;
CREATE INDEX idx_agents_harness ON agents(harness_id);
