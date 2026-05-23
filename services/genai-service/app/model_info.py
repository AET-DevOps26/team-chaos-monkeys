"""`Settings` → `ModelInfo` mapping shared across all GenAI endpoints.

Previously lived in `app.extraction` (issue #49) and was reached into by
`/embed` (#50) and `/verify-match` (#104). Lifted into its own module
(#94) so non-extraction callers don't import from extraction for
something that is not extraction-specific.
"""

from __future__ import annotations

from typing import Literal

from app.api.schemas import ModelInfo
from app.config import Settings


def resolve_model_info(
    settings: Settings, kind: Literal["chat", "embed", "vision"] = "chat"
) -> ModelInfo:
    """The provider and the model that served a request, for `ModelInfo`.

    `kind` selects the model family:
      - `"chat"`   — `/extract-attributes` (text-only) and `/verify-match`
      - `"embed"`  — `/embed`
      - `"vision"` — `/extract-attributes` with image content (modality
        `image` or `both`). For OpenAI this is still the chat model
        (`gpt-4o-mini` is vision-capable by default); for Ollama, the
        adapter routes vision calls to `OLLAMA_VISION_MODEL` and we
        report that here for honest provenance (see ADR 0001 §7).
    """
    if settings.provider == "openai":
        if kind == "embed":
            model = settings.openai_embed_model
        else:
            # OpenAI's default chat model is vision-capable; the "vision"
            # kind reports the same string.
            model = settings.openai_chat_model
    else:
        if kind == "embed":
            model = settings.ollama_embed_model
        elif kind == "vision":
            model = settings.ollama_vision_model
        else:
            model = settings.ollama_chat_model
    return ModelInfo(provider=settings.provider, model=model)
