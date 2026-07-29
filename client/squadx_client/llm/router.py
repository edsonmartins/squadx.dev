"""LLM routing using LiteLLM for multi-provider support.

LiteLLM provides a unified interface to 100+ LLM providers including:
- OpenAI (gpt-4o, gpt-4-turbo, o1)
- Anthropic (claude-3-opus, claude-3-sonnet, claude-3-haiku)
- Google (gemini-pro, gemini-1.5-pro)
- OpenRouter (openrouter/openai/gpt-4o-mini, openrouter/anthropic/...)
- Ollama (local models)
- Azure OpenAI, AWS Bedrock, Vertex AI, and more

See https://docs.litellm.ai/docs/providers for full provider list.
"""

from __future__ import annotations

import os
from functools import lru_cache

import structlog
from langchain_community.chat_models import ChatLiteLLM
from langchain_core.language_models import BaseChatModel

from squadx_client.config import settings

logger = structlog.get_logger()


def _export_keys_to_environ() -> None:
    """Ensure LiteLLM sees keys from pydantic settings (loaded from client/.env).

    pydantic-settings binds fields on ``settings`` but does not always put them
    into ``os.environ``; LiteLLM only reads the process environment.
    """
    mapping = {
        "OPENAI_API_KEY": settings.openai_api_key,
        "ANTHROPIC_API_KEY": settings.anthropic_api_key,
        "GOOGLE_API_KEY": settings.google_api_key,
        "GEMINI_API_KEY": settings.google_api_key,
        "OPENROUTER_API_KEY": settings.openrouter_api_key,
    }
    for env_name, value in mapping.items():
        if value and not os.environ.get(env_name):
            os.environ[env_name] = value


def _has_openrouter() -> bool:
    return bool(settings.openrouter_api_key or os.environ.get("OPENROUTER_API_KEY"))


def _has_openai() -> bool:
    return bool(settings.openai_api_key or os.environ.get("OPENAI_API_KEY"))


def _has_anthropic() -> bool:
    return bool(settings.anthropic_api_key or os.environ.get("ANTHROPIC_API_KEY"))


@lru_cache(maxsize=10)
def get_llm(model: str | None = None, temperature: float | None = None) -> BaseChatModel:
    """Get an LLM instance for the specified model via LiteLLM.

    LiteLLM auto-detects the provider from the model name. You can also
    use provider prefixes for explicit routing:
    - "openai/gpt-4o"
    - "anthropic/claude-3-opus-20240229"
    - "openrouter/openai/gpt-4o-mini"
    - "gemini/gemini-1.5-pro"
    - "ollama/llama3"
    - "bedrock/anthropic.claude-3-sonnet"

    API keys: OPENAI_API_KEY, ANTHROPIC_API_KEY, OPENROUTER_API_KEY, GEMINI_API_KEY, …
    """
    model_name = model or settings.default_model
    logger.debug("getting_llm", model=model_name)

    _export_keys_to_environ()
    _ensure_api_key(model_name)

    return ChatLiteLLM(
        model=model_name,
        temperature=temperature if temperature is not None else 0.7,
    )


def get_coding_llm() -> BaseChatModel:
    """Get an LLM optimized for code generation."""
    # Prefer explicit default when it is already an OpenRouter (or other) route.
    default = settings.default_model or ""
    if default.startswith("openrouter/") and _has_openrouter():
        return get_llm(default)
    if _has_openai():
        return get_llm("gpt-4o")
    if _has_anthropic():
        return get_llm("claude-3-opus-20240229")
    if _has_openrouter():
        # Fallback when only OpenRouter is configured
        return get_llm(default if default.startswith("openrouter/") else "openrouter/openai/gpt-4o-mini")
    raise ValueError(
        "No API key configured for coding LLM "
        "(set OPENAI_API_KEY, ANTHROPIC_API_KEY, or OPENROUTER_API_KEY)"
    )


def get_fast_llm() -> BaseChatModel:
    """Get a fast LLM for quick operations."""
    default = settings.default_model or ""
    if default.startswith("openrouter/") and _has_openrouter():
        return get_llm(default)
    if _has_openai():
        return get_llm("gpt-4o-mini")
    if _has_anthropic():
        return get_llm("claude-3-haiku-20240307")
    if _has_openrouter():
        return get_llm(default if default.startswith("openrouter/") else "openrouter/openai/gpt-4o-mini")
    raise ValueError(
        "No API key configured for fast LLM "
        "(set OPENAI_API_KEY, ANTHROPIC_API_KEY, or OPENROUTER_API_KEY)"
    )


def _ensure_api_key(model_name: str) -> None:
    """Validate that the required API key is available for the model."""
    if model_name.startswith("openrouter/"):
        if not _has_openrouter():
            raise ValueError("OPENROUTER_API_KEY is required for openrouter/ models")
    elif model_name.startswith("gpt") or model_name.startswith("o1") or model_name.startswith("openai/"):
        if not _has_openai():
            raise ValueError("OPENAI_API_KEY is required for OpenAI models")
    elif model_name.startswith("claude") or model_name.startswith("anthropic/"):
        if not _has_anthropic():
            raise ValueError("ANTHROPIC_API_KEY is required for Anthropic models")
    # For other providers (gemini, ollama, bedrock, etc.), LiteLLM will
    # raise its own error if the required key is missing.

