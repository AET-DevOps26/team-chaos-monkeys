"""LLM provider abstraction.

Defines the `LLMProvider` protocol — two primitives, `chat` and `embed` —
plus a `build_provider` factory that picks an implementation from settings.

Domain logic (prompt templates, JSON schema validation, notification
templating) sits on top of these primitives. Providers know nothing about
the GenAI service's HTTP surface or its callers.
"""

from __future__ import annotations

from typing import Literal, Protocol, TypedDict, runtime_checkable

from app.config import Settings


class Message(TypedDict):
    role: Literal["system", "user", "assistant"]
    content: str


@runtime_checkable
class LLMProvider(Protocol):
    async def chat(
        self, messages: list[Message], *, json_mode: bool = False
    ) -> str: ...

    async def embed(self, text: str) -> list[float]: ...

    async def aclose(self) -> None: ...


def build_provider(settings: Settings) -> LLMProvider:
    if settings.provider == "openai":
        from app.providers.openai import OpenAIProvider

        return OpenAIProvider(
            api_key=settings.openai_api_key or "",
            chat_model=settings.openai_chat_model,
            embed_model=settings.openai_embed_model,
            timeout_seconds=settings.timeout_seconds,
        )
    if settings.provider == "local":
        from app.providers.ollama import OllamaProvider

        return OllamaProvider(
            base_url=settings.ollama_base_url,
            chat_model=settings.ollama_chat_model,
            embed_model=settings.ollama_embed_model,
            timeout_seconds=settings.timeout_seconds,
        )
    raise ValueError(f"unknown provider: {settings.provider!r}")


__all__ = ["LLMProvider", "Message", "build_provider"]
