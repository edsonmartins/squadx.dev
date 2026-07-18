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

    def test_build_command_keeps_prompt_with_spaces_as_single_arg(self):
        """A prompt with spaces stays one argv token (no shell splitting)."""
        cmd = ExternalCliAgent(provider="CLAUDE_CODE")._build_command("fix the login bug")
        assert "fix the login bug" in cmd

    def test_generic_template_registers_provider_without_code(self, monkeypatch):
        """A provider configured via settings needs no code change."""
        from squadx_client.agents import external_cli_agent as mod

        monkeypatch.setattr(
            mod.settings,
            "external_cli_command_templates",
            {"MYCLI": "mycli run --task {prompt}"},
            raising=False,
        )
        agent = ExternalCliAgent(provider="mycli")  # case-insensitive
        assert agent.provider == "MYCLI"
        cmd = agent._build_command("hello world")
        assert cmd == ["mycli", "run", "--task", "hello world"]

    def test_generic_template_without_placeholder_raises(self, monkeypatch):
        from squadx_client.agents import external_cli_agent as mod

        monkeypatch.setattr(
            mod.settings,
            "external_cli_command_templates",
            {"NOPROMPT": "noprompt run"},
            raising=False,
        )
        agent = ExternalCliAgent(provider="NOPROMPT")
        with pytest.raises(ValueError, match="no .*placeholder"):
            agent._build_command("x")

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


class TestUsageReporting:
    """EXTERNAL_CLI runs must bill for real, not always report zero cost."""

    def test_claude_code_uses_json_output_format(self):
        # --output-format json is what makes Claude Code emit total_cost_usd + usage.
        cmd = ExternalCliAgent(provider="CLAUDE_CODE")._build_command("x")
        assert "json" in cmd
        assert "text" not in cmd

    def test_extract_usage_parses_claude_json(self):
        agent = ExternalCliAgent(provider="CLAUDE_CODE")
        blob = (
            '{"result": "the answer", "total_cost_usd": 0.0234, '
            '"usage": {"input_tokens": 1200, "output_tokens": 340}}'
        )
        text, usage = agent._extract_usage(blob)
        assert text == "the answer"
        assert usage == {"input_tokens": 1200, "output_tokens": 340, "cost": 0.0234}

    def test_extract_usage_non_json_falls_back_to_zero(self):
        # A schema change / plain text must never break the run.
        agent = ExternalCliAgent(provider="CLAUDE_CODE")
        text, usage = agent._extract_usage("not json at all")
        assert text == "not json at all"
        assert usage == {"input_tokens": 0, "output_tokens": 0, "cost": 0.0}

    def test_extract_usage_other_provider_is_zero(self):
        # Codex/Gemini/etc. have no machine-readable usage — stay at zero.
        agent = ExternalCliAgent(provider="CODEX")
        text, usage = agent._extract_usage('{"total_cost_usd": 9.9}')
        assert text == '{"total_cost_usd": 9.9}'
        assert usage["cost"] == 0.0

    @pytest.mark.asyncio
    async def test_execute_reports_claude_usage(self):
        blob = (
            '{"result": "done", "total_cost_usd": 0.05, '
            '"usage": {"input_tokens": 500, "output_tokens": 200}}'
        )
        sandbox = _make_sandbox(SandboxResult(success=True, exit_code=0, output=blob))
        agent = ExternalCliAgent(provider="CLAUDE_CODE", sandbox=sandbox)

        result = await agent.execute("Build feature", "Implement X")

        assert result["output"] == "done"          # parsed text, not the raw JSON
        assert result["cost"] == 0.05
        assert result["input_tokens"] == 500
        assert result["output_tokens"] == 200
