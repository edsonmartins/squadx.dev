"""Configuration management for SquadX Client."""

from typing import Any

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # API Configuration
    api_url: str = Field(default="http://localhost:8080", alias="SQUADX_API_URL")
    ws_url: str = Field(default="ws://localhost:8080/ws", alias="SQUADX_WS_URL")
    api_token: str | None = Field(default=None, alias="SQUADX_API_TOKEN")

    # LLM Configuration
    openai_api_key: str | None = Field(default=None, alias="OPENAI_API_KEY")
    anthropic_api_key: str | None = Field(default=None, alias="ANTHROPIC_API_KEY")
    google_api_key: str | None = Field(default=None, alias="GOOGLE_API_KEY")
    # OpenRouter (LiteLLM provider prefix openrouter/<vendor>/<model>)
    openrouter_api_key: str | None = Field(default=None, alias="OPENROUTER_API_KEY")
    default_model: str = Field(default="gpt-4o", alias="SQUADX_DEFAULT_MODEL")

    # External CLI runtime adapter (Claude Code / Codex / Gemini CLI in the sandbox)
    external_cli_timeout_seconds: int = Field(
        default=1800, alias="SQUADX_EXTERNAL_CLI_TIMEOUT_SECONDS"
    )
    # Generic harness fallback: register a new coding CLI with no code change.
    # JSON object of PROVIDER -> shell-style command template, where "{prompt}" is
    # substituted with the task prompt, e.g.
    #   {"MYCLI": "mycli run --task {prompt}"}
    # Providers here augment the built-in set (CLAUDE_CODE/CODEX/GEMINI_CLI/AIDER/OPENCODE).
    external_cli_command_templates: dict[str, str] = Field(
        default_factory=dict, alias="SQUADX_EXTERNAL_CLI_COMMAND_TEMPLATES"
    )

    # Resilience: periodically claim pending tasks over HTTP as a fallback when the
    # STOMP push is missed (NAT/firewall, reconnect gaps). 0 disables polling.
    poll_fallback_interval_seconds: int = Field(
        default=0, alias="SQUADX_POLL_FALLBACK_INTERVAL_SECONDS"
    )

    # Docker Configuration
    docker_host: str | None = Field(default=None, alias="DOCKER_HOST")
    docker_network: str = Field(default="squadx-network", alias="SQUADX_DOCKER_NETWORK")
    agent_image: str = Field(default="squadx/agent:latest", alias="SQUADX_AGENT_IMAGE")

    # Git Configuration
    git_user_name: str = Field(default="SquadX Agent", alias="SQUADX_GIT_USER_NAME")
    git_user_email: str = Field(default="agent@squadx.dev", alias="SQUADX_GIT_USER_EMAIL")

    # Git worktree isolation
    use_worktrees: bool = Field(default=True, alias="SQUADX_USE_WORKTREES")

    # Local Storage
    data_dir: str = Field(default="~/.squadx", alias="SQUADX_DATA_DIR")
    db_path: str = Field(default="~/.squadx/squadx.db", alias="SQUADX_DB_PATH")
    log_level: str = Field(default="INFO", alias="SQUADX_LOG_LEVEL")

    # Workspace Configuration
    workspace_path: str = Field(default="/workspace", alias="SQUADX_WORKSPACE_PATH")
    workspace_mount_path: str | None = Field(default=None, alias="SQUADX_WORKSPACE_MOUNT_PATH")

    # Agent Configuration
    max_concurrent_agents: int = Field(default=4, alias="SQUADX_MAX_CONCURRENT_AGENTS")
    agent_timeout: int = Field(default=3600, alias="SQUADX_AGENT_TIMEOUT")  # seconds
    agent_memory_limit: str = Field(default="2g", alias="SQUADX_AGENT_MEMORY_LIMIT")
    agent_cpu_limit: float = Field(default=2.0, alias="SQUADX_AGENT_CPU_LIMIT")

    # Sandbox Execution Configuration
    enable_sandbox: bool = Field(default=True, alias="SQUADX_ENABLE_SANDBOX")
    enable_vnc: bool = Field(default=True, alias="SQUADX_ENABLE_VNC")

    # Security Configuration (Docker Hardening)
    enable_network: bool = Field(default=False, alias="SQUADX_ENABLE_NETWORK")
    seccomp_profile: str | None = Field(default=None, alias="SQUADX_SECCOMP_PROFILE")
    apparmor_profile: str | None = Field(default=None, alias="SQUADX_APPARMOR_PROFILE")
    # External-CLI prompt-injection policy: "enforce" | "audit" | "off" (RFC-0005 / ADR-0007).
    # Secure-by-default: block-severity findings (instruction-override, secret-exfiltration,
    # credential-file-read) abort the run. Set "audit" to only log, "off" to skip.
    cli_security_mode: str = Field(default="enforce", alias="SQUADX_CLI_SECURITY_MODE")

    # Network policy (ADR-0008 / RFC-0006): agent-default | deny-all | full | (deprecated) none, package-managers
    network_policy: str = Field(default="agent-default", alias="SQUADX_NETWORK_POLICY")
    # RFC-0006 Phase 1: enforce egress via a privileged sidecar sharing the agent netns.
    # ON by default: it is the only place egress can actually be enforced (the agent is
    # cap-drop ALL, so in-agent iptables cannot work), and with it off an agent running
    # untrusted model output has unrestricted network access. A run whose policy cannot
    # be applied fails closed. Requires the squadx/egress-proxy image — `make build-egress-proxy`.
    egress_sidecar_enabled: bool = Field(default=True, alias="SQUADX_EGRESS_SIDECAR")
    egress_sidecar_image: str = Field(default="squadx/egress-proxy:latest", alias="SQUADX_EGRESS_PROXY_IMAGE")
    egress_fail_open: bool = Field(default=False, alias="SQUADX_EGRESS_FAIL_OPEN")  # never true in prod
    # ADR-0008 Phase 0: block cloud metadata egress (169.254.169.254 / ECS creds) host-side,
    # on the DOCKER-USER chain. Default on; degrades loudly if the host can't apply it.
    block_cloud_metadata: bool = Field(default=True, alias="SQUADX_BLOCK_CLOUD_METADATA")
    # Default per-run cost ceiling (USD). Over it the arbiter escalates to a human (see
    # orchestrator nodes). Threaded into OrchestratorState by the daemon. Raise for large
    # tasks; set very high to effectively disable. Complements the max_cycles=3 backstop.
    cost_budget_usd: float | None = Field(default=5.0, alias="SQUADX_COST_BUDGET_USD")
    sandbox_ttl_seconds: int = Field(default=3600, alias="SQUADX_SANDBOX_TTL")
    sandbox_max_ttl_seconds: int = Field(default=86400, alias="SQUADX_SANDBOX_MAX_TTL")

    # Sandbox Backend (ADR-0009) — which isolator: docker | process | firecracker | remote.
    # Distinct from sandbox_runtime (docker|gvisor|firecracker *under* the Docker backend).
    # Default docker; process/firecracker/remote are not selectable until later phases.
    sandbox_backend: str = Field(default="docker", alias="SQUADX_SANDBOX_BACKEND")

    # Sandbox Runtime Configuration
    # Which container runtime to use: docker, gvisor, firecracker
    sandbox_runtime: str = Field(default="docker", alias="SQUADX_SANDBOX_RUNTIME")
    # Auto-upgrade to stronger runtime when execution thresholds are met
    auto_upgrade_runtime: bool = Field(default=True, alias="SQUADX_AUTO_UPGRADE_RUNTIME")
    # Daily execution threshold to auto-upgrade to gVisor (runsc)
    gvisor_threshold: int = Field(default=100, alias="SQUADX_GVISOR_THRESHOLD")
    # Daily execution threshold to auto-upgrade to Firecracker
    firecracker_threshold: int = Field(default=1000, alias="SQUADX_FIRECRACKER_THRESHOLD")

    # Warm container pool (cuts Docker cold start from 10-20s to <1s)
    # Off by default — opt-in per deployment.
    sandbox_pool_enabled: bool = Field(default=False, alias="SQUADX_SANDBOX_POOL_ENABLED")
    # Target number of pre-created containers kept warm in the background
    sandbox_pool_size: int = Field(default=4, alias="SQUADX_SANDBOX_POOL_SIZE")
    # Minimum number of containers kept warm even if no traffic — eliminates
    # the cold-start penalty on the very first task after daemon start
    sandbox_pool_min_ready: int = Field(default=1, alias="SQUADX_SANDBOX_POOL_MIN_READY")
    # Host directory bind-mounted into every pool container as /workspace.
    # Pair with worktree-per-subtask (SQUADX_USE_WORKTREES=true) for isolation
    # between concurrent tasks sharing the same pool container.
    sandbox_pool_workspace_root: str = Field(
        default="/var/squadx/workspaces", alias="SQUADX_SANDBOX_POOL_WORKSPACE_ROOT"
    )
    # How often the background refill checks the pool size
    sandbox_pool_refill_interval_seconds: float = Field(
        default=5.0, alias="SQUADX_SANDBOX_POOL_REFILL_INTERVAL_SECONDS"
    )

    # BrainSentry Integration (Agent Memory)
    brainsentry_url: str | None = Field(default=None, alias="SQUADX_BRAINSENTRY_URL")
    brainsentry_api_key: str | None = Field(default=None, alias="SQUADX_BRAINSENTRY_API_KEY")
    brainsentry_tenant_id: str = Field(default="default", alias="SQUADX_BRAINSENTRY_TENANT_ID")
    brainsentry_memory_scope: str = Field(default="adaptive", alias="SQUADX_BRAINSENTRY_MEMORY_SCOPE")
    brainsentry_enable_procedural_memory: bool = Field(default=True, alias="SQUADX_BRAINSENTRY_ENABLE_PROCEDURAL_MEMORY")
    brainsentry_procedural_limit: int = Field(default=5, alias="SQUADX_BRAINSENTRY_PROCEDURAL_LIMIT")

    # SquadX Live Integration
    squadx_live_url: str | None = Field(default=None, alias="SQUADX_LIVE_URL")
    livekit_url: str | None = Field(default=None, alias="LIVEKIT_URL")
    livekit_api_key: str | None = Field(default=None, alias="LIVEKIT_API_KEY")
    livekit_api_secret: str | None = Field(default=None, alias="LIVEKIT_API_SECRET")

    # Supabase Configuration (Live Streaming)
    supabase_url: str | None = Field(default=None, alias="SUPABASE_URL")
    supabase_anon_key: str | None = Field(default=None, alias="SUPABASE_ANON_KEY")
    supabase_service_key: str | None = Field(default=None, alias="SUPABASE_SERVICE_KEY")

    # WebRTC / TURN Configuration
    turn_url: str | None = Field(default=None, alias="TURN_URL")
    turn_username: str | None = Field(default=None, alias="TURN_USERNAME")
    turn_credential: str | None = Field(default=None, alias="TURN_CREDENTIAL")
    # Alternative: Cloudflare TURN (free tier available)
    cloudflare_turn_token: str | None = Field(default=None, alias="CLOUDFLARE_TURN_TOKEN")

    # Real smoke / local deterministic execution mode
    smoke_execution_mode: bool = Field(default=False, alias="SQUADX_SMOKE_EXECUTION_MODE")
    smoke_execution_delay_seconds: float = Field(default=0.5, alias="SQUADX_SMOKE_EXECUTION_DELAY_SECONDS")
    smoke_execution_summary: str = Field(
        default="Smoke execution completed successfully.",
        alias="SQUADX_SMOKE_EXECUTION_SUMMARY"
    )

    @property
    def expanded_data_dir(self) -> str:
        """Return expanded data directory path."""
        import os
        return os.path.expanduser(self.data_dir)

    @property
    def expanded_db_path(self) -> str:
        """Return expanded database path."""
        import os
        return os.path.expanduser(self.db_path)

    def get_ice_servers(self) -> list[dict]:
        """Get ICE servers configuration for WebRTC.

        Returns list of ICE server configs including STUN and TURN servers.
        TURN is essential for NAT traversal in production environments.
        """
        servers: list[dict[str, Any]] = [
            # Google's free STUN servers
            {"urls": ["stun:stun.l.google.com:19302"]},
            {"urls": ["stun:stun1.l.google.com:19302"]},
        ]

        # Add TURN server if configured
        if self.turn_url:
            turn_config: dict = {"urls": [self.turn_url]}
            if self.turn_username:
                turn_config["username"] = self.turn_username
            if self.turn_credential:
                turn_config["credential"] = self.turn_credential
            servers.append(turn_config)

        # Add Cloudflare TURN if token is available
        # Cloudflare TURN uses token-based authentication
        if self.cloudflare_turn_token:
            servers.append({
                "urls": [
                    "turn:turn.cloudflare.com:3478?transport=udp",
                    "turn:turn.cloudflare.com:3478?transport=tcp",
                    "turns:turn.cloudflare.com:5349?transport=tcp",
                ],
                "username": "cf",
                "credential": self.cloudflare_turn_token,
            })

        return servers


settings = Settings()
