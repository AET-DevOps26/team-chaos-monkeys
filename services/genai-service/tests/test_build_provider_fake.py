"""build_provider("fake") returns a deterministic in-process provider.

This is the runtime escape hatch used by docker-compose E2E and Spring-side
integration tests (issue #128) so we never need a real OpenAI key or a
slow Ollama pull just to exercise the GenAI wiring.
"""

from __future__ import annotations

import json

import pytest

from app.answer import SYSTEM_PROMPT as ANSWER_SYSTEM_PROMPT
from app.config import Settings
from app.providers import build_provider
from app.providers.fake import FakeProvider
from app.verification import SYSTEM_PROMPT as VERIFY_SYSTEM_PROMPT


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
async def test_fake_returns_answer_json_for_answer_prompt(monkeypatch):
    monkeypatch.setenv("GENAI_PROVIDER", "fake")
    provider = build_provider(Settings())
    messages = [
        {"role": "system", "content": 'return "grounded" key ...'},
        {"role": "user", "content": "Staff query: wallet"},
    ]
    raw = await provider.chat(messages, json_mode=True)
    body = json.loads(raw)
    assert set(body) == {"answer", "citations", "grounded"}


@pytest.mark.asyncio
async def test_fake_returns_verify_json_for_verify_prompt(monkeypatch):
    monkeypatch.setenv("GENAI_PROVIDER", "fake")
    provider = build_provider(Settings())
    messages = [{"role": "system", "content": 'return "verdict" key ...'}]
    raw = await provider.chat(messages, json_mode=True)
    body = json.loads(raw)
    assert set(body) == {"verdict", "confidence", "rationale"}


@pytest.mark.asyncio
async def test_fake_still_returns_extraction_json_by_default(monkeypatch):
    monkeypatch.setenv("GENAI_PROVIDER", "fake")
    provider = build_provider(Settings())
    raw = await provider.chat([{"role": "user", "content": "extract"}], json_mode=True)
    assert json.loads(raw)["category"] == "jacket"


def test_answer_system_prompt_contains_fake_dispatch_marker():
    # build_provider's _canned() uses the '"grounded"' substring to route
    # /answer calls to the fake's answer JSON. If you change the prompt,
    # update the marker in app/providers/__init__.py.
    assert '"grounded"' in ANSWER_SYSTEM_PROMPT


def test_verify_system_prompt_contains_fake_dispatch_marker():
    # _canned() uses '"verdict"' to route /verify-match calls. Keep in sync
    # with app/providers/__init__.py.
    assert '"verdict"' in VERIFY_SYSTEM_PROMPT
