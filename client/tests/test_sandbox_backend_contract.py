"""ADR-0009 Phase 0: SandboxBackend contract, factory, and feature matrix."""

from __future__ import annotations

import pytest

from squadx_client.sandbox import (
    BackendFeatures,
    SandboxBackend,
    SandboxBackendKind,
    SandboxHandle,
    SandboxLifecycleStatus,
    features_for,
    get_sandbox_backend,
    get_sandbox_backend_kind,
    parse_backend_kind,
)
from squadx_client.sandbox.types import ExecResult


def test_parse_backend_kind_defaults_and_aliases() -> None:
    assert parse_backend_kind(None) is SandboxBackendKind.DOCKER
    assert parse_backend_kind("") is SandboxBackendKind.DOCKER
    assert parse_backend_kind("docker") is SandboxBackendKind.DOCKER
    assert parse_backend_kind("DOCKER") is SandboxBackendKind.DOCKER
    assert parse_backend_kind("container") is SandboxBackendKind.DOCKER
    assert parse_backend_kind("process") is SandboxBackendKind.PROCESS
    assert parse_backend_kind("bwrap") is SandboxBackendKind.PROCESS
    assert parse_backend_kind("firecracker") is SandboxBackendKind.FIRECRACKER
    assert parse_backend_kind("remote") is SandboxBackendKind.REMOTE


def test_parse_backend_kind_unknown() -> None:
    with pytest.raises(ValueError, match="unknown"):
        parse_backend_kind("wasm")


def test_get_sandbox_backend_kind_default_docker() -> None:
    assert get_sandbox_backend_kind() is SandboxBackendKind.DOCKER


def test_features_docker_implemented() -> None:
    feats = features_for(SandboxBackendKind.DOCKER)
    assert isinstance(feats, BackendFeatures)
    assert feats.implemented is True
    assert feats.live_view is True
    assert feats.egress_sidecar is True
    assert feats.external_cli is True


def test_features_process_implemented_no_live() -> None:
    feats = features_for(SandboxBackendKind.PROCESS)
    assert feats.implemented is True
    assert feats.live_view is False
    assert feats.egress_sidecar is False
    assert feats.external_cli is False


def test_get_sandbox_backend_returns_docker_backend() -> None:
    from squadx_client.sandbox.docker_backend import DockerSandboxBackend

    backend = get_sandbox_backend()
    assert isinstance(backend, DockerSandboxBackend)
    assert backend.kind is SandboxBackendKind.DOCKER
    assert backend.supports_live_view() is True
    assert backend.supports_egress_sidecar() is True
    assert isinstance(backend, SandboxBackend)


def test_create_agent_sandbox_builds_agent_sandbox() -> None:
    from squadx_client.docker.sandbox import AgentSandbox
    from squadx_client.sandbox import create_agent_sandbox

    sb = create_agent_sandbox(
        task_id=42,
        agent_type="coder",
        workspace_path="/tmp/ws",
        network_policy="agent-default",
    )
    assert isinstance(sb, AgentSandbox)
    assert sb.task_id == 42
    assert sb.agent_type == "coder"
    assert sb.workspace_path == "/tmp/ws"


def test_get_sandbox_backend_process_returns_process_backend(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        "squadx_client.config.settings.sandbox_backend",
        "process",
    )
    monkeypatch.setenv("SQUADX_PROCESS_UNSAFE", "1")
    from squadx_client.sandbox.process_backend import ProcessSandboxBackend

    backend = get_sandbox_backend()
    assert isinstance(backend, ProcessSandboxBackend)
    assert backend.kind is SandboxBackendKind.PROCESS
    assert backend.supports_live_view() is False

def test_sandbox_backend_is_runtime_checkable_protocol() -> None:
    """A minimal duck type satisfies the Protocol for structural typing tests."""

    class _Stub:
        @property
        def kind(self) -> SandboxBackendKind:
            return SandboxBackendKind.DOCKER

        def create_session(self, **kwargs):  # noqa: ANN003
            return object()

        def supports_live_view(self) -> bool:
            return True

        def supports_egress_sidecar(self) -> bool:
            return True

        async def start(self, **kwargs):  # noqa: ANN003
            return SandboxHandle(
                task_id=1,
                backend=SandboxBackendKind.DOCKER,
                id="stub",
                workspace_path="/tmp",
                status=SandboxLifecycleStatus.RUNNING,
            )

        async def exec(self, handle, command, **kwargs):  # noqa: ANN001, ANN003
            return ExecResult(success=True, exit_code=0, output="")

        async def exec_streaming(self, handle, command, **kwargs):  # noqa: ANN001, ANN003
            return ExecResult(success=True, exit_code=0, output="")

        async def write_file(self, handle, path, content):  # noqa: ANN001
            return True

        async def read_file(self, handle, path):  # noqa: ANN001
            return None

        async def list_dir(self, handle, path):  # noqa: ANN001
            return []

        async def stop(self, handle, *, timeout=10):  # noqa: ANN001
            return True

        async def cleanup(self, handle):  # noqa: ANN001
            return True

        def get_metrics(self, handle):  # noqa: ANN001
            return None

        async def status(self, handle):  # noqa: ANN001
            return SandboxLifecycleStatus.RUNNING

    assert isinstance(_Stub(), SandboxBackend)


def test_create_sandbox_session_is_sandbox_session() -> None:
    from squadx_client.sandbox import SandboxSession, create_sandbox_session

    sb = create_sandbox_session(
        task_id=1, agent_type="coder", workspace_path="/tmp/ws"
    )
    assert isinstance(sb, SandboxSession)


def test_doctor_reports_sandbox_backend() -> None:
    from squadx_client.cli.doctor import CheckStatus, run_doctor

    report = run_doctor(skip_docker=True, skip_api=True)
    backend = next(c for c in report.checks if c.name == "sandbox.backend")
    assert backend.status is CheckStatus.OK
    assert "docker" in backend.detail
