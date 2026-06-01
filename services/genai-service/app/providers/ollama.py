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

from typing import Any

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
from app.providers import Message, has_image_content


def _to_ollama_message(msg: Message) -> dict[str, Any]:
    """Translate a `Message` into the Ollama chat API shape.

    Ollama splits multimodal content across two fields on a single message:
    `content` (the joined text) and `images` (a list of base64 strings).
    Our `Message.content` may be a plain string or a list of content parts;
    list inputs are flattened — text parts joined by a single newline,
    image parts collected into the `images` array.

    A message with only an image is admitted with `content=""`; the model
    handles this fine and the explicit empty string is preferable to a
    missing field on the wire.
    """
    content = msg["content"]
    if isinstance(content, str):
        return {"role": msg["role"], "content": content}

    text_chunks: list[str] = []
    images: list[str] = []
    for part in content:
        if part["type"] == "text":
            text_chunks.append(part["text"])
        else:
            images.append(part["dataBase64"])
    out: dict[str, Any] = {"role": msg["role"], "content": "\n".join(text_chunks)}
    if images:
        out["images"] = images
    return out


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
    name = "local"

    def __init__(
        self,
        *,
        base_url: str,
        chat_model: str,
        vision_model: str,
        embed_model: str,
        embedding_dimensions: int,
        timeout_seconds: int,
    ) -> None:
        self._client = AsyncClient(host=base_url, timeout=float(timeout_seconds))
        self._chat_model = chat_model
        # Used when an incoming chat carries an image content part.
        # See ADR 0001 §7 — split text/vision models keep the text-path
        # performance characteristics unchanged.
        self._vision_model = vision_model
        self._embed_model = embed_model
        self._embedding_dimensions = embedding_dimensions

    async def chat(
        self, messages: list[Message], *, json_mode: bool = False
    ) -> str:
        model = self._vision_model if has_image_content(messages) else self._chat_model
        wire_messages = [_to_ollama_message(msg) for msg in messages]
        try:
            response = await self._client.chat(
                model=model,
                messages=wire_messages,
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
        embedding = list(embeddings[0])
        if len(embedding) != self._embedding_dimensions:
            raise ValueError(
                f"Expected {self._embedding_dimensions} dims but Ollama model "
                f"{self._embed_model} returned {len(embedding)}. "
                f"Pick an embed model whose natural dim matches."
            )
        return embedding

    async def aclose(self) -> None:
        # AsyncClient holds an httpx.AsyncClient internally; close it for
        # clean connection-pool shutdown.
        inner = getattr(self._client, "_client", None)
        if inner is not None and hasattr(inner, "aclose"):
            await inner.aclose()
