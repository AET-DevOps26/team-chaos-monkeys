"""Gated real-LLM check: /verify-match against a real provider.

NOT run in normal CI. Set GENAI_RUN_VERIFICATION=1 plus the provider env
(GENAI_PROVIDER and its credentials) to run it:

    GENAI_RUN_VERIFICATION=1 GENAI_PROVIDER=openai OPENAI_API_KEY=sk-... \\
        pytest tests/integration/test_real_verification.py -s

It makes one LLM call per case and asserts structural validity plus the
obvious-case verdict — that our SYSTEM_PROMPT yields schema-valid output and
distinguishes a clear match from a clear mismatch. It does not assert on the
deliberately ambiguous case.
"""

from __future__ import annotations

import os
from collections.abc import AsyncIterator

import pytest

from app.api.schemas import ItemSide, VerifyMatchRequest
from app.config import Settings
from app.providers import LLMProvider, build_provider
from app.verification import verify_match

pytestmark = pytest.mark.skipif(
    os.getenv("GENAI_RUN_VERIFICATION") != "1",
    reason="set GENAI_RUN_VERIFICATION=1 (and provider env) to run the real-LLM check",
)


@pytest.fixture
async def provider() -> AsyncIterator[LLMProvider]:
    llm = build_provider(Settings())
    try:
        yield llm
    finally:
        await llm.aclose()


# (lost description, found description, expected verdict or None for "do not assert")
_CASES = [
    (
        "black North Face puffer jacket with a small Berlin enamel pin, lost near the cloakroom",
        "dark puffer jacket, North Face brand, enamel pin on the lapel, found by the cloakroom",
        "match",
    ),
    (
        "black North Face puffer jacket with a Berlin enamel pin",
        "brown leather wallet containing a Personalausweis",
        "no_match",
    ),
    (
        "a pair of black headphones",
        "black wireless earbuds in a charging case",
        None,
    ),
]


@pytest.mark.parametrize(("lost", "found", "expected"), _CASES)
async def test_verify_match_real_provider(
    provider: LLMProvider, lost: str, found: str, expected: str | None
) -> None:
    # verify_match raises ModelOutputError if the model's output does not parse
    # into VerificationOutput — a clean return already proves the prompt yields
    # schema-valid output on this provider.
    result = await verify_match(
        VerifyMatchRequest(lost=ItemSide(description=lost), found=ItemSide(description=found)),
        provider,
    )

    assert result.verdict in {"match", "no_match", "uncertain"}
    assert 0.0 <= result.confidence <= 1.0
    assert result.rationale.strip()
    if expected is not None:
        assert result.verdict == expected
