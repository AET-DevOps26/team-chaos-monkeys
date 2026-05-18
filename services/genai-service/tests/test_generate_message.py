"""Endpoint tests for POST /generate-message (issue #53).

Exercises the HTTP surface — status codes, the contract error envelope,
camelCase serialisation — with a scripted FakeProvider. No real provider and
no network. Domain-logic tests live in tests/test_generation.py.
"""

from __future__ import annotations

import copy
import json

import pytest
from fastapi.testclient import TestClient

from app.dependencies import get_llm
from app.exceptions import (
    LLMBadRequestError,
    LLMError,
    LLMRateLimitError,
    LLMTimeoutError,
    LLMUnavailableError,
)
from app.main import app
from app.providers.fake import FakeProvider

VALID_OUTPUT = json.dumps(
    {
        "subject": "Your lost item has been found",
        "body": "Hello, we found your wallet. Collect it at reception, "
        "08:00-22:00 daily. Quote case FF-2026-04812.",
    }
)

VALID_BODY = {
    "messageType": "pickup_notification",
    "language": "en",
    "tone": "formal",
    "context": {
        "itemDescription": "brown leather wallet",
        "pickupLocation": "Hotel reception",
        "pickupHours": "08:00-22:00 daily",
        "caseReference": "FF-2026-04812",
    },
}


def _body(**overrides):
    """A deep copy of VALID_BODY with optional top-level overrides."""
    body = copy.deepcopy(VALID_BODY)
    body.update(overrides)
    return body


@pytest.fixture
def post_generate(client_with_fake: TestClient):
    """POST /generate-message with a FakeProvider scripted per call."""

    def _post(body, *, chat_response: str = VALID_OUTPUT, raise_on_chat=None):
        provider = FakeProvider(chat_response=chat_response, raise_on_chat=raise_on_chat)
        app.dependency_overrides[get_llm] = lambda: provider
        return client_with_fake.post("/generate-message", json=body)

    return _post


# --- happy path ----------------------------------------------------------


def test_happy_path_returns_subject_body_and_model_info(post_generate):
    response = post_generate(_body())
    assert response.status_code == 200
    body = response.json()
    assert body["subject"] == "Your lost item has been found"
    assert "wallet" in body["body"]
    assert body["modelInfo"] == {"provider": "local", "model": "llama3.2:3b"}


def test_response_uses_camelcase_keys(post_generate):
    body = post_generate(_body()).json()
    assert "modelInfo" in body and "model_info" not in body


# --- 400 VALIDATION_ERROR ------------------------------------------------


def test_invalid_language_returns_400(post_generate):
    response = post_generate(_body(language="english"))
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_invalid_tone_returns_400(post_generate):
    response = post_generate(_body(tone="angry"))
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_invalid_message_type_returns_400(post_generate):
    response = post_generate(_body(messageType="claim_reminder"))
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_missing_language_returns_400(post_generate):
    body = _body()
    del body["language"]
    response = post_generate(body)
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_missing_context_returns_400(post_generate):
    body = _body()
    del body["context"]
    response = post_generate(body)
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_missing_context_field_returns_400(post_generate):
    body = _body()
    del body["context"]["caseReference"]
    response = post_generate(body)
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


# --- 422 MODEL_OUTPUT_INVALID -------------------------------------------


def test_invalid_model_json_returns_422(post_generate):
    response = post_generate(_body(), chat_response="{broken")
    assert response.status_code == 422
    body = response.json()
    assert body["code"] == "MODEL_OUTPUT_INVALID"
    assert body["details"]["rawOutput"] == "{broken"
    assert body["details"]["schemaErrors"]


def test_model_output_missing_field_returns_422(post_generate):
    response = post_generate(
        _body(), chat_response=json.dumps({"subject": "only a subject"})
    )
    assert response.status_code == 422
    assert response.json()["code"] == "MODEL_OUTPUT_INVALID"


def test_model_output_oversized_subject_returns_422(post_generate):
    response = post_generate(
        _body(), chat_response=json.dumps({"subject": "x" * 201, "body": "ok"})
    )
    assert response.status_code == 422
    assert response.json()["code"] == "MODEL_OUTPUT_INVALID"


# --- provider failures ---------------------------------------------------


@pytest.mark.parametrize(
    ("error", "status", "code"),
    [
        (LLMRateLimitError("slow down"), 429, "PROVIDER_RATE_LIMITED"),
        (LLMTimeoutError("too slow"), 504, "PROVIDER_TIMEOUT"),
        (LLMUnavailableError("down"), 502, "PROVIDER_UNAVAILABLE"),
        (LLMError("generic failure"), 502, "PROVIDER_UNAVAILABLE"),
        (LLMBadRequestError("unknown model"), 500, "INTERNAL_ERROR"),
    ],
)
def test_provider_errors_map_to_contract_status(post_generate, error, status, code):
    response = post_generate(_body(), raise_on_chat=error)
    assert response.status_code == status
    assert response.json()["code"] == code


def test_bad_request_error_does_not_leak_provider_detail(post_generate):
    response = post_generate(
        _body(), raise_on_chat=LLMBadRequestError("model gpt-9 does not exist")
    )
    assert response.status_code == 500
    assert "gpt-9" not in response.json()["message"]


# --- error envelope shape ------------------------------------------------


def test_error_envelope_is_flat(post_generate):
    body = post_generate(_body(tone="angry")).json()
    assert set(body) <= {"code", "message", "details"}
    assert "error" not in body
    assert isinstance(body["code"], str)
    assert isinstance(body["message"], str)


# --- uncaught exception --------------------------------------------------


def test_uncaught_exception_returns_500_envelope(client_no_raise):
    fake = FakeProvider(raise_on_chat=ValueError("boom"))
    app.dependency_overrides[get_llm] = lambda: fake
    response = client_no_raise.post("/generate-message", json=VALID_BODY)
    assert response.status_code == 500
    body = response.json()
    assert body["code"] == "INTERNAL_ERROR"
    assert "boom" not in body["message"]
