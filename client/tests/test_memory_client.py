"""Tests for BrainSentry client graceful degradation behavior."""

from unittest.mock import AsyncMock, patch

import pytest

from squadx_client.memory.client import BrainSentryClient


class TestBrainSentryClientFallbacks:
    """Verify BrainSentry client fails open when the service is unavailable."""

    @pytest.mark.asyncio
    async def test_intercept_prompt_returns_original_on_failure(self):
        client = BrainSentryClient(base_url="http://brainsentry.local", api_key="token")

        with patch.object(client, "_request_with_retry", AsyncMock(side_effect=RuntimeError("unavailable"))):
            enriched = await client.intercept_prompt("hello world", session_id="s1", user_id="u1")

        assert enriched == "hello world"

    @pytest.mark.asyncio
    async def test_start_session_returns_none_on_failure(self):
        client = BrainSentryClient(base_url="http://brainsentry.local", api_key="token")

        with patch.object(client, "_request_with_retry", AsyncMock(side_effect=RuntimeError("unavailable"))):
            session_id = await client.start_session("123", task_id="10", agent_id="7")

        assert session_id is None

    @pytest.mark.asyncio
    async def test_end_session_swallows_failures(self):
        client = BrainSentryClient(base_url="http://brainsentry.local", api_key="token")

        with patch.object(client, "_request_with_retry", AsyncMock(side_effect=RuntimeError("unavailable"))):
            await client.end_session("bs-session-1", status="completed", summary="done")

    @pytest.mark.asyncio
    async def test_search_memories_returns_empty_list_on_failure(self):
        client = BrainSentryClient(base_url="http://brainsentry.local", api_key="token")

        with patch.object(client, "_request_with_retry", AsyncMock(side_effect=RuntimeError("unavailable"))):
            memories = await client.search_memories("task context", limit=5)

        assert memories == []
