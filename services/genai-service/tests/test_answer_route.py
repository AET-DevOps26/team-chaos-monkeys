"""Endpoint tests for POST /answer (issue #178).

Exercises the HTTP surface — status codes, the contract error envelope,
camelCase serialisation — with a scripted FakeProvider. No real provider and
no network. Domain-logic tests live in tests/test_answer.py.
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
        "answer": "Likely match [1].",
        "citations": ["a"],
        "grounded": True,
    }
)

VALID_BODY = {
    "query": "Did anyone find a black North Face jacket?",
    "snippets": [
        {
            "id": "a",
            "itemType": "found_item",
            "text": "dark puffer jacket, North Face, enamel pin on the lapel",
        }
    ],
}


def _body(**overrides):
    """A deep copy of VALID_BODY with optional top-level overrides."""
    body = copy.deepcopy(VALID_BODY)
    body.update(overrides)
    return body


@pytest.fixture
def post_answer(client_with_fake: TestClient):
    """POST /answer with a FakeProvider scripted per call."""

    def _post(body, *, chat_response: str = VALID_OUTPUT, raise_on_chat=None):
        provider = FakeProvider(chat_response=chat_response, raise_on_chat=raise_on_chat)
        app.dependency_overrides[get_llm] = lambda: provider
        return client_with_fake.post("/answer", json=body)

    return _post


# --- happy path ----------------------------------------------------------


def test_happy_path_grounded_returns_citations_and_model_info(post_answer):
    response = post_answer(_body())
    assert response.status_code == 200
    body = response.json()
    assert body["grounded"] is True
    assert body["citations"] == ["a"]
    assert body["answer"]
    assert "provider" in body["modelInfo"]


def test_happy_path_empty_snippets_returns_not_grounded(post_answer):
    chat_response = json.dumps({"answer": "none", "citations": [], "grounded": False})
    response = post_answer(
        {"query": "anything?", "snippets": []},
        chat_response=chat_response,
    )
    assert response.status_code == 200
    assert response.json()["grounded"] is False


def test_happy_path_answer_text_present(post_answer):
    response = post_answer(_body())
    assert response.json()["answer"] == "Likely match [1]."


def test_response_uses_camelcase_keys(post_answer):
    body = post_answer(_body()).json()
    assert "modelInfo" in body and "model_info" not in body


# --- 400 VALIDATION_ERROR ------------------------------------------------


def test_missing_query_returns_400(post_answer):
    body = _body()
    del body["query"]
    response = post_answer(body)
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_empty_query_returns_400(post_answer):
    response = post_answer(_body(query=""))
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_invalid_language_returns_400(post_answer):
    response = post_answer(_body(language="english"))
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_snippet_missing_text_returns_400(post_answer):
    body = _body(snippets=[{"id": "a", "itemType": "found_item"}])
    response = post_answer(body)
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


# --- 422 MODEL_OUTPUT_INVALID -------------------------------------------


def test_invalid_model_json_returns_422(post_answer):
    response = post_answer(_body(), chat_response="{broken")
    assert response.status_code == 422
    body = response.json()
    assert body["code"] == "MODEL_OUTPUT_INVALID"
    assert body["details"]["rawOutput"] == "{broken"
    assert body["details"]["schemaErrors"]


def test_missing_grounded_field_returns_422(post_answer):
    response = post_answer(
        _body(),
        chat_response=json.dumps({"answer": "ok", "citations": []}),
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
def test_provider_errors_map_to_contract_status(post_answer, error, status, code):
    response = post_answer(_body(), raise_on_chat=error)
    assert response.status_code == status
    assert response.json()["code"] == code


def test_bad_request_error_does_not_leak_provider_detail(post_answer):
    response = post_answer(
        _body(), raise_on_chat=LLMBadRequestError("model gpt-9 does not exist")
    )
    assert response.status_code == 500
    assert "gpt-9" not in response.json()["message"]


# --- error envelope shape ------------------------------------------------


def test_error_envelope_is_flat(post_answer):
    body = post_answer(_body(language="english")).json()
    assert set(body) <= {"code", "message", "details"}
    assert "error" not in body
    assert isinstance(body["code"], str)
    assert isinstance(body["message"], str)


# --- uncaught exception --------------------------------------------------


def test_uncaught_exception_returns_500_envelope(client_no_raise):
    fake = FakeProvider(raise_on_chat=ValueError("boom"))
    app.dependency_overrides[get_llm] = lambda: fake
    response = client_no_raise.post("/answer", json=VALID_BODY)
    assert response.status_code == 500
    body = response.json()
    assert body["code"] == "INTERNAL_ERROR"
    assert "boom" not in body["message"]
