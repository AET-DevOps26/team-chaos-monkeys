"""In-memory LLMProvider stub for unit tests and downstream test fixtures.

Used by:
  - genai-service's own diagnostic tests
  - downstream tickets (#49 extraction, #50 embeddings, #53 notifications)
    which want to test domain logic without touching either real provider

Records every call so tests can assert on inputs. Configurable to raise
arbitrary exceptions for failure-path tests.
"""

from __future__ import annotations

from collections.abc import Callable

from app.providers import Message


class FakeProvider:
    def __init__(
        self,
        *,
        name: str = "fake",
        chat_response: str | Callable[[list[Message], bool], str] = "ok",
        embed_vector: list[float] | Callable[[str], list[float]] | None = None,
        raise_on_chat: Exception | None = None,
        raise_on_embed: Exception | None = None,
    ) -> None:
        # Label used by metric tests; production code reads `llm.name` and
        # routes it onto `genai_provider_requests_total{provider}`.
        self.name = name
        self._chat_response = chat_response
        self._embed_vector: list[float] | Callable[[str], list[float]] = (
            embed_vector if embed_vector is not None else [0.1, 0.2, 0.3]
        )
        self._raise_on_chat = raise_on_chat
        self._raise_on_embed = raise_on_embed
        self.chat_calls: list[tuple[list[Message], bool]] = []
        self.embed_calls: list[str] = []
        self.aclose_called = False

    async def chat(
        self, messages: list[Message], *, json_mode: bool = False
    ) -> str:
        self.chat_calls.append((messages, json_mode))
        if self._raise_on_chat is not None:
            raise self._raise_on_chat
        if callable(self._chat_response):
            return self._chat_response(messages, json_mode)
        return self._chat_response

    async def embed(self, text: str) -> list[float]:
        self.embed_calls.append(text)
        if self._raise_on_embed is not None:
            raise self._raise_on_embed
        if callable(self._embed_vector):
            return self._embed_vector(text)
        return self._embed_vector

    async def aclose(self) -> None:
        self.aclose_called = True
