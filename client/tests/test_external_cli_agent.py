"""Tests for the external CLI runtime adapter agent."""

from unittest.mock import AsyncMock, MagicMock

import pytest

from squadx_client.agents.external_cli_agent import ExternalCliAgent
from squadx_client.agents.factory import create_agent
from squadx_client.docker.sandbox import SandboxResult


def _make_sandbox(stream_result, status_output=" M src/app.py\n?? new.txt\n"):
    sandbox = MagicMock()
    sandbox.execute_streaming = AsyncMock(return_value=stream_result)
    sandbox.execute = AsyncMock(
        return_value=SandboxResult(success=True, exit_code=0, output=status_output)
    )
    return sandbox


class TestFactoryRouting:
    def test_factory_routes_to_external_cli(self):
        agent = create_agent(
            "external_cli", runtime_kind="EXTERNAL_CLI", cli_provider="CLAUDE_CODE"
        )
        assert isinstance(agent, ExternalCliAgent)
        assert agent.provider == "CLAUDE_CODE"

    def test_native_runtime_still_returns_specialist(self):
        from unittest.mock import patch

        from squadx_client.agents.factory import BackendAgent

        with patch("squadx_client.agents.base.get_coding_llm", return_value=MagicMock()):
            agent = create_agent("backend", runtime_kind="NATIVE")
        assert isinstance(agent, BackendAgent)


class TestExternalCliAgent:
    def test_unsupported_provider_raises(self):
        with pytest.raises(ValueError, match="Unsupported CLI provider"):
            ExternalCliAgent(provider="FOO")

    def test_build_command_claude_code(self):
        agent = ExternalCliAgent(provider="CLAUDE_CODE")
        cmd = agent._build_command("do the thing")
        assert cmd[0] == "claude"
        assert "do the thing" in cmd

    def test_build_command_per_provider(self):
        assert ExternalCliAgent(provider="CODEX")._build_command("x")[0] == "codex"
        assert ExternalCliAgent(provider="GEMINI_CLI")._build_command("x")[0] == "gemini"
        assert ExternalCliAgent(provider="AIDER")._build_command("x")[0] == "aider"
        assert ExternalCliAgent(provider="OPENCODE")._build_command("x")[0] == "opencode"

    def test_build_command_aider_passes_through_flags(self):
        """Aider headless: --no-auto-commits + --yes-always + --message."""
        cmd = ExternalCliAgent(provider="AIDER")._build_command("hello")
        # Must include all three flags; order matches the implementation.
        assert "--no-auto-commits" in cmd
        assert "--yes-always" in cmd
        assert "--message" in cmd
        # The prompt is the last positional arg
        assert cmd[-1] == "hello"

    def test_build_command_opencode_uses_run_subcommand(self):
        """OpenCode's `run` subcommand is the headless entry point."""
        cmd = ExternalCliAgent(provider="OPENCODE")._build_command("hello")
        assert cmd[0] == "opencode"
        assert cmd[1] == "run"
        assert cmd[2] == "hello"

    @pytest.mark.asyncio
    async def test_requires_sandbox(self):
        agent = ExternalCliAgent(provider="CLAUDE_CODE")
        with pytest.raises(ValueError, match="requires a sandbox"):
            await agent.execute("title", "desc")

    @pytest.mark.asyncio
    async def test_execute_runs_cli_and_collects_changed_files(self):
        sandbox = _make_sandbox(
            SandboxResult(success=True, exit_code=0, output="all done")
        )
        agent = ExternalCliAgent(provider="CLAUDE_CODE", sandbox=sandbox)

        result = await agent.execute("Build feature", "Implement X")

        assert result["output"] == "all done"
        assert "src/app.py" in result["files_modified"]
        assert "new.txt" in result["files_modified"]
        sandbox.execute_streaming.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_execute_raises_on_cli_failure(self):
        sandbox = _make_sandbox(
            SandboxResult(success=False, exit_code=2, output="boom", error="failed")
        )
        agent = ExternalCliAgent(provider="CLAUDE_CODE", sandbox=sandbox)

        with pytest.raises(RuntimeError, match="CLAUDE_CODE CLI failed"):
            await agent.execute("title", "desc")

    @pytest.mark.asyncio
    async def test_progress_callback_forwarded(self):
        async def fake_stream(command, on_output=None, timeout=0):
            if on_output:
                on_output("compiling...\nwriting files")
            return SandboxResult(success=True, exit_code=0, output="ok")

        sandbox = MagicMock()
        sandbox.execute_streaming = AsyncMock(side_effect=fake_stream)
        sandbox.execute = AsyncMock(
            return_value=SandboxResult(success=True, exit_code=0, output="")
        )
        seen: list[str] = []
        agent = ExternalCliAgent(provider="CLAUDE_CODE", sandbox=sandbox)

        await agent.execute(
            "t", "d", context={"progress_callback": lambda c: seen.append(c)}
        )

        assert seen == ["compiling...\nwriting files"]
