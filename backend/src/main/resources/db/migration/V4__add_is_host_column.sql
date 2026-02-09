-- Add is_host column to live_session_participants
-- Version: 4.0.0
-- Description: Adds the is_host column to identify session hosts

ALTER TABLE live_session_participants
ADD COLUMN IF NOT EXISTS is_host BOOLEAN NOT NULL DEFAULT FALSE;
