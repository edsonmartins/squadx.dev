"""Main entry point for SquadX Client CLI."""

import asyncio
import signal
import sys
from pathlib import Path

import typer
from rich.console import Console
from rich.panel import Panel

from squadx_client import __version__
from squadx_client.config import settings
from squadx_client.daemon import SquadXDaemon

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
    # TODO: Implement status check via PID file or socket
    console.print("[yellow]Status check not yet implemented[/yellow]")


@app.command()
def stop():
    """Stop the running SquadX daemon."""
    console.print("[bold]Stopping SquadX Daemon[/bold]")
    # TODO: Implement graceful shutdown via PID file or socket
    console.print("[yellow]Stop command not yet implemented[/yellow]")


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
