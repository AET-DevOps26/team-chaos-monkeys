"""build_provider("fake") returns a deterministic in-process provider.

This is the runtime escape hatch used by docker-compose E2E and Spring-side
integration tests (issue #128) so we never need a real OpenAI key or a
slow Ollama pull just to exercise the GenAI wiring.
"""

from __future__ import annotations

import json

import pytest

from app.config import Settings
from app.providers import build_provider
from app.providers.fake import FakeProvider


def test_build_provider_returns_fake_when_configured(monkeypatch):
    monkeypatch.setenv("GENAI_PROVIDER", "fake")
    settings = Settings()

    provider = build_provider(settings)

    assert isinstance(provider, FakeProvider)
    assert provider.name == "fake"


@pytest.mark.asyncio
async def test_fake_provider_chat_returns_valid_item_attributes_json(monkeypatch):
    """The canned response must be valid JSON shaped like ItemAttributes,
    so the /extract-attributes pipeline does not 422 on it."""
    monkeypatch.setenv("GENAI_PROVIDER", "fake")
    settings = Settings()
    provider = build_provider(settings)

    response = await provider.chat([{"role": "user", "content": "anything"}], json_mode=True)

    parsed = json.loads(response)
    assert parsed["category"] == "jacket"
    assert "color" in parsed
    assert isinstance(parsed["distinguishingMarks"], list)
