"""Tests for squadx_client.config module."""

import os
from unittest.mock import patch

from squadx_client.config import Settings


class TestSettingsDefaults:
    """Test default values for Settings."""

    def test_default_data_dir(self):
        s = Settings(_env_file=None)
        assert s.data_dir == "~/.squadx"

    def test_default_db_path(self):
        s = Settings(_env_file=None)
        assert s.db_path == "~/.squadx/squadx.db"

    def test_default_model(self):
        s = Settings(_env_file=None)
        assert s.default_model == "gpt-4o"

    def test_default_api_url(self):
        s = Settings(_env_file=None)
        assert s.api_url == "http://localhost:8080"

    def test_default_ws_url(self):
        s = Settings(_env_file=None)
        assert s.ws_url == "ws://localhost:8080/ws"

    def test_default_max_concurrent_agents(self):
        s = Settings(_env_file=None)
        assert s.max_concurrent_agents == 4

    def test_default_agent_timeout(self):
        s = Settings(_env_file=None)
        assert s.agent_timeout == 3600

    def test_default_log_level(self):
        s = Settings(_env_file=None)
        assert s.log_level == "INFO"

    def test_default_cli_security_mode_is_enforce(self):
        # Secure-by-default (threat-model #5): block-severity injection aborts the run.
        s = Settings(_env_file=None)
        assert s.cli_security_mode == "enforce"

    def test_cli_security_mode_env_override(self):
        with patch.dict(os.environ, {"SQUADX_CLI_SECURITY_MODE": "audit"}):
            s = Settings(_env_file=None)
        assert s.cli_security_mode == "audit"

    def test_default_cost_budget_usd(self):
        # Threat-model #5: a per-run cost ceiling is set by default (was uncapped None).
        s = Settings(_env_file=None)
        assert s.cost_budget_usd == 5.0

    def test_cost_budget_usd_env_override(self):
        with patch.dict(os.environ, {"SQUADX_COST_BUDGET_USD": "25"}):
            s = Settings(_env_file=None)
        assert s.cost_budget_usd == 25.0

    def test_default_block_cloud_metadata(self):
        s = Settings(_env_file=None)
        assert s.block_cloud_metadata is True


class TestExpandedPaths:
    """Test path expansion properties."""

    def test_expanded_data_dir_expands_tilde(self):
        s = Settings(_env_file=None)
        expanded = s.expanded_data_dir
        assert "~" not in expanded
        assert expanded.endswith(".squadx")
        assert expanded.startswith(os.path.expanduser("~"))

    def test_expanded_db_path_expands_tilde(self):
        s = Settings(_env_file=None)
        expanded = s.expanded_db_path
        assert "~" not in expanded
        assert expanded.endswith("squadx.db")
        assert expanded.startswith(os.path.expanduser("~"))

    def test_expanded_data_dir_with_custom_path(self, monkeypatch):
        monkeypatch.setenv("SQUADX_DATA_DIR", "/tmp/custom-squadx")
        s = Settings(_env_file=None)
        assert s.expanded_data_dir == "/tmp/custom-squadx"


class TestGetIceServers:
    """Test ICE server configuration."""

    def test_default_stun_only(self):
        s = Settings(_env_file=None)
        servers = s.get_ice_servers()
        assert len(servers) == 2
        assert servers[0]["urls"] == ["stun:stun.l.google.com:19302"]
        assert servers[1]["urls"] == ["stun:stun1.l.google.com:19302"]

    def test_with_turn_configured(self, monkeypatch):
        monkeypatch.setenv("TURN_URL", "turn:my-turn.example.com:3478")
        monkeypatch.setenv("TURN_USERNAME", "user1")
        monkeypatch.setenv("TURN_CREDENTIAL", "secret1")
        s = Settings(_env_file=None)
        servers = s.get_ice_servers()
        assert len(servers) == 3
        turn = servers[2]
        assert turn["urls"] == ["turn:my-turn.example.com:3478"]
        assert turn["username"] == "user1"
        assert turn["credential"] == "secret1"

    def test_with_turn_no_credentials(self, monkeypatch):
        monkeypatch.setenv("TURN_URL", "turn:my-turn.example.com:3478")
        s = Settings(_env_file=None)
        servers = s.get_ice_servers()
        turn = servers[2]
        assert "username" not in turn
        assert "credential" not in turn

    def test_with_cloudflare_turn(self, monkeypatch):
        monkeypatch.setenv("CLOUDFLARE_TURN_TOKEN", "cf-token-xyz")
        s = Settings(_env_file=None)
        servers = s.get_ice_servers()
        assert len(servers) == 3
        cf = servers[2]
        assert any("cloudflare" in u for u in cf["urls"])
        assert cf["username"] == "cf"
        assert cf["credential"] == "cf-token-xyz"

    def test_with_both_turn_and_cloudflare(self, monkeypatch):
        monkeypatch.setenv("TURN_URL", "turn:my-turn.example.com:3478")
        monkeypatch.setenv("CLOUDFLARE_TURN_TOKEN", "cf-token-xyz")
        s = Settings(_env_file=None)
        servers = s.get_ice_servers()
        assert len(servers) == 4


class TestEnvOverrides:
    """Test environment variable overrides."""

    def test_override_default_model(self, monkeypatch):
        monkeypatch.setenv("SQUADX_DEFAULT_MODEL", "claude-3-opus-20240229")
        s = Settings(_env_file=None)
        assert s.default_model == "claude-3-opus-20240229"

    def test_override_api_url(self, monkeypatch):
        monkeypatch.setenv("SQUADX_API_URL", "https://api.squadx.dev")
        s = Settings(_env_file=None)
        assert s.api_url == "https://api.squadx.dev"

    def test_override_max_concurrent_agents(self, monkeypatch):
        monkeypatch.setenv("SQUADX_MAX_CONCURRENT_AGENTS", "8")
        s = Settings(_env_file=None)
        assert s.max_concurrent_agents == 8

    def test_api_token_default_none(self):
        s = Settings(_env_file=None)
        assert s.api_token is None

    def test_override_api_token(self, monkeypatch):
        monkeypatch.setenv("SQUADX_API_TOKEN", "tok-abc123")
        s = Settings(_env_file=None)
        assert s.api_token == "tok-abc123"
