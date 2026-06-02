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


class TextContentPart(TypedDict):
    type: Literal["text"]
    text: str


class ImageContentPart(TypedDict):
    """Multimodal image part — a single image attached to a user turn.

    `dataBase64` mirrors the wire field name from `ImageContent` in
    `app.api.schemas` so message construction can carry the request payload
    through unchanged. Adapters translate this into provider-specific shapes
    (OpenAI: data URL; Ollama: separate `images` field on the message).
    """

    type: Literal["image"]
    contentType: str
    dataBase64: str


ContentPart = TextContentPart | ImageContentPart


class Message(TypedDict):
    role: Literal["system", "user", "assistant"]
    # Either a plain string (text-only — the original shape) or a list of
    # content parts for multimodal turns. Existing text-only call sites
    # keep using strings; the vision-aware extraction path (issue #90)
    # emits content-part lists.
    content: str | list[ContentPart]


def has_image_content(messages: list[Message]) -> bool:
    """True if any message in `messages` carries an image content part.

    Used by adapters to decide whether to route a call to a vision-capable
    model. Cheap — O(n) over messages and content parts; called once per
    chat invocation.
    """
    for msg in messages:
        content = msg["content"]
        if isinstance(content, list):
            for part in content:
                if part["type"] == "image":
                    return True
    return False


@runtime_checkable
class LLMProvider(Protocol):
    # Short, stable identifier used as a metric label. Mirrors the
    # `GENAI_PROVIDER` setting ("openai" / "local") in production
    # implementations so dashboards can be filtered consistently.
    name: str

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
            vision_model=settings.ollama_vision_model,
            embed_model=settings.ollama_embed_model,
            timeout_seconds=settings.timeout_seconds,
        )
    if settings.provider == "fake":
        from app.providers.fake import FakeProvider

        # Canned JSON shaped like `ItemAttributes`. Lets docker-compose E2E
        # and downstream Spring service integration tests exercise the full
        # /extract-attributes path without OpenAI or Ollama.
        return FakeProvider(
            name="fake",
            chat_response=(
                '{"category":"jacket","brand":null,"color":"black",'
                '"distinguishingMarks":[],"approximateTime":null,'
                '"location":null}'
            ),
        )
    raise ValueError(f"unknown provider: {settings.provider!r}")


__all__ = [
    "ContentPart",
    "ImageContentPart",
    "LLMProvider",
    "Message",
    "TextContentPart",
    "build_provider",
    "has_image_content",
]
