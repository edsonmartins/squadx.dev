"""Runtime adapter agent that runs an external coding-agent CLI in the sandbox.

Instead of SquadX's native LangGraph + LiteLLM loop, this agent shells out to a
frontier coding CLI inside the hardened sandbox. The CLI does its own
planning/editing; we stream its output as live progress and derive
``files_modified`` from git afterwards.

Harnesses are declarative: each provider maps to an argv template in
``BUILTIN_HARNESS_TEMPLATES`` (Claude Code, Codex, Gemini CLI, Aider, OpenCode).
A new CLI can be registered with *no code change* via a JSON command template in
``settings.external_cli_command_templates`` — see ``_resolve_template``.
"""

import shlex
from typing import TYPE_CHECKING, Any, Optional

import structlog

from squadx_client.agents.base import BaseAgent
from squadx_client.agents.security import enforce_prompt_security, filter_internal_artifacts
from squadx_client.config import settings

if TYPE_CHECKING:
    from squadx_client.docker.sandbox import AgentSandbox

logger = structlog.get_logger()

# Placeholder replaced with the task prompt when rendering a harness command.
# Kept as a standalone argv token so prompts containing spaces stay a single arg.
PROMPT_PLACEHOLDER = "{prompt}"

# Built-in harness command templates: provider -> argv template. A new provider is
# a new entry here — or, with zero code, a JSON entry in
# settings.external_cli_command_templates (SQUADX_EXTERNAL_CLI_COMMAND_TEMPLATES).
BUILTIN_HARNESS_TEMPLATES: dict[str, list[str]] = {
    # Headless, non-interactive run that auto-accepts edits.
    "CLAUDE_CODE": ["claude", "-p", PROMPT_PLACEHOLDER, "--permission-mode", "acceptEdits",
                    "--output-format", "text"],
    "CODEX": ["codex", "exec", "--full-auto", PROMPT_PLACEHOLDER],
    "GEMINI_CLI": ["gemini", "--yolo", "-p", PROMPT_PLACEHOLDER],
    # Aider is chat-style: --no-auto-commits leaves the commit to us (the
    # orchestrator's commit_changes merges the worktree branch); --yes-always
    # auto-accepts confirmations; --message runs the given task non-interactively.
    "AIDER": ["aider", "--no-auto-commits", "--yes-always", "--message", PROMPT_PLACEHOLDER],
    # OpenCode's `run` subcommand executes a single task headless and streams the
    # result to stdout. It picks up ANTHROPIC/OPENAI/GOOGLE_API_KEY from env.
    "OPENCODE": ["opencode", "run", PROMPT_PLACEHOLDER],
}

# The built-in providers. Configured generic templates extend this at runtime.
SUPPORTED_PROVIDERS = frozenset(BUILTIN_HARNESS_TEMPLATES)


def _configured_templates() -> dict[str, str]:
    """User-registered generic harness templates (provider -> shell-style string)."""
    return {
        str(k).upper(): v
        for k, v in (getattr(settings, "external_cli_command_templates", None) or {}).items()
    }


def _resolve_template(provider: str) -> list[str]:
    """Return the argv template for a provider (built-in first, then configured)."""
    if provider in BUILTIN_HARNESS_TEMPLATES:
        return BUILTIN_HARNESS_TEMPLATES[provider]
    configured = _configured_templates().get(provider)
    if configured:
        return shlex.split(configured) if isinstance(configured, str) else list(configured)
    raise ValueError(f"Unsupported CLI provider: {provider}")


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
        if self.provider not in SUPPORTED_PROVIDERS and self.provider not in _configured_templates():
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
        """Render the provider's argv template, substituting the task prompt.

        The prompt replaces ``{prompt}`` inside each token, so a prompt with
        spaces stays a single argv element (no shell splitting).
        """
        template = _resolve_template(self.provider)
        if not any(PROMPT_PLACEHOLDER in token for token in template):
            raise ValueError(
                f"CLI command template for {self.provider} has no {PROMPT_PLACEHOLDER} placeholder"
            )
        return [token.replace(PROMPT_PLACEHOLDER, prompt) for token in template]

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
        ``enforce`` raises and aborts the run. Shared with the native path via
        ``enforce_prompt_security``.
        """
        mode = getattr(settings, "cli_security_mode", "enforce")
        enforce_prompt_security(prompt, mode=mode, logger=self.logger)

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
