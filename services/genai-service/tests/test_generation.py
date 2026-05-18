"""Domain-logic tests for guest pickup notification generation (issue #53).

Exercises `app.generation` directly with a scripted FakeProvider — no HTTP
layer, no network. Endpoint tests live in tests/test_generate_message.py.
"""

from __future__ import annotations

import json

import pytest
from pydantic import ValidationError

from app.api.schemas import (
    GenerateMessageRequest,
    PickupContext,
    PickupNotificationMessage,
)
from app.exceptions import LLMUnavailableError, ModelOutputError
from app.generation import build_messages, generate_message, parse_pickup_message
from app.providers.fake import FakeProvider

VALID_OUTPUT = json.dumps(
    {
        "subject": "Your lost item has been found",
        "body": "Hello, we found your brown leather wallet. Collect it at "
        "Hotel reception, 08:00-22:00 daily. Quote case FF-2026-04812.",
    }
)


def _request(**overrides) -> GenerateMessageRequest:
    """A valid GenerateMessageRequest, with optional top-level field overrides."""
    context = overrides.pop("context", None) or PickupContext(
        item_description="brown leather wallet",
        pickup_location="Hotel reception",
        pickup_hours="08:00-22:00 daily",
        case_reference="FF-2026-04812",
    )
    fields = {
        "message_type": "pickup_notification",
        "language": "en",
        "tone": "formal",
        "context": context,
    }
    fields.update(overrides)
    return GenerateMessageRequest(**fields)


# --- schemas -------------------------------------------------------------


def test_pickup_notification_message_accepts_valid_output():
    message = PickupNotificationMessage.model_validate(
        {"subject": "Found your wallet", "body": "Please collect it."}
    )
    assert message.subject == "Found your wallet"
    assert message.body == "Please collect it."


def test_pickup_notification_message_rejects_oversized_subject():
    with pytest.raises(ValidationError):
        PickupNotificationMessage.model_validate({"subject": "x" * 201, "body": "ok"})


def test_pickup_notification_message_rejects_empty_fields():
    with pytest.raises(ValidationError):
        PickupNotificationMessage.model_validate({"subject": "", "body": ""})


def test_generate_message_request_round_trips_camelcase():
    payload = GenerateMessageRequest.model_validate(
        {
            "messageType": "pickup_notification",
            "language": "de",
            "tone": "formal",
            "context": {
                "itemDescription": "wallet",
                "pickupLocation": "reception",
                "pickupHours": "all day",
                "caseReference": "FF-1",
            },
        }
    )
    assert payload.message_type == "pickup_notification"
    assert payload.context.case_reference == "FF-1"


# --- build_messages ------------------------------------------------------


def test_build_messages_has_system_then_user_role():
    messages = build_messages(_request())
    assert messages[0]["role"] == "system"
    assert messages[1]["role"] == "user"


def test_build_messages_includes_every_context_detail():
    user = build_messages(_request())[1]["content"]
    assert "brown leather wallet" in user
    assert "Hotel reception" in user
    assert "08:00-22:00 daily" in user
    assert "FF-2026-04812" in user


def test_build_messages_includes_language_and_tone():
    user = build_messages(_request(language="de", tone="terse"))[1]["content"]
    assert "de" in user
    assert "terse" in user


def test_build_messages_fences_context_as_untrusted_data():
    # The context block is delimited so the model treats it as data, not
    # instructions — itemDescription can carry guest/staff free text.
    user = build_messages(_request())[1]["content"]
    assert '"""' in user


# --- generate_message ----------------------------------------------------


async def test_generate_message_returns_subject_and_body():
    provider = FakeProvider(chat_response=VALID_OUTPUT)
    subject, body = await generate_message(_request(), provider)
    assert subject == "Your lost item has been found"
    assert "brown leather wallet" in body


async def test_generate_message_calls_provider_in_json_mode():
    provider = FakeProvider(chat_response=VALID_OUTPUT)
    await generate_message(_request(), provider)
    _messages, json_mode = provider.chat_calls[0]
    assert json_mode is True


async def test_generate_message_propagates_provider_error():
    provider = FakeProvider(raise_on_chat=LLMUnavailableError("provider down"))
    with pytest.raises(LLMUnavailableError):
        await generate_message(_request(), provider)


# --- parse_pickup_message ------------------------------------------------


def test_parse_pickup_message_accepts_valid_json():
    message = parse_pickup_message(VALID_OUTPUT)
    assert message.subject == "Your lost item has been found"


def test_parse_pickup_message_rejects_invalid_json():
    with pytest.raises(ModelOutputError):
        parse_pickup_message("{not json")


def test_parse_pickup_message_rejects_non_object():
    with pytest.raises(ModelOutputError):
        parse_pickup_message(json.dumps(["a", "list"]))


def test_parse_pickup_message_rejects_missing_body():
    with pytest.raises(ModelOutputError):
        parse_pickup_message(json.dumps({"subject": "only a subject"}))


def test_parse_pickup_message_rejects_oversized_subject():
    with pytest.raises(ModelOutputError):
        parse_pickup_message(json.dumps({"subject": "x" * 201, "body": "ok"}))
