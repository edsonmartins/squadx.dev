CREATE TABLE user_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    email_notifications BOOLEAN NOT NULL DEFAULT true,
    push_notifications BOOLEAN NOT NULL DEFAULT true,
    execution_alerts BOOLEAN NOT NULL DEFAULT true,
    live_session_alerts BOOLEAN NOT NULL DEFAULT true,
    auto_start_live BOOLEAN NOT NULL DEFAULT true,
    default_quality VARCHAR(10) NOT NULL DEFAULT 'HD',
    max_viewers INTEGER NOT NULL DEFAULT 5,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_preferences_user ON user_preferences(user_id);
