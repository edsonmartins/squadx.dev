-- SquadX.dev Conversations & Messages Schema
-- Persistent chat for live sessions and team collaboration

-- Conversations table
CREATE TABLE IF NOT EXISTS conversations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Link to live session (optional - can be standalone team chat)
    session_id UUID REFERENCES live_sessions(id) ON DELETE SET NULL,

    -- Organization reference (links to SquadX.dev backend)
    organization_id BIGINT NOT NULL,

    -- Conversation metadata
    title TEXT,
    type TEXT NOT NULL DEFAULT 'session'
        CHECK (type IN ('session', 'team', 'direct')),

    -- State
    status TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'archived', 'deleted')),

    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    last_message_at TIMESTAMPTZ
);

-- Index for finding conversations by session
CREATE INDEX IF NOT EXISTS idx_conversations_session
    ON conversations(session_id)
    WHERE session_id IS NOT NULL;

-- Index for org-level queries
CREATE INDEX IF NOT EXISTS idx_conversations_org
    ON conversations(organization_id, status);

-- Index for recent conversations
CREATE INDEX IF NOT EXISTS idx_conversations_last_message
    ON conversations(organization_id, last_message_at DESC)
    WHERE status = 'active';

-- Messages table
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,

    -- Sender
    user_id UUID,
    display_name TEXT NOT NULL,

    -- Content
    content TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'text'
        CHECK (type IN ('text', 'system', 'annotation', 'code', 'file')),

    -- Optional metadata (for code blocks, file references, etc.)
    metadata JSONB DEFAULT '{}'::jsonb,

    -- Reply threading
    reply_to_id UUID REFERENCES messages(id) ON DELETE SET NULL,

    -- Edit/delete tracking
    edited_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,

    -- Timestamps
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Index for loading conversation messages (newest first)
CREATE INDEX IF NOT EXISTS idx_messages_conversation
    ON messages(conversation_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- Index for user message history
CREATE INDEX IF NOT EXISTS idx_messages_user
    ON messages(user_id, created_at DESC);

-- Conversation participants (tracks who is in each conversation)
CREATE TABLE IF NOT EXISTS conversation_participants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    display_name TEXT NOT NULL,

    -- Read tracking
    last_read_at TIMESTAMPTZ DEFAULT NOW(),
    unread_count INTEGER DEFAULT 0,

    -- Timestamps
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    left_at TIMESTAMPTZ,

    CONSTRAINT unique_conversation_user UNIQUE (conversation_id, user_id)
);

-- Index for participant lookups
CREATE INDEX IF NOT EXISTS idx_conversation_participants_conv
    ON conversation_participants(conversation_id)
    WHERE left_at IS NULL;

-- Index for user's conversations
CREATE INDEX IF NOT EXISTS idx_conversation_participants_user
    ON conversation_participants(user_id)
    WHERE left_at IS NULL;

-- Function to auto-create conversation when a live session starts
-- Note: organization_id is set to the task_id as a reference key since
-- live_sessions only tracks task_id. The backend resolves the actual org.
CREATE OR REPLACE FUNCTION create_session_conversation()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'active' AND OLD.status = 'created' THEN
        -- Check if a conversation already exists for this session
        IF NOT EXISTS (SELECT 1 FROM conversations WHERE session_id = NEW.id) THEN
            INSERT INTO conversations (session_id, organization_id, title, type)
            VALUES (NEW.id, NEW.task_id, 'Session Chat', 'session');
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger: auto-create conversation when session becomes active
CREATE TRIGGER trg_create_session_conversation
    AFTER UPDATE OF status ON live_sessions
    FOR EACH ROW
    EXECUTE FUNCTION create_session_conversation();

-- Function to update conversation's last_message_at
CREATE OR REPLACE FUNCTION update_conversation_last_message()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE conversations
    SET last_message_at = NEW.created_at,
        updated_at = NOW()
    WHERE id = NEW.conversation_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger: update last_message_at on new message
CREATE TRIGGER trg_update_last_message
    AFTER INSERT ON messages
    FOR EACH ROW
    EXECUTE FUNCTION update_conversation_last_message();

-- Row Level Security (RLS) policies
ALTER TABLE conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversation_participants ENABLE ROW LEVEL SECURITY;

-- Conversations: anyone can view active conversations
CREATE POLICY "Anyone can view active conversations"
    ON conversations FOR SELECT
    USING (status = 'active');

-- Conversations: authenticated users can create
CREATE POLICY "Authenticated users can create conversations"
    ON conversations FOR INSERT
    WITH CHECK (true);

-- Conversations: participants can update
CREATE POLICY "Participants can update conversations"
    ON conversations FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM conversation_participants cp
            WHERE cp.conversation_id = id
            AND cp.user_id = auth.uid()
            AND cp.left_at IS NULL
        )
    );

-- Messages: anyone in conversation can view
CREATE POLICY "Participants can view messages"
    ON messages FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM conversation_participants cp
            WHERE cp.conversation_id = messages.conversation_id
            AND cp.user_id = auth.uid()
            AND cp.left_at IS NULL
        )
    );

-- Messages: participants can send messages
CREATE POLICY "Participants can send messages"
    ON messages FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM conversation_participants cp
            WHERE cp.conversation_id = messages.conversation_id
            AND cp.user_id = auth.uid()
            AND cp.left_at IS NULL
        )
    );

-- Messages: authors can edit their own messages
CREATE POLICY "Authors can update own messages"
    ON messages FOR UPDATE
    USING (user_id = auth.uid());

-- Conversation participants: anyone can view
CREATE POLICY "Anyone can view participants"
    ON conversation_participants FOR SELECT
    USING (true);

-- Conversation participants: authenticated users can join
CREATE POLICY "Authenticated users can join conversations"
    ON conversation_participants FOR INSERT
    WITH CHECK (true);

-- Conversation participants: users can update their own record
CREATE POLICY "Users can update own participant record"
    ON conversation_participants FOR UPDATE
    USING (user_id = auth.uid());

-- Enable realtime for chat tables
ALTER PUBLICATION supabase_realtime ADD TABLE conversations;
ALTER PUBLICATION supabase_realtime ADD TABLE messages;
ALTER PUBLICATION supabase_realtime ADD TABLE conversation_participants;
