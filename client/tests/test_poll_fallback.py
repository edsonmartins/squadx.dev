"""Tests for the daemon's HTTP polling fallback."""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from squadx_client.daemon import SquadXDaemon


@pytest.fixture
def daemon():
    with patch("squadx_client.daemon.StompClientManager") as mock_stomp_cls, \
         patch("squadx_client.daemon.create_orchestrator") as mock_create_orchestrator:
        mock_stomp_cls.return_value = MagicMock()
        mock_create_orchestrator.return_value = MagicMock()
        return SquadXDaemon(api_url="http://localhost:8080", token="test-token")


def _mock_session(payload, status=200):
    resp = MagicMock()
    resp.status = status
    resp.json = AsyncMock(return_value=payload)

    get_ctx = MagicMock()
    get_ctx.__aenter__ = AsyncMock(return_value=resp)
    get_ctx.__aexit__ = AsyncMock(return_value=False)

    session = MagicMock()
    session.get = MagicMock(return_value=get_ctx)

    session_ctx = MagicMock()
    session_ctx.__aenter__ = AsyncMock(return_value=session)
    session_ctx.__aexit__ = AsyncMock(return_value=False)
    return session_ctx


class TestPollFallback:
    @pytest.mark.asyncio
    async def test_processes_new_tasks_and_skips_in_flight(self, daemon):
        daemon._handle_task_assigned = AsyncMock()
        daemon.current_tasks = {7: MagicMock()}  # task 7 already running

        payload = {
            "data": [
                {"task_id": 7, "task": {"title": "x"}},  # skipped
                {"task_id": 8, "task": {"title": "y"}},  # processed
            ]
        }

        with patch(
            "squadx_client.daemon.aiohttp.ClientSession",
            return_value=_mock_session(payload),
        ):
            await daemon._poll_pending_once()

        daemon._handle_task_assigned.assert_awaited_once()
        claimed = daemon._handle_task_assigned.await_args.args[0]
        assert claimed["task_id"] == 8

    @pytest.mark.asyncio
    async def test_http_error_is_swallowed(self, daemon):
        daemon._handle_task_assigned = AsyncMock()

        with patch(
            "squadx_client.daemon.aiohttp.ClientSession",
            return_value=_mock_session({}, status=503),
        ):
            await daemon._poll_pending_once()

        daemon._handle_task_assigned.assert_not_awaited()
