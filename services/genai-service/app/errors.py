"""HTTP error envelope and exception handlers.

Every non-2xx response uses the flat `ErrorResponse` envelope from
api/openapi.yaml — `{code, message, details?}`. This module owns that
envelope and registers the handlers that map each failure type to its
contract HTTP status:

    RequestValidationError  -> 400  VALIDATION_ERROR
    ModelOutputError        -> 422  MODEL_OUTPUT_INVALID
    LLMRateLimitError       -> 429  PROVIDER_RATE_LIMITED
    LLMTimeoutError         -> 504  PROVIDER_TIMEOUT
    LLMUnavailableError     -> 502  PROVIDER_UNAVAILABLE
    LLMError (base)         -> 502  PROVIDER_UNAVAILABLE
    LLMBadRequestError      -> 500  INTERNAL_ERROR
    Exception (uncaught)    -> 500  INTERNAL_ERROR

FastAPI resolves the most specific registered handler per exception, so
the `LLMError` and `Exception` handlers act as fallbacks for subclasses
without a more specific entry.

Message-disclosure policy
-------------------------
Upstream-operational failures (429/502/504) surface the provider adapter's
message — it is adapter-controlled and useful in the caller's logs. Server
faults (`LLMBadRequestError` and uncaught exceptions -> 500) return a generic
message and log the detail, so a misconfiguration or stack detail never
leaves the process.
"""

from __future__ import annotations

import logging
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.schemas import ErrorCode
from app.exceptions import (
    ImageProcessingError,
    LLMBadRequestError,
    LLMError,
    LLMRateLimitError,
    LLMTimeoutError,
    LLMUnavailableError,
    ModelOutputError,
)
from app.metrics import validation_errors_total

logger = logging.getLogger("genai.errors")


def error_response(
    status_code: int,
    code: ErrorCode,
    message: str,
    details: dict[str, Any] | None = None,
) -> JSONResponse:
    """Build a JSONResponse carrying the contract `ErrorResponse` envelope."""
    payload: dict[str, Any] = {"code": code.value, "message": message}
    if details is not None:
        payload["details"] = details
    return JSONResponse(status_code=status_code, content=payload)


async def _handle_request_validation(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    # Surface the first failing field as {field, reason} — the shape the
    # contract's VALIDATION_ERROR example uses. `details` is a debugging aid.
    errors = exc.errors()
    first = errors[0] if errors else {}
    field = ".".join(
        str(part) for part in first.get("loc", ()) if part != "body"
    )
    details = {
        "field": field or "(body)",
        "reason": _validation_reason(first),
    }
    return error_response(
        400, ErrorCode.VALIDATION_ERROR, "Request body failed validation", details
    )


def _validation_reason(err: dict[str, Any]) -> str:
    """Extract a clean `reason` string from a Pydantic v2 error dict.

    Custom `ValueError` raised inside a `model_validator` surfaces as
    `{"type": "value_error", "msg": "Value error, <raw>"}`; we strip the
    "Value error, " prefix so the documented reasons
    (`at_least_one_required`, etc.) appear verbatim in the API response.
    Falls back to the raw `msg` for the standard built-in validators.
    """
    raw_msg = err.get("msg", "invalid")
    if err.get("type") == "value_error" and raw_msg.startswith("Value error, "):
        return raw_msg.removeprefix("Value error, ")
    return raw_msg


async def _handle_image_processing(
    request: Request, exc: ImageProcessingError
) -> JSONResponse:
    """Image input failed one of the server-side checks (decode / MIME /
    size / Pillow). Maps to 400 `VALIDATION_ERROR`; the `details.reason`
    carries the specific failure mode (`image_base64_invalid`,
    `image_mime_unsupported`, `image_too_large`, `image_decode_failed`).
    """
    details = dict(exc.details)
    details.setdefault("reason", exc.reason)
    return error_response(400, ErrorCode.VALIDATION_ERROR, str(exc), details)


async def _handle_model_output(
    request: Request, exc: ModelOutputError
) -> JSONResponse:
    validation_errors_total.labels(
        endpoint=exc.endpoint, reason=exc.reason, modality=exc.modality
    ).inc()
    return error_response(
        422,
        ErrorCode.MODEL_OUTPUT_INVALID,
        str(exc),
        {"rawOutput": exc.raw_output, "schemaErrors": exc.schema_errors},
    )


async def _handle_rate_limit(
    request: Request, exc: LLMRateLimitError
) -> JSONResponse:
    return error_response(
        429,
        ErrorCode.PROVIDER_RATE_LIMITED,
        str(exc) or "Upstream provider rate-limited the request",
    )


async def _handle_timeout(request: Request, exc: LLMTimeoutError) -> JSONResponse:
    return error_response(
        504, ErrorCode.PROVIDER_TIMEOUT, str(exc) or "Upstream provider timed out"
    )


async def _handle_unavailable(
    request: Request, exc: LLMUnavailableError
) -> JSONResponse:
    return error_response(
        502,
        ErrorCode.PROVIDER_UNAVAILABLE,
        str(exc) or "Upstream provider is unavailable",
    )


async def _handle_bad_request(
    request: Request, exc: LLMBadRequestError
) -> JSONResponse:
    # A misconfigured model is a server fault — log it, disclose nothing specific.
    logger.error("provider rejected request as invalid: %s", exc)
    return error_response(
        500,
        ErrorCode.INTERNAL_ERROR,
        "Internal error while contacting the model provider",
    )


async def _handle_llm_error(request: Request, exc: LLMError) -> JSONResponse:
    return error_response(
        502, ErrorCode.PROVIDER_UNAVAILABLE, str(exc) or "Upstream provider error"
    )


async def _handle_uncaught(request: Request, exc: Exception) -> JSONResponse:
    logger.exception(
        "unhandled error processing %s %s", request.method, request.url.path
    )
    return error_response(500, ErrorCode.INTERNAL_ERROR, "Internal server error")


def register_exception_handlers(app: FastAPI) -> None:
    """Install the contract error handlers on the FastAPI app."""
    app.add_exception_handler(RequestValidationError, _handle_request_validation)
    app.add_exception_handler(ImageProcessingError, _handle_image_processing)
    app.add_exception_handler(ModelOutputError, _handle_model_output)
    app.add_exception_handler(LLMRateLimitError, _handle_rate_limit)
    app.add_exception_handler(LLMTimeoutError, _handle_timeout)
    app.add_exception_handler(LLMUnavailableError, _handle_unavailable)
    app.add_exception_handler(LLMBadRequestError, _handle_bad_request)
    app.add_exception_handler(LLMError, _handle_llm_error)
    app.add_exception_handler(Exception, _handle_uncaught)
