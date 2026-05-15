"""OpenAI provider adapter.

Wraps the official `openai` async SDK and translates its exception
hierarchy onto the service-internal `LLMError` hierarchy. Retries are
disabled at the SDK level — the upstream caller (Spring service /
RabbitMQ consumer) decides retry policy.
"""

from __future__ import annotations

import openai
from openai import AsyncOpenAI

from app.exceptions import (
    LLMBadRequestError,
    LLMError,
    LLMRateLimitError,
    LLMTimeoutError,
    LLMUnavailableError,
)
from app.providers import Message


class OpenAIProvider:
    def __init__(
        self,
        *,
        api_key: str,
        chat_model: str,
        embed_model: str,
        timeout_seconds: int,
    ) -> None:
        self._client = AsyncOpenAI(
            api_key=api_key,
            timeout=float(timeout_seconds),
            max_retries=0,
        )
        self._chat_model = chat_model
        self._embed_model = embed_model

    async def chat(
        self, messages: list[Message], *, json_mode: bool = False
    ) -> str:
        kwargs: dict = {
            "model": self._chat_model,
            "messages": messages,
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

        return list(response.data[0].embedding)

    async def aclose(self) -> None:
        await self._client.close()
