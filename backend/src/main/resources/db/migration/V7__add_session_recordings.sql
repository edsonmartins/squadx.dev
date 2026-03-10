CREATE TABLE session_recordings (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES live_sessions(id),
    s3_key VARCHAR(500) NOT NULL,
    s3_bucket VARCHAR(255) NOT NULL,
    duration_seconds INTEGER,
    file_size_bytes BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'RECORDING',
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_session_recordings_session_id ON session_recordings(session_id);
