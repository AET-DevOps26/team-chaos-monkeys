"""Unit tests for app.model_info — Settings → ModelInfo mapping.

Lifted out of test_extraction.py as part of #94 so the test home matches
the production module home.
"""

from __future__ import annotations

from app.config import Settings
from app.model_info import resolve_model_info


def test_resolve_model_info_local(monkeypatch):
    monkeypatch.setenv("GENAI_PROVIDER", "local")
    monkeypatch.setenv("OLLAMA_CHAT_MODEL", "llama3.2:1b")
    info = resolve_model_info(Settings())
    assert info.provider == "local"
    assert info.model == "llama3.2:1b"


def test_resolve_model_info_openai(monkeypatch):
    monkeypatch.setenv("GENAI_PROVIDER", "openai")
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test")
    monkeypatch.setenv("OPENAI_CHAT_MODEL", "gpt-4o")
    info = resolve_model_info(Settings())
    assert info.provider == "openai"
    assert info.model == "gpt-4o"


def test_resolve_model_info_embed_local(monkeypatch):
    monkeypatch.setenv("GENAI_PROVIDER", "local")
    monkeypatch.setenv("OLLAMA_EMBED_MODEL", "nomic-embed-text")
    info = resolve_model_info(Settings(), kind="embed")
    assert info.provider == "local"
    assert info.model == "nomic-embed-text"


def test_resolve_model_info_embed_openai(monkeypatch):
    monkeypatch.setenv("GENAI_PROVIDER", "openai")
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test")
    monkeypatch.setenv("OPENAI_EMBED_MODEL", "text-embedding-3-large")
    info = resolve_model_info(Settings(), kind="embed")
    assert info.provider == "openai"
    assert info.model == "text-embedding-3-large"


def test_resolve_model_info_defaults_to_chat(monkeypatch):
    # No kind arg => chat model, so #49's call site stays correct unchanged.
    monkeypatch.setenv("GENAI_PROVIDER", "local")
    monkeypatch.setenv("OLLAMA_CHAT_MODEL", "llama3.2:3b")
    monkeypatch.setenv("OLLAMA_EMBED_MODEL", "nomic-embed-text")
    info = resolve_model_info(Settings())
    assert info.model == "llama3.2:3b"
