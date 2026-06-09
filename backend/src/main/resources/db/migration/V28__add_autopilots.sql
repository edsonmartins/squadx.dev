-- Autopilots: scheduled/recurring work that auto-creates (and optionally runs) tasks.
-- Inspired by Multica's autopilot concept. Scheduling is driven by JobRunr recurring jobs
-- (one recurring job per autopilot, id = "autopilot-{id}").

CREATE TABLE autopilots (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    cron_expression VARCHAR(120) NOT NULL,
    timezone VARCHAR(60) NOT NULL DEFAULT 'UTC',
    execution_mode VARCHAR(20) NOT NULL DEFAULT 'CREATE_TASK',
    target_squad_id BIGINT REFERENCES squads(id) ON DELETE SET NULL,
    target_agent_id BIGINT REFERENCES agents(id) ON DELETE SET NULL,
    task_title VARCHAR(255) NOT NULL,
    task_description TEXT,
    task_priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at TIMESTAMP WITH TIME ZONE,
    next_run_at TIMESTAMP WITH TIME ZONE,
    run_count INTEGER NOT NULL DEFAULT 0,
    created_by_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_autopilots_organization ON autopilots(organization_id);
CREATE INDEX idx_autopilots_project ON autopilots(project_id);
CREATE INDEX idx_autopilots_enabled ON autopilots(enabled);

CREATE TABLE autopilot_runs (
    id BIGSERIAL PRIMARY KEY,
    autopilot_id BIGINT NOT NULL REFERENCES autopilots(id) ON DELETE CASCADE,
    trigger_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_task_id BIGINT REFERENCES tasks(id) ON DELETE SET NULL,
    execution_id BIGINT REFERENCES executions(id) ON DELETE SET NULL,
    message TEXT,
    triggered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_autopilot_runs_autopilot ON autopilot_runs(autopilot_id);
CREATE INDEX idx_autopilot_runs_triggered_at ON autopilot_runs(triggered_at);
