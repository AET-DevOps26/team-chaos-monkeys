"""OpenAI provider adapter.

Wraps the official `openai` async SDK and translates its exception
hierarchy onto the service-internal `LLMError` hierarchy. Retries are
disabled at the SDK level — the upstream caller (Spring service /
RabbitMQ consumer) decides retry policy.
"""

from __future__ import annotations

from typing import Any

import openai
from openai import AsyncOpenAI

from app.exceptions import (
    LLMBadRequestError,
    LLMError,
    LLMRateLimitError,
    LLMTimeoutError,
    LLMUnavailableError,
)
from app.providers import ContentPart, Message


def _to_openai_content(content: str | list[ContentPart]) -> str | list[dict[str, Any]]:
    """Translate our message content to OpenAI's Chat Completions shape.

    Plain strings pass through (the SDK accepts them on user/system turns).
    Lists of content parts become OpenAI's `[{type, ...}]` schema:
      - text parts → `{type: "text", text}`
      - image parts → `{type: "image_url", image_url: {url: "data:<mime>;base64,<...>"}}`

    Wrapping image bytes in a data URL keeps the adapter stateless — no
    transient URL hosting, no provider-side fetch — and matches the
    inline-base64 transport chosen in ADR 0001.
    """
    if isinstance(content, str):
        return content
    out: list[dict[str, Any]] = []
    for part in content:
        if part["type"] == "text":
            out.append({"type": "text", "text": part["text"]})
        else:
            data_url = f"data:{part['contentType']};base64,{part['dataBase64']}"
            out.append({"type": "image_url", "image_url": {"url": data_url}})
    return out


class OpenAIProvider:
    name = "openai"

    def __init__(
        self,
        *,
        api_key: str,
        chat_model: str,
        embed_model: str,
        embedding_dimensions: int,
        timeout_seconds: int,
    ) -> None:
        self._client = AsyncOpenAI(
            api_key=api_key,
            timeout=float(timeout_seconds),
            max_retries=0,
        )
        self._chat_model = chat_model
        self._embed_model = embed_model
        self._embedding_dimensions = embedding_dimensions

    async def chat(
        self, messages: list[Message], *, json_mode: bool = False
    ) -> str:
        wire_messages = [
            {"role": msg["role"], "content": _to_openai_content(msg["content"])}
            for msg in messages
        ]
        kwargs: dict = {
            "model": self._chat_model,
            "messages": wire_messages,
        }
        if json_mode:
            kwargs["response_format"] = {"type": "json_object"}
        try:
            response = await self._client.chat.completions.create(**kwargs)
        except openai.RateLimitError as e:
            raise LLMRateLimitError(str(e)) from e
        except openai.APITimeoutError as e:
            raise LLMTimeoutError(str(e)) from e
        except (openai.APIConnectionError, openai.InternalServerError) as e:
            raise LLMUnavailableError(str(e)) from e
        except openai.BadRequestError as e:
            raise LLMBadRequestError(str(e)) from e
        except openai.APIStatusError as e:
            if e.status_code == 429:
                raise LLMRateLimitError(str(e)) from e
            if e.status_code and 500 <= e.status_code < 600:
                raise LLMUnavailableError(str(e)) from e
            raise LLMError(str(e)) from e
        except openai.APIError as e:
            raise LLMError(str(e)) from e

        content = response.choices[0].message.content
        return content or ""

    async def embed(self, text: str) -> list[float]:
        try:
            response = await self._client.embeddings.create(
                model=self._embed_model,
                input=text,
                dimensions=self._embedding_dimensions,
            )
        except openai.RateLimitError as e:
            raise LLMRateLimitError(str(e)) from e
        except openai.APITimeoutError as e:
            raise LLMTimeoutError(str(e)) from e
        except (openai.APIConnectionError, openai.InternalServerError) as e:
            raise LLMUnavailableError(str(e)) from e
        except openai.BadRequestError as e:
            raise LLMBadRequestError(str(e)) from e
        except openai.APIStatusError as e:
            if e.status_code == 429:
                raise LLMRateLimitError(str(e)) from e
            if e.status_code and 500 <= e.status_code < 600:
                raise LLMUnavailableError(str(e)) from e
            raise LLMError(str(e)) from e
        except openai.APIError as e:
            raise LLMError(str(e)) from e

        embedding = list(response.data[0].embedding)
        if len(embedding) != self._embedding_dimensions:
            raise ValueError(
                f"Expected {self._embedding_dimensions} dims but OpenAI returned "
                f"{len(embedding)} (model={self._embed_model}). "
                f"Pick a model that honours the dimensions= parameter."
            )
        return embedding

    async def aclose(self) -> None:
        await self._client.close()
