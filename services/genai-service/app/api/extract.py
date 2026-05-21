"""POST /extract-attributes — structured attribute extraction (issue #49).

Thin HTTP layer over `app.extraction`: FastAPI validates the body against
`ExtractAttributesRequest`, the route delegates to `extract_attributes`,
and assembles the `ExtractAttributesResponse`. Provider, validation, and
model-output failures propagate as exceptions and are mapped to the
contract error envelope by `app.errors`.
"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends

from app.api.schemas import (
    ErrorResponse,
    ExtractAttributesRequest,
    ExtractAttributesResponse,
)
from app.config import Settings
from app.dependencies import get_llm, get_settings
from app.extraction import extract_attributes
from app.model_info import resolve_model_info
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
    "/extract-attributes",
    operation_id="extractAttributes",
    response_model=ExtractAttributesResponse,
    summary="Extract structured attributes from a free-text lost-item description",
    responses=_ERROR_RESPONSES,
)
async def post_extract_attributes(
    payload: ExtractAttributesRequest,
    llm: LLMProvider = Depends(get_llm),
    settings: Settings = Depends(get_settings),
) -> ExtractAttributesResponse:
    attributes = await extract_attributes(payload.description, payload.language, llm)
    return ExtractAttributesResponse(
        attributes=attributes, model_info=resolve_model_info(settings)
    )
