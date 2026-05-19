"""POST /verify-match — match verification & explanation (issue #104).

Thin HTTP layer over `app.verification`: FastAPI validates the body against
`VerifyMatchRequest`, the route delegates to `verify_match`, and assembles the
`VerifyMatchResponse`. Provider and model-output failures propagate as
exceptions and are mapped to the contract error envelope by `app.errors`.

See docs/superpowers/specs/2026-05-19-genai-match-verification-design.md.
"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends

from app.api.schemas import ErrorResponse, VerifyMatchRequest, VerifyMatchResponse
from app.config import Settings
from app.dependencies import get_llm, get_settings
from app.extraction import resolve_model_info
from app.providers import LLMProvider
from app.verification import verify_match

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
    "/verify-match",
    operation_id="verifyMatch",
    response_model=VerifyMatchResponse,
    summary="Verify and explain a candidate lost/found match",
    responses=_ERROR_RESPONSES,
)
async def post_verify_match(
    payload: VerifyMatchRequest,
    llm: LLMProvider = Depends(get_llm),
    settings: Settings = Depends(get_settings),
) -> VerifyMatchResponse:
    result = await verify_match(payload, llm)
    return VerifyMatchResponse(
        verdict=result.verdict,
        confidence=result.confidence,
        rationale=result.rationale,
        model_info=resolve_model_info(settings),
    )
