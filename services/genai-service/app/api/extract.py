"""POST /extract-attributes — structured attribute extraction.

Thin HTTP layer over `app.extraction`: FastAPI validates the body against
`ExtractAttributesRequest`, the image (when present) is prepared by
`app.image.prepare_image`, and the route delegates to `extract_attributes`
for the LLM call and validation. Provider, validation, and model-output
failures propagate as exceptions and are mapped to the contract error
envelope by `app.errors`.

Image input (#90): when `payload.image` is present, the image pipeline
decodes the base64, validates MIME and size, downscales to ≤1024 px,
strips EXIF, and re-encodes JPEG before the bytes leave the process.
Failures map to 400 `VALIDATION_ERROR` with a structured `details.reason`.
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
from app.extraction import extract_attributes, select_modality
from app.image import prepare_image
from app.model_info import resolve_model_info
from app.providers import LLMProvider

router = APIRouter(tags=["genai"])

_ERROR_RESPONSES: dict[int | str, dict[str, Any]] = {
    400: {"model": ErrorResponse, "description": "Request body failed validation"},
    413: {"model": ErrorResponse, "description": "Request body exceeded the 8 MiB cap"},
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
    summary="Extract structured attributes from a lost-item description or photo",
    responses=_ERROR_RESPONSES,
)
async def post_extract_attributes(
    payload: ExtractAttributesRequest,
    llm: LLMProvider = Depends(get_llm),
    settings: Settings = Depends(get_settings),
) -> ExtractAttributesResponse:
    # Server-side image processing runs before any provider call so its
    # failure modes (bad base64 / unsupported MIME / too large / corrupt
    # bytes) are 400s rather than provider-side 4xx noise.
    image_part = prepare_image(payload.image) if payload.image is not None else None

    # Modality drives both the metric label inside `extract_attributes`
    # and the `ModelInfo` reporting here. Computed at the route layer
    # from the request so the extraction-layer return shape stays
    # backward-compatible with the text-only path.
    modality = select_modality(payload.description, image_part)
    info_kind = "vision" if modality in ("image", "both") else "chat"

    attributes = await extract_attributes(
        payload.description, payload.language, llm, image=image_part
    )
    return ExtractAttributesResponse(
        attributes=attributes,
        model_info=resolve_model_info(settings, kind=info_kind),
    )
