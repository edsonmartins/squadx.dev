ALTER TABLE spec_events ADD COLUMN actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_spec_events_actor ON spec_events(actor_user_id);
