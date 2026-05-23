"""Image processing pipeline for `/extract-attributes` image input.

Decode → validate → downscale → strip EXIF → re-encode. Every image that
reaches the LLM provider has passed through this pipeline, so:
  - cost is bounded (the OpenAI vision call sees at most a 1024 × 1024
    image, capping its per-call token spend at a small constant);
  - EXIF metadata (GPS, device info, timestamps) is dropped before the
    bytes leave our process;
  - a decompression-bomb cap prevents a small payload from allocating
    gigabytes of RAM during decode.

Errors raise `ImageProcessingError` with a `reason` discriminator that
`app.errors` maps onto the contract's `details.reason` values
(`image_base64_invalid`, `image_mime_unsupported`, `image_too_large`,
`image_decode_failed`).

See ADR 0001 §8 in `services/genai-service/docs/adr/` for the design.
"""

from __future__ import annotations

import base64
import binascii
import io

from PIL import Image, UnidentifiedImageError

from app.api.schemas import ALLOWED_IMAGE_MIME_TYPES, ImageContent
from app.exceptions import ImageProcessingError
from app.providers import ImageContentPart

# Decompression-bomb defence. Pillow raises `Image.DecompressionBombError`
# when an image's declared dimensions exceed this product — preventing a
# small malicious file (e.g. a tiny PNG declaring 50000 × 50000 pixels)
# from forcing a multi-gigabyte allocation during decode.
Image.MAX_IMAGE_PIXELS = 4096 * 4096

# Cap on the decoded byte size. Sized at 5 MiB to cover typical phone
# photos with margin; matches the OpenAPI `maxLength` on `dataBase64`
# (≈ 6.7 M chars of base64 → ~5 MiB raw).
MAX_DECODED_BYTES = 5 * 1024 * 1024

# Downscale target — longest edge in pixels. Caps the OpenAI vision
# call at four 512×512 tiles per image (the boundary at which the
# provider charges the next tier). Llava internally downsamples to
# ~672 px anyway, so we don't pay quality for the cap.
MAX_DIMENSION = 1024

# Quality knob for the JPEG re-encode. 85 is the standard "good for the
# web" setting — visually indistinguishable from the original at typical
# phone-photo sizes and ~4× smaller than quality 95.
JPEG_QUALITY = 85


def prepare_image(content: ImageContent) -> ImageContentPart:
    """Run the full pipeline and return a provider-ready content part.

    The returned `ImageContentPart` is always `image/jpeg` regardless of
    the input format — re-encoding is a deliberate normalisation step
    that also drops EXIF metadata (Pillow does not preserve EXIF on
    `Image.save(buf, "JPEG")` unless explicitly told to).

    Raises `ImageProcessingError` for every failure mode declared on the
    contract's `VALIDATION_ERROR` reason set.
    """
    if content.content_type not in ALLOWED_IMAGE_MIME_TYPES:
        raise ImageProcessingError(
            f"unsupported image content type: {content.content_type!r}",
            reason="image_mime_unsupported",
            details={
                "field": "image.contentType",
                "contentType": content.content_type,
                "allowed": list(ALLOWED_IMAGE_MIME_TYPES),
            },
        )

    try:
        raw = base64.b64decode(content.data_base64, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ImageProcessingError(
            "image base64 data did not decode",
            reason="image_base64_invalid",
            details={"field": "image.dataBase64"},
        ) from exc

    if len(raw) > MAX_DECODED_BYTES:
        raise ImageProcessingError(
            f"image exceeds maximum size of {MAX_DECODED_BYTES} bytes",
            reason="image_too_large",
            details={
                "field": "image.dataBase64",
                "actualSizeBytes": len(raw),
                "maxSizeBytes": MAX_DECODED_BYTES,
            },
        )

    try:
        processed_bytes = _decode_and_normalise(raw)
    except Image.DecompressionBombError as exc:
        raise ImageProcessingError(
            "image decode rejected by decompression-bomb defence",
            reason="image_decode_failed",
            details={"field": "image.dataBase64"},
        ) from exc
    except UnidentifiedImageError as exc:
        raise ImageProcessingError(
            "image bytes could not be opened",
            reason="image_decode_failed",
            details={"field": "image.dataBase64"},
        ) from exc
    except (OSError, ValueError) as exc:
        raise ImageProcessingError(
            f"image processing failed: {exc}",
            reason="image_decode_failed",
            details={"field": "image.dataBase64"},
        ) from exc

    return ImageContentPart(
        type="image",
        contentType="image/jpeg",
        dataBase64=base64.b64encode(processed_bytes).decode("ascii"),
    )


def _decode_and_normalise(raw: bytes) -> bytes:
    """Open, downscale, drop alpha, re-encode JPEG. Returns the new bytes.

    Caller catches `Image.DecompressionBombError` / `UnidentifiedImageError`
    / `OSError` / `ValueError` and maps them to `image_decode_failed`.
    """
    with Image.open(io.BytesIO(raw)) as img:
        # Force decode now so the bomb check actually fires on bad input.
        # Without this, lazy decode could defer the failure to `resize`.
        img.load()

        # JPEG output cannot carry alpha. RGBA / P / LA / etc. → RGB.
        # Grayscale (L) is kept as-is — Pillow encodes it as JPEG fine.
        if img.mode not in ("RGB", "L"):
            img = img.convert("RGB")

        longest_edge = max(img.size)
        if longest_edge > MAX_DIMENSION:
            scale = MAX_DIMENSION / longest_edge
            new_size = (
                max(1, int(img.size[0] * scale)),
                max(1, int(img.size[1] * scale)),
            )
            img = img.resize(new_size, Image.Resampling.LANCZOS)

        # Saving to a fresh buffer drops EXIF unless we explicitly carry
        # it forward — which we don't. `optimize=True` produces a smaller
        # file at the cost of a few extra ms per encode.
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=JPEG_QUALITY, optimize=True)
        return buf.getvalue()
