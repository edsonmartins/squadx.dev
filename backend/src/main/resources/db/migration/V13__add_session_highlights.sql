-- Session highlights for AI-powered analysis
CREATE TABLE session_highlights (
    id                  BIGSERIAL PRIMARY KEY,
    recording_id        BIGINT NOT NULL REFERENCES session_recordings(id) ON DELETE CASCADE,
    highlight_type      VARCHAR(30) NOT NULL,
    timestamp_seconds   INTEGER NOT NULL,
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    confidence          REAL NOT NULL DEFAULT 1.0,
    metadata            JSONB,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_highlight_type CHECK (highlight_type IN (
        'BUG_FOUND', 'FEATURE_COMPLETED', 'TEST_PASSED', 'TEST_FAILED',
        'ERROR_DETECTED', 'COMMIT_MADE', 'DEPLOY', 'CUSTOM'
    )),
    CONSTRAINT chk_confidence_range CHECK (confidence >= 0.0 AND confidence <= 1.0)
);

CREATE INDEX idx_session_highlights_recording_id ON session_highlights(recording_id);
CREATE INDEX idx_session_highlights_type ON session_highlights(highlight_type);
CREATE INDEX idx_session_highlights_timestamp ON session_highlights(recording_id, timestamp_seconds);
