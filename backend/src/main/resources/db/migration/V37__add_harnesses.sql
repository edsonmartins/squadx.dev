-- Control Panel — harness-connectors: registry of agent harnesses (Claude Code, Codex, Gemini CLI,
-- Cursor), all speaking the same MCP `workspace` contract (ADR-0003). Model is chosen per harness.

CREATE TABLE harnesses (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    key VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    vendor VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',   -- AVAILABLE | CONNECTED
    model VARCHAR(255),                                -- chosen LLM model
    agent_id BIGINT REFERENCES agents(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_harness_org_key UNIQUE (organization_id, key)
);
CREATE INDEX idx_harnesses_org ON harnesses(organization_id);

CREATE TABLE harness_models (
    harness_id BIGINT NOT NULL REFERENCES harnesses(id) ON DELETE CASCADE,
    model VARCHAR(255) NOT NULL
);
CREATE INDEX idx_harness_models_harness ON harness_models(harness_id);
