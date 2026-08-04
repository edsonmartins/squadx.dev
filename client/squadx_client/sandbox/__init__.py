"""Pluggable sandbox backends (ADR-0009).

Production: ``create_sandbox_session()`` → ``SandboxSession`` (Docker or PROCESS).
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
    create_sandbox_session,
    features_for,
    get_sandbox_backend,
    get_sandbox_backend_kind,
    parse_backend_kind,
)
from squadx_client.sandbox.protocol import SandboxBackend
from squadx_client.sandbox.session import SandboxSession
from squadx_client.sandbox.types import (
    CommandResult,
    SandboxBackendKind,
    SandboxLifecycleStatus,
)

__all__ = [
    "BackendFeatures",
    "CommandResult",
    "SandboxBackend",
    "SandboxBackendError",
    "SandboxBackendKind",
    "SandboxExecError",
    "SandboxLifecycleStatus",
    "SandboxNotSupportedError",
    "SandboxPolicyError",
    "SandboxSession",
    "SandboxStartError",
    "create_sandbox_session",
    "features_for",
    "get_sandbox_backend",
    "get_sandbox_backend_kind",
    "parse_backend_kind",
]
