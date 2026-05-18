"""POST /generate-message — guest pickup notification text (issue #53).

Thin HTTP layer over `app.generation`: FastAPI validates the body against
`GenerateMessageRequest`, the route delegates to `generate_message`, and
assembles the `GenerateMessageResponse`. Provider and model-output failures
propagate as exceptions and are mapped to the contract error envelope by
`app.errors`.

On any failure the caller (`notification-service`) is expected to fall back
to its own static template — generation is best-effort. See
docs/superpowers/specs/2026-05-18-genai-generate-message-design.md.
"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends

from app.api.schemas import (
    ErrorResponse,
    GenerateMessageRequest,
    GenerateMessageResponse,
)
from app.config import Settings
from app.dependencies import get_llm, get_settings
from app.extraction import resolve_model_info
from app.generation import generate_message
from app.providers import LLMProvider

router = APIRouter(tags=["genai"])

_ERROR_RESPONSES: dict[int | str, dict[str, Any]] = {
    400: {"model": ErrorResponse, "description": "Request body failed validation"},
    422: {"model": ErrorResponse, "description": "Model output failed schema validation"},
    429: {"model": ErrorResponse, "description": "Upstream provider rate-limited"},
    500: {"model": ErrorResponse, "description": "Internal server error"},
    502: {"model": ErrorResponse, "description": "Upstream provider unavailable"},
    504: {"model": ErrorResponse, "description": "Upstream provider timed out"},
}


@router.post(
    "/generate-message",
    operation_id="generateMessage",
    response_model=GenerateMessageResponse,
    summary="Generate guest pickup notification text",
    responses=_ERROR_RESPONSES,
)
async def post_generate_message(
    payload: GenerateMessageRequest,
    llm: LLMProvider = Depends(get_llm),
    settings: Settings = Depends(get_settings),
) -> GenerateMessageResponse:
    subject, body = await generate_message(payload, llm)
    return GenerateMessageResponse(
        subject=subject,
        body=body,
        model_info=resolve_model_info(settings),
    )
