"""Tests for squadx_client.live.session_manager module."""

import asyncio
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from squadx_client.live.session_manager import (
    LiveSessionManager,
    LiveSession,
    PeerConnection,
    SessionState,
    RealtimeMessage,
)


@pytest.fixture
def mock_supabase_client():
    """Create a mock SupabaseClient."""
    client = AsyncMock()
    client.get_session_by_task = AsyncMock(return_value=None)
    client.create_session = AsyncMock(return_value={
        "id": "session-1",
        "join_code": "ABC123",
        "mode": "p2p",
    })
    client.subscribe_to_session = AsyncMock()
    client.unsubscribe_from_session = AsyncMock()
    client.end_session = AsyncMock()
    client.update_session_status = AsyncMock()
    client.send_offer = AsyncMock(return_value=True)
    client.send_answer = AsyncMock(return_value=True)
    client.send_ice_candidate = AsyncMock(return_value=True)
    client.close = AsyncMock()
    return client


@pytest.fixture
def session_manager(mock_supabase_client):
    """Create a LiveSessionManager with mocked client."""
    return LiveSessionManager(supabase_client=mock_supabase_client)


class TestCreateSession:
    """Test session creation."""

    @pytest.mark.asyncio
    async def test_create_new_session(self, session_manager, mock_supabase_client):
        session = await session_manager.create_session(task_id=42, vnc_host="localhost", vnc_port=5900)

        assert session.id == "session-1"
        assert session.join_code == "ABC123"
        assert session.task_id == 42
        assert session.state == SessionState.CREATED
        mock_supabase_client.create_session.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_reuse_existing_session(self, session_manager, mock_supabase_client):
        mock_supabase_client.get_session_by_task.return_value = {
            "id": "existing-1",
            "join_code": "XYZ789",
            "mode": "p2p",
            "status": "created",
        }
        session = await session_manager.create_session(task_id=42)

        assert session.id == "existing-1"
        assert session.join_code == "XYZ789"
        mock_supabase_client.create_session.assert_not_awaited()


class TestEndSession:
    """Test session ending."""

    @pytest.mark.asyncio
    async def test_end_session_success(self, session_manager, mock_supabase_client):
        session = await session_manager.create_session(task_id=42)
        result = await session_manager.end_session(session.id)

        assert result is True
        mock_supabase_client.unsubscribe_from_session.assert_awaited_once_with("ABC123")
        mock_supabase_client.end_session.assert_awaited_once_with("session-1")

    @pytest.mark.asyncio
    async def test_end_nonexistent_session(self, session_manager):
        result = await session_manager.end_session("nonexistent")
        assert result is False


class TestStartStreaming:
    """Test streaming lifecycle."""

    @pytest.mark.asyncio
    async def test_start_streaming(self, session_manager, mock_supabase_client):
        session = await session_manager.create_session(task_id=42)
        result = await session_manager.start_streaming(session.id)

        assert result is True
        assert session_manager.get_session(session.id).state == SessionState.STREAMING
        mock_supabase_client.update_session_status.assert_awaited_once_with("session-1", "active")

    @pytest.mark.asyncio
    async def test_start_streaming_nonexistent(self, session_manager):
        result = await session_manager.start_streaming("nope")
        assert result is False

    @pytest.mark.asyncio
    async def test_pause_streaming(self, session_manager, mock_supabase_client):
        session = await session_manager.create_session(task_id=42)
        await session_manager.start_streaming(session.id)
        result = await session_manager.pause_streaming(session.id)

        assert result is True
        assert session_manager.get_session(session.id).state == SessionState.PAUSED


class TestSessionLookup:
    """Test session lookup methods."""

    @pytest.mark.asyncio
    async def test_get_session_by_task(self, session_manager):
        await session_manager.create_session(task_id=42)
        found = session_manager.get_session_by_task(42)

        assert found is not None
        assert found.task_id == 42

    @pytest.mark.asyncio
    async def test_get_session_by_task_not_found(self, session_manager):
        assert session_manager.get_session_by_task(999) is None

    def test_get_join_url(self, session_manager):
        session = LiveSession(
            id="s1", task_id=1, join_code="CODE1", mode="p2p",
        )
        url = session_manager.get_join_url(session, base_url="http://localhost:3000")
        assert url == "http://localhost:3000/live/CODE1"


class TestSignalHandling:
    """Test WebRTC signal message handling."""

    @pytest.mark.asyncio
    async def test_handle_join_signal(self, session_manager):
        session = await session_manager.create_session(task_id=42)

        callback = MagicMock()
        session_manager.on_peer_join(callback)

        msg = MagicMock(spec=RealtimeMessage)
        msg.payload = {"type": "join", "sender_id": "viewer-1"}

        session_manager._handle_signal_message(session.id, session.join_code, msg)

        callback.assert_called_once()
        assert session.viewer_count == 1

    @pytest.mark.asyncio
    async def test_handle_leave_signal(self, session_manager):
        session = await session_manager.create_session(task_id=42)

        # Simulate a join first
        join_msg = MagicMock(spec=RealtimeMessage)
        join_msg.payload = {"type": "join", "sender_id": "viewer-1"}
        session_manager._handle_signal_message(session.id, session.join_code, join_msg)

        # Now leave
        leave_msg = MagicMock(spec=RealtimeMessage)
        leave_msg.payload = {"type": "leave", "sender_id": "viewer-1"}
        session_manager._handle_signal_message(session.id, session.join_code, leave_msg)

        assert session.viewer_count == 0

    @pytest.mark.asyncio
    async def test_ignores_own_messages(self, session_manager):
        session = await session_manager.create_session(task_id=42)

        callback = MagicMock()
        session_manager.on_signal(callback)

        msg = MagicMock(spec=RealtimeMessage)
        msg.payload = {"type": "offer", "sender_id": "host", "sdp": "..."}

        session_manager._handle_signal_message(session.id, session.join_code, msg)

        callback.assert_not_called()

    @pytest.mark.asyncio
    async def test_async_signal_callback_is_awaited(self, session_manager):
        """An async on_signal callback must actually run (coroutine scheduled)."""
        session = await session_manager.create_session(task_id=42)

        ran = asyncio.Event()

        async def async_handler(session_id, signal_type, signal_data):
            ran.set()

        session_manager.on_signal(async_handler)

        msg = MagicMock(spec=RealtimeMessage)
        msg.payload = {"type": "offer", "sender_id": "viewer-1", "sdp": "..."}

        session_manager._handle_signal_message(session.id, session.join_code, msg)

        # Without the dispatch fix the coroutine is dropped and this times out.
        await asyncio.wait_for(ran.wait(), 1)
        assert ran.is_set()

    @pytest.mark.asyncio
    async def test_sync_signal_callback_still_called(self, session_manager):
        """A plain sync on_signal callback keeps being invoked directly."""
        session = await session_manager.create_session(task_id=42)

        callback = MagicMock()
        session_manager.on_signal(callback)

        msg = MagicMock(spec=RealtimeMessage)
        msg.payload = {"type": "offer", "sender_id": "viewer-1", "sdp": "..."}

        session_manager._handle_signal_message(session.id, session.join_code, msg)

        callback.assert_called_once()
