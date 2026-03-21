"""LLM routing using LiteLLM for multi-provider support.

LiteLLM provides a unified interface to 100+ LLM providers including:
- OpenAI (gpt-4o, gpt-4-turbo, o1)
- Anthropic (claude-3-opus, claude-3-sonnet, claude-3-haiku)
- Google (gemini-pro, gemini-1.5-pro)
- Ollama (local models)
- Azure OpenAI, AWS Bedrock, Vertex AI, and more

See https://docs.litellm.ai/docs/providers for full provider list.
"""

from functools import lru_cache

import structlog
from langchain_community.chat_models import ChatLiteLLM
from langchain_core.language_models import BaseChatModel

from squadx_client.config import settings

logger = structlog.get_logger()


@lru_cache(maxsize=10)
def get_llm(model: str | None = None, temperature: float | None = None) -> BaseChatModel:
    """Get an LLM instance for the specified model via LiteLLM.

    LiteLLM auto-detects the provider from the model name. You can also
    use provider prefixes for explicit routing:
    - "openai/gpt-4o"
    - "anthropic/claude-3-opus-20240229"
    - "gemini/gemini-1.5-pro"
    - "ollama/llama3"
    - "bedrock/anthropic.claude-3-sonnet"

    API keys are read from environment variables automatically by LiteLLM:
    - OPENAI_API_KEY, ANTHROPIC_API_KEY, GEMINI_API_KEY, etc.
    """
    model_name = model or settings.default_model
    logger.debug("getting_llm", model=model_name)

    _ensure_api_key(model_name)

    return ChatLiteLLM(
        model=model_name,
        temperature=temperature if temperature is not None else 0.7,
    )


def get_coding_llm() -> BaseChatModel:
    """Get an LLM optimized for code generation."""
    if settings.openai_api_key:
        return get_llm("gpt-4o")
    elif settings.anthropic_api_key:
        return get_llm("claude-3-opus-20240229")
    else:
        raise ValueError("No API key configured for coding LLM")


def get_fast_llm() -> BaseChatModel:
    """Get a fast LLM for quick operations."""
    if settings.openai_api_key:
        return get_llm("gpt-4o-mini")
    elif settings.anthropic_api_key:
        return get_llm("claude-3-haiku-20240307")
    else:
        raise ValueError("No API key configured for fast LLM")


def _ensure_api_key(model_name: str) -> None:
    """Validate that the required API key is available for the model."""
    if model_name.startswith("gpt") or model_name.startswith("o1") or model_name.startswith("openai/"):
        if not settings.openai_api_key:
            raise ValueError("OPENAI_API_KEY is required for OpenAI models")
    elif model_name.startswith("claude") or model_name.startswith("anthropic/"):
        if not settings.anthropic_api_key:
            raise ValueError("ANTHROPIC_API_KEY is required for Anthropic models")
    # For other providers (gemini, ollama, bedrock, etc.), LiteLLM will
    # raise its own error if the required key is missing.
