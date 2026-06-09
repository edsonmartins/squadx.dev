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


def _ctx(resp):
    ctx = MagicMock()
    ctx.__aenter__ = AsyncMock(return_value=resp)
    ctx.__aexit__ = AsyncMock(return_value=False)
    return ctx


def _resp(payload, status=200):
    r = MagicMock()
    r.status = status
    r.json = AsyncMock(return_value=payload)
    return r


def _mock_session(get_payload, get_status=200, claim_result=True, claim_status=200):
    session = MagicMock()
    session.get = MagicMock(return_value=_ctx(_resp(get_payload, get_status)))
    session.post = MagicMock(return_value=_ctx(_resp({"data": claim_result}, claim_status)))

    session_ctx = MagicMock()
    session_ctx.__aenter__ = AsyncMock(return_value=session)
    session_ctx.__aexit__ = AsyncMock(return_value=False)
    return session_ctx


class TestPollFallback:
    @pytest.mark.asyncio
    async def test_processes_claimed_new_task_and_skips_in_flight(self, daemon):
        daemon._handle_task_assigned = AsyncMock()
        daemon.current_tasks = {7: MagicMock()}  # task 7 already running

        payload = {
            "data": [
                {"task_id": 7, "task": {"execution_id": 70}},  # skipped (in flight)
                {"task_id": 8, "task": {"execution_id": 80}},  # claimed -> processed
            ]
        }

        with patch(
            "squadx_client.daemon.aiohttp.ClientSession",
            return_value=_mock_session(payload, claim_result=True),
        ):
            await daemon._poll_pending_once()

        daemon._handle_task_assigned.assert_awaited_once()
        claimed = daemon._handle_task_assigned.await_args.args[0]
        assert claimed["task_id"] == 8

    @pytest.mark.asyncio
    async def test_skips_when_claim_lost(self, daemon):
        daemon._handle_task_assigned = AsyncMock()
        payload = {"data": [{"task_id": 8, "task": {"execution_id": 80}}]}

        with patch(
            "squadx_client.daemon.aiohttp.ClientSession",
            return_value=_mock_session(payload, claim_result=False),
        ):
            await daemon._poll_pending_once()

        daemon._handle_task_assigned.assert_not_awaited()

    @pytest.mark.asyncio
    async def test_http_error_is_swallowed(self, daemon):
        daemon._handle_task_assigned = AsyncMock()

        with patch(
            "squadx_client.daemon.aiohttp.ClientSession",
            return_value=_mock_session({}, get_status=503),
        ):
            await daemon._poll_pending_once()

        daemon._handle_task_assigned.assert_not_awaited()
