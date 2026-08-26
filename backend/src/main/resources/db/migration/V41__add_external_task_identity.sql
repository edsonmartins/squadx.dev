ALTER TABLE tasks
    ADD COLUMN external_system VARCHAR(40),
    ADD COLUMN external_id VARCHAR(160),
    ADD COLUMN requested_git_revision VARCHAR(128),
    ADD COLUMN architecture_only BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX ux_tasks_external_identity
    ON tasks (external_system, external_id)
    WHERE external_system IS NOT NULL AND external_id IS NOT NULL;
