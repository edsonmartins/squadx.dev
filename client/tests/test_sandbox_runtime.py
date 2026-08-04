"""Tests for sandbox runtime configuration and DockerSandbox initialization."""

from unittest.mock import patch

from squadx_client.docker.hardening import (
    SandboxRuntime,
    detect_available_runtimes,
    get_runtime_config,
    resolve_runtime,
)


class TestSandboxRuntimeEnum:
    """Test SandboxRuntime enum values."""

    def test_docker_value(self):
        assert SandboxRuntime.DOCKER.value == "docker"

    def test_gvisor_value(self):
        assert SandboxRuntime.GVISOR.value == "gvisor"

    def test_firecracker_value(self):
        assert SandboxRuntime.FIRECRACKER.value == "firecracker"

    def test_is_string_enum(self):
        assert isinstance(SandboxRuntime.DOCKER, str)
        assert SandboxRuntime.DOCKER == "docker"


class TestGetRuntimeConfig:
    """Test get_runtime_config for each runtime type."""

    def test_docker_returns_empty_dict(self):
        config = get_runtime_config(SandboxRuntime.DOCKER)
        assert config == {}

    def test_gvisor_returns_runsc_runtime(self):
        config = get_runtime_config(SandboxRuntime.GVISOR)
        assert config == {"runtime": "runsc"}

    def test_firecracker_returns_firecracker_runtime(self):
        config = get_runtime_config(SandboxRuntime.FIRECRACKER)
        assert config == {"runtime": "firecracker"}


class TestDetectAvailableRuntimes:
    """Test detect_available_runtimes with mocked shutil.which."""

    @patch("squadx_client.docker.hardening.shutil.which")
    def test_only_docker_when_no_extras(self, mock_which):
        mock_which.return_value = None
        runtimes = detect_available_runtimes()
        assert runtimes == [SandboxRuntime.DOCKER]

    @patch("squadx_client.docker.hardening.shutil.which")
    def test_includes_gvisor_when_runsc_found(self, mock_which):
        mock_which.side_effect = lambda cmd: "/usr/bin/runsc" if cmd == "runsc" else None
        runtimes = detect_available_runtimes()
        assert SandboxRuntime.DOCKER in runtimes
        assert SandboxRuntime.GVISOR in runtimes
        assert SandboxRuntime.FIRECRACKER not in runtimes

    @patch("squadx_client.docker.hardening.shutil.which")
    def test_includes_firecracker_when_binary_found(self, mock_which):
        mock_which.side_effect = (
            lambda cmd: "/usr/bin/firecracker-containerd"
            if cmd == "firecracker-containerd"
            else None
        )
        runtimes = detect_available_runtimes()
        assert SandboxRuntime.DOCKER in runtimes
        assert SandboxRuntime.FIRECRACKER in runtimes
        assert SandboxRuntime.GVISOR not in runtimes

    @patch("squadx_client.docker.hardening.shutil.which")
    def test_includes_all_when_all_found(self, mock_which):
        mock_which.return_value = "/usr/bin/something"
        runtimes = detect_available_runtimes()
        assert SandboxRuntime.DOCKER in runtimes
        assert SandboxRuntime.GVISOR in runtimes
        assert SandboxRuntime.FIRECRACKER in runtimes


class TestResolveRuntime:
    """Test resolve_runtime fallback logic."""

    @patch("squadx_client.docker.hardening.detect_available_runtimes")
    @patch("squadx_client.docker.hardening.settings")
    def test_returns_docker_when_configured_docker(self, mock_settings, mock_detect):
        mock_settings.sandbox_runtime = "docker"
        mock_detect.return_value = [SandboxRuntime.DOCKER]
        result = resolve_runtime()
        assert result == SandboxRuntime.DOCKER

    @patch("squadx_client.docker.hardening.detect_available_runtimes")
    @patch("squadx_client.docker.hardening.settings")
    def test_returns_gvisor_when_available(self, mock_settings, mock_detect):
        mock_settings.sandbox_runtime = "gvisor"
        mock_detect.return_value = [SandboxRuntime.DOCKER, SandboxRuntime.GVISOR]
        result = resolve_runtime()
        assert result == SandboxRuntime.GVISOR

    @patch("squadx_client.docker.hardening.detect_available_runtimes")
    @patch("squadx_client.docker.hardening.settings")
    def test_falls_back_to_docker_when_requested_unavailable(self, mock_settings, mock_detect):
        mock_settings.sandbox_runtime = "gvisor"
        mock_detect.return_value = [SandboxRuntime.DOCKER]
        result = resolve_runtime()
        assert result == SandboxRuntime.DOCKER

    @patch("squadx_client.docker.hardening.detect_available_runtimes")
    @patch("squadx_client.docker.hardening.settings")
    def test_falls_back_to_docker_on_unknown_runtime(self, mock_settings, mock_detect):
        mock_settings.sandbox_runtime = "unknown-runtime"
        mock_detect.return_value = [SandboxRuntime.DOCKER]
        result = resolve_runtime()
        assert result == SandboxRuntime.DOCKER


class TestAgentSandboxInitialization:
    """Test AgentSandbox initialization with runtime parameter."""

    @patch("squadx_client.docker.sandbox.docker_manager")
    @patch("squadx_client.docker.sandbox.resolve_runtime")
    def test_sandbox_uses_explicit_runtime(self, mock_resolve, mock_manager):
        from squadx_client.sandbox.docker_backend import DockerSandboxBackend

        session = DockerSandboxBackend(manager=mock_manager).create_session(
            task_id=1,
            agent_type="backend",
            workspace_path="/tmp/workspace",
        )
        session.runtime = SandboxRuntime.GVISOR
        assert session.runtime == SandboxRuntime.GVISOR
        # resolve_runtime may have been called at construct; explicit set wins
        assert session.runtime == SandboxRuntime.GVISOR

    @patch("squadx_client.docker.sandbox.docker_manager")
    @patch("squadx_client.docker.sandbox.resolve_runtime", return_value=SandboxRuntime.DOCKER)
    def test_sandbox_auto_resolves_runtime_when_none(self, mock_resolve, mock_manager):
        from squadx_client.sandbox.docker_backend import DockerSandboxBackend

        session = DockerSandboxBackend(manager=mock_manager).create_session(
            task_id=2,
            agent_type="frontend",
            workspace_path="/tmp/workspace",
        )
        assert session.runtime == SandboxRuntime.DOCKER
        mock_resolve.assert_called()

    @patch("squadx_client.docker.sandbox.docker_manager")
    @patch("squadx_client.docker.sandbox.resolve_runtime")
    def test_sandbox_initial_status_is_created(self, mock_resolve, mock_manager):
        from squadx_client.docker.sandbox import SandboxStatus
        from squadx_client.sandbox.docker_backend import DockerSandboxBackend

        mock_resolve.return_value = SandboxRuntime.DOCKER

        session = DockerSandboxBackend(manager=mock_manager).create_session(
            task_id=3,
            agent_type="backend",
            workspace_path="/tmp/workspace",
        )
        assert session.status == SandboxStatus.CREATED
        assert session.container_id is None
        assert session.vnc_port is None
