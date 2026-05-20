"""Prometheus metrics exposed at /metrics (issue #54).

The metric definitions are global singletons (one shared default
registry), so each test reads counter values *before* and *after*
exercising the endpoint and asserts on the delta — that makes the
suite robust against test ordering and tests bumping the same
counter twice.
"""

from __future__ import annotations

import json

from prometheus_client import REGISTRY

from app.exceptions import LLMRateLimitError, LLMTimeoutError


def _sample(name: str, labels: dict[str, str]) -> float:
    value = REGISTRY.get_sample_value(name, labels)
    return value if value is not None else 0.0


_VALID_EXTRACT_BODY = {"description": "a dark blue umbrella near the entrance"}
_VALID_EXTRACT_OUTPUT = json.dumps(
    {
        "category": "umbrella",
        "brand": None,
        "color": "dark blue",
        "distinguishingMarks": [],
        "approximateTime": None,
        "location": "near the entrance",
    }
)

_VALID_VERIFY_BODY = {
    "lost": {"description": "a blue umbrella from the cloakroom"},
    "found": {"description": "umbrella, blue, near the cloakroom"},
    "language": "en",
}
_VALID_VERIFY_OUTPUT = json.dumps(
    {
        "verdict": "uncertain",
        "confidence": 0.5,
        "rationale": "Both mention a blue umbrella, but the detail is thin.",
    }
)


# --- /metrics endpoint -----------------------------------------------------


def test_metrics_endpoint_returns_prometheus_payload(client_with_fake):
    response = client_with_fake.get("/metrics")

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/plain")
    body = response.text
    assert "genai_provider_requests_total" in body
    assert "genai_provider_request_duration_seconds" in body
    assert "genai_validation_errors_total" in body
    assert "genai_build_info" in body


def test_metrics_endpoint_excluded_from_openapi(client_with_fake):
    schema = client_with_fake.get("/openapi.json").json()
    assert "/metrics" not in schema.get("paths", {})


# --- HTTP-level metrics from prometheus-fastapi-instrumentator ------------


def test_http_request_counter_increments_on_extract(client_with_fake, fake_provider):
    fake_provider._chat_response = _VALID_EXTRACT_OUTPUT
    labels = {"handler": "/extract-attributes", "method": "POST", "status": "2xx"}
    before = _sample("http_requests_total", labels)

    response = client_with_fake.post("/extract-attributes", json=_VALID_EXTRACT_BODY)

    assert response.status_code == 200
    assert _sample("http_requests_total", labels) - before == 1.0


# --- genai_provider_requests_total ----------------------------------------


def test_provider_counter_ok_on_extract_success(client_with_fake, fake_provider):
    fake_provider._chat_response = _VALID_EXTRACT_OUTPUT
    labels = {"provider": "fake", "endpoint": "extract-attributes", "outcome": "ok"}
    before = _sample("genai_provider_requests_total", labels)

    response = client_with_fake.post("/extract-attributes", json=_VALID_EXTRACT_BODY)

    assert response.status_code == 200
    assert _sample("genai_provider_requests_total", labels) - before == 1.0


def test_provider_counter_timeout(client_with_fake, fake_provider):
    fake_provider._raise_on_chat = LLMTimeoutError("upstream slow")
    labels = {
        "provider": "fake",
        "endpoint": "extract-attributes",
        "outcome": "timeout",
    }
    before = _sample("genai_provider_requests_total", labels)

    response = client_with_fake.post("/extract-attributes", json=_VALID_EXTRACT_BODY)

    assert response.status_code == 504
    assert _sample("genai_provider_requests_total", labels) - before == 1.0


def test_provider_counter_rate_limit(client_with_fake, fake_provider):
    fake_provider._raise_on_chat = LLMRateLimitError("slow down")
    labels = {
        "provider": "fake",
        "endpoint": "extract-attributes",
        "outcome": "rate_limit",
    }
    before = _sample("genai_provider_requests_total", labels)

    response = client_with_fake.post("/extract-attributes", json=_VALID_EXTRACT_BODY)

    assert response.status_code == 429
    assert _sample("genai_provider_requests_total", labels) - before == 1.0


def test_provider_counter_distinguishes_endpoints(
    client_with_fake, fake_provider
):
    fake_provider._chat_response = _VALID_EXTRACT_OUTPUT
    extract_labels = {
        "provider": "fake",
        "endpoint": "extract-attributes",
        "outcome": "ok",
    }
    verify_labels = {
        "provider": "fake",
        "endpoint": "verify-match",
        "outcome": "ok",
    }
    extract_before = _sample("genai_provider_requests_total", extract_labels)
    verify_before = _sample("genai_provider_requests_total", verify_labels)

    client_with_fake.post("/extract-attributes", json=_VALID_EXTRACT_BODY)
    fake_provider._chat_response = _VALID_VERIFY_OUTPUT
    client_with_fake.post("/verify-match", json=_VALID_VERIFY_BODY)

    assert (
        _sample("genai_provider_requests_total", extract_labels) - extract_before == 1.0
    )
    assert (
        _sample("genai_provider_requests_total", verify_labels) - verify_before == 1.0
    )


# --- genai_provider_request_duration_seconds ------------------------------


def test_provider_duration_observed_on_success(client_with_fake, fake_provider):
    fake_provider._chat_response = _VALID_EXTRACT_OUTPUT
    labels = {"provider": "fake", "endpoint": "extract-attributes"}
    before = _sample("genai_provider_request_duration_seconds_count", labels)

    client_with_fake.post("/extract-attributes", json=_VALID_EXTRACT_BODY)

    assert (
        _sample("genai_provider_request_duration_seconds_count", labels) - before
        == 1.0
    )


