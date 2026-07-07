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
from prometheus_fastapi_instrumentator import Instrumentator
from prometheus_fastapi_instrumentator import routing as instrumentator_routing
from starlette.routing import Match
from starlette.types import Scope

from app.api import answer, diagnostic, embed, extract, health, verify
from app.config import Settings
from app.errors import register_exception_handlers
from app.logging_config import setup_json_logging_if_configured
from app.metrics import build_info
from app.middleware import MaxBodySizeMiddleware
from app.providers import build_provider
from app.tracing import setup_tracing_if_configured


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = Settings()
    app.state.settings = settings
    app.state.llm = build_provider(settings)
    build_info.info({"provider": settings.provider})

    # Startup probe — verify the configured embed model produces the configured dim.
    # Hard-fail on mismatch: better a crashlooping container than silent data corruption.
    try:
        probe = await app.state.llm.embed(text="probe")
    except Exception as e:
        raise RuntimeError(
            f"genai-service startup probe failed: {e!r} "
            f"(provider={settings.provider}, embed_model={settings.openai_embed_model if settings.provider == 'openai' else settings.ollama_embed_model})"
        ) from e

    actual_dim = len(probe)
    if actual_dim != settings.embedding_dimensions:
        raise RuntimeError(
            f"Embedding dim mismatch at startup: configured={settings.embedding_dimensions}, "
            f"actual={actual_dim} (provider={settings.provider}). "
            f"Check EMBEDDING_DIMENSIONS / OPENAI_EMBED_MODEL / OLLAMA_EMBED_MODEL."
        )
    app.state.embed_dimensions_actual = actual_dim

    try:
        yield
    finally:
        await app.state.llm.aclose()


# Runs after uvicorn's own logging setup (uvicorn configures logging before
# importing the app), so the JSON handler wins when LOG_FORMAT=json.
setup_json_logging_if_configured()

app = FastAPI(
    title="FoundFlow GenAI service",
    version="0.1.0",
    lifespan=lifespan,
)

register_exception_handlers(app)

# Reject oversized request bodies (default 8 MiB) at the ASGI layer
# before Pydantic parses anything. See ADR 0001 §2/§8 for sizing and
# `app.middleware` for the implementation.
app.add_middleware(MaxBodySizeMiddleware)

# After MaxBodySizeMiddleware so the OTel middleware sits outermost and
# oversized-body rejections still show up as spans.
setup_tracing_if_configured(app)


def _instrumentator_effective_candidate_name(scope: Scope, route: object) -> str | None:
    candidates = getattr(route, "effective_candidates", None)
    if not callable(candidates):
        return None

    for candidate in candidates():
        matches = getattr(candidate, "matches", None)
        if matches is None:
            continue

        match, _ = matches(scope)
        if match == Match.FULL:
            return getattr(candidate, "path", None)
    return None


def _instrumentator_route_name(scope: Scope, routes: list[object]) -> str | None:
    for route in routes:
        child_routes = getattr(route, "routes", None)
        matches = getattr(route, "matches", None)
        if matches is None:
            if child_routes:
                child_name = _instrumentator_route_name(scope, child_routes)
                if child_name:
                    return child_name
            continue

        match, child_scope = matches(scope)
        route_path = getattr(route, "path", None)

        if match == Match.FULL:
            route_path = route_path or _instrumentator_effective_candidate_name(
                {**scope, **child_scope},
                route,
            )
            if child_routes:
                child_name = _instrumentator_route_name(
                    {**scope, **child_scope},
                    child_routes,
                )
                if child_name:
                    return f"{route_path}{child_name}"
            return route_path or None
    return None


def _safe_instrumentator_route_name(request) -> str | None:
    return _instrumentator_route_name(request.scope, request.app.routes)


# prometheus-fastapi-instrumentator 8.0.0 assumes every FastAPI route object has
# `.path`. FastAPI 0.136 adds internal `_IncludedRouter` entries that do not, so
# route-name detection must skip/unwrap those entries before instrumentation.
instrumentator_routing.get_route_name = _safe_instrumentator_route_name

# `/metrics` exposes the default HTTP histograms/counters plus the
# GenAI-specific metrics defined in `app.metrics`. Kept out of the
# OpenAPI schema since it is not part of the service contract.
Instrumentator().instrument(app).expose(
    app, endpoint="/metrics", include_in_schema=False
)

app.include_router(health.router)
app.include_router(extract.router)
app.include_router(embed.router)
app.include_router(verify.router)
app.include_router(answer.router)
app.include_router(diagnostic.router)
