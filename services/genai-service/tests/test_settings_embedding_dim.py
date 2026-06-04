"""Tests for Settings.embedding_dimensions field.

This field controls the vector dimension for embeddings produced by the
GenAI service. It defaults to 768 (the standard for nomic-embed-text)
and can be overridden via EMBEDDING_DIMENSIONS environment variable.
"""

from __future__ import annotations

from pathlib import Path

import pytest
from pydantic import ValidationError

from app.config import Settings


@pytest.fixture(autouse=True)
def _isolate_dotenv(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    """Run each test from an empty working directory.

    `Settings` loads `.env` relative to the current directory. Without this,
    a developer's local `services/genai-service/.env` would leak into
    `Settings()`.
    """
    monkeypatch.chdir(tmp_path)


def _clear_env(monkeypatch: pytest.MonkeyPatch) -> None:
    for key in (
        "GENAI_PROVIDER",
        "OPENAI_API_KEY",
        "OPENAI_CHAT_MODEL",
        "OPENAI_EMBED_MODEL",
        "OLLAMA_BASE_URL",
        "OLLAMA_CHAT_MODEL",
        "OLLAMA_EMBED_MODEL",
        "GENAI_TIMEOUT_SECONDS",
        "EMBEDDING_DIMENSIONS",
    ):
        monkeypatch.delenv(key, raising=False)


def test_embedding_dimensions_default_is_768(monkeypatch: pytest.MonkeyPatch) -> None:
    _clear_env(monkeypatch)
    monkeypatch.setenv("GENAI_PROVIDER", "local")

    settings = Settings()

    assert settings.embedding_dimensions == 768


def test_embedding_dimensions_reads_env(monkeypatch: pytest.MonkeyPatch) -> None:
    _clear_env(monkeypatch)
    monkeypatch.setenv("GENAI_PROVIDER", "local")
    monkeypatch.setenv("EMBEDDING_DIMENSIONS", "1536")

    settings = Settings()

    assert settings.embedding_dimensions == 1536


def test_embedding_dimensions_rejects_zero(monkeypatch: pytest.MonkeyPatch) -> None:
    _clear_env(monkeypatch)
    monkeypatch.setenv("GENAI_PROVIDER", "local")
    monkeypatch.setenv("EMBEDDING_DIMENSIONS", "0")

    with pytest.raises(ValidationError):
        Settings()


def test_embedding_dimensions_rejects_negative(monkeypatch: pytest.MonkeyPatch) -> None:
    _clear_env(monkeypatch)
    monkeypatch.setenv("GENAI_PROVIDER", "local")
    monkeypatch.setenv("EMBEDDING_DIMENSIONS", "-1")

    with pytest.raises(ValidationError):
        Settings()
