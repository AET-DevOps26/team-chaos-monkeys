"""Gated real-LLM check: generate-message against a real provider.

NOT run in normal CI. Set GENAI_RUN_GENERATION=1 plus the provider env
(GENAI_PROVIDER and its credentials) to run it. Mirrors the env-gating of
test_golden_extraction.py — the nightly genai-integration workflow runs a
tiny Ollama model that cannot reliably satisfy the output schema, so it
must not pick this suite up.

    GENAI_RUN_GENERATION=1 GENAI_PROVIDER=openai OPENAI_API_KEY=sk-... \\
        pytest tests/integration/test_real_generation.py -s

It makes one LLM call per case and asserts structural validity — that our
SYSTEM_PROMPT yields output that parses into PickupNotificationMessage and
carries the supplied case reference. It does NOT score prose quality.
"""

from __future__ import annotations

import os
from collections.abc import AsyncIterator

import pytest

from app.api.schemas import GenerateMessageRequest, PickupContext
from app.config import Settings
from app.generation import generate_message
from app.providers import LLMProvider, build_provider

pytestmark = pytest.mark.skipif(
    os.getenv("GENAI_RUN_GENERATION") != "1",
    reason="set GENAI_RUN_GENERATION=1 (and provider env) to run the real-LLM generation check",
)

_CASE_REFERENCE = "FF-2026-04812"

# (language, tone) combinations exercised against the real model.
_CASES = [
    ("en", "formal"),
    ("de", "formal"),
    ("en", "terse"),
]


@pytest.fixture
async def provider() -> AsyncIterator[LLMProvider]:
    llm = build_provider(Settings())
    try:
        yield llm
    finally:
        await llm.aclose()


def _request(language: str, tone: str) -> GenerateMessageRequest:
    return GenerateMessageRequest(
        message_type="pickup_notification",
        language=language,
        tone=tone,
        context=PickupContext(
            item_description="brown leather wallet",
            pickup_location="Hotel reception",
            pickup_hours="08:00-22:00 daily",
            case_reference=_CASE_REFERENCE,
        ),
    )


@pytest.mark.parametrize(("language", "tone"), _CASES)
async def test_generate_message_real_provider(
    provider: LLMProvider, language: str, tone: str
) -> None:
    # generate_message raises ModelOutputError if the model's output does not
    # parse into PickupNotificationMessage — so a clean return already proves
    # our SYSTEM_PROMPT yields schema-valid output on this provider.
    subject, body = await generate_message(_request(language, tone), provider)

    assert subject.strip()
    assert len(subject) <= 200
    assert body.strip()
    assert _CASE_REFERENCE in body  # the model used the supplied details
