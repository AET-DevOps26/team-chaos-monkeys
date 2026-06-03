"""Settings load + validation tests.

These cover the "fail fast with a clear error" acceptance criterion of #51.
Validation happens at Settings() construction time, which is invoked
during FastAPI's lifespan startup — a failure there crashloops the
container instead of 500ing on the first request.
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
    `Settings()` — and these tests, which drive configuration purely through
    environment variables, would pass or fail depending on whether that file
    exists. CI has no `.env` (it is gitignored), so the gap was invisible there.
    """
    monkeypatch.chdir(tmp_path)


def _clear_env(monkeypatch: pytest.MonkeyPatch) -> None:
    for key in (
        "EMBEDDING_DIMENSIONS",
        "GENAI_PROVIDER",
        "OPENAI_API_KEY",
        "OPENAI_CHAT_MODEL",
        "OPENAI_EMBED_MODEL",
        "OLLAMA_BASE_URL",
        "OLLAMA_CHAT_MODEL",
        "OLLAMA_EMBED_MODEL",
        "GENAI_TIMEOUT_SECONDS",
    ):
        monkeypatch.delenv(key, raising=False)


def test_provider_local_loads_with_defaults(monkeypatch: pytest.MonkeyPatch) -> None:
    _clear_env(monkeypatch)
    monkeypatch.setenv("GENAI_PROVIDER", "local")

    settings = Settings()

    assert settings.provider == "local"
    assert settings.ollama_base_url == "http://ollama:11434"
    assert settings.ollama_chat_model == "llama3.2:3b"
    assert settings.ollama_embed_model == "nomic-embed-text"
    assert settings.timeout_seconds == 30


def test_provider_openai_requires_api_key(monkeypatch: pytest.MonkeyPatch) -> None:
    _clear_env(monkeypatch)
    monkeypatch.setenv("GENAI_PROVIDER", "openai")

    with pytest.raises(ValidationError) as exc_info:
        Settings()

    assert "OPENAI_API_KEY is required" in str(exc_info.value)


def test_provider_openai_with_api_key(monkeypatch: pytest.MonkeyPatch) -> None:
    _clear_env(monkeypatch)
    monkeypatch.setenv("GENAI_PROVIDER", "openai")
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test")

    settings = Settings()

    assert settings.provider == "openai"
    assert settings.openai_api_key == "sk-test"
    assert settings.openai_chat_model == "gpt-4o-mini"
    assert settings.openai_embed_model == "text-embedding-3-small"


def test_unknown_provider_rejected(monkeypatch: pytest.MonkeyPatch) -> None:
    _clear_env(monkeypatch)
    monkeypatch.setenv("GENAI_PROVIDER", "azure")

    with pytest.raises(ValidationError) as exc_info:
        Settings()

    # pydantic's Literal validator names the rejected value
    assert "azure" in str(exc_info.value).lower() or "literal" in str(exc_info.value).lower()


def test_missing_provider_rejected(monkeypatch: pytest.MonkeyPatch) -> None:
    _clear_env(monkeypatch)

    with pytest.raises(ValidationError):
        Settings()


def test_overrides_applied(monkeypatch: pytest.MonkeyPatch) -> None:
    _clear_env(monkeypatch)
    monkeypatch.setenv("GENAI_PROVIDER", "local")
    monkeypatch.setenv("OLLAMA_BASE_URL", "http://host.docker.internal:11434")
    monkeypatch.setenv("OLLAMA_CHAT_MODEL", "qwen2.5:0.5b")
    monkeypatch.setenv("GENAI_TIMEOUT_SECONDS", "10")

    settings = Settings()

    assert settings.ollama_base_url == "http://host.docker.internal:11434"
    assert settings.ollama_chat_model == "qwen2.5:0.5b"
    assert settings.timeout_seconds == 10


def test_timeout_must_be_positive(monkeypatch: pytest.MonkeyPatch) -> None:
    _clear_env(monkeypatch)
    monkeypatch.setenv("GENAI_PROVIDER", "local")
    monkeypatch.setenv("GENAI_TIMEOUT_SECONDS", "0")

    with pytest.raises(ValidationError):
        Settings()
