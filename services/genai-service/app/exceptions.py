"""Normalized exception hierarchy for the GenAI provider layer.

Each LLM* error maps 1:1 to an HTTP status declared in api/openapi.yaml.
Provider adapters catch SDK-specific exceptions and re-raise as one of
these. FastAPI exception handlers (registered in app.main) translate
each type to its HTTP status code.
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
    """Caller built an invalid request (e.g. unknown model). Maps to HTTP 400.

    This is NOT for the model returning invalid JSON — that's a caller-layer
    concern (#52) and surfaces as HTTP 422 ModelOutputInvalid.
    """
