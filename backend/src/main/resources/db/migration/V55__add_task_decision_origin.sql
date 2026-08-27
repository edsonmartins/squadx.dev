-- RFC-0007 / T-0010-4: origem da decisão na task (source_ref + source_kind).
-- ``source_ref`` é a âncora estável da decisão que gerou a task
-- (ex.: "docs/rfc/RFC-0007.md#T-0011-6"); ``source_kind`` é ADR|RFC|CHANGE|NONE.
ALTER TABLE tasks ADD COLUMN source_ref VARCHAR(255);
ALTER TABLE tasks ADD COLUMN source_kind VARCHAR(16) DEFAULT 'NONE' NOT NULL;
CREATE INDEX idx_tasks_source_ref ON tasks(source_ref) WHERE source_ref IS NOT NULL;
