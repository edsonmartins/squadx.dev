"""Pluggable sandbox backends (ADR-0009).

Phase 0: typed contract + config selection. Runtime default remains Docker via
``AgentSandbox`` until Phase 2 extracts ``DockerSandboxBackend``.
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
    "features_for",
    "get_sandbox_backend",
    "get_sandbox_backend_kind",
    "parse_backend_kind",
]
