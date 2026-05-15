"""Unit tests for app.extraction — prompt building and output validation.

Deterministic, no real LLM: a scripted FakeProvider stands in for the
provider so the parsing and validation logic is exercised directly.
"""

from __future__ import annotations

import json

import pytest

from app.config import Settings
from app.exceptions import LLMRateLimitError, ModelOutputError
from app.extraction import (
    _FIELD_GUIDANCE,
    SYSTEM_PROMPT,
    build_messages,
    extract_attributes,
    item_attribute_fields,
    parse_item_attributes,
    resolve_model_info,
)
from app.providers.fake import FakeProvider

_FULL_OUTPUT = {
    "category": "jacket",
    "brand": "North Face",
    "color": "black",
    "distinguishingMarks": ["enamel pin on the chest"],
    "approximateTime": "Saturday around 11pm",
    "location": "near the cloakroom",
}


# --- prompt --------------------------------------------------------------


def test_field_guidance_matches_model():
    # Drift guard: a guidance line for every ItemAttributes field, no extras.
    assert set(_FIELD_GUIDANCE) == set(item_attribute_fields())


def test_system_prompt_lists_every_field():
    for alias in item_attribute_fields():
        assert f'"{alias}"' in SYSTEM_PROMPT


def test_build_messages_has_system_then_user():
    messages = build_messages("a black wallet", None)
    assert [m["role"] for m in messages] == ["system", "user"]
    assert messages[0]["content"] == SYSTEM_PROMPT
    assert "a black wallet" in messages[1]["content"]


def test_build_messages_includes_language_hint():
    messages = build_messages("ein schwarzer Rucksack", "de")
    assert "de" in messages[1]["content"]


def test_build_messages_omits_language_when_absent():
    messages = build_messages("a black wallet", None)
    assert "language" not in messages[1]["content"].lower()


# --- extract_attributes — happy paths -----------------------------------


async def test_extract_attributes_happy_path():
    llm = FakeProvider(chat_response=json.dumps(_FULL_OUTPUT))
    attrs = await extract_attributes("black North Face jacket", "en", llm)
    assert attrs.category == "jacket"
    assert attrs.brand == "North Face"
    assert attrs.distinguishing_marks == ["enamel pin on the chest"]


async def test_extract_attributes_calls_provider_in_json_mode():
    llm = FakeProvider(chat_response=json.dumps(_FULL_OUTPUT))
    await extract_attributes("x", None, llm)
    messages, json_mode = llm.chat_calls[-1]
    assert json_mode is True
    assert messages[0]["role"] == "system"


async def test_missing_fields_coerced_to_null_and_empty_list():
    llm = FakeProvider(chat_response=json.dumps({"category": "phone"}))
    attrs = await extract_attributes("a phone", None, llm)
    assert attrs.category == "phone"
    assert attrs.brand is None
    assert attrs.color is None
    assert attrs.distinguishing_marks == []
    assert attrs.approximate_time is None
    assert attrs.location is None


async def test_explicit_nulls_are_preserved():
    payload = dict(_FULL_OUTPUT, brand=None, approximateTime=None)
    llm = FakeProvider(chat_response=json.dumps(payload))
    attrs = await extract_attributes("x", None, llm)
    assert attrs.brand is None
    assert attrs.approximate_time is None


async def test_null_distinguishing_marks_become_empty_list():
    payload = dict(_FULL_OUTPUT, distinguishingMarks=None)
    llm = FakeProvider(chat_response=json.dumps(payload))
    attrs = await extract_attributes("x", None, llm)
    assert attrs.distinguishing_marks == []


async def test_blank_scalar_coerced_to_null():
    payload = dict(_FULL_OUTPUT, category="", color="   ")
    llm = FakeProvider(chat_response=json.dumps(payload))
    attrs = await extract_attributes("x", None, llm)
    assert attrs.category is None
    assert attrs.color is None


async def test_literal_null_string_coerced_to_null():
    payload = dict(_FULL_OUTPUT, brand="null", approximateTime="None")
    llm = FakeProvider(chat_response=json.dumps(payload))
    attrs = await extract_attributes("x", None, llm)
    assert attrs.brand is None
    assert attrs.approximate_time is None


# --- extract_attributes — invalid model output --------------------------


async def test_invalid_json_raises_model_output_error():
    llm = FakeProvider(chat_response="{not valid json")
    with pytest.raises(ModelOutputError) as exc_info:
        await extract_attributes("x", None, llm)
    assert exc_info.value.raw_output == "{not valid json"
    assert exc_info.value.schema_errors


async def test_non_object_output_raises_model_output_error():
    llm = FakeProvider(chat_response='["jacket", "wallet"]')
    with pytest.raises(ModelOutputError):
        await extract_attributes("x", None, llm)


async def test_wrong_typed_scalar_raises_model_output_error():
    bad = json.dumps(dict(_FULL_OUTPUT, category=["jacket"]))
    llm = FakeProvider(chat_response=bad)
    with pytest.raises(ModelOutputError) as exc_info:
        await extract_attributes("x", None, llm)
    assert any("category" in err for err in exc_info.value.schema_errors)


async def test_wrong_typed_marks_raises_model_output_error():
    bad = json.dumps(dict(_FULL_OUTPUT, distinguishingMarks="a single pin"))
    llm = FakeProvider(chat_response=bad)
    with pytest.raises(ModelOutputError):
        await extract_attributes("x", None, llm)


async def test_provider_error_propagates_unchanged():
    llm = FakeProvider(raise_on_chat=LLMRateLimitError("rate limited"))
    with pytest.raises(LLMRateLimitError):
        await extract_attributes("x", None, llm)


# --- parse_item_attributes ----------------------------------------------


def test_parse_accepts_snake_case_keys():
    # populate_by_name lets the parser accept either casing from the model.
    attrs = parse_item_attributes(json.dumps({"distinguishing_marks": ["pin"]}))
    assert attrs.distinguishing_marks == ["pin"]


# --- resolve_model_info -------------------------------------------------


def test_resolve_model_info_local(monkeypatch):
    monkeypatch.setenv("GENAI_PROVIDER", "local")
    monkeypatch.setenv("OLLAMA_CHAT_MODEL", "llama3.2:1b")
    info = resolve_model_info(Settings())
    assert info.provider == "local"
    assert info.model == "llama3.2:1b"


def test_resolve_model_info_openai(monkeypatch):
    monkeypatch.setenv("GENAI_PROVIDER", "openai")
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test")
    monkeypatch.setenv("OPENAI_CHAT_MODEL", "gpt-4o")
    info = resolve_model_info(Settings())
    assert info.provider == "openai"
    assert info.model == "gpt-4o"
