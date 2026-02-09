"""LLM routing using LiteLLM for multi-provider support."""

from functools import lru_cache

import structlog
from langchain_openai import ChatOpenAI
from langchain_anthropic import ChatAnthropic
from langchain_core.language_models import BaseChatModel

from squadx_client.config import settings

logger = structlog.get_logger()


@lru_cache(maxsize=10)
def get_llm(model: str | None = None) -> BaseChatModel:
    """Get an LLM instance for the specified model.

    Supports:
    - OpenAI models (gpt-4o, gpt-4-turbo, gpt-3.5-turbo)
    - Anthropic models (claude-3-opus, claude-3-sonnet, claude-3-haiku)
    """
    model_name = model or settings.default_model
    logger.debug("getting_llm", model=model_name)

    # Determine provider from model name
    if model_name.startswith("gpt") or model_name.startswith("o1"):
        if not settings.openai_api_key:
            raise ValueError("OPENAI_API_KEY is required for OpenAI models")
        return ChatOpenAI(
            model=model_name,
            api_key=settings.openai_api_key,
            temperature=0.7,
        )
    elif model_name.startswith("claude"):
        if not settings.anthropic_api_key:
            raise ValueError("ANTHROPIC_API_KEY is required for Anthropic models")
        return ChatAnthropic(
            model=model_name,
            api_key=settings.anthropic_api_key,
            temperature=0.7,
        )
    else:
        # Default to OpenAI
        if settings.openai_api_key:
            return ChatOpenAI(
                model=model_name,
                api_key=settings.openai_api_key,
                temperature=0.7,
            )
        elif settings.anthropic_api_key:
            return ChatAnthropic(
                model="claude-3-sonnet-20240229",
                api_key=settings.anthropic_api_key,
                temperature=0.7,
            )
        else:
            raise ValueError("No API key configured for LLM")


def get_coding_llm() -> BaseChatModel:
    """Get an LLM optimized for code generation."""
    # Prefer GPT-4 or Claude Opus for coding tasks
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
