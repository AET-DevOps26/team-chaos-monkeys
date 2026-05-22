"""Exception types for the GenAI service.

The `LLMError` hierarchy normalises provider failures: adapters catch
SDK-specific exceptions and re-raise as one of these. `ModelOutputError`
is separate — the provider call succeeded but the model's response was
unusable. FastAPI exception handlers (registered in `app.errors`) translate
each type to the HTTP status and error envelope declared in api/openapi.yaml.
"""

from __future__ import annotations


class LLMError(Exception):
    """Generic provider failure. Falls back to HTTP 502."""


class LLMRateLimitError(LLMError):
    """Upstream provider rate-limited the request. Maps to HTTP 429."""


class LLMTimeoutError(LLMError):
    """Upstream provider exceeded the configured timeout. Maps to HTTP 504."""


class LLMUnavailableError(LLMError):
    """Upstream provider is unreachable or returned 5xx. Maps to HTTP 502."""


class LLMBadRequestError(LLMError):
    """The provider rejected the request as invalid (e.g. unknown model).

    An unknown or misconfigured model is a server-side fault, not a bad
    request from the API caller — `app.errors` maps it to HTTP 500
    INTERNAL_ERROR. This is NOT for the model returning invalid JSON; that
    is `ModelOutputError` below and surfaces as HTTP 422.
    """


class ImageProcessingError(Exception):
    """The image input could not be decoded, validated, or processed.

    Distinct from `LLMError` (provider failed) and `ModelOutputError`
    (provider succeeded but the model's response was bad): this fires
    before any provider call, when the request's `image` field cannot be
    decoded or rejects one of the server-side checks.

    `app.errors` maps it to HTTP 400 `VALIDATION_ERROR`; the `reason` is
    carried into `details.reason` so the caller can distinguish base64
    decode failures from MIME, size, or Pillow-decode rejections.
    `details` carries the extra structured context (sizes, field name)
    that the error envelope advertises in `api/openapi.yaml`.
    """

    def __init__(
        self,
        message: str,
        *,
        reason: str,
        details: dict[str, object] | None = None,
    ) -> None:
        super().__init__(message)
        self.reason = reason
        self.details: dict[str, object] = dict(details) if details else {}


class ModelOutputError(Exception):
    """The LLM returned output that failed our schema validation.

    Distinct from `LLMError`: the provider call itself *succeeded*, but the
    model's response was malformed JSON, the wrong shape, or carried values
    that fail `ItemAttributes` validation. `app.errors` maps it to HTTP 422
    MODEL_OUTPUT_INVALID; the caller should retry once before falling back.

    Carries the raw model output and the validation errors so the response
    `details` can expose `{rawOutput, schemaErrors}` per the contract. The
    `endpoint` and `reason` discriminators are consumed by `app.metrics` for
    the `genai_validation_errors_total{endpoint, reason}` counter so
    dashboards can break failures down by kind (json_decode / wrong_type /
    schema). Both are sourced from the constants in `app.metrics` at the
    raise site so the label values stay aligned with the provider metrics
    that share the same `endpoint` label.
    """

    def __init__(
        self,
        message: str,
        *,
        endpoint: str,
        reason: str,
        raw_output: str,
        schema_errors: list[str],
        modality: str = "text",
    ) -> None:
        super().__init__(message)
        self.endpoint = endpoint
        self.reason = reason
        self.raw_output = raw_output
        self.schema_errors = schema_errors
        # Carries the request modality (text/image/both) so the metrics
        # helper in `app.errors` can stamp the correct label when this
        # exception is observed. Defaults to "text" for back-compat with
        # any older raise sites.
        self.modality = modality
