"""Tests for ProcessSandboxBackend (ADR-0009 Phase 4)."""

from __future__ import annotations

import os
from pathlib import Path

import pytest

from squadx_client.sandbox.errors import SandboxNotSupportedError
from squadx_client.sandbox.process_backend import (
    ProcessIsolator,
    ProcessSandboxBackend,
    build_isolated_command,
    inject_bwrap_env,
)
from squadx_client.sandbox.types import SandboxBackendKind, SandboxLifecycleStatus


def test_build_bwrap_command_binds_workspace(tmp_path: Path) -> None:
    cmd = build_isolated_command(
        isolator=ProcessIsolator.BWRAP,
        workspace=tmp_path,
        command=["echo", "hi"],
        workdir="/workspace",
    )
    assert cmd[0] == "bwrap"
    assert "--bind" in cmd
    assert str(tmp_path) in cmd
    assert "/workspace" in cmd
    assert cmd[-2:] == ["echo", "hi"] or cmd[cmd.index("--") + 1 :] == ["echo", "hi"]


def test_inject_bwrap_env() -> None:
    argv = ["bwrap", "--clearenv", "--", "echo", "x"]
    out = inject_bwrap_env(argv, {"OPENAI_API_KEY": "sk-test"})
    assert "--setenv" in out
    assert "OPENAI_API_KEY" in out
    assert "sk-test" in out
    assert out.index("--setenv") < out.index("--")


def test_build_none_isolator_uses_bash_cd(tmp_path: Path) -> None:
    cmd = build_isolated_command(
        isolator=ProcessIsolator.NONE,
        workspace=tmp_path,
        command=["ls", "-la"],
    )
    assert cmd[0] == "bash"
    assert "-c" in cmd


@pytest.mark.asyncio
async def test_process_session_start_exec_cleanup(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("SQUADX_PROCESS_UNSAFE", "1")
    backend = ProcessSandboxBackend(isolator=ProcessIsolator.NONE)
    session = backend.create_session(
        task_id=1,
        agent_type="coder",
        workspace_path=str(tmp_path),
    )
    assert await session.start() is True
    result = await session.execute(["bash", "-c", "echo hello-process"])
    assert result.success is True
    assert "hello-process" in result.output

    await session.write_file("/workspace/note.txt", "abc")
    content = await session.read_file("/workspace/note.txt")
    assert content == "abc"
    assert (tmp_path / "note.txt").read_text() == "abc"

    handle = backend.register_session(session)
    assert handle.backend is SandboxBackendKind.PROCESS
    assert await backend.status(handle) is SandboxLifecycleStatus.RUNNING
    assert await backend.cleanup(handle) is True


@pytest.mark.asyncio
async def test_process_backend_protocol_start(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("SQUADX_PROCESS_UNSAFE", "1")
    backend = ProcessSandboxBackend(isolator=ProcessIsolator.NONE)
    handle = await backend.start(
        task_id=9,
        agent_type="coder",
        workspace_path=str(tmp_path),
        exec_env={"FOO": "bar"},
    )
    result = await backend.exec(handle, ["bash", "-c", "echo $FOO"])
    # With NONE isolator, env is passed via Popen env
    assert result.success is True
    assert "bar" in result.output
    await backend.cleanup(handle)


def test_create_sandbox_session_process(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("SQUADX_PROCESS_UNSAFE", "1")
    monkeypatch.setattr(
        "squadx_client.config.settings.sandbox_backend",
        "process",
    )
    from squadx_client.sandbox import create_sandbox_session
    from squadx_client.sandbox.process_backend import ProcessSession

    session = create_sandbox_session(
        task_id=3,
        agent_type="coder",
        workspace_path=str(tmp_path),
        enable_live_streaming=False,
    )
    assert isinstance(session, ProcessSession)


def test_create_agent_sandbox_rejects_process(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(
        "squadx_client.config.settings.sandbox_backend",
        "process",
    )
    from squadx_client.sandbox import create_agent_sandbox

    with pytest.raises(SandboxNotSupportedError, match="docker"):
        create_agent_sandbox(task_id=1, agent_type="x", workspace_path="/tmp")


def test_features_process_implemented() -> None:
    from squadx_client.sandbox import SandboxBackendKind, features_for

    feats = features_for(SandboxBackendKind.PROCESS)
    assert feats.implemented is True
    assert feats.live_view is False
    assert feats.external_cli is False


@pytest.mark.sandbox_process
@pytest.mark.skipif(not os.path.exists("/usr/bin/bwrap") and not os.path.exists("/usr/bin/sandbox-exec"), reason="no isolator")
@pytest.mark.asyncio
async def test_real_isolator_echo(tmp_path: Path) -> None:
    """Optional integration: real bwrap or sandbox-exec on the host."""
    backend = ProcessSandboxBackend()  # auto-detect
    handle = await backend.start(
        task_id=1,
        agent_type="coder",
        workspace_path=str(tmp_path),
    )
    result = await backend.exec(handle, ["bash", "-c", "echo isolated && pwd"])
    assert result.success is True
    assert "isolated" in result.output
    await backend.cleanup(handle)
