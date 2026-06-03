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


@pytest.mark.asyncio
async def test_fake_provider_chat_returns_verification_shape_for_verify_prompt(
    monkeypatch,
):
    """When the system prompt is the verify-match prompt, FakeProvider must
    return a JSON object shaped like VerificationOutput — otherwise
    matching-service's async verifyAsync path 422s under GENAI_PROVIDER=fake."""
    from app.verification import SYSTEM_PROMPT as VERIFY_SYSTEM_PROMPT

    monkeypatch.setenv("GENAI_PROVIDER", "fake")
    settings = Settings()
    provider = build_provider(settings)

    response = await provider.chat(
        [
            {"role": "system", "content": VERIFY_SYSTEM_PROMPT},
            {"role": "user", "content": "lost: jacket. found: jacket."},
        ],
        json_mode=True,
    )

    parsed = json.loads(response)
    assert parsed["verdict"] in {"match", "no_match", "uncertain"}
    assert 0.0 <= parsed["confidence"] <= 1.0
    assert isinstance(parsed["rationale"], str)
