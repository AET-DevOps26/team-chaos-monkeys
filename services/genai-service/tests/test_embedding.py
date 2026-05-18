"""Unit tests for app.embedding — batch fan-out and dimension checks.

Deterministic, no real provider: a scripted FakeProvider stands in so the
batching, ordering, and validation logic is exercised directly.
"""

from __future__ import annotations

import pytest

from app.embedding import embed_texts
from app.exceptions import LLMRateLimitError
from app.providers.fake import FakeProvider


async def test_embed_single_text_returns_one_vector():
    llm = FakeProvider(embed_vector=[0.1, 0.2, 0.3])
    embeddings, dimensions = await embed_texts(["a wallet"], llm)
    assert embeddings == [[0.1, 0.2, 0.3]]
    assert dimensions == 3


async def test_embed_batch_returns_one_vector_per_text():
    llm = FakeProvider(embed_vector=[0.5, 0.5])
    texts = ["a", "b", "c", "d"]
    embeddings, dimensions = await embed_texts(texts, llm)
    assert len(embeddings) == len(texts)
    assert dimensions == 2
    assert set(llm.embed_calls) == set(texts)


async def test_embed_preserves_text_order():
    # Each vector encodes its source text's length, so a reordering is visible.
    llm = FakeProvider(embed_vector=lambda text: [float(len(text))])
    texts = ["xxxxx", "x", "xxx", "xxxx"]
    embeddings, _ = await embed_texts(texts, llm)
    assert embeddings == [[5.0], [1.0], [3.0], [4.0]]


async def test_embed_reports_vector_length_as_dimensions():
    llm = FakeProvider(embed_vector=[0.0] * 768)
    _, dimensions = await embed_texts(["a"], llm)
    assert dimensions == 768


async def test_provider_error_propagates_unchanged():
    llm = FakeProvider(raise_on_embed=LLMRateLimitError("slow down"))
    with pytest.raises(LLMRateLimitError):
        await embed_texts(["a"], llm)


async def test_inconsistent_dimensions_raise_value_error():
    # A misbehaving provider returns vectors of differing length.
    llm = FakeProvider(embed_vector=lambda text: [0.0] * len(text))
    with pytest.raises(ValueError, match="dimensionality"):
        await embed_texts(["xx", "xxxx"], llm)


async def test_empty_vector_raises_value_error():
    llm = FakeProvider(embed_vector=[])
    with pytest.raises(ValueError, match="dimensionality"):
        await embed_texts(["a"], llm)
