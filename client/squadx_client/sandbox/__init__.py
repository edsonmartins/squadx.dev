"""Pluggable sandbox backends (ADR-0009).

- Docker: ``DockerSandboxBackend`` / ``create_agent_sandbox()``
- Process: ``ProcessSandboxBackend`` / ``create_sandbox_session()`` (bubblewrap|Seatbelt)
"""

from squadx_client.sandbox.errors import (
    SandboxBackendError,
    SandboxExecError,
    SandboxNotSupportedError,
    SandboxPolicyError,
    SandboxStartError,
)
from squadx_client.sandbox.factory import (
    BackendFeatures,
    create_agent_sandbox,
    create_sandbox_session,
    features_for,
    get_sandbox_backend,
    get_sandbox_backend_kind,
    parse_backend_kind,
)
from squadx_client.sandbox.protocol import SandboxBackend
from squadx_client.sandbox.types import (
    ExecResult,
    SandboxBackendKind,
    SandboxHandle,
    SandboxLifecycleStatus,
)

__all__ = [
    "BackendFeatures",
    "ExecResult",
    "SandboxBackend",
    "SandboxBackendError",
    "SandboxBackendKind",
    "SandboxExecError",
    "SandboxHandle",
    "SandboxLifecycleStatus",
    "SandboxNotSupportedError",
    "SandboxPolicyError",
    "SandboxStartError",
    "create_agent_sandbox",
    "create_sandbox_session",
    "features_for",
    "get_sandbox_backend",
    "get_sandbox_backend_kind",
    "parse_backend_kind",
]
