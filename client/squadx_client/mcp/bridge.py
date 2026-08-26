"""SquadX workspace MCP bridge (RFC-0001)."""

import argparse
import sys
from typing import Any, Optional

from mcp.server.fastmcp import FastMCP

from ..config import settings
from .workspace_client import WorkspaceApiClient

mcp = FastMCP("squadx-workspace")
_client: Optional[WorkspaceApiClient] = None


def configure(client: WorkspaceApiClient) -> None:
    global _client
    _client = client


def _require_client() -> WorkspaceApiClient:
    if _client is None:
        raise RuntimeError("workspace client not configured (missing SQUADX_WORKSPACE_TOKEN?)")
    return _client


async def get_change_impl() -> Any:
    return await _require_client().get_change()


async def get_tasks_impl(assignee: Optional[str] = None) -> Any:
    return await _require_client().get_tasks(assignee)


async def update_task_status_impl(task_id: int, status: str, note: Optional[str] = None) -> Any:
    return await _require_client().update_task_status(task_id, status, note)


async def report_blocker_impl(task_id: int, reason: str) -> Any:
    return await _require_client().report_blocker(task_id, reason)


async def materialize_change_impl() -> Any:
    return await _require_client().materialize_change()


async def scaffold_tests_impl(requirement_id: Optional[int] = None) -> Any:
    return await _require_client().scaffold_tests(requirement_id)


async def search_code_impl(query: str, limit: int = 20) -> Any:
    return await _require_client().search_code(query, limit)


@mcp.tool()
async def get_change() -> Any:
    """Briefing da mudança da sessão."""
    return await get_change_impl()


@mcp.tool()
async def get_tasks(assignee: Optional[str] = None) -> Any:
    """Lista tarefas da mudança."""
    return await get_tasks_impl(assignee)


@mcp.tool()
async def update_task_status(task_id: int, status: str, note: Optional[str] = None) -> Any:
    """Reporta progresso de uma tarefa."""
    return await update_task_status_impl(task_id, status, note)


@mcp.tool()
async def report_blocker(task_id: int, reason: str) -> Any:
    """Registra um bloqueio."""
    return await report_blocker_impl(task_id, reason)


@mcp.tool()
async def materialize_change() -> Any:
    """Materializa a spec corrente."""
    return await materialize_change_impl()


@mcp.tool()
async def scaffold_tests(requirement_id: Optional[int] = None) -> Any:
    """Gera esqueleto de testes."""
    return await scaffold_tests_impl(requirement_id)


@mcp.tool()
async def search_code(query: str, limit: int = 20) -> Any:
    """Busca código no snapshot ativo e revision-pinned da sessão."""
    return await search_code_impl(query, limit)


def main() -> None:
    parser = argparse.ArgumentParser(description="SquadX workspace MCP bridge")
    parser.add_argument("--transport", choices=["stdio", "sse"], default="stdio")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    token = settings.workspace_token
    if not token:
        print("SQUADX_WORKSPACE_TOKEN is not set.", file=sys.stderr)
        sys.exit(1)
    configure(WorkspaceApiClient(settings.api_url, token))
    if args.transport == "sse":
        mcp.settings.port = args.port
    mcp.run(transport=args.transport)


if __name__ == "__main__":
    main()
