"""Integration tests against the real OpenAI API.

Gated by GENAI_RUN_INTEGRATION=1 so they don't gate PR CI. Runs nightly
+ on push to `main` via `.github/workflows/openai-integration.yml`, which
boots genai-service standalone against a real OPENAI_API_KEY.

Also gated on GENAI_PROVIDER=openai so a wider `pytest tests/integration/`
run under the Ollama nightly doesn't accidentally try to call OpenAI without
a key — the Ollama workflow sets GENAI_PROVIDER=local.

To run locally with the real OpenAI key in .env:

    GENAI_RUN_INTEGRATION=1 \\
    GENAI_PROVIDER=openai \\
    OPENAI_API_KEY=sk-... \\
    OPENAI_CHAT_MODEL=gpt-4o-mini \\
    OPENAI_EMBED_MODEL=text-embedding-3-small \\
    EMBEDDING_DIMENSIONS=768 \\
    pytest tests/integration/test_real_openai_provider.py -v
"""

from __future__ import annotations

import os

import pytest

from app.providers.openai import OpenAIProvider

pytestmark = pytest.mark.skipif(
    os.getenv("GENAI_RUN_INTEGRATION") != "1" or os.getenv("GENAI_PROVIDER") != "openai",
    reason=(
        "set GENAI_RUN_INTEGRATION=1 and GENAI_PROVIDER=openai (with OPENAI_API_KEY) "
        "to run integration tests against the real OpenAI API"
    ),
)


@pytest.fixture
def embedding_dimensions() -> int:
    return int(os.getenv("EMBEDDING_DIMENSIONS", "768"))


@pytest.fixture
def provider(embedding_dimensions: int) -> OpenAIProvider:
    api_key = os.environ["OPENAI_API_KEY"]
    return OpenAIProvider(
        api_key=api_key,
        chat_model=os.getenv("OPENAI_CHAT_MODEL", "gpt-4o-mini"),
        embed_model=os.getenv("OPENAI_EMBED_MODEL", "text-embedding-3-small"),
        embedding_dimensions=embedding_dimensions,
        timeout_seconds=int(os.getenv("GENAI_TIMEOUT_SECONDS", "60")),
    )


async def test_chat_returns_nonempty_string(provider: OpenAIProvider) -> None:
    result = await provider.chat(
        [
            {"role": "system", "content": "Reply with exactly one word."},
            {"role": "user", "content": "What colour is the sky on a clear day?"},
        ]
    )

    assert isinstance(result, str)
    assert len(result) > 0


async def test_chat_json_mode_returns_parseable_json(provider: OpenAIProvider) -> None:
    import json

    result = await provider.chat(
        [
            {
                "role": "user",
                "content": (
                    "Respond ONLY with a JSON object with two keys "
                    "'color' and 'shape'. Example: "
                    '{"color":"red","shape":"circle"}'
                ),
            }
        ],
        json_mode=True,
    )

    parsed = json.loads(result)
    assert isinstance(parsed, dict)


async def test_embed_returns_dense_vector(provider: OpenAIProvider) -> None:
    vector = await provider.embed("a black leather wallet")

    assert isinstance(vector, list)
    assert len(vector) > 0
    assert all(isinstance(x, float) for x in vector)


async def test_embed_returns_configured_dimensions(
    provider: OpenAIProvider, embedding_dimensions: int
) -> None:
    # The OpenAI provider passes `dimensions=` to text-embedding-3-small;
    # this catches a regression where that argument is dropped or the
    # model's effective dimension drifts from what we configure.
    vector = await provider.embed("a small umbrella with a wooden handle")

    assert len(vector) == embedding_dimensions
