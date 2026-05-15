"""Ollama provider adapter.

Wraps the official `ollama` async SDK and translates its (much smaller)
exception surface onto the service-internal `LLMError` hierarchy.

The Ollama SDK raises:
  - `ollama.ResponseError(error, status_code)` for HTTP errors from the
    Ollama server (e.g. model not found -> 404, server overloaded -> 503).
  - `ollama.RequestError` for client-side request construction errors.
  - `httpx.TimeoutException` for read/connect timeouts.
  - `httpx.ConnectError` / `httpx.RemoteProtocolError` for unreachable host.
"""

from __future__ import annotations

import httpx
import ollama
from ollama import AsyncClient

from app.exceptions import (
    LLMBadRequestError,
    LLMError,
    LLMRateLimitError,
    LLMTimeoutError,
    LLMUnavailableError,
)
from app.providers import Message


def _map_response_error(e: ollama.ResponseError) -> LLMError:
    status = e.status_code
    if status == 429:
        return LLMRateLimitError(str(e))
    if status == 400 or status == 404:
        return LLMBadRequestError(str(e))
    if status and 500 <= status < 600:
        return LLMUnavailableError(str(e))
    return LLMError(str(e))


class OllamaProvider:
    def __init__(
        self,
        *,
        base_url: str,
        chat_model: str,
        embed_model: str,
        timeout_seconds: int,
    ) -> None:
        self._client = AsyncClient(host=base_url, timeout=float(timeout_seconds))
        self._chat_model = chat_model
        self._embed_model = embed_model

    async def chat(
        self, messages: list[Message], *, json_mode: bool = False
    ) -> str:
        try:
            response = await self._client.chat(
                model=self._chat_model,
                messages=list(messages),
                format="json" if json_mode else "",
            )
        except ollama.ResponseError as e:
            raise _map_response_error(e) from e
        except ollama.RequestError as e:
            raise LLMBadRequestError(str(e)) from e
        except httpx.TimeoutException as e:
            raise LLMTimeoutError(str(e)) from e
        except ConnectionError as e:
            # Ollama SDK wraps httpx.ConnectError into builtin ConnectionError.
            raise LLMUnavailableError(str(e)) from e
        except (httpx.ConnectError, httpx.RemoteProtocolError, httpx.NetworkError) as e:
            raise LLMUnavailableError(str(e)) from e

        return response.message.content or ""

    async def embed(self, text: str) -> list[float]:
        try:
            response = await self._client.embed(
                model=self._embed_model,
                input=text,
            )
        except ollama.ResponseError as e:
            raise _map_response_error(e) from e
        except ollama.RequestError as e:
            raise LLMBadRequestError(str(e)) from e
        except httpx.TimeoutException as e:
            raise LLMTimeoutError(str(e)) from e
        except ConnectionError as e:
            # Ollama SDK wraps httpx.ConnectError into builtin ConnectionError.
            raise LLMUnavailableError(str(e)) from e
        except (httpx.ConnectError, httpx.RemoteProtocolError, httpx.NetworkError) as e:
            raise LLMUnavailableError(str(e)) from e

        embeddings = response.embeddings
        if not embeddings:
            raise LLMError("ollama returned no embeddings")
        return list(embeddings[0])

    async def aclose(self) -> None:
        # AsyncClient holds an httpx.AsyncClient internally; close it for
        # clean connection-pool shutdown.
        inner = getattr(self._client, "_client", None)
        if inner is not None and hasattr(inner, "aclose"):
            await inner.aclose()
