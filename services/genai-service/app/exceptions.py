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


class ModelOutputError(Exception):
    """The LLM returned output that failed our schema validation.

    Distinct from `LLMError`: the provider call itself *succeeded*, but the
    model's response was malformed JSON, the wrong shape, or carried values
    that fail `ItemAttributes` validation. `app.errors` maps it to HTTP 422
    MODEL_OUTPUT_INVALID; the caller should retry once before falling back.

    Carries the raw model output and the validation errors so the response
    `details` can expose `{rawOutput, schemaErrors}` per the contract.
    """

    def __init__(
        self, message: str, *, raw_output: str, schema_errors: list[str]
    ) -> None:
        super().__init__(message)
        self.raw_output = raw_output
        self.schema_errors = schema_errors
