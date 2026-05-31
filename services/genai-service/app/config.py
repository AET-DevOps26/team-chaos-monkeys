"""Service configuration loaded from environment variables.

Flat env-var schema chosen to map cleanly onto docker-compose `environment:`
blocks, Kubernetes `ConfigMap`/`Secret`, and GitHub Actions repo secrets.
A cross-field validator enforces fail-fast on the active provider's
required fields so the container crashloops at startup rather than 500ing
on the first request.
"""

from __future__ import annotations

from typing import Literal

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # `fake` is a deterministic in-process provider used by docker-compose
    # E2E and downstream service integration tests. It never makes a network
    # call and returns canned JSON shaped like `ItemAttributes` so the
    # extraction path succeeds without an OpenAI key or local Ollama.
    provider: Literal["openai", "local", "fake"] = Field(alias="GENAI_PROVIDER")

    openai_api_key: str | None = Field(default=None, alias="OPENAI_API_KEY")
    openai_chat_model: str = Field(default="gpt-4o-mini", alias="OPENAI_CHAT_MODEL")
    openai_embed_model: str = Field(
        default="text-embedding-3-small", alias="OPENAI_EMBED_MODEL"
    )

    ollama_base_url: str = Field(default="http://ollama:11434", alias="OLLAMA_BASE_URL")
    ollama_chat_model: str = Field(default="llama3.2:3b", alias="OLLAMA_CHAT_MODEL")
    ollama_vision_model: str = Field(default="llava:7b", alias="OLLAMA_VISION_MODEL")
    ollama_embed_model: str = Field(
        default="nomic-embed-text", alias="OLLAMA_EMBED_MODEL"
    )

    timeout_seconds: int = Field(default=30, alias="GENAI_TIMEOUT_SECONDS", ge=1)

    @model_validator(mode="after")
    def _require_active_provider_config(self) -> "Settings":
        if self.provider == "openai" and not self.openai_api_key:
            raise ValueError(
                "OPENAI_API_KEY is required when GENAI_PROVIDER=openai"
            )
        return self
