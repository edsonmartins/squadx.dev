-- Tasks can be assigned to a squad (not just an agent). At execution time the
-- squad's leader (or an online member) is resolved to run the task.
ALTER TABLE tasks
    ADD COLUMN assigned_squad_id BIGINT REFERENCES squads(id) ON DELETE SET NULL;
CREATE INDEX idx_tasks_assigned_squad_id ON tasks(assigned_squad_id);
