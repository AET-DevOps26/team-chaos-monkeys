import pytest
import respx
from httpx import Response

from app.providers.openai import OpenAIProvider


@respx.mock
@pytest.mark.asyncio
async def test_openai_embed_passes_dimensions_to_sdk():
    route = respx.post("https://api.openai.com/v1/embeddings").mock(
        return_value=Response(
            200,
            json={
                "object": "list",
                "data": [{"object": "embedding", "embedding": [0.0] * 768, "index": 0}],
                "model": "text-embedding-3-small",
                "usage": {"prompt_tokens": 1, "total_tokens": 1},
            },
        )
    )

    provider = OpenAIProvider(
        api_key="sk-test",
        chat_model="gpt-4o-mini",
        embed_model="text-embedding-3-small",
        embedding_dimensions=768,
        timeout_seconds=10,
    )

    vec = await provider.embed("hello")
    assert len(vec) == 768

    sent = route.calls.last.request
    body = sent.read().decode("utf-8")
    assert '"dimensions":768' in body or '"dimensions": 768' in body


@respx.mock
@pytest.mark.asyncio
async def test_openai_embed_rejects_wrong_dim_response():
    """Provider should raise when OpenAI returns the wrong dim (e.g. someone picks a model that ignores dimensions=)."""
    respx.post("https://api.openai.com/v1/embeddings").mock(
        return_value=Response(
            200,
            json={
                "object": "list",
                "data": [{"object": "embedding", "embedding": [0.0] * 1536, "index": 0}],
                "model": "text-embedding-ada-002",
                "usage": {"prompt_tokens": 1, "total_tokens": 1},
            },
        )
    )

    provider = OpenAIProvider(
        api_key="sk-test",
        chat_model="gpt-4o-mini",
        embed_model="text-embedding-ada-002",
        embedding_dimensions=768,
        timeout_seconds=10,
    )

    with pytest.raises(ValueError, match="768.*1536"):
        await provider.embed("hello")
