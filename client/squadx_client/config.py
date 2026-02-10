"""Configuration management for SquadX Client."""

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
    default_model: str = Field(default="gpt-4o", alias="SQUADX_DEFAULT_MODEL")

    # Docker Configuration
    docker_host: str | None = Field(default=None, alias="DOCKER_HOST")
    docker_network: str = Field(default="squadx-network", alias="SQUADX_DOCKER_NETWORK")
    agent_image: str = Field(default="squadx/agent:latest", alias="SQUADX_AGENT_IMAGE")

    # Git Configuration
    git_user_name: str = Field(default="SquadX Agent", alias="SQUADX_GIT_USER_NAME")
    git_user_email: str = Field(default="agent@squadx.dev", alias="SQUADX_GIT_USER_EMAIL")

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


settings = Settings()
