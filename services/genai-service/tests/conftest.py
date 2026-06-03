"""Shared test fixtures."""

from __future__ import annotations

from collections.abc import Iterator
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.dependencies import get_llm
from app.main import app
from app.providers.fake import FakeProvider

# FakeProvider's default embed vector is [0.1, 0.2, 0.3] — 3 dimensions.
# All fixtures that start the lifespan must set EMBEDDING_DIMENSIONS=3 so the
# startup probe sees a matching dim (configured == actual == 3).
_FAKE_EMBED_DIM = 3


@pytest.fixture
def fake_provider() -> FakeProvider:
    return FakeProvider()


@pytest.fixture
def client_with_fake(
    fake_provider: FakeProvider, monkeypatch: pytest.MonkeyPatch
) -> Iterator[TestClient]:
    """A TestClient where the provider is replaced with FakeProvider.

    We patch build_provider to return the fake so the lifespan startup probe
    runs against the in-process FakeProvider (no real Ollama/OpenAI needed).
    EMBEDDING_DIMENSIONS is set to match FakeProvider's default 3-dim vector.
    """
    monkeypatch.setenv("GENAI_PROVIDER", "local")
    monkeypatch.setenv("EMBEDDING_DIMENSIONS", str(_FAKE_EMBED_DIM))

    with patch("app.main.build_provider", return_value=fake_provider):
        with TestClient(app) as client:
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
    monkeypatch.setenv("EMBEDDING_DIMENSIONS", str(_FAKE_EMBED_DIM))
    fake = FakeProvider()
    with patch("app.main.build_provider", return_value=fake):
        with TestClient(app, raise_server_exceptions=False) as client:
            yield client
    app.dependency_overrides.clear()
