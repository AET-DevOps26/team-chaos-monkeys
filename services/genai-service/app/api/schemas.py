"""Pydantic models for the GenAI HTTP contract.

These mirror `components.schemas` of the repo-root OpenAPI contract
(`api/openapi.yaml`), the single source of truth for the sync API. The
wire format is camelCase; `CamelModel` maps between snake_case Python
attributes and camelCase JSON so the rest of the service stays Pythonic.

This revision covers `/extract-attributes` (#49), `/embed` (#50), and
`/verify-match`.
"""

from __future__ import annotations

from enum import StrEnum
from typing import Annotated, Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator
from pydantic.alias_generators import to_camel

# Allowed MIME types for `ImageContent.contentType`. Kept in sync with the
# OpenAPI enum and the `image_mime_unsupported` validation reason in
# `app.errors`. HEIC/HEIF and other formats are intentionally absent —
# callers convert upstream (e.g. in the photo-storage layer).
ALLOWED_IMAGE_MIME_TYPES: tuple[str, ...] = (
    "image/jpeg",
    "image/png",
    "image/webp",
)

# Cap on the base64-encoded `dataBase64` string. Sized to admit ≈ 5 MiB of
# decoded payload (5 MiB × 4/3 ≈ 6.7 M chars). The decoded byte count is
# re-checked in `app.image` after base64 decoding.
MAX_IMAGE_BASE64_LENGTH = 6_700_000


class CamelModel(BaseModel):
    """Base model: camelCase JSON aliases over snake_case attributes.

    `populate_by_name` keeps construction by Python name working (tests,
    and accepting either casing when parsing LLM output). `protected_namespaces`
    is cleared so a `modelInfo` field does not trip Pydantic's `model_` guard.
    """

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        protected_namespaces=(),
    )


class Category(StrEnum):
    """Controlled vocabulary for an item's coarse category.

    matching-service gates candidate pairs on exact category equality, so a
    fixed taxonomy — rather than free text — keeps near-synonyms ("shirt" vs
    "tshirt") from zeroing out an otherwise-strong semantic match. Free-text
    model output is coerced onto this set (see `_coerce_category`); anything
    off-list becomes `OTHER` so a stray label never fails extraction.
    """

    ELECTRONICS = "ELECTRONICS"
    CLOTHING = "CLOTHING"
    ACCESSORIES = "ACCESSORIES"
    BAGS = "BAGS"
    DOCUMENTS = "DOCUMENTS"
    KEYS = "KEYS"
    JEWELRY = "JEWELRY"
    OTHER = "OTHER"


class ItemAttributes(CamelModel):
    """Structured attributes of a lost item — the `ItemAttributes` schema.

    Fields the model cannot determine are `null`; `distinguishingMarks` is
    always an array (empty, never null). Missing keys in LLM output fall back
    to these defaults, so a benign omission is not treated as invalid output —
    only malformed JSON or wrong-typed values fail validation (-> 422).
    """

    category: Category | None = None
    description: str | None = None
    brand: str | None = None
    color: str | None = None
    distinguishing_marks: list[str] = Field(default_factory=list)
    approximate_time: str | None = None
    location: str | None = None

    @field_validator("distinguishing_marks", mode="before")
    @classmethod
    def _empty_marks_for_null(cls, value: object) -> object:
        """A `null` marks value means "no marks" — coerce it to [].

        Models commonly emit `null` instead of `[]`; that is unambiguously
        recoverable, so it should not fail validation. Wrong-typed values
        (a string, non-string items) still fall through to a 422.
        """
        return [] if value is None else value

    @field_validator(
        "description", "brand", "color", "approximate_time", "location", mode="before"
    )
    @classmethod
    def _nullish_string_to_null(cls, value: object) -> object:
        """Normalise "not determined" scalars to None.

        The contract returns undeterminable fields as `null`. Smaller models
        often emit a blank string, or the literal word "null"/"none", instead
        of a JSON null — coerce those so they do not slip through as values.
        """
        if isinstance(value, str) and value.strip().casefold() in {"", "null", "none"}:
            return None
        return value

    @field_validator("category", mode="before")
    @classmethod
    def _coerce_category(cls, value: object) -> object:
        """Map free-text model output onto the fixed `Category` taxonomy.

        Blank / "null" / "none" → None; a recognised value (any case) → that
        member; any other non-empty string → `OTHER`, so an off-list label is
        absorbed rather than failing extraction. Non-strings fall through to
        normal validation (a list/number still 422s).
        """
        if not isinstance(value, str):
            return value
        text = value.strip()
        if text.casefold() in {"", "null", "none"}:
            return None
        try:
            return Category(text.upper())
        except ValueError:
            return Category.OTHER


class ModelInfo(CamelModel):
    """Which provider/model produced a result — the `ModelInfo` schema."""

    # `fake` is the in-process provider used by CI E2E and downstream
    # Spring integration tests (issue #128) — it never makes a network
    # call and returns canned JSON. Callers that care about provenance
    # treat `fake` like `local` for routing/observability purposes.
    provider: Literal["openai", "local", "fake"]
    model: str


class ImageContent(CamelModel):
    """Inline image payload — the `ImageContent` schema.

    `dataBase64` is sized at the Pydantic layer for a fast reject; the
    decoded byte count is re-checked in `app.image` because base64 padding
    and whitespace can produce small overshoots that should still be
    rejected before reaching the LLM provider.
    """

    content_type: Literal["image/jpeg", "image/png", "image/webp"]
    data_base64: str = Field(min_length=1, max_length=MAX_IMAGE_BASE64_LENGTH)


