"""Tests for squadx_client.llm.router module."""

import os
from unittest.mock import patch, MagicMock

import pytest

from squadx_client.llm.router import get_llm, get_coding_llm, get_fast_llm

_KEY_ENVS = (
    "OPENAI_API_KEY",
    "ANTHROPIC_API_KEY",
    "GOOGLE_API_KEY",
    "GEMINI_API_KEY",
    "OPENROUTER_API_KEY",
)


@pytest.fixture(autouse=True)
def _clear_lru_cache_and_provider_env(monkeypatch):
    """Clear get_llm cache and provider env so client/.env cannot leak into asserts."""
    get_llm.cache_clear()
    for key in _KEY_ENVS:
        monkeypatch.delenv(key, raising=False)
    yield
    get_llm.cache_clear()


def _make_settings(**overrides):
    """Create a mock settings object with given overrides."""
    defaults = {
        "openai_api_key": None,
        "anthropic_api_key": None,
        "google_api_key": None,
        "openrouter_api_key": None,
        "default_model": "gpt-4o",
    }
    defaults.update(overrides)
    mock = MagicMock()
    for k, v in defaults.items():
        setattr(mock, k, v)
    return mock


class TestGetLlmOpenAI:
    """Test get_llm with OpenAI models."""

    @patch("squadx_client.llm.router.ChatLiteLLM")
    @patch("squadx_client.llm.router.settings", _make_settings(openai_api_key="sk-test"))
    def test_openai_model(self, mock_chat_litellm):
        mock_chat_litellm.return_value = MagicMock()
        llm = get_llm("gpt-4o")
        mock_chat_litellm.assert_called_once_with(
            model="gpt-4o",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.ChatLiteLLM")
    @patch("squadx_client.llm.router.settings", _make_settings(openai_api_key="sk-test"))
    def test_o1_model_uses_litellm(self, mock_chat_litellm):
        mock_chat_litellm.return_value = MagicMock()
        get_llm("o1-preview")
        mock_chat_litellm.assert_called_once_with(
            model="o1-preview",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.settings", _make_settings(openai_api_key=None))
    def test_openai_model_missing_key_raises(self):
        with pytest.raises(ValueError, match="OPENAI_API_KEY"):
            get_llm("gpt-4o")


class TestGetLlmAnthropic:
    """Test get_llm with Anthropic models."""

    @patch("squadx_client.llm.router.ChatLiteLLM")
    @patch("squadx_client.llm.router.settings", _make_settings(anthropic_api_key="sk-ant-test"))
    def test_anthropic_model(self, mock_chat_litellm):
        mock_chat_litellm.return_value = MagicMock()
        llm = get_llm("claude-3-opus-20240229")
        mock_chat_litellm.assert_called_once_with(
            model="claude-3-opus-20240229",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.settings", _make_settings(anthropic_api_key=None))
    def test_anthropic_model_missing_key_raises(self):
        with pytest.raises(ValueError, match="ANTHROPIC_API_KEY"):
            get_llm("claude-3-sonnet-20240229")


class TestGetLlmDefault:
    """Test get_llm with default/unknown model names."""

    @patch("squadx_client.llm.router.ChatLiteLLM")
    @patch("squadx_client.llm.router.settings", _make_settings(openai_api_key="sk-test"))
    def test_default_model_used_when_none(self, mock_chat_litellm):
        mock_chat_litellm.return_value = MagicMock()
        get_llm(None)
        mock_chat_litellm.assert_called_once_with(
            model="gpt-4o",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.ChatLiteLLM")
    @patch(
        "squadx_client.llm.router.settings",
        _make_settings(openai_api_key=None, anthropic_api_key="sk-ant-test"),
    )
    def test_unknown_model_routes_through_litellm(self, mock_chat_litellm):
        """LiteLLM handles routing for unknown/custom model names."""
        mock_chat_litellm.return_value = MagicMock()
        get_llm("gemini/gemini-1.5-pro")
        mock_chat_litellm.assert_called_once_with(
            model="gemini/gemini-1.5-pro",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.settings", _make_settings())
    def test_openai_no_key_raises(self):
        with pytest.raises(ValueError, match="OPENAI_API_KEY"):
            get_llm("gpt-4o")


class TestGetLlmProviderPrefixes:
    """Test get_llm with explicit provider prefixes."""

    @patch("squadx_client.llm.router.ChatLiteLLM")
    @patch("squadx_client.llm.router.settings", _make_settings(openai_api_key="sk-test"))
    def test_openai_prefix(self, mock_chat_litellm):
        mock_chat_litellm.return_value = MagicMock()
        get_llm("openai/gpt-4o")
        mock_chat_litellm.assert_called_once_with(
            model="openai/gpt-4o",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.ChatLiteLLM")
    @patch("squadx_client.llm.router.settings", _make_settings(anthropic_api_key="sk-ant-test"))
    def test_anthropic_prefix(self, mock_chat_litellm):
        mock_chat_litellm.return_value = MagicMock()
        get_llm("anthropic/claude-3-sonnet-20240229")
        mock_chat_litellm.assert_called_once_with(
            model="anthropic/claude-3-sonnet-20240229",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.ChatLiteLLM")
    @patch("squadx_client.llm.router.settings", _make_settings())
    def test_ollama_no_key_needed(self, mock_chat_litellm):
        """Ollama models don't require API keys."""
        mock_chat_litellm.return_value = MagicMock()
        get_llm("ollama/llama3")
        mock_chat_litellm.assert_called_once_with(
            model="ollama/llama3",
            temperature=0.7,
        )


class TestGetCodingLlm:
    """Test get_coding_llm helper."""

    @patch("squadx_client.llm.router.ChatLiteLLM")
    @patch("squadx_client.llm.router.settings", _make_settings(openai_api_key="sk-test"))
    def test_coding_llm_prefers_openai(self, mock_chat_litellm):
        mock_chat_litellm.return_value = MagicMock()
        get_coding_llm()
        mock_chat_litellm.assert_called_once_with(
            model="gpt-4o",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.ChatLiteLLM")
    @patch(
        "squadx_client.llm.router.settings",
        _make_settings(openai_api_key=None, anthropic_api_key="sk-ant-test"),
    )
    def test_coding_llm_falls_back_to_anthropic(self, mock_chat_litellm):
        mock_chat_litellm.return_value = MagicMock()
        get_coding_llm()
        mock_chat_litellm.assert_called_once_with(
            model="claude-3-opus-20240229",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.settings", _make_settings())
    def test_coding_llm_no_keys_raises(self):
        with pytest.raises(ValueError, match="No API key configured"):
            get_coding_llm()

    @patch("squadx_client.llm.router.ChatLiteLLM")
    @patch(
        "squadx_client.llm.router.settings",
        _make_settings(
            openrouter_api_key="sk-or-test",
            default_model="openrouter/openai/gpt-4o-mini",
        ),
    )
    def test_coding_llm_openrouter_default(self, mock_chat_litellm):
        mock_chat_litellm.return_value = MagicMock()
        get_coding_llm()
        mock_chat_litellm.assert_called_once_with(
            model="openrouter/openai/gpt-4o-mini",
            temperature=0.7,
        )


class TestGetFastLlm:
    """Test get_fast_llm helper."""

    @patch("squadx_client.llm.router.ChatLiteLLM")
    @patch("squadx_client.llm.router.settings", _make_settings(openai_api_key="sk-test"))
    def test_fast_llm_prefers_openai_mini(self, mock_chat_litellm):
        mock_chat_litellm.return_value = MagicMock()
        get_fast_llm()
        mock_chat_litellm.assert_called_once_with(
            model="gpt-4o-mini",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.ChatLiteLLM")
    @patch(
        "squadx_client.llm.router.settings",
        _make_settings(openai_api_key=None, anthropic_api_key="sk-ant-test"),
    )
    def test_fast_llm_falls_back_to_haiku(self, mock_chat_litellm):
        mock_chat_litellm.return_value = MagicMock()
        get_fast_llm()
        mock_chat_litellm.assert_called_once_with(
            model="claude-3-haiku-20240307",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.settings", _make_settings())
    def test_fast_llm_no_keys_raises(self):
        with pytest.raises(ValueError, match="No API key configured"):
            get_fast_llm()

    @patch("squadx_client.llm.router.ChatLiteLLM")
    @patch(
        "squadx_client.llm.router.settings",
        _make_settings(
            openrouter_api_key="sk-or-test",
            default_model="openrouter/openai/gpt-4o-mini",
        ),
    )
    def test_fast_llm_openrouter_default(self, mock_chat_litellm):
        mock_chat_litellm.return_value = MagicMock()
        get_fast_llm()
        mock_chat_litellm.assert_called_once_with(
            model="openrouter/openai/gpt-4o-mini",
            temperature=0.7,
        )


class TestOpenRouterEnsure:
    @patch("squadx_client.llm.router.ChatLiteLLM")
    @patch(
        "squadx_client.llm.router.settings",
        _make_settings(openrouter_api_key="sk-or-test"),
    )
    def test_openrouter_model_ok(self, mock_chat_litellm):
        mock_chat_litellm.return_value = MagicMock()
        get_llm("openrouter/openai/gpt-4o-mini")
        mock_chat_litellm.assert_called_once()

    @patch("squadx_client.llm.router.settings", _make_settings())
    def test_openrouter_missing_key_raises(self):
        with pytest.raises(ValueError, match="OPENROUTER_API_KEY"):
            get_llm("openrouter/openai/gpt-4o-mini")
