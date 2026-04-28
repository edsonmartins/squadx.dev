ALTER TABLE live_sessions
    ADD COLUMN IF NOT EXISTS external_session_id VARCHAR(255);

ALTER TABLE live_sessions
    ADD COLUMN IF NOT EXISTS external_join_code VARCHAR(64);

ALTER TABLE live_sessions
    ADD COLUMN IF NOT EXISTS external_join_url VARCHAR(1024);
