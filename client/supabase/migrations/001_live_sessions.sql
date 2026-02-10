-- SquadX.dev Live Sessions Schema
-- This schema supports live streaming of agent screens via WebRTC

-- Enable necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Live Sessions table
CREATE TABLE IF NOT EXISTS live_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Task reference (links to SquadX.dev backend)
    task_id INTEGER NOT NULL,

    -- Host information
    host_user_id UUID,
    current_host_id UUID,

    -- Session state
    status TEXT NOT NULL DEFAULT 'created'
        CHECK (status IN ('created', 'active', 'paused', 'ended')),

    -- Connection mode
    mode TEXT NOT NULL DEFAULT 'p2p'
        CHECK (mode IN ('p2p', 'sfu')),

    -- Join code for easy sharing (8 characters)
    join_code TEXT UNIQUE NOT NULL,

    -- Limits
    max_controllers INTEGER DEFAULT 3,
    max_viewers INTEGER DEFAULT 25,

    -- Settings (JSON for flexibility)
    settings JSONB DEFAULT '{}'::jsonb,

    -- Host tracking
    host_last_seen_at TIMESTAMPTZ,
    host_status TEXT DEFAULT 'offline'
        CHECK (host_status IN ('online', 'reconnecting', 'offline', 'transferred')),
    backup_host_id UUID,
    host_transferred_at TIMESTAMPTZ,

    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,

    -- Indexes for common queries
    CONSTRAINT unique_active_task UNIQUE (task_id, status)
        DEFERRABLE INITIALLY DEFERRED
);

-- Index for finding active sessions by task
CREATE INDEX IF NOT EXISTS idx_live_sessions_task_status
    ON live_sessions(task_id, status)
    WHERE status = 'active';

-- Index for join code lookups
CREATE INDEX IF NOT EXISTS idx_live_sessions_join_code
    ON live_sessions(join_code);

-- Participants table
CREATE TABLE IF NOT EXISTS live_participants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,

    -- User info (can be null for anonymous viewers)
    user_id UUID,
    display_name TEXT NOT NULL,

    -- Role and permissions
    role TEXT NOT NULL DEFAULT 'viewer'
        CHECK (role IN ('host', 'viewer', 'controller')),
    control_state TEXT NOT NULL DEFAULT 'view-only'
        CHECK (control_state IN ('view-only', 'requested', 'granted')),

    -- Tracking
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    left_at TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ DEFAULT NOW(),

    -- Connection info
    connection_state TEXT DEFAULT 'connecting'
        CHECK (connection_state IN ('connecting', 'connected', 'disconnected')),

    CONSTRAINT unique_session_user UNIQUE (session_id, user_id)
);

-- Index for participant lookups
CREATE INDEX IF NOT EXISTS idx_live_participants_session
    ON live_participants(session_id)
    WHERE left_at IS NULL;

-- Media sessions table (tracks active streams)
CREATE TABLE IF NOT EXISTS live_media_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,

    -- Publisher info
    publisher_id UUID NOT NULL,

    -- Stream configuration
    mode TEXT NOT NULL DEFAULT 'p2p',
    status TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'paused', 'ended')),

    -- Video settings
    video_codec TEXT DEFAULT 'H264',
    max_bitrate INTEGER DEFAULT 4000000, -- 4 Mbps
    resolution TEXT DEFAULT '1920x1080',
    frame_rate INTEGER DEFAULT 30,

    -- Timestamps
    started_at TIMESTAMPTZ DEFAULT NOW(),
    ended_at TIMESTAMPTZ,

    CONSTRAINT one_active_per_session UNIQUE (session_id, status)
        DEFERRABLE INITIALLY DEFERRED
);

-- Function to generate join code
CREATE OR REPLACE FUNCTION generate_join_code()
RETURNS TEXT AS $$
DECLARE
    chars TEXT := 'abcdefghjkmnpqrstuvwxyz23456789';
    result TEXT := '';
    i INTEGER;
BEGIN
    FOR i IN 1..8 LOOP
        result := result || substr(chars, floor(random() * length(chars) + 1)::integer, 1);
    END LOOP;
    RETURN result;
END;
$$ LANGUAGE plpgsql;

-- Function to create a live session
CREATE OR REPLACE FUNCTION create_live_session(
    p_task_id INTEGER,
    p_host_user_id UUID DEFAULT NULL,
    p_mode TEXT DEFAULT 'p2p',
    p_max_viewers INTEGER DEFAULT 25
)
RETURNS live_sessions AS $$
DECLARE
    v_session live_sessions;
    v_join_code TEXT;
    v_attempts INTEGER := 0;
