"""Unit tests for FakeProvider's embedding_dimensions parameter."""

import pytest

from app.providers.fake import FakeProvider


@pytest.mark.asyncio
async def test_fake_embed_returns_default_3_dims():
    p = FakeProvider()
    vec = await p.embed("hello")
    assert len(vec) == 3


@pytest.mark.asyncio
async def test_fake_embed_honors_configured_dim():
    p = FakeProvider(embedding_dimensions=768)
    vec = await p.embed("hello")
    assert len(vec) == 768
