CREATE TABLE cost_events (
    id BIGSERIAL PRIMARY KEY,
    execution_id BIGINT REFERENCES executions(id),
    agent_id BIGINT REFERENCES agents(id),
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    cost_cents NUMERIC(10,4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cost_events_org ON cost_events(organization_id);
CREATE INDEX idx_cost_events_agent ON cost_events(agent_id);
CREATE INDEX idx_cost_events_created ON cost_events(created_at);
