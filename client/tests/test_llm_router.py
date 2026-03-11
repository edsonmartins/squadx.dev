"""Tests for squadx_client.llm.router module."""

from unittest.mock import patch, MagicMock

import pytest

from squadx_client.llm.router import get_llm, get_coding_llm, get_fast_llm


@pytest.fixture(autouse=True)
def _clear_lru_cache():
    """Clear the lru_cache on get_llm before each test."""
    get_llm.cache_clear()
    yield
    get_llm.cache_clear()


def _make_settings(**overrides):
    """Create a mock settings object with given overrides."""
    defaults = {
        "openai_api_key": None,
        "anthropic_api_key": None,
        "default_model": "gpt-4o",
    }
    defaults.update(overrides)
    mock = MagicMock()
    for k, v in defaults.items():
        setattr(mock, k, v)
    return mock


class TestGetLlmOpenAI:
    """Test get_llm with OpenAI models."""

    @patch("squadx_client.llm.router.ChatOpenAI")
    @patch("squadx_client.llm.router.settings", _make_settings(openai_api_key="sk-test"))
    def test_openai_model(self, mock_chat_openai):
        mock_chat_openai.return_value = MagicMock()
        llm = get_llm("gpt-4o")
        mock_chat_openai.assert_called_once_with(
            model="gpt-4o",
            api_key="sk-test",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.ChatOpenAI")
    @patch("squadx_client.llm.router.settings", _make_settings(openai_api_key="sk-test"))
    def test_o1_model_uses_openai(self, mock_chat_openai):
        mock_chat_openai.return_value = MagicMock()
        get_llm("o1-preview")
        mock_chat_openai.assert_called_once_with(
            model="o1-preview",
            api_key="sk-test",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.settings", _make_settings(openai_api_key=None))
    def test_openai_model_missing_key_raises(self):
        with pytest.raises(ValueError, match="OPENAI_API_KEY"):
            get_llm("gpt-4o")


class TestGetLlmAnthropic:
    """Test get_llm with Anthropic models."""

    @patch("squadx_client.llm.router.ChatAnthropic")
    @patch("squadx_client.llm.router.settings", _make_settings(anthropic_api_key="sk-ant-test"))
    def test_anthropic_model(self, mock_chat_anthropic):
        mock_chat_anthropic.return_value = MagicMock()
        llm = get_llm("claude-3-opus-20240229")
        mock_chat_anthropic.assert_called_once_with(
            model="claude-3-opus-20240229",
            api_key="sk-ant-test",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.settings", _make_settings(anthropic_api_key=None))
    def test_anthropic_model_missing_key_raises(self):
        with pytest.raises(ValueError, match="ANTHROPIC_API_KEY"):
            get_llm("claude-3-sonnet-20240229")


class TestGetLlmDefault:
    """Test get_llm with default/unknown model names."""

    @patch("squadx_client.llm.router.ChatOpenAI")
    @patch("squadx_client.llm.router.settings", _make_settings(openai_api_key="sk-test"))
    def test_default_model_used_when_none(self, mock_chat_openai):
        mock_chat_openai.return_value = MagicMock()
        get_llm(None)
        mock_chat_openai.assert_called_once_with(
            model="gpt-4o",
            api_key="sk-test",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.ChatAnthropic")
    @patch(
        "squadx_client.llm.router.settings",
        _make_settings(openai_api_key=None, anthropic_api_key="sk-ant-test"),
    )
    def test_unknown_model_falls_back_to_anthropic(self, mock_chat_anthropic):
        mock_chat_anthropic.return_value = MagicMock()
        get_llm("some-custom-model")
        mock_chat_anthropic.assert_called_once()

    @patch("squadx_client.llm.router.settings", _make_settings())
    def test_no_keys_raises(self):
        with pytest.raises(ValueError, match="No API key configured"):
            get_llm("some-custom-model")


class TestGetCodingLlm:
    """Test get_coding_llm helper."""

    @patch("squadx_client.llm.router.ChatOpenAI")
    @patch("squadx_client.llm.router.settings", _make_settings(openai_api_key="sk-test"))
    def test_coding_llm_prefers_openai(self, mock_chat_openai):
        mock_chat_openai.return_value = MagicMock()
        get_coding_llm()
        mock_chat_openai.assert_called_once_with(
            model="gpt-4o",
            api_key="sk-test",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.ChatAnthropic")
    @patch(
        "squadx_client.llm.router.settings",
        _make_settings(openai_api_key=None, anthropic_api_key="sk-ant-test"),
    )
    def test_coding_llm_falls_back_to_anthropic(self, mock_chat_anthropic):
        mock_chat_anthropic.return_value = MagicMock()
        get_coding_llm()
        mock_chat_anthropic.assert_called_once_with(
            model="claude-3-opus-20240229",
            api_key="sk-ant-test",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.settings", _make_settings())
    def test_coding_llm_no_keys_raises(self):
        with pytest.raises(ValueError, match="No API key configured"):
            get_coding_llm()


class TestGetFastLlm:
    """Test get_fast_llm helper."""

    @patch("squadx_client.llm.router.ChatOpenAI")
    @patch("squadx_client.llm.router.settings", _make_settings(openai_api_key="sk-test"))
    def test_fast_llm_prefers_openai_mini(self, mock_chat_openai):
        mock_chat_openai.return_value = MagicMock()
        get_fast_llm()
        mock_chat_openai.assert_called_once_with(
            model="gpt-4o-mini",
            api_key="sk-test",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.ChatAnthropic")
    @patch(
        "squadx_client.llm.router.settings",
        _make_settings(openai_api_key=None, anthropic_api_key="sk-ant-test"),
    )
    def test_fast_llm_falls_back_to_haiku(self, mock_chat_anthropic):
        mock_chat_anthropic.return_value = MagicMock()
        get_fast_llm()
        mock_chat_anthropic.assert_called_once_with(
            model="claude-3-haiku-20240307",
            api_key="sk-ant-test",
            temperature=0.7,
        )

    @patch("squadx_client.llm.router.settings", _make_settings())
    def test_fast_llm_no_keys_raises(self):
        with pytest.raises(ValueError, match="No API key configured"):
            get_fast_llm()
