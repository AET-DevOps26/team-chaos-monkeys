import pytest
from fastapi.testclient import TestClient
from unittest.mock import AsyncMock, patch


def _patched_provider(embed_return: list[float]):
    p = AsyncMock()
    p.embed = AsyncMock(return_value=embed_return)
    p.aclose = AsyncMock()
    return p


def test_startup_probe_passes_when_dim_matches(monkeypatch):
    monkeypatch.setenv("EMBEDDING_DIMENSIONS", "768")
    monkeypatch.setenv("GENAI_PROVIDER", "fake")
    with patch("app.main.build_provider", return_value=_patched_provider([0.0] * 768)):
        from app.main import app
        with TestClient(app) as client:
            assert client.get("/health").status_code == 200
            assert app.state.embed_dimensions_actual == 768


def test_startup_probe_hard_fails_when_dim_mismatches(monkeypatch):
    monkeypatch.setenv("EMBEDDING_DIMENSIONS", "768")
    monkeypatch.setenv("GENAI_PROVIDER", "fake")
    with patch("app.main.build_provider", return_value=_patched_provider([0.0] * 1024)):
        from app.main import app
        with pytest.raises(RuntimeError, match="768.*1024"):
            with TestClient(app):
                pass


def test_startup_probe_hard_fails_when_provider_raises(monkeypatch):
    monkeypatch.setenv("EMBEDDING_DIMENSIONS", "768")
    monkeypatch.setenv("GENAI_PROVIDER", "fake")
    bad = AsyncMock()
    bad.embed = AsyncMock(side_effect=RuntimeError("provider unreachable"))
    bad.aclose = AsyncMock()
    with patch("app.main.build_provider", return_value=bad):
        from app.main import app
        with pytest.raises(RuntimeError):
            with TestClient(app):
                pass
