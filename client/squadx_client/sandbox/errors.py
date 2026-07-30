"""Typed errors for sandbox backends (ADR-0009)."""

from __future__ import annotations


class SandboxBackendError(Exception):
    """Base class for sandbox backend failures."""


class SandboxNotSupportedError(SandboxBackendError):
    """Requested backend or feature is not implemented / not available on this host."""


class SandboxStartError(SandboxBackendError):
    """Sandbox failed to start (image missing, policy fail-closed, resource limits, …)."""


class SandboxExecError(SandboxBackendError):
    """Command execution inside the sandbox failed at the transport layer."""


class SandboxPolicyError(SandboxBackendError):
    """Network or security policy could not be applied (fail-closed path)."""
