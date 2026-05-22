"""Unit tests for the image processing pipeline (#90).

The pipeline is responsible for:
  - Decoding the base64 payload
  - Enforcing the MIME allowlist (JPEG / PNG / WebP only)
  - Enforcing the 5 MiB decoded-bytes cap
  - Defending against decompression bombs
  - Downscaling to ≤ 1024 px on the longest edge
  - Stripping EXIF metadata
  - Re-encoding to JPEG

Test images are synthesised in memory with Pillow so the tests run
without external fixtures. The golden set (issue #55 extension under
this branch) covers real photo cases against the LLM provider — those
tests live in `tests/golden/`.
"""

from __future__ import annotations

import base64
import io
from typing import Literal

import pytest
from PIL import Image
from PIL.ExifTags import Base as ExifBase

from app.api.schemas import ImageContent
from app.exceptions import ImageProcessingError
from app.image import (
    JPEG_QUALITY,
    MAX_DECODED_BYTES,
    MAX_DIMENSION,
    prepare_image,
)


def _encode_test_image(
    *,
    size: tuple[int, int] = (200, 200),
    color: tuple[int, int, int] = (128, 64, 200),
    fmt: Literal["JPEG", "PNG", "WEBP"] = "JPEG",
    with_exif: bool = False,
) -> bytes:
    """Produce a small in-memory image of the requested format."""
    img = Image.new("RGB", size, color)
    buf = io.BytesIO()
    if with_exif and fmt == "JPEG":
        # 0x0112 = Orientation tag; a minimal real EXIF block makes the
        # "EXIF stripped" assertion meaningful — Pillow only writes EXIF
        # back out when asked to.
        exif = img.getexif()
        exif[ExifBase.Orientation.value] = 1
        img.save(buf, format=fmt, exif=exif.tobytes())
    else:
        img.save(buf, format=fmt)
    return buf.getvalue()


def _b64(raw: bytes) -> str:
    return base64.b64encode(raw).decode("ascii")


def _make(
    raw: bytes, *, content_type: str = "image/jpeg"
) -> ImageContent:
    return ImageContent(content_type=content_type, data_base64=_b64(raw))


# --- Happy paths ----------------------------------------------------------


def test_prepare_image_returns_jpeg_content_part():
    raw = _encode_test_image()
    part = prepare_image(_make(raw))
    assert part["type"] == "image"
    assert part["contentType"] == "image/jpeg"
    assert part["dataBase64"]


def test_prepare_image_accepts_png():
    raw = _encode_test_image(fmt="PNG")
    part = prepare_image(_make(raw, content_type="image/png"))
    # Re-encoded to JPEG regardless of input format.
    assert part["contentType"] == "image/jpeg"


def test_prepare_image_accepts_webp():
    raw = _encode_test_image(fmt="WEBP")
    part = prepare_image(_make(raw, content_type="image/webp"))
    assert part["contentType"] == "image/jpeg"


def test_prepare_image_downscales_oversized_input():
    raw = _encode_test_image(size=(2048, 1024))
    part = prepare_image(_make(raw))
    out_bytes = base64.b64decode(part["dataBase64"])
    with Image.open(io.BytesIO(out_bytes)) as out:
        assert max(out.size) <= MAX_DIMENSION
        # Aspect ratio preserved within a small rounding margin.
        assert abs(out.size[0] / out.size[1] - 2.0) < 0.05


def test_prepare_image_does_not_upscale_small_input():
    raw = _encode_test_image(size=(100, 50))
    part = prepare_image(_make(raw))
    out_bytes = base64.b64decode(part["dataBase64"])
    with Image.open(io.BytesIO(out_bytes)) as out:
        assert out.size == (100, 50)


def test_prepare_image_strips_exif():
    raw = _encode_test_image(with_exif=True)
    # Sanity check: input does carry EXIF (otherwise the test asserts nothing).
    with Image.open(io.BytesIO(raw)) as inp:
        assert inp.getexif()
    part = prepare_image(_make(raw))
    out_bytes = base64.b64decode(part["dataBase64"])
    with Image.open(io.BytesIO(out_bytes)) as out:
        assert not out.getexif(), "EXIF must be stripped before forwarding"


