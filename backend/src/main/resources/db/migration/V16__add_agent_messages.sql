CREATE TABLE agent_messages (
    id BIGSERIAL PRIMARY KEY,
    from_agent_id BIGINT NOT NULL REFERENCES agents(id),
    to_agent_id BIGINT REFERENCES agents(id),
    execution_id BIGINT REFERENCES executions(id),
    message_type VARCHAR(30) NOT NULL DEFAULT 'MESSAGE',
    content TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    is_broadcast BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_agent_msg_to ON agent_messages(to_agent_id, is_read);
CREATE INDEX idx_agent_msg_exec ON agent_messages(execution_id);
