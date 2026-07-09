"""Runtime adapter agent that runs an external coding-agent CLI in the sandbox.

Instead of SquadX's native LangGraph + LiteLLM loop, this agent shells out to a
frontier coding CLI (Claude Code, Codex, or Gemini CLI) inside the hardened
sandbox. The CLI does its own planning/editing; we stream its output as live
progress and derive ``files_modified`` from git afterwards.
"""

from typing import TYPE_CHECKING, Any, Optional

import structlog

from squadx_client.agents.base import BaseAgent
from squadx_client.agents.security import assess_prompt, filter_internal_artifacts
from squadx_client.config import settings

if TYPE_CHECKING:
    from squadx_client.docker.sandbox import AgentSandbox

logger = structlog.get_logger()

# Supported providers and the CLI binary name we expect on the sandbox PATH.
SUPPORTED_PROVIDERS = {"CLAUDE_CODE", "CODEX", "GEMINI_CLI", "AIDER", "OPENCODE"}


class ExternalCliAgent(BaseAgent):
    """Executes a task by driving an external CLI inside the sandbox."""

    agent_type = "external_cli"

    def __init__(
        self,
        provider: str = "CLAUDE_CODE",
        sandbox: Optional["AgentSandbox"] = None,
        brainsentry_session_id: str | None = None,
    ):
        # Intentionally does NOT call super().__init__: we don't need the
        # native LLM/tool wiring or memory plumbing — the external CLI owns the
        # full agentic loop. We only need the sandbox.
        self.provider = (provider or "CLAUDE_CODE").upper()
        if self.provider not in SUPPORTED_PROVIDERS:
            raise ValueError(f"Unsupported CLI provider: {self.provider}")
        self.sandbox = sandbox
        self.brainsentry_session_id = brainsentry_session_id
        self.tools = []
        self.logger = structlog.get_logger().bind(
            agent_type=self.agent_type, provider=self.provider
        )

    def get_system_prompt(self) -> str:
        # External CLIs carry their own system prompt.
        return ""

    def _build_prompt(
        self, task_title: str, task_description: str, context: dict[str, Any] | None
    ) -> str:
        parts = [f"# Task: {task_title}", "", task_description or ""]
        packet = (context or {}).get("context_packet")
        rendered = packet.render() if packet is not None and hasattr(packet, "render") else ""
        if rendered:
            parts += ["", "## SquadX context packet", rendered]
        else:
            main_task = (context or {}).get("main_task")
            if isinstance(main_task, dict) and main_task.get("description"):
                parts += ["", "## Project context", str(main_task["description"])]
        parts += [
            "",
            "Work in the current directory (/workspace). Make the changes "
            "directly to the files. When finished, summarize what you did.",
        ]
        return "\n".join(parts)

    def _build_command(self, prompt: str) -> list[str]:
        if self.provider == "CLAUDE_CODE":
            # Headless, non-interactive run that auto-accepts edits.
            return [
                "claude",
                "-p",
                prompt,
                "--permission-mode",
                "acceptEdits",
                "--output-format",
                "text",
            ]
        if self.provider == "CODEX":
            return ["codex", "exec", "--full-auto", prompt]
        if self.provider == "GEMINI_CLI":
            return ["gemini", "--yolo", "-p", prompt]
        if self.provider == "AIDER":
            # Aider is chat-style: --no-auto-commits leaves the commit to us
            # (the orchestrator's commit_changes merges the worktree branch);
            # --yes-always auto-accepts confirmations; --message runs the
            # given task non-interactively.
            return [
                "aider",
                "--no-auto-commits",
                "--yes-always",
                "--message",
                prompt,
            ]
        if self.provider == "OPENCODE":
            # OpenCode's `run` subcommand executes a single task headless and
            # streams the result to stdout. It picks up ANTHROPIC_API_KEY /
            # OPENAI_API_KEY / GOOGLE_API_KEY from env.
            return ["opencode", "run", prompt]
        raise ValueError(f"Unsupported CLI provider: {self.provider}")

    async def execute(
        self,
        task_title: str,
        task_description: str,
        context: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        if not self.sandbox:
            raise ValueError("ExternalCliAgent requires a sandbox to run the CLI")

        context = context or {}
        progress_callback = context.get("progress_callback")
        prompt = self._build_prompt(task_title, task_description, context)
        self._assess_prompt_security(prompt)
        command = self._build_command(prompt)
        timeout = float(
            context.get("cli_timeout")
            or getattr(settings, "external_cli_timeout_seconds", 1800)
        )

        self.logger.info("external_cli_start", title=task_title, timeout=timeout)

        def _on_output(chunk: str) -> None:
            if progress_callback:
                try:
                    progress_callback(chunk)
                except Exception as exc:  # noqa: BLE001
                    self.logger.error("progress_callback_error", error=str(exc))

        result = await self.sandbox.execute_streaming(
            command, on_output=_on_output, timeout=timeout
        )

        files_modified = await self._collect_changed_files()

        if not result.success:
            tail = (result.output or "")[-2000:]
            raise RuntimeError(
                f"{self.provider} CLI failed (exit {result.exit_code}): "
                f"{result.error or tail}"
            )

        self.logger.info(
            "external_cli_done",
            exit_code=result.exit_code,
            files=len(files_modified),
        )

        return {
            "output": result.output,
            "files_modified": files_modified,
            # External CLIs don't report token usage back to us.
            "input_tokens": 0,
            "output_tokens": 0,
            "cost": 0.0,
            "tool_calls": 0,
        }

    def _assess_prompt_security(self, prompt: str) -> None:
        """Scan the prompt for injection/exfiltration patterns (ADR-0007).

        Mode comes from ``settings.cli_security_mode``: ``off`` skips; ``audit`` logs findings;
        ``enforce`` raises and aborts the run.
        """
        mode = getattr(settings, "cli_security_mode", "audit")
        if mode == "off":
            return
        findings = assess_prompt(prompt)
        if not findings:
            return
        codes = [f.code for f in findings]
        self.logger.warning("prompt_security_findings", mode=mode, findings=codes)
        if mode == "enforce" and any(f.severity == "block" for f in findings):
            raise RuntimeError("Prompt blocked by security policy: " + ", ".join(codes))

    async def _collect_changed_files(self) -> list[str]:
        """Derive the list of changed files from git working-tree status."""
        try:
            # core.quotepath=false keeps non-ASCII paths unquoted; we still strip
            # the quotes git adds for paths containing spaces/special chars.
            status = await self.sandbox.execute(
                ["git", "-c", "core.quotepath=false", "status", "--porcelain"],
                timeout=30,
            )
        except Exception as exc:  # noqa: BLE001
            self.logger.warning("git_status_failed", error=str(exc))
            return []

        if not status.success or not status.output:
            return []

        files: list[str] = []
        for line in status.output.splitlines():
            if not line.strip():
                continue
            # Porcelain v1: 2-char status (XY) + space + path.
            path = line[3:] if len(line) > 3 else line.strip()
            if " -> " in path:  # rename/copy: "old -> new"
                path = path.split(" -> ", 1)[1]
            path = path.strip()
            if len(path) >= 2 and path.startswith('"') and path.endswith('"'):
                path = path[1:-1]  # unquote git-quoted path
            if path:
                files.append(path)
        # Internal agent-CLI artifacts (.claude/.codex/.omx) must not be committed (ADR-0007).
        return filter_internal_artifacts(files)
