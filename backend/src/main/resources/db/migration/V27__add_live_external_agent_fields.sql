ALTER TABLE live_sessions
    ADD COLUMN IF NOT EXISTS external_agent_participant_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS external_agent_display_name VARCHAR(255);
