"""Pydantic models for the GenAI HTTP contract.

These mirror `components.schemas` of the repo-root OpenAPI contract
(`api/openapi.yaml`), the single source of truth for the sync API. The
wire format is camelCase; `CamelModel` maps between snake_case Python
attributes and camelCase JSON so the rest of the service stays Pythonic.

This revision covers `/extract-attributes` (#49). Models for `/embed`
(#50) and `/generate-message` (#53) are added by those tickets.
"""

from __future__ import annotations

from enum import StrEnum
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator
from pydantic.alias_generators import to_camel


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


class ItemAttributes(CamelModel):
    """Structured attributes of a lost item — the `ItemAttributes` schema.

    Fields the model cannot determine are `null`; `distinguishingMarks` is
    always an array (empty, never null). Missing keys in LLM output fall back
    to these defaults, so a benign omission is not treated as invalid output —
    only malformed JSON or wrong-typed values fail validation (-> 422).
    """

    category: str | None = None
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
        "category", "brand", "color", "approximate_time", "location", mode="before"
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


class ModelInfo(CamelModel):
    """Which provider/model produced a result — the `ModelInfo` schema."""

    provider: Literal["openai", "local"]
    model: str


class ExtractAttributesRequest(CamelModel):
    """Request body for `POST /extract-attributes`."""

    description: str = Field(min_length=1, max_length=4000)
    language: str | None = Field(default=None, pattern=r"^[a-z]{2}$")


class ExtractAttributesResponse(CamelModel):
    """Response body for `POST /extract-attributes`."""

    attributes: ItemAttributes
    model_info: ModelInfo


class ErrorCode(StrEnum):
    """Machine-readable error codes from the contract's `ErrorResponse`.

    Single source of truth for the code set — `app.errors` builds every
    error response from these members, so a typo cannot reach the wire.
    """

    VALIDATION_ERROR = "VALIDATION_ERROR"
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
