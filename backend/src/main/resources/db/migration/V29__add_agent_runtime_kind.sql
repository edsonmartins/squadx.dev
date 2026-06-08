-- Runtime adapter: an agent can either run SquadX's native LangGraph loop (NATIVE)
-- or shell out to an external coding-agent CLI (EXTERNAL_CLI) inside the sandbox.
ALTER TABLE agents
    ADD COLUMN runtime_kind VARCHAR(30) NOT NULL DEFAULT 'NATIVE',
    ADD COLUMN cli_provider VARCHAR(30);