def test_provider_duration_observed_on_failure(client_with_fake, fake_provider):
    fake_provider._raise_on_chat = LLMTimeoutError("upstream slow")
    labels = {"provider": "fake", "endpoint": "extract-attributes"}
    before = _sample("genai_provider_request_duration_seconds_count", labels)

    client_with_fake.post("/extract-attributes", json=_VALID_EXTRACT_BODY)

    assert (
        _sample("genai_provider_request_duration_seconds_count", labels) - before
        == 1.0
    )


# --- genai_validation_errors_total ----------------------------------------


def test_validation_errors_json_decode(client_with_fake, fake_provider):
    fake_provider._chat_response = "not valid json"
    labels = {"endpoint": "extract-attributes", "reason": "json_decode"}
    before = _sample("genai_validation_errors_total", labels)

    response = client_with_fake.post("/extract-attributes", json=_VALID_EXTRACT_BODY)

    assert response.status_code == 422
    assert _sample("genai_validation_errors_total", labels) - before == 1.0


def test_validation_errors_wrong_type(client_with_fake, fake_provider):
    fake_provider._chat_response = '"a JSON string, not an object"'
    labels = {"endpoint": "extract-attributes", "reason": "wrong_type"}
    before = _sample("genai_validation_errors_total", labels)

    response = client_with_fake.post("/extract-attributes", json=_VALID_EXTRACT_BODY)

    assert response.status_code == 422
    assert _sample("genai_validation_errors_total", labels) - before == 1.0


def test_validation_errors_schema(client_with_fake, fake_provider):
    # `distinguishingMarks` must be a JSON array; a string fails the
    # list[str] type check on `ItemAttributes` and lands on the schema
    # branch. (null is benignly coerced to [] by a validator.)
    fake_provider._chat_response = (
        '{"category":"umbrella","brand":null,"color":"blue",'
        '"distinguishingMarks":"not a list",'
        '"approximateTime":null,"location":null}'
    )
    labels = {"endpoint": "extract-attributes", "reason": "schema"}
    before = _sample("genai_validation_errors_total", labels)

    response = client_with_fake.post("/extract-attributes", json=_VALID_EXTRACT_BODY)

    assert response.status_code == 422
    assert _sample("genai_validation_errors_total", labels) - before == 1.0


def test_validation_failure_does_not_bump_provider_error_counter(
    client_with_fake, fake_provider
):
    """A parse failure must not be counted as a provider failure.

    The provider call succeeded — the response just wasn't usable — so
    `genai_provider_requests_total{outcome="ok"}` ticks and
    `{outcome="error"}` does not. The dedicated validation counter takes
    care of surfacing the parse failure.
    """
    fake_provider._chat_response = "not valid json"
    provider_ok = {
        "provider": "fake",
        "endpoint": "extract-attributes",
        "outcome": "ok",
    }
    provider_err = {
        "provider": "fake",
        "endpoint": "extract-attributes",
        "outcome": "error",
    }
    validation = {"endpoint": "extract-attributes", "reason": "json_decode"}

    ok_before = _sample("genai_provider_requests_total", provider_ok)
    err_before = _sample("genai_provider_requests_total", provider_err)
    val_before = _sample("genai_validation_errors_total", validation)

    client_with_fake.post("/extract-attributes", json=_VALID_EXTRACT_BODY)

    assert _sample("genai_provider_requests_total", provider_ok) - ok_before == 1.0
    assert _sample("genai_provider_requests_total", provider_err) - err_before == 0.0
    assert _sample("genai_validation_errors_total", validation) - val_before == 1.0


# --- genai_build_info -----------------------------------------------------


def test_build_info_labels_match_settings_provider(client_with_fake):
    # `client_with_fake` sets GENAI_PROVIDER=local before lifespan runs,
    # so build_info gets stamped with provider="local".
    value = _sample("genai_build_info", {"provider": "local"})
    assert value == 1.0


# --- /embed and /verify-match are also instrumented ----------------------


def test_embed_endpoint_increments_provider_counter(client_with_fake, fake_provider):
    fake_provider._embed_vector = [0.1, 0.2, 0.3]
    labels = {"provider": "fake", "endpoint": "embed", "outcome": "ok"}
    before = _sample("genai_provider_requests_total", labels)

    response = client_with_fake.post(
        "/embed", json={"texts": ["a wallet"], "purpose": "lost_report"}
    )

    assert response.status_code == 200
    assert _sample("genai_provider_requests_total", labels) - before == 1.0


def test_embed_records_one_observation_per_text(client_with_fake, fake_provider):
    fake_provider._embed_vector = [0.1, 0.2, 0.3]
    labels = {"provider": "fake", "endpoint": "embed", "outcome": "ok"}
    before = _sample("genai_provider_requests_total", labels)

    client_with_fake.post(
        "/embed", json={"texts": ["a", "b", "c"], "purpose": "lost_report"}
    )

    # Three texts → three provider calls → three counter increments.
    assert _sample("genai_provider_requests_total", labels) - before == 3.0


def test_verify_match_endpoint_increments_provider_counter(
    client_with_fake, fake_provider
):
    fake_provider._chat_response = _VALID_VERIFY_OUTPUT
    labels = {"provider": "fake", "endpoint": "verify-match", "outcome": "ok"}
    before = _sample("genai_provider_requests_total", labels)

    response = client_with_fake.post("/verify-match", json=_VALID_VERIFY_BODY)

    assert response.status_code == 200
    assert _sample("genai_provider_requests_total", labels) - before == 1.0
