"""Tests for squadx_client.websocket.handlers module."""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from squadx_client.websocket.handlers import WebSocketHandler, TaskMessageHandler
from squadx_client.websocket.messages import MessageType


@pytest.fixture
def mock_daemon():
    """Create a mock daemon with all required async methods."""
    daemon = MagicMock()
    daemon._handle_task_assigned = AsyncMock()
    daemon._handle_task_cancelled = AsyncMock()
    daemon._handle_start_live_view = AsyncMock()
    daemon._handle_stop_live_view = AsyncMock()
    daemon._send_pong = AsyncMock()
    return daemon


@pytest.fixture
def handler(mock_daemon):
    """Create a WebSocketHandler with the mock daemon."""
    return WebSocketHandler(mock_daemon)


class TestMessageRouting:
    """Test that messages are routed to the correct handler."""

    async def test_routes_task_assigned(self, handler, mock_daemon):
        data = {"type": "task_assigned", "task_id": 1, "task": {"title": "Test"}}
        await handler.handle(data)
        mock_daemon._handle_task_assigned.assert_awaited_once_with(data)

    async def test_routes_task_cancelled(self, handler, mock_daemon):
        data = {"type": "task_cancelled", "task_id": 1, "reason": "Cancelled"}
        await handler.handle(data)
        mock_daemon._handle_task_cancelled.assert_awaited_once_with(data)

    async def test_routes_start_live_view(self, handler, mock_daemon):
        data = {"type": "start_live_view", "task_id": 1, "container_id": "abc"}
        await handler.handle(data)
        mock_daemon._handle_start_live_view.assert_awaited_once_with(data)

    async def test_routes_stop_live_view(self, handler, mock_daemon):
        data = {"type": "stop_live_view", "session_id": "xyz"}
        await handler.handle(data)
        mock_daemon._handle_stop_live_view.assert_awaited_once_with(data)

    async def test_routes_ping(self, handler, mock_daemon):
        data = {"type": "ping"}
        await handler.handle(data)
        mock_daemon._send_pong.assert_awaited_once()


class TestHandleTaskAssigned:
    """Test handle_task_assigned in detail."""

    async def test_delegates_to_daemon(self, handler, mock_daemon):
        data = {
            "type": "task_assigned",
            "task_id": 42,
            "task": {"title": "Build feature", "status": "IN_PROGRESS"},
        }
        await handler.handle_task_assigned(data)
        mock_daemon._handle_task_assigned.assert_awaited_once_with(data)


class TestHandleTaskCancelled:
    """Test handle_task_cancelled in detail."""

    async def test_delegates_to_daemon(self, handler, mock_daemon):
        data = {"type": "task_cancelled", "task_id": 42, "reason": "User request"}
        await handler.handle_task_cancelled(data)
        mock_daemon._handle_task_cancelled.assert_awaited_once_with(data)


class TestHandlePing:
    """Test handle_ping sends pong."""

    async def test_sends_pong(self, handler, mock_daemon):
        await handler.handle_ping({"type": "ping"})
        mock_daemon._send_pong.assert_awaited_once()


class TestHandleConfigUpdate:
    """Test handle_config_update modifies settings."""

    async def test_updates_max_concurrent_agents(self, handler):
        # handle_config_update imports settings from squadx_client.config locally.
        with patch("squadx_client.config.settings") as mock_settings:
            data = {"type": "config_update", "config": {"max_concurrent_agents": 8}}
            await handler.handle_config_update(data)
            assert mock_settings.max_concurrent_agents == 8

    async def test_updates_multiple_fields(self, handler):
        data = {
            "type": "config_update",
            "config": {
                "max_concurrent_agents": 10,
                "default_model": "claude-3-opus-20240229",
                "log_level": "DEBUG",
            },
        }
        # Should not raise
        await handler.handle_config_update(data)

    async def test_ignores_unknown_fields(self, handler):
        data = {"type": "config_update", "config": {"nonexistent_field": "value"}}
        # Should not raise
        await handler.handle_config_update(data)


class TestUnknownMessageType:
    """Test handling of unknown message types."""

    async def test_unknown_type_does_not_crash(self, handler, mock_daemon):
        data = {"type": "totally_unknown_type", "foo": "bar"}
        # Should not raise
        await handler.handle(data)

    async def test_missing_type_does_not_crash(self, handler, mock_daemon):
        data = {"foo": "bar"}
        await handler.handle(data)


class TestTaskMessageHandler:
    """Test TaskMessageHandler callable wrapper."""

    async def test_delegates_to_websocket_handler(self, mock_daemon):
        task_handler = TaskMessageHandler(mock_daemon)
        data = {"type": "task_assigned", "task_id": 1, "task": {"title": "Test"}}
        await task_handler(data)
        mock_daemon._handle_task_assigned.assert_awaited_once_with(data)
