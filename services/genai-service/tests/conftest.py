"""Shared test fixtures."""

from __future__ import annotations

from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.dependencies import get_llm
from app.main import app
from app.providers.fake import FakeProvider


@pytest.fixture
def fake_provider() -> FakeProvider:
    return FakeProvider()


@pytest.fixture
def client_with_fake(
    fake_provider: FakeProvider, monkeypatch: pytest.MonkeyPatch
) -> Iterator[TestClient]:
    """A TestClient where the provider is replaced with FakeProvider.

    We still need `Settings` to construct successfully during lifespan, so
    set a minimal valid env first, then override the LLM dependency.
    """
    monkeypatch.setenv("GENAI_PROVIDER", "local")

    # Bypass real provider construction in lifespan by pre-installing the fake.
    app.state.settings = Settings()
    app.state.llm = fake_provider
    app.dependency_overrides[get_llm] = lambda: fake_provider

    # Skip lifespan to avoid build_provider running again; FastAPI's TestClient
    # runs lifespan by default — disable it here.
    with TestClient(app) as client:  # lifespan runs but build_provider returns a real instance...
        # ...so override state again after lifespan ran.
        app.state.llm = fake_provider
        app.dependency_overrides[get_llm] = lambda: fake_provider
        yield client

    app.dependency_overrides.clear()


@pytest.fixture
def client_no_raise(monkeypatch: pytest.MonkeyPatch) -> Iterator[TestClient]:
    """A TestClient that returns 5xx responses instead of re-raising them.

    The default TestClient re-raises uncaught server exceptions into the
    test; this one surfaces them as HTTP responses so the uncaught-exception
    (500) handler can be asserted on.
    """
    monkeypatch.setenv("GENAI_PROVIDER", "local")
    app.state.settings = Settings()
    with TestClient(app, raise_server_exceptions=False) as client:
        yield client
    app.dependency_overrides.clear()
