-- Control Panel — harness live handshake: opening a workspace MCP session with a harness_key
-- stamps last_connected_at; CONNECTED status is derived (within session TTL), not job-managed.

ALTER TABLE harnesses ADD COLUMN last_connected_at TIMESTAMP WITH TIME ZONE;
