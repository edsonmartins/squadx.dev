-- Add resolution column to live_sessions
-- Version: 2.0.0
-- Description: Adds the resolution field for live session screen configuration

ALTER TABLE live_sessions
ADD COLUMN IF NOT EXISTS resolution VARCHAR(20) DEFAULT '1280x720';

COMMENT ON COLUMN live_sessions.resolution IS 'Screen resolution for the live session (e.g., 1280x720, 1920x1080)';
