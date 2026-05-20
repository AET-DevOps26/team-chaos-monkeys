"""FastAPI app entrypoint.

Lifespan builds the configured LLM provider once at startup. Misconfigured
deployments (unknown provider, missing OPENAI_API_KEY when provider=openai)
fail here, crashlooping the container rather than 500ing on the first
request.

Exception handlers (`app.errors`) translate provider, validation, and
model-output failures onto the HTTP statuses and error envelope declared
in api/openapi.yaml.
"""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI

from app.api import diagnostic, embed, extract, health, verify
from app.config import Settings
from app.errors import register_exception_handlers
from app.providers import build_provider


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = Settings()
    app.state.settings = settings
    app.state.llm = build_provider(settings)
    try:
        yield
    finally:
        await app.state.llm.aclose()


app = FastAPI(
    title="FoundFlow GenAI service",
    version="0.1.0",
    lifespan=lifespan,
)

register_exception_handlers(app)

app.include_router(health.router)
app.include_router(extract.router)
app.include_router(embed.router)
app.include_router(verify.router)
app.include_router(diagnostic.router)
