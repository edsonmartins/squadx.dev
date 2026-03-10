"""Main entry point for SquadX Client CLI."""

import asyncio
import os
import signal
import sys
from pathlib import Path

import typer
from rich.console import Console
from rich.panel import Panel

from squadx_client import __version__
from squadx_client.config import settings
from squadx_client.daemon import SquadXDaemon


def _read_pid() -> int | None:
    """Read PID from the daemon PID file.

    Returns the PID as an int, or None if the file doesn't exist or is invalid.
    """
    pid_path = SquadXDaemon.pid_file_path()
    try:
        return int(pid_path.read_text().strip())
    except (FileNotFoundError, ValueError):
        return None


def _is_process_running(pid: int) -> bool:
    """Check if a process with the given PID is running."""
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        # Process exists but we don't have permission to signal it
        return True

app = typer.Typer(
    name="squadx-client",
    help="SquadX.dev Client - AI Development Squad Orchestration Agent",
    add_completion=False,
)
console = Console()


@app.command()
def start(
    api_url: str = typer.Option(
        settings.api_url, "--api-url", "-a", help="Backend API URL"
    ),
    token: str = typer.Option(
        settings.api_token, "--token", "-t", help="API authentication token"
    ),
    foreground: bool = typer.Option(
        False, "--foreground", "-f", help="Run in foreground"
    ),
):
    """Start the SquadX daemon to receive and execute tasks."""
    console.print(
        Panel(
            f"[bold blue]SquadX.dev Client v{__version__}[/bold blue]\n"
            f"API: {api_url}",
            title="Starting Daemon",
        )
    )

    if not token:
        console.print("[red]Error: API token is required. Use --token or set SQUADX_API_TOKEN[/red]")
        raise typer.Exit(1)

    daemon = SquadXDaemon(api_url=api_url, token=token)

    def signal_handler(sig, frame):
        console.print("\n[yellow]Shutting down...[/yellow]")
        asyncio.get_event_loop().run_until_complete(daemon.stop())
        sys.exit(0)

    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    try:
        asyncio.run(daemon.run())
    except KeyboardInterrupt:
        console.print("\n[yellow]Interrupted by user[/yellow]")


@app.command()
def status():
    """Check the status of the SquadX daemon."""
    console.print("[bold]SquadX Daemon Status[/bold]")

    pid = _read_pid()
    if pid is None:
        console.print("[yellow]Daemon is not running (no PID file found)[/yellow]")
        raise typer.Exit(1)

    if _is_process_running(pid):
        console.print(f"[green]Daemon is running (PID {pid})[/green]")
    else:
        console.print(f"[yellow]Daemon is not running (stale PID file, PID {pid})[/yellow]")
        # Clean up stale PID file
        try:
            SquadXDaemon.pid_file_path().unlink(missing_ok=True)
        except OSError:
            pass
        raise typer.Exit(1)


@app.command()
def stop():
    """Stop the running SquadX daemon."""
    console.print("[bold]Stopping SquadX Daemon[/bold]")

    pid = _read_pid()
    if pid is None:
        console.print("[yellow]Daemon is not running (no PID file found)[/yellow]")
        raise typer.Exit(1)

    if not _is_process_running(pid):
        console.print(f"[yellow]Daemon is not running (stale PID file, PID {pid})[/yellow]")
        try:
            SquadXDaemon.pid_file_path().unlink(missing_ok=True)
        except OSError:
            pass
        raise typer.Exit(1)

    try:
        os.kill(pid, signal.SIGTERM)
        console.print(f"[green]Sent SIGTERM to daemon (PID {pid})[/green]")
    except PermissionError:
        console.print(f"[red]Permission denied: cannot stop daemon (PID {pid})[/red]")
        raise typer.Exit(1)
    except ProcessLookupError:
        console.print(f"[yellow]Daemon already stopped (PID {pid})[/yellow]")
        try:
            SquadXDaemon.pid_file_path().unlink(missing_ok=True)
        except OSError:
            pass


@app.command()
def version():
    """Show version information."""
    console.print(f"SquadX.dev Client v{__version__}")


@app.command()
def config():
    """Show current configuration."""
    console.print("[bold]Current Configuration[/bold]\n")
    console.print(f"API URL: {settings.api_url}")
    console.print(f"WebSocket URL: {settings.ws_url}")
    console.print(f"Default Model: {settings.default_model}")
    console.print(f"Docker Network: {settings.docker_network}")
    console.print(f"Max Concurrent Agents: {settings.max_concurrent_agents}")
    console.print(f"Data Directory: {settings.data_dir}")
    console.print(f"Log Level: {settings.log_level}")


if __name__ == "__main__":
    app()
