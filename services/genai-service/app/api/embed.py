"""POST /embed — text embedding (issue #50).

Thin HTTP layer over `app.embedding`: FastAPI validates the body against
`EmbedRequest`, the route delegates to `embed_texts`, and assembles the
`EmbedResponse`. Provider failures propagate as exceptions and are mapped
to the contract error envelope by `app.errors`.

Unlike `/extract-attributes`, `/embed` has no 422 — there is no model-output
validation step; the provider returns vectors, not JSON to schema-check.
"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends

from app.api.schemas import EmbedRequest, EmbedResponse, ErrorResponse
from app.config import Settings
from app.dependencies import get_llm, get_settings
from app.embedding import embed_texts
from app.model_info import resolve_model_info
from app.providers import LLMProvider

router = APIRouter(tags=["genai"])

_ERROR_RESPONSES: dict[int | str, dict[str, Any]] = {
    400: {"model": ErrorResponse, "description": "Request body failed validation"},
    429: {"model": ErrorResponse, "description": "Upstream provider rate-limited"},
    500: {"model": ErrorResponse, "description": "Internal server error"},
    502: {"model": ErrorResponse, "description": "Upstream provider unavailable"},
    504: {"model": ErrorResponse, "description": "Upstream provider timed out"},
}


@router.post(
    "/embed",
    operation_id="embed",
    response_model=EmbedResponse,
    summary="Produce embedding vectors for one or more texts",
    responses=_ERROR_RESPONSES,
)
async def post_embed(
    payload: EmbedRequest,
    llm: LLMProvider = Depends(get_llm),
    settings: Settings = Depends(get_settings),
) -> EmbedResponse:
    embeddings, dimensions = await embed_texts(payload.texts, llm)
    return EmbedResponse(
        embeddings=embeddings,
        dimensions=dimensions,
        model_info=resolve_model_info(settings, kind="embed"),
    )
