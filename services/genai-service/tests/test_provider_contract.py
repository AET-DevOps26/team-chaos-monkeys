"""Provider contract tests — the load-bearing test for AC
"both adapters share the same internal interface".

Each test runs once per provider via parametrize. The `ProviderHarness`
abstracts away the per-provider HTTP shape (different URLs, different
request/response bodies) so the actual assertions are identical.

If a new failure mode is added to `LLMError`, or one provider drifts
from the other (e.g. silently swallows timeouts), this file fails first.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Callable

import httpx
import pytest
import respx

from app.exceptions import (
    LLMBadRequestError,
    LLMError,
    LLMRateLimitError,
    LLMTimeoutError,
    LLMUnavailableError,
)
from app.providers import LLMProvider
from app.providers.ollama import OllamaProvider
from app.providers.openai import OpenAIProvider

# ---------------------------------------------------------------------------
# Per-provider harness: same surface, different wire format underneath
# ---------------------------------------------------------------------------


@dataclass
class ProviderHarness:
    name: str
    provider: LLMProvider
    chat_route: respx.Route
    embed_route: respx.Route
    # Function that, given the last captured request, returns whether
    # the json_mode flag is set in the provider's native way.
    json_flag_in_request: Callable[[httpx.Request], bool]
    # Build a happy-path chat response body (text content)
    build_chat_response: Callable[[str], dict]
    # Build a happy-path embed response body (vector)
    build_embed_response: Callable[[list[float]], dict]


def _openai_harness(respx_mock: respx.MockRouter) -> ProviderHarness:
    provider = OpenAIProvider(
        api_key="sk-test",
        chat_model="gpt-4o-mini",
        embed_model="text-embedding-3-small",
        embedding_dimensions=3,
        timeout_seconds=5,
    )
    chat = respx_mock.post("https://api.openai.com/v1/chat/completions")
    embed = respx_mock.post("https://api.openai.com/v1/embeddings")

    def _json_flag(req: httpx.Request) -> bool:
        body = json.loads(req.content.decode())
        return body.get("response_format", {}).get("type") == "json_object"

    def _chat_body(text: str) -> dict:
        return {
            "id": "chatcmpl-x",
            "object": "chat.completion",
            "created": 0,
            "model": "gpt-4o-mini",
            "choices": [
                {
                    "index": 0,
                    "message": {"role": "assistant", "content": text},
                    "finish_reason": "stop",
                }
            ],
        }

    def _embed_body(vec: list[float]) -> dict:
        return {
            "object": "list",
            "model": "text-embedding-3-small",
            "data": [{"object": "embedding", "index": 0, "embedding": vec}],
        }

    return ProviderHarness(
        name="openai",
        provider=provider,
        chat_route=chat,
        embed_route=embed,
        json_flag_in_request=_json_flag,
        build_chat_response=_chat_body,
        build_embed_response=_embed_body,
    )


def _ollama_harness(respx_mock: respx.MockRouter) -> ProviderHarness:
    provider = OllamaProvider(
        base_url="http://ollama-test:11434",
        chat_model="llama3.2:3b",
        vision_model="llava:7b",
        embed_model="nomic-embed-text",
        timeout_seconds=5,
    )
    chat = respx_mock.post("http://ollama-test:11434/api/chat")
    embed = respx_mock.post("http://ollama-test:11434/api/embed")

    def _json_flag(req: httpx.Request) -> bool:
        body = json.loads(req.content.decode())
        return body.get("format") == "json"

    def _chat_body(text: str) -> dict:
        return {
            "model": "llama3.2:3b",
            "created_at": "2026-05-14T00:00:00Z",
            "done": True,
            "message": {"role": "assistant", "content": text},
        }

    def _embed_body(vec: list[float]) -> dict:
        return {
            "model": "nomic-embed-text",
            "created_at": "2026-05-14T00:00:00Z",
            "done": True,
            "embeddings": [vec],
        }

    return ProviderHarness(
        name="ollama",
        provider=provider,
        chat_route=chat,
        embed_route=embed,
        json_flag_in_request=_json_flag,
        build_chat_response=_chat_body,
        build_embed_response=_embed_body,
    )


@pytest.fixture(params=["openai", "ollama"])
def harness(
    request: pytest.FixtureRequest, respx_mock: respx.MockRouter
) -> ProviderHarness:
    if request.param == "openai":
        return _openai_harness(respx_mock)
    return _ollama_harness(respx_mock)


# ---------------------------------------------------------------------------
# Happy path
# ---------------------------------------------------------------------------


async def test_chat_returns_string(harness: ProviderHarness) -> None:
    harness.chat_route.mock(
        return_value=httpx.Response(200, json=harness.build_chat_response("hello"))
    )

    result = await harness.provider.chat([{"role": "user", "content": "hi"}])

    assert isinstance(result, str)
    assert result == "hello"


async def test_chat_json_mode_sets_provider_flag(harness: ProviderHarness) -> None:
    harness.chat_route.mock(
        return_value=httpx.Response(200, json=harness.build_chat_response('{"k":"v"}'))
    )

    await harness.provider.chat(
        [{"role": "user", "content": "give me json"}], json_mode=True
    )

    assert harness.chat_route.called
    last_request = harness.chat_route.calls.last.request
    assert harness.json_flag_in_request(last_request), (
        f"{harness.name}: json_mode=True did not set the native provider flag"
    )


async def test_chat_json_mode_omitted_by_default(harness: ProviderHarness) -> None:
    harness.chat_route.mock(
        return_value=httpx.Response(200, json=harness.build_chat_response("plain"))
    )

    await harness.provider.chat([{"role": "user", "content": "hi"}])

    last_request = harness.chat_route.calls.last.request
    assert not harness.json_flag_in_request(last_request), (
        f"{harness.name}: native json flag was set even though json_mode=False"
    )


async def test_embed_returns_list_of_floats(harness: ProviderHarness) -> None:
    harness.embed_route.mock(
        return_value=httpx.Response(
            200, json=harness.build_embed_response([0.1, 0.2, 0.3])
        )
    )

    result = await harness.provider.embed("hello world")

    assert isinstance(result, list)
    assert len(result) == 3
    assert all(isinstance(x, float) for x in result)
    assert result == [0.1, 0.2, 0.3]


# ---------------------------------------------------------------------------
# Error normalization
# ---------------------------------------------------------------------------


async def test_chat_rate_limited_raises_rate_limit_error(
    harness: ProviderHarness,
) -> None:
    harness.chat_route.mock(
        return_value=httpx.Response(
            429, json={"error": {"message": "rate limited", "type": "rate_limit"}}
        )
    )

    with pytest.raises(LLMRateLimitError):
        await harness.provider.chat([{"role": "user", "content": "hi"}])


async def test_chat_server_error_raises_unavailable(
    harness: ProviderHarness,
) -> None:
    harness.chat_route.mock(
        return_value=httpx.Response(
            503, json={"error": {"message": "overloaded", "type": "server_error"}}
        )
    )

    with pytest.raises(LLMUnavailableError):
        await harness.provider.chat([{"role": "user", "content": "hi"}])


async def test_chat_bad_request_raises_bad_request(
    harness: ProviderHarness,
) -> None:
    harness.chat_route.mock(
        return_value=httpx.Response(
            400, json={"error": {"message": "model not found", "type": "invalid_request"}}
        )
    )

    with pytest.raises(LLMBadRequestError):
        await harness.provider.chat([{"role": "user", "content": "hi"}])


async def test_chat_timeout_raises_timeout(harness: ProviderHarness) -> None:
    harness.chat_route.mock(side_effect=httpx.TimeoutException("read timeout"))

    with pytest.raises(LLMTimeoutError):
        await harness.provider.chat([{"role": "user", "content": "hi"}])


async def test_chat_connect_error_raises_unavailable(
    harness: ProviderHarness,
) -> None:
    harness.chat_route.mock(side_effect=httpx.ConnectError("connection refused"))

    with pytest.raises(LLMUnavailableError):
        await harness.provider.chat([{"role": "user", "content": "hi"}])


async def test_embed_rate_limited_raises_rate_limit_error(
    harness: ProviderHarness,
) -> None:
    harness.embed_route.mock(
        return_value=httpx.Response(
            429, json={"error": {"message": "rate limited", "type": "rate_limit"}}
        )
    )

    with pytest.raises(LLMRateLimitError):
        await harness.provider.embed("hi")


async def test_embed_timeout_raises_timeout(harness: ProviderHarness) -> None:
    harness.embed_route.mock(side_effect=httpx.TimeoutException("read timeout"))

    with pytest.raises(LLMTimeoutError):
        await harness.provider.embed("hi")


# ---------------------------------------------------------------------------
# All LLM*Error types are subclasses of LLMError so a single
# `except LLMError` covers all failure modes in caller code.
# ---------------------------------------------------------------------------


@pytest.mark.parametrize(
    "exc",
    [
        LLMRateLimitError,
        LLMTimeoutError,
        LLMUnavailableError,
        LLMBadRequestError,
    ],
)
def test_all_errors_subclass_llm_error(exc: type[Exception]) -> None:
    assert issubclass(exc, LLMError)