class ExtractAttributesRequest(CamelModel):
    """Request body for `POST /extract-attributes`.

    Either `description` or `image` (or both) must be present — the
    `_at_least_one` validator enforces this. The OpenAPI contract carries
    the same constraint as an `anyOf` so generated clients reject empty
    requests without round-tripping.
    """

    description: str | None = Field(default=None, min_length=1, max_length=4000)
    image: ImageContent | None = None
    language: str | None = Field(default=None, pattern=r"^[a-z]{2}$")

    @model_validator(mode="after")
    def _at_least_one(self) -> "ExtractAttributesRequest":
        if self.description is None and self.image is None:
            raise ValueError("at_least_one_required")
        return self


class ExtractAttributesResponse(CamelModel):
    """Response body for `POST /extract-attributes`."""

    attributes: ItemAttributes
    model_info: ModelInfo


class EmbedRequest(CamelModel):
    """Request body for `POST /embed`.

    `texts` is bounded at 1-32 items — callers keep batches small to bound
    provider latency — and each text is 1-8000 characters. `purpose` is
    recorded for future prompt/model specialisation; today every purpose
    embeds the same way.
    """

    texts: list[Annotated[str, Field(min_length=1, max_length=8000)]] = Field(
        min_length=1, max_length=32
    )
    purpose: Literal["lost_report", "found_item", "search_query"]


class EmbedResponse(CamelModel):
    """Response body for `POST /embed`.

    `embeddings` is parallel to the request `texts` — `embeddings[i]` is the
    vector for `texts[i]` — and every vector has length `dimensions`.
    """

    embeddings: list[list[float]]
    dimensions: int
    model_info: ModelInfo


class ItemSide(CamelModel):
    """One item in a candidate match — a lost report or a found item.

    `description` is the free-text description; `attributes` carries the
    structured `ItemAttributes` when extracted (optional, so a description-only
    input is valid).
    """

    description: str = Field(min_length=1)
    attributes: ItemAttributes | None = None


class VerifyMatchRequest(CamelModel):
    """Request body for `POST /verify-match`."""

    lost: ItemSide
    found: ItemSide
    language: str = Field(default="en", pattern=r"^[a-z]{2}$")


class VerifyMatchResponse(CamelModel):
    """Response body for `POST /verify-match`.

    `verdict` and `confidence` are the verification signal; `rationale` is a
    short staff-facing explanation. The matching-service combines these with
    its own attribute/semantic scores.
    """

    verdict: Literal["match", "no_match", "uncertain"]
    confidence: float = Field(ge=0.0, le=1.0)
    rationale: str
    model_info: ModelInfo


class VerificationOutput(CamelModel):
    """The LLM's verification output — used for validation only.

    Not part of the API surface. `app.verification.parse_verification`
    validates the model's JSON against this before the route builds the
    `VerifyMatchResponse`.
    """

    verdict: Literal["match", "no_match", "uncertain"]
    confidence: float = Field(ge=0.0, le=1.0)
    rationale: str = Field(min_length=1)


class SearchSnippet(CamelModel):
    """One retrieved item passed to /answer as grounding context.

    `text` is the stored `text_source` from matching-service's
    `item_embeddings`; `id` is the item id the model cites. `distance` is
    optional retrieval context — the model may use it for ordering but it is
    not required.
    """

    id: str = Field(min_length=1)
    item_type: Literal["lost_report", "found_item"]
    category: str | None = None
    text: str = Field(min_length=1, max_length=8000)
    distance: float | None = None


class AnswerRequest(CamelModel):
    """Request body for `POST /answer`.

    `snippets` may be empty — an empty list is the "nothing retrieved" case
    and must yield a grounded:false answer, never an error.
    """

    query: str = Field(min_length=1, max_length=4000)
    snippets: list[SearchSnippet] = Field(max_length=32)
    language: str = Field(default="en", pattern=r"^[a-z]{2}$")


class AnswerResponse(CamelModel):
    """Response body for `POST /answer`."""

    answer: str
    citations: list[str]
    grounded: bool
    model_info: ModelInfo


class AnswerOutput(CamelModel):
    """The LLM's answer output — used for validation only (not API surface).

    `app.answer.parse_answer` validates the model JSON against this, then
    enforces that every citation refers to a provided snippet id.
    """

    answer: str = Field(min_length=1)
    citations: list[str] = Field(default_factory=list)
    grounded: bool


class ErrorCode(StrEnum):
    """Machine-readable error codes from the contract's `ErrorResponse`.

    Single source of truth for the code set — `app.errors` builds every
    error response from these members, so a typo cannot reach the wire.
    """

    VALIDATION_ERROR = "VALIDATION_ERROR"
    PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE"
    MODEL_OUTPUT_INVALID = "MODEL_OUTPUT_INVALID"
    PROVIDER_RATE_LIMITED = "PROVIDER_RATE_LIMITED"
    PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE"
    PROVIDER_AUTH_FAILED = "PROVIDER_AUTH_FAILED"
    PROVIDER_TIMEOUT = "PROVIDER_TIMEOUT"
    INTERNAL_ERROR = "INTERNAL_ERROR"


class ErrorResponse(CamelModel):
    """Unified error envelope — the `ErrorResponse` schema.

    `code` is a stable, machine-readable value callers switch on; `details`
    is optional structured debugging context whose shape depends on `code`.
    """

    code: ErrorCode
    message: str
    details: dict[str, Any] | None = None
