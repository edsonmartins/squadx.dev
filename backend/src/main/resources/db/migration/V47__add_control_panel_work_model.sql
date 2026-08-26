-- Control Panel (SquadX.dev Spec) — work model: project -> change -> requirement -> task.
-- New bounded context (ADR-0006). Distinct table names to avoid colliding with the
-- existing execution `tasks`. Spec labels are PT; identifiers are EN.

CREATE TABLE changes (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    module VARCHAR(255),
    phase VARCHAR(50) NOT NULL DEFAULT 'SPEC',
    created_by_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_changes_project ON changes(project_id);

CREATE TABLE requirements (
    id BIGSERIAL PRIMARY KEY,
    change_id BIGINT NOT NULL REFERENCES changes(id) ON DELETE CASCADE,
    requirement_id VARCHAR(50) NOT NULL,          -- stable ref within the change, e.g. "R1"
    type VARCHAR(50) NOT NULL,                     -- ADDED | MODIFIED | REMOVED
    title VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_requirements_change ON requirements(change_id);

CREATE TABLE scenarios (
    id BIGSERIAL PRIMARY KEY,
    requirement_id BIGINT NOT NULL REFERENCES requirements(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    when_condition TEXT NOT NULL,                  -- "when" is a reserved word
    then_result TEXT NOT NULL,                     -- "then" is a reserved word
    covered BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_scenarios_requirement ON scenarios(requirement_id);

CREATE TABLE spec_tasks (
    id BIGSERIAL PRIMARY KEY,
    change_id BIGINT NOT NULL REFERENCES changes(id) ON DELETE CASCADE,
    requirement_id BIGINT REFERENCES requirements(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'A_FAZER',
    assignee_type VARCHAR(20),                     -- HUMAN | AGENT
    assigned_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    assigned_agent_id BIGINT REFERENCES agents(id) ON DELETE SET NULL,
    pass5 VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | PASS | FAIL
    blocker_reason TEXT,
    revise_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_spec_tasks_change ON spec_tasks(change_id);
CREATE INDEX idx_spec_tasks_requirement ON spec_tasks(requirement_id);
CREATE INDEX idx_spec_tasks_status ON spec_tasks(status);