BEGIN
    -- Generate unique join code
    LOOP
        v_join_code := generate_join_code();
        EXIT WHEN NOT EXISTS (SELECT 1 FROM live_sessions WHERE join_code = v_join_code);
        v_attempts := v_attempts + 1;
        IF v_attempts > 10 THEN
            RAISE EXCEPTION 'Could not generate unique join code';
        END IF;
    END LOOP;

    -- Create session
    INSERT INTO live_sessions (
        task_id,
        host_user_id,
        current_host_id,
        mode,
        join_code,
        max_viewers,
        status,
        host_status
    ) VALUES (
        p_task_id,
        p_host_user_id,
        p_host_user_id,
        p_mode,
        v_join_code,
        p_max_viewers,
        'created',
        'offline'
    ) RETURNING * INTO v_session;

    RETURN v_session;
END;
$$ LANGUAGE plpgsql;

-- Function to join a session
CREATE OR REPLACE FUNCTION join_live_session(
    p_join_code TEXT,
    p_user_id UUID DEFAULT NULL,
    p_display_name TEXT DEFAULT 'Viewer'
)
RETURNS live_participants AS $$
DECLARE
    v_session live_sessions;
    v_participant live_participants;
    v_count INTEGER;
BEGIN
    -- Find session
    SELECT * INTO v_session
    FROM live_sessions
    WHERE join_code = p_join_code
    AND status IN ('created', 'active', 'paused');

    IF v_session IS NULL THEN
        RAISE EXCEPTION 'Session not found or ended';
    END IF;

    -- Check viewer limit
    SELECT COUNT(*) INTO v_count
    FROM live_participants
    WHERE session_id = v_session.id
    AND left_at IS NULL;

    IF v_count >= v_session.max_viewers THEN
        RAISE EXCEPTION 'Session is full';
    END IF;

    -- Add participant
    INSERT INTO live_participants (
        session_id,
        user_id,
        display_name,
        role,
        control_state
    ) VALUES (
        v_session.id,
        p_user_id,
        p_display_name,
        'viewer',
        'view-only'
    )
    ON CONFLICT (session_id, user_id)
    DO UPDATE SET
        left_at = NULL,
        last_seen_at = NOW(),
        connection_state = 'connecting'
    RETURNING * INTO v_participant;

    RETURN v_participant;
END;
$$ LANGUAGE plpgsql;

-- Function to update host heartbeat
CREATE OR REPLACE FUNCTION update_host_heartbeat(p_session_id UUID)
RETURNS live_sessions AS $$
DECLARE
    v_session live_sessions;
BEGIN
    UPDATE live_sessions
    SET
        host_last_seen_at = NOW(),
        host_status = 'online'
    WHERE id = p_session_id
    RETURNING * INTO v_session;

    RETURN v_session;
END;
$$ LANGUAGE plpgsql;

-- Row Level Security (RLS) policies
ALTER TABLE live_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE live_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE live_media_sessions ENABLE ROW LEVEL SECURITY;

-- Allow read access to active sessions
CREATE POLICY "Anyone can view active sessions"
    ON live_sessions FOR SELECT
    USING (status IN ('created', 'active', 'paused'));

-- Allow authenticated users to create sessions
CREATE POLICY "Authenticated users can create sessions"
    ON live_sessions FOR INSERT
    WITH CHECK (true);

-- Allow hosts to update their sessions
CREATE POLICY "Hosts can update their sessions"
    ON live_sessions FOR UPDATE
    USING (host_user_id = auth.uid() OR current_host_id = auth.uid());

-- Participants policies
CREATE POLICY "Anyone can view participants"
    ON live_participants FOR SELECT
    USING (true);

CREATE POLICY "Anyone can join as participant"
    ON live_participants FOR INSERT
    WITH CHECK (true);

CREATE POLICY "Participants can update their own record"
    ON live_participants FOR UPDATE
    USING (user_id = auth.uid() OR user_id IS NULL);

-- Media sessions policies
CREATE POLICY "Anyone can view media sessions"
    ON live_media_sessions FOR SELECT
    USING (true);

CREATE POLICY "Hosts can manage media sessions"
    ON live_media_sessions FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM live_sessions s
            WHERE s.id = session_id
            AND (s.host_user_id = auth.uid() OR s.current_host_id = auth.uid())
        )
    );

-- Enable realtime for live sessions
ALTER PUBLICATION supabase_realtime ADD TABLE live_sessions;
ALTER PUBLICATION supabase_realtime ADD TABLE live_participants;
