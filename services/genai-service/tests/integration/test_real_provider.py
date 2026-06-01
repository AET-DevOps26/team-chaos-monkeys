"""Integration tests against a real Ollama backend.

Gated by GENAI_RUN_INTEGRATION=1 so they don't gate PR CI. Runs nightly
+ on push to `main` via `.github/workflows/genai-integration.yml`, which
spins up Ollama with a small model in a service container.

To run locally with the compose stack already up:

    GENAI_RUN_INTEGRATION=1 \\
    OLLAMA_BASE_URL=http://localhost:11434 \\
    OLLAMA_CHAT_MODEL=llama3.2:3b \\
    OLLAMA_EMBED_MODEL=nomic-embed-text \\
    pytest tests/integration/
"""

from __future__ import annotations

import os

import pytest

from app.providers.ollama import OllamaProvider

pytestmark = pytest.mark.skipif(
    os.getenv("GENAI_RUN_INTEGRATION") != "1",
    reason="set GENAI_RUN_INTEGRATION=1 to run integration tests against a real Ollama",
)


@pytest.fixture
def provider() -> OllamaProvider:
    return OllamaProvider(
        base_url=os.getenv("OLLAMA_BASE_URL", "http://localhost:11434"),
        chat_model=os.getenv("OLLAMA_CHAT_MODEL", "llama3.2:1b"),
        vision_model=os.getenv("OLLAMA_VISION_MODEL", "llava:7b"),
        embed_model=os.getenv("OLLAMA_EMBED_MODEL", "nomic-embed-text"),
        embedding_dimensions=int(os.getenv("EMBEDDING_DIMENSIONS", "768")),
        timeout_seconds=int(os.getenv("GENAI_TIMEOUT_SECONDS", "60")),
    )


async def test_chat_returns_nonempty_string(provider: OllamaProvider) -> None:
    result = await provider.chat(
        [
            {"role": "system", "content": "Reply with exactly one word."},
            {"role": "user", "content": "What colour is the sky on a clear day?"},
        ]
    )

    assert isinstance(result, str)
    assert len(result) > 0


async def test_chat_json_mode_returns_parseable_json(provider: OllamaProvider) -> None:
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


async def test_embed_returns_dense_vector(provider: OllamaProvider) -> None:
    vector = await provider.embed("a black leather wallet")

    assert isinstance(vector, list)
    assert len(vector) > 0
    assert all(isinstance(x, float) for x in vector)
