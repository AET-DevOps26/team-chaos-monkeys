"""Endpoint tests for POST /embed.

Exercises the HTTP surface — status codes, the contract error envelope,
camelCase serialisation, request validation, batching — with a scripted
FakeProvider. No real provider and no network.
"""

from __future__ import annotations

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


@pytest.fixture
def post_embed(client_with_fake: TestClient):
    """POST /embed with a FakeProvider scripted per call."""

    def _post(body, *, embed_vector=None, raise_on_embed=None):
        provider = FakeProvider(
            embed_vector=embed_vector, raise_on_embed=raise_on_embed
        )
        app.dependency_overrides[get_llm] = lambda: provider
        return client_with_fake.post("/embed", json=body)

    return _post


# --- happy path ----------------------------------------------------------


def test_happy_path_returns_embeddings_and_model_info(post_embed):
    response = post_embed(
        {"texts": ["a brown leather wallet"], "purpose": "found_item"},
        embed_vector=[0.1, 0.2, 0.3, 0.4],
    )
    assert response.status_code == 200
    body = response.json()
    assert body["embeddings"] == [[0.1, 0.2, 0.3, 0.4]]
    assert body["dimensions"] == 4
    assert body["modelInfo"] == {"provider": "local", "model": "nomic-embed-text"}


def test_response_uses_camelcase_keys(post_embed):
    body = post_embed({"texts": ["a wallet"], "purpose": "search_query"}).json()
    assert "modelInfo" in body and "model_info" not in body


def test_model_info_reports_embed_model_not_chat_model(post_embed):
    # /embed must report the embed model — regression guard on resolve_model_info.
    body = post_embed({"texts": ["x"], "purpose": "lost_report"}).json()
    assert body["modelInfo"]["model"] == "nomic-embed-text"


def test_batch_returns_one_vector_per_text(post_embed):
    response = post_embed(
        {"texts": ["one", "two", "three"], "purpose": "lost_report"},
        embed_vector=[1.0, 2.0],
    )
    body = response.json()
    assert len(body["embeddings"]) == 3
    assert body["dimensions"] == 2


def test_embeddings_are_parallel_to_texts(post_embed):
    response = post_embed(
        {"texts": ["xxxxx", "x", "xxx"], "purpose": "found_item"},
        embed_vector=lambda text: [float(len(text))],
    )
    assert response.json()["embeddings"] == [[5.0], [1.0], [3.0]]


def test_max_batch_of_32_is_accepted(post_embed):
    response = post_embed({"texts": ["t"] * 32, "purpose": "lost_report"})
    assert response.status_code == 200
    assert len(response.json()["embeddings"]) == 32


# --- 400 VALIDATION_ERROR ------------------------------------------------


def test_empty_texts_list_returns_400(post_embed):
    response = post_embed({"texts": [], "purpose": "lost_report"})
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_too_many_texts_returns_400(post_embed):
    response = post_embed({"texts": ["t"] * 33, "purpose": "lost_report"})
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_empty_string_in_texts_returns_400(post_embed):
    response = post_embed({"texts": ["ok", ""], "purpose": "lost_report"})
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_oversized_text_returns_400(post_embed):
    response = post_embed({"texts": ["x" * 8001], "purpose": "lost_report"})
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_missing_texts_returns_400(post_embed):
    response = post_embed({"purpose": "lost_report"})
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_missing_purpose_returns_400(post_embed):
    response = post_embed({"texts": ["a wallet"]})
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_invalid_purpose_returns_400(post_embed):
    response = post_embed({"texts": ["a wallet"], "purpose": "smalltalk"})
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


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
def test_provider_errors_map_to_contract_status(post_embed, error, status, code):
    response = post_embed(
        {"texts": ["a wallet"], "purpose": "lost_report"}, raise_on_embed=error
    )
    assert response.status_code == status
    assert response.json()["code"] == code


# --- error envelope shape ------------------------------------------------


def test_error_envelope_is_flat(post_embed):
    body = post_embed({"texts": [], "purpose": "lost_report"}).json()
    assert set(body) <= {"code", "message", "details"}
    assert isinstance(body["code"], str)
    assert isinstance(body["message"], str)


# --- uncaught exception --------------------------------------------------


def test_uncaught_exception_returns_500_envelope(client_no_raise):
    fake = FakeProvider(raise_on_embed=ValueError("boom"))
    app.dependency_overrides[get_llm] = lambda: fake
    response = client_no_raise.post(
        "/embed", json={"texts": ["x"], "purpose": "lost_report"}
    )
    assert response.status_code == 500
    body = response.json()
    assert body["code"] == "INTERNAL_ERROR"
    assert "boom" not in body["message"]