def test_prepare_image_converts_alpha_to_rgb():
    img = Image.new("RGBA", (100, 100), (10, 20, 30, 128))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    part = prepare_image(_make(buf.getvalue(), content_type="image/png"))
    out_bytes = base64.b64decode(part["dataBase64"])
    with Image.open(io.BytesIO(out_bytes)) as out:
        assert out.mode in ("RGB", "L")


# --- MIME allowlist -------------------------------------------------------


def test_prepare_image_rejects_unsupported_mime():
    """The Pydantic `Literal` on `content_type` is the primary gate; this
    asserts the redundant defence-in-depth check inside `prepare_image`
    by bypassing validation with `model_construct`."""
    raw = _encode_test_image(fmt="PNG")
    bypass = ImageContent.model_construct(
        content_type="image/heic", data_base64=_b64(raw)
    )
    with pytest.raises(ImageProcessingError) as exc_info:
        prepare_image(bypass)
    assert exc_info.value.reason == "image_mime_unsupported"
    assert exc_info.value.details["contentType"] == "image/heic"


# --- Base64 + size --------------------------------------------------------


def test_prepare_image_rejects_invalid_base64(monkeypatch):
    # ImageContent's Pydantic validator allows any non-empty string within
    # length bounds; bad base64 is caught at decode time inside prepare_image.
    bad = ImageContent(content_type="image/jpeg", data_base64="!!!not_base64!!!")
    with pytest.raises(ImageProcessingError) as exc_info:
        prepare_image(bad)
    assert exc_info.value.reason == "image_base64_invalid"


def test_prepare_image_rejects_oversized_bytes():
    """Pydantic's `maxLength` on `dataBase64` is the primary gate; this
    asserts the defence-in-depth size check inside `prepare_image` by
    bypassing validation with `model_construct`."""
    payload = bytes((i * 17) % 256 for i in range(MAX_DECODED_BYTES + 1))
    bypass = ImageContent.model_construct(
        content_type="image/jpeg", data_base64=_b64(payload)
    )
    with pytest.raises(ImageProcessingError) as exc_info:
        prepare_image(bypass)
    assert exc_info.value.reason == "image_too_large"
    assert exc_info.value.details["actualSizeBytes"] == len(payload)


def test_prepare_image_rejects_corrupt_bytes():
    # Bytes that base64-decode fine but Pillow won't open as an image.
    payload = b"definitely not an image, just some text bytes"
    with pytest.raises(ImageProcessingError) as exc_info:
        prepare_image(_make(payload))
    assert exc_info.value.reason == "image_decode_failed"


# --- Decompression bomb defence ------------------------------------------


def test_prepare_image_decompression_bomb_defence():
    """Pillow raises `DecompressionBombError` when an image's declared
    dimensions exceed `2 * MAX_IMAGE_PIXELS` regardless of warning
    filters. With `MAX_IMAGE_PIXELS = 4096²`, the threshold is ~33 M
    pixels — an 8192×8192 image trips it for certain.
    """
    big_img = Image.new("RGB", (8192, 8192), (255, 255, 255))
    buf = io.BytesIO()
    big_img.save(buf, format="PNG", optimize=False)
    content = _make(buf.getvalue(), content_type="image/png")
    with pytest.raises(ImageProcessingError) as exc_info:
        prepare_image(content)
    assert exc_info.value.reason == "image_decode_failed"


# --- Re-encode parameters --------------------------------------------------


def test_prepare_image_emits_compact_jpeg():
    # A 1024×1024 photo at quality 85 should weigh well under the 5 MiB
    # cap; this sanity-checks that the re-encode actually compresses.
    raw = _encode_test_image(size=(1024, 1024), color=(120, 80, 40))
    part = prepare_image(_make(raw))
    out_bytes = base64.b64decode(part["dataBase64"])
    assert len(out_bytes) < 500_000  # noticeably smaller than 5 MiB cap


def test_jpeg_quality_constant():
    # Guards against an accidental quality bump that would balloon the
    # forwarded payload and the OpenAI vision-token bill.
    assert JPEG_QUALITY == 85
