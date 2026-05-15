"""End-to-end test for the /_diagnostic endpoint.

This verifies the FastAPI wiring as a whole:
  - lifespan builds Settings successfully
  - dependency injection delivers the LLM provider to the route
  - the route exercises both chat() and embed()
  - the exception handler chain maps LLMError -> HTTP status correctly
"""

from __future__ import annotations

from fastapi.testclient import TestClient

from app.exceptions import LLMRateLimitError, LLMUnavailableError
from app.providers.fake import FakeProvider


def test_diagnostic_happy_path(client_with_fake: TestClient) -> None:
    response = client_with_fake.get("/_diagnostic")

    assert response.status_code == 200
    body = response.json()
    assert body["provider"] == "local"
    assert body["chat_ok"] is True
    assert body["embed_ok"] is True
    assert body["chat_model"] == "llama3.2:3b"
    assert body["embed_model"] == "nomic-embed-text"
    assert body["chat_latency_ms"] >= 0
    assert body["embed_latency_ms"] >= 0


def test_diagnostic_rate_limit_returns_429(
    client_with_fake: TestClient, fake_provider: FakeProvider
) -> None:
    # Replace the fake provider with one that raises rate-limit on chat
    rate_limited = FakeProvider(
        raise_on_chat=LLMRateLimitError("simulated rate limit")
    )
    from app.dependencies import get_llm
    from app.main import app

    app.dependency_overrides[get_llm] = lambda: rate_limited

    response = client_with_fake.get("/_diagnostic")

    assert response.status_code == 429
    body = response.json()
    assert body["error"]["code"] == "PROVIDER_RATE_LIMITED"
    assert "simulated rate limit" in body["error"]["message"]


def test_diagnostic_provider_unavailable_returns_502(
    client_with_fake: TestClient,
) -> None:
    unavailable = FakeProvider(
        raise_on_chat=LLMUnavailableError("upstream is down")
    )
    from app.dependencies import get_llm
    from app.main import app

    app.dependency_overrides[get_llm] = lambda: unavailable

    response = client_with_fake.get("/_diagnostic")

    assert response.status_code == 502
    assert response.json()["error"]["code"] == "PROVIDER_UNAVAILABLE"


def test_diagnostic_not_in_openapi_schema(client_with_fake: TestClient) -> None:
    response = client_with_fake.get("/openapi.json")

    assert response.status_code == 200
    paths = response.json()["paths"]
    assert "/_diagnostic" not in paths
