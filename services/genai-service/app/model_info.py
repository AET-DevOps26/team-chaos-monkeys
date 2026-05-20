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
    settings: Settings, kind: Literal["chat", "embed"] = "chat"
) -> ModelInfo:
    """The provider and the model that served a request, for `ModelInfo`.

    `kind` selects the model family: `/extract-attributes` and
    `/verify-match` run on the chat model, `/embed` on the embed model.
    Defaults to `"chat"` so existing callers are unaffected.
    """
    if settings.provider == "openai":
        chat_model = settings.openai_chat_model
        embed_model = settings.openai_embed_model
    else:
        chat_model = settings.ollama_chat_model
        embed_model = settings.ollama_embed_model
    model = chat_model if kind == "chat" else embed_model
    return ModelInfo(provider=settings.provider, model=model)
