"""Workspace path containment for PROCESS (and any host-side fs helpers)."""

from __future__ import annotations

from pathlib import Path

from squadx_client.sandbox.errors import SandboxExecError


def resolve_under_workspace(workspace: str | Path, path: str) -> Path:
    """Map a sandbox path onto the host workspace; refuse escapes.

    Accepts:
    - ``/workspace`` or ``/workspace/rel/file``
    - relative paths (joined under workspace)
    - absolute paths only if already inside the workspace tree

    Always returns a resolved path with ``path.is_relative_to(root)`` (or equal).
    """
    root = Path(workspace).expanduser().resolve()
    if not path or path == ".":
        return root

    if path.startswith("/workspace"):
        rel = path[len("/workspace") :].lstrip("/")
        candidate = (root / rel).resolve() if rel else root
    else:
        p = Path(path)
        if p.is_absolute():
            candidate = p.expanduser().resolve()
        else:
            # Reject absolute-looking escapes after join
            candidate = (root / path).resolve()

    try:
        candidate.relative_to(root)
    except ValueError as e:
        raise SandboxExecError(
            f"path outside workspace refused: {path!r} (workspace={root})"
        ) from e
    return candidate
