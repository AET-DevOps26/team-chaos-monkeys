"""Route-level tests for `/extract-attributes` image input (#90).

Covers:
  - The at-least-one cross-field validator (400 VALIDATION_ERROR with
    `details.reason="at_least_one_required"`).
  - Image-only and both-modes happy paths through the route +
    `prepare_image` + extraction.
  - Image-shape failure modes mapping to 400 VALIDATION_ERROR with the
    documented `details.reason` discriminator.
  - The 8 MiB HTTP body cap → 413 PAYLOAD_TOO_LARGE.
  - The `modality` label being propagated onto the GenAI-domain metrics.

Test images are synthesised in memory with Pillow so this file has no
external fixtures. Provider-side image content is forwarded as a
`FakeProvider.chat_response` JSON string; the FakeProvider sees the
multimodal message structure but does not actually look at the bytes,
which is the right level of coupling for unit tests.
"""

from __future__ import annotations

import base64
import io
import json

from PIL import Image
from prometheus_client import REGISTRY


def _b64_jpeg(size: tuple[int, int] = (200, 200)) -> str:
    img = Image.new("RGB", size, (128, 64, 200))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return base64.b64encode(buf.getvalue()).decode("ascii")


_VALID_OUTPUT = json.dumps(
    {
        "category": "ACCESSORIES",
        "description": "brown leather wallet",
        "brand": None,
        "color": "brown",
        "distinguishingMarks": ["small scratch on the corner"],
        "approximateTime": None,
        "location": None,
    }
)


def _sample(name: str, labels: dict[str, str]) -> float:
    value = REGISTRY.get_sample_value(name, labels)
    return value if value is not None else 0.0


# --- At-least-one validator -----------------------------------------------


def test_request_without_description_or_image_400s(client_with_fake):
    response = client_with_fake.post("/extract-attributes", json={})
    assert response.status_code == 400
    body = response.json()
    assert body["code"] == "VALIDATION_ERROR"
    assert body["details"]["reason"] == "at_least_one_required"


def test_request_with_only_language_400s(client_with_fake):
    # `language` alone doesn't satisfy at-least-one — it's a hint, not content.
    response = client_with_fake.post(
        "/extract-attributes", json={"language": "en"}
    )
    assert response.status_code == 400
    assert response.json()["details"]["reason"] == "at_least_one_required"


# --- Image-only and both-modes happy paths --------------------------------


def test_image_only_request_succeeds(client_with_fake, fake_provider):
    fake_provider._chat_response = _VALID_OUTPUT
    payload = {
        "image": {"contentType": "image/jpeg", "dataBase64": _b64_jpeg()}
    }
    response = client_with_fake.post("/extract-attributes", json=payload)
    assert response.status_code == 200
    body = response.json()
    assert body["attributes"]["category"] == "ACCESSORIES"
    # The multimodal user turn made it through to the provider as a
    # content-part list — see ADR 0001 §6.
    messages, _ = fake_provider.chat_calls[-1]
    user_content = messages[-1]["content"]
    assert isinstance(user_content, list)
    assert any(part["type"] == "image" for part in user_content)


def test_both_modalities_request_succeeds(client_with_fake, fake_provider):
    fake_provider._chat_response = _VALID_OUTPUT
    payload = {
        "description": "small brown leather wallet",
        "image": {"contentType": "image/jpeg", "dataBase64": _b64_jpeg()},
    }
    response = client_with_fake.post("/extract-attributes", json=payload)
    assert response.status_code == 200
    messages, _ = fake_provider.chat_calls[-1]
    user_content = messages[-1]["content"]
    # Both parts (text instruction + image) end up in the user turn.
    assert isinstance(user_content, list)
    assert sum(1 for p in user_content if p["type"] == "text") == 1
    assert sum(1 for p in user_content if p["type"] == "image") == 1


# --- Image-shape failures -------------------------------------------------


def test_invalid_base64_400s_with_reason(client_with_fake):
    payload = {
        "image": {"contentType": "image/jpeg", "dataBase64": "!!!not base64!!!"}
    }
    response = client_with_fake.post("/extract-attributes", json=payload)
    assert response.status_code == 400
    assert response.json()["details"]["reason"] == "image_base64_invalid"


def test_unsupported_mime_400s_at_pydantic_layer(client_with_fake):
    """The Pydantic Literal on `contentType` rejects HEIC before the
    route handler runs, surfaced as a VALIDATION_ERROR by FastAPI's
    handler. The `details.reason` is the Pydantic error message rather
    than `image_mime_unsupported` — that reason is reserved for the
    in-route check inside `prepare_image` (defence-in-depth).
    """
    payload = {
        "image": {"contentType": "image/heic", "dataBase64": _b64_jpeg()}
    }
    response = client_with_fake.post("/extract-attributes", json=payload)
    assert response.status_code == 400
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_corrupt_image_bytes_400s_with_reason(client_with_fake):
    not_an_image = base64.b64encode(b"this is not image bytes at all").decode(
        "ascii"
    )
    payload = {
        "image": {"contentType": "image/jpeg", "dataBase64": not_an_image}
    }
    response = client_with_fake.post("/extract-attributes", json=payload)
    assert response.status_code == 400
    assert response.json()["details"]["reason"] == "image_decode_failed"


# --- Body size cap (middleware) -------------------------------------------


def test_oversized_body_413s(client_with_fake):
    # 10 MiB body is well above the 8 MiB middleware cap. We don't even
    # need it to be valid JSON; the middleware rejects on Content-Length.
    big_body = b'{"description":"' + b"x" * (10 * 1024 * 1024) + b'"}'
    response = client_with_fake.post(
        "/extract-attributes",
        content=big_body,
        headers={"content-type": "application/json"},
    )
    assert response.status_code == 413
    body = response.json()
    assert body["code"] == "PAYLOAD_TOO_LARGE"
    assert body["details"]["maxBytes"] == 8 * 1024 * 1024


# --- Modality label propagation ------------------------------------------


def test_image_request_increments_image_modality_counter(
    client_with_fake, fake_provider
):
    fake_provider._chat_response = _VALID_OUTPUT
    labels = {
        "provider": "fake",
        "endpoint": "extract-attributes",
        "outcome": "ok",
        "modality": "image",
    }
    before = _sample("genai_provider_requests_total", labels)
    payload = {
        "image": {"contentType": "image/jpeg", "dataBase64": _b64_jpeg()}
    }
    response = client_with_fake.post("/extract-attributes", json=payload)
    assert response.status_code == 200
    assert _sample("genai_provider_requests_total", labels) - before == 1.0


def test_both_modality_request_increments_both_modality_counter(
    client_with_fake, fake_provider
):
    fake_provider._chat_response = _VALID_OUTPUT
    labels = {
        "provider": "fake",
        "endpoint": "extract-attributes",
        "outcome": "ok",
        "modality": "both",
    }
    before = _sample("genai_provider_requests_total", labels)
    payload = {
        "description": "brown leather wallet",
        "image": {"contentType": "image/jpeg", "dataBase64": _b64_jpeg()},
    }
    response = client_with_fake.post("/extract-attributes", json=payload)
    assert response.status_code == 200
    assert _sample("genai_provider_requests_total", labels) - before == 1.0
