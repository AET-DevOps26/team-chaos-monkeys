"""Domain-logic tests for match verification (issue #104).

Exercises `app.verification` directly with a scripted FakeProvider — no HTTP
layer, no network. Endpoint tests live in tests/test_verify_match.py.
"""

from __future__ import annotations

import json

import pytest
from pydantic import ValidationError

from app.api.schemas import (
    ItemAttributes,
    ItemSide,
    VerificationOutput,
    VerifyMatchRequest,
)
from app.exceptions import LLMUnavailableError, ModelOutputError
from app.providers.fake import FakeProvider
from app.verification import build_messages, parse_verification, verify_match

VALID_OUTPUT = json.dumps(
    {
        "verdict": "match",
        "confidence": 0.9,
        "rationale": "Both describe a black North Face puffer jacket with an "
        "enamel pin; the cloakroom location and Saturday-evening time align.",
    }
)


def _request(**overrides) -> VerifyMatchRequest:
    """A valid VerifyMatchRequest, with optional top-level field overrides."""
    fields = {
        "lost": ItemSide(description="black North Face puffer jacket, Berlin pin"),
        "found": ItemSide(description="dark puffer jacket, North Face, pin on lapel"),
        "language": "en",
    }
    fields.update(overrides)
    return VerifyMatchRequest(**fields)


# --- schemas -------------------------------------------------------------


def test_verification_output_accepts_valid():
    out = VerificationOutput.model_validate(
        {"verdict": "uncertain", "confidence": 0.5, "rationale": "ambiguous"}
    )
    assert out.verdict == "uncertain"


def test_verification_output_rejects_unknown_verdict():
    with pytest.raises(ValidationError):
        VerificationOutput.model_validate(
            {"verdict": "maybe", "confidence": 0.5, "rationale": "x"}
        )


def test_verification_output_rejects_out_of_range_confidence():
    with pytest.raises(ValidationError):
        VerificationOutput.model_validate(
            {"verdict": "match", "confidence": 1.5, "rationale": "x"}
        )


def test_verification_output_rejects_empty_rationale():
    with pytest.raises(ValidationError):
        VerificationOutput.model_validate(
            {"verdict": "match", "confidence": 0.9, "rationale": ""}
        )


def test_verify_match_request_round_trips_camelcase():
    payload = VerifyMatchRequest.model_validate(
        {
            "lost": {"description": "a wallet"},
            "found": {"description": "a wallet"},
            "language": "de",
        }
    )
    assert payload.lost.description == "a wallet"
    assert payload.language == "de"


# --- build_messages ------------------------------------------------------


def test_build_messages_has_system_then_user_role():
    messages = build_messages(_request())
    assert messages[0]["role"] == "system"
    assert messages[1]["role"] == "user"


def test_build_messages_includes_both_descriptions():
    user = build_messages(_request())[1]["content"]
    assert "black North Face puffer jacket, Berlin pin" in user
    assert "dark puffer jacket, North Face, pin on lapel" in user


def test_build_messages_fences_descriptions_as_untrusted_data():
    user = build_messages(_request())[1]["content"]
    assert '"""' in user


def test_build_messages_includes_language():
    user = build_messages(_request(language="de"))[1]["content"]
    assert "de" in user


def test_format_side_includes_structured_attributes():
    attrs = ItemAttributes(category="jacket", brand="North Face")
    user = build_messages(
        _request(lost=ItemSide(description="x", attributes=attrs))
    )[1]["content"]
    assert "structured attributes" in user
    assert "North Face" in user
    assert '"category"' in user  # confirms camelCase model_dump_json output


def test_format_side_omits_attributes_block_when_absent():
    user = build_messages(_request())[1]["content"]
    assert "structured attributes" not in user


# --- verify_match --------------------------------------------------------


async def test_verify_match_returns_verdict():
    provider = FakeProvider(chat_response=VALID_OUTPUT)
    result = await verify_match(_request(), provider)
    assert result.verdict == "match"
    assert result.confidence == 0.9
    assert result.rationale


async def test_verify_match_calls_provider_in_json_mode():
    provider = FakeProvider(chat_response=VALID_OUTPUT)
    await verify_match(_request(), provider)
    _messages, json_mode = provider.chat_calls[0]
    assert json_mode is True


async def test_verify_match_propagates_provider_error():
    provider = FakeProvider(raise_on_chat=LLMUnavailableError("provider down"))
    with pytest.raises(LLMUnavailableError):
        await verify_match(_request(), provider)


# --- parse_verification --------------------------------------------------


def test_parse_verification_accepts_valid_json():
    out = parse_verification(VALID_OUTPUT)
    assert out.verdict == "match"


def test_parse_verification_rejects_invalid_json():
    with pytest.raises(ModelOutputError):
        parse_verification("{not json")


def test_parse_verification_rejects_non_object():
    with pytest.raises(ModelOutputError):
        parse_verification(json.dumps(["a", "list"]))


def test_parse_verification_rejects_missing_field():
    with pytest.raises(ModelOutputError):
        parse_verification(json.dumps({"verdict": "match", "confidence": 0.9}))


def test_parse_verification_rejects_unknown_verdict():
    with pytest.raises(ModelOutputError):
        parse_verification(
            json.dumps({"verdict": "maybe", "confidence": 0.5, "rationale": "x"})
        )


def test_parse_verification_rejects_out_of_range_confidence():
    with pytest.raises(ModelOutputError):
        parse_verification(
            json.dumps({"verdict": "match", "confidence": 2.0, "rationale": "x"})
        )
