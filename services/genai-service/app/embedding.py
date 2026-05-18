"""Text embedding (issue #50).

Domain logic over the `LLMProvider.embed` primitive. `embed` handles a
single text, so a batch request fans out one call per text with
`asyncio.gather` and reassembles the vectors in request order.

Stateless: the GenAI service returns vectors to `matching-service`, which
persists them in its own pgvector store. Nothing is stored here.
"""

from __future__ import annotations

import asyncio

from app.providers import LLMProvider


async def embed_texts(
    texts: list[str], llm: LLMProvider
) -> tuple[list[list[float]], int]:
    """Embed every text and report the shared vector dimensionality.

    Returns `(embeddings, dimensions)` where `embeddings[i]` is the vector
    for `texts[i]` — `asyncio.gather` preserves argument order regardless of
    completion order. `texts` is assumed non-empty; the caller (`/embed`)
    enforces the contract's 1-32 bound before delegating here.

    Provider failures (`LLMError` subclasses) propagate unchanged for the
    caller to map. Raises `ValueError` if the provider returns an empty
    vector or vectors of differing length — a provider-integration fault
    that surfaces as HTTP 500.
    """
    embeddings: list[list[float]] = list(
        await asyncio.gather(*(llm.embed(text) for text in texts))
    )
    dimensions = len(embeddings[0])
    if dimensions == 0 or any(len(vector) != dimensions for vector in embeddings):
        observed = sorted({len(vector) for vector in embeddings})
        raise ValueError(
            f"provider returned embeddings with empty or inconsistent "
            f"dimensionality (observed vector lengths: {observed})"
        )
    return embeddings, dimensions
