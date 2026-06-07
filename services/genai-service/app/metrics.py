"""Prometheus metric definitions for the GenAI service (issues #54, #90).

Two layers of metrics are exposed at `/metrics`:

1. HTTP-level — request count, latency, request/response sizes — come
   from `prometheus-fastapi-instrumentator` and are wired in `app.main`.
   They live under the standard `http_*` namespace.

2. GenAI-specific — defined here:

   - `genai_provider_requests_total{provider, endpoint, outcome, modality}` —
     every call to the LLM provider, labelled by outcome
     (ok / timeout / rate_limit / unavailable / bad_request / error) and
     modality (text / image / both — see ADR 0001 §10).
   - `genai_provider_request_duration_seconds{provider, endpoint, modality}` —
     latency of the provider call itself, separate from end-to-end
     HTTP request latency.
   - `genai_validation_errors_total{endpoint, reason, modality}` —
     structured-output validation failures, by reason
     (json_decode / wrong_type / schema) and modality.
   - `genai_build_info{provider}` — set once at startup so dashboards
     can filter by deployment.

The `modality` label was added in issue #90. Endpoints that have no
image dimension (`embed`, `verify-match`) emit `modality="text"` so the
label space stays at the documented three values.

`observe_provider_call` is the helper domain code uses to wrap a single
provider invocation. It scopes the histogram and counter to just that
call, so downstream parsing failures do not pollute provider-side
metrics — those have their own counter.
"""

from __future__ import annotations

from contextlib import asynccontextmanager
from time import perf_counter
from typing import AsyncIterator, Final

from prometheus_client import Counter, Histogram, Info

from app.exceptions import (
    LLMBadRequestError,
    LLMError,
    LLMRateLimitError,
    LLMTimeoutError,
    LLMUnavailableError,
)

# Endpoint label values — kept as constants so call sites stay aligned.
ENDPOINT_EXTRACT: Final = "extract-attributes"
ENDPOINT_EMBED: Final = "embed"
ENDPOINT_VERIFY: Final = "verify-match"
ENDPOINT_ANSWER: Final = "answer"

# Modality label values. Endpoints that don't take an image emit "text".
MODALITY_TEXT: Final = "text"
MODALITY_IMAGE: Final = "image"
MODALITY_BOTH: Final = "both"

# ModelOutputError reasons — kept as constants for the same reason.
VALIDATION_JSON_DECODE: Final = "json_decode"
VALIDATION_WRONG_TYPE: Final = "wrong_type"
VALIDATION_SCHEMA: Final = "schema"


provider_requests_total = Counter(
    "genai_provider_requests_total",
    "GenAI provider invocations, by endpoint, provider, outcome, and modality.",
    labelnames=("provider", "endpoint", "outcome", "modality"),
)

provider_request_duration_seconds = Histogram(
    "genai_provider_request_duration_seconds",
    "GenAI provider invocation latency in seconds.",
    labelnames=("provider", "endpoint", "modality"),
)

validation_errors_total = Counter(
    "genai_validation_errors_total",
    "Model-output validation failures, by endpoint, reason, and modality.",
    labelnames=("endpoint", "reason", "modality"),
)

build_info = Info(
    "genai_build",
    "Build / deployment info for the GenAI service.",
)


# Ordered so subclasses match before their LLMError catch-all base.
_OUTCOME_BY_EXC: Final = (
    (LLMTimeoutError, "timeout"),
    (LLMRateLimitError, "rate_limit"),
    (LLMUnavailableError, "unavailable"),
    (LLMBadRequestError, "bad_request"),
    (LLMError, "error"),
)


def _outcome_for(exc: BaseException) -> str:
    for cls, outcome in _OUTCOME_BY_EXC:
        if isinstance(exc, cls):
            return outcome
    return "error"


@asynccontextmanager
async def observe_provider_call(
    provider: str, endpoint: str, *, modality: str = MODALITY_TEXT
) -> AsyncIterator[None]:
    """Time a provider invocation and label its outcome.

    Wrap only the actual `llm.chat()` / `llm.embed()` call. The histogram
    records the elapsed time on every exit, and the counter increments
    with `outcome=ok` on success or the matching LLMError subclass label
    on failure. Anything unexpected lands on `outcome=error`.

    `modality` defaults to `"text"` so endpoints without an image
    dimension (`/embed`, `/verify-match`) keep emitting the same
    three-value label space without a per-call site change.

    Downstream parsing failures (ModelOutputError) must be raised
    *outside* this context so they do not skew provider-side accounting.
    """
    start = perf_counter()
    outcome = "ok"
    try:
        yield
    except LLMError as exc:
        outcome = _outcome_for(exc)
        raise
    except BaseException:
        outcome = "error"
        raise
    finally:
        elapsed = perf_counter() - start
        provider_request_duration_seconds.labels(provider, endpoint, modality).observe(
            elapsed
        )
        provider_requests_total.labels(provider, endpoint, outcome, modality).inc()
