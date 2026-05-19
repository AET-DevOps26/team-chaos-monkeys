"""Endpoint tests for POST /verify-match (issue #104).

Exercises the HTTP surface — status codes, the contract error envelope,
camelCase serialisation — with a scripted FakeProvider. No real provider and
no network. Domain-logic tests live in tests/test_verification.py.
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
        "verdict": "match",
        "confidence": 0.88,
        "rationale": "Both describe a black North Face puffer jacket with an "
        "enamel pin; cloakroom location matches.",
    }
)

VALID_BODY = {
    "lost": {"description": "black North Face puffer jacket with a Berlin enamel pin"},
    "found": {"description": "dark puffer jacket, North Face, enamel pin on the lapel"},
    "language": "en",
}


def _body(**overrides):
    """A deep copy of VALID_BODY with optional top-level overrides."""
    body = copy.deepcopy(VALID_BODY)
    body.update(overrides)
    return body


@pytest.fixture
def post_verify(client_with_fake: TestClient):
    """POST /verify-match with a FakeProvider scripted per call."""

    def _post(body, *, chat_response: str = VALID_OUTPUT, raise_on_chat=None):
        provider = FakeProvider(chat_response=chat_response, raise_on_chat=raise_on_chat)
        app.dependency_overrides[get_llm] = lambda: provider
        return client_with_fake.post("/verify-match", json=body)

    return _post


# --- happy path ----------------------------------------------------------


def test_happy_path_returns_verdict_and_model_info(post_verify):
    response = post_verify(_body())
    assert response.status_code == 200
    body = response.json()
    assert body["verdict"] == "match"
    assert body["confidence"] == 0.88
    assert body["rationale"]
    assert body["modelInfo"] == {"provider": "local", "model": "llama3.2:3b"}


def test_happy_path_no_match_verdict(post_verify):
    chat_response = json.dumps(
        {"verdict": "no_match", "confidence": 0.12, "rationale": "Different brand and colour."}
    )
    response = post_verify(_body(), chat_response=chat_response)
    assert response.status_code == 200
    assert response.json()["verdict"] == "no_match"


def test_happy_path_uncertain_verdict(post_verify):
    chat_response = json.dumps(
        {"verdict": "uncertain", "confidence": 0.55, "rationale": "Some features overlap but not conclusive."}
    )
    response = post_verify(_body(), chat_response=chat_response)
    assert response.status_code == 200
    assert response.json()["verdict"] == "uncertain"


def test_response_uses_camelcase_keys(post_verify):
    body = post_verify(_body()).json()
    assert "modelInfo" in body and "model_info" not in body


def test_accepts_optional_attributes(post_verify):
    body = _body()
    body["lost"]["attributes"] = {
        "category": "jacket",
        "brand": "North Face",
        "color": "black",
        "distinguishingMarks": ["enamel pin"],
        "approximateTime": None,
        "location": "cloakroom",
    }
    assert post_verify(body).status_code == 200


# --- 400 VALIDATION_ERROR ------------------------------------------------


def test_missing_lost_returns_400(post_verify):
    body = _body()
    del body["lost"]
    response = post_verify(body)
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_missing_found_description_returns_400(post_verify):
    body = _body()
    body["found"] = {}
    response = post_verify(body)
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_empty_description_returns_400(post_verify):
    body = _body()
    body["lost"]["description"] = ""
    response = post_verify(body)
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_invalid_language_returns_400(post_verify):
    response = post_verify(_body(language="english"))
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


# --- 422 MODEL_OUTPUT_INVALID -------------------------------------------


def test_invalid_model_json_returns_422(post_verify):
    response = post_verify(_body(), chat_response="{broken")
    assert response.status_code == 422
    body = response.json()
    assert body["code"] == "MODEL_OUTPUT_INVALID"
    assert body["details"]["rawOutput"] == "{broken"
    assert body["details"]["schemaErrors"]


def test_unknown_verdict_returns_422(post_verify):
    response = post_verify(
        _body(),
        chat_response=json.dumps(
            {"verdict": "maybe", "confidence": 0.5, "rationale": "x"}
        ),
    )
    assert response.status_code == 422
    assert response.json()["code"] == "MODEL_OUTPUT_INVALID"


def test_out_of_range_confidence_returns_422(post_verify):
    response = post_verify(
        _body(),
        chat_response=json.dumps(
            {"verdict": "match", "confidence": 1.7, "rationale": "x"}
        ),
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
def test_provider_errors_map_to_contract_status(post_verify, error, status, code):
    response = post_verify(_body(), raise_on_chat=error)
    assert response.status_code == status
    assert response.json()["code"] == code


def test_bad_request_error_does_not_leak_provider_detail(post_verify):
    response = post_verify(
        _body(), raise_on_chat=LLMBadRequestError("model gpt-9 does not exist")
    )
    assert response.status_code == 500
    assert "gpt-9" not in response.json()["message"]


# --- error envelope shape ------------------------------------------------


def test_error_envelope_is_flat(post_verify):
    body = post_verify(_body(language="english")).json()
    assert set(body) <= {"code", "message", "details"}
    assert "error" not in body
    assert isinstance(body["code"], str)
    assert isinstance(body["message"], str)


# --- uncaught exception --------------------------------------------------


def test_uncaught_exception_returns_500_envelope(client_no_raise):
    fake = FakeProvider(raise_on_chat=ValueError("boom"))
    app.dependency_overrides[get_llm] = lambda: fake
    response = client_no_raise.post("/verify-match", json=VALID_BODY)
    assert response.status_code == 500
    body = response.json()
    assert body["code"] == "INTERNAL_ERROR"
    assert "boom" not in body["message"]
