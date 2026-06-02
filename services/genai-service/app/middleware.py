"""FastAPI middleware for request body size enforcement.

The image-extraction path accepts base64-encoded image bytes in the JSON
body (ADR 0001 §2). Even with the 5 MiB decoded-bytes cap enforced by
the image pipeline, a hostile caller could POST gigabytes of base64
garbage and waste process memory just to parse-and-reject it.

`MaxBodySizeMiddleware` rejects oversized requests at the ASGI layer
before Pydantic ever sees them. The check is by `Content-Length` header,
which is reliable for every non-streaming HTTP client we expect to talk
to this service (Spring `RestClient`, browser `fetch`, curl).

The 413 response uses the contract `ErrorResponse` shape so callers see
the same error envelope they get from the rest of the API.
"""

from __future__ import annotations

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse, Response

# 8 MiB cap. Sized to admit a 5 MiB raw image base64-encoded (~6.7 MiB)
# plus the surrounding JSON envelope with margin. See ADR 0001 §2 / §8.
MAX_REQUEST_BODY_BYTES = 8 * 1024 * 1024


class MaxBodySizeMiddleware(BaseHTTPMiddleware):
    def __init__(self, app, max_bytes: int = MAX_REQUEST_BODY_BYTES) -> None:
        super().__init__(app)
        self._max_bytes = max_bytes

    async def dispatch(self, request: Request, call_next) -> Response:
        content_length = request.headers.get("content-length")
        if content_length is not None:
            try:
                size = int(content_length)
            except ValueError:
                size = None
            if size is not None and size > self._max_bytes:
                return JSONResponse(
                    status_code=413,
                    content={
                        "code": "PAYLOAD_TOO_LARGE",
                        "message": (
                            f"Request body exceeds the {self._max_bytes} byte limit"
                        ),
                        "details": {
                            "maxBytes": self._max_bytes,
                            "actualBytes": size,
                        },
                    },
                )
        return await call_next(request)
