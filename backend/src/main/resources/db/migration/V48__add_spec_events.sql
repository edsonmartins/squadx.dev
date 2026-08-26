-- Control Panel — execution-tracking: append-only event log. Task status is a projection of
-- these events (ADR-0002, RFC-0003). Sources: GIT (webhooks), MCP (agent/dev), PASS5.

CREATE TABLE spec_events (
    id BIGSERIAL PRIMARY KEY,
    spec_task_id BIGINT NOT NULL REFERENCES spec_tasks(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,                 -- STARTED | IMPLEMENTED | PR_OPENED | BLOCKED | ...
    source VARCHAR(20) NOT NULL,               -- GIT | MCP | PASS5
    source_ref VARCHAR(255),                   -- commit sha, PR id, MCP call id, pass5 run id
    payload TEXT,                              -- reason / critique / note
    dedup_key VARCHAR(255) NOT NULL UNIQUE,    -- idempotency (R4)
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_spec_events_task_occurred ON spec_events(spec_task_id, occurred_at);

-- Append-only: block UPDATE (events are immutable). DELETE is allowed so that deleting a
-- spec_task cascades cleanly; the trail is otherwise application-managed (insert-only).
CREATE OR REPLACE FUNCTION prevent_spec_event_update()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'spec_events are immutable and cannot be updated';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_spec_events_no_update
    BEFORE UPDATE ON spec_events
    FOR EACH ROW
    EXECUTE FUNCTION prevent_spec_event_update();


