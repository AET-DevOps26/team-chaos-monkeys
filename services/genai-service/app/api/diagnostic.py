"""Internal diagnostic endpoint.

NOT part of the public OpenAPI contract. Exercises both LLM primitives
(`chat` + `embed`) against the currently configured provider so that a
single `curl /_diagnostic` proves the provider switch and credentials
are wired correctly. Used during checkpoint demos and local development.

Excluded from the OpenAPI schema (`include_in_schema=False`) so it does
not leak into generated SDKs.
"""

from __future__ import annotations

import time
from typing import Literal

from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel

from app.dependencies import get_llm
from app.providers import LLMProvider

router = APIRouter(tags=["_internal"])


class DiagnosticResponse(BaseModel):
    provider: Literal["openai", "local"]
    chat_ok: bool
    embed_ok: bool
    chat_latency_ms: int
    embed_latency_ms: int
    chat_model: str
    embed_model: str


@router.get(
    "/_diagnostic",
    response_model=DiagnosticResponse,
    include_in_schema=False,
)
async def diagnostic(
    request: Request, llm: LLMProvider = Depends(get_llm)
) -> DiagnosticResponse:
    settings = request.app.state.settings

    if settings.provider == "openai":
        chat_model = settings.openai_chat_model
        embed_model = settings.openai_embed_model
    else:
        chat_model = settings.ollama_chat_model
        embed_model = settings.ollama_embed_model

    chat_start = time.perf_counter()
    chat_text = await llm.chat(
        [{"role": "user", "content": "reply with the single word ok"}]
    )
    chat_latency_ms = int((time.perf_counter() - chat_start) * 1000)

    embed_start = time.perf_counter()
    vector = await llm.embed("ping")
    embed_latency_ms = int((time.perf_counter() - embed_start) * 1000)

    return DiagnosticResponse(
        provider=settings.provider,
        chat_ok=bool(chat_text),
        embed_ok=len(vector) > 0,
        chat_latency_ms=chat_latency_ms,
        embed_latency_ms=embed_latency_ms,
        chat_model=chat_model,
        embed_model=embed_model,
    )
