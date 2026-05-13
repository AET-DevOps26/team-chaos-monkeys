"""Initial request/response models for future GenAI endpoints.

These are placeholders so downstream tickets (#48 contracts, #49 extraction,
#50 embeddings, #53 notifications) can iterate on a shared shape. They are
not yet wired to routers.
"""

from __future__ import annotations

from pydantic import BaseModel, Field


class ExtractAttributesRequest(BaseModel):
    description: str = Field(..., description="Free-text lost-item description from the guest.")
    locale: str | None = Field(default=None, description="Optional BCP-47 language tag (e.g. 'en', 'de').")


class ExtractedAttributes(BaseModel):
    category: str | None = None
    brand: str | None = None
    color: str | None = None
    distinguishing_marks: str | None = None
    approximate_lost_time: str | None = None
    location: str | None = None


class ExtractAttributesResponse(BaseModel):
    attributes: ExtractedAttributes
    confidence: float | None = None


class EmbedRequest(BaseModel):
    text: str


class EmbedResponse(BaseModel):
    vector: list[float]
    model: str


class NotificationRequest(BaseModel):
    guest_name: str | None = None
    item_summary: str
    pickup_location: str
    locale: str | None = None


class NotificationResponse(BaseModel):
    subject: str
    body: str
