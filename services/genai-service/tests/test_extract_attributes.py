"""Endpoint tests for POST /extract-attributes.

Exercises the HTTP surface — status codes, the contract error envelope,
camelCase serialisation — with a scripted FakeProvider. The 22-case
golden set is parametrised through the endpoint as a wiring regression:
every golden description must pass request validation and flow through.
The real-LLM quality regression lives in
tests/integration/test_golden_extraction.py.
"""

from __future__ import annotations

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
from tests.golden import load_golden_set

VALID_OUTPUT = json.dumps(
    {
        "category": "CLOTHING",
        "brand": "North Face",
        "color": "black",
        "distinguishingMarks": ["enamel pin"],
        "approximateTime": "Saturday around 11pm",
        "location": "near the cloakroom",
    }
)

ATTRIBUTE_KEYS = {
    "category",
    "brand",
    "color",
    "distinguishingMarks",
    "approximateTime",
    "location",
}

_GOLDEN = load_golden_set()


@pytest.fixture
def post_extract(client_with_fake: TestClient):
    """POST /extract-attributes with a FakeProvider scripted per call."""

    def _post(body, *, chat_response: str = VALID_OUTPUT, raise_on_chat=None):
        provider = FakeProvider(chat_response=chat_response, raise_on_chat=raise_on_chat)
        app.dependency_overrides[get_llm] = lambda: provider
        return client_with_fake.post("/extract-attributes", json=body)

    return _post


# --- happy path ----------------------------------------------------------


def test_happy_path_returns_attributes_and_model_info(post_extract):
    response = post_extract({"description": "black North Face puffer jacket"})
    assert response.status_code == 200
    body = response.json()
    assert body["attributes"]["category"] == "CLOTHING"
    assert body["attributes"]["brand"] == "North Face"
    assert body["modelInfo"] == {"provider": "local", "model": "llama3.2:3b"}


def test_response_uses_camelcase_keys(post_extract):
    body = post_extract({"description": "a jacket"}).json()
    assert "modelInfo" in body and "model_info" not in body
    attrs = body["attributes"]
    assert "distinguishingMarks" in attrs and "distinguishing_marks" not in attrs
    assert "approximateTime" in attrs and "approximate_time" not in attrs


def test_missing_fields_serialised_as_null_and_empty_list(post_extract):
    response = post_extract(
        {"description": "a phone"}, chat_response=json.dumps({"category": "ELECTRONICS"})
    )
    attrs = response.json()["attributes"]
    assert attrs["category"] == "ELECTRONICS"
    assert attrs["brand"] is None
    assert attrs["distinguishingMarks"] == []


def test_language_hint_is_accepted(post_extract):
    response = post_extract({"description": "ein schwarzer Rucksack", "language": "de"})
    assert response.status_code == 200


# --- 400 VALIDATION_ERROR ------------------------------------------------


def test_empty_description_returns_400(post_extract):
    response = post_extract({"description": ""})
    assert response.status_code == 400
    body = response.json()
    assert body["code"] == "VALIDATION_ERROR"
    assert body["details"]["field"] == "description"


def test_missing_description_returns_400(post_extract):
    response = post_extract({})
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_too_long_description_returns_400(post_extract):
    response = post_extract({"description": "x" * 4001})
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_bad_language_code_returns_400(post_extract):
    response = post_extract({"description": "a jacket", "language": "english"})
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


# --- 422 MODEL_OUTPUT_INVALID -------------------------------------------


def test_invalid_model_json_returns_422(post_extract):
    response = post_extract({"description": "a jacket"}, chat_response="{broken")
    assert response.status_code == 422
    body = response.json()
    assert body["code"] == "MODEL_OUTPUT_INVALID"
    assert body["details"]["rawOutput"] == "{broken"
    assert body["details"]["schemaErrors"]


def test_model_output_wrong_shape_returns_422(post_extract):
    response = post_extract(
        {"description": "a jacket"},
        chat_response=json.dumps({"distinguishingMarks": "a single pin"}),
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
def test_provider_errors_map_to_contract_status(post_extract, error, status, code):
    response = post_extract({"description": "a jacket"}, raise_on_chat=error)
    assert response.status_code == status
    assert response.json()["code"] == code


def test_bad_request_error_does_not_leak_provider_detail(post_extract):
    response = post_extract(
        {"description": "a jacket"},
        raise_on_chat=LLMBadRequestError("model gpt-9 does not exist"),
    )
    assert response.status_code == 500
    assert "gpt-9" not in response.json()["message"]


# --- error envelope shape ------------------------------------------------


def test_error_envelope_is_flat(post_extract):
    body = post_extract({"description": ""}).json()
    assert set(body) <= {"code", "message", "details"}
    assert "error" not in body
    assert isinstance(body["code"], str)
    assert isinstance(body["message"], str)


# --- golden-set wiring regression ---------------------------------------

# Image-bearing golden cases (#131) are exercised by the gated runner
# `tests/integration/test_golden_extraction.py`; this wiring test stays on
# the text path because it uses a FakeProvider and only validates request/
# response plumbing.
_GOLDEN_TEXT_CASES = [c for c in _GOLDEN if "description" in c]


@pytest.mark.parametrize(
    "case", _GOLDEN_TEXT_CASES, ids=[c["id"] for c in _GOLDEN_TEXT_CASES]
)
def test_golden_description_is_accepted(post_extract, case):
    body = {"description": case["description"]}
    if "language" in case:
        body["language"] = case["language"]
    response = post_extract(body)
    assert response.status_code == 200
    assert set(response.json()["attributes"]) == ATTRIBUTE_KEYS


# --- uncaught exception --------------------------------------------------


def test_uncaught_exception_returns_500_envelope(client_no_raise):
    fake = FakeProvider(raise_on_chat=ValueError("boom"))
    app.dependency_overrides[get_llm] = lambda: fake
    response = client_no_raise.post("/extract-attributes", json={"description": "x"})
    assert response.status_code == 500
    body = response.json()
    assert body["code"] == "INTERNAL_ERROR"
    assert "boom" not in body["message"]
