"""POST /answer — grounded answer over retrieved snippets (issue #178).

Thin HTTP layer over `app.answer`: FastAPI validates the body against
`AnswerRequest`, the route delegates to `generate_answer`, and assembles the
`AnswerResponse`. Provider and model-output failures propagate as exceptions
and are mapped to the contract error envelope by `app.errors`.
"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends

from app.answer import generate_answer
from app.api.schemas import AnswerRequest, AnswerResponse, ErrorResponse
from app.config import Settings
from app.dependencies import get_llm, get_settings
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
    "/answer",
    operation_id="answer",
    response_model=AnswerResponse,
    summary="Generate a grounded, cited answer over retrieved items",
    responses=_ERROR_RESPONSES,
)
async def post_answer(
    payload: AnswerRequest,
    llm: LLMProvider = Depends(get_llm),
    settings: Settings = Depends(get_settings),
) -> AnswerResponse:
    result = await generate_answer(payload, llm)
    return AnswerResponse(
        answer=result.answer,
        citations=result.citations,
        grounded=result.grounded,
        model_info=resolve_model_info(settings),
    )
