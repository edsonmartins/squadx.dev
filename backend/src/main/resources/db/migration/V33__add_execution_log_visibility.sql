-- Attention Budget (ADR-0007, RFC-0005 §1): classify each execution log/event so the dashboard
-- can stay quiet by default (only visibility = 'human') and reveal audit/debug on demand.
ALTER TABLE execution_logs
    ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'human',
    ADD COLUMN importance VARCHAR(16) NOT NULL DEFAULT 'normal';

CREATE INDEX idx_execution_logs_visibility ON execution_logs(execution_id, visibility);
