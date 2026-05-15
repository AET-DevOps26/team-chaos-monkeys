"""FastAPI app entrypoint.

Lifespan builds the configured LLM provider once at startup. Misconfigured
deployments (unknown provider, missing OPENAI_API_KEY when provider=openai)
fail here, crashlooping the container rather than 500ing on the first
request — that's the "fail fast with clear error" acceptance criterion
of #51.

Exception handlers translate the normalized LLMError hierarchy onto the
HTTP status codes declared in api/openapi.yaml.
"""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import AsyncIterator

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.api import diagnostic, health
from app.config import Settings
from app.exceptions import (
    LLMBadRequestError,
    LLMError,
    LLMRateLimitError,
    LLMTimeoutError,
    LLMUnavailableError,
)
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


def _error_payload(message: str, code: str) -> dict[str, dict[str, str]]:
    return {"error": {"code": code, "message": message}}


@app.exception_handler(LLMRateLimitError)
async def _handle_rate_limit(request: Request, exc: LLMRateLimitError) -> JSONResponse:
    return JSONResponse(
        status_code=429,
        content=_error_payload(str(exc), "PROVIDER_RATE_LIMITED"),
    )


@app.exception_handler(LLMTimeoutError)
async def _handle_timeout(request: Request, exc: LLMTimeoutError) -> JSONResponse:
    return JSONResponse(
        status_code=504,
        content=_error_payload(str(exc), "PROVIDER_TIMEOUT"),
    )


@app.exception_handler(LLMUnavailableError)
async def _handle_unavailable(
    request: Request, exc: LLMUnavailableError
) -> JSONResponse:
    return JSONResponse(
        status_code=502,
        content=_error_payload(str(exc), "PROVIDER_UNAVAILABLE"),
    )


@app.exception_handler(LLMBadRequestError)
async def _handle_bad_request(
    request: Request, exc: LLMBadRequestError
) -> JSONResponse:
    return JSONResponse(
        status_code=400,
        content=_error_payload(str(exc), "BAD_REQUEST"),
    )


@app.exception_handler(LLMError)
async def _handle_generic_llm_error(request: Request, exc: LLMError) -> JSONResponse:
    return JSONResponse(
        status_code=502,
        content=_error_payload(str(exc), "PROVIDER_ERROR"),
    )


app.include_router(health.router)
app.include_router(diagnostic.router)
