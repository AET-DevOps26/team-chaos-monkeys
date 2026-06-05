import pytest
import respx
from httpx import Response

from app.providers.ollama import OllamaProvider


@respx.mock
@pytest.mark.asyncio
async def test_ollama_embed_accepts_matching_dim():
    respx.post("http://localhost:11434/api/embed").mock(
        return_value=Response(
            200,
            json={"model": "nomic-embed-text", "embeddings": [[0.0] * 768]},
        )
    )

    provider = OllamaProvider(
        base_url="http://localhost:11434",
        chat_model="llama3.2:3b",
        vision_model="llava:7b",
        embed_model="nomic-embed-text",
        embedding_dimensions=768,
        timeout_seconds=10,
    )

    vec = await provider.embed("hello")
    assert len(vec) == 768


@respx.mock
@pytest.mark.asyncio
async def test_ollama_embed_rejects_wrong_dim():
    respx.post("http://localhost:11434/api/embed").mock(
        return_value=Response(
            200,
            json={"model": "mxbai-embed-large", "embeddings": [[0.0] * 1024]},
        )
    )

    provider = OllamaProvider(
        base_url="http://localhost:11434",
        chat_model="llama3.2:3b",
        vision_model="llava:7b",
        embed_model="mxbai-embed-large",
        embedding_dimensions=768,
        timeout_seconds=10,
    )

    with pytest.raises(ValueError, match="768.*1024"):
        await provider.embed("hello")
